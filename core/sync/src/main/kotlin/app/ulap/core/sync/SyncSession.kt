package app.ulap.core.sync

import app.ulap.core.auth.session.SessionManager
import app.ulap.core.auth.session.SessionState
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The signed-in person, as far as sync needs to know. */
internal data class SyncAccount(val userId: String, val emailVerified: Boolean)

/** Who is signed in. Sync runs only while someone is. */
internal interface SyncSession {
    /** Null for a guest, and after the server ended the session ("Sign in again"). */
    val account: Flow<SyncAccount?>
}

internal class SessionManagerSyncSession @Inject constructor(sessionManager: SessionManager) : SyncSession {
    override val account: Flow<SyncAccount?> = sessionManager.state
        .map { state ->
            (state as? SessionState.SignedIn)?.let {
                // An old session without a profile counts as verified; the server has the last word.
                SyncAccount(it.userId, emailVerified = it.profile?.emailVerified ?: true)
            }
        }
        .distinctUntilChanged()
}
