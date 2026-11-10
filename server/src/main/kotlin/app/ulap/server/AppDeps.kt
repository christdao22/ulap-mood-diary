package app.ulap.server

import app.ulap.server.common.RateLimitSettings
import app.ulap.server.config.AppConfig
import app.ulap.server.crypto.NoteCipher
import app.ulap.server.db.Db
import javax.sql.DataSource

/**
 * Everything the server needs from outside, passed into [module] so tests can hand in their own
 * database and settings (ulap-reference.md §6.1). Services join this list as they arrive.
 */
class AppDeps(
    val config: AppConfig,
    val dataSource: DataSource,
    val db: Db = Db(dataSource),
    val noteCipher: NoteCipher = NoteCipher.from(config.noteEncryption),
    val rateLimits: RateLimitSettings = RateLimitSettings(),
)
