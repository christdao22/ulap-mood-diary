package app.ulap.feature.reports

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ulap.core.common.dispatchers.DefaultDispatcher
import app.ulap.core.domain.checkin.CheckInRepository
import app.ulap.core.domain.settings.SettingsRepository
import app.ulap.core.model.UserSettings
import app.ulap.shared.model.CheckIn
import app.ulap.shared.rules.ReportInsight
import app.ulap.shared.rules.ReportPeriod
import app.ulap.shared.rules.ReportPeriodType
import app.ulap.shared.rules.ReportSummary
import app.ulap.shared.rules.Share
import app.ulap.shared.rules.StressBar
import app.ulap.shared.rules.SupportStat
import app.ulap.shared.rules.TriggerStat
import app.ulap.shared.rules.inPeriod
import app.ulap.shared.rules.localEpochDay
import app.ulap.shared.rules.moodFrequency
import app.ulap.shared.rules.stressTrend
import app.ulap.shared.rules.topTriggers
import app.ulap.shared.rules.whatHelped
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaInstant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the Reports screen shows (design-spec.md §7.7). */
sealed interface ReportsUiState {
    /** Room hasn't answered yet (a few milliseconds). */
    data object Loading : ReportsUiState

    /**
     * One period's report. [hasNext] is false once the period reaches today, so the › arrow
     * stops at "now". [todayEpochDay] lets the period label say "Today".
     */
    data class Loaded(
        val period: ReportPeriod,
        val todayEpochDay: Long,
        val hasNext: Boolean,
        val report: PeriodReport,
    ) : ReportsUiState
}

/**
 * Every number on one period's report, all from :shared's rules (technical-plan.md §10). Under
 * [ReportInsight.MIN_CHECK_INS] check-ins the screen shows the not-enough-data state instead.
 */
data class PeriodReport(
    val summary: ReportSummary,
    val trend: List<StressBar>,
    val moods: List<Share<String>>,
    val triggers: List<TriggerStat>,
    /** Only supports that lowered stress on average: a rise isn't something that "helped". */
    val helped: List<SupportStat>,
    /** The first insight that holds, or null when nothing stands out. */
    val insight: ReportInsight?,
) {
    val hasEnoughData: Boolean get() = summary.checkInCount >= ReportInsight.MIN_CHECK_INS

    companion object {
        fun of(checkIns: List<CheckIn>, period: ReportPeriod): PeriodReport {
            val inside = checkIns.inPeriod(period)
            return PeriodReport(
                summary = ReportSummary.of(checkIns, period),
                trend = stressTrend(inside, period),
                moods = moodFrequency(inside),
                triggers = topTriggers(inside),
                helped = whatHelped(inside).filter { it.averageRelief > 0 },
                insight = ReportInsight.of(inside, period).firstOrNull(),
            )
        }
    }
}

@HiltViewModel
class ReportsViewModel @Inject constructor(
    checkInRepository: CheckInRepository,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
    @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {
    /** The selected segment. Weekly first: it's the view the design spec shows (§7.7). */
    var periodType by mutableStateOf(ReportPeriodType.WEEKLY)
        private set

    /**
     * The day the shown period ends on or contains, after ‹ or ›. Null means "follow today", so
     * the report moves on by itself at midnight.
     */
    private var anchorEpochDay by mutableStateOf<Long?>(null)

    /** The user can turn Ulap off; the bubble's words still show (design-spec.md §8.5). */
    val showUlap: StateFlow<Boolean> = settingsRepository.settings
        .map { it.mascotEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UserSettings().mascotEnabled)

    // Screens read Room only (technical-plan.md §3). Every number comes from :shared, worked out
    // off the main thread: a year of check-ins is a few thousand rows.
    val uiState: StateFlow<ReportsUiState> = combine(
        checkInRepository.observeAll(),
        todayEpochDay(),
        snapshotFlow { periodType to anchorEpochDay },
    ) { checkIns, today, (type, anchor) ->
        val period = ReportPeriod.of(type, anchor ?: today)
        ReportsUiState.Loaded(
            period = period,
            todayEpochDay = today,
            hasNext = period.hasNext(today),
            report = PeriodReport.of(checkIns, period),
        )
    }.flowOn(defaultDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ReportsUiState.Loading)

    /** A segment tap keeps the day in view: Weekly → Daily shows the last day of that week. */
    fun onPeriodTypeSelect(type: ReportPeriodType) {
        val current = (uiState.value as? ReportsUiState.Loaded)?.period
        if (current != null && anchorEpochDay != null) anchorEpochDay = current.endEpochDay
        periodType = type
        clampToToday()
    }

    /** ‹ : the period just before. */
    fun onPrevious() {
        val current = (uiState.value as? ReportsUiState.Loaded)?.period ?: return
        anchorEpochDay = current.previous().endEpochDay
    }

    /** › : the period just after, never past today. */
    fun onNext() {
        val loaded = uiState.value as? ReportsUiState.Loaded ?: return
        if (!loaded.hasNext) return
        anchorEpochDay = loaded.period.next().endEpochDay
        clampToToday()
    }

    /** Once the anchor reaches today, go back to following today. */
    private fun clampToToday() {
        val now = (uiState.value as? ReportsUiState.Loaded)?.todayEpochDay ?: return
        anchorEpochDay = anchorEpochDay?.takeIf { it < now }
    }

    /**
     * Today's local day on the phone, again at each local midnight, so the report moves on if the
     * screen stays open overnight (same rule as Logs).
     */
    private fun todayEpochDay(): Flow<Long> = flow {
        while (true) {
            val now = clock.now()
            val zone = ZoneId.systemDefault()
            val offsetMin = zone.rules.getOffset(now.toJavaInstant()).totalSeconds / SECONDS_PER_MINUTE
            val today = localEpochDay(now, offsetMin)
            emit(today)
            val nextMidnight = LocalDate.ofEpochDay(today + 1).atStartOfDay(zone).toInstant().toEpochMilli()
            delay((nextMidnight - now.toEpochMilliseconds()).coerceAtLeast(1).milliseconds)
        }
    }.distinctUntilChanged()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val SECONDS_PER_MINUTE = 60
    }
}
