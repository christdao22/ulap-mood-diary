package app.ulap.server.migrate

import app.ulap.server.config.AppConfig
import app.ulap.server.config.Secret
import app.ulap.server.db.Database
import kotlin.system.exitProcess
import org.slf4j.LoggerFactory

private const val MIGRATE_POOL_SIZE = 3
private const val DATABASE_VARIABLES = "DATABASE_URL, DATABASE_USER and DATABASE_PASSWORD"

/**
 * The one-off `migrate` step for staging and production (D4, README §7.1):
 * `docker compose run --rm migrate` runs this main class (`app.ulap.server.migrate.MigrateKt`)
 * from the server image, applies the pending Flyway migrations and exits. It needs only the
 * database variables.
 */
fun main() {
    val log = LoggerFactory.getLogger("app.ulap.server.migrate")
    val env = System.getenv()
    val url = env["DATABASE_URL"]
    val user = env["DATABASE_USER"]
    val password = env["DATABASE_PASSWORD"]
    if (url.isNullOrBlank() || user.isNullOrBlank() || password.isNullOrBlank()) {
        log.error("$DATABASE_VARIABLES are required")
        exitProcess(1)
    }
    val config = AppConfig.DatabaseConfig(
        url = url,
        user = user,
        password = Secret(password),
        // Flyway holds one connection for its lock and works on another, so 1 would deadlock.
        poolSize = MIGRATE_POOL_SIZE,
    )
    Database.createDataSource(config).use { dataSource ->
        val applied = Database.migrate(dataSource)
        log.info("Applied $applied migration(s)")
    }
}
