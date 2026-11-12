@file:OptIn(ExperimentalUuidApi::class)

package app.ulap.server.users

import app.ulap.server.auth.authenticated
import app.ulap.server.auth.userPrincipal
import app.ulap.shared.api.DeleteMeRequest
import app.ulap.shared.api.UpdateMeRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.route
import kotlin.uuid.ExperimentalUuidApi

/**
 * The routes under `/v1/me` (api-spec.md §7). All three need an access token, and the token alone
 * says whose account it is. They fall under the default rate limit, 120 per minute per user (§10).
 */
fun Route.userRoutes(users: UserService) {
    authenticated {
        route("/me") {
            get {
                call.respond(users.me(call.userPrincipal().userId))
            }
            patch {
                val request = call.receive<UpdateMeRequest>()
                call.respond(users.update(call.userPrincipal().userId, request))
            }
            delete {
                val request = call.receive<DeleteMeRequest>()
                users.delete(call.userPrincipal().userId, request.confirm)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
