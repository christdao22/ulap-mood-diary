package app.ulap.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Every schema version must upgrade to the next without losing data (ulap-reference.md §5).
 * Old versions are built from the committed JSON files in schemas/, so this tests exactly what an
 * installed app has. Add a test here with each new version.
 *
 * Uses the driver-based helper: the SupportSQLite one fails on Windows, where its driver compares
 * file names by splitting paths on '/' (found in macro-tracker).
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = context.getDatabasePath(DB),
        driver = AndroidSQLiteDriver(),
        databaseClass = UlapDatabase::class,
    )

    @AfterTest
    fun clean() {
        context.deleteDatabase(DB)
    }

    /**
     * Version 1 built from schemas/1.json opens with the app's code. Room checks the schema's
     * identity hash on open, so this fails if the entities changed without a new version.
     */
    @Test
    fun `the committed version 1 schema matches the app's code`() = runTest {
        helper.createDatabase(1).close()

        val database = Room.databaseBuilder(context, UlapDatabase::class.java, DB).build()
        try {
            database.checkInDao().insert(checkIn("a"))
            assertEquals(checkIn("a"), database.checkInDao().get("a"))
        } finally {
            database.close()
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
