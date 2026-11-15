package app.ulap.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

/** Puts sync runs in WorkManager. Only SyncTriggers and SyncRepository use it. */
internal interface SyncScheduler {
    /** One run, as soon as there's a network. */
    suspend fun requestSync()

    /** The 6-hour job while someone is signed in (api-spec.md §8.6). Keeps one already scheduled. */
    fun keepPeriodicSync()

    /** Sign-out: no more runs, and a running one stops. */
    fun cancelAll()

    /** True while a run is under way. */
    val running: Flow<Boolean>
}

/**
 * Both jobs need a network, and retry with exponential backoff from 30 seconds
 * (technical-plan.md §8, When sync runs). The one-time job is unique, so requests while one waits
 * don't pile up; the periodic job is unique under its own name (WorkManager can't mix the two),
 * and SyncEngine's lock keeps them from running at the same time.
 */
internal class WorkManagerSyncScheduler @Inject constructor(@param:ApplicationContext private val context: Context) :
    SyncScheduler {
    private val workManager get() = WorkManager.getInstance(context)

    /**
     * - A run is already queued behind the current one: it will see this change too. Nothing to do.
     * - A run is under way: queue one more after it, because it may have pushed before this change.
     * - Otherwise start one now. That replaces a run waiting out its backoff, so "Sync now" and a
     *   new check-in don't wait hours behind an old failure.
     */
    override suspend fun requestSync() {
        val states = workManager.getWorkInfosForUniqueWorkFlow(ONE_TIME).first().map { it.state }
        when {
            WorkInfo.State.BLOCKED in states -> Unit

            WorkInfo.State.RUNNING in states ->
                workManager.enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.APPEND_OR_REPLACE, oneTimeRequest())

            else -> workManager.enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.REPLACE, oneTimeRequest())
        }
    }

    override fun keepPeriodicSync() {
        workManager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, periodicRequest())
    }

    override fun cancelAll() {
        workManager.cancelUniqueWork(ONE_TIME)
        workManager.cancelUniqueWork(PERIODIC)
    }

    override val running: Flow<Boolean>
        get() = combine(
            workManager.getWorkInfosForUniqueWorkFlow(ONE_TIME),
            workManager.getWorkInfosForUniqueWorkFlow(PERIODIC),
        ) { oneTime, periodic -> (oneTime + periodic).any { it.state == WorkInfo.State.RUNNING } }
            .distinctUntilChanged()

    companion object {
        const val ONE_TIME: String = "ulap-sync"
        const val PERIODIC: String = "ulap-sync-periodic"
        const val BACKOFF_SECONDS: Long = 30
        const val PERIOD_HOURS: Long = 6

        fun oneTimeRequest(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        fun periodicRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<SyncWorker>(PERIOD_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED, requiresBatteryNotLow = true))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
    }
}
