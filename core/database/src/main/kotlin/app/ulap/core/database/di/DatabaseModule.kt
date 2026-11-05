package app.ulap.core.database.di

import android.content.Context
import androidx.room.Room
import app.ulap.core.database.UlapDatabase
import app.ulap.core.database.dao.CheckInDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * One database for the whole app. Not encrypted yet: the SQLCipher task (PHASES.md Phase 2,
 * 🔒) adds the passphrase here before any release. Room opens the file on the first query,
 * off the main thread.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext context: Context): UlapDatabase =
        Room.databaseBuilder(context, UlapDatabase::class.java, UlapDatabase.NAME).build()

    @Provides
    fun providesCheckInDao(database: UlapDatabase): CheckInDao = database.checkInDao()
}
