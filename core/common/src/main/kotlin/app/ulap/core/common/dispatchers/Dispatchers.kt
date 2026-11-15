package app.ulap.core.common.dispatchers

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * The dispatcher for CPU work that would make the screen stutter on the main thread, such as
 * searching and grouping a long Logs history. Inject it rather than using `Dispatchers.Default`
 * directly, so a test can pass its own and keep virtual time.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DefaultDispatcher

/** The dispatcher for blocking disk work that has no suspend API, such as Room's `clearAllTables()`. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
internal object DispatchersModule {
    @Provides
    @DefaultDispatcher
    fun providesDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @IoDispatcher
    fun providesIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
