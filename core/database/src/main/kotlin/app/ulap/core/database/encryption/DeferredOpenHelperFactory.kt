package app.ulap.core.database.encryption

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper

/**
 * Waits until the first query to build the real open helper with [open].
 *
 * Room calls [create] inside `build()`, which Hilt can run on the main thread. Unlocking the
 * passphrase reads the Android Keystore, which is slow, so it has to happen later: Room opens
 * the database on its first query, and Room never runs queries on the main thread
 * (technical-plan.md §6, "Opening the database").
 */
internal class DeferredOpenHelperFactory(
    private val open: (SupportSQLiteOpenHelper.Configuration) -> SupportSQLiteOpenHelper,
) : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
        DeferredOpenHelper(configuration, open)
}

private class DeferredOpenHelper(
    private val configuration: SupportSQLiteOpenHelper.Configuration,
    private val open: (SupportSQLiteOpenHelper.Configuration) -> SupportSQLiteOpenHelper,
) : SupportSQLiteOpenHelper {
    private val lock = Any()

    @Volatile
    private var delegate: SupportSQLiteOpenHelper? = null

    /** Room sets this during `build()`, before there is a delegate to pass it to. Guarded by [lock]. */
    private var writeAheadLogging: Boolean? = null

    override val databaseName: String?
        get() = configuration.name

    override val writableDatabase: SupportSQLiteDatabase
        get() = opened().writableDatabase

    override val readableDatabase: SupportSQLiteDatabase
        get() = opened().readableDatabase

    override fun setWriteAheadLoggingEnabled(enabled: Boolean) = synchronized(lock) {
        val current = delegate
        if (current != null) current.setWriteAheadLoggingEnabled(enabled) else writeAheadLogging = enabled
    }

    override fun close() = synchronized(lock) {
        // Never opened: nothing to close, and no reason to touch the Keystore now.
        delegate?.close() ?: Unit
    }

    private fun opened(): SupportSQLiteOpenHelper = delegate ?: synchronized(lock) {
        delegate ?: open(configuration).also { helper ->
            writeAheadLogging?.let(helper::setWriteAheadLoggingEnabled)
            delegate = helper
        }
    }
}
