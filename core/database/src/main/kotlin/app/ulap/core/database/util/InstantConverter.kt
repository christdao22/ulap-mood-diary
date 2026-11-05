package app.ulap.core.database.util

import androidx.room.TypeConverter
import kotlin.time.Instant

/** Stores an [Instant] as epoch milliseconds (UTC), the INTEGER columns of technical-plan.md §6. */
internal class InstantConverter {
    @TypeConverter
    fun fromEpochMillis(value: Long?): Instant? = value?.let(Instant::fromEpochMilliseconds)

    @TypeConverter
    fun toEpochMillis(value: Instant?): Long? = value?.toEpochMilliseconds()
}
