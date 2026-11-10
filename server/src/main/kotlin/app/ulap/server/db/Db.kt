package app.ulap.server.db

import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database as ExposedDatabase
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Exposed on top of the shared Hikari pool, plus the one way services open a transaction
 * (ulap-reference.md §6.1).
 *
 * A service wraps a whole operation in [tx], and the DAL functions it calls inside run in that same
 * transaction: Exposed keeps the open transaction for the current thread, so a DAL function never
 * starts its own. Either everything in the block commits, or an exception rolls all of it back.
 * That's what `DELETE /me` and a sync push batch rely on (api-spec.md §7.3, §12).
 */
class Db(dataSource: DataSource) {
    val database: ExposedDatabase = ExposedDatabase.connect(dataSource)

    /**
     * Runs [block] in one transaction on the IO dispatcher, so a slow query never blocks a Ktor
     * request thread. The block is deliberately not `suspend`: nothing should wait on the network
     * or a timer while it holds a database connection and row locks.
     */
    suspend fun <T> tx(block: JdbcTransaction.() -> T): T = withContext(Dispatchers.IO) {
        transaction(database) { block() }
    }
}

/** True when PostgreSQL refused a row because a UNIQUE constraint already holds that value (SQLSTATE 23505). */
fun ExposedSQLException.isUniqueViolation(): Boolean = sqlState == "23505"
