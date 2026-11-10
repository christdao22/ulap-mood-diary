package app.ulap.server.health

import app.ulap.server.withTestServer
import app.ulap.shared.api.Api
import app.ulap.shared.api.ErrorCode
import app.ulap.shared.api.ErrorResponse
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Health, readiness and the error shape, with a database that never answers (api-spec.md §9). */
class HealthRoutesTest {
    private fun withDeadDatabase(block: suspend ApplicationTestBuilder.() -> Unit) = withTestServer(block = block)

    @Test
    fun healthIsOkWithoutTheDatabase() = withDeadDatabase {
        val response = client.get("/v1/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"status":"ok"}""", response.bodyAsText())
    }

    @Test
    fun readyIs503WhenTheDatabaseIsDown() = withDeadDatabase {
        val response = client.get("/v1/ready")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        val error = Api.json.decodeFromString<ErrorResponse>(response.bodyAsText()).error
        assertEquals(ErrorCode.UNAVAILABLE, error.code)
        assertEquals(response.headers[Api.HEADER_REQUEST_ID], error.requestId)
    }

    @Test
    fun unknownRoutesUseTheStandardErrorShape() = withDeadDatabase {
        val response = client.get("/v1/nope")

        assertEquals(HttpStatusCode.NotFound, response.status)
        val error = Api.json.decodeFromString<ErrorResponse>(response.bodyAsText()).error
        assertEquals(ErrorCode.NOT_FOUND, error.code)
    }

    @Test
    fun everyResponseCarriesARequestId() = withDeadDatabase {
        val requestId = assertNotNull(client.get("/v1/health").headers[Api.HEADER_REQUEST_ID])

        assertTrue(requestId.startsWith("req_"))
        assertEquals(30, requestId.length)
    }

    @Test
    fun aSafeClientRequestIdIsKept() = withDeadDatabase {
        val response = client.get("/v1/health") { header(Api.HEADER_REQUEST_ID, "abc-123") }

        assertEquals("abc-123", response.headers[Api.HEADER_REQUEST_ID])
    }

    @Test
    fun anUnsafeClientRequestIdIsReplaced() = withDeadDatabase {
        val response = client.get("/v1/health") { header(Api.HEADER_REQUEST_ID, "bad id; level=ERROR") }

        assertTrue(response.headers[Api.HEADER_REQUEST_ID].orEmpty().startsWith("req_"))
    }
}
