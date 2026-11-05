package app.ulap.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.ulap.core.database.dao.CheckInDao
import app.ulap.core.database.model.CheckInEntity
import app.ulap.core.database.util.InstantConverter

/**
 * The phone's database, the source of truth for every screen (technical-plan.md §6).
 *
 * Every schema change raises [version] and comes with a migration, a test in MigrationTest and
 * the new schemas/ JSON committed (ulap-reference.md §5). Never fall back to a destructive
 * migration: the database can hold check-ins that haven't synced yet.
 *
 * - 1: check_ins.
 */
@Database(entities = [CheckInEntity::class], version = 1, exportSchema = true)
@TypeConverters(InstantConverter::class)
abstract class UlapDatabase : RoomDatabase() {
    abstract fun checkInDao(): CheckInDao

    companion object {
        const val NAME: String = "ulap.db"
    }
}
