package app.ulap.server.db

import app.ulap.server.config.AppConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource
import org.flywaydb.core.Flyway

/** The PostgreSQL connection pool and the Flyway migrations (README §3, §6.4). */
object Database {
    /** `/ready` must answer within 2 seconds (api-spec.md §9.2), so waiting for a connection does too. */
    private const val READY_TIMEOUT_MS = 2_000L
    private const val READY_QUERY_TIMEOUT_SECONDS = 2

    fun createDataSource(config: AppConfig.DatabaseConfig): HikariDataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = config.url
            username = config.user
            password = config.password.value
            maximumPoolSize = config.poolSize
            connectionTimeout = READY_TIMEOUT_MS
            // Start even when the database is down: /health stays up and /ready reports 503.
            initializationFailTimeout = -1
            poolName = "ulap"
        },
    )

    /**
     * Applies every pending migration in `resources/db/migration`. Runs on startup only in
     * development; staging and production run it as the separate `migrate` step (D4, README §7.1).
     * Migrations only go forward: never edit one that has been applied.
     */
    fun migrate(dataSource: DataSource): Int = Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .load()
        .migrate()
        .migrationsExecuted

    /** `SELECT 1` through the pool. False when the database doesn't answer in time. */
    fun isReady(dataSource: DataSource): Boolean = try {
        dataSource.connection.use(::selectOne)
    } catch (_: SQLException) {
        false
    }

    private fun selectOne(connection: Connection): Boolean = connection.createStatement().use { statement ->
        statement.queryTimeout = READY_QUERY_TIMEOUT_SECONDS
        statement.executeQuery("SELECT 1").use { it.next() }
    }
}
