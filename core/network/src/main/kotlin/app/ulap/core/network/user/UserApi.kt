package app.ulap.core.network.user

import app.ulap.core.network.ApiResult
import app.ulap.core.network.apiCall
import app.ulap.core.network.di.AuthorizedClient
import app.ulap.shared.api.DeleteMeRequest
import app.ulap.shared.validation.AccountRules
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject

/** The 🔒 routes under `/me` (api-spec.md §7). They need the signed-in user's access token. */
interface UserApi {
    /**
     * `DELETE /me` (api-spec.md §7.3): deletes the account and every check-in on the server, for
     * good. The confirmation the server asks for is always sent; the app asks the person first.
     */
    suspend fun deleteMe(): ApiResult<Unit>
}

internal class KtorUserApi @Inject constructor(@param:AuthorizedClient private val client: HttpClient) : UserApi {
    override suspend fun deleteMe(): ApiResult<Unit> = client.apiCall {
        delete("me") {
            contentType(ContentType.Application.Json)
            setBody(DeleteMeRequest(AccountRules.DELETE_CONFIRMATION))
        }
    }
}
