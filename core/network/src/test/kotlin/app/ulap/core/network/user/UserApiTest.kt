package app.ulap.core.network.user

import app.ulap.core.network.ApiResult
import app.ulap.core.network.respondError
import app.ulap.core.network.testClient
import app.ulap.shared.api.ErrorCode
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class UserApiTest {
    @Test
    fun `delete me sends DELETE with the confirmation`() = runTest {
        var seen: HttpRequestData? = null
        val api = KtorUserApi(
            testClient { request ->
                seen = request
                respond("", HttpStatusCode.NoContent)
            },
        )

        assertEquals(ApiResult.Success(Unit), api.deleteMe())

        val request = checkNotNull(seen)
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("/v1/me", request.url.encodedPath)
        assertEquals(
            """{"confirm":"DELETE"}""",
            (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString(),
        )
    }

    @Test
    fun `an account that is already gone comes back with its code`() = runTest {
        val api = KtorUserApi(testClient { respondError(HttpStatusCode.Forbidden, ErrorCode.ACCOUNT_DELETED) })

        assertEquals(ApiResult.Failure(403, ErrorCode.ACCOUNT_DELETED), api.deleteMe())
    }
}
