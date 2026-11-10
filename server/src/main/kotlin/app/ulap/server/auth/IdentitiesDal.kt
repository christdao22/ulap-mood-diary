@file:OptIn(ExperimentalUuidApi::class)

package app.ulap.server.auth

import app.ulap.server.db.AuthIdentitiesTable
import app.ulap.server.db.AuthProvider
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/*
 * Queries on `auth_identities` (technical-plan.md §7): one row per way a user can sign in.
 * `providerSubject` is Google's `sub` for GOOGLE and the lower-cased email for EMAIL.
 */

fun JdbcTransaction.insertIdentity(identity: IdentityRow) {
    AuthIdentitiesTable.insert {
        it[id] = identity.id
        it[userId] = identity.userId
        it[provider] = identity.provider.name
        it[providerSubject] = identity.providerSubject
        it[email] = identity.email
        it[emailVerified] = identity.emailVerified
        it[passwordHash] = identity.passwordHash
    }
}

fun JdbcTransaction.findIdentity(provider: AuthProvider, subject: String): IdentityRow? = AuthIdentitiesTable
    .selectAll()
    .where { (AuthIdentitiesTable.provider eq provider.name) and (AuthIdentitiesTable.providerSubject eq subject) }
    .singleOrNull()
    ?.toIdentityRow()

fun JdbcTransaction.findIdentityById(id: Uuid): IdentityRow? =
    AuthIdentitiesTable.selectAll().where { AuthIdentitiesTable.id eq id }.singleOrNull()?.toIdentityRow()

fun JdbcTransaction.identitiesOf(userId: Uuid): List<IdentityRow> = AuthIdentitiesTable
    .selectAll()
    .where { AuthIdentitiesTable.userId eq userId }
    .orderBy(AuthIdentitiesTable.createdAt)
    .map { it.toIdentityRow() }

fun JdbcTransaction.markEmailVerified(identityId: Uuid) {
    AuthIdentitiesTable.update({ AuthIdentitiesTable.id eq identityId }) { it[emailVerified] = true }
}

fun JdbcTransaction.setPasswordHash(identityId: Uuid, hash: String) {
    AuthIdentitiesTable.update({ AuthIdentitiesTable.id eq identityId }) { it[passwordHash] = hash }
}

private fun ResultRow.toIdentityRow() = IdentityRow(
    id = this[AuthIdentitiesTable.id],
    userId = this[AuthIdentitiesTable.userId],
    provider = AuthProvider.valueOf(this[AuthIdentitiesTable.provider]),
    providerSubject = this[AuthIdentitiesTable.providerSubject],
    email = this[AuthIdentitiesTable.email],
    emailVerified = this[AuthIdentitiesTable.emailVerified],
    passwordHash = this[AuthIdentitiesTable.passwordHash],
)
