package app.ulap.shared.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PasswordRulesTest {
    private val common = setOf("1234567890", "qwertyuiop")

    @Test
    fun aLongPassphrasePasses() {
        assertNull(PasswordRules.check("Kalma-sa-Ulap-2026", "ana@example.com") { it in common })
    }

    @Test
    fun shortAndLongPasswordsAreTurnedDown() {
        assertEquals(PasswordRules.Problem.TOO_SHORT, PasswordRules.check("123456789"))
        assertNull(PasswordRules.check("1234567890a"))
        assertEquals(PasswordRules.Problem.TOO_LONG, PasswordRules.check("a".repeat(129)))
        assertNull(PasswordRules.check("a".repeat(128)))
    }

    @Test
    fun aCommonPasswordIsTurnedDownWhenTheListKnowsIt() {
        assertEquals(PasswordRules.Problem.COMMON, PasswordRules.check("1234567890") { it in common })
        // Without a list, only the other rules apply (the app before it talks to the server).
        assertNull(PasswordRules.check("1234567890"))
    }

    @Test
    fun thePasswordMayNotBeTheEmail() {
        assertEquals(PasswordRules.Problem.SAME_AS_EMAIL, PasswordRules.check("Ana@Example.com ", "ana@example.com"))
        assertNull(PasswordRules.check("ana@example.com", null))
    }

    @Test
    fun noSymbolsOrCapitalsAreForced() {
        assertNull(PasswordRules.check("all lowercase words here"))
    }
}
