package app.ulap.core.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** One unique job, network required (technical-plan.md §8, When sync runs). */
@RunWith(RobolectricTestRunner::class)
class WorkManagerSyncSchedulerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scheduler = WorkManagerSyncScheduler(context)
    private lateinit var workManager: WorkManager

    @BeforeTest
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(context)
    }

    private fun infos(name: String): List<WorkInfo> = workManager.getWorkInfosForUniqueWork(name).get()

    @Test
    fun `a request waits for a network, and requests while it waits stay one job`() = runTest {
        scheduler.requestSync()
        scheduler.requestSync()
        scheduler.requestSync()

        val waiting = infos(WorkManagerSyncScheduler.ONE_TIME).filter { it.state == WorkInfo.State.ENQUEUED }
        assertEquals(1, waiting.size)
        assertEquals(NetworkType.CONNECTED, waiting.single().constraints.requiredNetworkType)
    }

    @Test
    fun `the 6-hour job needs a network and a battery that isn't low, and is kept, not replaced`() {
        scheduler.keepPeriodicSync()
        val first = infos(WorkManagerSyncScheduler.PERIODIC).single()
        scheduler.keepPeriodicSync()

        val periodic = infos(WorkManagerSyncScheduler.PERIODIC).single()
        assertEquals(first.id, periodic.id)
        assertEquals(TimeUnit.HOURS.toMillis(6), periodic.periodicityInfo?.repeatIntervalMillis)
        assertTrue(periodic.constraints.requiresBatteryNotLow())
        assertEquals(NetworkType.CONNECTED, periodic.constraints.requiredNetworkType)
    }

    @Test
    fun `sign-out cancels both jobs`() = runTest {
        scheduler.requestSync()
        scheduler.keepPeriodicSync()

        scheduler.cancelAll()

        val all = infos(WorkManagerSyncScheduler.ONE_TIME) + infos(WorkManagerSyncScheduler.PERIODIC)
        assertTrue(all.all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun `failed runs back off exponentially from 30 seconds`() {
        // WorkInfo doesn't show the backoff, so this checks the request the scheduler builds.
        for (request in listOf(WorkManagerSyncScheduler.oneTimeRequest(), WorkManagerSyncScheduler.periodicRequest())) {
            assertEquals(androidx.work.BackoffPolicy.EXPONENTIAL, request.workSpec.backoffPolicy)
            assertEquals(30_000L, request.workSpec.backoffDelayDuration)
        }
    }
}
