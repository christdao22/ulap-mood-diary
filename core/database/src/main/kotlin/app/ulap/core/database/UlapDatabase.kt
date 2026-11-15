package app.ulap.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.ulap.core.database.dao.CheckInDao
import app.ulap.core.database.dao.SyncDao
import app.ulap.core.database.model.CheckInEntity
import app.ulap.core.database.model.PullSeenEntity
import app.ulap.core.database.model.SyncPositionEntity
import app.ulap.core.database.util.InstantConverter

/**
 * The phone's database, the source of truth for every screen (technical-plan.md §6).
 *
 * Every schema change raises [version] and comes with a migration, a test in MigrationTest and
 * the new schemas/ JSON committed (ulap-reference.md §5). Never fall back to a destructive
 * migration: the database can hold check-ins that haven't synced yet.
 *
 * - 1: check_ins.
 * - 2: pull_seen and sync_position, for sync (technical-plan.md §6). Only new tables, so Room
 *   writes the migration itself from the two schema files.
 */
@Database(
    entities = [CheckInEntity::class, PullSeenEntity::class, SyncPositionEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(InstantConverter::class)
abstract class UlapDatabase : RoomDatabase() {
    abstract fun checkInDao(): CheckInDao

    abstract fun syncDao(): SyncDao

    companion object {
        const val NAME: String = "ulap.db"
    }
}
