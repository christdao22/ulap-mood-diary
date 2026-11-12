package app.ulap.core.network

/**
 * What the network layer needs from the build: where the API is and which app version is calling.
 * androidApp provides it from BuildConfig, so this module never reads build settings itself.
 */
data class NetworkConfig(
    /** Ends in `/v1`, e.g. `http://10.0.2.2:8080/v1` for the emulator (api-spec.md §2, Base URL). */
    val baseUrl: String,
    val versionName: String,
    val versionCode: Int,
)
