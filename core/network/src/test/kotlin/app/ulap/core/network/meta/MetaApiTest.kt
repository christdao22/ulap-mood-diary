package app.ulap.core.network.meta

import app.ulap.core.network.ApiResult
import app.ulap.core.network.respondJson
import app.ulap.core.network.testClient
import app.ulap.shared.api.HotlineInfo
import app.ulap.shared.api.MetaResponse
import app.ulap.shared.api.PlatformVersions
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class MetaApiTest {
    @Test
    fun `meta is a GET without a token and reads the hotline`() = runTest {
        var seen: HttpRequestData? = null
        val api = KtorMetaApi(
            testClient { request ->
                seen = request
                respondJson(
                    """{"minAppVersion":{"android":1},"latestAppVersion":{"android":1},""" +
                        """"moodCatalogVersion":1,"hotline":{"name":"NCMH Crisis Hotline","number":"1553"}}""",
                )
            },
        )

        val expected = MetaResponse(
            minAppVersion = PlatformVersions(android = 1),
            latestAppVersion = PlatformVersions(android = 1),
            moodCatalogVersion = 1,
            hotline = HotlineInfo.NCMH,
        )
        assertEquals(ApiResult.Success(expected), api.meta())
        val request = checkNotNull(seen)
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/v1/meta", request.url.encodedPath)
        assertEquals(null, request.headers["Authorization"])
    }

    @Test
    fun `a server error comes back as a failure`() = runTest {
        val api = KtorMetaApi(testClient { respondError(HttpStatusCode.ServiceUnavailable) })

        assertEquals(503, assertIs<ApiResult.Failure>(api.meta()).status)
    }
}
