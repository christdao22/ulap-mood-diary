package app.ulap.core.network.auth

import app.ulap.core.network.ApiResult
import app.ulap.core.network.apiCall
import app.ulap.core.network.di.AuthorizedClient
import app.ulap.core.network.map
import app.ulap.shared.api.AuthResponse
import app.ulap.shared.api.EmailLoginRequest
import app.ulap.shared.api.EmailRegisterRequest
import app.ulap.shared.api.ForgotPasswordRequest
import app.ulap.shared.api.GoogleSignInRequest
import app.ulap.shared.api.LogoutRequest
import app.ulap.shared.api.RefreshRequest
import app.ulap.shared.api.ResetPasswordRequest
import app.ulap.shared.api.UserDto
import app.ulap.shared.api.VerifyEmailRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject
import kotlin.time.Instant

/**
 * The auth endpoints anyone can call (api-spec.md §6). They go through the client without the
 * bearer plugin: a refresh must never wait on a refresh, and a sign-in has no token yet.
 */
interface AuthApi {
    /** `POST /auth/refresh` (api-spec.md §6.9). Every success brings a new refresh token. */
    suspend fun refresh(refreshToken: String): ApiResult<SessionTokens>

    /** `POST /auth/google` (api-spec.md §6.1): signs in, or signs up on first use. */
    suspend fun google(idToken: String, displayName: String?): ApiResult<SessionTokens>

    /** `POST /auth/email/register` (api-spec.md §6.2). The server sends a verification email. */
    suspend fun register(email: String, password: String): ApiResult<SessionTokens>

    /** `POST /auth/email/login` (api-spec.md §6.4). */
    suspend fun login(email: String, password: String): ApiResult<SessionTokens>

    /** `POST /auth/password/forgot` (api-spec.md §6.7). 204 whether or not the account exists. */
    suspend fun forgotPassword(email: String): ApiResult<Unit>

    /**
     * `POST /auth/email/verify` (api-spec.md §6.5) with the token from the email's link. Needs no
     * sign-in: the link may open on a phone that isn't signed in. 410 once the link is spent.
     */
    suspend fun verifyEmail(token: String): ApiResult<Unit>

    /**
     * `POST /auth/password/reset` (api-spec.md §6.8). A 204 signs out every device, this one too.
     * A weak password is refused (422) without spending the link.
     */
    suspend fun resetPassword(token: String, newPassword: String): ApiResult<Unit>
}

/**
 * The 🔒 auth routes that need the signed-in user's access token (api-spec.md §6.6, §6.10).
 * They use the bearer client, so an expired access token is refreshed first.
 */
interface SignedInAuthApi {
    /**
     * `POST /auth/logout` (api-spec.md §6.10): ends this device's session on the server. The
     * server finds the device from the access token, so [refreshToken] may be null.
     */
    suspend fun logout(refreshToken: String?): ApiResult<Unit>

    /** `POST /auth/email/resend-verification` (api-spec.md §6.6). Limited to 3 an hour. */
    suspend fun resendVerification(): ApiResult<Unit>
}

/**
 * What a successful sign-in or refresh hands the app (api-spec.md §5.2). Not a DTO, so it may
 * leave this module. `toString()` hides both tokens (CLAUDE.md rules).
 */
data class SessionTokens(
    val userId: String,
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
    val isNewUser: Boolean,
    val profile: SessionProfile,
) {
    override fun toString(): String = "SessionTokens(userId=$userId, isNewUser=$isNewUser, tokens=***)"
}

/**
 * The parts of `User` (api-spec.md §5.1) that the Account screen shows. Every sign-in and refresh
 * brings a fresh copy, so a verified email shows up after the next refresh.
 */
data class SessionProfile(
    val displayName: String?,
    /** The email sign-in's address, else the Google account's. */
    val email: String?,
    /** True once any sign-in method has a verified email. Sync waits for it (technical-plan.md §9). */
    val emailVerified: Boolean,
    val createdAt: Instant,
) {
    override fun toString(): String = "SessionProfile(emailVerified=$emailVerified, createdAt=$createdAt)"
}

internal class KtorAuthApi @Inject constructor(private val client: HttpClient) : AuthApi {
    override suspend fun refresh(refreshToken: String): ApiResult<SessionTokens> =
        postForSession("auth/refresh", RefreshRequest(refreshToken))

    override suspend fun google(idToken: String, displayName: String?): ApiResult<SessionTokens> =
        postForSession("auth/google", GoogleSignInRequest(idToken, displayName))

    override suspend fun register(email: String, password: String): ApiResult<SessionTokens> =
        postForSession("auth/email/register", EmailRegisterRequest(email, password))

    override suspend fun login(email: String, password: String): ApiResult<SessionTokens> =
        postForSession("auth/email/login", EmailLoginRequest(email, password))

    override suspend fun forgotPassword(email: String): ApiResult<Unit> =
        postForNothing("auth/password/forgot", ForgotPasswordRequest(email))

    override suspend fun verifyEmail(token: String): ApiResult<Unit> =
        postForNothing("auth/email/verify", VerifyEmailRequest(token))

    override suspend fun resetPassword(token: String, newPassword: String): ApiResult<Unit> =
        postForNothing("auth/password/reset", ResetPasswordRequest(token, newPassword))

    private suspend inline fun <reified B : Any> postForNothing(path: String, body: B): ApiResult<Unit> =
        client.apiCall {
            post(path) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }

    private suspend inline fun <reified B : Any> postForSession(path: String, body: B): ApiResult<SessionTokens> =
        client
            .apiCall<AuthResponse> {
                post(path) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
            .map { it.toSessionTokens() }
}

internal class KtorSignedInAuthApi @Inject constructor(@param:AuthorizedClient private val client: HttpClient) :
    SignedInAuthApi {
    override suspend fun logout(refreshToken: String?): ApiResult<Unit> = client.apiCall {
        post("auth/logout") {
            contentType(ContentType.Application.Json)
            setBody(LogoutRequest(refreshToken))
        }
    }

    override suspend fun resendVerification(): ApiResult<Unit> = client.apiCall {
        post("auth/email/resend-verification") {
            contentType(ContentType.Application.Json)
            // The request is `{}` (api-spec.md §6.6). A String body is sent as is.
            setBody("{}")
        }
    }
}

private fun AuthResponse.toSessionTokens() = SessionTokens(
    userId = user.id,
    accessToken = accessToken,
    accessTokenExpiresAt = accessTokenExpiresAt,
    refreshToken = refreshToken,
    refreshTokenExpiresAt = refreshTokenExpiresAt,
    isNewUser = isNewUser,
    profile = user.toSessionProfile(),
)

private fun UserDto.toSessionProfile() = SessionProfile(
    displayName = displayName,
    email = identities.firstOrNull { it.provider == PROVIDER_EMAIL }?.email
        ?: identities.firstNotNullOfOrNull { it.email },
    emailVerified = identities.any { it.emailVerified },
    createdAt = createdAt,
)

private const val PROVIDER_EMAIL = "EMAIL"
