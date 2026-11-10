package app.ulap.server.common

import app.ulap.shared.api.Api
import app.ulap.shared.api.AppVersion
import app.ulap.shared.api.ErrorCode
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.path

/**
 * `426 APP_UPDATE_REQUIRED` when `X-App-Version` names an Android build below
 * `MIN_ANDROID_VERSION_CODE` (api-spec.md §2, Minimum app version). The app then asks the person
 * to update, and local use keeps working.
 *
 * Health, readiness and meta are never blocked: Docker and the deploy runbook call them without
 * the header, and an old app needs `/meta` to learn it's old. A missing or unreadable header also
 * passes. This is a compatibility check, not a security one, and skipping the header gains nothing.
 */
val AppVersionGate = createApplicationPlugin("AppVersionGate", ::AppVersionGateConfig) {
    val minAndroid = pluginConfig.minAndroidVersionCode
    val open = setOf("/health", "/ready", "/meta").map { Api.PATH_PREFIX + it }.toSet()

    onCall { call ->
        if (call.request.path() in open) return@onCall
        val version = AppVersion.parse(call.request.headers[Api.HEADER_APP_VERSION]) ?: return@onCall
        if (version.platform == AppVersion.ANDROID && version.code < minAndroid) {
            throw ApiException(ErrorCode.APP_UPDATE_REQUIRED, "Android build ${version.code} is below $minAndroid")
        }
    }
}

/** Settings for [AppVersionGate]: the lowest Android build that may still call the API. */
class AppVersionGateConfig {
    var minAndroidVersionCode: Int = 1
}
