package app.ulap.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * One sync run in the background. WorkManager starts it ([WorkManagerSyncScheduler]); Hilt builds
 * it with the engine (`HiltWorkerFactory`, set up in UlapApp).
 */
@HiltWorker
internal class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: SyncEngine,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (engine.sync()) {
        SyncRunResult.DONE, SyncRunResult.NOT_NOW -> Result.success()

        // Backoff from 30 seconds, set on the request.
        SyncRunResult.RETRY_LATER -> Result.retry()
    }
}
