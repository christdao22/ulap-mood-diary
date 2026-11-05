package app.ulap.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.ulap.core.designsystem.R

// Both fonts are Google Fonts' variable files (res/font/, licenses in res/font/LICENSES/,
// README §18): one file holds every weight, picked here with FontVariation (API 26+, our minSdk).
// If a font can't load, Android falls back to system sans-serif, as design-spec.md §3 asks.

/** Bricolage's optical-size axis, set for the 19–26sp headings it draws (design-spec.md §3). */
private val DisplayOpticalSize = 24.sp

/** Headings: Bricolage Grotesque, bold only (design-spec.md §3). */
internal val DisplayFontFamily = FontFamily(
    Font(
        resId = R.font.bricolage_grotesque,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(FontWeight.Bold.weight),
            FontVariation.opticalSizing(DisplayOpticalSize),
        ),
    ),
)

/**
 * Everything else: Figtree at 400, 500 and 600 (design-spec.md §3), plus 700 for a bold number
 * inside running text, like the slider's "**8** / 10" (§6.4).
 */
internal val BodyFontFamily = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(
            resId = R.font.figtree,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

/**
 * design-spec.md §3 mapped onto Material 3 roles, following §12. Line heights are the spec's
 * multipliers times the size, rounded to whole sp. Screens use only these roles:
 *
 * | Spec role  | Material role    | Use                                               |
 * |------------|------------------|---------------------------------------------------|
 * | `displayL` | `headlineMedium` | Selected mood name, screen titles                 |
 * | `displayM` | `headlineSmall`  | Response and after-check titles                   |
 * | `displayS` | `titleMedium`    | Greeting bubble title, stat tile values           |
 * | `bodyL`    | `bodyLarge`      | Response body, button labels                      |
 * | `body`     | `bodyMedium`     | Default text                                      |
 * | `label`    | `labelLarge`     | Mood tile name, section headings, slider labels   |
 * | `small`    | `bodySmall`      | Notes, helper text, chips                         |
 * | `overline` | `labelMedium`    | Zone label above the mood name                    |
 * | `caption`  | `labelSmall`     | Tile meta ("Stress 9"), axis labels               |
 *
 * §12's sketch leaves out `overline` and the `label` line height. Both come from the §3 table.
 * `overline` is UPPERCASE in the spec, but a TextStyle can't change case: the caller uppercases
 * the string (with the app's locale), so TalkBack still reads the word, not the letters.
 */
val UlapTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 29.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 25.sp,
        lineHeight = 30.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 23.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.04.em,
    ),
    labelSmall = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)

/**
 * Tabular figures: every digit is the same width, so numbers in stats, sliders and charts don't
 * shift as they change (design-spec.md §3).
 */
fun TextStyle.withTabularNumbers(): TextStyle = copy(fontFeatureSettings = "tnum")
