package app.ulap.core.network

import app.ulap.shared.api.Api
import app.ulap.shared.api.ErrorResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.io.IOException
import kotlinx.serialization.SerializationException

/** The outcome of one API call. Callers branch on it instead of catching exceptions. */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>

    /**
     * The server answered with an error. [code] is the stable `error.code` from api-spec.md §4,
     * or null when the body wasn't in that shape (a proxy's error page, for example).
     */
    data class Failure(val status: Int, val code: String?) : ApiResult<Nothing> {
        /** 429 and 5xx are worth trying again later; the rest never are (api-spec.md §4, Retry policy). */
        val isRetryable: Boolean get() = status == TOO_MANY_REQUESTS || status >= SERVER_ERROR
    }

    /** No answer at all: offline, a timeout, or the server couldn't be reached. Try again later. */
    data class NetworkError(val cause: IOException) : ApiResult<Nothing>

    private companion object {
        const val TOO_MANY_REQUESTS = 429
        const val SERVER_ERROR = 500
    }
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
    is ApiResult.NetworkError -> this
}

/**
 * Runs one request and turns its response into an [ApiResult]. A success body that doesn't match
 * [T] still throws: that's a bug in the app or the server, not something to retry quietly.
 */
internal suspend inline fun <reified T> HttpClient.apiCall(request: HttpClient.() -> HttpResponse): ApiResult<T> = try {
    val response = request()
    if (response.status.isSuccess()) {
        ApiResult.Success(response.body<T>())
    } else {
        ApiResult.Failure(response.status.value, response.errorCode())
    }
} catch (e: IOException) {
    ApiResult.NetworkError(e)
}

/** `error.code` from the standard error body (api-spec.md §4), or null if the body isn't one. */
internal suspend fun HttpResponse.errorCode(): String? = try {
    Api.json.decodeFromString<ErrorResponse>(bodyAsText()).error.code
} catch (_: SerializationException) {
    null
} catch (_: IllegalArgumentException) {
    null
}
