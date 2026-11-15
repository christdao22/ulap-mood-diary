package app.ulap.core.auth.account

import app.ulap.core.auth.session.SessionManager
import app.ulap.core.auth.session.SessionState
import app.ulap.core.common.dispatchers.ApplicationScope
import app.ulap.core.domain.account.AccountProfile
import app.ulap.core.domain.account.AccountRepository
import app.ulap.core.domain.account.AccountState
import app.ulap.core.domain.account.AuthProblem
import app.ulap.core.domain.account.SignInResult
import app.ulap.core.domain.erase.LocalDataEraser
import app.ulap.core.domain.sync.SyncSignInPreparer
import app.ulap.core.network.ApiResult
import app.ulap.core.network.auth.AuthApi
import app.ulap.core.network.auth.SessionProfile
import app.ulap.core.network.auth.SessionTokens
import app.ulap.core.network.auth.SignedInAuthApi
import app.ulap.core.network.user.UserApi
import app.ulap.shared.api.ErrorCode
import app.ulap.shared.validation.AccountRules
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * [AccountRepository] on top of the session (SessionManager) and the auth API. The only place
 * where a sign-in's tokens are handed to the session, and where API error codes become
 * [AuthProblem]s. Passwords pass straight through to the request and are never kept.
 */
// Hilt builds it; each dependency is one thing an account action touches (session, three APIs,
// this phone's diary, sync's handover, the app scope). Grouping them would only hide that.
@Suppress("LongParameterList")
internal class SessionAccountRepository @Inject constructor(
    private val sessionManager: SessionManager,
    private val authApi: AuthApi,
    private val signedInAuthApi: SignedInAuthApi,
    private val userApi: UserApi,
    private val eraser: LocalDataEraser,
    private val syncPreparer: SyncSignInPreparer,
    @param:ApplicationScope private val scope: CoroutineScope,
) : AccountRepository {
    override val state: Flow<AccountState> = sessionManager.state.map { state ->
        when (state) {
            SessionState.SignedOut -> AccountState.Guest
            SessionState.SignInAgain -> AccountState.SignInAgain
            is SessionState.SignedIn -> AccountState.SignedIn(state.profile.toAccountProfile())
        }
    }

    override suspend fun signInWithGoogle(idToken: String, displayName: String?): SignInResult =
        // The server takes a name of 1 to 50 characters (api-spec.md §5.1); anything else is left out.
        signIn(authApi.google(idToken, displayName?.trim()?.takeIf(AccountRules::isValidDisplayName)))

    override suspend fun createAccount(email: String, password: String): SignInResult =
        signIn(authApi.register(normalized(email), password))

    override suspend fun signInWithEmail(email: String, password: String): SignInResult =
        signIn(authApi.login(normalized(email), password))

    override suspend fun sendPasswordReset(email: String): AuthProblem? =
        authApi.forgotPassword(normalized(email)).problemOrNull()

    override suspend fun resendVerification(): AuthProblem? = signedInAuthApi.resendVerification().problemOrNull()

    override suspend fun verifyEmail(token: String): AuthProblem? {
        val problem = authApi.verifyEmail(token).problemOrNull()
        // The saved profile says "not verified" until a refresh brings the new one.
        if (problem == null) sessionManager.refreshProfile()
        return problem
    }

    override suspend fun resetPassword(token: String, newPassword: String): AuthProblem? =
        authApi.resetPassword(token, newPassword).problemOrNull()

    override suspend fun signOut() {
        // Whatever the server answers, this phone signs out (SessionManager.signOut).
        sessionManager.signOut { refreshToken -> signedInAuthApi.logout(refreshToken) }
    }

    /** The server first, the phone only after a 204 (api-spec.md §7.3, "App actions after a 204"). */
    override suspend fun deleteAccount(): AuthProblem? = runToEnd {
        if (sessionManager.state.first() !is SessionState.SignedIn) return@runToEnd AuthProblem.SIGNED_OUT
        val problem = when (val result = userApi.deleteMe()) {
            is ApiResult.Success -> null
            is ApiResult.Failure -> result.toDeleteProblem()
            is ApiResult.NetworkError -> AuthProblem.OFFLINE
        }
        if (problem == null) eraseEverything()
        problem
    }

    override suspend fun eraseThisPhone() {
        runToEnd { eraseEverything() }
    }

    /**
     * Runs [block] in the app-wide scope and waits for it, so a screen that closes meanwhile can't
     * stop a deletion between the server and the phone.
     */
    private suspend fun <T> runToEnd(block: suspend () -> T): T = scope.async { block() }.await()

    /** Tokens first, so nothing can reach the server with them while the diary is erased. */
    private suspend fun eraseEverything() {
        sessionManager.forget()
        eraser.eraseAll()
    }

    private suspend fun signIn(result: ApiResult<SessionTokens>): SignInResult = when (result) {
        is ApiResult.Success -> {
            val tokens = result.value
            // The check-ins are made ready first (api-spec.md §8.5), so sync, which starts when
            // the session appears, never sees this phone's check-ins before they're sorted out.
            // Both finish even if the sign-in sheet closes in between.
            runToEnd {
                syncPreparer.beforeSignIn(tokens.userId, tokens.isNewUser)
                sessionManager.signedIn(tokens)
            }
            SignInResult.SignedIn(tokens.isNewUser)
        }

        is ApiResult.Failure -> SignInResult.Failed(result.toProblem())

        is ApiResult.NetworkError -> SignInResult.Failed(AuthProblem.OFFLINE)
    }

    /** The forms check the email first; this only makes sure the server sees the same form of it. */
    private fun normalized(email: String) = AccountRules.normalizeEmail(email) ?: email.trim()
}

