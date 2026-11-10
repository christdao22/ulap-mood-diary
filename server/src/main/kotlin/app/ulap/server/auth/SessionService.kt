@file:OptIn(ExperimentalUuidApi::class)

package app.ulap.server.auth

import app.ulap.server.common.ApiException
import app.ulap.server.common.atUtc
import app.ulap.server.common.toApiInstant
import app.ulap.server.db.Db
import app.ulap.shared.api.AuthResponse
import app.ulap.shared.api.ErrorCode
import app.ulap.shared.model.Ids
import java.time.Clock
import java.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction

/**
 * Sessions (api-spec.md §3, §6.9–§6.11): a session is one refresh token for one device. Signing
 * in opens one, `/auth/refresh` rotates it, `/auth/logout` closes the device's and
 * `/auth/logout-all` every device's.
 */
class SessionService(
    private val db: Db,
    private val clock: Clock,
    private val accessTokens: AccessTokens,
    refreshTokenTtlDays: Int,
) {
    private val refreshTtl: Duration = Duration.ofDays(refreshTokenTtlDays.toLong())

    /**
     * Inside the caller's transaction: stores a new refresh token for [deviceId], signs an access
     * token and loads the `User`, which together make the `AuthResponse` of api-spec.md §5.2.
     */
    fun JdbcTransaction.openSession(userId: Uuid, deviceId: Uuid, isNewUser: Boolean): AuthResponse {
        val user = loadUserOrGone(userId)
        val now = clock.instant()
        val refreshToken = OpaqueTokens.generate()
        val refreshExpiresAt = now + refreshTtl
        insertRefreshToken(
            RefreshTokenRow(
                id = Uuid.parse(Ids.newId()),
                userId = userId,
                deviceId = deviceId,
                tokenHash = OpaqueTokens.hash(refreshToken),
                expiresAt = refreshExpiresAt.atUtc(),
                revokedAt = null,
            ),
        )
        val access = accessTokens.issue(UserPrincipal(userId, deviceId))
        return AuthResponse(
            accessToken = access.token,
            accessTokenExpiresAt = access.expiresAt.toApiInstant(),
            refreshToken = refreshToken,
            refreshTokenExpiresAt = refreshExpiresAt.toApiInstant(),
            user = user,
            isNewUser = isNewUser,
        )
    }

    /**
     * `POST /auth/refresh` (api-spec.md §6.9): the old token is revoked and a new one issued, in one
     * transaction. An already-revoked token coming back means it was copied somewhere, so every
     * session on that device is closed and the app gets `REFRESH_TOKEN_REUSED` (§3, Refresh rules).
     */
    suspend fun refresh(refreshToken: String, deviceId: Uuid): AuthResponse {
        val hash = OpaqueTokens.hash(refreshToken)
        val outcome = db.tx {
            val now = clock.instant()
            // Unknown, another device's, or expired: all simply invalid (api-spec.md §6.9).
            val token = findRefreshToken(hash)
                ?.takeIf { it.deviceId == deviceId && it.expiresAt.toInstant().isAfter(now) }
                ?: throw AuthErrors.invalidRefreshToken()
            if (token.revokedAt != null || revokeRefreshToken(hash, now.atUtc()) == 0) {
                Reused(token)
            } else {
                Rotated(openSession(token.userId, deviceId, isNewUser = false))
            }
        }
        return when (outcome) {
            is Rotated -> outcome.response

            is Reused -> {
                // Its own transaction: throwing inside the one above would have rolled the sign-out back.
                db.tx { deleteDeviceSessions(outcome.token.userId, outcome.token.deviceId) }
                throw ApiException(ErrorCode.REFRESH_TOKEN_REUSED, "Refresh token was already used; device signed out")
            }
        }
    }

    private sealed interface RefreshOutcome

    private class Rotated(val response: AuthResponse) : RefreshOutcome

    private class Reused(val token: RefreshTokenRow) : RefreshOutcome

    /** `POST /auth/logout` (api-spec.md §6.10): closes the caller's device session (and the given token's, if it's theirs). */
    suspend fun logout(caller: UserPrincipal, refreshToken: String?) {
        db.tx {
            deleteDeviceSessions(caller.userId, caller.deviceId)
            val given = refreshToken?.let { findRefreshToken(OpaqueTokens.hash(it)) }
            if (given != null && given.userId == caller.userId) deleteDeviceSessions(given.userId, given.deviceId)
        }
    }

    /** `POST /auth/logout-all` (api-spec.md §6.11). */
    suspend fun logoutAll(userId: Uuid) {
        db.tx { deleteAllSessions(userId) }
    }
}
