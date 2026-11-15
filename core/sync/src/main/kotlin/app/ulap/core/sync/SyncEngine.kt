package app.ulap.core.sync

import androidx.room.withTransaction
import app.ulap.core.database.SyncLock
import app.ulap.core.database.UlapDatabase
import app.ulap.core.database.model.SyncPositionEntity
import app.ulap.core.network.ApiResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.flow.first

/** How a run ended, which tells SyncWorker what to say to WorkManager. */
internal enum class SyncRunResult {
    /** Everything pushed and pulled. Inside a step: go on to the next. */
    DONE,

    /** An outage (no network, 429, 5xx): WorkManager tries again with backoff. */
    RETRY_LATER,

    /**
     * Nothing to do for now, and retrying won't help: no one is signed in, the email isn't
     * verified yet, an update is needed, or the person hasn't answered "Add this phone's
     * check-ins?". SyncTriggers asks again when that changes.
     */
    NOT_NOW,
}

/**
 * One sync run: push every `PENDING` row, then pull until `hasMore = false` (technical-plan.md §8,
 * api-spec.md §8.6). Runs one at a time ([SyncLock]), so the one-time and the 6-hour job never
 * overlap, and never while the diary is being erased.
 */
@Singleton
internal class SyncEngine @Inject constructor(
    private val database: UlapDatabase,
    private val session: SyncSession,
    private val push: PushStep,
    private val pull: PullStep,
    private val lock: SyncLock,
    private val clock: Clock,
) {
    private val dao = database.syncDao()

    suspend fun sync(): SyncRunResult = lock.withLock {
        val account = session.account.first() ?: return@withLock SyncRunResult.NOT_NOW
        if (!readyFor(account.userId)) return@withLock SyncRunResult.NOT_NOW

        val pushed = push.run()
        if (pushed != SyncRunResult.DONE) return@withLock pushed
        val pulled = pull.run()
        if (pulled != SyncRunResult.DONE) return@withLock pulled

        database.withTransaction { dao.savePosition(position().copy(lastSyncAt = clock.now())) }
        SyncRunResult.DONE
    }

    /**
     * The check-ins here belong to this account, and no question is waiting (api-spec.md §8.5).
     * A database with no owner yet (a new one after a lost Keystore key, or a session from before
     * this version) is claimed by the signed-in account.
     */
    private suspend fun readyFor(userId: String): Boolean = database.withTransaction {
        val position = position()
        when {
            position.askingToAdd -> false

            position.ownerUserId == null -> {
                dao.savePosition(position.copy(ownerUserId = userId))
                true
            }

            else -> position.ownerUserId == userId
        }
    }

    private suspend fun position() = dao.position() ?: SyncPositionEntity()
}

/**
 * 429 and 5xx are outages: try the run again later. The rest won't get better by retrying: 401
 * (the session ended, and the app says "Sign in again"), 403 (`EMAIL_NOT_VERIFIED`,
 * `ACCOUNT_DELETED`), 426 and 400 (a bug).
 */
internal fun ApiResult.Failure.toRunResult(): SyncRunResult =
    if (isRetryable) SyncRunResult.RETRY_LATER else SyncRunResult.NOT_NOW
