package app.ulap.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.okio.OkioSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.BufferedSink
import okio.BufferedSource

/**
 * The settings file as stored (technical-plan.md §6). Every field has a default, so a field a
 * later version adds needs no migration. The sync position fields arrive with Phase 7.
 */
@Serializable
internal data class SettingsFile(
    /** `en`, `fil` or `ceb`; `null` until chosen. */
    val language: String? = null,
    val mascotEnabled: Boolean = true,
    val onboardingDone: Boolean = false,
    /** Made on first use, see [DataStoreSettingsRepository.deviceId]. */
    val deviceId: String? = null,
)

/** DataStore reads and writes the whole file through this, as JSON. */
internal object SettingsSerializer : OkioSerializer<SettingsFile> {
    private val json = Json { ignoreUnknownKeys = true }

    override val defaultValue: SettingsFile = SettingsFile()

    override suspend fun readFrom(source: BufferedSource): SettingsFile {
        val text = source.readUtf8()
        if (text.isBlank()) return defaultValue
        return try {
            json.decodeFromString(text)
        } catch (e: SerializationException) {
            // The corruption handler then starts over with the defaults (and a new device ID).
            throw CorruptionException("Settings file isn't valid", e)
        }
    }

    override suspend fun writeTo(t: SettingsFile, sink: BufferedSink) {
        sink.writeUtf8(json.encodeToString(t))
    }
}
