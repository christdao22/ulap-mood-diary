package app.ulap.core.designsystem.motion

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * True when the person turned on "Remove animations" (Settings → Accessibility), which sets the
 * system animator scale to 0. Every animation in design-spec.md §9 then switches off or becomes
 * instant.
 *
 * Compose doesn't stop infinite animations on its own when the scale is 0, so components ask
 * here. It reads the setting once per composition; the next screen picks up a change.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
