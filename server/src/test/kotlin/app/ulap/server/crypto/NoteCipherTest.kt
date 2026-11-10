@file:OptIn(ExperimentalUuidApi::class)

package app.ulap.server.crypto

import app.ulap.server.testConfig
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** AES-256-GCM sealing of `note` and `triggerOther` (api-spec.md §11). */
class NoteCipherTest {
    private val cipher = NoteCipher.from(testConfig().noteEncryption)
    private val checkInId = Uuid.parse("019a4f2e-6b1c-7d3a-9f20-5c8e1b7a4d60")

    @Test
    fun sealedNotesOpenToTheSameText() {
        val sealed = cipher.seal(checkInId, note = "Pagod pero okay 🌧️", triggerOther = "Traffic sa EDSA")
        val opened = cipher.open(checkInId, sealed)
        assertEquals("k1", sealed.keyId)
        assertEquals("Pagod pero okay 🌧️", opened.note)
        assertEquals("Traffic sa EDSA", opened.triggerOther)
    }

    @Test
    fun nullFieldsStayNullAndNeedNoKey() {
        val empty = cipher.seal(checkInId, note = null, triggerOther = null)
        assertNull(empty.keyId)
        assertNull(empty.note)
        assertNull(empty.triggerOther)

        val noteOnly = cipher.seal(checkInId, note = "Just this", triggerOther = null)
        assertEquals("k1", noteOnly.keyId)
        assertNull(noteOnly.triggerOther)
        assertNull(cipher.open(checkInId, noteOnly).triggerOther)
    }

    @Test
    fun ciphertextHidesTheTextAndNeverRepeats() {
        val text = "a private thought"
        val first = assertNotNull(cipher.seal(checkInId, text, null).note)
        val second = assertNotNull(cipher.seal(checkInId, text, null).note)
        assertFalse(first.decodeToString(throwOnInvalidSequence = false).contains(text))
        // A fresh nonce each time: the same text never gives the same bytes.
        assertFalse(first.contentEquals(second))
    }

    @Test
    fun aValueMovedToAnotherCheckInDoesNotOpen() {
        val sealed = cipher.seal(checkInId, "mine", null)
        val otherId = Uuid.parse("019a4f2e-6b1c-7d3a-9f20-5c8e1b7a4d61")
        assertFailsWith<NoteCipherException> { cipher.open(otherId, sealed) }
    }

    @Test
    fun noteAndTriggerOtherCannotBeSwapped() {
        val sealed = cipher.seal(checkInId, note = "note text", triggerOther = "trigger text")
        val swapped = SealedNotes(sealed.keyId, note = sealed.triggerOther, triggerOther = sealed.note)
        assertFailsWith<NoteCipherException> { cipher.open(checkInId, swapped) }
    }

    @Test
    fun aChangedByteDoesNotOpen() {
        val sealed = cipher.seal(checkInId, "do not touch", null)
        val tampered = assertNotNull(sealed.note).copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertFailsWith<NoteCipherException> { cipher.open(checkInId, SealedNotes(sealed.keyId, tampered, null)) }
    }

    @Test
    fun anUnknownKeyIdOrMissingKeyIdFails() {
        val sealed = cipher.seal(checkInId, "text", null)
        assertFailsWith<NoteCipherException> { cipher.open(checkInId, SealedNotes("k9", sealed.note, null)) }
        assertFailsWith<NoteCipherException> { cipher.open(checkInId, SealedNotes(null, sealed.note, null)) }
    }

    @Test
    fun afterRotationOldRowsOpenAndNewRowsUseTheNewKey() {
        val k1 = NoteCipher.aesKey(testConfig().noteEncryption.key.value)
        val k2 = NoteCipher.aesKey(Base64.getEncoder().encodeToString(ByteArray(32) { 7 }))
        val old = cipher.seal(checkInId, "written before rotation", null)

        val rotated = NoteCipher(keys = mapOf("k1" to k1, "k2" to k2), currentKeyId = "k2")
        assertEquals("written before rotation", rotated.open(checkInId, old).note)
        val fresh = rotated.seal(checkInId, "written after", null)
        assertEquals("k2", fresh.keyId)
        // The old cipher doesn't have k2, so it can't open the new row.
        assertFailsWith<NoteCipherException> { cipher.open(checkInId, fresh) }
    }

    @Test
    fun openedTextAndErrorsNeverShowTheText() {
        val opened = cipher.open(checkInId, cipher.seal(checkInId, "secret words", "more secret"))
        assertEquals("OpenNotes(***)", opened.toString())

        val error = assertFailsWith<NoteCipherException> {
            cipher.open(Uuid.random(), cipher.seal(checkInId, "secret words", null))
        }
        assertFalse(error.message.orEmpty().contains("secret"))
    }

    @Test
    fun keysMustBeAes256() {
        val short = NoteCipher.aesKey(Base64.getEncoder().encodeToString(ByteArray(16)))
        assertFailsWith<IllegalArgumentException> { NoteCipher(mapOf("k1" to short), "k1") }
        val good = NoteCipher.aesKey(testConfig().noteEncryption.key.value)
        assertFailsWith<IllegalArgumentException> { NoteCipher(mapOf("k1" to good), "k2") }
    }

    @Test
    fun sealedFormatStartsWithVersionThenNonce() {
        val sealed = assertNotNull(cipher.seal(checkInId, "x", null).note)
        assertEquals(1, sealed[0].toInt())
        // 1 version byte + 12 nonce bytes + 1 byte of text + 16 tag bytes.
        assertEquals(30, sealed.size)
    }
}
