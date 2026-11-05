package app.ulap.core.database.di

import android.content.Context
import app.ulap.core.database.UlapDatabase
import app.ulap.core.database.dao.CheckInDao
import app.ulap.core.database.encryption.buildEncryptedDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * One database for the whole app, encrypted with SQLCipher (technical-plan.md §6). Safe to
 * inject on the main thread: the Keystore and the file are only touched on the first query.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext context: Context): UlapDatabase = buildEncryptedDatabase(context)

    @Provides
    fun providesCheckInDao(database: UlapDatabase): CheckInDao = database.checkInDao()
}
