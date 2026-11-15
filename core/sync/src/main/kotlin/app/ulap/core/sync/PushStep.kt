package app.ulap.core.sync

import android.util.Log
import androidx.room.withTransaction
import app.ulap.core.database.UlapDatabase
import app.ulap.core.database.model.CheckInEntity
import app.ulap.core.network.ApiResult
import app.ulap.core.network.sync.SyncApi
import app.ulap.shared.api.SyncLimits
import app.ulap.shared.sync.PushOutcome
import javax.inject.Inject

/**
 * A run's first step: every `PENDING` row goes up, in batches of at most 200 (api-spec.md §8.2).
 * Each batch's answer is saved in one transaction, so a run that stops halfway loses nothing.
 * Sending a batch twice is safe: the server answers `STALE` and keeps one copy.
 */
internal class PushStep(
    private val database: UlapDatabase,
    private val api: SyncApi,
    private val retry: ApiRetry,
    private val firstBatchSize: Int,
) {
    @Inject constructor(database: UlapDatabase, api: SyncApi, retry: ApiRetry) :
        this(database, api, retry, SyncLimits.MAX_PUSH_CHANGES)

    private val dao = database.syncDao()

    suspend fun run(): SyncRunResult {
        var batchSize = firstBatchSize
        repeat(MAX_BATCHES) {
            val batch = dao.uploadBatch(batchSize)
            when (val next = if (batch.isEmpty()) Next.Stop(SyncRunResult.DONE) else push(batch, batchSize)) {
                is Next.Stop -> return next.result
                is Next.Batch -> batchSize = next.size
            }
        }
        return SyncRunResult.DONE
    }

    /** What to do after one batch: push another of [Batch.size], or end the step. */
    private sealed interface Next {
        data class Batch(val size: Int) : Next

        data class Stop(val result: SyncRunResult) : Next
    }

    private suspend fun push(batch: List<CheckInEntity>, size: Int): Next =
        when (val result = retry.call { api.push(batch.map { it.toChange() }) }) {
            is ApiResult.Success -> if (saveAnswer(
                    batch,
                    result.value,
                )
            ) {
                Next.Batch(size)
            } else {
                Next.Stop(SyncRunResult.DONE)
            }

            // 200 long notes can pass the 1 MB body limit: send fewer at a time.
            is ApiResult.Failure -> if (result.status == PAYLOAD_TOO_LARGE && size > 1) {
                Next.Batch(size / 2)
            } else {
                if (result.status == INTERNAL) countInternalError(batch)
                Next.Stop(result.toRunResult())
            }

            is ApiResult.NetworkError -> Next.Stop(SyncRunResult.RETRY_LATER)
        }

    /**
     * `APPLIED` and `STALE` → `SYNCED` with the server's number; rejected → stop trying, with the
     * reason (api-spec.md §8.2, Phone actions). A row edited meanwhile is left `PENDING` (SyncDao).
     *
     * Returns false when the answer named none of the batch's rows, a server bug: the step stops
     * instead of sending the same batch forever. The rows stay `PENDING` for the next run.
     */
    private suspend fun saveAnswer(batch: List<CheckInEntity>, outcome: PushOutcome): Boolean {
        val sent = batch.associate { it.id to it.updatedAt }
        var answered = false
        database.withTransaction {
            for (accepted in outcome.accepted) {
                val updatedAt = sent[accepted.id] ?: continue
                dao.markSynced(accepted.id, updatedAt, accepted.serverSeq)
                answered = true
            }
            for (rejected in outcome.rejected) {
                val updatedAt = sent[rejected.id] ?: continue
                // The ID and reason only, never the change: it can hold journal text.
                Log.w(TAG, "push rejected ${rejected.id}: ${rejected.reason} (${rejected.field})")
                dao.markRejected(rejected.id, updatedAt, rejected.reason)
                answered = true
            }
        }
        return answered
    }

    /** Each row in a batch that failed with `500 INTERNAL` counts one try (technical-plan.md §8). */
    private suspend fun countInternalError(batch: List<CheckInEntity>) {
        database.withTransaction {
            for (row in batch) dao.countInternalError(row.id, row.updatedAt, MAX_INTERNAL_ERRORS)
        }
    }

    companion object {
        /** After this many `500 INTERNAL` pushes, a row stops with `sync_error = FAILED`. */
        const val MAX_INTERNAL_ERRORS: Int = 10

        /** More than anyone writes. Only there so a bug can't loop forever. */
        private const val MAX_BATCHES = 1_000
        private const val INTERNAL = 500
        private const val PAYLOAD_TOO_LARGE = 413
        private const val TAG = "UlapSync"
    }
}
