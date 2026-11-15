package app.ulap.core.sync

import app.ulap.core.common.dispatchers.ApplicationScope
import app.ulap.core.domain.sync.SyncRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Decides when sync runs (D8, technical-plan.md §8, When sync runs). Repositories only write to
 * Room and never schedule anything, so a new write path can't forget to.
 *
 * While no one is signed in it does nothing, and signing out cancels all sync work. While someone
 * is signed in it keeps the 6-hour job, and asks for a run:
 * - 5 seconds after the queue of check-ins waiting to upload grows, so a burst of quick edits goes
 *   up in one push;
 * - when the app comes to the foreground and the last sync was more than 15 minutes ago;
 * - when the email is verified, since sync waits for that.
 */
@Singleton
class SyncTriggers @Inject internal constructor(
    private val session: SyncSession,
    private val sync: SyncRepository,
    private val scheduler: SyncScheduler,
    private val foreground: AppForeground,
    private val clock: Clock,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private var job: Job? = null

    /** Called once, from `Application.onCreate`. */
    fun start() {
        if (job == null) job = scope.launch { watch() }
    }

    internal suspend fun watch() {
        session.account.map { it?.userId }.distinctUntilChanged().collectLatest { userId ->
            if (userId == null) {
                scheduler.cancelAll()
            } else {
                scheduler.keepPeriodicSync()
                whileSignedIn()
            }
        }
    }

    private suspend fun whileSignedIn() = coroutineScope {
        launch { afterWrites() }
        launch { onForeground() }
        launch { onEmailVerified() }
    }

    @OptIn(FlowPreview::class)
    private suspend fun afterWrites() {
        sync.status
            // Nothing goes up while "Add this phone's check-ins?" waits for an answer.
            .map { if (it.askToAddCheckIns) 0 else it.waitingCount }
            .distinctUntilChanged()
            .filter { it > 0 }
            .debounce(WRITE_DEBOUNCE)
            .collect { scheduler.requestSync() }
    }

    private suspend fun onForeground() {
        foreground.starts.collect {
            val last = sync.status.first().lastSyncedAt
            if (last == null || clock.now() - last > FOREGROUND_MIN_GAP) scheduler.requestSync()
        }
    }

    private suspend fun onEmailVerified() {
        var wasVerified: Boolean? = null
        session.account.map { it?.emailVerified }.distinctUntilChanged().collect { verified ->
            if (wasVerified == false && verified == true) scheduler.requestSync()
            wasVerified = verified
        }
    }

    private companion object {
        val WRITE_DEBOUNCE = 5.seconds
        val FOREGROUND_MIN_GAP = 15.minutes
    }
}
