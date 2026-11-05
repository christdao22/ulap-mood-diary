package app.ulap.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.designsystem.theme.withTabularNumbers
import app.ulap.shared.model.Zone
import app.ulap.shared.rules.LEVEL_RANGE
import kotlin.math.roundToInt

/**
 * An energy or stress slider from 1 to 10 (design-spec.md §6.4): a header with the [label] and
 * "**8** / 10 · high", the slider in the zone's strong color, and the end labels under it.
 *
 * TalkBack reads only the slider: [label] plus [stateDescription], e.g. "Stress, 8 of 10, high"
 * (§11). The visible header and end labels are hidden from it so nothing is read twice.
 *
 * @param levelWord The word for [value] from StressLabels or EnergyLabels, e.g. "high".
 * @param startLabel "Very low" or "Drained". [endLabel] is "Very high" or "Energized".
 */
@Composable
fun LevelSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    levelWord: String,
    startLabel: String,
    endLabel: String,
    stateDescription: String,
    zone: Zone,
    modifier: Modifier = Modifier,
) {
    val colors = UlapTheme.colors
    val accent = colors.zone(zone).strong
    val typography = MaterialTheme.typography
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {},
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = typography.labelLarge, color = colors.ink)
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(value.toString()) }
                    append(" / ${LEVEL_RANGE.last} · $levelWord")
                },
                style = typography.bodyMedium.withTabularNumbers(),
                color = colors.ink2,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { raw ->
                val level = raw.roundToInt()
                if (level != value) onValueChange(level)
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = label
                    this.stateDescription = stateDescription
                },
            valueRange = LEVEL_RANGE.first.toFloat()..LEVEL_RANGE.last.toFloat(),
            // Material counts the stops between the two ends: 2..9 for a 1..10 scale.
            steps = LEVEL_RANGE.last - LEVEL_RANGE.first - 1,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                activeTickColor = colors.onInk,
                inactiveTrackColor = colors.lineSoft,
                inactiveTickColor = colors.lineStrong,
            ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {},
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = startLabel, style = typography.labelSmall, color = colors.muted)
            Text(text = endLabel, style = typography.labelSmall, color = colors.muted)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 360)
@Composable
private fun LevelSliderPreview() {
    UlapTheme {
        var stress by remember { mutableIntStateOf(8) }
        LevelSlider(
            label = "Stress",
            value = stress,
            onValueChange = { stress = it },
            levelWord = "high",
            startLabel = "Very low",
            endLabel = "Very high",
            stateDescription = "$stress of 10, high",
            zone = Zone.RED,
            modifier = Modifier.padding(UlapSpacing.M),
        )
    }
}
