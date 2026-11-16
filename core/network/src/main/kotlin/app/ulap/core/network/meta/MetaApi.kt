package app.ulap.core.network.meta

import app.ulap.core.network.ApiResult
import app.ulap.core.network.apiCall
import app.ulap.shared.api.MetaResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import javax.inject.Inject

/** `GET /meta` (api-spec.md §9.3). No auth, so it uses the plain client. */
interface MetaApi {
    suspend fun meta(): ApiResult<MetaResponse>
}

internal class KtorMetaApi @Inject constructor(private val client: HttpClient) : MetaApi {
    override suspend fun meta(): ApiResult<MetaResponse> = client.apiCall { get("meta") }
}
