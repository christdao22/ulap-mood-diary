package app.ulap.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import app.ulap.core.designsystem.theme.DisplayFontFamily
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme

/**
 * The NCMH crisis hotline card (design-spec.md §6.8): surface, 1dp `infoBorder`, radius 18,
 * padding 18. The label, the number in large type, a primary call button, then a helper line.
 * Never animated, never decorated, and Ulap never appears on it (§8.5). Safety (🔒 in PHASES.md):
 * a change here needs two reviewers.
 *
 * The words come from the caller, because every string lives in core:ui's strings.xml.
 *
 * @param callDescription What TalkBack reads for the call button, with the number digit by
 *   digit ("Call 1 5 5 3"), like the number above it. Without it TalkBack reads [callLabel]'s
 *   number as "one thousand five hundred fifty-three".
 */
@Composable
fun UlapHotlineCard(
    title: String,
    number: String,
    callLabel: String,
    callDescription: String,
    helper: String,
    onCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UlapTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surface,
        border = BorderStroke(UlapSpacing.Border, colors.infoBorder),
    ) {
        Column(
            modifier = Modifier.padding(UlapSpacing.HotlineCardPadding),
            verticalArrangement = Arrangement.spacedBy(UlapSpacing.Xs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.info,
            )
            Text(
                text = number,
                style = HotlineNumberStyle,
                color = colors.ink,
                // TalkBack reads "1 5 5 3", the way a person dials it, not "one thousand five hundred…".
                modifier = Modifier.semantics { contentDescription = number.spokenDigits() },
            )
            UlapPrimaryButton(
                text = callLabel,
                onClick = onCall,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = callDescription },
            )
            Text(text = helper, style = MaterialTheme.typography.bodySmall, color = colors.ink2)
        }
    }
}

/**
 * "1553" as "1 5 5 3", so TalkBack reads it the way a person dials it. Pass it into the call
 * button's [UlapHotlineCard] `callDescription` too.
 */
fun String.spokenDigits(): String = toList().joinToString(" ")

/** The number: Bricolage 34/700 (design-spec.md §6.8). Only this card uses it, so it isn't a Material role. */
private val HotlineNumberStyle = TextStyle(
    fontFamily = DisplayFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 34.sp,
    lineHeight = 40.sp,
)

@Preview
@Composable
private fun UlapHotlineCardPreview() {
    UlapTheme {
        UlapHotlineCard(
            title = "NCMH Crisis Hotline · available 24/7",
            number = "1553",
            callLabel = "Call 1553",
            callDescription = "Call 1 5 5 3",
            helper = "This opens your phone app. You choose when to call.",
            onCall = {},
        )
    }
}
