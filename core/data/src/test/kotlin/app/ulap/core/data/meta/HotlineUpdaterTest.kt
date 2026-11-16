package app.ulap.core.data.meta

import app.ulap.core.domain.settings.SettingsRepository
import app.ulap.core.model.AppLanguage
import app.ulap.core.model.PrivacyConsent
import app.ulap.core.model.UserSettings
import app.ulap.core.network.ApiResult
import app.ulap.core.network.meta.MetaApi
import app.ulap.shared.api.HotlineInfo
import app.ulap.shared.api.MetaResponse
import app.ulap.shared.api.PlatformVersions
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest

class HotlineUpdaterTest {
    private class FixedMeta(var answer: ApiResult<MetaResponse>) : MetaApi {
        override suspend fun meta(): ApiResult<MetaResponse> = answer
    }

    /** Settings in memory. core:testing's fake isn't a dependency of core:data. */
    private class MemorySettings(hotlineNumber: String? = null) : SettingsRepository {
        override val settings = MutableStateFlow(UserSettings(hotlineNumber = hotlineNumber))
        var writes = 0

        override suspend fun setLanguage(language: AppLanguage) = Unit

        override suspend fun setMascotEnabled(enabled: Boolean) = Unit

        override suspend fun setOnboardingDone(consent: PrivacyConsent) = Unit

        override suspend fun deviceId(): String = "device-1"

        override suspend fun setHotlineNumber(number: String?) {
            writes++
            settings.update { it.copy(hotlineNumber = number) }
        }

        override suspend fun clear() = Unit
    }

    private fun meta(number: String) = ApiResult.Success(
        MetaResponse(
            minAppVersion = PlatformVersions(android = 1),
            latestAppVersion = PlatformVersions(android = 1),
            moodCatalogVersion = 1,
            hotline = HotlineInfo("NCMH Crisis Hotline", number),
        ),
    )

    @Test
    fun `a new number from meta is saved`() = runTest {
        val settings = MemorySettings()

        HotlineUpdater(FixedMeta(meta("1554")), settings, backgroundScope).update()

        assertEquals("1554", settings.settings.value.hotlineNumber)
    }

    @Test
    fun `the built-in number writes nothing when nothing is saved`() = runTest {
        val settings = MemorySettings()

        HotlineUpdater(FixedMeta(meta("1553")), settings, backgroundScope).update()

        assertEquals(null, settings.settings.value.hotlineNumber)
        assertEquals(0, settings.writes)
    }

    @Test
    fun `the built-in number from meta clears a saved one`() = runTest {
        val settings = MemorySettings(hotlineNumber = "1554")

        HotlineUpdater(FixedMeta(meta("1553")), settings, backgroundScope).update()

        assertEquals(null, settings.settings.value.hotlineNumber)
    }

    @Test
    fun `offline, a server error or a bad number keep the saved number`() = runTest {
        val settings = MemorySettings(hotlineNumber = "1554")
        val api = FixedMeta(ApiResult.NetworkError(IOException("offline")))
        val updater = HotlineUpdater(api, settings, backgroundScope)

        updater.update()
        api.answer = ApiResult.Failure(status = 503, code = null)
        updater.update()
        api.answer = meta("call us")
        updater.update()

        assertEquals("1554", settings.settings.value.hotlineNumber)
        assertEquals(0, settings.writes)
    }
}
