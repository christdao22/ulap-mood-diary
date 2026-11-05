package app.ulap.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.ulap.shared.model.Zone

/** One zone's three roles (design-spec.md §2.2). */
@Immutable
data class ZoneColors(
    /** Dots, stripes, the slider accent and the selected tile. */
    val strong: Color,
    /** Labels on white or on the tint. */
    val text: Color,
    /** Mood tile and response screen backgrounds. Text on it is `ink`. */
    val tint: Color,
)

/** One stress band's colors. Reports only (design-spec.md §2.3). */
@Immutable
data class StressBandColors(val bar: Color, val chipBackground: Color, val chipText: Color)

/**
 * Every color token in design-spec.md §2, by role. Read it with `UlapTheme.colors`.
 *
 * Tokens are named by role, not by hue, so dark mode (§13) is a second instance of this class.
 * Material's [ColorScheme] has no slot for zones, stress bands or the mascot, which is why this
 * class exists alongside it.
 */
@Immutable
class UlapColors(
    // Neutrals (§2.1)
    val bg: Color,
    val surface: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val muted: Color,
    val placeholder: Color,
    val line: Color,
    val lineStrong: Color,
    val lineSoft: Color,
    val outlineButton: Color,
    /** Secondary text on a zone tint: the mood tile's "Stress N" (design-spec.md §6.1). */
    val mutedOnTint: Color,
    /** Text on `ink` and on the Red, Blue and Green strong colors. Never on Yellow strong (§2.2). */
    val onInk: Color,
    // Zones (§2.2)
    val red: ZoneColors,
    val blue: ZoneColors,
    val yellow: ZoneColors,
    val green: ZoneColors,
    // Stress bands (§2.3)
    val stressHigh: StressBandColors,
    val stressModerate: StressBandColors,
    val stressLow: StressBandColors,
    // Semantic (§2.4)
    val positiveText: Color,
    val positiveBg: Color,
    val info: Color,
    val infoBg: Color,
    val infoBorder: Color,
    val sampleBg: Color,
    val sampleText: Color,
    // Ulap the mascot (§2.4, §8). The body stays white in dark mode too (§13).
    val ulapBody: Color,
    val heart: Color,
    val cheek: Color,
    val ulapStroke: Color,
    /** The calm expression's leaf; its vein is Green strong (§8.3). */
    val leaf: Color,
) {
    fun zone(zone: Zone): ZoneColors = when (zone) {
        Zone.RED -> red
        Zone.BLUE -> blue
        Zone.YELLOW -> yellow
        Zone.GREEN -> green
    }
}

/** The light palette, the only one in v1 (design-spec.md §2). */
val LightUlapColors = UlapColors(
    bg = Color(0xFFF4F5F8),
    surface = Color(0xFFFFFFFF),
    ink = Color(0xFF1B1F2A),
    ink2 = Color(0xFF2E3443),
    ink3 = Color(0xFF3B4252),
    muted = Color(0xFF5B6272),
    placeholder = Color(0xFF6B7282),
    line = Color(0xFFE1E4EA),
    lineStrong = Color(0xFFC9CED8),
    lineSoft = Color(0xFFEEF0F4),
    outlineButton = Color(0xFFA9B1BF),
    mutedOnTint = Color(0xFF4A5162),
    onInk = Color(0xFFFFFFFF),
    red = ZoneColors(strong = Color(0xFFC7392F), text = Color(0xFFA82E26), tint = Color(0xFFFCECEA)),
    blue = ZoneColors(strong = Color(0xFF2F62C9), text = Color(0xFF2754B0), tint = Color(0xFFE8EFFC)),
    yellow = ZoneColors(strong = Color(0xFFE9AE1F), text = Color(0xFF7F5A00), tint = Color(0xFFFFF5D9)),
    green = ZoneColors(strong = Color(0xFF267A4C), text = Color(0xFF22704A), tint = Color(0xFFE3F4EA)),
    stressHigh = StressBandColors(
        bar = Color(0xFFC7392F),
        chipBackground = Color(0xFFFCECEA),
        chipText = Color(0xFFA82E26),
    ),
    stressModerate = StressBandColors(
        bar = Color(0xFFD9822B),
        chipBackground = Color(0xFFFDEFE2),
        chipText = Color(0xFF8A4A10),
    ),
    stressLow = StressBandColors(
        bar = Color(0xFF267A4C),
        chipBackground = Color(0xFFE3F4EA),
        chipText = Color(0xFF1B5E3B),
    ),
    positiveText = Color(0xFF1B5E3B),
    positiveBg = Color(0xFFE3F4EA),
    info = Color(0xFF2754B0),
    infoBg = Color(0xFFE6EEFB),
    infoBorder = Color(0xFFC9D6F2),
    sampleBg = Color(0xFFFFF5D9),
    sampleText = Color(0xFF7F5A00),
    ulapBody = Color(0xFFFFFFFF),
    heart = Color(0xFFE7848A),
    cheek = Color(0xFFF6B4AE).copy(alpha = 0.7f),
    ulapStroke = Color(0xFFC3CAD6),
    leaf = Color(0xFF3E9A6A),
)

/**
 * Maps [UlapColors] onto Material 3's roles, so stock Material components (Scaffold, TextField,
 * Switch) pick up Ulap's colors without per-call overrides.
 *
 * design-spec.md §12 names six roles (primary = ink, onPrimary = white, background, surface,
 * outline, outlineVariant). The rest are filled from the same neutrals, so none of Material's
 * purple baseline shows through. Specific choices:
 * - `error` is `ink`: errors use ink text with an icon, never Red (§2.2).
 * - Every surface container is `surface` and `surfaceTint` is `surface`: Ulap is flat (§4), so
 *   tonal elevation must not tint anything.
 */
fun UlapColors.toMaterialColorScheme(): ColorScheme = lightColorScheme(
    primary = ink,
    onPrimary = onInk,
    primaryContainer = lineSoft,
    onPrimaryContainer = ink,
    inversePrimary = lineStrong,
    secondary = ink3,
    onSecondary = onInk,
    secondaryContainer = lineSoft,
    onSecondaryContainer = ink,
    tertiary = info,
    onTertiary = onInk,
    tertiaryContainer = infoBg,
    onTertiaryContainer = info,
    background = bg,
    onBackground = ink,
    surface = surface,
    onSurface = ink,
    surfaceVariant = lineSoft,
    onSurfaceVariant = muted,
    surfaceTint = surface,
    inverseSurface = ink,
    inverseOnSurface = surface,
    error = ink,
    onError = onInk,
    errorContainer = lineSoft,
    onErrorContainer = ink,
    outline = lineStrong,
    outlineVariant = line,
    surfaceBright = surface,
    surfaceDim = bg,
    surfaceContainerLowest = surface,
    surfaceContainerLow = surface,
    surfaceContainer = surface,
    surfaceContainerHigh = surface,
    surfaceContainerHighest = surface,
)

/**
 * The palette for the current screen. The one CompositionLocal the lint rules allow (root
 * build.gradle.kts, Spotless). Static, because the palette only changes with the whole theme.
 */
internal val LocalUlapColors = staticCompositionLocalOf { LightUlapColors }
