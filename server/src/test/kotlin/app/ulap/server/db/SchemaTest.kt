@file:OptIn(ExperimentalUuidApi::class)

package app.ulap.server.db

import app.ulap.server.crypto.NoteCipher
import app.ulap.server.crypto.SealedNotes
import app.ulap.shared.model.Ids
import java.sql.SQLException
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * V2__init_schema.sql (technical-plan.md §7) through the Exposed tables and [Db.tx], on a real
 * PostgreSQL ([TestPostgres]). Every test makes its own user, so tests don't see each other's rows.
 */
class SchemaTest {
    private val db = Db(TestPostgres.dataSource)
    private val cipher = NoteCipher.from(TestPostgres.config.noteEncryption)
    private val loggedAt = OffsetDateTime.of(2026, 11, 10, 8, 30, 0, 0, ZoneOffset.ofHours(8))

    @Test
    fun aCheckInRoundTripsWithItsNotesSealed() = runBlocking {
        val userId = newUser()
        val checkInId = newId()
        val sealed = cipher.seal(checkInId, note = "Masaya ako ngayon", triggerOther = "Bagong trabaho")
        db.tx { insertCheckIn(checkInId, userId, sealed) }

        val row = db.tx { CheckInsTable.selectAll().where { CheckInsTable.id eq checkInId }.single() }
        assertEquals(userId, row[CheckInsTable.userId])
        assertEquals(7.toShort(), row[CheckInsTable.energy])
        assertEquals(loggedAt.toInstant(), row[CheckInsTable.loggedAt].toInstant())
        assertEquals("k1", row[CheckInsTable.keyId])
        assertTrue(row[CheckInsTable.serverSeq] > 0)

        val opened = cipher.open(
            checkInId,
            SealedNotes(row[CheckInsTable.keyId], row[CheckInsTable.note], row[CheckInsTable.triggerOther]),
        )
        assertEquals("Masaya ako ngayon", opened.note)
        assertEquals("Bagong trabaho", opened.triggerOther)
    }

