package app.ulap.core.sync

import androidx.room.withTransaction
import app.ulap.core.database.SyncLock
import app.ulap.core.database.UlapDatabase
import app.ulap.core.database.dao.SyncDao
import app.ulap.core.database.model.SyncPositionEntity
import app.ulap.core.domain.sync.SyncRepository
import app.ulap.core.domain.sync.SyncSignInPreparer
import app.ulap.core.domain.sync.SyncStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** [SyncRepository] read from Room, WorkManager and the network state. */
internal class RoomSyncRepository @Inject constructor(
    private val database: UlapDatabase,
    private val scheduler: SyncScheduler,
    private val connectivity: Connectivity,
    private val lock: SyncLock,
) : SyncRepository {
    private val dao = database.syncDao()

    override val status: Flow<SyncStatus> = combine(
        dao.observePosition(),
        dao.observeWaitingCount(),
        dao.observeStuckCount(),
        scheduler.running,
        connectivity.online,
    ) { position, waiting, stuck, running, online ->
        SyncStatus(
            lastSyncedAt = position?.lastSyncAt,
            waitingCount = waiting,
            stuckCount = stuck,
            running = running,
            online = online,
            askToAddCheckIns = position?.askingToAdd == true,
        )
    }.distinctUntilChanged()

    override suspend fun syncNow() = scheduler.requestSync()

    override suspend fun retryStuck() {
        // The queue grows, so SyncTriggers asks for a run.
        dao.retryStuck()
    }

    /** Add them: push all, then pull from 0. Don't add: delete them here, then pull from 0 (api-spec.md §8.5). */
    override suspend fun answerAddCheckIns(add: Boolean) {
        val answered = lock.withLock {
            database.withTransaction {
                val position = dao.position() ?: SyncPositionEntity()
                if (position.askingToAdd) {
                    if (add) dao.queueForNewAccount() else dao.deleteAllCheckIns()
                    dao.savePosition(position.copy(askingToAdd = false))
                }
                position.askingToAdd
            }
        }
        // "Don't add" leaves nothing to push, so the queue alone wouldn't start the pull.
        if (answered) scheduler.requestSync()
    }
}

/**
 * Sorts out this phone's check-ins for the account signing in (api-spec.md §8.5, technical-plan.md
 * §8, First sign-in), in one transaction, before its session is saved:
 * - The account these check-ins already belong to (signing in again): carry on where sync was.
 * - A new account: every live check-in goes up, then pull from 0.
 * - An account with check-ins elsewhere, and this phone has some: ask first. Nothing syncs until
 *   the answer ([RoomSyncRepository.answerAddCheckIns]).
 * - An account with check-ins elsewhere, and none here: pull from 0.
 */
internal class AccountHandover @Inject constructor(private val database: UlapDatabase, private val lock: SyncLock) :
    SyncSignInPreparer {
    private val dao = database.syncDao()

    override suspend fun beforeSignIn(userId: String, isNewUser: Boolean) {
        lock.withLock {
            database.withTransaction {
                val position = dao.position() ?: SyncPositionEntity()
                if (position.ownerUserId == userId) return@withTransaction
                val ask = !isNewUser && dao.countLive() > 0
                if (isNewUser) {
                    dao.queueForNewAccount()
                } else if (!ask) {
                    dao.deleteAllCheckIns()
                }
                dao.clearSeen()
                // A fresh position: this account's own cursor starts at 0.
                dao.savePosition(SyncPositionEntity(ownerUserId = userId, askingToAdd = ask))
            }
        }
    }
}

/** Live check-ins go up as new; tombstones go, since the account never had those check-ins. */
private suspend fun SyncDao.queueForNewAccount() {
    deleteTombstones()
    queueLiveForUpload()
}
