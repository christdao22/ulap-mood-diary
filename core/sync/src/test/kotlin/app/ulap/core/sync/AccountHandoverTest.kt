package app.ulap.core.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.ulap.core.database.SyncLock
import app.ulap.core.database.UlapDatabase
import app.ulap.core.database.model.SyncPositionEntity
import app.ulap.core.database.model.SyncState
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The first sign-in from guest mode (api-spec.md §8.5) and the answers to its question. */
@RunWith(RobolectricTestRunner::class)
class AccountHandoverTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        UlapDatabase::class.java,
    ).build()
    private val checkIns = database.checkInDao()
    private val dao = database.syncDao()
    private val lock = SyncLock()
    private val scheduler = FakeScheduler()
    private val handover = AccountHandover(database, lock)
    private val repository = RoomSyncRepository(
        database,
        scheduler,
        object : Connectivity {
            override val online: Flow<Boolean> = flowOf(true)
        },
        lock,
    )

    @AfterTest
    fun tearDown() = database.close()

    private suspend fun position() = dao.position() ?: SyncPositionEntity()

    @Test
    fun `a new account takes every live check-in, as new, and pulls from 0`() = runTest {
        checkIns.insert(row("guest"))
        checkIns.insert(row("synced elsewhere", syncState = SyncState.SYNCED, serverSeq = 9))
        checkIns.insert(row("deleted", deletedAt = T0))

        handover.beforeSignIn("user-1", isNewUser = true)

        assertEquals(SyncPositionEntity(ownerUserId = "user-1"), position())
        assertEquals(2, dao.observeWaitingCount().first())
        assertNull(checkIns.get("synced elsewhere")!!.serverSeq)
        assertNull(checkIns.get("deleted"), "the new account never had it, so there's no delete to send")
    }

    @Test
    fun `an account with check-ins elsewhere asks first when this phone has some`() = runTest {
        checkIns.insert(row("guest"))

        handover.beforeSignIn("user-1", isNewUser = false)

        assertTrue(position().askingToAdd)
        assertTrue(repository.status.first().askToAddCheckIns)
        assertEquals(SyncState.PENDING, checkIns.get("guest")!!.syncState)
    }

    @Test
    fun `add them queues this phone's check-ins and starts a sync`() = runTest {
        checkIns.insert(row("guest", syncState = SyncState.SYNCED))
        handover.beforeSignIn("user-1", isNewUser = false)

        repository.answerAddCheckIns(add = true)

        assertFalse(position().askingToAdd)
        assertEquals(1, dao.observeWaitingCount().first())
        assertEquals(1, scheduler.requests)
    }

    @Test
    fun `don't add deletes this phone's check-ins and still starts the pull`() = runTest {
        checkIns.insert(row("guest"))
        handover.beforeSignIn("user-1", isNewUser = false)

        repository.answerAddCheckIns(add = false)

        assertNull(checkIns.get("guest"))
        assertFalse(position().askingToAdd)
        assertEquals(1, scheduler.requests)
        // A second answer (a double tap) changes nothing.
        repository.answerAddCheckIns(add = true)
        assertEquals(1, scheduler.requests)
    }

    @Test
    fun `an account with check-ins elsewhere and none here just pulls`() = runTest {
        checkIns.insert(row("deleted", deletedAt = T0))

        handover.beforeSignIn("user-1", isNewUser = false)

        assertEquals(SyncPositionEntity(ownerUserId = "user-1"), position())
        assertNull(checkIns.get("deleted"))
    }

    @Test
    fun `signing in again to the same account carries on where sync was`() = runTest {
        checkIns.insert(row("mine", syncState = SyncState.SYNCED))
        checkIns.insert(row("written while signed out"))
        dao.savePosition(SyncPositionEntity(ownerUserId = "user-1", lastPullSeq = 40))

        handover.beforeSignIn("user-1", isNewUser = false)

        assertEquals(40L, position().lastPullSeq)
        assertFalse(position().askingToAdd)
        assertEquals(1, dao.observeWaitingCount().first())
    }

    @Test
    fun `another account's check-ins on this phone count as this phone's`() = runTest {
        checkIns.insert(row("first account's", syncState = SyncState.SYNCED, serverSeq = 3))
        dao.savePosition(SyncPositionEntity(ownerUserId = "user-1", lastPullSeq = 40))

        handover.beforeSignIn("user-2", isNewUser = false)

        assertEquals(SyncPositionEntity(ownerUserId = "user-2", askingToAdd = true), position())
    }

    @Test
    fun `try again puts stopped check-ins back in the queue`() = runTest {
        checkIns.insert(row("a").copy(syncError = "FAILED", syncAttempts = 10))
        assertEquals(1, repository.status.first().stuckCount)

        repository.retryStuck()

        val status = repository.status.first()
        assertEquals(0, status.stuckCount)
        assertEquals(1, status.waitingCount)
    }

    private class FakeScheduler : SyncScheduler {
        var requests = 0

        override suspend fun requestSync() {
            requests++
        }

        override fun keepPeriodicSync() = Unit

        override fun cancelAll() = Unit

        override val running = MutableStateFlow(false)
    }
}
