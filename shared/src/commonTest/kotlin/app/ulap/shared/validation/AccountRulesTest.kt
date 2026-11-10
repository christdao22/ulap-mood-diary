package app.ulap.shared.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountRulesTest {
    @Test
    fun emailsAreTrimmedAndLowerCased() {
        assertEquals("ana@example.com", AccountRules.normalizeEmail("  Ana@Example.COM "))
    }

    @Test
    fun thingsThatArentAddressesAreRejected() {
        listOf("", "ana", "ana@", "@example.com", "ana@example", "a na@example.com", "a@b.c" + "d".repeat(254))
            .forEach { assertNull(AccountRules.normalizeEmail(it), it) }
    }

    @Test
    fun displayNamesAreOneToFiftyCharacters() {
        assertTrue(AccountRules.isValidDisplayName("Christian"))
        assertTrue(AccountRules.isValidDisplayName("a".repeat(50)))
        assertFalse(AccountRules.isValidDisplayName("   "))
        assertFalse(AccountRules.isValidDisplayName("a".repeat(51)))
    }

    @Test
    fun languageTagsMapToTheThreeLanguages() {
        assertEquals("fil", AccountRules.languageFromTag("fil-PH"))
        assertEquals("fil", AccountRules.languageFromTag("tl"))
        assertEquals("ceb", AccountRules.languageFromTag("ceb"))
        assertEquals("en", AccountRules.languageFromTag("en-US;q=0.9"))
        assertNull(AccountRules.languageFromTag("es"))
        assertNull(AccountRules.languageFromTag(null))
    }
}
