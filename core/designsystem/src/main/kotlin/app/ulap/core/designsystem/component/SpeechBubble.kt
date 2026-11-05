package app.ulap.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ulap.core.designsystem.mascot.Ulap
import app.ulap.core.designsystem.mascot.UlapExpression
import app.ulap.core.designsystem.mascot.UlapSize
import app.ulap.core.designsystem.theme.UlapShape
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme

/**
 * What Ulap says (design-spec.md §6.3): an optional [context] line, the [message], then an
 * optional [supporting] line. The bottom-left corner points at Ulap. TalkBack reads every line as
 * one.
 *
 * @param messageStyle `displayS` (titleMedium, the default) or `body` 600 for longer messages.
 * @param supporting A quiet line under the message, e.g. "Any answer is okay." (§7.2).
 */
@Composable
fun SpeechBubble(
    message: String,
    modifier: Modifier = Modifier,
    context: String? = null,
    supporting: String? = null,
    messageStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    val colors = UlapTheme.colors
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = UlapShape.Bubble,
        color = colors.surface,
        contentColor = colors.ink,
        border = BorderStroke(UlapSpacing.Border, colors.line),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            if (context != null) {
                Text(text = context, style = MaterialTheme.typography.bodySmall, color = colors.muted)
            }
            Text(text = message, style = messageStyle)
            if (supporting != null) {
                Text(text = supporting, style = MaterialTheme.typography.bodySmall, color = colors.muted)
            }
        }
    }
}

/**
 * Ulap with a [SpeechBubble] to its right, bottom-aligned with Ulap's base (design-spec.md
 * §6.3). Ulap is decorative here: the bubble already says it, so TalkBack reads only the bubble
 * (§11).
 */
@Composable
fun UlapSays(
    expression: UlapExpression,
    message: String,
    modifier: Modifier = Modifier,
    context: String? = null,
    supporting: String? = null,
    ulapSize: Dp = UlapSize.Greeting,
    showUlap: Boolean = true,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(UlapSpacing.Xs),
        verticalAlignment = Alignment.Bottom,
    ) {
        // The user can turn Ulap off; the message still shows (§8.5).
        if (showUlap) Ulap(expression = expression, contentDescription = null, size = ulapSize)
        SpeechBubble(
            message = message,
            context = context,
            supporting = supporting,
            // Ulap's base sits 20 of its 110 viewBox units above the bottom edge (§8.2).
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (showUlap) ulapSize * BASE_OFFSET else 0.dp),
        )
    }
}

private const val BASE_OFFSET = 20f / 130f

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F8, widthDp = 360)
@Composable
private fun UlapSaysPreview() {
    UlapTheme {
        UlapSays(
            expression = UlapExpression.NEUTRAL,
            context = "Good evening",
            message = "How are you feeling right now?",
            supporting = "Any answer is okay.",
            modifier = Modifier.padding(UlapSpacing.M),
        )
    }
}
