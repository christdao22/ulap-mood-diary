package app.ulap.android

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.ulap.core.sync.SyncTriggers
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * The Application class. Hilt builds the app's dependency graph from here (D2).
 *
 * It also starts SyncTriggers, the one place that decides when sync runs (D8), and hands
 * WorkManager Hilt's worker factory, so SyncWorker gets its SyncEngine injected. WorkManager's own
 * start-up is turned off in the manifest for that reason.
 */
@HiltAndroidApp
class UlapApp :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncTriggers: SyncTriggers

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        syncTriggers.start()
    }
}
