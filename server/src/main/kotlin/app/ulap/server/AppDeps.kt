package app.ulap.server

import app.ulap.server.auth.AuthComponents
import app.ulap.server.auth.AuthServices
import app.ulap.server.common.RateLimitSettings
import app.ulap.server.config.AppConfig
import app.ulap.server.crypto.NoteCipher
import app.ulap.server.db.Db
import app.ulap.server.users.UserService
import java.time.Clock
import javax.sql.DataSource

/**
 * Everything the server needs from outside, passed into [module] so tests can hand in their own
 * database, clock, limits and auth parts (ulap-reference.md §6.1). Services are built from these.
 */
class AppDeps(
    val config: AppConfig,
    val dataSource: DataSource,
    /** Every "now" on the server. Tests pass a clock they can move, e.g. past a token's expiry. */
    val clock: Clock = Clock.systemUTC(),
    val rateLimits: RateLimitSettings = RateLimitSettings(),
    val auth: AuthComponents = AuthComponents.from(config, clock),
) {
    val db: Db = Db(dataSource)
    val noteCipher: NoteCipher = NoteCipher.from(config.noteEncryption)
    val authServices: AuthServices = AuthServices(db, config, clock, rateLimits, auth)
    val userService: UserService = UserService(db)
}
