package app.ulap.shared.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class SyncDtosTest {
    private val deleted = Instant.parse("2026-11-04T02:00:00Z")

    @Test
    fun aTombstoneCarriesOnlyItsIdTimesAndSeq() {
        // api-spec.md §8.3: a delete is id, updatedAt, deletedAt and serverSeq, nothing else.
        val tombstone = CheckInDto(id = "0c9d", updatedAt = deleted, deletedAt = deleted, serverSeq = 1043)
        assertEquals(
            """{"id":"0c9d","updatedAt":"2026-11-04T02:00:00.000Z","deletedAt":"2026-11-04T02:00:00.000Z",""" +
                """"serverSeq":1043}""",
            Api.json.encodeToString(tombstone),
        )
        assertTrue(tombstone.isDelete)
    }

    @Test
    fun resetRequiredIsAlwaysSentEvenWhenFalse() {
        // api-spec.md §8.3: "The field is always present".
        val page =
            PullResponse(emptyList(), nextSince = 7, hasMore = false, resetRequired = false, serverTime = deleted)
        assertTrue(Api.json.encodeToString(page).contains("\"resetRequired\":false"))
    }

    @Test
    fun anUnknownEnumNameStillReads() {
        // Kept as strings, so the server can reject one change (INVALID_ENUM) instead of the request.
        val change = Api.json.decodeFromString<CheckInDto>(
            """{"id":"a","trigger":"WEATHER","supportUsed":"NAP","updatedAt":"2026-11-04T02:00:00.000Z"}""",
        )
        assertEquals("WEATHER", change.trigger)
        assertEquals("NAP", change.supportUsed)
        assertFalse(change.isDelete)
    }

    @Test
    fun toStringHidesTheJournalText() {
        val change = CheckInDto(id = "a", note = "Private words", triggerOther = "My reason", updatedAt = deleted)
        assertFalse(change.toString().contains("Private"))
        assertFalse(change.toString().contains("reason"))
    }
}
