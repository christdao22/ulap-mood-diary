@file:OptIn(ExperimentalUuidApi::class)

package app.ulap.server.db

import kotlin.uuid.ExperimentalUuidApi
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/*
 * Exposed views of the tables in V2__init_schema.sql (technical-plan.md §7). Flyway owns the
 * schema: these objects only describe it for queries and are never used to create or alter
 * tables. Keep both in step: a column added here needs a migration first.
 *
 * TIMESTAMPTZ columns map to OffsetDateTime, which PostgreSQL stores as an exact instant, so the
 * JVM's default time zone never changes what is read or written.
 */

object UsersTable : Table("users") {
    val id = uuid("id")
    val displayName = text("display_name").nullable()
    val language = text("language")
    val mascotEnabled = bool("mascot_enabled")
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object AuthIdentitiesTable : Table("auth_identities") {
    val id = uuid("id")
    val userId = uuid("user_id").references(UsersTable.id)
    val provider = text("provider")
    val providerSubject = text("provider_subject")
    val email = text("email").nullable()
    val emailVerified = bool("email_verified")
    val passwordHash = text("password_hash").nullable()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()

    override val primaryKey = PrimaryKey(id)
}

/** The `provider` values allowed by the CHECK in V2 (technical-plan.md §7). */
enum class AuthProvider { GOOGLE, EMAIL, APPLE }

object RefreshTokensTable : Table("refresh_tokens") {
    val id = uuid("id")
    val userId = uuid("user_id").references(UsersTable.id)
    val deviceId = uuid("device_id")
    val tokenHash = text("token_hash")
    val expiresAt = timestampWithTimeZone("expires_at")
    val revokedAt = timestampWithTimeZone("revoked_at").nullable()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()

    override val primaryKey = PrimaryKey(id)
}

object EmailTokensTable : Table("email_tokens") {
    val id = uuid("id")
    val identityId = uuid("identity_id").references(AuthIdentitiesTable.id)
    val purpose = text("purpose")
    val tokenHash = text("token_hash")
    val expiresAt = timestampWithTimeZone("expires_at")
    val usedAt = timestampWithTimeZone("used_at").nullable()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()

    override val primaryKey = PrimaryKey(id)
}

/** The `purpose` values allowed by the CHECK in V2, as stored. */
enum class EmailTokenPurpose(val dbValue: String) { VERIFY("verify"), RESET("reset") }

/**
 * `note` and `triggerOther` are ciphertext here, never plain text. Seal and open them with
 * [app.ulap.server.crypto.NoteCipher]; `keyId` records which key sealed them (api-spec.md §11).
 */
object CheckInsTable : Table("check_ins") {
    val id = uuid("id")
    val userId = uuid("user_id").references(UsersTable.id)
    val moodCode = text("mood_code")
    val energy = short("energy")
    val stress = short("stress")
    val stressAfter = short("stress_after").nullable()
    val trigger = text("trigger").nullable()
    val triggerOther = binary("trigger_other").nullable()
    val note = binary("note").nullable()
    val keyId = text("key_id").nullable()
    val supportUsed = text("support_used")
    val isGoodMoment = bool("is_good_moment")
    val loggedAt = timestampWithTimeZone("logged_at")
    val tzOffsetMin = short("tz_offset_min")
    val clientUpdatedAt = timestampWithTimeZone("client_updated_at")
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()

    /** From `check_in_seq` on insert. Every update must set it again from the sequence (§8). */
    val serverSeq = long("server_seq").databaseGenerated()
    val serverUpdatedAt = timestampWithTimeZone("server_updated_at").databaseGenerated()

    override val primaryKey = PrimaryKey(id)
}

/** The single row (id = 1) that records how far tombstones have been purged (technical-plan.md §7). */
object SyncPurgeTable : Table("sync_purge") {
    val id = short("id")
    val purgedThroughSeq = long("purged_through_seq")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}
