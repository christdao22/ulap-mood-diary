package app.ulap.server.db

import app.ulap.server.AppDeps
import app.ulap.server.module
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals

/** Flyway and `/ready` against a real PostgreSQL in Docker ([TestPostgres]). */
class DatabaseTest {
    @Test
    fun migrationsApplyAndReadyIsOk() {
        val dataSource = TestPostgres.dataSource
        assertEquals(listOf("1", "2"), appliedVersions(dataSource))
        // A second run finds nothing to do: migrations are idempotent per version.
        assertEquals(0, Database.migrate(dataSource))

        testApplication {
            application { module(AppDeps(TestPostgres.config, dataSource)) }
            val response = client.get("/v1/ready")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"status":"ready"}""", response.bodyAsText())
        }
    }

    private fun appliedVersions(dataSource: DataSource): List<String> = dataSource.connection.use { connection ->
        connection.createStatement()
            .executeQuery("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank")
            .use { rows -> generateSequence { if (rows.next()) rows.getString(1) else null }.toList() }
    }
}
