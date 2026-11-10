@file:OptIn(ExperimentalUuidApi::class)

package app.ulap.server.auth

import app.ulap.server.db.AuthProvider
import app.ulap.server.db.EmailTokenPurpose
import java.time.OffsetDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/*
 * Rows of the auth tables (technical-plan.md §7) as the DAL files read and write them. Plain
 * classes, not data classes, so no accidental `toString()` prints a hash.
 */

// One parameter per column of auth_identities.
@Suppress("LongParameterList")
class IdentityRow(
    val id: Uuid,
    val userId: Uuid,
    val provider: AuthProvider,
    val providerSubject: String,
    val email: String?,
    val emailVerified: Boolean,
    /** Argon2id, EMAIL only. `toString()` leaves it out. */
    val passwordHash: String?,
) {
    override fun toString(): String = "IdentityRow(id=$id, provider=$provider)"
}

class RefreshTokenRow(
    val id: Uuid,
    val userId: Uuid,
    val deviceId: Uuid,
    val tokenHash: String,
    val expiresAt: OffsetDateTime,
    val revokedAt: OffsetDateTime?,
)

class EmailTokenRow(
    val id: Uuid,
    val identityId: Uuid,
    val purpose: EmailTokenPurpose,
    val tokenHash: String,
    val expiresAt: OffsetDateTime,
    val usedAt: OffsetDateTime?,
)
