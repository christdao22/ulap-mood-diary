package app.ulap.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.ulap.core.database.T0
import app.ulap.core.database.UlapDatabase
import app.ulap.core.database.checkIn
import app.ulap.core.database.model.PullSeenEntity
import app.ulap.core.database.model.SyncPositionEntity
import app.ulap.core.database.model.SyncState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncDaoTest {
    private lateinit var database: UlapDatabase
    private lateinit var checkIns: CheckInDao
    private lateinit var dao: SyncDao

    @BeforeTest
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UlapDatabase::class.java,
        ).build()
        checkIns = database.checkInDao()
        dao = database.syncDao()
    }

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun `the upload batch is the oldest waiting changes, without stopped or synced rows`() = runTest {
        checkIns.insert(checkIn("new").copy(updatedAt = T0 + 2.minutes))
        checkIns.insert(checkIn("old").copy(updatedAt = T0))
        checkIns.insert(checkIn("stuck").copy(syncError = "FAILED"))
        checkIns.insert(checkIn("synced").copy(syncState = SyncState.SYNCED))

        assertEquals(listOf("old", "new"), dao.uploadBatch(limit = 10).map { it.id })
        assertEquals(listOf("old"), dao.uploadBatch(limit = 1).map { it.id })
        assertEquals(2, dao.observeWaitingCount().first())
        assertEquals(1, dao.observeStuckCount().first())
    }

    @Test
    fun `marking synced keeps the server's number`() = runTest {
        checkIns.insert(checkIn("a").copy(syncAttempts = 3))

        assertEquals(1, dao.markSynced("a", T0, serverSeq = 42))

        val row = checkIns.get("a")!!
        assertEquals(SyncState.SYNCED, row.syncState)
        assertEquals(42L, row.serverSeq)
        assertEquals(0, row.syncAttempts)
    }

    @Test
    fun `a row edited while its push was on the way stays pending`() = runTest {
        checkIns.insert(checkIn("a").copy(updatedAt = T0 + 1.minutes))

        // The push carried the version from T0; the row has a newer one now.
        assertEquals(0, dao.markSynced("a", T0, serverSeq = 42))
        assertEquals(0, dao.markRejected("a", T0, "OUT_OF_RANGE"))
        assertEquals(0, dao.countInternalError("a", T0, maxAttempts = 10))

        val row = checkIns.get("a")!!
        assertEquals(SyncState.PENDING, row.syncState)
        assertNull(row.syncError)
        assertEquals(0, row.syncAttempts)
    }

    @Test
    fun `a rejected row stops trying until try again`() = runTest {
        checkIns.insert(checkIn("a"))

        dao.markRejected("a", T0, "INVALID_MOOD")
        assertEquals("INVALID_MOOD", checkIns.get("a")!!.syncError)
        assertEquals(emptyList(), dao.uploadBatch(limit = 10))

        assertEquals(1, dao.retryStuck())
        assertNull(checkIns.get("a")!!.syncError)
        assertEquals(listOf("a"), dao.uploadBatch(limit = 10).map { it.id })
    }

    @Test
    fun `the tenth internal error stops the row with FAILED`() = runTest {
        checkIns.insert(checkIn("a"))

        repeat(9) { dao.countInternalError("a", T0, maxAttempts = 10) }
        assertEquals(9, checkIns.get("a")!!.syncAttempts)
        assertNull(checkIns.get("a")!!.syncError)

        dao.countInternalError("a", T0, maxAttempts = 10)
        assertEquals("FAILED", checkIns.get("a")!!.syncError)

        // Once stopped, more errors don't count: the row isn't in any push.
        assertEquals(0, dao.countInternalError("a", T0, maxAttempts = 10))
        dao.retryStuck()
        assertEquals(0, checkIns.get("a")!!.syncAttempts)
    }

    @Test
    fun `queueing for a new account takes the live rows, as new`() = runTest {
        checkIns.insert(checkIn("a").copy(syncState = SyncState.SYNCED, serverSeq = 7, syncError = "FAILED"))
        checkIns.insert(checkIn("gone", deletedAt = T0))

        dao.deleteTombstones()
        dao.queueLiveForUpload()

        val row = checkIns.get("a")!!
        assertEquals(SyncState.PENDING, row.syncState)
        assertNull(row.serverSeq)
        assertNull(row.syncError)
        assertNull(checkIns.get("gone"))
        assertEquals(1, dao.countLive())
    }

    @Test
    fun `the resync's end deletes synced rows the server didn't send, and keeps pending ones`() = runTest {
        checkIns.insert(checkIn("seen").copy(syncState = SyncState.SYNCED))
        checkIns.insert(checkIn("purged").copy(syncState = SyncState.SYNCED))
        checkIns.insert(checkIn("pending"))
        dao.addSeen(listOf(PullSeenEntity("seen"), PullSeenEntity("seen")))

        assertEquals(1, dao.deleteSyncedNotSeen())

        assertNull(checkIns.get("purged"))
        assertEquals(setOf("seen", "pending"), checkIns.observeAll().first().map { it.id }.toSet())
    }

    @Test
    fun `the position is one row, saved whole`() = runTest {
        assertNull(dao.position())

        dao.savePosition(SyncPositionEntity(ownerUserId = "user-1", lastPullSeq = 10))
        dao.savePosition(dao.position()!!.copy(lastPullSeq = 20, resetting = true))

        assertEquals(SyncPositionEntity(ownerUserId = "user-1", lastPullSeq = 20, resetting = true), dao.position())
        assertEquals(dao.position(), dao.observePosition().first())
    }
}
