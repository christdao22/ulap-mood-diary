package app.ulap.android

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.ulap.android.crash.CrashReporting
import app.ulap.core.data.meta.HotlineUpdater
import app.ulap.core.sync.SyncTriggers
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * The Application class. Hilt builds the app's dependency graph from here (D2).
 *
 * It also starts SyncTriggers, the one place that decides when sync runs (D8), and hands
 * WorkManager Hilt's worker factory, so SyncWorker gets its SyncEngine injected. WorkManager's own
 * start-up is turned off in the manifest for that reason. And it asks `/meta` once for a changed
 * hotline number (api-spec.md §9.3); the built-in NCMH 1553 shows until then, and offline.
 */
@HiltAndroidApp
class UlapApp :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncTriggers: SyncTriggers

    @Inject
    lateinit var hotlineUpdater: HotlineUpdater

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // First, so a crash while starting up is reported too. Without a DSN it does nothing.
        CrashReporting.start(this)
        syncTriggers.start()
        hotlineUpdater.start()
    }
}
