package app.ulap.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Ulap's theme. For now it is Material 3's defaults. The Phase 2 "Theme and components" tasks
 * fill in Color.kt, Type.kt and Shape.kt from design-spec.md §2–§4; screens don't change.
 */
@Composable
fun UlapTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
