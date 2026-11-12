package app.ulap.core.auth.session

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SessionStoreTest {
    private val dir = createTempDirectory("session").toFile()
    private val file = File(dir, "session.enc")
    private val session = StoredSession(userId = "user-1", refreshToken = "refresh-secret", refreshTokenExpiresAt = 1L)

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `saves, reads back and clears`() = runTest {
        val store = testSessionStore(file, backgroundScope)

        store.save(session)
        assertEquals(session, store.current())

        store.clear(signInAgain = false)
        assertNull(store.current())
    }

    @Test
    fun `the refresh token is never written as plain text`() = runTest {
        testSessionStore(file, backgroundScope).save(session)

        assertFalse("refresh-secret" in file.readBytes().decodeToString())
    }

    @Test
    fun `a file encrypted with a lost key reads as signed out`() = runTest {
        testSessionStore(file, backgroundScope).save(session)
        // The same bytes opened with another key: what a restore onto another phone looks like.
        // (A copy, because DataStore allows one instance per file.)
        val restored = file.copyTo(File(dir, "restored.enc"))

        assertEquals(SessionFile(), testSessionStore(restored, backgroundScope, newTestAead()).file.first())
    }

    @Test
    fun `replace only saves over the expected refresh token`() = runTest {
        val store = testSessionStore(file, backgroundScope)
        store.save(session)
        val rotated = session.copy(refreshToken = "refresh-2")

        assertFalse(store.replace("someone-else", rotated))
        assertEquals(session, store.current())

        assertTrue(store.replace("refresh-secret", rotated))
        assertEquals(rotated, store.current())
    }

    @Test
    fun `clearIf leaves a newer session alone and marks a cleared one sign in again`() = runTest {
        val store = testSessionStore(file, backgroundScope)
        store.save(session)

        assertFalse(store.clearIf("old-token"))
        assertEquals(session, store.current())

        assertTrue(store.clearIf("refresh-secret"))
        assertEquals(SessionFile(signInAgain = true), store.file.first())
    }

    @Test
    fun `toString never shows the refresh token`() {
        assertFalse("refresh-secret" in session.toString())
    }
}
