package app.ulap.core.auth.session

import app.ulap.core.common.dispatchers.ApplicationScope
import app.ulap.core.network.AccessTokenSource
import app.ulap.core.network.ApiResult
import app.ulap.core.network.SessionRefreshUnavailable
import app.ulap.core.network.auth.AuthApi
import app.ulap.core.network.auth.SessionProfile
import app.ulap.core.network.auth.SessionTokens
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Whether someone is signed in on this phone. Guests use the app fully, just without sync. */
sealed interface SessionState {
    data object SignedOut : SessionState

    /** The server ended the session. Local data stays; the app says "Sign in again to keep syncing." */
    data object SignInAgain : SessionState

    /** [profile] is null only for a session saved before profiles were kept; the next refresh fills it. */
    data class SignedIn(val userId: String, val profile: SessionProfile?) : SessionState
}

/**
 * The signed-in session: the refresh token on disk (encrypted, [SessionStore]) and the access
 * token in memory only (api-spec.md §3). The bearer plugin asks it for tokens through
 * [AccessTokenSource].
 *
 * Only one refresh runs at a time (api-spec.md §3, Client flow, step 4). That matters more than
 * it looks: every refresh rotates the refresh token, so two refreshes with the same token would
 * look like theft to the server, which then signs this phone out (`REFRESH_TOKEN_REUSED`).
 * The refresh also runs in [scope], not in the caller's coroutine, so a screen that closes
 * mid-refresh can't drop the new refresh token before it's saved.
 */
@Singleton
class SessionManager @Inject internal constructor(
    private val store: SessionStore,
    private val authApi: AuthApi,
    private val clock: Clock,
    @param:ApplicationScope private val scope: CoroutineScope,
) : AccessTokenSource {
    private class AccessToken(val value: String, val expiresAt: Instant) {
        /** Refreshes a little early, so a token doesn't expire on its way to the server. */
        fun isFreshAt(now: Instant) = expiresAt - EXPIRY_MARGIN > now
    }

    @Volatile
    private var access: AccessToken? = null

    /** Guards the choice to start a refresh; the refresh itself runs as [inFlight]. */
    private val mutex = Mutex()
    private var inFlight: Deferred<String?>? = null

    val state: Flow<SessionState> = store.file
        .map { file ->
            when {
                file.session != null -> SessionState.SignedIn(file.session.userId, file.session.profile?.toProfile())
                file.signInAgain -> SessionState.SignInAgain
                else -> SessionState.SignedOut
            }
        }
        .distinctUntilChanged()

    /** A sign-in (Google or email) succeeded: keep its tokens. Replaces any earlier session. */
    suspend fun signedIn(tokens: SessionTokens) {
        mutex.withLock {
            store.save(tokens.toStored())
            access = AccessToken(tokens.accessToken, tokens.accessTokenExpiresAt)
        }
    }

    /**
     * The person signs out. [endOnServer] tells the server (`/auth/logout`, api-spec.md §6.10)
     * while the tokens still work; then the session is forgotten here, whatever the server said.
     * Offline still signs out: the person asked to, and the refresh token expires by itself.
     *
     * [endOnServer] runs outside the mutex, because its request may need a refresh, which takes
     * the mutex. The whole sign-out runs in [scope], so closing the screen can't stop it halfway.
     */
    suspend fun signOut(endOnServer: suspend (refreshToken: String) -> Unit) {
        scope.async {
            store.current()?.let { endOnServer(it.refreshToken) }
            signedOut()
        }.await()
    }

    /** Forgets the session on this phone. Check-ins stay. */
    private suspend fun signedOut() {
        mutex.withLock {
            access = null
            store.clear(signInAgain = false)
        }
    }

    override suspend fun accessToken(): String? = when {
        store.current() == null -> null
        else -> access?.takeIf { it.isFreshAt(clock.now()) }?.value ?: refreshOnce(rejected = null)
    }

    override suspend fun refreshAfterExpired(rejected: String): String? = refreshOnce(rejected)

    /**
     * Joins the refresh that's running, or starts one. A token that another caller refreshed
     * meanwhile is returned straight away, unless it's the one the server just [rejected].
     */
    private suspend fun refreshOnce(rejected: String?): String? {
        val refresh = mutex.withLock {
            access?.takeIf { it.isFreshAt(clock.now()) && it.value != rejected }?.let { return it.value }
            inFlight?.takeIf { it.isActive } ?: scope.async { refresh() }.also { inFlight = it }
        }
        return refresh.await()
    }

    private suspend fun refresh(): String? {
        val stored = store.current()
        return when {
            stored == null -> null

            // No point asking the server: it would answer REFRESH_TOKEN_INVALID.
            Instant.fromEpochMilliseconds(stored.refreshTokenExpiresAt) <= clock.now() -> {
                endSession(stored)
                null
            }

            else -> refresh(stored)
        }
    }

    private suspend fun refresh(stored: StoredSession): String? =
        when (val result = authApi.refresh(stored.refreshToken)) {
            is ApiResult.Success -> refreshed(stored, result.value)

            is ApiResult.Failure -> refreshRejected(stored, result)

            is ApiResult.NetworkError -> throw SessionRefreshUnavailable(
                "Refresh couldn't reach the server",
                result.cause,
            )
        }

    /** Saves the rotated tokens. Skipped when the person signed out (or in again) while it ran. */
    private suspend fun refreshed(stored: StoredSession, tokens: SessionTokens): String? =
        if (store.replace(stored.refreshToken, tokens.toStored())) {
            access = AccessToken(tokens.accessToken, tokens.accessTokenExpiresAt)
            tokens.accessToken
        } else {
            null
        }

    private suspend fun refreshRejected(stored: StoredSession, failure: ApiResult.Failure): String? {
        // 429, 5xx, 426 and the like: the session is fine, the server just can't refresh now.
        if (failure.status != UNAUTHORIZED) throw SessionRefreshUnavailable("Refresh failed with ${failure.status}")
        // REFRESH_TOKEN_INVALID or REFRESH_TOKEN_REUSED: this session is over (api-spec.md §3).
        endSession(stored)
        return null
    }

    private suspend fun endSession(stored: StoredSession) {
        if (store.clearIf(stored.refreshToken)) access = null
    }

    private companion object {
        const val UNAUTHORIZED = 401
        val EXPIRY_MARGIN = 60.seconds
    }
}

private fun SessionTokens.toStored() = StoredSession(
    userId = userId,
    refreshToken = refreshToken,
    refreshTokenExpiresAt = refreshTokenExpiresAt.toEpochMilliseconds(),
    profile = StoredProfile(
        displayName = profile.displayName,
        email = profile.email,
        emailVerified = profile.emailVerified,
        createdAt = profile.createdAt.toEpochMilliseconds(),
    ),
)

private fun StoredProfile.toProfile() = SessionProfile(
    displayName = displayName,
    email = email,
    emailVerified = emailVerified,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
)
