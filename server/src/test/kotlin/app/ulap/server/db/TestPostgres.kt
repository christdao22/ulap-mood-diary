package app.ulap.server.db

import app.ulap.server.config.AppConfig
import app.ulap.server.testConfig
import com.zaxxer.hikari.HikariDataSource
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * One PostgreSQL in Docker for every database test in the run (Testcontainers, README §11), so
 * the container starts once rather than once per test class. Testcontainers removes it when the
 * test JVM exits. The image tag matches infra/compose.yaml: tests run on the same version as dev
 * and the VPS.
 */
object TestPostgres {
    private val container: PostgreSQLContainer by lazy {
        PostgreSQLContainer("postgres:17.11-alpine3.24").apply { start() }
    }

    val config: AppConfig by lazy { testConfig(container.jdbcUrl, container.username, container.password) }

    /** A pool on a database with every migration applied. Shared, so tests use their own rows. */
    val dataSource: HikariDataSource by lazy {
        Database.createDataSource(config.database).also { Database.migrate(it) }
    }
}
