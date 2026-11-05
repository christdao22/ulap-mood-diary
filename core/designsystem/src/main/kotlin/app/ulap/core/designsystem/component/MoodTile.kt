package app.ulap.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ulap.core.designsystem.motion.rememberReducedMotion
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.shared.model.Zone

/**
 * One mood in the pick-mood grid (design-spec.md §6.1): the zone tint with the mood's name and
 * its default stress. Tapping opens Details; the grid has no selected state.
 *
 * The tile grows taller instead of cutting off a long name or large text (§3). The caller sizes
 * its width (a third of the row) and passes the text, because the strings live in core:ui.
 *
 * @param stressText "Stress 8", in the user's language.
 * @param contentDescription What TalkBack reads instead of the two lines, e.g.
 *   "Worried. Red zone. Default stress 8." (§11).
 */
@Composable
fun MoodTile(
    name: String,
    stressText: String,
    zone: Zone,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UlapTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // A 96% squeeze for 100ms while pressed, off with "Remove animations" (§9).
    val pressScale = if (pressed && !rememberReducedMotion()) PRESSED_SCALE else 1f
    val scale by animateFloatAsState(pressScale, tween(PRESS_MILLIS), label = "moodTilePress")

    Surface(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .heightIn(min = MinHeight)
            .semantics { this.contentDescription = contentDescription },
        shape = MaterialTheme.shapes.medium,
        color = colors.zone(zone).tint,
        contentColor = colors.ink,
        interactionSource = interactionSource,
    ) {
        Column(
            modifier = Modifier
                .clearAndSetSemantics {}
                .padding(horizontal = 10.dp, vertical = UlapSpacing.Xs),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = name, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp))
            Text(text = stressText, style = MaterialTheme.typography.labelSmall, color = colors.mutedOnTint)
        }
    }
}

private val MinHeight = 52.dp
private const val PRESSED_SCALE = 0.96f
private const val PRESS_MILLIS = 100

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 360)
@Composable
private fun MoodTilePreview() {
    UlapTheme {
        Row(
            modifier = Modifier.padding(UlapSpacing.M),
            horizontalArrangement = Arrangement.spacedBy(UlapSpacing.MoodGridGap),
        ) {
            listOf(Zone.RED to "Worried", Zone.BLUE to "Disappointed", Zone.YELLOW to "Excited").forEach {
                MoodTile(
                    name = it.second,
                    stressText = "Stress 8",
                    zone = it.first,
                    contentDescription = it.second,
                    onClick = {},
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
