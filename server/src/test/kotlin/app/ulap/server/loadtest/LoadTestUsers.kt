@file:OptIn(ExperimentalUuidApi::class)

package app.ulap.server.loadtest

import app.ulap.server.auth.AccessTokens
import app.ulap.server.auth.UserPrincipal
import app.ulap.server.db.AuthIdentitiesTable
import app.ulap.server.db.AuthProvider
import app.ulap.server.db.Db
import app.ulap.server.db.UsersTable
import app.ulap.shared.model.Ids
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select

/** One seeded account as the k6 script reads it from `users.json`. The file holds live tokens. */
@Serializable
class SeededUser(val userId: String, val deviceId: String, val accessToken: String) {
    override fun toString(): String = "SeededUser(userId=$userId, deviceId=$deviceId, accessToken=***)"
}

/**
 * Creates and removes the accounts the sync load test runs as (docs/runbooks/load-test.md).
 *
 * Each account is a user with one GOOGLE identity whose subject is `loadtest-<user id>`. Google
 * subjects are digits only, so [cleanup] can never match a real person. A Google identity counts
 * as verified, so the accounts can sync straight away (api-spec.md §8).
 *
 * The access tokens are signed here, with the target server's `JWT_SECRET`, instead of signing in
 * through the API: every route without a token shares one limit of 120 per minute per IP
 * (api-spec.md §10), so 1,000 sign-ins from one machine would take more than 8 minutes, and the
 * test is about sync, not sign-in.
 */
class LoadTestUsers(private val db: Db, private val tokens: AccessTokens) {
    suspend fun seed(count: Int): List<SeededUser> {
        require(count > 0) { "count must be at least 1" }
        val accounts = List(count) { Account(userId = Uuid.parse(Ids.newId()), deviceId = Uuid.random()) }
        db.tx {
            UsersTable.batchInsert(accounts, shouldReturnGeneratedValues = false) { account ->
                this[UsersTable.id] = account.userId
                this[UsersTable.language] = "en"
                this[UsersTable.mascotEnabled] = true
            }
            AuthIdentitiesTable.batchInsert(accounts, shouldReturnGeneratedValues = false) { account ->
                this[AuthIdentitiesTable.id] = Uuid.parse(Ids.newId())
                this[AuthIdentitiesTable.userId] = account.userId
                this[AuthIdentitiesTable.provider] = AuthProvider.GOOGLE.name
                this[AuthIdentitiesTable.providerSubject] = SUBJECT_PREFIX + account.userId
                this[AuthIdentitiesTable.email] = null
                this[AuthIdentitiesTable.emailVerified] = true
                this[AuthIdentitiesTable.passwordHash] = null
            }
        }
        return accounts.map { account ->
            val token = tokens.issue(UserPrincipal(account.userId, account.deviceId)).token
            SeededUser(account.userId.toString(), account.deviceId.toString(), token)
        }
    }

    /**
     * Deletes every load-test user. Identities, refresh tokens and check-ins go with them
     * (`ON DELETE CASCADE`, technical-plan.md §7). Returns how many users were removed.
     */
    suspend fun cleanup(): Int = db.tx {
        val loadTestUserIds = AuthIdentitiesTable.select(AuthIdentitiesTable.userId).where {
            (AuthIdentitiesTable.provider eq AuthProvider.GOOGLE.name) and
                (AuthIdentitiesTable.providerSubject like "$SUBJECT_PREFIX%")
        }
        UsersTable.deleteWhere { UsersTable.id inSubQuery loadTestUserIds }
    }

    private class Account(val userId: Uuid, val deviceId: Uuid)

    companion object {
        /** Marks a load-test account. Real Google subjects never contain a letter. */
        const val SUBJECT_PREFIX: String = "loadtest-"
    }
}
