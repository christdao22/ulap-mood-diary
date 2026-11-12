package app.ulap.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.ulap.core.designsystem.icon.GoogleLogo
import app.ulap.core.designsystem.theme.UlapShape
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme

// The button types in design-spec.md §6.6. Each one sets a minimum height, never a fixed one, so
// a label up to 40% longer in Filipino or Bisaya wraps to a second line instead of being cut off
// (§10.5). At most one primary button per screen; when stress is 8 or more, only the primary.

/** The one main action on a screen: ink fill, white text 16/600, 52dp high. */
@Composable
fun UlapPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = UlapTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = PrimaryHeight),
        enabled = enabled,
        shape = UlapShape.Button,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.ink,
            contentColor = colors.onInk,
            disabledContainerColor = colors.lineSoft,
            disabledContentColor = colors.muted,
        ),
    ) {
        ButtonLabel(text, MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
    }
}

/** A quieter choice next to the primary: transparent, 1dp outline, ink text 15/500, 48dp high. */
@Composable
fun UlapSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = UlapTheme.colors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = SecondaryHeight),
        enabled = enabled,
        shape = UlapShape.Button,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink, disabledContentColor = colors.muted),
        border = BorderStroke(UlapSpacing.Border, colors.outlineButton),
    ) {
        ButtonLabel(text, MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium))
    }
}

/** The strong outline, used for "Check in": surface fill, 1.5dp ink border, 48dp high. */
@Composable
fun UlapTertiaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = UlapTheme.colors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = SecondaryHeight),
        enabled = enabled,
        shape = UlapShape.Button,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.surface,
            contentColor = colors.ink,
            disabledContentColor = colors.muted,
        ),
        border = BorderStroke(StrongBorder, if (enabled) colors.ink else colors.lineStrong),
    ) {
        ButtonLabel(text, MaterialTheme.typography.labelLarge)
    }
}

/**
 * No border and no fill, ink2 text, 48dp high (design-spec.md §6.6). A quiet way out that sits
 * under a primary without competing with it, e.g. "Maybe later" under "Breathe with me".
 */
@Composable
fun UlapQuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = SecondaryHeight),
        shape = UlapShape.Button,
        colors = ButtonDefaults.textButtonColors(contentColor = UlapTheme.colors.ink2),
    ) {
        ButtonLabel(text, MaterialTheme.typography.labelLarge)
    }
}

/**
 * An underlined link in `info`, used for "Reset to default". The spec asks for at least 44dp of
 * touch area; this uses Android's 48dp minimum (§11).
 */
@Composable
fun UlapTextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = SecondaryHeight),
        colors = ButtonDefaults.textButtonColors(contentColor = UlapTheme.colors.info),
    ) {
        ButtonLabel(text, MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline))
    }
}

/**
 * A 44dp square icon button on `surface`, with a 48dp touch area (§11). [contentDescription] is
 * required: an icon alone means nothing to TalkBack (§5), e.g. "Back to moods".
 */
@Composable
fun UlapIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        shape = MaterialTheme.shapes.medium,
        color = UlapTheme.colors.surface,
        contentColor = UlapTheme.colors.ink,
    ) {
        Box(Modifier.size(IconButtonSize), contentAlignment = Alignment.Center) { icon() }
    }
}

/**
 * "Continue with Google" in the shape of Google's standard button (design-spec.md §7.9): the
 * neutral theme, a white fill, a thin outline and the four-color G on the left. It isn't a
 * primary, so it can sit on a screen that already has one.
 */
@Composable
fun UlapGoogleButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = UlapTheme.colors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = SecondaryHeight),
        enabled = enabled,
        shape = UlapShape.Button,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.surface,
            contentColor = colors.ink,
            disabledContainerColor = colors.surface,
            disabledContentColor = colors.muted,
        ),
        border = BorderStroke(UlapSpacing.Border, colors.outlineButton),
    ) {
        Icon(imageVector = GoogleLogo, contentDescription = null, tint = Color.Unspecified)
        Spacer(Modifier.width(GoogleLogoGap))
        ButtonLabel(text, MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun ButtonLabel(text: String, style: TextStyle) {
    Text(text = text, style = style, textAlign = TextAlign.Center)
}

private val PrimaryHeight = 52.dp
private val SecondaryHeight = 48.dp
private val StrongBorder = 1.5.dp
private val IconButtonSize = 44.dp
private val GoogleLogoGap = 10.dp

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F8, widthDp = 360)
@Composable
private fun ButtonsPreview() {
    UlapTheme {
        Column(
            modifier = Modifier.padding(UlapSpacing.M),
            verticalArrangement = Arrangement.spacedBy(UlapSpacing.S),
        ) {
            UlapPrimaryButton(text = "Breathe with me", onClick = {}, modifier = Modifier.fillMaxWidth())
            UlapSecondaryButton(text = "Maybe later", onClick = {}, modifier = Modifier.fillMaxWidth())
            UlapTertiaryButton(text = "Check in", onClick = {}, modifier = Modifier.fillMaxWidth())
            UlapGoogleButton(text = "Continue with Google", onClick = {}, modifier = Modifier.fillMaxWidth())
            UlapTextLink(text = "Reset to default", onClick = {})
            UlapIconButton(onClick = {}, contentDescription = "Back to moods") { Text("‹") }
        }
    }
}
