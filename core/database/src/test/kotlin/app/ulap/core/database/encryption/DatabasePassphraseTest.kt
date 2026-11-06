package app.ulap.core.database.encryption

import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The passphrase file logic, with a software keyset standing in for the Keystore-locked one.
 * EncryptedDatabaseTest in androidTest uses the real Keystore.
 */
@RunWith(RobolectricTestRunner::class)
class DatabasePassphraseTest {
    private val dir = createTempDirectory("passphrase").toFile()
    private val file = File(dir, "db_passphrase.bin")
    private val aead = newAead()

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `the first call makes a new 32-byte passphrase and stores it encrypted`() {
        val passphrase = DatabasePassphrase(file, aead).getOrCreate()

        assertTrue(passphrase.isNew)
        assertEquals(32, passphrase.bytes.size)
        assertFalse(file.readBytes().containsSequence(passphrase.bytes))
    }

    @Test
    fun `later calls return the same passphrase`() {
        val first = DatabasePassphrase(file, aead).getOrCreate()

        val second = DatabasePassphrase(file, aead).getOrCreate()

        assertFalse(second.isNew)
        assertContentEquals(first.bytes, second.bytes)
    }

    @Test
    fun `a passphrase the keyset can't decrypt is replaced with a new one`() {
        val lost = DatabasePassphrase(file, newAead()).getOrCreate()

        val replaced = DatabasePassphrase(file, aead).getOrCreate()
        val reread = DatabasePassphrase(file, aead).getOrCreate()

        assertTrue(replaced.isNew)
        assertFalse(lost.bytes.contentEquals(replaced.bytes))
        assertFalse(reread.isNew)
        assertContentEquals(replaced.bytes, reread.bytes)
    }

    @Test
    fun `a damaged file is replaced with a new passphrase`() {
        file.writeBytes(ByteArray(60))

        assertTrue(DatabasePassphrase(file, aead).getOrCreate().isNew)
    }

    @Test
    fun `the old database is cleared before a new passphrase is saved`() {
        val savedWhenCleared = mutableListOf<Boolean>()

        DatabasePassphrase(file, aead).getOrCreate(beforeNewPassphrase = { savedWhenCleared += file.exists() })

        // Killed between the two steps, the next start still has no passphrase, so it clears
        // the database again instead of keeping a file nothing can open.
        assertEquals(listOf(false), savedWhenCleared)
    }

    @Test
    fun `an existing passphrase leaves the database alone`() {
        DatabasePassphrase(file, aead).getOrCreate()
        var cleared = false

        DatabasePassphrase(file, aead).getOrCreate(beforeNewPassphrase = { cleared = true })

        assertFalse(cleared)
    }

    @Test
    fun `a passphrase the keyset can't decrypt clears the database before the new one is saved`() {
        DatabasePassphrase(file, newAead()).getOrCreate()
        val savedWhenCleared = mutableListOf<Boolean>()

        DatabasePassphrase(file, aead).getOrCreate(beforeNewPassphrase = { savedWhenCleared += file.exists() })

        assertEquals(listOf(false), savedWhenCleared)
    }

    private fun ByteArray.containsSequence(part: ByteArray): Boolean =
        (0..size - part.size).any { start -> part.indices.all { this[start + it] == part[it] } }

    private companion object {
        fun newAead(): Aead {
            AeadConfig.register()
            return KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM, RegistryConfiguration.get())
                .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        }
    }
}
