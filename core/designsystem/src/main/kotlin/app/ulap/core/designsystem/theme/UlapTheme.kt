package app.ulap.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Ulap's theme: the design-spec.md §2–§4 tokens on Material 3, plus [UlapColors] for the roles
 * Material lacks. Wrap every screen and every @Preview in it. Light only in v1 (§13).
 */
@Composable
fun UlapTheme(content: @Composable () -> Unit) {
    val colors = LightUlapColors
    CompositionLocalProvider(LocalUlapColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(),
            typography = UlapTypography,
            shapes = UlapShapes,
            content = content,
        )
    }
}

/** Reads the current theme's values, the way `MaterialTheme.colorScheme` does. */
object UlapTheme {
    /** Zone, stress-band, semantic and mascot colors (design-spec.md §2). */
    val colors: UlapColors
        @Composable
        @ReadOnlyComposable
        get() = LocalUlapColors.current
}
