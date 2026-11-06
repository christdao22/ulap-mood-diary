package app.ulap.core.data.checkin

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.ulap.core.database.UlapDatabase
import app.ulap.core.database.model.SyncState
import app.ulap.core.domain.checkin.SaveResult
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import app.ulap.shared.validation.CheckInInput
import app.ulap.shared.validation.Reason
import app.ulap.shared.validation.Violation
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OfflineFirstCheckInRepositoryTest {
    /** A clock the test moves by hand. */
    private class TestClock(var now: Instant) : Clock {
        override fun now(): Instant = now

        fun advance(by: Duration) {
            now += by
        }
    }

    private val t0 = Instant.parse("2026-11-04T01:15:00Z")
    private val clock = TestClock(t0)
    private lateinit var database: UlapDatabase
    private lateinit var repository: OfflineFirstCheckInRepository

    private val input = CheckInInput(
        moodCode = "worried",
        energy = 6,
        stress = 8,
        stressAfter = null,
        trigger = Trigger.WORK,
        triggerOther = null,
        note = "  Report due tomorrow.  ",
        supportUsed = SupportType.NONE,
        isGoodMoment = false,
        loggedAt = t0,
        tzOffsetMin = 480,
    )

    @BeforeTest
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UlapDatabase::class.java,
        ).build()
        repository = OfflineFirstCheckInRepository(database, clock)
    }

    @AfterTest
    fun tearDown() = database.close()

    private suspend fun added(): String = assertIs<SaveResult.Saved>(repository.add(input)).checkIn.id

    private suspend fun row(id: String) = database.checkInDao().get(id)!!

    @Test
    fun `add stores a cleaned-up check-in, pending sync, with a new UUIDv7`() = runTest {
        val saved = assertIs<SaveResult.Saved>(repository.add(input)).checkIn

        assertTrue(Regex("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}").matches(saved.id))
        assertEquals("Report due tomorrow.", saved.note)
        assertEquals(t0, saved.createdAt)
        assertEquals(t0, saved.updatedAt)
        assertEquals(saved, repository.get(saved.id))

        val row = row(saved.id)
        assertEquals(SyncState.PENDING, row.syncState)
        assertNull(row.serverSeq)
    }

    @Test
    fun `an invalid check-in is not saved`() = runTest {
        val result = repository.add(input.copy(moodCode = "stressed"))

        assertEquals(SaveResult.Invalid(listOf(Violation("moodCode", Reason.INVALID_MOOD))), result)
        assertEquals(emptyList(), repository.observeDay(LocalDate(2026, 11, 4)).first())
    }

    @Test
    fun `edit replaces the fields, keeps the id and creation time, and queues it for sync again`() = runTest {
        val id = added()
        // The server has it, and an earlier push had trouble with it.
        database.checkInDao().update(
            row(id).copy(syncState = SyncState.SYNCED, serverSeq = 42, syncError = "FAILED", syncAttempts = 10),
        )
        clock.advance(10.minutes)

        val after = input.copy(stressAfter = 5, supportUsed = SupportType.BREATHE)
        val edited = assertIs<SaveResult.Saved>(repository.edit(id, after)).checkIn

        assertEquals(5, edited.stressAfter)
        assertEquals(SupportType.BREATHE, edited.supportUsed)
        assertEquals(t0, edited.createdAt)
        assertEquals(t0 + 10.minutes, edited.updatedAt)
        val row = row(id)
        assertEquals(SyncState.PENDING, row.syncState)
        assertNull(row.syncError)
        assertEquals(0, row.syncAttempts)
        assertEquals(42L, row.serverSeq)
    }

    @Test
    fun `editing a missing or deleted check-in finds nothing`() = runTest {
        assertEquals(SaveResult.NotFound, repository.edit("missing", input))

        val id = added()
        repository.delete(id)
        assertEquals(SaveResult.NotFound, repository.edit(id, input))
    }

    @Test
    fun `an invalid edit changes nothing`() = runTest {
        val id = added()
        val before = row(id)

        val result = repository.edit(id, input.copy(stress = 11))

        assertEquals(SaveResult.Invalid(listOf(Violation("stress", Reason.OUT_OF_RANGE))), result)
        assertEquals(before, row(id))
    }

    @Test
    fun `delete leaves a tombstone pending sync and hides the check-in`() = runTest {
        val id = added()
        clock.advance(1.minutes)

        assertTrue(repository.delete(id))

        assertNull(repository.get(id))
        assertEquals(emptyList(), repository.observeDay(LocalDate(2026, 11, 4)).first())
        val row = row(id)
        assertEquals(t0 + 1.minutes, row.deletedAt)
        assertEquals(SyncState.PENDING, row.syncState)
        assertFalse(repository.delete(id))
    }

    @Test
    fun `a day shows the check-ins logged on it`() = runTest {
        val id = added()
        // 01:15 UTC is 9:15 AM in Manila on Nov 4.
        assertEquals(listOf(id), repository.observeDay(LocalDate(2026, 11, 4)).first().map { it.id })
        assertEquals(emptyList(), repository.observeDay(LocalDate(2026, 11, 3)).first())
    }

    @Test
    fun `all shows live check-ins, newest first, and drops deleted ones`() = runTest {
        val older = assertIs<SaveResult.Saved>(repository.add(input.copy(loggedAt = t0 - 1.minutes))).checkIn.id
        val newer = added()
        val deleted = added()
        repository.delete(deleted)

        assertEquals(listOf(newer, older), repository.observeAll().first().map { it.id })
    }
}