private fun ApiResult<Unit>.problemOrNull(): AuthProblem? = when (this) {
    is ApiResult.Success -> null
    is ApiResult.Failure -> toProblem()
    is ApiResult.NetworkError -> AuthProblem.OFFLINE
}

/**
 * `403 ACCOUNT_DELETED` means another phone, or an earlier try whose answer got lost, already
 * deleted it: the phone is erased as after a 204. A 401 here means the session ended.
 */
private fun ApiResult.Failure.toDeleteProblem(): AuthProblem? = when {
    code == ErrorCode.ACCOUNT_DELETED -> null
    status == UNAUTHORIZED -> AuthProblem.SIGNED_OUT
    else -> toProblem()
}

/** api-spec.md §4: the app branches on the stable code, and on the status where codes are shared. */
private fun ApiResult.Failure.toProblem(): AuthProblem = when {
    code == ErrorCode.INVALID_CREDENTIALS -> AuthProblem.WRONG_EMAIL_OR_PASSWORD
    code == ErrorCode.EMAIL_TAKEN -> AuthProblem.EMAIL_TAKEN
    code == ErrorCode.WEAK_PASSWORD -> AuthProblem.WEAK_PASSWORD
    code == ErrorCode.INVALID_GOOGLE_TOKEN -> AuthProblem.GOOGLE_NOT_ACCEPTED
    code == ErrorCode.TOKEN_USED_OR_EXPIRED -> AuthProblem.LINK_USED_OR_EXPIRED
    status == UPGRADE_REQUIRED -> AuthProblem.UPDATE_REQUIRED
    status == TOO_MANY_REQUESTS -> AuthProblem.TOO_MANY_TRIES
    else -> AuthProblem.SERVER
}

private fun SessionProfile?.toAccountProfile() = AccountProfile(
    displayName = this?.displayName,
    email = this?.email,
    // An old session without a profile is treated as verified, so it doesn't nag before the
    // next refresh brings the real answer.
    emailVerified = this?.emailVerified ?: true,
    memberSince = this?.createdAt,
)

private const val UNAUTHORIZED = 401
private const val UPGRADE_REQUIRED = 426
private const val TOO_MANY_REQUESTS = 429
