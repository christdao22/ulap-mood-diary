package app.ulap.server.config

/** The variables the one-off mains (`migrate`, `scheduler`) need: only the database ones. */
const val DATABASE_VARIABLES: String = "DATABASE_URL, DATABASE_USER and DATABASE_PASSWORD"

/**
 * The database settings from [env], or null when one of [DATABASE_VARIABLES] is missing. The full
 * [AppConfig] would also ask for JWT, mail and note keys, which these mains never use.
 */
fun databaseConfigFromEnv(env: Map<String, String>, poolSize: Int): AppConfig.DatabaseConfig? {
    val url = env["DATABASE_URL"]
    val user = env["DATABASE_USER"]
    val password = env["DATABASE_PASSWORD"]
    if (url.isNullOrBlank() || user.isNullOrBlank() || password.isNullOrBlank()) return null
    return AppConfig.DatabaseConfig(url = url, user = user, password = Secret(password), poolSize = poolSize)
}
