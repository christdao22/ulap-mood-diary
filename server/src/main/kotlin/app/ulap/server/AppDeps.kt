package app.ulap.server

import app.ulap.server.config.AppConfig
import javax.sql.DataSource

/**
 * Everything the server needs from outside, passed into [module] so tests can hand in their own
 * database and settings (ulap-reference.md §6.1). Services join this list as they arrive.
 */
class AppDeps(val config: AppConfig, val dataSource: DataSource)
