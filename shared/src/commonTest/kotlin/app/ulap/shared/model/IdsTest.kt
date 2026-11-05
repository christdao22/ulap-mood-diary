package app.ulap.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

class IdsTest {
    // 8-4-4-4-12 lowercase hex. Version nibble 7; variant bits 10, so the 17th hex digit is 8, 9, a or b.
    private val uuidV7 = Regex("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")

    @Test
    fun isALowercaseUuidV7() {
        repeat(1_000) {
            val id = Ids.newId()
            assertTrue(uuidV7.matches(id), id)
        }
    }

    @Test
    fun startsWithTheCurrentTimeInMilliseconds() {
        val before = Clock.System.now().toEpochMilliseconds()
        val id = Ids.newId()
        val after = Clock.System.now().toEpochMilliseconds()
        // The first 48 bits (12 hex digits, ignoring the dash) are Unix epoch milliseconds.
        val millis = id.replace("-", "").take(12).toLong(radix = 16)
        assertTrue(millis in before..after, "$millis not in $before..$after")
    }

    @Test
    fun idsAreUniqueAndSortInTheOrderTheyWereMade() {
        // Many IDs fall in the same millisecond here; they must still come out in order.
        val ids = List(10_000) { Ids.newId() }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(ids, ids.sorted())
    }
}
