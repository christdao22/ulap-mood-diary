package app.ulap.server

import app.ulap.server.common.AppVersionGate
import app.ulap.server.common.RateLimits
import app.ulap.server.common.RequestIds
import app.ulap.server.common.installErrorHandling
import app.ulap.server.common.installRateLimits
import app.ulap.server.config.AppConfig
import app.ulap.server.config.AppEnv
import app.ulap.server.config.ConfigException
import app.ulap.server.db.Database
import app.ulap.server.health.healthRoutes
import app.ulap.server.meta.metaRoutes
import app.ulap.shared.api.Api
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlin.system.exitProcess
import org.slf4j.LoggerFactory

/** Starts the server. Configuration comes only from environment variables (README §8). */
fun main() {
    val log = LoggerFactory.getLogger("app.ulap.server")
    val config = try {
        AppConfig.fromEnv(System.getenv())
    } catch (e: ConfigException) {
        log.error(e.message)
        exitProcess(1)
    }
    val dataSource = Database.createDataSource(config.database)
    if (config.env == AppEnv.DEVELOPMENT) {
        val applied = Database.migrate(dataSource)
        log.info("Development: applied $applied migration(s) on startup")
    }
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        monitor.subscribe(ApplicationStopped) { dataSource.close() }
        module(AppDeps(config, dataSource))
    }.start(wait = true)
}

/**
 * The whole HTTP API. Plugins are installed in the order of ulap-reference.md §6.2: request id,
 * compression, the app-version gate, rate limits, then (with the auth tasks) authentication.
 */
fun Application.module(deps: AppDeps) {
    install(CallId) {
        retrieveFromHeader(Api.HEADER_REQUEST_ID)
        generate { RequestIds.next() }
        verify { RequestIds.isAcceptable(it) }
        replyToHeader(Api.HEADER_REQUEST_ID)
    }
    // Method, path and status only. Request and response bodies are never logged (README §7.1).
    install(CallLogging) {
        callIdMdc("requestId")
    }
    install(Compression)
    install(ContentNegotiation) {
        json(Api.json)
    }
    installErrorHandling()
    install(AppVersionGate) {
        minAndroidVersionCode = deps.config.minAndroidVersionCode
    }
    installRateLimits(deps.rateLimits)

    routing {
        rateLimit(RateLimits.DEFAULT) {
            route(Api.PATH_PREFIX) {
                healthRoutes(deps.dataSource)
                metaRoutes(deps.config)
            }
        }
    }
}
