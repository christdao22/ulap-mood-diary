package app.ulap.core.network

import app.ulap.shared.api.Api
import app.ulap.shared.api.AppVersion
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json

private const val REQUEST_TIMEOUT_MS = 30_000L
private const val CONNECT_TIMEOUT_MS = 15_000L

/**
 * The one HTTP client every API call goes through, without a signed-in user. [withBearerAuth]
 * adds the access token on top of it.
 *
 * There is deliberately no Logging plugin: request and response bodies carry journal notes,
 * passwords and tokens, and none of those may reach a log (CLAUDE.md rules, api-spec.md §11).
 *
 * [deviceId] and [language] are read on every request, so a language change applies to the next
 * email the server sends.
 */
internal fun ulapHttpClient(
    engine: HttpClientEngine,
    config: NetworkConfig,
    deviceId: suspend () -> String,
    language: suspend () -> String?,
): HttpClient = HttpClient(engine) {
    // Error statuses come back as responses, so apiCall can read the error body.
    expectSuccess = false
    install(ContentNegotiation) { json(Api.json) }
    install(HttpTimeout) {
        requestTimeoutMillis = REQUEST_TIMEOUT_MS
        connectTimeoutMillis = CONNECT_TIMEOUT_MS
    }
    defaultRequest {
        // The trailing slash makes a relative path like "auth/refresh" land under /v1.
        url(config.baseUrl.trimEnd('/') + "/")
    }
    install(
        appHeaders(
            appVersion = AppVersion(AppVersion.ANDROID, config.versionName, config.versionCode),
            deviceId = deviceId,
            language = language,
        ),
    )
}

/**
 * The headers every route expects (api-spec.md §2, Required headers). The refresh call needs
 * `X-Device-Id` too: the server only accepts a refresh token from the device it was issued to.
 */
private fun appHeaders(appVersion: AppVersion, deviceId: suspend () -> String, language: suspend () -> String?) =
    createClientPlugin("UlapAppHeaders") {
        val versionHeader = appVersion.toHeader()
        onRequest { request, _ ->
            request.headers[Api.HEADER_DEVICE_ID] = deviceId()
            request.headers[Api.HEADER_APP_VERSION] = versionHeader
            language()?.let { request.headers[HttpHeaders.AcceptLanguage] = it }
        }
    }
