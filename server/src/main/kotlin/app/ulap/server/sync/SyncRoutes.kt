@file:OptIn(ExperimentalUuidApi::class)

package app.ulap.server.sync

import app.ulap.server.auth.authenticated
import app.ulap.server.auth.userPrincipal
import app.ulap.server.common.ApiException
import app.ulap.server.common.RateLimits
import app.ulap.server.common.receiveLimited
import app.ulap.shared.api.PushRequest
import app.ulap.shared.api.SyncLimits
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlin.uuid.ExperimentalUuidApi

/**
 * The routes under `/v1/sync` (api-spec.md §8). All need an access token. Push and pull have their
 * own limits by user (60 and 120 per minute, §10); status falls under the default one.
 */
fun Route.syncRoutes(sync: SyncService) {
    authenticated {
        route("/sync") {
            rateLimit(RateLimits.SYNC_PUSH) {
                post("/push") {
                    val request = call.receiveLimited<PushRequest>()
                    call.respond(sync.push(call.userPrincipal().userId, request))
                }
            }
            rateLimit(RateLimits.SYNC_PULL) {
                get("/pull") {
                    val since = call.queryLong("since") ?: 0L
                    // Clamped into an Int, so a huge limit is rejected by the range check, not wrapped.
                    val limit =
                        call.queryLong("limit")?.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())?.toInt()
                            ?: SyncLimits.PULL_DEFAULT_LIMIT
                    call.respond(sync.pull(call.userPrincipal().userId, since, limit))
                }
            }
            get("/status") {
                call.respond(sync.status(call.userPrincipal().userId))
            }
        }
    }
}

/** A query parameter that must be a whole number when it's there. Not one → `400 VALIDATION_FAILED`. */
private fun ApplicationCall.queryLong(name: String): Long? {
    val value = request.queryParameters[name] ?: return null
    return value.toLongOrNull() ?: throw ApiException.invalidField(name)
}
