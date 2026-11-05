package app.ulap.server.config

import java.util.Base64

/** `APP_ENV` (README §8). Development migrates on startup; production never does (D4). */
enum class AppEnv { DEVELOPMENT, PRODUCTION }

/** A value that must never reach a log. `toString()` hides it. */
@JvmInline
value class Secret(val value: String) {
    override fun toString(): String = "Secret(***)"
}

/**
 * Every server setting, read from environment variables once at startup (README §8,
 * ulap-reference.md §6.4). A missing or malformed required value stops the server with a list of
 * the variable names that are wrong. The values themselves are never printed.
 */
data class AppConfig(
    val env: AppEnv,
    val port: Int,
    val database: DatabaseConfig,
    val jwt: JwtConfig,
    val googleWebClientId: String?,
    val noteEncryption: NoteEncryptionConfig,
    val mail: MailConfig,
    val appLinkBase: String,
    val minAndroidVersionCode: Int,
    val sentryDsn: String?,
) {
    data class DatabaseConfig(val url: String, val user: String, val password: Secret, val poolSize: Int)

    data class JwtConfig(
        val secret: Secret,
        val issuer: String,
        val audience: String,
        val accessTokenTtlMinutes: Int,
        val refreshTokenTtlDays: Int,
    )

    data class NoteEncryptionConfig(val key: Secret, val keyId: String)

    data class MailConfig(
        val smtpHost: String,
        val smtpPort: Int,
        val smtpUser: String?,
        val smtpPassword: Secret?,
        val from: String,
    )

    companion object {
        private const val DEFAULT_PORT = 8080
        private const val DEFAULT_POOL_SIZE = 10
        private const val DEFAULT_ACCESS_TTL_MIN = 15
        private const val DEFAULT_REFRESH_TTL_DAYS = 60
        private const val NOTE_KEY_BYTES = 32
        private const val MIN_JWT_SECRET_BYTES_IN_PRODUCTION = 64

        /** Reads the config from [env] (normally `System.getenv()`), or throws [ConfigException]. */
        fun fromEnv(env: Map<String, String>): AppConfig {
            val reader = EnvReader(env)
            val appEnv = reader.appEnv()
            val config = AppConfig(
                env = appEnv,
                port = reader.int("PORT", DEFAULT_PORT),
                database = DatabaseConfig(
                    url = reader.required("DATABASE_URL"),
                    user = reader.required("DATABASE_USER"),
                    password = Secret(reader.required("DATABASE_PASSWORD")),
                    poolSize = reader.int("DATABASE_POOL_SIZE", DEFAULT_POOL_SIZE),
                ),
                jwt = JwtConfig(
                    secret = Secret(reader.required("JWT_SECRET")),
                    issuer = reader.required("JWT_ISSUER"),
                    audience = reader.required("JWT_AUDIENCE"),
                    accessTokenTtlMinutes = reader.int("ACCESS_TOKEN_TTL_MIN", DEFAULT_ACCESS_TTL_MIN),
                    refreshTokenTtlDays = reader.int("REFRESH_TOKEN_TTL_DAYS", DEFAULT_REFRESH_TTL_DAYS),
                ),
                googleWebClientId = reader.optional("GOOGLE_WEB_CLIENT_ID"),
                noteEncryption = NoteEncryptionConfig(
                    key = Secret(reader.required("NOTE_ENCRYPTION_KEY")),
                    keyId = reader.required("NOTE_ENCRYPTION_KEY_ID"),
                ),
                mail = MailConfig(
                    smtpHost = reader.required("SMTP_HOST"),
                    smtpPort = reader.requiredInt("SMTP_PORT"),
                    smtpUser = reader.optional("SMTP_USER"),
                    smtpPassword = reader.optional("SMTP_PASSWORD")?.let(::Secret),
                    from = reader.required("MAIL_FROM"),
                ),
                appLinkBase = reader.required("APP_LINK_BASE"),
                minAndroidVersionCode = reader.int("MIN_ANDROID_VERSION_CODE", 1),
                sentryDsn = reader.optional("SENTRY_DSN"),
            )
            reader.check("NOTE_ENCRYPTION_KEY", "must be base64 for exactly 32 bytes (AES-256)") {
                decodedLength(config.noteEncryption.key.value) == NOTE_KEY_BYTES
            }
            if (appEnv == AppEnv.PRODUCTION) {
                reader.check("JWT_SECRET", "must be at least 64 bytes in production (README §7.1)") {
                    config.jwt.secret.value.toByteArray().size >= MIN_JWT_SECRET_BYTES_IN_PRODUCTION
                }
            }
            reader.throwIfProblems()
            return config
        }

        private fun decodedLength(base64: String): Int? = runCatching {
            Base64.getDecoder().decode(base64).size
        }.getOrNull()
    }
}

/** Thrown when the environment is incomplete. The message names variables, never their values. */
class ConfigException(val problems: List<String>) :
    IllegalStateException("Server configuration is incomplete:\n" + problems.joinToString("\n") { "  - $it" })

/** Collects every problem first, so one start-up names all of them instead of one at a time. */
private class EnvReader(private val env: Map<String, String>) {
    private val problems = mutableListOf<String>()

    fun optional(name: String): String? = env[name]?.trim()?.takeIf { it.isNotEmpty() }

    fun required(name: String): String = optional(name) ?: run {
        problems += "$name is required"
        ""
    }

    fun int(name: String, default: Int): Int {
        val raw = optional(name) ?: return default
        return raw.toIntOrNull() ?: run {
            problems += "$name must be a whole number"
            default
        }
    }

    fun requiredInt(name: String): Int {
        val raw = required(name)
        if (raw.isEmpty()) return 0
        return raw.toIntOrNull() ?: run {
            problems += "$name must be a whole number"
            0
        }
    }

    fun appEnv(): AppEnv = when (val raw = required("APP_ENV")) {
        "development" -> AppEnv.DEVELOPMENT

        "production" -> AppEnv.PRODUCTION

        "" -> AppEnv.PRODUCTION

        else -> {
            problems += "APP_ENV must be development or production, not \"$raw\""
            AppEnv.PRODUCTION
        }
    }

    /** Adds [problem] for [name] unless it's already missing or [isValid] holds. */
    fun check(name: String, problem: String, isValid: () -> Boolean) {
        if (optional(name) != null && !isValid()) problems += "$name $problem"
    }

    fun throwIfProblems() {
        if (problems.isNotEmpty()) throw ConfigException(problems.toList())
    }
}
