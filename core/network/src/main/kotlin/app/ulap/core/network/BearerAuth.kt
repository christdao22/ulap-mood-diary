package app.ulap.core.network

import app.ulap.shared.api.ErrorCode
import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.io.IOException

/**
 * Where the bearer plugin gets access tokens. core:auth implements it with the stored session, so
 * this module never sees how tokens are kept. The implementation makes sure only one refresh runs
 * at a time (api-spec.md §3, Client flow, step 4).
 */
interface AccessTokenSource {
    /**
     * An access token that isn't about to expire, refreshing first when needed. Null when
     * nobody is signed in, or the session just ended.
     *
     * @throws SessionRefreshUnavailable when a refresh was needed but couldn't reach the server
     */
    suspend fun accessToken(): String?

    /**
     * The server answered `401 TOKEN_EXPIRED` to a request sent with [rejected]. Returns the token
     * to retry with: a new one, or one another request refreshed meanwhile. Null when the
     * session has ended and a retry won't help.
     *
     * @throws SessionRefreshUnavailable when the refresh couldn't reach the server
     */
    suspend fun refreshAfterExpired(rejected: String): String?
}

/**
 * A refresh was needed but the server couldn't be reached, or answered with something worth
 * retrying (429, 5xx). The session is still valid. It's an [IOException], so the call that needed
 * the token ends as [ApiResult.NetworkError] and is tried again later.
 */
class SessionRefreshUnavailable(message: String, cause: Throwable? = null) : IOException(message, cause)

/** The same client, plus `Authorization: Bearer …` on every request. */
internal fun HttpClient.withBearerAuth(tokens: AccessTokenSource): HttpClient = config {
    install(bearerAuth(tokens))
}

/**
 * Sends the access token, and on `401 TOKEN_EXPIRED` refreshes once and retries once
 * (api-spec.md §3, Client flow). Other 401s, such as `UNAUTHENTICATED`, go back to the caller
 * as they are: a refresh wouldn't change them.
 *
 * Ktor's own Auth plugin refreshes on any 401 and keeps its own copy of the tokens, so Ulap uses
 * this small plugin instead, like the server's AccessTokenAuth.
 */
private fun bearerAuth(tokens: AccessTokenSource) = createClientPlugin("UlapBearerAuth") {
    on(Send) { request ->
        val sent = tokens.accessToken()
        request.setBearer(sent)
        val call = proceed(request)
        if (sent == null || call.response.status != HttpStatusCode.Unauthorized) return@on call
        if (call.response.errorCode() != ErrorCode.TOKEN_EXPIRED) return@on call

        val fresh = tokens.refreshAfterExpired(sent) ?: return@on call
        request.setBearer(fresh)
        proceed(request)
    }
}

private fun HttpRequestBuilder.setBearer(token: String?) {
    headers.remove(HttpHeaders.Authorization)
    if (token != null) headers[HttpHeaders.Authorization] = "Bearer $token"
}
