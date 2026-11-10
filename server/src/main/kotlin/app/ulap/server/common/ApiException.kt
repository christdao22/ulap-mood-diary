package app.ulap.server.common

import app.ulap.shared.api.ErrorCode
import app.ulap.shared.api.ErrorDetail
import io.ktor.http.HttpStatusCode

/**
 * The one way a service reports a failure the client should see (ulap-reference.md §6.1).
 * StatusPages turns it into the standard error body (api-spec.md §4). Routes never catch it.
 * The HTTP status comes from [code] (see [ErrorStatuses]), so a code can't be sent with the wrong one.
 * [message] is for developers, in English, and must never contain a note, token or password.
 * [retryAfterSeconds] becomes the `Retry-After` header of a `429` (api-spec.md §10).
 */
class ApiException(
    val code: String,
    override val message: String,
    val details: List<ErrorDetail>? = null,
    val retryAfterSeconds: Long? = null,
) : RuntimeException(message) {
    val status: HttpStatusCode = ErrorStatuses.of(code)

    companion object {
        fun badRequest(message: String): ApiException = ApiException(ErrorCode.BAD_REQUEST, message)

        fun validationFailed(details: List<ErrorDetail>, message: String = "Validation failed"): ApiException =
            ApiException(ErrorCode.VALIDATION_FAILED, message, details)

        /** One field that failed, e.g. `email` / `INVALID`. */
        fun invalidField(field: String, issue: String = "INVALID"): ApiException =
            validationFailed(listOf(ErrorDetail(field, issue)), "$field is not allowed: $issue")

        fun unauthenticated(message: String = "Sign-in required"): ApiException =
            ApiException(ErrorCode.UNAUTHENTICATED, message)

        fun notFound(message: String = "Not found"): ApiException = ApiException(ErrorCode.NOT_FOUND, message)

        fun rateLimited(retryAfterSeconds: Long): ApiException =
            ApiException(ErrorCode.RATE_LIMITED, "Too many requests", retryAfterSeconds = retryAfterSeconds)

        fun unavailable(message: String): ApiException = ApiException(ErrorCode.UNAVAILABLE, message)
    }
}

/**
 * The HTTP status for every error code, exactly as the table in api-spec.md §4 lists them.
 * ErrorStatusesTest compares this map with that table. Only ever add a code (ulap-reference.md §6.3).
 */
object ErrorStatuses {
    private val statuses: Map<String, HttpStatusCode> = mapOf(
        ErrorCode.BAD_REQUEST to HttpStatusCode.BadRequest,
        ErrorCode.VALIDATION_FAILED to HttpStatusCode.BadRequest,
        ErrorCode.UNAUTHENTICATED to HttpStatusCode.Unauthorized,
        ErrorCode.TOKEN_EXPIRED to HttpStatusCode.Unauthorized,
        ErrorCode.INVALID_CREDENTIALS to HttpStatusCode.Unauthorized,
        ErrorCode.INVALID_GOOGLE_TOKEN to HttpStatusCode.Unauthorized,
        ErrorCode.REFRESH_TOKEN_INVALID to HttpStatusCode.Unauthorized,
        ErrorCode.REFRESH_TOKEN_REUSED to HttpStatusCode.Unauthorized,
        ErrorCode.EMAIL_NOT_VERIFIED to HttpStatusCode.Forbidden,
        ErrorCode.ACCOUNT_DELETED to HttpStatusCode.Forbidden,
        ErrorCode.NOT_FOUND to HttpStatusCode.NotFound,
        ErrorCode.EMAIL_TAKEN to HttpStatusCode.Conflict,
        ErrorCode.IDENTITY_LINKED_ELSEWHERE to HttpStatusCode.Conflict,
        ErrorCode.TOKEN_USED_OR_EXPIRED to HttpStatusCode.Gone,
        ErrorCode.PAYLOAD_TOO_LARGE to HttpStatusCode.PayloadTooLarge,
        ErrorCode.WEAK_PASSWORD to HttpStatusCode.UnprocessableEntity,
        ErrorCode.APP_UPDATE_REQUIRED to HttpStatusCode.UpgradeRequired,
        ErrorCode.RATE_LIMITED to HttpStatusCode.TooManyRequests,
        ErrorCode.INTERNAL to HttpStatusCode.InternalServerError,
        ErrorCode.UNAVAILABLE to HttpStatusCode.ServiceUnavailable,
    )

    /** Every known code and its status. */
    val all: Map<String, HttpStatusCode> get() = statuses

    /** The status for [code]. An unknown code is a programming error, caught by the tests. */
    fun of(code: String): HttpStatusCode = requireNotNull(statuses[code]) { "Unknown error code $code" }
}
