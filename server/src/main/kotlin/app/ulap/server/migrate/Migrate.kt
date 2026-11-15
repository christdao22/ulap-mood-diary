package app.ulap.server.migrate

import app.ulap.server.config.DATABASE_VARIABLES
import app.ulap.server.config.databaseConfigFromEnv
import app.ulap.server.db.Database
import kotlin.system.exitProcess
import org.slf4j.LoggerFactory

// Flyway holds one connection for its lock and works on another, so 1 would deadlock.
private const val MIGRATE_POOL_SIZE = 3

/**
 * The one-off `migrate` step for staging and production (D4, README §7.1):
 * `docker compose run --rm migrate` runs this main class (`app.ulap.server.migrate.MigrateKt`)
 * from the server image, applies the pending Flyway migrations and exits. It needs only the
 * database variables.
 */
fun main() {
    val log = LoggerFactory.getLogger("app.ulap.server.migrate")
    val config = databaseConfigFromEnv(System.getenv(), MIGRATE_POOL_SIZE) ?: run {
        log.error("$DATABASE_VARIABLES are required")
        exitProcess(1)
    }
    Database.createDataSource(config).use { dataSource ->
        val applied = Database.migrate(dataSource)
        log.info("Applied $applied migration(s)")
    }
}
