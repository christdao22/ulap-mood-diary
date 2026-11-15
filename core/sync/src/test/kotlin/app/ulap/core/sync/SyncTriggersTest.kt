package app.ulap.core.sync

import app.ulap.core.domain.sync.SyncStatus
import app.ulap.core.testing.FakeSyncRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** When sync runs (technical-plan.md §8, When sync runs). Plain JVM: every part is a fake. */
@OptIn(ExperimentalCoroutinesApi::class) // runCurrent and advanceTimeBy drive virtual time.
class SyncTriggersTest {
    private val now = Instant.parse("2026-11-15T08:00:00Z")
    private val session = MutableStateFlow<SyncAccount?>(null)
    private val sync = FakeSyncRepository()
    private val scheduler = RecordingScheduler()
    private val foreground = MutableSharedFlow<Unit>()

    private fun TestScope.started() {
        val triggers = SyncTriggers(
            session = object : SyncSession {
                override val account = session
            },
            sync = sync,
            scheduler = scheduler,
            foreground = object : AppForeground {
                override val starts = foreground
            },
            clock = object : Clock {
                override fun now() = now
            },
            scope = backgroundScope,
        )
        backgroundScope.launch { triggers.watch() }
        runCurrent()
    }

    private fun signIn(verified: Boolean = true) {
        session.value = SyncAccount("user-1", emailVerified = verified)
    }

    @Test
    fun `a guest gets no sync at all`() = runTest {
        sync.status.value = SyncStatus(waitingCount = 3)
        started()
        advanceTimeBy(1.minutes)

        assertEquals(listOf("cancelAll"), scheduler.calls)
    }

    @Test
    fun `signing in keeps the 6-hour job, and signing out cancels everything`() = runTest {
        started()
        signIn()
        runCurrent()
        session.value = null
        runCurrent()

        assertEquals(listOf("cancelAll", "periodic", "cancelAll"), scheduler.calls)
    }

    @Test
    fun `a burst of writes goes up in one sync, 5 seconds after the last`() = runTest {
        started()
        signIn()
        runCurrent()

        for (count in 1..3) {
            sync.status.value = sync.status.value.copy(waitingCount = count)
            advanceTimeBy(2.seconds)
        }
        assertEquals(0, scheduler.requests)

        advanceTimeBy(4.seconds)
        assertEquals(1, scheduler.requests)

        // Synced: the queue empties, and nothing new is asked for.
        sync.status.value = sync.status.value.copy(waitingCount = 0)
        advanceTimeBy(1.minutes)
        assertEquals(1, scheduler.requests)
    }

    @Test
    fun `nothing goes up while the add question waits`() = runTest {
        sync.status.value = SyncStatus(waitingCount = 2, askToAddCheckIns = true)
        started()
        signIn()
        advanceTimeBy(1.minutes)
        assertEquals(0, scheduler.requests)

        sync.status.value = sync.status.value.copy(askToAddCheckIns = false)
        advanceTimeBy(6.seconds)
        assertEquals(1, scheduler.requests)
    }

    @Test
    fun `opening the app syncs when the last sync was over 15 minutes ago`() = runTest {
        sync.status.value = SyncStatus(lastSyncedAt = now - 5.minutes)
        started()
        signIn()
        runCurrent()

        foreground.emit(Unit)
        runCurrent()
        assertEquals(0, scheduler.requests)

        sync.status.value = SyncStatus(lastSyncedAt = now - 20.minutes)
        foreground.emit(Unit)
        runCurrent()
        assertEquals(1, scheduler.requests)
    }

    @Test
    fun `verifying the email starts a sync`() = runTest {
        started()
        signIn(verified = false)
        runCurrent()
        assertEquals(0, scheduler.requests)

        signIn(verified = true)
        runCurrent()
        assertEquals(1, scheduler.requests)
    }

    private class RecordingScheduler : SyncScheduler {
        val calls = mutableListOf<String>()
        val requests get() = calls.count { it == "request" }

        override suspend fun requestSync() {
            calls += "request"
        }

        override fun keepPeriodicSync() {
            calls += "periodic"
        }

        override fun cancelAll() {
            calls += "cancelAll"
        }

        override val running = MutableStateFlow(false)
    }
}
