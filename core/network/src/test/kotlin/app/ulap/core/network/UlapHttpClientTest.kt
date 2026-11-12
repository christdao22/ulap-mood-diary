package app.ulap.core.network

import app.ulap.shared.api.Api
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class UlapHttpClientTest {
    @Test
    fun `every request carries the device, app version and language headers`() = runTest {
        var seen: HttpRequestData? = null
        val client = testClient(language = "ceb") { request ->
            seen = request
            respondJson("{}")
        }

        client.get("meta")

        val request = checkNotNull(seen)
        assertEquals(TEST_DEVICE_ID, request.headers[Api.HEADER_DEVICE_ID])
        assertEquals("android/1.2.0 (12)", request.headers[Api.HEADER_APP_VERSION])
        assertEquals("ceb", request.headers[HttpHeaders.AcceptLanguage])
    }

    @Test
    fun `no language chosen yet means no Accept-Language header`() = runTest {
        var seen: HttpRequestData? = null
        val client = testClient(language = null) { request ->
            seen = request
            respondJson("{}")
        }

        client.get("meta")

        assertNull(checkNotNull(seen).headers[HttpHeaders.AcceptLanguage])
    }

    @Test
    fun `relative paths land under v1`() = runTest {
        var seen: HttpRequestData? = null
        val client = testClient { request ->
            seen = request
            respondJson("{}")
        }

        client.get("auth/refresh")

        assertEquals("http://localhost:8080/v1/auth/refresh", checkNotNull(seen).url.toString())
    }

    @Test
    fun `requests never carry a bearer token without the bearer plugin`() = runTest {
        var seen: HttpRequestData? = null
        val client = testClient { request ->
            seen = request
            respondJson("{}")
        }

        client.get("meta")

        assertNull(checkNotNull(seen).headers[HttpHeaders.Authorization])
    }
}
