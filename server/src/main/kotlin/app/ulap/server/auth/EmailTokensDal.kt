@file:OptIn(ExperimentalUuidApi::class)

package app.ulap.server.auth

import app.ulap.server.db.EmailTokenPurpose
import app.ulap.server.db.EmailTokensTable
import java.time.OffsetDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/*
 * Queries on `email_tokens` (technical-plan.md §7): the single-use tokens behind verification
 * (24 hours) and password-reset (30 minutes) links. Only the SHA-256 of a token is stored.
 */

fun JdbcTransaction.insertEmailToken(token: EmailTokenRow) {
    EmailTokensTable.insert {
        it[id] = token.id
        it[identityId] = token.identityId
        it[purpose] = token.purpose.dbValue
        it[tokenHash] = token.tokenHash
        it[expiresAt] = token.expiresAt
        it[usedAt] = token.usedAt
    }
}

fun JdbcTransaction.findEmailToken(tokenHash: String): EmailTokenRow? =
    EmailTokensTable.selectAll().where { EmailTokensTable.tokenHash eq tokenHash }.singleOrNull()?.toRow()

/** Spends one token. Returns 1 when this call spent it, 0 when it had already been used. */
fun JdbcTransaction.useEmailToken(id: Uuid, now: OffsetDateTime): Int =
    EmailTokensTable.update({ (EmailTokensTable.id eq id) and EmailTokensTable.usedAt.isNull() }) {
        it[usedAt] = now
    }

/** Marks every other live token of this kind used, so older links stop working once one was used. */
fun JdbcTransaction.retireEmailTokens(identityId: Uuid, purpose: EmailTokenPurpose, now: OffsetDateTime): Int =
    EmailTokensTable.update({
        (EmailTokensTable.identityId eq identityId) and
            (EmailTokensTable.purpose eq purpose.dbValue) and
            EmailTokensTable.usedAt.isNull()
    }) {
        it[usedAt] = now
    }

private fun ResultRow.toRow() = EmailTokenRow(
    id = this[EmailTokensTable.id],
    identityId = this[EmailTokensTable.identityId],
    purpose = EmailTokenPurpose.entries.first { it.dbValue == this[EmailTokensTable.purpose] },
    tokenHash = this[EmailTokensTable.tokenHash],
    expiresAt = this[EmailTokensTable.expiresAt],
    usedAt = this[EmailTokensTable.usedAt],
)
