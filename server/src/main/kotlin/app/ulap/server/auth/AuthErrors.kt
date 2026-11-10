@file:OptIn(ExperimentalUuidApi::class)

package app.ulap.server.auth

import app.ulap.server.common.ApiException
import app.ulap.server.users.loadUser
import app.ulap.shared.api.ErrorCode
import app.ulap.shared.api.UserDto
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction

/** The errors more than one auth service throws (api-spec.md §4), with one wording each. */
object AuthErrors {
    fun emailTaken(message: String = "An account with this email already exists") =
        ApiException(ErrorCode.EMAIL_TAKEN, message)

    fun invalidCredentials() = ApiException(ErrorCode.INVALID_CREDENTIALS, "Wrong email or password")

    fun accountDeleted() = ApiException(ErrorCode.ACCOUNT_DELETED, "The account no longer exists")

    fun invalidRefreshToken() = ApiException(ErrorCode.REFRESH_TOKEN_INVALID, "Unknown or expired refresh token")

    fun tokenUsedOrExpired() =
        ApiException(ErrorCode.TOKEN_USED_OR_EXPIRED, "This link was already used or has expired")
}

/** The `User` for a signed-in caller, or `403 ACCOUNT_DELETED` when the row is gone. */
fun JdbcTransaction.loadUserOrGone(userId: Uuid): UserDto = loadUser(userId) ?: throw AuthErrors.accountDeleted()
