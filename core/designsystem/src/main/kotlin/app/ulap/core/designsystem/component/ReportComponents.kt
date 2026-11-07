package app.ulap.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ulap.core.designsystem.theme.UlapShape
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.designsystem.theme.withTabularNumbers

/**
 * The Daily / Weekly / Monthly control (design-spec.md §6.11): equal segments in a `surface`
 * container. The selected one is filled with `ink`. TalkBack reads each as a selectable tab.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UlapTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colors.surface,
        border = BorderStroke(UlapSpacing.Border, colors.line),
    ) {
        Row(Modifier.padding(UlapSpacing.Xxs).selectableGroup()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        // 40dp drawn; the row's padding brings the touch area to 48dp (§11).
                        .heightIn(min = SegmentHeight)
                        .background(if (selected) colors.ink else Color.Transparent, SegmentShape)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) colors.onInk else colors.ink3,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * One Reports stat tile (design-spec.md §6.14): a label, a big tabular value and an optional
 * sub line. [subIsPositive] colors the sub `positiveText`, for a change that is good news; the
 * words always say it too, so color is never the only sign. TalkBack reads the tile as one item.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    subIsPositive: Boolean = false,
) {
    val colors = UlapTheme.colors
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = UlapShape.Button,
        color = colors.surface,
        border = BorderStroke(UlapSpacing.Border, colors.line),
    ) {
        Column(Modifier.padding(UlapSpacing.S)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = CaptionSize),
                color = colors.muted,
            )
            Text(
                text = value,
                // Bricolage 22/700 (§6.14): titleMedium is Bricolage Bold.
                style = MaterialTheme.typography.titleMedium.copy(fontSize = StatValueSize, lineHeight = StatValueLine)
                    .withTabularNumbers(),
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (sub != null) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = CaptionSize),
                    color = if (subIsPositive) colors.positiveText else colors.muted,
                )
            }
        }
    }
}

/**
 * One row of a horizontal bar list (design-spec.md §6.15): the name, a 10dp pill track filled to
 * [fraction] (0–1) in [fillColor], and the value on the right in tabular figures. The name always
 * says what the bar is, so color is never the only sign (§11).
 *
 * @param description What TalkBack reads for the whole row, e.g. "Work, 4 check-ins".
 */
@Composable
fun HorizontalBarRow(
    label: String,
    value: String,
    fraction: Float,
    fillColor: Color,
    description: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = UlapTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(UlapSpacing.Xxs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UlapSpacing.Xs),
        ) {
            leading?.invoke()
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink,
            )
            Text(text = value, style = MaterialTheme.typography.bodySmall.withTabularNumbers(), color = colors.ink3)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(BarTrackHeight)
                .background(colors.lineSoft, UlapShape.Pill),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(BarTrackHeight)
                    .background(fillColor, UlapShape.Pill),
            )
        }
    }
}

/** A legend swatch: a small colored square before its label, e.g. "High 7–10" (design-spec.md §2.3). */
@Composable
fun LegendItem(label: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UlapSpacing.Xxs),
    ) {
        Box(Modifier.size(LegendSwatch).background(color, RoundedCornerShape(2.dp)))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = UlapTheme.colors.muted)
    }
}

private val SegmentHeight = 40.dp
private val SegmentShape = RoundedCornerShape(9.dp)
private val CaptionSize = 12.sp
private val StatValueSize = 22.sp
private val StatValueLine = 27.sp
private val BarTrackHeight = 10.dp
private val LegendSwatch = 10.dp

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F8, widthDp = 360)
@Composable
private fun ReportComponentsPreview() {
    UlapTheme {
        Column(Modifier.padding(UlapSpacing.M), verticalArrangement = Arrangement.spacedBy(UlapSpacing.S)) {
            SegmentedControl(options = listOf("Daily", "Weekly", "Monthly"), selectedIndex = 1, onSelect = {})
            Row(horizontalArrangement = Arrangement.spacedBy(UlapSpacing.Xs)) {
                StatTile("Check-ins", "7", Modifier.weight(1f), sub = "2 more than before")
                StatTile(
                    "Average stress",
                    "4.4",
                    Modifier.weight(1f),
                    sub = "0.5 lower than before",
                    subIsPositive = true,
                )
            }
            HorizontalBarRow("Work", "4", 0.6f, UlapTheme.colors.ink3, "Work, 4 check-ins")
            Row(horizontalArrangement = Arrangement.spacedBy(UlapSpacing.S)) {
                LegendItem("High 7–10", UlapTheme.colors.stressHigh.bar)
                LegendItem("Low 0–3.9", UlapTheme.colors.stressLow.bar, Modifier.width(100.dp))
            }
        }
    }
}
