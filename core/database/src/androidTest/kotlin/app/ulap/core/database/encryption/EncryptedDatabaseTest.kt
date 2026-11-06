package app.ulap.core.database.encryption

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase as PlainSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.ulap.core.database.UlapDatabase
import app.ulap.core.database.model.CheckInEntity
import app.ulap.core.database.model.SyncState
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import com.google.crypto.tink.integration.android.AndroidKeystore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import net.zetetic.database.DatabaseErrorHandler as CipherErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherSQLiteDatabase
import org.junit.runner.RunWith

/**
 * The real encryption: SQLCipher's native library and the Android Keystore, so it runs on a
 * phone or emulator (technical-plan.md §6, "Tests"):
 * `./gradlew :core:database:connectedDebugAndroidTest`
 */
@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "encryption-test.db"
    private val row = checkIn("0192f3a0-0000-7000-8000-000000000001")

    // Stop the default handlers from deleting a file they can't read, which would hide a failure.
    private val keepFile = DatabaseErrorHandler { }
    private val cipherKeepFile = CipherErrorHandler { _, _ -> }

    @BeforeTest
    @AfterTest
    fun clean() {
        context.deleteDatabase(name)
        DatabasePassphrase.passphraseFile(context).delete()
    }

    @Test
    fun fileOnDiskIsNotPlainSqlite() = runTest {
        buildEncryptedDatabase(context, name).use { it.checkInDao().insert(row) }

        // Every plain SQLite file starts with this text. An encrypted one looks random.
        val header = ByteArray(16)
        context.getDatabasePath(name).inputStream().use { it.read(header) }
        assertFalse(header.contentEquals("SQLite format 3\u0000".toByteArray()))
    }

    @Test
    fun fileCannotBeOpenedWithoutThePassphrase() = runTest {
        buildEncryptedDatabase(context, name).use { it.checkInDao().insert(row) }
        val path = context.getDatabasePath(name).path

        // Android's own SQLite, with no passphrase at all.
        assertFails {
            PlainSQLiteDatabase.openDatabase(path, null, PlainSQLiteDatabase.OPEN_READONLY, keepFile).use { db ->
                db.rawQuery("SELECT count(*) FROM check_ins", null).use { it.moveToFirst() }
            }
        }
        // SQLCipher, with the wrong passphrase.
        System.loadLibrary("sqlcipher")
        assertFails {
            CipherSQLiteDatabase.openDatabase(
                path,
                ByteArray(32),
                null,
                CipherSQLiteDatabase.OPEN_READONLY,
                cipherKeepFile,
                null,
            ).use { db -> db.rawQuery("SELECT count(*) FROM check_ins", null).use { it.moveToFirst() } }
        }
    }

    @Test
    fun reopensWithTheStoredPassphrase() = runTest {
        buildEncryptedDatabase(context, name).use { it.checkInDao().insert(row) }

        buildEncryptedDatabase(context, name).use { db ->
            assertEquals(row, db.checkInDao().get(row.id))
        }
    }

    @Test
    fun unreadablePassphraseStartsAnEmptyDatabase() = runTest {
        buildEncryptedDatabase(context, name).use { it.checkInDao().insert(row) }
        DatabasePassphrase.passphraseFile(context).writeBytes(ByteArray(60))

        buildEncryptedDatabase(context, name).use { db ->
            assertNull(db.checkInDao().get(row.id))
        }
    }

    @Test
    fun missingPassphraseFileStartsAnEmptyDatabase() = runTest {
        // What a kill between clearing the database and saving a new passphrase can leave.
        buildEncryptedDatabase(context, name).use { it.checkInDao().insert(row) }
        DatabasePassphrase.passphraseFile(context).delete()

        buildEncryptedDatabase(context, name).use { db ->
            assertNull(db.checkInDao().get(row.id))
        }
    }

    @Test
    fun lostKeystoreKeyStartsAnEmptyDatabase() = runTest {
        buildEncryptedDatabase(context, name).use { it.checkInDao().insert(row) }
        // What a lock-screen reset can do: the Keystore key that locks the keyset is gone.
        AndroidKeystore.deleteKey(DatabasePassphrase.MASTER_KEY_ALIAS)

        buildEncryptedDatabase(context, name).use { db ->
            assertNull(db.checkInDao().get(row.id))
            db.checkInDao().insert(row)
        }
        // And the new key and passphrase work from then on.
        buildEncryptedDatabase(context, name).use { db ->
            assertEquals(row, db.checkInDao().get(row.id))
        }
    }

    private inline fun <T> UlapDatabase.use(block: (UlapDatabase) -> T): T = try {
        block(this)
    } finally {
        close()
    }

    private fun checkIn(id: String): CheckInEntity {
        val at = Instant.parse("2026-11-04T01:15:00Z")
        return CheckInEntity(
            id = id,
            moodCode = "worried",
            energy = 6,
            stress = 8,
            stressAfter = null,
            trigger = Trigger.WORK,
            triggerOther = null,
            note = "Report due tomorrow.",
            supportUsed = SupportType.NONE,
            isGoodMoment = false,
            loggedAt = at,
            tzOffsetMin = 480,
            createdAt = at,
            updatedAt = at,
            deletedAt = null,
            syncState = SyncState.PENDING,
            syncError = null,
            syncAttempts = 0,
            serverSeq = null,
        )
    }
}
