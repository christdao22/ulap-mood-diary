package app.ulap.core.database

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lets one thing at a time change the diary as a whole: a sync run, a sign-in's handover, or
 * erasing the phone. Without it, a pull that was on its way could write check-ins back after
 * "Delete account and all data" emptied the database.
 */
@Singleton
class SyncLock @Inject constructor() {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
