package app.ulap.server.common

import app.ulap.server.error
import app.ulap.server.testConfig
import app.ulap.server.withTestServer
import app.ulap.shared.api.Api
import app.ulap.shared.api.ErrorCode
import app.ulap.shared.api.ErrorDetail
import app.ulap.shared.api.StatusResponse
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** Error mapping, the app-version gate and rate limits (api-spec.md §2, §4, §10). */
class FoundationsTest {
    private val echo: Route.() -> Unit = {
        get("/test/ok") { call.respond(StatusResponse.OK) }
        post("/test/echo") { call.respond(call.receive<StatusResponse>()) }
        get("/test/invalid") {
            throw ApiException.validationFailed(listOf(ErrorDetail("changes[2].stress", "OUT_OF_RANGE")))
        }
    }

    @Test
    fun malformedJsonIs400BadRequest() = withTestServer(extraRoutes = echo) {
        val response = client.post("/v1/test/echo") {
            contentType(ContentType.Application.Json)
            setBody("""{"status":""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ErrorCode.BAD_REQUEST, response.error().code)
    }

    @Test
    fun aBodyThatIsNotJsonIs400BadRequest() = withTestServer(extraRoutes = echo) {
        val response = client.post("/v1/test/echo") {
            contentType(ContentType.Text.Plain)
            setBody("hello")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ErrorCode.BAD_REQUEST, response.error().code)
    }

    @Test
    fun validationFailuresCarryTheirDetails() = withTestServer(extraRoutes = echo) {
        val response = client.get("/v1/test/invalid")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.error()
        assertEquals(ErrorCode.VALIDATION_FAILED, error.code)
        assertEquals(listOf(ErrorDetail("changes[2].stress", "OUT_OF_RANGE")), error.details)
        assertEquals(response.headers[Api.HEADER_REQUEST_ID], error.requestId)
    }

    @Test
    fun aWrongMethodIs404LikeAnUnknownRoute() = withTestServer {
        val response = client.post("/v1/health")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(ErrorCode.NOT_FOUND, response.error().code)
    }

    @Test
    fun anOldAndroidBuildGets426() = withTestServer(config = minAndroid(5), extraRoutes = echo) {
        val response = client.get("/v1/test/ok") { header(Api.HEADER_APP_VERSION, "android/1.0.0 (4)") }

        assertEquals(HttpStatusCode.UpgradeRequired, response.status)
        assertEquals(ErrorCode.APP_UPDATE_REQUIRED, response.error().code)
    }

    @Test
    fun theMinimumBuildAndNewerPass() = withTestServer(config = minAndroid(5), extraRoutes = echo) {
        listOf("android/1.1.0 (5)", "android/2.0.0 (40)").forEach { version ->
            val response = client.get("/v1/test/ok") { header(Api.HEADER_APP_VERSION, version) }
            assertEquals(HttpStatusCode.OK, response.status, version)
        }
    }

    @Test
    fun aMissingOrUnreadableVersionPasses() = withTestServer(config = minAndroid(5), extraRoutes = echo) {
        assertEquals(HttpStatusCode.OK, client.get("/v1/test/ok").status)
        val garbled = client.get("/v1/test/ok") { header(Api.HEADER_APP_VERSION, "curl/8.0") }
        assertEquals(HttpStatusCode.OK, garbled.status)
    }

    @Test
    fun healthReadyAndMetaSkipTheVersionGate() = withTestServer(config = minAndroid(5)) {
        listOf("/v1/health", "/v1/meta", "/v1/ready").forEach { path ->
            val response = client.get(path) { header(Api.HEADER_APP_VERSION, "android/1.0.0 (1)") }
            // /ready is 503 here because the test database is down, which still means "not 426".
            assertNotEquals(HttpStatusCode.UpgradeRequired, response.status, path)
        }
    }

    @Test
    fun overTheLimitIs429WithRetryAfter() =
        withTestServer(rateLimits = RateLimitSettings(RateLimitRule(2, 1.minutes))) {
            repeat(2) { assertEquals(HttpStatusCode.OK, client.get("/v1/health").status) }

            val response = client.get("/v1/health")

            assertEquals(HttpStatusCode.TooManyRequests, response.status)
            assertEquals(ErrorCode.RATE_LIMITED, response.error().code)
            val retryAfter = assertNotNull(response.headers[HttpHeaders.RetryAfter]).toInt()
            assertTrue(retryAfter in 1..60, "Retry-After was $retryAfter")
        }

    @Test
    fun eachClientIpHasItsOwnLimit() = withTestServer(rateLimits = RateLimitSettings(RateLimitRule(1, 1.minutes))) {
        val first = client.get("/v1/health") { header(RateLimits.HEADER_CLIENT_IP, "203.0.113.1") }
        val other = client.get("/v1/health") { header(RateLimits.HEADER_CLIENT_IP, "203.0.113.2") }
        val again = client.get("/v1/health") { header(RateLimits.HEADER_CLIENT_IP, "203.0.113.1") }

        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(HttpStatusCode.OK, other.status)
        assertEquals(HttpStatusCode.TooManyRequests, again.status)
    }

    private fun minAndroid(code: Int) = testConfig().copy(minAndroidVersionCode = code)
}
