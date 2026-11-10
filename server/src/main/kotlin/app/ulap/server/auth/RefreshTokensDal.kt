@file:OptIn(ExperimentalUuidApi::class)

package app.ulap.server.auth

import app.ulap.server.db.RefreshTokensTable
import java.time.OffsetDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/*
 * Queries on `refresh_tokens` (technical-plan.md §7, api-spec.md §3). Rows hold only the SHA-256
 * of a token. Rotation marks the old row revoked and keeps it, so that the old token coming back
 * can be recognized as a reuse. Signing out (logout, logout-all, a password reset, or the reuse
 * itself) deletes the rows instead: a token that was signed out is simply unknown afterwards.
 */

fun JdbcTransaction.insertRefreshToken(token: RefreshTokenRow) {
    RefreshTokensTable.insert {
        it[id] = token.id
        it[userId] = token.userId
        it[deviceId] = token.deviceId
        it[tokenHash] = token.tokenHash
        it[expiresAt] = token.expiresAt
        it[revokedAt] = token.revokedAt
    }
}

fun JdbcTransaction.findRefreshToken(tokenHash: String): RefreshTokenRow? =
    RefreshTokensTable.selectAll().where { RefreshTokensTable.tokenHash eq tokenHash }.singleOrNull()?.toRow()

/**
 * Revokes one live token. Returns 1 when this call revoked it and 0 when it was already revoked
 * (or unknown): with two refreshes racing on the same token, only one gets the 1.
 */
fun JdbcTransaction.revokeRefreshToken(tokenHash: String, now: OffsetDateTime): Int = RefreshTokensTable
    .update({ (RefreshTokensTable.tokenHash eq tokenHash) and RefreshTokensTable.revokedAt.isNull() }) {
        it[revokedAt] = now
    }

/** Signs one device out: every token for this user on this device, live or rotated. */
fun JdbcTransaction.deleteDeviceSessions(userId: Uuid, deviceId: Uuid): Int = RefreshTokensTable
    .deleteWhere { (RefreshTokensTable.userId eq userId) and (RefreshTokensTable.deviceId eq deviceId) }

/** Signs every device out (`/auth/logout-all`, and after a password reset). */
fun JdbcTransaction.deleteAllSessions(userId: Uuid): Int =
    RefreshTokensTable.deleteWhere { RefreshTokensTable.userId eq userId }

private fun ResultRow.toRow() = RefreshTokenRow(
    id = this[RefreshTokensTable.id],
    userId = this[RefreshTokensTable.userId],
    deviceId = this[RefreshTokensTable.deviceId],
    tokenHash = this[RefreshTokensTable.tokenHash],
    expiresAt = this[RefreshTokensTable.expiresAt],
    revokedAt = this[RefreshTokensTable.revokedAt],
)
