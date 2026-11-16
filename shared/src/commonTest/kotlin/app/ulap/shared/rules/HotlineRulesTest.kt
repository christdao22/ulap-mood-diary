package app.ulap.shared.rules

import app.ulap.shared.api.HotlineInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HotlineRulesTest {
    @Test
    fun `the built-in number is NCMH 1553`() {
        assertEquals("1553", HotlineRules.BUILT_IN_NUMBER)
    }

    @Test
    fun `meta sending the built-in number keeps nothing saved`() {
        assertNull(HotlineRules.savedAfter(HotlineInfo.NCMH, saved = null))
    }

    @Test
    fun `meta going back to the built-in number clears the saved one`() {
        assertNull(HotlineRules.savedAfter(HotlineInfo.NCMH, saved = "1554"))
    }

    @Test
    fun `a new dialable number from meta is saved, trimmed`() {
        assertEquals("1554", HotlineRules.savedAfter(HotlineInfo("NCMH Crisis Hotline", " 1554 "), saved = null))
        assertEquals("+639178998727", HotlineRules.savedAfter(HotlineInfo("NCMH", "+639178998727"), saved = "1554"))
    }

    @Test
    fun `a number that is not dialable is ignored and the saved one stays`() {
        listOf("", "12", "15 53", "tel-1553", "1553;", "abc", "+", "1234567890123456").forEach { bad ->
            assertNull(HotlineRules.savedAfter(HotlineInfo("NCMH", bad), saved = null), bad)
            assertEquals("1554", HotlineRules.savedAfter(HotlineInfo("NCMH", bad), saved = "1554"), bad)
        }
    }

    @Test
    fun `with nothing saved the built-in number shows`() {
        assertEquals("1553", HotlineRules.numberToShow(null))
    }

    @Test
    fun `a saved number shows`() {
        assertEquals("1554", HotlineRules.numberToShow("1554"))
    }

    @Test
    fun `a saved number that is not dialable falls back to the built-in one`() {
        assertEquals("1553", HotlineRules.numberToShow("call us"))
    }
}
