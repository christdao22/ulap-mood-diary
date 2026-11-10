package app.ulap.shared.api

import kotlin.test.Test
import kotlin.test.assertEquals

class ApiTest {
    @Test
    fun healthBodyMatchesTheApiSpec() {
        assertEquals("""{"status":"ok"}""", Api.json.encodeToString(StatusResponse.OK))
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val decoded = Api.json.decodeFromString<StatusResponse>("""{"status":"ready","extra":1}""")
        assertEquals(StatusResponse.READY, decoded)
    }

    @Test
    fun metaBodyMatchesTheApiSpec() {
        val meta = MetaResponse(
            minAppVersion = PlatformVersions(android = 1),
            latestAppVersion = PlatformVersions(android = 1),
            moodCatalogVersion = 1,
            hotline = HotlineInfo.NCMH,
        )

        // api-spec.md §9.3. "ios": null is left out, which the spec treats the same (§2, Format).
        assertEquals(
            """{"minAppVersion":{"android":1},"latestAppVersion":{"android":1},"moodCatalogVersion":1,""" +
                """"hotline":{"name":"NCMH Crisis Hotline","number":"1553"}}""",
            Api.json.encodeToString(meta),
        )
    }

    @Test
    fun routesLiveUnderV1() {
        assertEquals("/v1", Api.PATH_PREFIX)
    }
}
