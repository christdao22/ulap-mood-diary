package app.ulap.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.ulap.core.designsystem.icon.UlapIcons
import app.ulap.core.designsystem.theme.UlapShape
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme

/**
 * A 36dp three-dot button that opens a small menu, e.g. a log row's Edit and Delete
 * (design-spec.md §6.13). The touch area is still 48dp (§11): Compose widens it around small
 * buttons. Like every icon-only button it needs a [contentDescription] (§5). In a list, name the
 * item, since TalkBack reads every row's button the same way otherwise: "Edit or delete
 * check-in: Worried, 9:15 AM".
 *
 * @param items The menu's [UlapMenuItem]s. Each gets `close`, to call before it acts.
 */
@Composable
fun UlapOverflowMenu(
    contentDescription: String,
    modifier: Modifier = Modifier,
    items: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val colors = UlapTheme.colors
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(ButtonSize)) {
            Icon(
                imageVector = UlapIcons.More,
                contentDescription = contentDescription,
                modifier = Modifier.size(IconSize),
                tint = colors.muted,
            )
        }
        // Flat, with a 1dp line border instead of a shadow (design-spec.md §4 Elevation).
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = UlapShape.Button,
            containerColor = colors.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(UlapSpacing.Border, colors.line),
        ) {
            items { expanded = false }
        }
    }
}

/** One choice in an [UlapOverflowMenu]: body text in ink, at least 48dp high. */
@Composable
fun UlapMenuItem(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    DropdownMenuItem(
        text = { Text(text = text, style = MaterialTheme.typography.bodyMedium, color = UlapTheme.colors.ink) },
        onClick = onClick,
        modifier = modifier,
    )
}

private val ButtonSize = 36.dp
private val IconSize = 18.dp

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F8)
@Composable
private fun OverflowMenuPreview() {
    UlapTheme {
        UlapOverflowMenu(contentDescription = "Edit or delete check-in: Worried, 9:15 AM") { close ->
            UlapMenuItem(text = "Edit", onClick = close)
            UlapMenuItem(text = "Delete", onClick = close)
        }
    }
}
