package app.ulap.shared.api

import kotlin.time.Instant
import kotlinx.serialization.Serializable

/*
 * Request and response bodies of the auth and user endpoints (api-spec.md §5.1, §5.2, §6).
 * Every class that carries a password or a token hides it from `toString()`, so a stray
 * `println(request)` or a log line can never leak one (CLAUDE.md rules).
 */

/** `User` (api-spec.md §5.1): profile, settings and the ways this person can sign in. */
@Serializable
data class UserDto(
    val id: String,
    val displayName: String? = null,
    val language: String,
    val mascotEnabled: Boolean,
    val identities: List<IdentityDto>,
    @Serializable(with = ApiInstantSerializer::class)
    val createdAt: Instant,
)

/** One sign-in method. [provider] is `GOOGLE`, `EMAIL` or, later, `APPLE`. */
@Serializable
data class IdentityDto(val provider: String, val email: String? = null, val emailVerified: Boolean)

/** `AuthResponse` (api-spec.md §5.2). `isNewUser` tells the app to upload its guest data (§8.5). */
@Serializable
data class AuthResponse(
    val accessToken: String,
    @Serializable(with = ApiInstantSerializer::class)
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    @Serializable(with = ApiInstantSerializer::class)
    val refreshTokenExpiresAt: Instant,
    val user: UserDto,
    val isNewUser: Boolean,
) {
    override fun toString(): String = "AuthResponse(user=${user.id}, isNewUser=$isNewUser, tokens=***)"
}

/** `POST /auth/google` and `POST /auth/link/google` (api-spec.md §6.1, §6.12). */
@Serializable
data class GoogleSignInRequest(val idToken: String, val displayName: String? = null) {
    override fun toString(): String = "GoogleSignInRequest(idToken=***)"
}

/** `POST /auth/email/register` and `POST /auth/link/email` (api-spec.md §6.2, §6.13). */
@Serializable
data class EmailRegisterRequest(val email: String, val password: String, val displayName: String? = null) {
    override fun toString(): String = "EmailRegisterRequest(password=***)"
}

/** `POST /auth/email/login` (api-spec.md §6.4). */
@Serializable
data class EmailLoginRequest(val email: String, val password: String) {
    override fun toString(): String = "EmailLoginRequest(password=***)"
}

/** `POST /auth/email/verify` (api-spec.md §6.5): the token from the verification link. */
@Serializable
data class VerifyEmailRequest(val token: String) {
    override fun toString(): String = "VerifyEmailRequest(token=***)"
}

/** `POST /auth/password/forgot` (api-spec.md §6.7). */
@Serializable
data class ForgotPasswordRequest(val email: String)

/** `POST /auth/password/reset` (api-spec.md §6.8): the token from the reset link and the new password. */
@Serializable
data class ResetPasswordRequest(val token: String, val newPassword: String) {
    override fun toString(): String = "ResetPasswordRequest(token=***, newPassword=***)"
}

/** `POST /auth/refresh` (api-spec.md §6.9). */
@Serializable
data class RefreshRequest(val refreshToken: String) {
    override fun toString(): String = "RefreshRequest(refreshToken=***)"
}

/**
 * `POST /auth/logout` (api-spec.md §6.10). The token is optional: a phone that lost it can still
 * sign this device out, because the server also knows the device from the access token.
 */
@Serializable
data class LogoutRequest(val refreshToken: String? = null) {
    override fun toString(): String = "LogoutRequest(refreshToken=***)"
}

/**
 * `PATCH /me` (api-spec.md §7.2): only the fields that changed. A field left out, or sent as
 * null, stays as it is.
 */
@Serializable
data class UpdateMeRequest(
    val displayName: String? = null,
    val language: String? = null,
    val mascotEnabled: Boolean? = null,
)

/** `DELETE /me` (api-spec.md §7.3). [confirm] must be `AccountRules.DELETE_CONFIRMATION`. */
@Serializable
data class DeleteMeRequest(val confirm: String? = null)
