package app.ulap.core.ui

import app.ulap.core.model.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppLanguageTest {
    @Test
    fun `each language maps back from its tag`() {
        AppLanguage.entries.forEach { assertEquals(it, AppLanguage.fromTag(it.tag)) }
    }

    @Test
    fun `a region suffix still finds the language`() {
        assertEquals(AppLanguage.FILIPINO, AppLanguage.fromTag("fil-PH"))
        assertEquals(AppLanguage.BISAYA, AppLanguage.fromTag("ceb-PH"))
    }

    @Test
    fun `unknown or missing tags give null`() {
        assertNull(AppLanguage.fromTag("ja"))
        assertNull(AppLanguage.fromTag(null))
    }
}
