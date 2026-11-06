package app.ulap.core.designsystem.component

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.ulap.core.designsystem.theme.UlapTheme

/**
 * Asks before something that can't be undone, e.g. "Delete this check-in? This can't be undone."
 * with Delete · Keep (design-spec.md §7.6). Flat on `surface` (§4). Tapping outside or Back is
 * the same as [dismissLabel]: nothing happens.
 */
@Composable
fun UlapConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UlapTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { DialogButton(text = confirmLabel, onClick = onConfirm) },
        modifier = modifier,
        dismissButton = { DialogButton(text = dismissLabel, onClick = onDismiss) },
        title = { Text(text = title, style = MaterialTheme.typography.titleMedium, color = colors.ink) },
        text = { Text(text = text, style = MaterialTheme.typography.bodyMedium, color = colors.ink3) },
        shape = MaterialTheme.shapes.large,
        containerColor = colors.surface,
        tonalElevation = 0.dp,
    )
}

@Composable
private fun DialogButton(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = ButtonHeight),
        colors = ButtonDefaults.textButtonColors(contentColor = UlapTheme.colors.ink),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

private val ButtonHeight = 48.dp

@Preview
@Composable
private fun ConfirmDialogPreview() {
    UlapTheme {
        UlapConfirmDialog(
            title = "Delete this check-in?",
            text = "This can't be undone.",
            confirmLabel = "Delete",
            dismissLabel = "Keep",
            onConfirm = {},
            onDismiss = {},
        )
    }
}
