package app.ulap.feature.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ulap.core.domain.checkin.CheckInRepository
import app.ulap.shared.catalog.MoodCatalog
import app.ulap.shared.rules.CheckInDay
import app.ulap.shared.rules.groupByLocalDay
import app.ulap.shared.rules.localEpochDay
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaInstant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** What the Logs screen shows (design-spec.md §7.6). */
sealed interface LogsUiState {
    /** Room hasn't answered yet (a few milliseconds). Shows no list, so nothing flashes. */
    data object Loading : LogsUiState

    /** Newest day first, each with its newest check-in on top. Empty when there are no check-ins. */
    data class Loaded(val days: List<LogDay>) : LogsUiState
}

/** One day group: its header label and its check-ins. */
data class LogDay(val label: DayLabel, val day: CheckInDay)

/** How a day header names its day: "Today", "Yesterday" or a date (design-spec.md §6.13). */
sealed interface DayLabel {
    data object Today : DayLabel

    data object Yesterday : DayLabel

    /** "Wed, Nov 4", with the year only when it isn't this year's ([showYear]). */
    data class Date(val date: LocalDate, val showYear: Boolean) : DayLabel

    companion object {
        /** The label for [epochDay] when the phone's local day is [today] (both days since 1970-01-01). */
        fun of(epochDay: Long, today: Long): DayLabel = when (epochDay) {
            today -> Today

            today - 1 -> Yesterday

            else -> LocalDate.ofEpochDay(epochDay).let {
                Date(it, showYear = it.year != LocalDate.ofEpochDay(today).year)
            }
        }
    }
}

@HiltViewModel
class LogsViewModel @Inject constructor(checkInRepository: CheckInRepository, private val clock: Clock) :
    ViewModel() {
    // The screen reads Room only (technical-plan.md §3). Each row's day is the one on its own clock
    // when it was logged; "Today" is the phone's day now. Both come from :shared's local-day rule.
    val uiState: StateFlow<LogsUiState> =
        combine(checkInRepository.observeAll(), todayEpochDay()) { checkIns, today ->
            // CheckInValidator rejects unknown mood codes, so this filter should never drop a row. It
            // keeps one bad row from breaking the whole list.
            val days = checkIns
                .filter { MoodCatalog.byCode(it.moodCode) != null }
                .groupByLocalDay()
                .map { LogDay(label = DayLabel.of(it.epochDay, today), day = it) }
            LogsUiState.Loaded(days)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = LogsUiState.Loading,
        )

    /**
     * Today's local day on the phone, again at each local midnight, so "Today" moves to
     * "Yesterday" if the screen stays open overnight. WhileSubscribed restarts it when the screen
     * comes back, which also catches a time zone change.
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
        // Keeps the flow alive through a screen rotation, which takes well under 5 seconds.
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val SECONDS_PER_MINUTE = 60
    }
}
