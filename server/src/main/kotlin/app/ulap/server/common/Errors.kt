package app.ulap.server.common

import app.ulap.shared.api.ErrorBody
import app.ulap.shared.api.ErrorCode
import app.ulap.shared.api.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond

/**
 * Maps every failure to the standard error body (api-spec.md §4). Unexpected exceptions become
 * `500 INTERNAL`, and only their type and stack trace are logged: never the request body, which
 * may hold a journal note (CLAUDE.md rules).
 */
fun Application.installErrorHandling() {
    install(StatusPages) {
        exception<ApiException> { call, e ->
            call.respondError(e.status, e.code, e.message, e)
        }
        exception<BadRequestException> { call, _ ->
            call.respondError(HttpStatusCode.BadRequest, ErrorCode.BAD_REQUEST, "Malformed request")
        }
        exception<Throwable> { call, e ->
            call.application.log.error("Unhandled ${e::class.qualifiedName} (requestId=${call.callId})", e)
            call.respondError(HttpStatusCode.InternalServerError, ErrorCode.INTERNAL, "Internal server error")
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respondError(HttpStatusCode.NotFound, ErrorCode.NOT_FOUND, "Route not found")
        }
        status(HttpStatusCode.MethodNotAllowed) { call, _ ->
            call.respondError(HttpStatusCode.NotFound, ErrorCode.NOT_FOUND, "Route not found")
        }
    }
}

private suspend fun ApplicationCall.respondError(
    status: HttpStatusCode,
    code: String,
    message: String,
    source: ApiException? = null,
) {
    respond(
        status,
        ErrorResponse(
            ErrorBody(code = code, message = message, details = source?.details, requestId = callId),
        ),
    )
}
