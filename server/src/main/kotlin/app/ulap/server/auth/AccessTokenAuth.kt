package app.ulap.server.auth

import app.ulap.server.common.ApiException
import app.ulap.shared.api.ErrorCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route

/**
 * Ktor authentication for `Authorization: Bearer <access token>` (api-spec.md §2, §3). Ktor's own
 * `jwt` provider can't tell an expired token from a bad one in its challenge, and the app needs
 * that difference (`TOKEN_EXPIRED` → refresh once, `UNAUTHENTICATED` → sign in again), so this
 * small provider does the check with [AccessTokens] and throws the matching [ApiException].
 */
class AccessTokenAuth(config: Config) : AuthenticationProvider(config) {
    private val tokens = config.tokens

    class Config(name: String, val tokens: AccessTokens) : AuthenticationProvider.Config(name)

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val token = context.call.bearerToken() ?: throw ApiException.unauthenticated()
        when (val result = tokens.verify(token)) {
            is AccessTokenResult.Valid -> context.principal(result.principal)
            AccessTokenResult.Expired -> throw ApiException(ErrorCode.TOKEN_EXPIRED, "Access token expired")
            AccessTokenResult.Invalid -> throw ApiException.unauthenticated("Invalid access token")
        }
    }

    companion object {
        /** The provider name routes pass to [authenticated]. */
        const val NAME: String = "access"
    }
}

fun Application.installAccessTokenAuth(tokens: AccessTokens) {
    install(Authentication) {
        register(AccessTokenAuth(AccessTokenAuth.Config(AccessTokenAuth.NAME, tokens)))
    }
}

/** Routes inside need a valid access token; `call.userPrincipal()` then says who's calling. */
fun Route.authenticated(build: Route.() -> Unit): Route = authenticate(AccessTokenAuth.NAME, build = build)

/** The token after `Bearer `, or null when the header is missing or isn't a bearer token. */
fun ApplicationCall.bearerToken(): String? {
    val header = runCatching { request.parseAuthorizationHeader() }.getOrNull() as? HttpAuthHeader.Single ?: return null
    return header.blob.takeIf { header.authScheme.equals("Bearer", ignoreCase = true) && it.isNotBlank() }
}

/** The caller, inside an [authenticated] route. */
fun ApplicationCall.userPrincipal(): UserPrincipal = principal<UserPrincipal>() ?: throw ApiException.unauthenticated()
