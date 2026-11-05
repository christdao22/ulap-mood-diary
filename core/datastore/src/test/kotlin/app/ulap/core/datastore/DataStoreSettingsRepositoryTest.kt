package app.ulap.core.datastore

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioStorage
import app.ulap.core.model.AppLanguage
import app.ulap.core.model.UserSettings
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toOkioPath

class DataStoreSettingsRepositoryTest {
    private val dir = createTempDirectory("settings").toFile()
    private val file = File(dir, "settings.json")

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    // The same storage as the app's DataStoreModule, on a temporary file.
    private fun repository(scope: CoroutineScope) = DataStoreSettingsRepository(
        DataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, SettingsSerializer, producePath = { file.toOkioPath() }),
            corruptionHandler = ReplaceFileCorruptionHandler { SettingsFile() },
            scope = scope,
        ),
    )

    @Test
    fun `a new install has no language yet, shows Ulap and hasn't done onboarding`() = runTest {
        assertEquals(
            UserSettings(language = null, mascotEnabled = true, onboardingDone = false),
            repository(backgroundScope).settings.first(),
        )
    }

    @Test
    fun `changes are saved and come back`() = runTest {
        val repository = repository(backgroundScope)

        repository.setLanguage(AppLanguage.BISAYA)
        repository.setMascotEnabled(false)
        repository.setOnboardingDone()

        assertEquals(
            UserSettings(AppLanguage.BISAYA, mascotEnabled = false, onboardingDone = true),
            repository.settings.first(),
        )
        assertTrue(file.readText().contains("\"ceb\""))
    }

    @Test
    fun `the device id is made once and then never changes`() = runTest {
        val repository = repository(backgroundScope)

        val ids = List(20) { async { repository.deviceId() } }.awaitAll()

        assertEquals(1, ids.toSet().size)
        assertTrue(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}").matches(ids.first()))
        assertEquals(ids.first(), repository.deviceId())
    }

    @Test
    fun `settings and the device id survive a restart`() = runTest {
        // DataStore allows one live instance per file, so the "old app" stops before the new one.
        val oldApp = Job()
        val first = repository(CoroutineScope(backgroundScope.coroutineContext + oldApp))
        first.setLanguage(AppLanguage.FILIPINO)
        val id = first.deviceId()
        oldApp.cancelAndJoin()

        val again = repository(backgroundScope)
        assertEquals(AppLanguage.FILIPINO, again.settings.first().language)
        assertEquals(id, again.deviceId())
    }

    @Test
    fun `a broken file starts over with the defaults and a new device id`() = runTest {
        file.writeText("{ not json")
        val repository = repository(backgroundScope)

        assertEquals(UserSettings(), repository.settings.first())
        assertNotEquals("", repository.deviceId())
    }

    @Test
    fun `an unknown language reads as not chosen`() = runTest {
        file.writeText("""{"language":"es","mascotEnabled":false}""")

        val settings = repository(backgroundScope).settings.first()

        assertEquals(null, settings.language)
        assertEquals(false, settings.mascotEnabled)
    }
}
