package app.ulap.server.db

import app.ulap.server.AppDeps
import app.ulap.server.module
import app.ulap.server.testConfig
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.AfterClass
import org.junit.BeforeClass
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * Flyway and `/ready` against a real PostgreSQL in Docker (Testcontainers, README §11). The image
 * tag matches infra/compose.yaml, so tests run on the same database version as dev and the VPS.
 */
class DatabaseTest {
    @Test
    fun migrationsApplyAndReadyIsOk() {
        val config = testConfig(postgres.jdbcUrl, postgres.username, postgres.password)
        Database.createDataSource(config.database).use { dataSource ->
            Database.migrate(dataSource)
            assertEquals(listOf("1"), appliedVersions(dataSource))
            // A second run finds nothing to do: migrations are idempotent per version.
            assertEquals(0, Database.migrate(dataSource))

            testApplication {
                application { module(AppDeps(config, dataSource)) }
                val response = client.get("/v1/ready")
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("""{"status":"ready"}""", response.bodyAsText())
            }
        }
    }

    private fun appliedVersions(dataSource: DataSource): List<String> = dataSource.connection.use { connection ->
        connection.createStatement()
            .executeQuery("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank")
            .use { rows -> generateSequence { if (rows.next()) rows.getString(1) else null }.toList() }
    }

    companion object {
        private val postgres = PostgreSQLContainer("postgres:17.11-alpine3.24")

        @JvmStatic
        @BeforeClass
        fun startPostgres() {
            postgres.start()
        }

        @JvmStatic
        @AfterClass
        fun stopPostgres() {
            postgres.stop()
        }
    }
}
