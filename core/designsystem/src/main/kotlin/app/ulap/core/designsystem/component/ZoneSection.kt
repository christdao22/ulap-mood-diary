package app.ulap.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ulap.core.designsystem.mascot.MoodFace
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.shared.model.Zone

/**
 * The card around one zone's moods on the pick-mood screen (design-spec.md §6.2): a header with
 * the zone's dot, name and descriptor, then [content] (the mood tiles). The dot is never the
 * only sign of the zone; the name is always next to it (§11).
 *
 * @param title "Red Zone", in the user's language.
 * @param descriptor "tense · high energy", in the user's language.
 */
@Composable
fun ZoneSection(
    zone: Zone,
    title: String,
    descriptor: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = UlapTheme.colors
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = colors.surface,
        border = BorderStroke(UlapSpacing.Border, colors.line),
    ) {
        Column(
            modifier = Modifier.padding(UlapSpacing.S),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                // TalkBack reads the header as one heading, so a user can jump between zones.
                modifier = Modifier.semantics(mergeDescendants = true) { heading() },
                horizontalArrangement = Arrangement.spacedBy(UlapSpacing.Xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(DotSize)
                        .background(colors.zone(zone).strong, RoundedCornerShape(DotRadius)),
                )
                Text(text = title, style = MaterialTheme.typography.labelLarge)
                Text(
                    text = descriptor,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = colors.muted,
                )
            }
            content()
        }
    }
}

private val DotSize = 10.dp
private val DotRadius = 3.dp

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F8, widthDp = 360)
@Composable
private fun ZoneSectionPreview() {
    UlapTheme {
        ZoneSection(
            zone = Zone.RED,
            title = "Red Zone",
            descriptor = "tense · high energy",
            modifier = Modifier.padding(UlapSpacing.M),
        ) {
            MoodTile(
                name = "Worried",
                face = MoodFace.WORRIED,
                zone = Zone.RED,
                contentDescription = "Worried",
                onClick = {},
            )
        }
    }
}
