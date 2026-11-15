package app.ulap.core.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.ulap.core.database.SyncLock
import app.ulap.core.database.UlapDatabase
import app.ulap.core.database.model.CheckInEntity
import app.ulap.core.database.model.SyncPositionEntity
import app.ulap.core.database.model.SyncState
import app.ulap.core.network.ApiResult
import app.ulap.shared.api.ErrorCode
import app.ulap.shared.sync.CheckInChange
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncEngineTest {
    private val server = FakeSyncServer()
    private val phones = mutableListOf<Phone>()

    /** One phone: its own database, signed in to user-1, syncing with [server]. */
    private inner class Phone(batchSize: Int = 200) {
        val database: UlapDatabase = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UlapDatabase::class.java,
        ).build()
        val session = MutableStateFlow<SyncAccount?>(SyncAccount(USER, emailVerified = true))
        var now: Instant = T0 + 60.minutes
        private val clock = object : Clock {
            override fun now() = now
        }
        val engine = SyncEngine(
            database = database,
            session = object : SyncSession {
                override val account = session
            },
            push = PushStep(database, server, noWaitRetry(), batchSize),
            pull = PullStep(database, server, noWaitRetry(), clock),
            lock = SyncLock(),
            clock = clock,
        )
        val checkIns = database.checkInDao()
        val sync = database.syncDao()

        init {
            phones += this
        }

        suspend fun get(id: String): CheckInEntity? = checkIns.get(id)

        suspend fun live(): Set<String> = checkIns.observeAll().first().map { it.id }.toSet()

        suspend fun position(): SyncPositionEntity = sync.position() ?: SyncPositionEntity()
    }

    @AfterTest
    fun tearDown() = phones.forEach { it.database.close() }

    @Test
    fun `pending check-ins go up in batches of 200 and come back synced, with the server's number`() = runTest {
        val phone = Phone()
        repeat(450) { phone.checkIns.insert(row("id-%03d".format(it), updatedAt = T0 + it.minutes)) }

        assertEquals(SyncRunResult.DONE, phone.engine.sync())

        assertEquals(listOf(200, 200, 50), server.pushes.map { it.size })
        assertEquals(0, phone.sync.observeWaitingCount().first())
        assertEquals(1L, phone.get("id-000")!!.serverSeq)
        assertEquals(450L, phone.position().lastPullSeq)
        assertEquals(phone.now, phone.position().lastSyncAt)
    }

    @Test
    fun `a check-in edited while its push is on the way goes up again`() = runTest {
        val phone = Phone()
        phone.checkIns.insert(row("a", stress = 8))
        var edited = false
        server.beforePushAnswer = {
            if (!edited) {
                edited = true
                phone.checkIns.update(phone.get("a")!!.copy(stress = 2, updatedAt = T0 + 1.minutes))
            }
        }

        phone.engine.sync()

        assertEquals(2, server.pushes.size)
        assertEquals(SyncState.SYNCED, phone.get("a")!!.syncState)
        assertEquals(2, (server.changes.getValue("a") as CheckInChange.Saved).checkIn.stress)
        assertEquals(2, phone.get("a")!!.stress)
    }

    @Test
    fun `a rejected change stops trying, and the rest go up`() = runTest {
        val phone = Phone()
        phone.checkIns.insert(row("bad"))
        phone.checkIns.insert(row("good"))
        server.reject = { if (it.id == "bad") "INVALID_MOOD" else null }

        assertEquals(SyncRunResult.DONE, phone.engine.sync())
        phone.engine.sync()

        assertEquals("INVALID_MOOD", phone.get("bad")!!.syncError)
        assertEquals(SyncState.SYNCED, phone.get("good")!!.syncState)
        // Never sent again until "Try again" or an edit.
        assertEquals(1, server.pushes.size)
    }

    @Test
    fun `each 500 INTERNAL counts one try, and the tenth stops the row with FAILED`() = runTest {
        val phone = Phone()
        phone.checkIns.insert(row("a"))
        repeat(10) { server.pushAnswers += ApiResult.Failure(500, ErrorCode.INTERNAL) }

        repeat(9) { assertEquals(SyncRunResult.RETRY_LATER, phone.engine.sync()) }
        assertEquals(9, phone.get("a")!!.syncAttempts)
        assertNull(phone.get("a")!!.syncError)

        phone.engine.sync()
        assertEquals("FAILED", phone.get("a")!!.syncError)

        // No endless retry: the next run doesn't send it.
        assertEquals(SyncRunResult.DONE, phone.engine.sync())
        assertEquals(emptyList(), server.pushes)
        // Try again puts it back, with a fresh count.
        phone.sync.retryStuck()
        phone.engine.sync()
        assertEquals(SyncState.SYNCED, phone.get("a")!!.syncState)
    }

    @Test
    fun `outages are retried inside the run and never count against the row`() = runTest {
        val phone = Phone()
        phone.checkIns.insert(row("a"))
        // 2s, 4s, 8s, 16s: five calls in all, then the run gives up for WorkManager's backoff.
        repeat(5) { server.pushAnswers += ApiResult.NetworkError(IOException("offline")) }

        assertEquals(SyncRunResult.RETRY_LATER, phone.engine.sync())
        assertEquals(0, server.pushAnswers.size)
        assertEquals(0, phone.get("a")!!.syncAttempts)

        server.pushAnswers += ApiResult.Failure(503, null)
        server.pushAnswers += ApiResult.Failure(429, ErrorCode.RATE_LIMITED)
        assertEquals(SyncRunResult.DONE, phone.engine.sync())
        assertEquals(0, phone.get("a")!!.syncAttempts)
        assertEquals(SyncState.SYNCED, phone.get("a")!!.syncState)
    }

    @Test
    fun `a body over the server's limit is sent again in smaller batches`() = runTest {
        val phone = Phone()
        repeat(200) { phone.checkIns.insert(row("id-%03d".format(it), updatedAt = T0 + it.minutes)) }
        server.pushAnswers += ApiResult.Failure(413, ErrorCode.PAYLOAD_TOO_LARGE)

        assertEquals(SyncRunResult.DONE, phone.engine.sync())

        assertEquals(listOf(100, 100), server.pushes.map { it.size })
    }

    @Test
    fun `an unverified email stops the run without retrying or counting`() = runTest {
        val phone = Phone()
        phone.checkIns.insert(row("a"))
        server.pushAnswers += ApiResult.Failure(403, ErrorCode.EMAIL_NOT_VERIFIED)

        assertEquals(SyncRunResult.NOT_NOW, phone.engine.sync())

        assertEquals(SyncState.PENDING, phone.get("a")!!.syncState)
        assertEquals(0, phone.get("a")!!.syncAttempts)
    }

    @Test
    fun `nothing syncs while signed out or while the add question waits`() = runTest {
        val phone = Phone()
        phone.checkIns.insert(row("a"))

        phone.session.value = null
        assertEquals(SyncRunResult.NOT_NOW, phone.engine.sync())

        phone.session.value = SyncAccount(USER, emailVerified = true)
        phone.sync.savePosition(SyncPositionEntity(ownerUserId = USER, askingToAdd = true))
        assertEquals(SyncRunResult.NOT_NOW, phone.engine.sync())

        // Another account's check-ins are never pushed to this one.
        phone.sync.savePosition(SyncPositionEntity(ownerUserId = "user-2"))
        assertEquals(SyncRunResult.NOT_NOW, phone.engine.sync())

        assertEquals(emptyList(), server.pushes)
        assertEquals(0, server.pulls)
    }

    @Test
    fun `pull pages until hasMore is false, merging deletes and keeping the cursor`() = runTest {
        val phone = Phone()
        server.store(row("gone").toChange())
        phone.engine.sync()
        server.pageLimit = 2
        server.store(row("a").toChange())
        server.store(row("b").toChange())
        server.store(CheckInChange.Deleted("gone", T0 + 1.minutes, T0 + 1.minutes))
        val pullsBefore = server.pulls

        assertEquals(SyncRunResult.DONE, phone.engine.sync())

        assertEquals(setOf("a", "b"), phone.live())
        assertEquals(T0 + 1.minutes, phone.get("gone")!!.deletedAt)
        assertEquals(4L, phone.position().lastPullSeq)
        assertEquals(2, server.pulls - pullsBefore)
        // An ordinary pull after the first download: not `full`.
        assertEquals(listOf(false, false), server.fullPulls.takeLast(2))

        // The next run asks only for what's new.
        phone.engine.sync()
        assertEquals(3, server.pulls - pullsBefore)
    }

    @Test
    fun `resetRequired, a full resync removes purged check-ins and keeps pending ones`() = runTest {
        val phone = Phone()
        server.store(row("kept").toChange())
        server.store(row("purged").toChange())
        phone.engine.sync()
        // Deleted on another phone, and the tombstone purged before this phone heard of it.
        server.store(CheckInChange.Deleted("purged", T0 + 1.minutes, T0 + 1.minutes))
        server.store(row("new").toChange())
        server.purgeTombstones()
        phone.checkIns.insert(row("written offline", updatedAt = T0 + 2.minutes))

        assertEquals(SyncRunResult.DONE, phone.engine.sync())

        assertEquals(setOf("kept", "new", "written offline"), phone.live())
        assertNull(phone.get("purged"))
        assertEquals(SyncState.SYNCED, phone.get("written offline")!!.syncState)
        assertEquals(false, phone.position().resetting)
    }

    @Test
    fun `a download from 0 pages past the server's purge line`() = runTest {
        val phone = Phone()
        server.pageLimit = 1
        server.store(row("a").toChange())
        server.store(row("b").toChange())
        server.store(CheckInChange.Deleted("gone", T0, T0))
        server.purgeTombstones()
        server.store(row("c").toChange())
        phone.checkIns.insert(row("stale copy", syncState = SyncState.SYNCED))

        assertEquals(SyncRunResult.DONE, phone.engine.sync())

        // Page 2 starts at since = 1, below the purge line: only `full` gets it through.
        assertEquals(setOf("a", "b", "c"), phone.live())
        assertTrue(server.fullPulls.all { it })
        assertEquals(false, phone.position().resetting)

        // Caught up: later pulls are ordinary ones.
        phone.engine.sync()
        assertEquals(false, server.fullPulls.last())
    }

    @Test
    fun `a full resync stopped halfway carries on in the next run`() = runTest {
        val phone = Phone()
        server.pageLimit = 1
        server.store(row("a").toChange())
        server.store(row("b").toChange())
        server.store(row("purged").toChange())
        phone.engine.sync()
        server.store(CheckInChange.Deleted("purged", T0 + 1.minutes, T0 + 1.minutes))
        server.purgeTombstones()
        phone.checkIns.insert(row("pending", updatedAt = T0 + 2.minutes))
        // The reset answer and one page get through, then the app is "killed" (a run that stops).
        server.pullAnswers.addAll(listOf(null, null, ApiResult.Failure(400, ErrorCode.BAD_REQUEST)))

        assertEquals(SyncRunResult.NOT_NOW, phone.engine.sync())
        assertTrue(phone.position().resetting)
        assertEquals(setOf("a", "b", "purged", "pending"), phone.live())

        assertEquals(SyncRunResult.DONE, phone.engine.sync())
        assertEquals(setOf("a", "b", "pending"), phone.live())
        assertEquals(false, phone.position().resetting)
    }

    @Test
    fun `two phones edit the same check-in offline and both end with the newest version`() = runTest {
        val first = Phone()
        val second = Phone()
        first.checkIns.insert(row("a", stress = 5))
        first.engine.sync()
        second.engine.sync()

        // Both offline: the first edits at :10, the second later, at :20.
        first.checkIns.update(
            first.get("a")!!.copy(stress = 1, updatedAt = T0 + 10.minutes, syncState = SyncState.PENDING),
        )
        second.checkIns.update(
            second.get("a")!!.copy(stress = 9, updatedAt = T0 + 20.minutes, syncState = SyncState.PENDING),
        )

        // Back online, in the "wrong" order: the newer edit goes up first.
        second.engine.sync()
        first.engine.sync()
        second.engine.sync()

        assertEquals(9, first.get("a")!!.stress)
        assertEquals(9, second.get("a")!!.stress)
        assertEquals(SyncState.SYNCED, first.get("a")!!.syncState)
    }

    @Test
    fun `the network drops halfway and the retry finishes without duplicates`() = runTest {
        val phone = Phone(batchSize = 2)
        repeat(5) { phone.checkIns.insert(row("id-$it", updatedAt = T0 + it.minutes)) }
        // The first batch gets through; then no answers for the rest of the run.
        server.pushAnswers.addAll(listOf(null) + List(5) { ApiResult.NetworkError(IOException("dropped")) })

        assertEquals(SyncRunResult.RETRY_LATER, phone.engine.sync())
        assertEquals(3, phone.sync.observeWaitingCount().first())

        assertEquals(SyncRunResult.DONE, phone.engine.sync())
        val sent = server.pushes.flatten().map { it.id }
        assertEquals(sent.toSet().size, sent.size, "no check-in was sent twice")
        assertEquals(5, server.changes.size)
        assertEquals(5, phone.live().size)
        assertEquals(0, phone.sync.observeWaitingCount().first())
    }

    private companion object {
        const val USER = "user-1"
    }
}
