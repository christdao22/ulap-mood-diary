package app.ulap.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

internal const val TEST_DEVICE_ID = "6a1f9b2e-1c3d-4e5f-8a9b-0c1d2e3f4a5b"

internal val testConfig = NetworkConfig(baseUrl = "http://localhost:8080/v1", versionName = "1.2.0", versionCode = 12)

/** The app's client on a MockEngine, so tests see each request and choose the answer. */
internal fun testClient(
    language: String? = "fil",
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): HttpClient = ulapHttpClient(
    engine = MockEngine(handler),
    config = testConfig,
    deviceId = { TEST_DEVICE_ID },
    language = { language },
)

internal fun MockRequestHandleScope.respondJson(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

internal fun MockRequestHandleScope.respondError(status: HttpStatusCode, code: String) =
    respondJson("""{"error":{"code":"$code","message":"test","requestId":"req_1"}}""", status)
