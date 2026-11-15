package app.ulap.core.sync

import androidx.room.withTransaction
import app.ulap.core.database.UlapDatabase
import app.ulap.core.database.model.PullSeenEntity
import app.ulap.core.database.model.SyncPositionEntity
import app.ulap.core.network.ApiResult
import app.ulap.core.network.sync.SyncApi
import app.ulap.shared.sync.CheckInChange
import app.ulap.shared.sync.PullPage
import javax.inject.Inject
import kotlin.time.Clock

/**
 * A run's second step: pull every change after `last_pull_seq` until `hasMore = false`
 * (api-spec.md §8.3). Each page, its merge and the new `last_pull_seq` are one transaction, so a
 * run killed between pages carries on from the last saved page and never applies one twice.
 *
 * `resetRequired` starts a full resync (technical-plan.md §8, Full resync): pull from 0, note
 * each live ID in `pull_seen`, and on the last page delete the `SYNCED` rows the server didn't
 * send. `sync_resetting` stays true until then, so a run killed midway carries on next time. A
 * first sign-in's pull from 0 runs the same way, so both page past the server's purge line.
 */
internal class PullStep @Inject constructor(
    private val database: UlapDatabase,
    private val api: SyncApi,
    private val retry: ApiRetry,
    private val clock: Clock,
) {
    private val dao = database.syncDao()
    private val checkIns = database.checkInDao()

    suspend fun run(): SyncRunResult {
        repeat(MAX_PAGES) { pullPage()?.let { return it } }
        // More pages than one run takes: the next run carries on from here.
        return SyncRunResult.RETRY_LATER
    }

    /** Pulls and saves one page. Null means "pull the next one". */
    private suspend fun pullPage(): SyncRunResult? {
        var position = position()
        // Every download from 0 is a full one: a first sign-in as much as a reset. Its cursor
        // passes below the server's purge line on the way up, so each page says `full`.
        if (position.lastPullSeq == 0L && !position.resetting) position = startReset()
        val since = position.lastPullSeq
        return when (val result = retry.call { api.pull(since, full = position.resetting) }) {
            is ApiResult.Success -> afterPage(result.value, since)
            is ApiResult.Failure -> result.toRunResult()
            is ApiResult.NetworkError -> SyncRunResult.RETRY_LATER
        }
    }

    private suspend fun afterPage(page: PullPage, since: Long): SyncRunResult? = when {
        // The server never asks that for since = 0 (api-spec.md §8.3). Don't loop on it.
        page.resetRequired && since == 0L -> SyncRunResult.RETRY_LATER

        page.resetRequired -> {
            startReset()
            null
        }

        else -> {
            savePage(page)
            when {
                !page.hasMore -> SyncRunResult.DONE

                // A cursor that doesn't move would pull the same page forever.
                page.nextSince <= since -> SyncRunResult.RETRY_LATER

                else -> null
            }
        }
    }

    /** Full resync, step 2: one transaction. */
    private suspend fun startReset(): SyncPositionEntity = database.withTransaction {
        dao.clearSeen()
        position().copy(lastPullSeq = 0, resetting = true).also { dao.savePosition(it) }
    }

    /** One page: merge, note the IDs during a resync, move the cursor, and finish a resync on its last page. */
    private suspend fun savePage(page: PullPage) {
        val now = clock.now()
        database.withTransaction {
            val position = position()
            for (pulled in page.changes) {
                mergePulled(checkIns.get(pulled.change.id), pulled, now)?.let { dao.saveMerged(it) }
            }
            if (position.resetting) {
                dao.addSeen(
                    page.changes.filter {
                        it.change is CheckInChange.Saved
                    }.map { PullSeenEntity(it.change.id) },
                )
            }
            val finishesReset = position.resetting && !page.hasMore
            if (finishesReset) {
                // Full resync, step 4.
                dao.deleteSyncedNotSeen()
                dao.clearSeen()
            }
            dao.savePosition(
                position.copy(lastPullSeq = page.nextSince, resetting = position.resetting && !finishesReset),
            )
        }
    }

    private suspend fun position() = dao.position() ?: SyncPositionEntity()

    private companion object {
        /** 1,000 pages of 500 is half a million changes. Only there so a bug can't loop forever. */
        const val MAX_PAGES = 1_000
    }
}
