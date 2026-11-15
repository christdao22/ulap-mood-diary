package app.ulap.core.domain.sync

import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Backup and sync as the Account screen sees it (design-spec.md §7.8). Everything here is read
 * from this phone, so it works offline. When sync runs is SyncTriggers' job (D8): this only
 * reports, and passes on the person's own requests.
 */
interface SyncRepository {
    val status: Flow<SyncStatus>

    /** Account → "Sync now". Runs as soon as there's a network. */
    suspend fun syncNow()

    /**
     * "Try again" on check-ins that stopped trying to upload (technical-plan.md §8, When a change
     * can't upload). They go back in the queue, and SyncTriggers picks them up.
     */
    suspend fun retryStuck()

    /**
     * The answer to "Add this phone's check-ins to your account?" (api-spec.md §8.5). [add] uploads
     * them; otherwise they're deleted from this phone and the account's check-ins come down.
     */
    suspend fun answerAddCheckIns(add: Boolean)
}

data class SyncStatus(
    /** When the last sync finished. Null before the first one. */
    val lastSyncedAt: Instant? = null,
    /** Check-ins saved here that haven't reached the server yet, and are still trying. */
    val waitingCount: Int = 0,
    /** Check-ins that stopped trying. Account says "%d check-ins couldn't back up yet". Never how many tries. */
    val stuckCount: Int = 0,
    val running: Boolean = false,
    /** Whether this phone has a network right now. */
    val online: Boolean = true,
    /** Signed in to an account that has check-ins already, and this phone has some too: ask first. */
    val askToAddCheckIns: Boolean = false,
)

/**
 * Gets this phone's check-ins ready for an account, right before its session is saved
 * (api-spec.md §8.5). AccountRepository calls it on every successful sign-in.
 */
fun interface SyncSignInPreparer {
    /**
     * [isNewUser] comes from the sign-in answer. The same account as before carries on. A new
     * account uploads everything. An account with check-ins elsewhere asks first, if this phone
     * has check-ins of its own.
     */
    suspend fun beforeSignIn(userId: String, isNewUser: Boolean)
}
