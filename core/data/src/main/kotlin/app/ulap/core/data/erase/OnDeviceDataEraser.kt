package app.ulap.core.data.erase

import app.ulap.core.common.dispatchers.IoDispatcher
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
 * `clearAllTables()` also runs VACUUM, so the deleted rows don't linger in free pages.
 */
internal class OnDeviceDataEraser @Inject constructor(
    private val database: UlapDatabase,
    private val settings: SettingsRepository,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : LocalDataEraser {
    override suspend fun eraseAll() {
        // clearAllTables() blocks and refuses to run on the main thread.
        withContext(io) { database.clearAllTables() }
        settings.clear()
    }
}
