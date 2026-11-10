package app.ulap.shared.api

import kotlinx.serialization.Serializable

/**
 * Body of `GET /v1/meta` (api-spec.md §9.3). The app uses it to check that it's new enough and
 * to pick up a changed hotline without a release. The app also has the hotline built in, so the
 * Hopeless flow works offline and never waits for this.
 */
@Serializable
data class MetaResponse(
    val minAppVersion: PlatformVersions,
    val latestAppVersion: PlatformVersions,
    val moodCatalogVersion: Int,
    val hotline: HotlineInfo,
)

/** Build numbers (`versionCode` on Android) per platform. Null means "no app on that platform yet". */
@Serializable
data class PlatformVersions(val android: Int? = null, val ios: Int? = null)

@Serializable
data class HotlineInfo(val name: String, val number: String) {
    companion object {
        /** The NCMH Crisis Hotline, the same number the app has built in (technical-plan.md §13). */
        val NCMH: HotlineInfo = HotlineInfo(name = "NCMH Crisis Hotline", number = "1553")
    }
}
