package app.ulap.shared.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrashReportRulesTest {
    @Test
    fun `the journal fields, credentials and email are sensitive in any spelling`() {
        listOf(
            "note", "Note", "triggerOther", "trigger_other", "Trigger-Other", "password", "newPassword",
            "email", "token", "accessToken", "refresh_token", "idToken", "Authorization", "cookie",
        ).forEach { assertTrue(CrashReportRules.isSensitiveKey(it), it) }
    }

    @Test
    fun `ordinary fields are kept`() {
        listOf("moodCode", "stress", "requestId", "screen", "notes_count", "trigger").forEach {
            assertFalse(CrashReportRules.isSensitiveKey(it), it)
        }
    }

    @Test
    fun `scrub drops sensitive keys at any depth, in maps and lists`() {
        val scrubbed = CrashReportRules.scrub(
            mapOf(
                "requestId" to "req_1",
                "note" to "a private note",
                "change" to mapOf("moodCode" to "worried", "triggerOther" to "the commute"),
                "changes" to listOf(mapOf("note" to "another", "stress" to 8)),
            ),
        )

        assertEquals(
            mapOf(
                "requestId" to "req_1",
                "change" to mapOf("moodCode" to "worried"),
                "changes" to listOf(mapOf("stress" to 8)),
            ),
            scrubbed,
        )
    }
}
