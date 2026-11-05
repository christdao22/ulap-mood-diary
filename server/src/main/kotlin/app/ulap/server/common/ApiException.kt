package app.ulap.server.common

import app.ulap.shared.api.ErrorCode
import app.ulap.shared.api.ErrorDetail
import io.ktor.http.HttpStatusCode

/**
 * The one way a service reports a failure the client should see (ulap-reference.md §6.1).
 * StatusPages turns it into the standard error body (api-spec.md §4). Routes never catch it.
 * [message] is for developers, in English, and must never contain a note, token or password.
 */
class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
    val details: List<ErrorDetail>? = null,
) : RuntimeException(message) {
    companion object {
        fun notFound(message: String = "Not found"): ApiException =
            ApiException(HttpStatusCode.NotFound, ErrorCode.NOT_FOUND, message)

        fun badRequest(message: String): ApiException =
            ApiException(HttpStatusCode.BadRequest, ErrorCode.BAD_REQUEST, message)

        fun unavailable(message: String): ApiException =
            ApiException(HttpStatusCode.ServiceUnavailable, ErrorCode.UNAVAILABLE, message)
    }
}
