package app.ulap.core.network

import app.ulap.shared.api.ErrorCode
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class BearerAuthTest {
    /** Hands out [current], and [refreshed] once a request says the token expired. */
    private class FakeTokens(var current: String?, private val refreshed: String? = "fresh") : AccessTokenSource {
        val rejected = mutableListOf<String>()
        var unavailable = false

        override suspend fun accessToken(): String? {
            if (unavailable) throw SessionRefreshUnavailable("offline")
            return current
        }

        override suspend fun refreshAfterExpired(rejected: String): String? {
            this.rejected += rejected
            current = refreshed
            return refreshed
        }
    }

    @Test
    fun `sends the access token as a bearer header`() = runTest {
        val sent = mutableListOf<String?>()
        val client = testClient { request ->
            sent += request.headers[HttpHeaders.Authorization]
            respondJson("{}")
        }.withBearerAuth(FakeTokens("abc"))

        client.get("me")

        assertEquals(listOf<String?>("Bearer abc"), sent)
    }

    @Test
    fun `signed out sends no bearer header`() = runTest {
        val sent = mutableListOf<String?>()
        val client = testClient { request ->
            sent += request.headers[HttpHeaders.Authorization]
            respondJson("{}")
        }.withBearerAuth(FakeTokens(null))

        client.get("me")

        assertEquals(listOf<String?>(null), sent)
    }

    @Test
    fun `TOKEN_EXPIRED refreshes once and retries with the new token`() = runTest {
        val sent = mutableListOf<String?>()
        val tokens = FakeTokens("old")
        val client = testClient { request ->
            val auth = request.headers[HttpHeaders.Authorization]
            sent += auth
            if (auth == "Bearer old") {
                respondError(HttpStatusCode.Unauthorized, ErrorCode.TOKEN_EXPIRED)
            } else {
                respondJson("""{"ok":true}""")
            }
        }.withBearerAuth(tokens)

        val result = client.apiCall<Map<String, Boolean>> { get("me") }

        assertEquals(ApiResult.Success(mapOf("ok" to true)), result)
        assertEquals(listOf("old"), tokens.rejected)
        assertEquals(listOf<String?>("Bearer old", "Bearer fresh"), sent)
    }

    @Test
    fun `a second TOKEN_EXPIRED after the refresh goes back to the caller`() = runTest {
        var calls = 0
        val tokens = FakeTokens("old")
        val client = testClient {
            calls++
            respondError(HttpStatusCode.Unauthorized, ErrorCode.TOKEN_EXPIRED)
        }.withBearerAuth(tokens)

        val result = client.apiCall<Unit> { get("me") }

        assertEquals(ApiResult.Failure(401, ErrorCode.TOKEN_EXPIRED), result)
        assertEquals(2, calls)
        assertEquals(1, tokens.rejected.size)
    }

    @Test
    fun `UNAUTHENTICATED is not refreshed`() = runTest {
        var calls = 0
        val tokens = FakeTokens("abc")
        val client = testClient {
            calls++
            respondError(HttpStatusCode.Unauthorized, ErrorCode.UNAUTHENTICATED)
        }.withBearerAuth(tokens)

        val result = client.apiCall<Unit> { get("me") }

        assertEquals(ApiResult.Failure(401, ErrorCode.UNAUTHENTICATED), result)
        assertEquals(1, calls)
        assertEquals(emptyList(), tokens.rejected)
    }

    @Test
    fun `a session that ended during the refresh returns the original 401`() = runTest {
        var calls = 0
        val client = testClient {
            calls++
            respondError(HttpStatusCode.Unauthorized, ErrorCode.TOKEN_EXPIRED)
        }.withBearerAuth(FakeTokens("old", refreshed = null))

        val result = client.apiCall<Unit> { get("me") }

        assertEquals(ApiResult.Failure(401, ErrorCode.TOKEN_EXPIRED), result)
        assertEquals(1, calls)
    }

    @Test
    fun `a refresh that can't reach the server ends the call as a network error`() = runTest {
        var calls = 0
        val tokens = FakeTokens("abc").apply { unavailable = true }
        val client = testClient {
            calls++
            respondJson("{}")
        }.withBearerAuth(tokens)

        val result = client.apiCall<Unit> { get("me") }

        assertIs<ApiResult.NetworkError>(result)
        assertEquals(0, calls)
    }
}