    @Test
    fun theDatabaseOnlyEverHoldsCiphertext() = runBlocking {
        val userId = newUser()
        val checkInId = newId()
        db.tx { insertCheckIn(checkInId, userId, cipher.seal(checkInId, "nobody reads this", null)) }

        // What a database dump or a stray SELECT would show.
        val stored = TestPostgres.dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT encode(note, 'escape') FROM check_ins WHERE id = ?::uuid").use {
                it.setString(1, checkInId.toString())
                it.executeQuery().use { rows ->
                    rows.next()
                    rows.getString(1)
                }
            }
        }
        assertFalse(stored.contains("nobody reads this"))
    }

    @Test
    fun serverSeqRisesWithEveryInsert() = runBlocking {
        val userId = newUser()
        val first = newId()
        val second = newId()
        db.tx {
            insertCheckIn(first, userId)
            insertCheckIn(second, userId)
        }
        val seqs = db.tx {
            listOf(first, second).map { id ->
                CheckInsTable.selectAll().where { CheckInsTable.id eq id }.single()[CheckInsTable.serverSeq]
            }
        }
        assertTrue(seqs[1] > seqs[0])
    }

    @Test
    fun theSchemaRejectsValuesTheApiNeverAllows() = runBlocking {
        val userId = newUser()
        assertFailsWith<SQLException> { db.tx { insertCheckIn(newId(), userId, energy = 11) } }
        assertFailsWith<SQLException> {
            // Ciphertext with no key id could never be opened.
            db.tx {
                insertCheckIn(newId(), userId, SealedNotes(keyId = null, note = byteArrayOf(1), triggerOther = null))
            }
        }
        assertFailsWith<SQLException> { newUser(language = "es") }
        assertFailsWith<SQLException> {
            db.tx { insertIdentity(userId, AuthProvider.GOOGLE, "google-sub", passwordHash = "only for email") }
        }
        db.tx { insertIdentity(userId, AuthProvider.GOOGLE, "google-sub-$userId") }
        // One Google account signs in to one Ulap user.
        val otherUser = newUser()
        assertFailsWith<SQLException> { db.tx { insertIdentity(otherUser, AuthProvider.GOOGLE, "google-sub-$userId") } }
        Unit
    }

    @Test
    fun deletingAUserDeletesEverythingTheyOwn() = runBlocking {
        val userId = newUser()
        db.tx {
            val identityId = insertIdentity(userId, AuthProvider.EMAIL, "person-$userId@example.com", "argon2-hash")
            EmailTokensTable.insert {
                it[id] = newId()
                it[EmailTokensTable.identityId] = identityId
                it[purpose] = EmailTokenPurpose.VERIFY.dbValue
                it[tokenHash] = "verify-hash-$userId"
                it[expiresAt] = loggedAt.plusHours(24)
            }
            RefreshTokensTable.insert {
                it[id] = newId()
                it[RefreshTokensTable.userId] = userId
                it[deviceId] = newId()
                it[tokenHash] = "refresh-hash-$userId"
                it[expiresAt] = loggedAt.plusDays(60)
            }
            insertCheckIn(newId(), userId)
        }

        // DELETE /me is one statement thanks to ON DELETE CASCADE (api-spec.md §7.3).
        db.tx { UsersTable.deleteWhere { UsersTable.id eq userId } }

        db.tx {
            assertEquals(0, AuthIdentitiesTable.selectAll().where { AuthIdentitiesTable.userId eq userId }.count())
            assertEquals(0, RefreshTokensTable.selectAll().where { RefreshTokensTable.userId eq userId }.count())
            assertEquals(
                0,
                EmailTokensTable.selectAll().where {
                    EmailTokensTable.tokenHash eq "verify-hash-$userId"
                }.count(),
            )
            assertEquals(0, CheckInsTable.selectAll().where { CheckInsTable.userId eq userId }.count())
        }
    }

    @Test
    fun aFailureInsideTxRollsBackTheWholeBlock() = runBlocking {
        val userId = newUser()
        val kept = newId()
        assertFailsWith<SQLException> {
            db.tx {
                insertCheckIn(kept, userId)
                insertCheckIn(newId(), userId, energy = 0)
            }
        }
        assertEquals(0, db.tx { CheckInsTable.selectAll().where { CheckInsTable.id eq kept }.count() })
    }

    @Test
    fun syncPurgeStartsWithOneRowAtZero() = runBlocking {
        val rows = db.tx { SyncPurgeTable.selectAll().toList() }
        assertEquals(1, rows.size)
        assertEquals(1.toShort(), rows.single()[SyncPurgeTable.id])
        assertEquals(0L, rows.single()[SyncPurgeTable.purgedThroughSeq])
    }

    private suspend fun newUser(language: String = "en"): Uuid {
        val userId = newId()
        db.tx {
            UsersTable.insert {
                it[id] = userId
                it[UsersTable.language] = language
                it[mascotEnabled] = true
            }
        }
        val createdAt = db.tx {
            UsersTable.selectAll().where { UsersTable.id eq userId }.single()[UsersTable.createdAt]
        }
        assertNotNull(createdAt)
        return userId
    }

    private fun JdbcTransaction.insertIdentity(
        userId: Uuid,
        provider: AuthProvider,
        subject: String,
        passwordHash: String? = null,
    ): Uuid {
        val identityId = newId()
        AuthIdentitiesTable.insert {
            it[id] = identityId
            it[AuthIdentitiesTable.userId] = userId
            it[AuthIdentitiesTable.provider] = provider.name
            it[providerSubject] = subject
            it[emailVerified] = false
            it[AuthIdentitiesTable.passwordHash] = passwordHash
        }
        return identityId
    }

    private fun JdbcTransaction.insertCheckIn(
        checkInId: Uuid,
        userId: Uuid,
        notes: SealedNotes = SealedNotes(null, null, null),
        energy: Int = 7,
    ) {
        CheckInsTable.insert {
            it[id] = checkInId
            it[CheckInsTable.userId] = userId
            it[moodCode] = "CALM"
            it[CheckInsTable.energy] = energy.toShort()
            it[stress] = 3
            it[note] = notes.note
            it[triggerOther] = notes.triggerOther
            it[keyId] = notes.keyId
            it[supportUsed] = "NONE"
            it[isGoodMoment] = false
            it[CheckInsTable.loggedAt] = this@SchemaTest.loggedAt
            it[tzOffsetMin] = 480
            it[clientUpdatedAt] = this@SchemaTest.loggedAt
        }
    }

    private fun newId(): Uuid = Uuid.parse(Ids.newId())
}
