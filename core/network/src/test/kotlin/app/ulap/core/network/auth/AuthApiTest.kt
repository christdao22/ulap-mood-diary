package app.ulap.core.network.auth

import app.ulap.core.network.ApiResult
import app.ulap.core.network.respondError
import app.ulap.core.network.respondJson
import app.ulap.core.network.testClient
import app.ulap.shared.api.ErrorCode
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class AuthApiTest {
    private val authResponse = """
        {
          "accessToken": "access.jwt",
          "accessTokenExpiresAt": "2026-11-12T08:15:00Z",
          "refreshToken": "refresh-2",
          "refreshTokenExpiresAt": "2027-01-11T08:00:00Z",
          "user": {
            "id": "user-1",
            "language": "fil",
            "mascotEnabled": true,
            "identities": [],
            "createdAt": "2026-11-01T00:00:00Z"
          },
          "isNewUser": false
        }
    """.trimIndent()

    @Test
    fun `refresh posts the refresh token and returns the new tokens`() = runTest {
        var seen: HttpRequestData? = null
        val api = KtorAuthApi(
            testClient { request ->
                seen = request
                respondJson(authResponse)
            },
        )

        val result = api.refresh("refresh-1")

        val request = checkNotNull(seen)
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/v1/auth/refresh", request.url.encodedPath)
        assertEquals(
            """{"refreshToken":"refresh-1"}""",
            (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString(),
        )
        assertEquals(
            ApiResult.Success(
                SessionTokens(
                    userId = "user-1",
                    accessToken = "access.jwt",
                    accessTokenExpiresAt = Instant.parse("2026-11-12T08:15:00Z"),
                    refreshToken = "refresh-2",
                    refreshTokenExpiresAt = Instant.parse("2027-01-11T08:00:00Z"),
                    isNewUser = false,
                ),
            ),
            result,
        )
    }

    @Test
    fun `a rejected refresh token comes back as a failure with its code`() = runTest {
        val api = KtorAuthApi(testClient { respondError(HttpStatusCode.Unauthorized, ErrorCode.REFRESH_TOKEN_REUSED) })

        val result = api.refresh("refresh-1")

        assertEquals(ApiResult.Failure(401, ErrorCode.REFRESH_TOKEN_REUSED), result)
        assertFalse((result as ApiResult.Failure).isRetryable)
    }

    @Test
    fun `an error page that isn't the standard shape has no code and 5xx is retryable`() = runTest {
        val api = KtorAuthApi(testClient { respondJson("<html>Bad gateway</html>", HttpStatusCode.BadGateway) })

        val result = api.refresh("refresh-1")

        assertEquals(ApiResult.Failure(502, null), result)
        assertTrue((result as ApiResult.Failure).isRetryable)
    }

    @Test
    fun `no connection comes back as a network error`() = runTest {
        val api = KtorAuthApi(testClient { throw IOException("offline") })

        assertIs<ApiResult.NetworkError>(api.refresh("refresh-1"))
    }

    @Test
    fun `toString never shows the tokens`() {
        val tokens =
            SessionTokens("user-1", "access.jwt", Instant.DISTANT_FUTURE, "refresh-2", Instant.DISTANT_FUTURE, false)

        assertFalse("access.jwt" in tokens.toString())
        assertFalse("refresh-2" in tokens.toString())
    }
}
