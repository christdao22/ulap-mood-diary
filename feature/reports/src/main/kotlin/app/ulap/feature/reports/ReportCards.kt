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

// The Reports chart and bar-list cards (design-spec.md §6.15, §7.7).

/** The stress trend: title, scale subtitle, the chart and the band legend (design-spec.md §6.15). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TrendCard(trend: List<StressBar>, period: ReportPeriod, formats: ReportFormats) {
    val colors = UlapTheme.colors
    val isDaily = period.type == ReportPeriodType.DAILY
    ReportCard(
        title = stringResource(R.string.reports_trend_title),
        subtitle = stringResource(if (isDaily) R.string.reports_trend_sub_daily else R.string.reports_trend_sub_days),
    ) {
        val summary = trendSummary(trend, period.type, formats)
        StressTrendChart(
            bars = trend,
            xLabel = { index -> trend.getOrNull(index)?.let { formats.axisLabel(it, period.type) }.orEmpty() },
            labelEvery = when (period.type) {
                ReportPeriodType.MONTHLY -> MONTH_LABEL_EVERY
                ReportPeriodType.WEEKLY -> 1
                ReportPeriodType.DAILY -> (trend.size + DAILY_LABELS - 1) / DAILY_LABELS
            },
            summary = summary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(UlapSpacing.S)) {
            LegendItem(stringResource(R.string.reports_band_high), colors.bandColor(StressBand.HIGH))
            LegendItem(stringResource(R.string.reports_band_moderate), colors.bandColor(StressBand.MODERATE))
            LegendItem(stringResource(R.string.reports_band_low), colors.bandColor(StressBand.LOW))
        }
    }
}

/** "Daily average stress. Highest Monday, 6.8. Lowest Sunday, 2.4." (design-spec.md §11). */
@Composable
private fun trendSummary(trend: List<StressBar>, type: ReportPeriodType, formats: ReportFormats): String {
    val withData = trend.filter { it.stress != null }
    val high = withData.maxByOrNull { it.stress!! } ?: return ""
    val low = withData.minBy { it.stress!! }
    return stringResource(
        if (type ==
            ReportPeriodType.DAILY
        ) {
            R.string.reports_trend_summary_daily
        } else {
            R.string.reports_trend_summary_days
        },
        formats.spokenLabel(high, type),
        formats.oneDecimal(high.stress!!),
        formats.spokenLabel(low, type),
        formats.oneDecimal(low.stress!!),
    )
}

/** Average drop in stress per support, Green strong bars (design-spec.md §6.15). */
@Composable
internal fun HelpedCard(report: PeriodReport, formats: ReportFormats) {
    ReportCard(
        title = stringResource(R.string.reports_helped_title),
        subtitle = stringResource(R.string.reports_helped_sub),
    ) {
        if (report.helped.isEmpty()) {
            Text(
                text = stringResource(R.string.reports_helped_empty),
                style = MaterialTheme.typography.bodySmall,
                color = UlapTheme.colors.muted,
            )
        }
        report.helped.forEach { stat ->
            val name = stringResource(stat.support.reportNameRes ?: return@forEach)
            val amount = formats.oneDecimal(stat.averageRelief)
            HorizontalBarRow(
                label = name,
                value = stringResource(R.string.reports_helped_value, amount),
                fraction = (stat.averageRelief / MAX_STRESS).toFloat(),
                fillColor = UlapTheme.colors.green.strong,
                description = stringResource(R.string.reports_helped_description, name, amount),
            )
        }
    }
}

/** Check-ins per trigger, `ink3` bars. */
@Composable
internal fun TriggersCard(report: PeriodReport) {
    val max = report.triggers.maxOf { it.count }
    ReportCard(
        title = stringResource(R.string.reports_triggers_title),
        subtitle = stringResource(R.string.reports_triggers_sub),
    ) {
        report.triggers.forEach { stat ->
            val name = stringResource(stat.trigger.nameRes)
            HorizontalBarRow(
                label = name,
                value = stat.count.toString(),
                fraction = stat.count.toFloat() / max,
                fillColor = UlapTheme.colors.ink3,
                description = pluralStringResource(
                    R.plurals.reports_bar_count_description,
                    stat.count,
                    name,
                    stat.count,
                ),
            )
        }
    }
}

/** The top 5 moods, each bar in its zone's color; the name says the mood, so color isn't alone. */
@Composable
internal fun MoodsCard(report: PeriodReport) {
    val max = report.moods.maxOfOrNull { it.count } ?: return
    ReportCard(
        title = stringResource(R.string.reports_moods_title),
        subtitle = stringResource(R.string.reports_moods_sub),
    ) {
        report.moods.forEach { share ->
            val mood = MoodCatalog.byCode(share.key) ?: return@forEach
            val name = stringResource(mood.nameRes)
            HorizontalBarRow(
                label = name,
                value = share.count.toString(),
                fraction = share.count.toFloat() / max,
                fillColor = UlapTheme.colors.zone(mood.zone).strong,
                description = pluralStringResource(
                    R.plurals.reports_bar_count_description,
                    share.count,
                    name,
                    share.count,
                ),
            )
        }
    }
}

/** A standard card (design-spec.md §6.7) with a chart title and a one-line subtitle (§6.15). */
@Composable
private fun ReportCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    val colors = UlapTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.surface,
        border = BorderStroke(UlapSpacing.Border, colors.line),
    ) {
        Column(
            modifier = Modifier.padding(UlapSpacing.CardPadding),
            verticalArrangement = Arrangement.spacedBy(UlapSpacing.S),
        ) {
            Column {
                Text(
                    text = title,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.ink,
                )
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colors.muted)
            }
            content()
        }
    }
}

private const val MAX_STRESS = 10.0
private const val MONTH_LABEL_EVERY = 7
private const val DAILY_LABELS = 6
