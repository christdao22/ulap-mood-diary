package app.ulap.core.auth.account

import app.ulap.core.auth.session.SessionManager
import app.ulap.core.auth.session.SessionState
import app.ulap.core.domain.account.AccountProfile
import app.ulap.core.domain.account.AccountRepository
import app.ulap.core.domain.account.AccountState
import app.ulap.core.domain.account.AuthProblem
import app.ulap.core.domain.account.SignInResult
import app.ulap.core.network.ApiResult
import app.ulap.core.network.auth.AuthApi
import app.ulap.core.network.auth.SessionProfile
import app.ulap.core.network.auth.SessionTokens
import app.ulap.core.network.auth.SignedInAuthApi
import app.ulap.shared.api.ErrorCode
import app.ulap.shared.validation.AccountRules
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [AccountRepository] on top of the session (SessionManager) and the auth API. The only place
 * where a sign-in's tokens are handed to the session, and where API error codes become
 * [AuthProblem]s. Passwords pass straight through to the request and are never kept.
 */
internal class SessionAccountRepository @Inject constructor(
    private val sessionManager: SessionManager,
    private val authApi: AuthApi,
    private val signedInAuthApi: SignedInAuthApi,
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

    override suspend fun signOut() {
        // Whatever the server answers, this phone signs out (SessionManager.signOut).
        sessionManager.signOut { refreshToken -> signedInAuthApi.logout(refreshToken) }
    }

    private suspend fun signIn(result: ApiResult<SessionTokens>): SignInResult = when (result) {
        is ApiResult.Success -> {
            sessionManager.signedIn(result.value)
            SignInResult.SignedIn(result.value.isNewUser)
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

/** api-spec.md §4: the app branches on the stable code, and on the status where codes are shared. */
private fun ApiResult.Failure.toProblem(): AuthProblem = when {
    code == ErrorCode.INVALID_CREDENTIALS -> AuthProblem.WRONG_EMAIL_OR_PASSWORD
    code == ErrorCode.EMAIL_TAKEN -> AuthProblem.EMAIL_TAKEN
    code == ErrorCode.WEAK_PASSWORD -> AuthProblem.WEAK_PASSWORD
    code == ErrorCode.INVALID_GOOGLE_TOKEN -> AuthProblem.GOOGLE_NOT_ACCEPTED
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

private const val UPGRADE_REQUIRED = 426
private const val TOO_MANY_REQUESTS = 429
