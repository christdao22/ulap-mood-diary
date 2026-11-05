package app.ulap.core.database.encryption

import android.content.Context
import android.util.AtomicFile
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.integration.android.AndroidKeystore
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.security.SecureRandom

/**
 * The key SQLCipher encrypts the database with (technical-plan.md §6, D3).
 *
 * It's 32 random bytes, made once per install. They're stored encrypted by [aead], a Tink
 * keyset that is itself locked by a key in the Android Keystore, which never leaves the phone's
 * secure hardware. The file sits in noBackupFilesDir, which Android never backs up.
 *
 * Never log [Passphrase.bytes] (CLAUDE.md rules).
 */
internal class DatabasePassphrase(private val file: File, private val aead: Aead) {
    /**
     * [isNew] is true when no earlier passphrase could be read. Any database left from before
     * can't be opened any more and must be deleted.
     */
    class Passphrase(val bytes: ByteArray, val isNew: Boolean)

    fun getOrCreate(): Passphrase {
        val stored = AtomicFile(file)
        if (file.exists()) {
            try {
                return Passphrase(aead.decrypt(stored.readFully(), ASSOCIATED_DATA), isNew = false)
            } catch (_: GeneralSecurityException) {
                // The keyset that wrapped it is gone (the Keystore key was lost, e.g. the lock
                // screen was reset) and so is any way to open the old database. Start over:
                // synced check-ins come back from the server (technical-plan.md §6). The file
                // can never be read again, so remove it. (That also keeps AtomicFile working in
                // JVM tests on Windows, where renaming onto an existing file fails.)
                stored.delete()
            }
        }
        val bytes = ByteArray(PASSPHRASE_BYTES).also(SecureRandom()::nextBytes)
        val encrypted = aead.encrypt(bytes, ASSOCIATED_DATA)
        val out = stored.startWrite()
        try {
            out.write(encrypted)
            stored.finishWrite(out)
        } catch (e: IOException) {
            // Keeps the previous file (if any) instead of leaving a half-written one.
            stored.failWrite(out)
            throw e
        }
        return Passphrase(bytes, isNew = true)
    }

    companion object {
        private const val PASSPHRASE_BYTES = 32

        /** Binds the ciphertext to this purpose, so it can't be swapped with another secret. */
        private val ASSOCIATED_DATA = "ulap database passphrase".toByteArray()

        private const val FILE_NAME = "db_passphrase.bin"

        // Internal so EncryptedDatabaseTest can simulate a lost key.
        internal const val KEYSET_PREFS = "ulap_db_keyset"
        private const val KEYSET_NAME = "db_passphrase_keyset"
        internal const val MASTER_KEY_ALIAS = "ulap_db_master_key"
        private const val MASTER_KEY_URI = "android-keystore://$MASTER_KEY_ALIAS"

        /** The real passphrase store. Slow (Keystore), so call it off the main thread. */
        fun forApp(context: Context): DatabasePassphrase {
            AeadConfig.register()
            val keysetManager = try {
                buildKeysetManager(context)
            } catch (_: GeneralSecurityException) {
                startOver(context)
            } catch (_: IOException) {
                startOver(context)
            } catch (_: ProviderException) {
                // The Keystore throws this RuntimeException for some broken-key states.
                startOver(context)
            }
            val aead = keysetManager.keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
            return DatabasePassphrase(passphraseFile(context), aead)
        }

        fun passphraseFile(context: Context): File = File(context.noBackupFilesDir, FILE_NAME)

        /**
         * The saved keyset can't be unlocked any more. Without it the passphrase is unreadable
         * too, so drop the keyset and its Keystore key and make new ones. [getOrCreate] then
         * fails to decrypt the old file and makes a new passphrase.
         */
        private fun startOver(context: Context): AndroidKeysetManager {
            context.deleteSharedPreferences(KEYSET_PREFS)
            try {
                // Tink refuses to replace a master key that exists but is unusable.
                AndroidKeystore.deleteKey(MASTER_KEY_ALIAS)
            } catch (_: GeneralSecurityException) {
                // No Keystore at all: Tink falls back to an unwrapped keyset below.
            }
            return buildKeysetManager(context)
        }

        private fun buildKeysetManager(context: Context): AndroidKeysetManager = AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
    }
}
