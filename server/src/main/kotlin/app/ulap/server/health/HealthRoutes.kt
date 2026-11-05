package app.ulap.server.health

import app.ulap.server.common.ApiException
import app.ulap.server.db.Database
import app.ulap.shared.api.StatusResponse
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * `GET /health`: liveness. It never touches the database, so a database outage doesn't get the
 * container restarted (api-spec.md §9.1).
 * `GET /ready`: readiness. `SELECT 1` through the pool within 2 seconds, else 503 UNAVAILABLE
 * (api-spec.md §9.2, D5). The deploy runbook checks it before sending traffic.
 */
fun Route.healthRoutes(dataSource: DataSource) {
    get("/health") {
        call.respond(StatusResponse.OK)
    }
    get("/ready") {
        val ready = withContext(Dispatchers.IO) { Database.isReady(dataSource) }
        if (!ready) throw ApiException.unavailable("Database is not answering")
        call.respond(StatusResponse.READY)
    }
}
