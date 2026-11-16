package app.ulap.core.data.erase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.ulap.core.data.checkin.OfflineFirstCheckInRepository
import app.ulap.core.database.SyncLock
import app.ulap.core.database.UlapDatabase
import app.ulap.core.domain.checkin.SaveResult
import app.ulap.core.domain.settings.SettingsRepository
import app.ulap.core.model.AppLanguage
import app.ulap.core.model.PrivacyConsent
import app.ulap.core.model.UserSettings
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import app.ulap.shared.validation.CheckInInput
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OnDeviceDataEraserTest {
    private val t0 = Instant.parse("2026-11-04T01:15:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = t0
    }
    private lateinit var database: UlapDatabase

    /** Settings in memory. core:testing's fake isn't a dependency of core:data. */
    private class MemorySettings : SettingsRepository {
        override val settings = MutableStateFlow(UserSettings())

        override suspend fun setLanguage(language: AppLanguage) = settings.update { it.copy(language = language) }

        override suspend fun setMascotEnabled(enabled: Boolean) = settings.update { it.copy(mascotEnabled = enabled) }

        override suspend fun setOnboardingDone(consent: PrivacyConsent) =
            settings.update { it.copy(onboardingDone = true, consent = consent) }

        override suspend fun deviceId(): String = "device-1"

        override suspend fun setHotlineNumber(number: String?) = settings.update { it.copy(hotlineNumber = number) }

        override suspend fun clear() {
            settings.value = UserSettings(hotlineNumber = settings.value.hotlineNumber)
        }
    }

    @BeforeTest
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UlapDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun `erase empties the diary and resets the settings`() = runTest {
        val checkIns = OfflineFirstCheckInRepository(database, clock)
        assertIs<SaveResult.Saved>(checkIns.add(input))
        val settings = MemorySettings()
        settings.setLanguage(AppLanguage.BISAYA)
        settings.setMascotEnabled(false)
        settings.setOnboardingDone(PrivacyConsent(t0, PrivacyConsent.CURRENT_NOTICE_VERSION))

        OnDeviceDataEraser(database, settings, SyncLock(), Dispatchers.Unconfined).eraseAll()

        assertEquals(emptyList(), database.checkInDao().observeAll().first())
        assertEquals(UserSettings(), settings.settings.value)
    }

    private val input = CheckInInput(
        moodCode = "worried",
        energy = 6,
        stress = 8,
        stressAfter = null,
        trigger = Trigger.WORK,
        triggerOther = null,
        note = "Report due tomorrow.",
        supportUsed = SupportType.NONE,
        isGoodMoment = false,
        loggedAt = t0,
        tzOffsetMin = 480,
    )
}
