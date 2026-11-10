package app.ulap.server

import app.ulap.server.common.RateLimitSettings
import app.ulap.server.config.AppConfig
import app.ulap.server.db.Database
import app.ulap.shared.api.Api
import app.ulap.shared.api.ErrorBody
import app.ulap.shared.api.ErrorResponse
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication

/**
 * Runs [block] against the real [module] with a database that is "down" (see [testConfig]).
 * [extraRoutes] adds test-only routes under /v1, e.g. one that throws.
 */
fun withTestServer(
    config: AppConfig = testConfig(),
    rateLimits: RateLimitSettings = RateLimitSettings(),
    extraRoutes: Route.() -> Unit = {},
    block: suspend ApplicationTestBuilder.() -> Unit,
) {
    Database.createDataSource(config.database).use { dataSource ->
        testApplication {
            application {
                module(AppDeps(config, dataSource, rateLimits = rateLimits))
                routing { route(Api.PATH_PREFIX) { extraRoutes() } }
            }
            block()
        }
    }
}

/** The `error` object of a standard error response (api-spec.md §4). */
suspend fun HttpResponse.error(): ErrorBody = Api.json.decodeFromString<ErrorResponse>(bodyAsText()).error
