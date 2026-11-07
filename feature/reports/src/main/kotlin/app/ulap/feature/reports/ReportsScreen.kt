package app.ulap.feature.reports

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ulap.core.designsystem.component.HorizontalBarRow
import app.ulap.core.designsystem.component.LegendItem
import app.ulap.core.designsystem.component.SegmentedControl
import app.ulap.core.designsystem.component.StatTile
import app.ulap.core.designsystem.component.UlapSays
import app.ulap.core.designsystem.icon.UlapIcons
import app.ulap.core.designsystem.mascot.UlapExpression
import app.ulap.core.designsystem.mascot.UlapSize
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.ui.R
import app.ulap.core.ui.nameRes
import app.ulap.core.ui.reportNameRes
import app.ulap.core.ui.text
import app.ulap.shared.catalog.MoodCatalog
import app.ulap.shared.rules.ReportPeriod
import app.ulap.shared.rules.ReportPeriodType
import app.ulap.shared.rules.ReportSummary
import app.ulap.shared.rules.StressBand
import app.ulap.shared.rules.StressBar
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Reports (design-spec.md §7.7), wired to its ViewModel. */
@Composable
internal fun ReportsScreen(modifier: Modifier = Modifier, viewModel: ReportsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showUlap by viewModel.showUlap.collectAsStateWithLifecycle()
    ReportsContent(
        uiState = uiState,
        periodType = viewModel.periodType,
        showUlap = showUlap,
        onPeriodTypeSelect = viewModel::onPeriodTypeSelect,
        onPrevious = viewModel::onPrevious,
        onNext = viewModel::onNext,
        modifier = modifier,
    )
}

/**
 * Title → Daily/Weekly/Monthly → ‹ period › → Ulap's bubble → 2×2 stat tiles → stress trend →
 * What helped most → Top triggers → Moods (design-spec.md §7.7). Under 3 check-ins in the period,
 * Ulap says a few more will show patterns, and nothing else is drawn.
 */
@Composable
internal fun ReportsContent(
    uiState: ReportsUiState,
    periodType: ReportPeriodType,
    modifier: Modifier = Modifier,
    showUlap: Boolean = true,
    onPeriodTypeSelect: (ReportPeriodType) -> Unit = {},
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
) {
    val formats = rememberReportFormats()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(UlapSpacing.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(UlapSpacing.CardGap),
    ) {
        Text(
            text = stringResource(R.string.tab_reports),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMedium,
            color = UlapTheme.colors.ink,
        )
        SegmentedControl(
            options = listOf(
                stringResource(R.string.reports_daily),
                stringResource(R.string.reports_weekly),
                stringResource(R.string.reports_monthly),
            ),
            selectedIndex = periodType.ordinal,
            onSelect = { onPeriodTypeSelect(ReportPeriodType.entries[it]) },
        )
        val loaded = uiState as? ReportsUiState.Loaded ?: return@Column
        PeriodNavigator(
            label = formats.periodLabel(loaded.period, loaded.todayEpochDay, stringResource(R.string.reports_today)),
            hasNext = loaded.hasNext,
            onPrevious = onPrevious,
            onNext = onNext,
        )
        val report = loaded.report
        if (!report.hasEnoughData) {
            UlapSays(
                expression = UlapExpression.CALM,
                message = stringResource(R.string.reports_not_enough),
                ulapSize = UlapSize.ReportBubble,
                showUlap = showUlap,
                messageStyle = bubbleMessageStyle(),
            )
            return@Column
        }
        UlapSays(
            expression = UlapExpression.CALM,
            // "Ulap noticed:" only heads a real insight; the fallback is Ulap speaking for itself.
            context = report.insight?.let { stringResource(R.string.reports_noticed) },
            message = report.insight?.text(LocalResources.current) ?: stringResource(R.string.reports_noticed_none),
            ulapSize = UlapSize.ReportBubble,
            showUlap = showUlap,
            messageStyle = bubbleMessageStyle(),
        )
        StatTiles(report.summary, report.triggers.firstOrNull()?.count, formats)
        TrendCard(report.trend, loaded.period, formats)
        HelpedCard(report, formats)
        if (report.triggers.isNotEmpty()) TriggersCard(report)
        MoodsCard(report)
    }
}

/** ‹ Oct 29 – Nov 4 ›. The label is a live region, so TalkBack reads the new period after a tap. */
@Composable
private fun PeriodNavigator(label: String, hasNext: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    val colors = UlapTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(UlapIcons.Back, contentDescription = stringResource(R.string.reports_previous), tint = colors.ink3)
        }
        Text(
            text = label,
            modifier = Modifier
                .weight(1f)
                .semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.labelLarge,
            color = colors.ink,
            textAlign = TextAlign.Center,
        )
        // Hidden past today rather than greyed out: there's nothing ahead to show.
        IconButton(onClick = onNext, enabled = hasNext) {
            if (hasNext) {
                Icon(UlapIcons.Forward, contentDescription = stringResource(R.string.reports_next), tint = colors.ink3)
            }
        }
    }
}

/** Check-ins, average stress, most common mood, top trigger (design-spec.md §6.14). */
@Composable
private fun StatTiles(summary: ReportSummary, topTriggerCount: Int?, formats: ReportFormats) {
    val none = stringResource(R.string.reports_none)
    val mood = summary.mostCommonMoodCode?.let { MoodCatalog.byCode(it) }
    Column(verticalArrangement = Arrangement.spacedBy(UlapSpacing.Xs)) {
        TileRow {
            StatTile(
                label = stringResource(R.string.reports_tile_check_ins),
                value = summary.checkInCount.toString(),
                // No comparison here: "fewer than before" would read as a nudge to log more (design-spec §10).
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            val stressChange = summary.averageStressChange
            StatTile(
                label = stringResource(R.string.reports_tile_stress),
                value = summary.averageStress?.let(formats::oneDecimal) ?: none,
                sub = stressChange?.let { stressChange(it, formats) },
                // Lower stress is good news (§6.14); the words say so too.
                subIsPositive = stressChange != null && formats.oneDecimal(abs(stressChange)) != formats.zero &&
                    stressChange < 0,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        TileRow {
            StatTile(
                label = stringResource(R.string.reports_tile_mood),
                value = mood?.let { stringResource(it.nameRes) } ?: none,
                sub = mood?.let { stringResource(it.zone.nameRes) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            val trigger = summary.topTrigger
            StatTile(
                label = stringResource(R.string.reports_tile_trigger),
                value = trigger?.let { stringResource(it.nameRes) } ?: none,
                sub = topTriggerCount?.let { pluralStringResource(R.plurals.reports_trigger_count, it, it) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

/** Two tiles side by side, as tall as the taller one. */
@Composable
private fun TileRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(UlapSpacing.Xs),
        content = content,
    )
}

@Composable
private fun stressChange(change: Double, formats: ReportFormats): String {
    val amount = formats.oneDecimal(abs(change))
    return when {
        amount == formats.zero -> stringResource(R.string.reports_same)
        change < 0 -> stringResource(R.string.reports_stress_lower, amount)
        else -> stringResource(R.string.reports_stress_higher, amount)
    }
}

/** `body` 600 (design-spec.md §6.3): the insight is a sentence, longer than a greeting. */
@Composable
private fun bubbleMessageStyle() = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
