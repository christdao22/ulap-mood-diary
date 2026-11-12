package app.ulap.core.auth.session

import app.ulap.core.network.ApiResult
import app.ulap.core.network.SessionRefreshUnavailable
import app.ulap.core.network.auth.AuthApi
import app.ulap.core.network.auth.SessionProfile
import app.ulap.core.network.auth.SessionTokens
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

// runCurrent: lets a refresh start before the test lets the server answer.
@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {
    private val dir = createTempDirectory("session").toFile()
    private val file = File(dir, "session.enc")

    private val start = Instant.parse("2026-11-12T08:00:00Z")
    private val clock = object : Clock {
        var now = start

        override fun now() = now
    }

    /** Answers refreshes with [next], waiting on [gate] first when one is set. */
    private class FakeAuthApi : AuthApi {
        val sent = mutableListOf<String>()
        var gate: CompletableDeferred<Unit>? = null
        var next: (String) -> ApiResult<SessionTokens> = { error("no answer set") }

        override suspend fun refresh(refreshToken: String): ApiResult<SessionTokens> {
            sent += refreshToken
            gate?.await()
            return next(refreshToken)
        }

        override suspend fun google(idToken: String, displayName: String?) = error("not used")

        override suspend fun register(email: String, password: String) = error("not used")

        override suspend fun login(email: String, password: String) = error("not used")

        override suspend fun forgotPassword(email: String) = error("not used")
    }

    private val api = FakeAuthApi()

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun tokens(n: Int) = SessionTokens(
        userId = "user-1",
        accessToken = "access-$n",
        accessTokenExpiresAt = clock.now + 15.minutes,
        refreshToken = "refresh-$n",
        refreshTokenExpiresAt = clock.now + 60.days,
        isNewUser = false,
        profile = profile(verified = n > 1),
    )

    private fun profile(verified: Boolean) = SessionProfile("Ana", "ana@example.com", verified, start - 10.days)

    private fun TestScope.manager(): Pair<SessionManager, SessionStore> {
        val store = testSessionStore(file, backgroundScope)
        return SessionManager(store, api, clock, appScope()) to store
    }

    /** Like the app's ApplicationScope: a failed refresh stays inside its own Deferred. */
    private fun TestScope.appScope() =
        CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext.job))

    @Test
    fun `a guest has no access token and never calls the server`() = runTest {
        val (manager, _) = manager()

        assertNull(manager.accessToken())
        assertEquals(emptyList(), api.sent)
        assertEquals(SessionState.SignedOut, manager.state.first())
    }

    @Test
    fun `right after sign-in the access token comes from memory`() = runTest {
        val (manager, _) = manager()
        manager.signedIn(tokens(1))

        assertEquals("access-1", manager.accessToken())
        assertEquals(emptyList(), api.sent)
        assertEquals(SessionState.SignedIn("user-1", profile(verified = false)), manager.state.first())
    }

    @Test
    fun `an access token close to expiry is refreshed and the new refresh token saved`() = runTest {
        val (manager, store) = manager()
        manager.signedIn(tokens(1))
        clock.now += 14.minutes + 30.seconds
        api.next = { ApiResult.Success(tokens(2)) }

        assertEquals("access-2", manager.accessToken())
        assertEquals(listOf("refresh-1"), api.sent)
        assertEquals("refresh-2", store.current()?.refreshToken)
    }

    @Test
    fun `after a restart the first request refreshes, since access tokens live in memory only`() = runTest {
        val store = testSessionStore(file, backgroundScope)
        store.save(StoredSession("user-1", "refresh-1", (clock.now + 60.days).toEpochMilliseconds()))
        // A new manager over the saved file: what the app has after a restart.
        val manager = SessionManager(store, api, clock, appScope())
        api.next = { ApiResult.Success(tokens(2)) }

        assertEquals("access-2", manager.accessToken())
    }

    @Test
    fun `many requests at once share a single refresh`() = runTest {
        val (manager, _) = manager()
        manager.signedIn(tokens(1))
        clock.now += 20.minutes
        api.gate = CompletableDeferred()
        api.next = { ApiResult.Success(tokens(2)) }

        val calls = List(5) { async { manager.accessToken() } }
        runCurrent()
        api.gate?.complete(Unit)

        assertEquals(List(5) { "access-2" }, calls.awaitAll())
        assertEquals(listOf("refresh-1"), api.sent)
    }

    @Test
    fun `a TOKEN_EXPIRED for an old token returns the token another request already refreshed`() = runTest {
        val (manager, _) = manager()
        manager.signedIn(tokens(1))
        api.next = { ApiResult.Success(tokens(2)) }

        assertEquals("access-2", manager.refreshAfterExpired("access-1"))
        assertEquals("access-2", manager.refreshAfterExpired("access-1"))
        assertEquals(listOf("refresh-1"), api.sent)
    }

    @Test
    fun `a rejected refresh token ends the session and asks to sign in again`() = runTest {
        val (manager, store) = manager()
        manager.signedIn(tokens(1))
        api.next = { ApiResult.Failure(401, "REFRESH_TOKEN_REUSED") }

        assertNull(manager.refreshAfterExpired("access-1"))
        assertNull(store.current())
        assertEquals(SessionState.SignInAgain, manager.state.first())
        assertNull(manager.accessToken())
    }

    @Test
    fun `offline or a server error keeps the session and reports the refresh unavailable`() = runTest {
        val (manager, store) = manager()
        manager.signedIn(tokens(1))
        clock.now += 20.minutes

        api.next = { ApiResult.NetworkError(IOException("offline")) }
        assertFailsWith<SessionRefreshUnavailable> { manager.accessToken() }

        api.next = { ApiResult.Failure(503, "UNAVAILABLE") }
        assertFailsWith<SessionRefreshUnavailable> { manager.accessToken() }

        assertEquals("refresh-1", store.current()?.refreshToken)
        assertEquals(SessionState.SignedIn("user-1", profile(verified = false)), manager.state.first())
    }

    @Test
    fun `an expired refresh token signs out without calling the server`() = runTest {
        val (manager, _) = manager()
        manager.signedIn(tokens(1))
        clock.now += 61.days

        assertNull(manager.accessToken())
        assertEquals(emptyList(), api.sent)
        assertEquals(SessionState.SignInAgain, manager.state.first())
    }

    @Test
    fun `a refresh that finishes after sign-out doesn't bring the session back`() = runTest {
        val (manager, store) = manager()
        manager.signedIn(tokens(1))
        clock.now += 20.minutes
        api.gate = CompletableDeferred()
        api.next = { ApiResult.Success(tokens(2)) }

        val call = async { manager.accessToken() }
        runCurrent()
        manager.signOut {}
        api.gate?.complete(Unit)

        assertNull(call.await())
        assertNull(store.current())
        assertEquals(SessionState.SignedOut, manager.state.first())
    }

    @Test
    fun `a caller that goes away mid-refresh still gets the new refresh token saved`() = runTest {
        val (manager, store) = manager()
        manager.signedIn(tokens(1))
        clock.now += 20.minutes
        api.gate = CompletableDeferred()
        api.next = { ApiResult.Success(tokens(2)) }

        val screen = launch { manager.accessToken() }
        runCurrent()
        screen.cancel()
        api.gate?.complete(Unit)
        runCurrent()

        assertEquals("refresh-2", store.current()?.refreshToken)
        assertEquals("access-2", manager.accessToken())
        assertEquals(listOf("refresh-1"), api.sent)
    }

    @Test
    fun `a refresh brings the latest profile, such as a verified email`() = runTest {
        val (manager, _) = manager()
        manager.signedIn(tokens(1))
        clock.now += 20.minutes
        api.next = { ApiResult.Success(tokens(2)) }

        manager.accessToken()

        assertEquals(SessionState.SignedIn("user-1", profile(verified = true)), manager.state.first())
    }

    @Test
    fun `sign-out tells the server with the current refresh token, then forgets the session`() = runTest {
        val (manager, store) = manager()
        manager.signedIn(tokens(1))
        val toldServer = mutableListOf<String>()

        manager.signOut { refreshToken ->
            // Still signed in while the server is told, so the logout request has a token.
            assertEquals("access-1", manager.accessToken())
            toldServer += refreshToken
        }

        assertEquals(listOf("refresh-1"), toldServer)
        assertNull(store.current())
        assertNull(manager.accessToken())
        assertEquals(SessionState.SignedOut, manager.state.first())
    }

    @Test
    fun `a guest signing out doesn't call the server`() = runTest {
        val (manager, _) = manager()
        var called = false

        manager.signOut { called = true }

        assertEquals(false, called)
        assertEquals(SessionState.SignedOut, manager.state.first())
    }
}
