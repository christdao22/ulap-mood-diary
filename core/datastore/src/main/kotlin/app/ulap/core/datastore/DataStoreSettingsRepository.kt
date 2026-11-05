package app.ulap.core.datastore

import androidx.datastore.core.DataStore
import app.ulap.core.domain.settings.SettingsRepository
import app.ulap.core.model.AppLanguage
import app.ulap.core.model.UserSettings
import javax.inject.Inject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Settings in a DataStore file on this phone only (technical-plan.md §6). */
internal class DataStoreSettingsRepository @Inject constructor(private val dataStore: DataStore<SettingsFile>) :
    SettingsRepository {
    override val settings: Flow<UserSettings> = dataStore.data
        .map { file ->
            UserSettings(
                // A tag this version doesn't know reads as "not chosen yet".
                language = AppLanguage.fromTag(file.language),
                mascotEnabled = file.mascotEnabled,
                onboardingDone = file.onboardingDone,
            )
        }
        .distinctUntilChanged()

    override suspend fun setLanguage(language: AppLanguage) {
        dataStore.updateData { it.copy(language = language.tag) }
    }

    override suspend fun setMascotEnabled(enabled: Boolean) {
        dataStore.updateData { it.copy(mascotEnabled = enabled) }
    }

    override suspend fun setOnboardingDone() {
        dataStore.updateData { it.copy(onboardingDone = true) }
    }

    /**
     * Made inside one update, so two first calls at once still agree on one ID. A random UUID
     * (version 4), not a UUIDv7: the ID shouldn't reveal when the app was installed.
     */
    @OptIn(ExperimentalUuidApi::class)
    override suspend fun deviceId(): String {
        val file = dataStore.updateData { file ->
            if (file.deviceId != null) file else file.copy(deviceId = Uuid.random().toString())
        }
        return checkNotNull(file.deviceId)
    }
}
