package app.ulap.server.auth

import app.ulap.server.common.ApiException
import app.ulap.shared.api.ErrorCode
import app.ulap.shared.api.ErrorDetail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Argon2id with the parameters of api-spec.md §6.3, the common-password list and the policy around them. */
class PasswordsTest {
    @Test
    fun hashesAreArgon2idWithTheSpecParameters() {
        val hasher = Argon2PasswordHasher()

        val hash = hasher.hash(GOOD_PASSWORD)

        assertTrue(hash.startsWith("\$argon2id\$v=19\$m=65536,t=3,p=1\$"), hash)
        assertTrue(hasher.verify(hash, GOOD_PASSWORD))
        assertFalse(hasher.verify(hash, "kalma-sa-ulap-2026"))
        // A fresh salt every time: the same password never hashes the same way twice.
        assertNotEquals(hash, hasher.hash(GOOD_PASSWORD))
    }

    @Test
    fun theCommonPasswordListLoadsFromResources() {
        val common = CommonPasswords.load()

        assertTrue("1234567890" in common)
        assertTrue("1234567890 " in common)
        assertFalse(GOOD_PASSWORD in common)
    }

    @Test
    fun thePolicyReportsTheRuleThatFailed() {
        val policy = PasswordPolicy(Argon2PasswordHasher(memoryKb = 4096, iterations = 1), CommonPasswords.load())

        policy.requireStrong(GOOD_PASSWORD, "ana@example.com")
        val problems = mapOf(
            "short" to "TOO_SHORT",
            "a".repeat(129) to "TOO_LONG",
            "1234567890" to "COMMON",
            "ana@example.com" to "SAME_AS_EMAIL",
        )
        problems.forEach { (password, problem) ->
            val e = assertFailsWith<ApiException> { policy.requireStrong(password, "ana@example.com") }
            assertEquals(ErrorCode.WEAK_PASSWORD, e.code)
            assertEquals(listOf(ErrorDetail("password", problem)), e.details)
        }
    }

    @Test
    fun verifyingAgainstNoHashIsAlwaysFalse() {
        val policy = PasswordPolicy(Argon2PasswordHasher(memoryKb = 4096, iterations = 1), CommonPasswords(emptySet()))

        assertFalse(policy.verify(null, GOOD_PASSWORD))
        assertTrue(policy.verify(policy.hash(GOOD_PASSWORD), GOOD_PASSWORD))
    }
}
