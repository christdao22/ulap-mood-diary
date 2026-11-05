package app.ulap.core.database.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.ulap.core.database.MANILA
import app.ulap.core.database.T0
import app.ulap.core.database.UlapDatabase
import app.ulap.core.database.checkIn
import app.ulap.core.database.model.SyncState
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CheckInDaoTest {
    private lateinit var database: UlapDatabase
    private lateinit var dao: CheckInDao

    @BeforeTest
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UlapDatabase::class.java,
        ).build()
        dao = database.checkInDao()
    }

    @AfterTest
    fun tearDown() = database.close()

    /** Days since 1970-01-01, as `LocalDate.toEpochDays()` gives them. */
    private fun epochDay(isoDate: String): Long = Instant.parse("${isoDate}T00:00:00Z").epochSeconds / 86_400

    @Test
    fun `every column comes back exactly as saved`() = runTest {
        val full = checkIn("a").copy(
            stressAfter = 5,
            trigger = Trigger.OTHER,
            triggerOther = "Traffic",
            supportUsed = SupportType.BREATHE,
            isGoodMoment = true,
            deletedAt = T0 + 1.minutes,
            syncState = SyncState.SYNCED,
            syncError = "OUT_OF_RANGE",
            syncAttempts = 3,
            serverSeq = 1042,
        )
        val empty = checkIn("b", note = null, trigger = null)
        dao.insert(full)
        dao.insert(empty)

        assertEquals(full, dao.get("a"))
        assertEquals(empty, dao.get("b"))
        assertNull(dao.get("missing"))
    }

    @Test
    fun `inserting an id twice is a bug and throws`() = runTest {
        dao.insert(checkIn("a"))
        assertFailsWith<SQLiteConstraintException> { dao.insert(checkIn("a")) }
    }

    @Test
    fun `update replaces the row with the same id`() = runTest {
        dao.insert(checkIn("a"))
        val edited = checkIn("a", note = "Done after all.").copy(stress = 4, updatedAt = T0 + 1.hours)

        assertEquals(1, dao.update(edited))
        assertEquals(edited, dao.get("a"))
        assertEquals(0, dao.update(checkIn("missing")))
    }

    @Test
    fun `soft delete keeps a tombstone, clears the journal text and queues it for sync`() = runTest {
        val synced = checkIn("a", trigger = Trigger.OTHER, triggerOther = "Traffic")
            .copy(syncState = SyncState.SYNCED, syncError = "OUT_OF_RANGE", syncAttempts = 2, serverSeq = 7)
        dao.insert(synced)
        val at = T0 + 2.hours

        assertEquals(1, dao.softDelete("a", at))

        val expected = synced.copy(
            deletedAt = at,
            updatedAt = at,
            note = null,
            triggerOther = null,
            syncState = SyncState.PENDING,
            syncError = null,
            syncAttempts = 0,
        )
        assertEquals(expected, dao.get("a"))
        // Already deleted, or never there: nothing changes.
        assertEquals(0, dao.softDelete("a", at + 1.hours))
        assertEquals(expected, dao.get("a"))
        assertEquals(0, dao.softDelete("missing", at))
    }

    @Test
    fun `a day lists its live check-ins, newest first`() = runTest {
        val morning = checkIn("morning", loggedAt = Instant.parse("2026-11-04T00:30:00Z"))
        val noon = checkIn("noon", loggedAt = Instant.parse("2026-11-04T04:00:00Z"))
        dao.insert(morning)
        dao.insert(noon)
        dao.insert(checkIn("deleted", loggedAt = Instant.parse("2026-11-04T02:00:00Z"), deletedAt = T0))
        dao.insert(checkIn("next-day", loggedAt = Instant.parse("2026-11-05T02:00:00Z")))

        assertEquals(listOf(noon, morning), dao.observeDay(epochDay("2026-11-04")).first())
    }

    @Test
    fun `the day is the one on the phone's clock when it was logged`() = runTest {
        // 15:59Z is 11:59 PM on Nov 4 in Manila (+8); 16:00Z is already Nov 5 there.
        dao.insert(checkIn("late", loggedAt = Instant.parse("2026-11-04T15:59:00Z"), tzOffsetMin = MANILA))
        dao.insert(checkIn("midnight", loggedAt = Instant.parse("2026-11-04T16:00:00Z"), tzOffsetMin = MANILA))
        // The same instant logged in New York (−5) is still Nov 4, 11 AM there.
        dao.insert(checkIn("new-york", loggedAt = Instant.parse("2026-11-04T16:00:00Z"), tzOffsetMin = -300))

        assertEquals(listOf("new-york", "late"), dao.observeDay(epochDay("2026-11-04")).first().map { it.id })
        assertEquals(listOf("midnight"), dao.observeDay(epochDay("2026-11-05")).first().map { it.id })
    }

    @Test
    fun `the day re-emits after a change`() = runTest {
        val day = epochDay("2026-11-04")
        dao.insert(checkIn("a"))
        assertEquals(listOf("a"), dao.observeDay(day).first().map { it.id })

        dao.softDelete("a", T0 + 1.minutes)

        assertEquals(emptyList(), dao.observeDay(day).first())
    }
}
