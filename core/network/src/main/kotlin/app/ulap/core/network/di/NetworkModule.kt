package app.ulap.core.network.di

import app.ulap.core.domain.settings.SettingsRepository
import app.ulap.core.network.AccessTokenSource
import app.ulap.core.network.NetworkConfig
import app.ulap.core.network.auth.AuthApi
import app.ulap.core.network.auth.KtorAuthApi
import app.ulap.core.network.auth.KtorSignedInAuthApi
import app.ulap.core.network.auth.SignedInAuthApi
import app.ulap.core.network.ulapHttpClient
import app.ulap.core.network.withBearerAuth
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** The client that sends the signed-in user's access token. The plain [HttpClient] doesn't. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthorizedClient

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {
    /** One client, and so one connection pool, for the whole app. */
    @Provides
    @Singleton
    fun providesHttpClient(config: NetworkConfig, settings: SettingsRepository): HttpClient = ulapHttpClient(
        engine = OkHttp.create(),
        config = config,
        deviceId = settings::deviceId,
        language = { settings.settings.first().language?.tag },
    )

    /** Shares the plain client's engine and plugins. */
    @Provides
    @Singleton
    @AuthorizedClient
    fun providesAuthorizedHttpClient(client: HttpClient, tokens: AccessTokenSource): HttpClient =
        client.withBearerAuth(tokens)
}

@Module
@InstallIn(SingletonComponent::class)
internal interface NetworkBindsModule {
    @Binds
    fun bindsAuthApi(api: KtorAuthApi): AuthApi

    @Binds
    fun bindsSignedInAuthApi(api: KtorSignedInAuthApi): SignedInAuthApi
}
