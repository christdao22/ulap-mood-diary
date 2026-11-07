package app.ulap.feature.reports

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ulap.core.designsystem.theme.UlapColors
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.shared.rules.StressBand
import app.ulap.shared.rules.StressBar
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import kotlin.math.roundToInt

/**
 * The stress trend bar chart (design-spec.md §6.15, D9): 130dp high, gridlines every 2 points in
 * `lineSoft`, a `lineStrong` baseline, and each bar in its stress band's color. A day with no
 * check-ins has no bar (a gap, not a 0). TalkBack reads [summary] instead of the bars (§11).
 *
 * @param xLabel The label under bar number `index` ("Mon", "9:15 AM", "8").
 * @param labelEvery Show a label on every Nth bar only, e.g. 7 for Monthly (days 1, 8, 15, 22, 29).
 */
@Composable
internal fun StressTrendChart(
    bars: List<StressBar>,
    xLabel: (Int) -> String,
    labelEvery: Int,
    summary: String,
    modifier: Modifier = Modifier,
) {
    val points = bars.withIndex().mapNotNull { (index, bar) -> bar.stress?.let { index to it } }
    if (points.isEmpty()) return
    val colors = UlapTheme.colors
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(ChartHeight)
            .clearAndSetSemantics { contentDescription = summary },
    ) {
        // Thinner bars as the count grows (7 → wide, 31 → narrow), never wider than 30dp.
        val thickness = (maxWidth / bars.size * BAR_SHARE).coerceIn(MinBarWidth, MaxBarWidth)
        val columns = remember(colors, thickness) { BandColumns(colors, thickness) }
        val labelStyle = TextStyle(color = colors.muted, fontSize = AxisLabelSize)
        val layer = rememberColumnCartesianLayer(
            columnProvider = columns,
            columnCollectionSpacing = thickness,
            rangeProvider = remember(bars.size) {
                CartesianLayerRangeProvider.fixed(
                    minX = 0.0,
                    maxX = (bars.size - 1).toDouble(),
                    minY = 0.0,
                    maxY = MAX_STRESS,
                )
            },
        )
        val chart = rememberCartesianChart(
            layer,
            startAxis = rememberStressAxis(labelStyle),
            bottomAxis = rememberBarAxis(labelStyle, xLabel, labelEvery),
            getXStep = { _, _, _ -> 1.0 },
        )
        val model = remember(points) {
            CartesianChartModel(
                ColumnCartesianLayerModel.build { series(x = points.map { it.first }, y = points.map { it.second }) },
            )
        }
        CartesianChartHost(
            chart = chart,
            model = model,
            modifier = Modifier.fillMaxWidth().height(ChartHeight),
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            // Fit every bar of the period on screen: the chart neither scrolls nor zooms.
            zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
        )
    }
}

/** 0–10 on the left, a `lineSoft` gridline every 2 points (design-spec.md §6.15). */
@Composable
private fun rememberStressAxis(labelStyle: TextStyle) = VerticalAxis.rememberStart(
    line = null,
    tick = null,
    label = rememberAxisLabelComponent(style = labelStyle),
    guideline = rememberAxisGuidelineComponent(
        fill = Fill(UlapTheme.colors.lineSoft),
        shape = RectangleShape,
    ),
    valueFormatter = remember { CartesianValueFormatter { _, value, _ -> value.roundToInt().toString() } },
    itemPlacer = remember { VerticalAxis.ItemPlacer.step(step = { GRID_STEP }) },
)

/** The labels under the bars on a `lineStrong` baseline. */
@Composable
private fun rememberBarAxis(labelStyle: TextStyle, xLabel: (Int) -> String, labelEvery: Int) =
    HorizontalAxis.rememberBottom(
        line = rememberAxisLineComponent(fill = Fill(UlapTheme.colors.lineStrong)),
        tick = null,
        guideline = null,
        label = rememberAxisLabelComponent(style = labelStyle),
        valueFormatter = remember(xLabel) {
            CartesianValueFormatter { _, value, _ -> xLabel(value.roundToInt()) }
        },
        itemPlacer = remember(labelEvery) { HorizontalAxis.ItemPlacer.aligned(spacing = { labelEvery }) },
    )

/** Colors each bar by its own stress band (design-spec.md §2.3), with a 4dp top radius. */
private class BandColumns(colors: UlapColors, thickness: Dp) : ColumnCartesianLayer.ColumnProvider {
    private val shape = RoundedCornerShape(topStart = BarRadius, topEnd = BarRadius)
    private val high = LineComponent(Fill(colors.stressHigh.bar), thickness, shape)
    private val moderate = LineComponent(Fill(colors.stressModerate.bar), thickness, shape)
    private val low = LineComponent(Fill(colors.stressLow.bar), thickness, shape)

    override fun getColumn(entry: ColumnCartesianLayerModel.Entry, extraStore: ExtraStore): LineComponent =
        when (StressBand.of(entry.y)) {
            StressBand.HIGH -> high
            StressBand.MODERATE -> moderate
            StressBand.LOW -> low
        }

    override fun getWidestSeriesColumn(seriesKey: Any, seriesIndex: Int, extraStore: ExtraStore): LineComponent = high
}

/** Stress band color for a value, shared by the bars and the legend. */
internal fun UlapColors.bandColor(band: StressBand) = when (band) {
    StressBand.HIGH -> stressHigh.bar
    StressBand.MODERATE -> stressModerate.bar
    StressBand.LOW -> stressLow.bar
}

private val ChartHeight = 130.dp
private val MaxBarWidth = 30.dp
private val MinBarWidth = 3.dp
private val BarRadius = 4.dp
private val AxisLabelSize = 11.sp
private const val BAR_SHARE = 0.5f
private const val MAX_STRESS = 10.0
private const val GRID_STEP = 2.0
