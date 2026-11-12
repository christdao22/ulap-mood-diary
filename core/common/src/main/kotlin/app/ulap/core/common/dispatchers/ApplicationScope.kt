package app.ulap.core.common.dispatchers

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * A scope that lives as long as the app, for work that must finish even when the screen or
 * request that started it goes away, such as saving a rotated refresh token. Inject it instead
 * of using `GlobalScope` (ulap-reference.md §5, Kotlin style).
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
internal object ApplicationScopeModule {
    /** SupervisorJob: one failed job doesn't cancel the others. */
    @Provides
    @Singleton
    @ApplicationScope
    fun providesApplicationScope(@DefaultDispatcher dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)
}
