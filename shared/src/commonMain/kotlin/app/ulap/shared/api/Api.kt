package app.ulap.shared.api

import kotlinx.serialization.json.Json

/** Constants both the app and the server use for the HTTP API (api-spec.md §2). */
object Api {
    /** Every route lives under this prefix. Breaking changes go to /v2 (api-spec.md §2, Versioning). */
    const val PATH_PREFIX: String = "/v1"

    /** Request id header, also returned on errors as `error.requestId` (api-spec.md §4). */
    const val HEADER_REQUEST_ID: String = "X-Request-Id"

    /**
     * The JSON settings for every request and response. Unknown fields are ignored so that
     * adding a field is never a breaking change (api-spec.md §2), and nulls are left out.
     */
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
}
