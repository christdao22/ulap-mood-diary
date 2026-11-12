package app.ulap.core.auth.session

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioSerializer
import com.google.crypto.tink.Aead
import java.security.GeneralSecurityException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.BufferedSink
import okio.BufferedSource

/**
 * What is kept on disk while signed in. The access token is not here: it lives in memory only
 * (api-spec.md §3), so after a restart the first request refreshes.
 */
@Serializable
internal data class StoredSession(
    val userId: String,
    val refreshToken: String,
    /** Epoch milliseconds. */
    val refreshTokenExpiresAt: Long,
    /** Null only in files written before the Account screen showed a profile. */
    val profile: StoredProfile? = null,
) {
    override fun toString(): String = "StoredSession(userId=$userId, refreshToken=***)"
}

/** What the Account screen shows about the person. Kept in the encrypted file, never in plain DataStore. */
@Serializable
internal data class StoredProfile(
    val displayName: String? = null,
    val email: String? = null,
    val emailVerified: Boolean = false,
    /** Epoch milliseconds. */
    val createdAt: Long,
) {
    override fun toString(): String = "StoredProfile(emailVerified=$emailVerified)"
}

/**
 * The file's whole content. [session] is null when signed out. [signInAgain] is true when the
 * server ended the session (a refresh got a 401), so the app can say "Sign in again to keep
 * syncing" instead of treating the person as a new guest (api-spec.md §3, Client flow, step 3).
 */
@Serializable
internal data class SessionFile(val session: StoredSession? = null, val signInAgain: Boolean = false)

/**
 * The session, in a DataStore file encrypted with Tink (ulap-reference.md §5, Session storage).
 * DataStore gives atomic writes and a Flow of changes; Tink makes the file useless off this
 * phone. SessionManager is the only writer, under its mutex.
 */
internal class SessionStore(private val dataStore: DataStore<SessionFile>) {
    val file: Flow<SessionFile> = dataStore.data

    suspend fun current(): StoredSession? = dataStore.data.first().session

    suspend fun save(session: StoredSession) {
        dataStore.updateData { SessionFile(session) }
    }

    /** Signed out. [signInAgain] says the server ended it, not the person. */
    suspend fun clear(signInAgain: Boolean) {
        dataStore.updateData { SessionFile(signInAgain = signInAgain) }
    }

    /**
     * Saves [session] only if [expectedRefreshToken] is still the stored one, so a refresh that
     * finishes after a sign-out can't bring the session back. True when it was saved.
     */
    suspend fun replace(expectedRefreshToken: String, session: StoredSession): Boolean =
        updateIfCurrent(expectedRefreshToken) { SessionFile(session) }

    /** Ends the session the server rejected, unless a newer one replaced it meanwhile. */
    suspend fun clearIf(expectedRefreshToken: String): Boolean =
        updateIfCurrent(expectedRefreshToken) { SessionFile(signInAgain = true) }

    private suspend fun updateIfCurrent(expectedRefreshToken: String, next: () -> SessionFile): Boolean {
        var updated = false
        dataStore.updateData { file ->
            if (file.session?.refreshToken == expectedRefreshToken) {
                updated = true
                next()
            } else {
                updated = false
                file
            }
        }
        return updated
    }
}

/**
 * Reads and writes the file as AES-256-GCM ciphertext of its JSON. [aead] is lazy because
 * creating it talks to the Android Keystore, which shouldn't happen on the main thread;
 * DataStore first reads on its own background thread.
 */
internal class SessionSerializer(private val aead: Lazy<Aead>) : OkioSerializer<SessionFile> {
    private val json = Json { ignoreUnknownKeys = true }

    override val defaultValue = SessionFile()

    override suspend fun readFrom(source: BufferedSource): SessionFile {
        val bytes = source.readByteArray()
        if (bytes.isEmpty()) return defaultValue
        return try {
            json.decodeFromString(aead.value.decrypt(bytes, ASSOCIATED_DATA).decodeToString())
        } catch (e: GeneralSecurityException) {
            // The Keystore key is gone (data restored onto another phone, or the lock screen
            // was reset). DataStore's corruption handler then starts over signed out
            // (technical-plan.md §6, If the Keystore key is lost).
            throw CorruptionException("Session file can't be decrypted", e)
        } catch (e: SerializationException) {
            throw CorruptionException("Session file isn't valid", e)
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("Session file isn't valid", e)
        }
    }

    override suspend fun writeTo(t: SessionFile, sink: BufferedSink) {
        sink.write(aead.value.encrypt(json.encodeToString(t).encodeToByteArray(), ASSOCIATED_DATA))
    }

    private companion object {
        /** Binds the ciphertext to this purpose, so it can't be swapped with another secret. */
        val ASSOCIATED_DATA = "ulap session".encodeToByteArray()
    }
}
