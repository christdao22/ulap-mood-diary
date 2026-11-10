package app.ulap.server.meta

import app.ulap.server.testConfig
import app.ulap.server.withTestServer
import app.ulap.shared.api.Api
import app.ulap.shared.api.HotlineInfo
import app.ulap.shared.api.MetaResponse
import app.ulap.shared.api.PlatformVersions
import app.ulap.shared.catalog.MoodCatalog
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class MetaRoutesTest {
    @Test
    fun reportsVersionsCatalogAndHotline() = withTestServer(
        config = testConfig().copy(minAndroidVersionCode = 3, latestAndroidVersionCode = 7),
    ) {
        val response = client.get("/v1/meta")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            MetaResponse(
                minAppVersion = PlatformVersions(android = 3),
                latestAppVersion = PlatformVersions(android = 7),
                moodCatalogVersion = MoodCatalog.VERSION,
                hotline = HotlineInfo("NCMH Crisis Hotline", "1553"),
            ),
            Api.json.decodeFromString<MetaResponse>(response.bodyAsText()),
        )
    }
}
