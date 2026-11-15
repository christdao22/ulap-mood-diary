package app.ulap.core.domain.erase

/**
 * Erases the diary on this phone: every check-in in Room and every setting in DataStore
 * (api-spec.md §7.3, "App actions after a 204"). The session's tokens are the session's own job.
 * Only "Delete account and all data" calls it.
 */
interface LocalDataEraser {
    suspend fun eraseAll()
}
