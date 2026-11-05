package app.ulap.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioStorage
import app.ulap.core.datastore.DataStoreSettingsRepository
import app.ulap.core.datastore.SettingsFile
import app.ulap.core.datastore.SettingsSerializer
import app.ulap.core.domain.settings.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import okio.FileSystem
import okio.Path.Companion.toOkioPath

@Module
@InstallIn(SingletonComponent::class)
internal object DataStoreModule {
    /**
     * One DataStore per file for the whole app: two would overwrite each other's writes. The file
     * is in filesDir, which backup excludes like everything else (README §14), so a restored
     * phone never gets this phone's device ID.
     */
    @Provides
    @Singleton
    fun providesSettingsDataStore(@ApplicationContext context: Context): DataStore<SettingsFile> =
        DataStoreFactory.create(
            storage = OkioStorage(
                fileSystem = FileSystem.SYSTEM,
                serializer = SettingsSerializer,
                producePath = { File(context.filesDir, "datastore/settings.json").toOkioPath() },
            ),
            corruptionHandler = ReplaceFileCorruptionHandler { SettingsFile() },
        )
}

@Module
@InstallIn(SingletonComponent::class)
internal interface DataStoreBindsModule {
    @Binds
    fun bindsSettingsRepository(repository: DataStoreSettingsRepository): SettingsRepository
}
