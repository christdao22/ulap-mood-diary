package app.ulap.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.ulap.shared.model.Zone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Checks the text color pairs in design-spec.md §2.5 against WCAG 2.x, so a token change that
 * breaks readability fails the build instead of shipping.
 */
class ColorContrastTest {
    private val c = LightUlapColors
    private val zones = Zone.entries.map { it.name to c.zone(it) }

    @Test
    fun `ink is at least 13 to 1 on the background, surface and every zone tint`() {
        val pairs = listOf("bg" to c.bg, "surface" to c.surface) +
            zones.map { (name, zone) -> "$name tint" to zone.tint }
        pairs.forEach { (name, background) -> assertContrast("ink on $name", c.ink, background, 13.0) }
    }

    @Test
    fun `zone text colors are at least 5_5 to 1 on surface`() {
        zones.forEach { (name, zone) -> assertContrast("$name text on surface", zone.text, c.surface, 5.5) }
    }

    @Test
    fun `white text works on every zone strong color except Yellow`() {
        zones.filter { (name, _) -> name != Zone.YELLOW.name }.forEach { (name, zone) ->
            assertContrast("white on $name strong", c.onInk, zone.strong, BODY_TEXT)
        }
    }

    @Test
    fun `every other text pair in the palette meets the 4_5 to 1 body text minimum`() {
        val pairs = listOf(
            Triple("muted on surface", c.muted, c.surface),
            Triple("muted on bg", c.muted, c.bg),
            Triple("ink3 on surface", c.ink3, c.surface),
            Triple("placeholder on surface", c.placeholder, c.surface),
            Triple("white on ink", c.onInk, c.ink),
            Triple("info on surface", c.info, c.surface),
            Triple("info on infoBg", c.info, c.infoBg),
            Triple("positiveText on positiveBg", c.positiveText, c.positiveBg),
            Triple("sampleText on sampleBg", c.sampleText, c.sampleBg),
            Triple("high stress chip", c.stressHigh.chipText, c.stressHigh.chipBackground),
            Triple("moderate stress chip", c.stressModerate.chipText, c.stressModerate.chipBackground),
            Triple("low stress chip", c.stressLow.chipText, c.stressLow.chipBackground),
        ) + zones.flatMap { (name, zone) ->
            listOf(
                Triple("$name text on $name tint", zone.text, zone.tint),
                Triple("ink2 on $name tint", c.ink2, zone.tint),
                Triple("mutedOnTint on $name tint", c.mutedOnTint, zone.tint),
            )
        }
        pairs.forEach { (name, foreground, background) -> assertContrast(name, foreground, background, BODY_TEXT) }
    }

    @Test
    fun `contrast ratio matches the WCAG reference values`() {
        assertEquals(21.0, contrast(Color.Black, Color.White), absoluteTolerance = 0.01)
        assertEquals(1.0, contrast(c.ink, c.ink), absoluteTolerance = 0.01)
    }

    private fun assertContrast(name: String, foreground: Color, background: Color, minimum: Double) {
        val ratio = contrast(foreground, background)
        assertTrue(ratio >= minimum, "$name is %.2f:1, needs %.1f:1".format(ratio, minimum))
    }

    /** WCAG 2.x contrast ratio. `luminance()` is WCAG's relative luminance for sRGB colors. */
    private fun contrast(a: Color, b: Color): Double {
        val lighter = maxOf(a.luminance(), b.luminance())
        val darker = minOf(a.luminance(), b.luminance())
        return (lighter + 0.05) / (darker + 0.05)
    }

    private companion object {
        const val BODY_TEXT = 4.5
    }
}
