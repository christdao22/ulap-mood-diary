package app.ulap.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.ulap.core.designsystem.theme.UlapShape
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.designsystem.theme.withTabularNumbers
import app.ulap.shared.model.Zone

/**
 * One check-in in Logs (design-spec.md §6.13): a zone stripe on the left, then the mood and
 * "9:15 AM · Stress 8 · Work", then up to two lines of the note. The stripe is never the only
 * sign of the zone: the mood name says it too (§11).
 *
 * @param mood "Worried", in the user's language.
 * @param meta "9:15 AM · Stress 8 · Work", in the user's language.
 * @param note The journal note, or `null` for none. Shown on screen only, never logged.
 * @param menu The 36dp overflow button on the right, usually an [UlapOverflowMenu] with Edit and
 *   Delete. TalkBack reaches it on its own, after the row.
 */
@Composable
fun LogRow(
    zone: Zone,
    mood: String,
    meta: String,
    note: String?,
    modifier: Modifier = Modifier,
    menu: @Composable () -> Unit = {},
) {
    val colors = UlapTheme.colors
    Surface(
        // TalkBack reads the whole row as one item.
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        shape = UlapShape.Button,
        color = colors.surface,
        border = BorderStroke(UlapSpacing.Border, colors.line),
    ) {
        // IntrinsicSize.Min lets the stripe stretch to the text's height and no further.
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .padding(UlapSpacing.S),
            horizontalArrangement = Arrangement.spacedBy(UlapSpacing.S),
        ) {
            Box(
                Modifier
                    .width(StripeWidth)
                    .fillMaxHeight()
                    .background(colors.zone(zone).strong, RoundedCornerShape(StripeRadius)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(UlapSpacing.Xxs),
            ) {
                // A FlowRow, so at large font sizes the meta wraps under the mood instead of squeezing.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(UlapSpacing.Xs),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = mood, style = MaterialTheme.typography.labelLarge, color = colors.ink)
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall.withTabularNumbers(),
                        color = colors.muted,
                        // An "Other" trigger is the person's own words and can be long.
                        maxLines = META_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!note.isNullOrBlank()) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.ink3,
                        maxLines = NOTE_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            menu()
        }
    }
}

/**
 * The header above one day's log rows (design-spec.md §6.13): "Today" on the left and
 * "5 check-ins · avg stress 4.4" on the right. TalkBack reads it as one heading, so a person can
 * jump from day to day.
 */
@Composable
fun LogDayHeader(day: String, summary: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.spacedBy(UlapSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = day,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = UlapTheme.colors.ink,
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.labelSmall.withTabularNumbers(),
            color = UlapTheme.colors.muted,
        )
    }
}

private val StripeWidth = 4.dp
private val StripeRadius = 4.dp
private const val NOTE_MAX_LINES = 2
private const val META_MAX_LINES = 2

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F8, widthDp = 360)
@Composable
private fun LogRowPreview() {
    UlapTheme {
        Column(
            modifier = Modifier.padding(UlapSpacing.M),
            verticalArrangement = Arrangement.spacedBy(UlapSpacing.Xs),
        ) {
            LogDayHeader(day = "Today", summary = "2 check-ins · avg stress 5.5")
            LogRow(
                zone = Zone.RED,
                mood = "Worried",
                meta = "9:15 AM · Stress 8 · Work",
                note = "Report due tomorrow and I haven't started the slides. Maybe I can ask Ana for help " +
                    "with the charts after lunch.",
            ) {
                UlapOverflowMenu(contentDescription = "Edit or delete this check-in") { close ->
                    UlapMenuItem(text = "Edit", onClick = close)
                    UlapMenuItem(text = "Delete", onClick = close)
                }
            }
            LogRow(zone = Zone.GREEN, mood = "Calm", meta = "7:02 AM · Stress 3", note = null)
        }
    }
}
