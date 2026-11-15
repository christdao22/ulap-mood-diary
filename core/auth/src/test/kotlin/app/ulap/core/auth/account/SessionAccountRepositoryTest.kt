package app.ulap.core.auth.account

import app.ulap.core.auth.session.SessionManager
import app.ulap.core.auth.session.SessionStore
import app.ulap.core.auth.session.testSessionStore
import app.ulap.core.domain.account.AccountProfile
import app.ulap.core.domain.account.AccountState
import app.ulap.core.domain.account.AuthProblem
import app.ulap.core.domain.account.SignInResult
import app.ulap.core.domain.erase.LocalDataEraser
import app.ulap.core.network.ApiResult
import app.ulap.core.network.auth.AuthApi
import app.ulap.core.network.auth.SessionProfile
import app.ulap.core.network.auth.SessionTokens
import app.ulap.core.network.auth.SignedInAuthApi
import app.ulap.core.network.user.UserApi
import app.ulap.shared.api.ErrorCode
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class SessionAccountRepositoryTest {
    private val dir = createTempDirectory("account").toFile()
    private val now = Instant.parse("2026-11-12T08:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }

    private val tokens = SessionTokens(
        userId = "user-1",
        accessToken = "access-1",
        accessTokenExpiresAt = now + 15.minutes,
        refreshToken = "refresh-1",
        refreshTokenExpiresAt = now + 60.days,
        isNewUser = true,
        profile = SessionProfile("Ana", "ana@example.com", false, Instant.parse("2026-10-01T00:00:00Z")),
    )

    /** Answers every sign-in with [next] and records what was sent, without passwords. */
    private class FakeAuthApi : AuthApi {
        val sent = mutableListOf<String>()
        var next: ApiResult<SessionTokens> = ApiResult.NetworkError(IOException("no answer set"))
        var forgot: ApiResult<Unit> = ApiResult.Success(Unit)
        var link: ApiResult<Unit> = ApiResult.Success(Unit)
        var refreshed: ApiResult<SessionTokens> = ApiResult.NetworkError(IOException("no answer set"))

        override suspend fun refresh(refreshToken: String) = refreshed.also { sent += "refresh:$refreshToken" }

        override suspend fun google(idToken: String, displayName: String?) = next.also { sent += "google:$displayName" }

        override suspend fun register(email: String, password: String) = next.also { sent += "register:$email" }

        override suspend fun login(email: String, password: String) = next.also { sent += "login:$email" }

        override suspend fun forgotPassword(email: String) = forgot.also { sent += "forgot:$email" }

        override suspend fun verifyEmail(token: String) = link.also { sent += "verify:$token" }

        override suspend fun resetPassword(token: String, newPassword: String) = link.also { sent += "reset:$token" }
    }

    private class FakeUserApi : UserApi {
        var calls = 0
        var delete: ApiResult<Unit> = ApiResult.Success(Unit)

        override suspend fun deleteMe() = delete.also { calls++ }
    }

    private class FakeEraser : LocalDataEraser {
        var erased = 0

        override suspend fun eraseAll() {
            erased++
        }
    }

    private class FakeSignedInAuthApi : SignedInAuthApi {
        val loggedOut = mutableListOf<String?>()
        var logout: ApiResult<Unit> = ApiResult.Success(Unit)

        override suspend fun logout(refreshToken: String?) = logout.also { loggedOut += refreshToken }

        override suspend fun resendVerification(): ApiResult<Unit> = ApiResult.Failure(429, ErrorCode.RATE_LIMITED)
    }

    private val authApi = FakeAuthApi()
    private val signedInApi = FakeSignedInAuthApi()
    private val userApi = FakeUserApi()
    private val eraser = FakeEraser()
    private lateinit var store: SessionStore

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun TestScope.repository(): SessionAccountRepository {
        val scope =
            CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext.job))
        store = testSessionStore(File(dir, "session.enc"), backgroundScope)
        val manager = SessionManager(store, authApi, clock, scope)
        return SessionAccountRepository(manager, authApi, signedInApi, userApi, eraser, scope)
    }

    private suspend fun SessionAccountRepository.signedIn() {
        authApi.next = ApiResult.Success(tokens)
        signInWithEmail("ana@example.com", "pw")
    }

    @Test
    fun `a sign-in saves the session and shows the profile`() = runTest {
        val repository = repository()
        authApi.next = ApiResult.Success(tokens)

        assertEquals(SignInResult.SignedIn(isNewUser = true), repository.createAccount(" Ana@Example.COM ", "pw"))

        // The server gets the email in its normal form (AccountRules).
        assertEquals(listOf("register:ana@example.com"), authApi.sent)
        assertEquals(
            AccountState.SignedIn(
                AccountProfile("Ana", "ana@example.com", false, Instant.parse("2026-10-01T00:00:00Z")),
            ),
            repository.state.first(),
        )
    }

    @Test
    fun `error codes become problems the screen can explain`() = runTest {
        val repository = repository()
        val cases = mapOf(
            ApiResult.Failure(401, ErrorCode.INVALID_CREDENTIALS) to AuthProblem.WRONG_EMAIL_OR_PASSWORD,
            ApiResult.Failure(409, ErrorCode.EMAIL_TAKEN) to AuthProblem.EMAIL_TAKEN,
            ApiResult.Failure(422, ErrorCode.WEAK_PASSWORD) to AuthProblem.WEAK_PASSWORD,
            ApiResult.Failure(401, ErrorCode.INVALID_GOOGLE_TOKEN) to AuthProblem.GOOGLE_NOT_ACCEPTED,
            ApiResult.Failure(426, ErrorCode.APP_UPDATE_REQUIRED) to AuthProblem.UPDATE_REQUIRED,
            ApiResult.Failure(429, ErrorCode.RATE_LIMITED) to AuthProblem.TOO_MANY_TRIES,
            ApiResult.Failure(503, null) to AuthProblem.SERVER,
            ApiResult.NetworkError(IOException("offline")) to AuthProblem.OFFLINE,
        )

        cases.forEach { (answer, problem) ->
            authApi.next = answer
            assertEquals(SignInResult.Failed(problem), repository.signInWithEmail("ana@example.com", "pw"), "$answer")
        }
        assertEquals(AccountState.Guest, repository.state.first())
    }

    @Test
    fun `a Google name the server wouldn't take is left out`() = runTest {
        val repository = repository()
        authApi.next = ApiResult.Success(tokens)

        repository.signInWithGoogle("id.token", "  ")
        repository.signInWithGoogle("id.token", " Ana ")

        assertEquals(listOf("google:null", "google:Ana"), authApi.sent)
    }

    @Test
    fun `a reset link and a resend report only whether they went through`() = runTest {
        val repository = repository()

        assertNull(repository.sendPasswordReset("ana@example.com"))
        authApi.forgot = ApiResult.NetworkError(IOException("offline"))
        assertEquals(AuthProblem.OFFLINE, repository.sendPasswordReset("ana@example.com"))
        assertEquals(AuthProblem.TOO_MANY_TRIES, repository.resendVerification())
    }

    @Test
    fun `sign-out tells the server, and signs out here even when the server can't be reached`() = runTest {
        val repository = repository()
        authApi.next = ApiResult.Success(tokens)
        repository.signInWithEmail("ana@example.com", "pw")
        signedInApi.logout = ApiResult.NetworkError(IOException("offline"))

        repository.signOut()

        assertEquals(listOf<String?>("refresh-1"), signedInApi.loggedOut)
        assertEquals(AccountState.Guest, repository.state.first())
    }

    @Test
    fun `delete erases the phone only after the server deleted the account`() = runTest {
        val repository = repository()
        repository.signedIn()

        assertNull(repository.deleteAccount())

        assertEquals(1, userApi.calls)
        assertEquals(1, eraser.erased)
        assertEquals(AccountState.Guest, repository.state.first())
        assertNull(store.current())
    }

    @Test
    fun `a delete that didn't reach the server erases nothing`() = runTest {
        val repository = repository()
        repository.signedIn()
        val cases = mapOf(
            ApiResult.NetworkError(IOException("offline")) to AuthProblem.OFFLINE,
            ApiResult.Failure(503, null) to AuthProblem.SERVER,
            ApiResult.Failure(429, ErrorCode.RATE_LIMITED) to AuthProblem.TOO_MANY_TRIES,
        )

        cases.forEach { (answer, problem) ->
            userApi.delete = answer
            assertEquals(problem, repository.deleteAccount(), "$answer")
        }

        assertEquals(0, eraser.erased)
        assertEquals("refresh-1", store.current()?.refreshToken)
    }

    @Test
    fun `an account that is already deleted erases the phone too`() = runTest {
        val repository = repository()
        repository.signedIn()
        userApi.delete = ApiResult.Failure(403, ErrorCode.ACCOUNT_DELETED)

        assertNull(repository.deleteAccount())

        assertEquals(1, eraser.erased)
        assertEquals(AccountState.Guest, repository.state.first())
    }

    @Test
    fun `delete without a session asks to sign in and never calls the server`() = runTest {
        val repository = repository()

        assertEquals(AuthProblem.SIGNED_OUT, repository.deleteAccount())

        assertEquals(0, userApi.calls)
        assertEquals(0, eraser.erased)
    }

    @Test
    fun `erasing this phone also clears Sign in again`() = runTest {
        val repository = repository()
        store.clear(signInAgain = true)

        repository.eraseThisPhone()

        assertEquals(1, eraser.erased)
        assertEquals(AccountState.Guest, repository.state.first())
    }

    @Test
    fun `a verified email refreshes the profile, and a spent link says so`() = runTest {
        val repository = repository()
        repository.signedIn()
        authApi.refreshed = ApiResult.Success(
            tokens.copy(refreshToken = "refresh-2", profile = tokens.profile.copy(emailVerified = true)),
        )

        assertNull(repository.verifyEmail("Zk3"))

        assertEquals(listOf("verify:Zk3", "refresh:refresh-1"), authApi.sent.takeLast(2))
        val state = repository.state.first() as AccountState.SignedIn
        assertEquals(true, state.profile.emailVerified)

        authApi.link = ApiResult.Failure(410, ErrorCode.TOKEN_USED_OR_EXPIRED)
        assertEquals(AuthProblem.LINK_USED_OR_EXPIRED, repository.verifyEmail("Zk3"))
        assertEquals(AuthProblem.LINK_USED_OR_EXPIRED, repository.resetPassword("Rr8", "Bag-ong-Password-2026"))
    }
}
