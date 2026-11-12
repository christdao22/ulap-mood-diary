package app.ulap.android.di

import app.ulap.android.BuildConfig
import app.ulap.core.domain.account.GoogleSignInConfig
import app.ulap.core.network.NetworkConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hands the build's settings to the modules that need them, so no library module reads
 * BuildConfig itself. The values come from ~/.gradle/gradle.properties (README §8).
 */
@Module
@InstallIn(SingletonComponent::class)
internal object BuildConfigModule {
    @Provides
    fun providesNetworkConfig(): NetworkConfig = NetworkConfig(
        baseUrl = BuildConfig.API_BASE_URL,
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
    )

    /** Blank without `ulap.googleWebClientId`: the Google button then stays hidden (README §6.6). */
    @Provides
    fun providesGoogleSignInConfig(): GoogleSignInConfig = GoogleSignInConfig(BuildConfig.GOOGLE_WEB_CLIENT_ID)
}
