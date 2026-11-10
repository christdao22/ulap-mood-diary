@file:OptIn(ExperimentalUuidApi::class)

package app.ulap.server.users

import app.ulap.server.auth.identitiesOf
import app.ulap.server.common.toApiInstant
import app.ulap.server.db.UsersTable
import app.ulap.shared.api.IdentityDto
import app.ulap.shared.api.UserDto
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

/*
 * Queries on `users` (technical-plan.md §7). Exposed only, no rules (ulap-reference.md §6.1).
 * Each function runs in the transaction the caller opened with `Db.tx`.
 */

fun JdbcTransaction.insertUser(id: Uuid, displayName: String?, language: String) {
    UsersTable.insert {
        it[UsersTable.id] = id
        it[UsersTable.displayName] = displayName
        it[UsersTable.language] = language
        it[mascotEnabled] = true
    }
}

fun JdbcTransaction.findUser(id: Uuid): UserRow? =
    UsersTable.selectAll().where { UsersTable.id eq id }.singleOrNull()?.toUserRow()

/** The `User` of api-spec.md §5.1, or null when the account is gone. */
fun JdbcTransaction.loadUser(id: Uuid): UserDto? {
    val user = findUser(id) ?: return null
    return UserDto(
        id = user.id.toString(),
        displayName = user.displayName,
        language = user.language,
        mascotEnabled = user.mascotEnabled,
        identities = identitiesOf(id).map { IdentityDto(it.provider.name, it.email, it.emailVerified) },
        createdAt = user.createdAt.toInstant().toApiInstant(),
    )
}

private fun ResultRow.toUserRow() = UserRow(
    id = this[UsersTable.id],
    displayName = this[UsersTable.displayName],
    language = this[UsersTable.language],
    mascotEnabled = this[UsersTable.mascotEnabled],
    createdAt = this[UsersTable.createdAt],
)
