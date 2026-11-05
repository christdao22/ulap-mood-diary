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
    fun routesLiveUnderV1() {
        assertEquals("/v1", Api.PATH_PREFIX)
    }
}
