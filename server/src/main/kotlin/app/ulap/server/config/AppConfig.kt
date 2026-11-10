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
    val latestAndroidVersionCode: Int,
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
        private const val MAX_PORT = 65_535
        private val PORT_RANGE = 1..MAX_PORT
        private val POSITIVE = 1..Int.MAX_VALUE

        /** Reads the config from [env] (normally `System.getenv()`), or throws [ConfigException]. */
        fun fromEnv(env: Map<String, String>): AppConfig {
            val reader = EnvReader(env)
            val appEnv = reader.appEnv()
            val inProduction = appEnv == AppEnv.PRODUCTION
            val minAndroid = reader.int("MIN_ANDROID_VERSION_CODE", 1, POSITIVE)
            val config = AppConfig(
                // Never returned with a null APP_ENV: that's already a problem, so fromEnv throws below.
                env = appEnv ?: AppEnv.PRODUCTION,
                port = reader.int("PORT", DEFAULT_PORT, PORT_RANGE),
                database = DatabaseConfig(
                    url = reader.required("DATABASE_URL"),
                    user = reader.required("DATABASE_USER"),
                    password = Secret(reader.required("DATABASE_PASSWORD")),
                    poolSize = reader.int("DATABASE_POOL_SIZE", DEFAULT_POOL_SIZE, POSITIVE),
                ),
                jwt = JwtConfig(
                    secret = Secret(reader.required("JWT_SECRET")),
                    issuer = reader.required("JWT_ISSUER"),
                    audience = reader.required("JWT_AUDIENCE"),
                    accessTokenTtlMinutes = reader.int("ACCESS_TOKEN_TTL_MIN", DEFAULT_ACCESS_TTL_MIN, POSITIVE),
                    refreshTokenTtlDays = reader.int("REFRESH_TOKEN_TTL_DAYS", DEFAULT_REFRESH_TTL_DAYS, POSITIVE),
                ),
                googleWebClientId = reader.optional("GOOGLE_WEB_CLIENT_ID"),
                noteEncryption = NoteEncryptionConfig(
                    key = Secret(reader.required("NOTE_ENCRYPTION_KEY")),
                    keyId = reader.required("NOTE_ENCRYPTION_KEY_ID"),
                ),
                mail = MailConfig(
                    smtpHost = reader.required("SMTP_HOST"),
                    smtpPort = reader.requiredInt("SMTP_PORT", PORT_RANGE),
                    // Mailpit needs no login in development; a real mail server does (README §8 "prod").
                    smtpUser = reader.requiredIf(inProduction, "SMTP_USER"),
                    smtpPassword = reader.requiredIf(inProduction, "SMTP_PASSWORD")?.let(::Secret),
                    from = reader.required("MAIL_FROM"),
                ),
                appLinkBase = reader.required("APP_LINK_BASE"),
                minAndroidVersionCode = minAndroid,
                // GET /v1/meta tells the app about a newer build (api-spec.md §9.3). Defaults to the minimum.
                latestAndroidVersionCode = reader.int("LATEST_ANDROID_VERSION_CODE", minAndroid, POSITIVE),
                sentryDsn = reader.optional("SENTRY_DSN"),
            )
            reader.check("NOTE_ENCRYPTION_KEY", "must be base64 for exactly 32 bytes (AES-256)") {
                decodedLength(config.noteEncryption.key.value) == NOTE_KEY_BYTES
            }
            reader.check("LATEST_ANDROID_VERSION_CODE", "must not be below MIN_ANDROID_VERSION_CODE") {
                config.latestAndroidVersionCode >= config.minAndroidVersionCode
            }
            if (inProduction) {
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

    /** Required when [condition] holds, otherwise optional. Unset → null either way. */
    fun requiredIf(condition: Boolean, name: String): String? =
        if (condition) required(name).takeIf { it.isNotEmpty() } else optional(name)

    /** An optional whole number in [range], or [default] when unset. */
    fun int(name: String, default: Int, range: IntRange): Int {
        val raw = optional(name) ?: return default
        return parseInt(name, raw, range) ?: default
    }

    /** A required whole number in [range]. */
    fun requiredInt(name: String, range: IntRange): Int {
        val raw = required(name)
        if (raw.isEmpty()) return 0
        return parseInt(name, raw, range) ?: 0
    }

    private fun parseInt(name: String, raw: String, range: IntRange): Int? {
        val value = raw.toIntOrNull()
        when {
            value == null -> problems += "$name must be a whole number"
            value !in range && range.last == Int.MAX_VALUE -> problems += "$name must be at least ${range.first}"
            value !in range -> problems += "$name must be from ${range.first} to ${range.last}"
            else -> return value
        }
        return null
    }

    /**
     * Null when `APP_ENV` is missing or unknown. That's already a problem, so the caller skips the
     * production-only checks instead of reporting a pile of follow-on ones.
     */
    fun appEnv(): AppEnv? = when (val raw = required("APP_ENV")) {
        "development" -> AppEnv.DEVELOPMENT

        "production" -> AppEnv.PRODUCTION

        "" -> null

        else -> {
            problems += "APP_ENV must be development or production, not \"$raw\""
            null
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
