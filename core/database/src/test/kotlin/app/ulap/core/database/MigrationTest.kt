package app.ulap.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
     * The newest version built from its schemas/ JSON opens with the app's code. Room checks the
     * schema's identity hash on open, so this fails if the entities changed without a new version.
     */
    @Test
    fun `the committed newest schema matches the app's code`() = runTest {
        helper.createDatabase(2).close()

        val database = Room.databaseBuilder(context, UlapDatabase::class.java, DB).build()
        try {
            database.checkInDao().insert(checkIn("a"))
            assertEquals(checkIn("a"), database.checkInDao().get("a"))
        } finally {
            database.close()
        }
    }

    /**
     * 1 → 2 adds pull_seen and sync_position for sync and keeps every check-in. With no
     * sync_position row yet, sync starts from nothing (pull from 0), as it should.
     */
    @Test
    fun `version 2 keeps the check-ins and starts sync from nothing`() = runTest {
        helper.createDatabase(1).use { connection ->
            connection.execSQL(
                """
                INSERT INTO check_ins (id, mood_code, energy, stress, stress_after, trigger, trigger_other,
                    note, support_used, is_good_moment, logged_at, tz_offset_min, created_at, updated_at,
                    deleted_at, sync_state, sync_error, sync_attempts, server_seq)
                VALUES ('a', 'worried', 6, 8, NULL, 'WORK', NULL, 'Report due tomorrow.', 'NONE', 0,
                    ${T0.toEpochMilliseconds()}, $MANILA, ${T0.toEpochMilliseconds()},
                    ${T0.toEpochMilliseconds()}, NULL, 'PENDING', NULL, 0, NULL)
                """,
            )
        }
        helper.runMigrationsAndValidate(2).close()

        val database = Room.databaseBuilder(context, UlapDatabase::class.java, DB).build()
        try {
            assertEquals(checkIn("a"), database.checkInDao().get("a"))
            assertNull(database.syncDao().position())
            assertEquals(1, database.syncDao().uploadBatch(limit = 10).size)
        } finally {
            database.close()
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
