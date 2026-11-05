package app.ulap.core.database.encryption

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import app.ulap.core.database.UlapDatabase
import app.ulap.core.database.checkIn
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The deferral on its own, with plain SQLite standing in for SQLCipher (whose native library
 * doesn't load on the JVM). EncryptedDatabaseTest in androidTest covers the real thing.
 */
@RunWith(RobolectricTestRunner::class)
class DeferredOpenHelperFactoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "deferred-test.db"

    private var opens = 0
    private var openedOnMainThread = false
    private val walCalls = mutableListOf<Boolean>()

    private val factory = DeferredOpenHelperFactory { configuration ->
        opens++
        openedOnMainThread = Looper.myLooper() == Looper.getMainLooper()
        RecordingHelper(FrameworkSQLiteOpenHelperFactory().create(configuration))
    }

    private fun build(): UlapDatabase = Room.databaseBuilder(context, UlapDatabase::class.java, name)
        .openHelperFactory(factory)
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .build()

    @AfterTest
    fun tearDown() {
        context.deleteDatabase(name)
    }

    @Test
    fun `building the database doesn't open it`() {
        val database = build()

        assertEquals(0, opens)
        database.close()
    }

    @Test
    fun `the first query opens it once, off the main thread`() = runTest {
        val database = build()

        database.checkInDao().insert(checkIn("a"))
        val saved = database.checkInDao().get("a")

        assertEquals("a", saved?.id)
        assertEquals(1, opens)
        assertFalse(openedOnMainThread)
        database.close()
    }

    @Test
    fun `Room's write-ahead logging choice reaches the real helper`() = runTest {
        val database = build()

        database.checkInDao().get("a")

        assertEquals(listOf(true), walCalls)
        database.close()
    }

    @Test
    fun `closing before the first query doesn't open it`() {
        build().close()

        assertEquals(0, opens)
    }

    private inner class RecordingHelper(private val inner: SupportSQLiteOpenHelper) : SupportSQLiteOpenHelper by inner {
        override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
            walCalls += enabled
            inner.setWriteAheadLoggingEnabled(enabled)
        }
    }
}
