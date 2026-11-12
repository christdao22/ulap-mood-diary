package app.ulap.core.network.auth

import app.ulap.core.network.ApiResult
import app.ulap.core.network.apiCall
import app.ulap.core.network.map
import app.ulap.shared.api.AuthResponse
import app.ulap.shared.api.RefreshRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject
import kotlin.time.Instant

/**
 * The auth endpoints (api-spec.md §6). They go through the client without the bearer plugin: a
 * refresh must never wait on a refresh. More endpoints join as the sign-in screens arrive.
 */
interface AuthApi {
    /** `POST /auth/refresh` (api-spec.md §6.9). Every success brings a new refresh token. */
    suspend fun refresh(refreshToken: String): ApiResult<SessionTokens>
}

/**
 * What a successful sign-in or refresh hands the app (api-spec.md §5.2), without the profile.
 * Not a DTO, so it may leave this module. `toString()` hides both tokens (CLAUDE.md rules).
 */
data class SessionTokens(
    val userId: String,
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
    val isNewUser: Boolean,
) {
    override fun toString(): String = "SessionTokens(userId=$userId, isNewUser=$isNewUser, tokens=***)"
}

internal class KtorAuthApi @Inject constructor(private val client: HttpClient) : AuthApi {
    override suspend fun refresh(refreshToken: String): ApiResult<SessionTokens> = client
        .apiCall<AuthResponse> {
            post("auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody(RefreshRequest(refreshToken))
            }
        }
        .map { it.toSessionTokens() }
}

private fun AuthResponse.toSessionTokens() = SessionTokens(
    userId = user.id,
    accessToken = accessToken,
    accessTokenExpiresAt = accessTokenExpiresAt,
    refreshToken = refreshToken,
    refreshTokenExpiresAt = refreshTokenExpiresAt,
    isNewUser = isNewUser,
)
