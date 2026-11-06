package app.ulap.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.ulap.core.designsystem.icon.UlapIcons
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme

/**
 * A round 48dp button with an up arrow that floats over a long list and jumps back to its top
 * (Logs, design-spec.md §7.6). Flat, with a line border instead of a shadow (§4 Elevation).
 * Icon-only, so it needs a [contentDescription] (§5).
 */
@Composable
fun ScrollToTopButton(onClick: () -> Unit, contentDescription: String, modifier: Modifier = Modifier) {
    val colors = UlapTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier.size(ButtonSize),
        shape = CircleShape,
        color = colors.surface,
        border = BorderStroke(UlapSpacing.Border, colors.lineStrong),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = UlapIcons.Up,
                contentDescription = contentDescription,
                modifier = Modifier.size(IconSize),
                tint = colors.ink,
            )
        }
    }
}

private val ButtonSize = 48.dp
private val IconSize = 22.dp

@Preview
@Composable
private fun ScrollToTopButtonPreview() {
    UlapTheme { ScrollToTopButton(onClick = {}, contentDescription = "Back to top") }
}
