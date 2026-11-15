package app.ulap.core.data.erase

import app.ulap.core.common.dispatchers.IoDispatcher
import app.ulap.core.database.SyncLock
import app.ulap.core.database.UlapDatabase
import app.ulap.core.domain.erase.LocalDataEraser
import app.ulap.core.domain.settings.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Empties every Room table, then resets the settings (api-spec.md §7.3). Settings go last: the
 * reset clears "onboarding done", which sends the app back to onboarding, and by then the diary
 * is already gone.
 *
 * The database file and its passphrase stay. The file is encrypted, and Room's
 * `clearAllTables()` also runs VACUUM, so the deleted rows don't linger in free pages. That also
 * empties the sync position, so a later sign-in starts sync from nothing.
 *
 * It waits for a sync run that's under way ([SyncLock]), so a pull can't write check-ins back
 * after they were erased. The session is already gone by then, so no new run can start.
 */
internal class OnDeviceDataEraser @Inject constructor(
    private val database: UlapDatabase,
    private val settings: SettingsRepository,
    private val syncLock: SyncLock,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : LocalDataEraser {
    override suspend fun eraseAll() {
        // clearAllTables() blocks and refuses to run on the main thread.
        syncLock.withLock { withContext(io) { database.clearAllTables() } }
        settings.clear()
    }
}
