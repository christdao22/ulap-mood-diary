package app.ulap.core.domain.account

import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Signing in to back up and sync (technical-plan.md §2, api-spec.md §6). Optional: a guest uses
 * the whole app. Signing in or out never touches the check-ins on this phone.
 */
interface AccountRepository {
    /** Read from the session saved on this phone, never from the network, so it works offline. */
    val state: Flow<AccountState>

    /** Signs in, or signs up on first use, with an ID token from Credential Manager (api-spec.md §6.1). */
    suspend fun signInWithGoogle(idToken: String, displayName: String?): SignInResult

    /**
     * Creates an email account (api-spec.md §6.2). [email] and [password] are checked with
     * AccountRules and PasswordRules first. The person is signed in straight away; sync waits
     * until they open the link in the verification email.
     */
    suspend fun createAccount(email: String, password: String): SignInResult

    suspend fun signInWithEmail(email: String, password: String): SignInResult

    /**
     * Asks for a password reset link (api-spec.md §6.7). Null when the server took the request,
     * which it does whether or not the account exists.
     */
    suspend fun sendPasswordReset(email: String): AuthProblem?

    /** Sends the verification email again (api-spec.md §6.6). Null when it was sent. */
    suspend fun resendVerification(): AuthProblem?

    /** Signs out on the server if it can be reached, and on this phone always. Check-ins stay. */
    suspend fun signOut()
}

sealed interface AccountState {
    /** Never signed in, or signed out on purpose. */
    data object Guest : AccountState

    /** The server ended the session (api-spec.md §3, Client flow, step 3). Local data stays. */
    data object SignInAgain : AccountState

    data class SignedIn(val profile: AccountProfile) : AccountState
}

/** What the Account screen's profile card shows (design-spec.md §7.8). */
data class AccountProfile(
    val displayName: String?,
    val email: String?,
    /** False until the person opens the verification link. Sync waits for it (technical-plan.md §9). */
    val emailVerified: Boolean,
    /** Null only until the first refresh after an update that started keeping it. */
    val memberSince: Instant?,
) {
    override fun toString(): String = "AccountProfile(emailVerified=$emailVerified, memberSince=$memberSince)"
}

sealed interface SignInResult {
    /** [isNewUser] tells Phase 7's first sync whether to upload this phone's guest check-ins (api-spec.md §8.5). */
    data class SignedIn(val isNewUser: Boolean) : SignInResult

    data class Failed(val problem: AuthProblem) : SignInResult
}

/** Why an auth request didn't work, in terms the screen can explain (api-spec.md §4). */
enum class AuthProblem {
    WRONG_EMAIL_OR_PASSWORD,
    EMAIL_TAKEN,
    WEAK_PASSWORD,
    GOOGLE_NOT_ACCEPTED,
    OFFLINE,
    TOO_MANY_TRIES,
    UPDATE_REQUIRED,

    /** A 5xx, or anything the app doesn't expect. Worth trying again later. */
    SERVER,
}

/**
 * The Web OAuth client ID that Credential Manager asks Google to sign the ID token for
 * (api-spec.md §6.1). Blank in builds without one, which then hide the Google button (README §6.6).
 */
data class GoogleSignInConfig(val webClientId: String) {
    val isAvailable: Boolean get() = webClientId.isNotBlank()
}
