package app.ulap.server.meta

import app.ulap.server.config.AppConfig
import app.ulap.shared.api.HotlineInfo
import app.ulap.shared.api.MetaResponse
import app.ulap.shared.api.PlatformVersions
import app.ulap.shared.catalog.MoodCatalog
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * `GET /meta`: no sign-in. The minimum and latest app builds, the mood catalog version and the
 * hotline (api-spec.md §9.3). There's no iOS app yet, so its versions are left out.
 */
fun Route.metaRoutes(config: AppConfig) {
    val meta = MetaResponse(
        minAppVersion = PlatformVersions(android = config.minAndroidVersionCode),
        latestAppVersion = PlatformVersions(android = config.latestAndroidVersionCode),
        moodCatalogVersion = MoodCatalog.VERSION,
        hotline = HotlineInfo.NCMH,
    )
    get("/meta") {
        call.respond(meta)
    }
}
