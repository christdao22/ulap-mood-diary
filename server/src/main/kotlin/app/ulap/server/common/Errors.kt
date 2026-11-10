package app.ulap.server.common

import app.ulap.shared.api.ErrorBody
import app.ulap.shared.api.ErrorCode
import app.ulap.shared.api.ErrorResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.header
import io.ktor.server.response.respond

/**
 * Maps every failure to the standard error body (api-spec.md §4). Unexpected exceptions become
 * `500 INTERNAL` and are logged [redacted]: class names and stack frames only, because a message
 * can quote the request body, which may hold a journal note (CLAUDE.md rules).
 */
fun Application.installErrorHandling() {
    install(StatusPages) {
        exception<ApiException> { call, e ->
            e.retryAfterSeconds?.let { call.response.header(HttpHeaders.RetryAfter, it.toString()) }
            call.respondError(e.code, e.message, e)
        }
        // Malformed JSON, a missing required field, or a body that isn't JSON.
        exception<BadRequestException> { call, _ ->
            call.respondError(ErrorCode.BAD_REQUEST, "Malformed request")
        }
        exception<ContentTransformationException> { call, _ ->
            call.respondError(ErrorCode.BAD_REQUEST, "Malformed request")
        }
        exception<PayloadTooLargeException> { call, _ ->
            call.respondError(ErrorCode.PAYLOAD_TOO_LARGE, "Request body is too large")
        }
        exception<NotFoundException> { call, _ ->
            call.respondError(ErrorCode.NOT_FOUND, "Not found")
        }
        exception<Throwable> { call, e ->
            call.application.log.error("Unhandled exception (requestId=${call.callId})", e.redacted())
            call.respondError(ErrorCode.INTERNAL, "Internal server error")
        }
        status(HttpStatusCode.NotFound, HttpStatusCode.MethodNotAllowed) { call, _ ->
            call.respondError(ErrorCode.NOT_FOUND, "Route not found")
        }
        // The RateLimit plugin answers 429 with a Retry-After header and no body (api-spec.md §10).
        status(HttpStatusCode.TooManyRequests) { call, _ ->
            call.respondError(ErrorCode.RATE_LIMITED, "Too many requests")
        }
    }
}

private suspend fun ApplicationCall.respondError(code: String, message: String, source: ApiException? = null) {
    respond(
        ErrorStatuses.of(code),
        ErrorResponse(ErrorBody(code = code, message = message, details = source?.details, requestId = callId)),
    )
}
