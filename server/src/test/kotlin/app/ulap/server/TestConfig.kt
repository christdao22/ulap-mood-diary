package app.ulap.server

import app.ulap.server.config.AppConfig

/** A complete development environment for tests. None of these values is a real secret. */
fun testEnv(
    databaseUrl: String = NO_DATABASE,
    databaseUser: String = "ulap",
    databasePassword: String = "ulap",
): Map<String, String> = mapOf(
    "APP_ENV" to "development",
    "DATABASE_URL" to databaseUrl,
    "DATABASE_USER" to databaseUser,
    "DATABASE_PASSWORD" to databasePassword,
    "JWT_SECRET" to "test-only-secret",
    "JWT_ISSUER" to "http://localhost:8080",
    "JWT_AUDIENCE" to "ulap-app",
    // 32 zero bytes, base64.
    "NOTE_ENCRYPTION_KEY" to "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "NOTE_ENCRYPTION_KEY_ID" to "k1",
    "SMTP_HOST" to "localhost",
    "SMTP_PORT" to "1026",
    "MAIL_FROM" to "Ulap <hello@ulap.test>",
    "APP_LINK_BASE" to "http://localhost:8080",
)

fun testConfig(
    databaseUrl: String = NO_DATABASE,
    databaseUser: String = "ulap",
    databasePassword: String = "ulap",
): AppConfig = AppConfig.fromEnv(testEnv(databaseUrl, databaseUser, databasePassword))

/** Port 1 never has a database, so every connection fails fast: a database that is "down". */
private const val NO_DATABASE = "jdbc:postgresql://127.0.0.1:1/unused"
