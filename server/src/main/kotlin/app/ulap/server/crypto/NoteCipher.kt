@file:OptIn(ExperimentalUuidApi::class)

package app.ulap.server.crypto

import app.ulap.server.config.AppConfig
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Application-level encryption for the two free-text journal fields, `note` and `triggerOther`
 * (api-spec.md §11, technical-plan.md §7). The database, its disk and its backups only ever see
 * ciphertext, so a leaked dump or a stray `SELECT` shows no journal text.
 *
 * AES-256-GCM with a fresh random 12-byte nonce per value. A sealed value is
 * `[version 0x01][nonce, 12 bytes][ciphertext + 16-byte tag]`. The check-in id and the field
 * name are bound in as associated data, so a value copied into another row or swapped between
 * `note` and `trigger_other` fails to open instead of showing up in the wrong place.
 *
 * Rotation (the `key_id` column): new values are always sealed with [currentKeyId]; [keys] also
 * holds retired keys so older rows still open. A row is re-sealed with the current key whenever
 * it's written again.
 *
 * Nothing here logs, and no exception message carries the text or the key.
 */
class NoteCipher(
    private val keys: Map<String, SecretKey>,
    val currentKeyId: String,
    private val random: SecureRandom = SecureRandom(),
) {
    init {
        require(currentKeyId in keys) { "The current note key id has no key" }
        require(keys.values.all { it.encoded.size == KEY_BYTES }) { "Note keys must be 32 bytes (AES-256)" }
    }

    /** Seals both fields of one check-in with the current key. Null stays null. */
    fun seal(checkInId: Uuid, note: String?, triggerOther: String?): SealedNotes {
        if (note == null && triggerOther == null) return SealedNotes(keyId = null, note = null, triggerOther = null)
        val key = keys.getValue(currentKeyId)
        return SealedNotes(
            keyId = currentKeyId,
            note = note?.let { seal(key, it, aad(checkInId, Field.NOTE)) },
            triggerOther = triggerOther?.let { seal(key, it, aad(checkInId, Field.TRIGGER_OTHER)) },
        )
    }

    /** Opens what [seal] produced for the same check-in, or throws [NoteCipherException]. */
    fun open(checkInId: Uuid, sealed: SealedNotes): OpenNotes {
        if (sealed.note == null && sealed.triggerOther == null) return OpenNotes(note = null, triggerOther = null)
        val keyId = sealed.keyId ?: throw NoteCipherException("Check-in $checkInId has sealed notes but no key id")
        val key = keys[keyId] ?: throw NoteCipherException("Check-in $checkInId uses unknown note key id \"$keyId\"")
        return OpenNotes(
            note = sealed.note?.let { open(key, it, aad(checkInId, Field.NOTE), checkInId) },
            triggerOther = sealed.triggerOther?.let { open(key, it, aad(checkInId, Field.TRIGGER_OTHER), checkInId) },
        )
    }

    private fun seal(key: SecretKey, plaintext: String, aad: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext.encodeToByteArray())
        return byteArrayOf(FORMAT_VERSION) + nonce + ciphertext
    }

    private fun open(key: SecretKey, sealed: ByteArray, aad: ByteArray, checkInId: Uuid): String {
        if (sealed.size < HEADER_BYTES + TAG_BYTES || sealed[0] != FORMAT_VERSION) {
            throw NoteCipherException("Check-in $checkInId has a sealed note in an unknown format")
        }
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed, 1, NONCE_BYTES))
            cipher.updateAAD(aad)
            cipher.doFinal(sealed, HEADER_BYTES, sealed.size - HEADER_BYTES).decodeToString()
        } catch (e: GeneralSecurityException) {
            // A wrong key, a changed byte or a value from another row or field all end up here.
            throw NoteCipherException("Check-in $checkInId has a sealed note that doesn't open", e)
        }
    }

    private fun aad(checkInId: Uuid, field: Field): ByteArray =
        "ulap:check_ins:${field.column}:$checkInId".encodeToByteArray()

    private enum class Field(val column: String) { NOTE("note"), TRIGGER_OTHER("trigger_other") }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val FORMAT_VERSION: Byte = 1
        private const val KEY_BYTES = 32
        private const val NONCE_BYTES = 12
        private const val TAG_BYTES = 16
        private const val TAG_BITS = TAG_BYTES * 8
        private const val HEADER_BYTES = 1 + NONCE_BYTES

        /** The key from `NOTE_ENCRYPTION_KEY` (base64, already checked by [AppConfig]) under its id. */
        fun from(config: AppConfig.NoteEncryptionConfig): NoteCipher = NoteCipher(
            keys = mapOf(config.keyId to aesKey(config.key.value)),
            currentKeyId = config.keyId,
        )

        fun aesKey(base64: String): SecretKey = SecretKeySpec(Base64.getDecoder().decode(base64), "AES")
    }
}

/** The `note`, `trigger_other` and `key_id` columns of one check-in, as stored. */
class SealedNotes(val keyId: String?, val note: ByteArray?, val triggerOther: ByteArray?)

/** The opened journal text. `toString()` hides it, so it can't reach a log by accident. */
class OpenNotes(val note: String?, val triggerOther: String?) {
    override fun toString(): String = "OpenNotes(***)"
}

/** A sealed value that can't be opened. The message names the check-in, never its text. */
class NoteCipherException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
