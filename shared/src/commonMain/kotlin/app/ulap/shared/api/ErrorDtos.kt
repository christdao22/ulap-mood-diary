package app.ulap.shared.api

import kotlinx.serialization.Serializable

/**
 * The one error shape every route returns (api-spec.md §4):
 * `{ "error": { "code", "message", "details", "requestId" } }`.
 */
@Serializable
data class ErrorResponse(val error: ErrorBody)

/**
 * [code] is stable and the app branches on it. [message] is English for developers and is
 * never shown to users: the app shows its own translated text.
 */
@Serializable
data class ErrorBody(
    val code: String,
    val message: String,
    val details: List<ErrorDetail>? = null,
    val requestId: String? = null,
)

/** One rejected field, e.g. `changes[2].stress` / `OUT_OF_RANGE`. */
@Serializable
data class ErrorDetail(val field: String, val issue: String)

/**
 * Every error code in api-spec.md §4. Only ever add a code; never rename or remove one
 * (ulap-reference.md §6.3), because shipped apps branch on them.
 */
object ErrorCode {
    const val BAD_REQUEST: String = "BAD_REQUEST"
    const val VALIDATION_FAILED: String = "VALIDATION_FAILED"
    const val UNAUTHENTICATED: String = "UNAUTHENTICATED"
    const val TOKEN_EXPIRED: String = "TOKEN_EXPIRED"
    const val INVALID_CREDENTIALS: String = "INVALID_CREDENTIALS"
    const val INVALID_GOOGLE_TOKEN: String = "INVALID_GOOGLE_TOKEN"
    const val REFRESH_TOKEN_INVALID: String = "REFRESH_TOKEN_INVALID"
    const val REFRESH_TOKEN_REUSED: String = "REFRESH_TOKEN_REUSED"
    const val EMAIL_NOT_VERIFIED: String = "EMAIL_NOT_VERIFIED"
    const val ACCOUNT_DELETED: String = "ACCOUNT_DELETED"
    const val NOT_FOUND: String = "NOT_FOUND"
    const val EMAIL_TAKEN: String = "EMAIL_TAKEN"
    const val IDENTITY_LINKED_ELSEWHERE: String = "IDENTITY_LINKED_ELSEWHERE"
    const val TOKEN_USED_OR_EXPIRED: String = "TOKEN_USED_OR_EXPIRED"
    const val PAYLOAD_TOO_LARGE: String = "PAYLOAD_TOO_LARGE"
    const val WEAK_PASSWORD: String = "WEAK_PASSWORD"
    const val APP_UPDATE_REQUIRED: String = "APP_UPDATE_REQUIRED"
    const val RATE_LIMITED: String = "RATE_LIMITED"
    const val INTERNAL: String = "INTERNAL"
    const val UNAVAILABLE: String = "UNAVAILABLE"
}
