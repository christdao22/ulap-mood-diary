package app.ulap.server.config

import app.ulap.server.testEnv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppConfigTest {
    @Test
    fun readsACompleteEnvironmentWithDefaults() {
        val config = AppConfig.fromEnv(testEnv())

        assertEquals(AppEnv.DEVELOPMENT, config.env)
        assertEquals(8080, config.port)
        assertEquals(10, config.database.poolSize)
        assertEquals(15, config.jwt.accessTokenTtlMinutes)
        assertEquals(60, config.jwt.refreshTokenTtlDays)
        assertEquals(1, config.minAndroidVersionCode)
        assertEquals(1, config.latestAndroidVersionCode)
    }

    @Test
    fun theLatestBuildDefaultsToTheMinimum() {
        val config = AppConfig.fromEnv(testEnv() + ("MIN_ANDROID_VERSION_CODE" to "4"))

        assertEquals(4, config.latestAndroidVersionCode)
    }

    @Test
    fun theLatestBuildCanNotBeBelowTheMinimum() {
        val env = testEnv() + ("MIN_ANDROID_VERSION_CODE" to "4") + ("LATEST_ANDROID_VERSION_CODE" to "3")

        val e = assertFailsWith<ConfigException> { AppConfig.fromEnv(env) }

        assertEquals(listOf("LATEST_ANDROID_VERSION_CODE must not be below MIN_ANDROID_VERSION_CODE"), e.problems)
    }

    @Test
    fun namesEveryMissingVariableAtOnce() {
        val env = testEnv() - "DATABASE_URL" - "JWT_SECRET"

        val e = assertFailsWith<ConfigException> { AppConfig.fromEnv(env) }

        assertEquals(listOf("DATABASE_URL is required", "JWT_SECRET is required"), e.problems)
    }

    @Test
    fun rejectsANoteKeyThatIsNot32Bytes() {
        val env = testEnv() + ("NOTE_ENCRYPTION_KEY" to "dG9vLXNob3J0")

        val e = assertFailsWith<ConfigException> { AppConfig.fromEnv(env) }

        assertTrue(e.problems.single().startsWith("NOTE_ENCRYPTION_KEY"))
    }

    @Test
    fun productionNeedsALongJwtSecret() {
        val env = productionEnv() + ("JWT_SECRET" to "too-short")

        val e = assertFailsWith<ConfigException> { AppConfig.fromEnv(env) }

        assertTrue(e.problems.single().startsWith("JWT_SECRET"))
    }

    @Test
    fun productionAcceptsACompleteEnvironment() {
        val config = AppConfig.fromEnv(productionEnv())

        assertEquals(AppEnv.PRODUCTION, config.env)
        assertEquals("mailer", config.mail.smtpUser)
    }

    @Test
    fun productionNeedsAnSmtpLogin() {
        val env = productionEnv() - "SMTP_USER" - "SMTP_PASSWORD"

        val e = assertFailsWith<ConfigException> { AppConfig.fromEnv(env) }

        assertEquals(listOf("SMTP_USER is required", "SMTP_PASSWORD is required"), e.problems)
    }

    @Test
    fun developmentNeedsNoSmtpLogin() {
        val config = AppConfig.fromEnv(testEnv() + ("SMTP_USER" to "") + ("SMTP_PASSWORD" to " "))

        assertEquals(null, config.mail.smtpUser)
        assertEquals(null, config.mail.smtpPassword)
    }

    @Test
    fun rejectsNumbersOutOfRange() {
        val env = testEnv() + mapOf(
            "PORT" to "0",
            "SMTP_PORT" to "70000",
            "DATABASE_POOL_SIZE" to "0",
            "ACCESS_TOKEN_TTL_MIN" to "-5",
            "REFRESH_TOKEN_TTL_DAYS" to "ten",
        )

        val e = assertFailsWith<ConfigException> { AppConfig.fromEnv(env) }

        assertEquals(
            listOf(
                "PORT must be from 1 to 65535",
                "DATABASE_POOL_SIZE must be at least 1",
                "ACCESS_TOKEN_TTL_MIN must be at least 1",
                "REFRESH_TOKEN_TTL_DAYS must be a whole number",
                "SMTP_PORT must be from 1 to 65535",
            ),
            e.problems,
        )
    }

    @Test
    fun rejectsAnUnknownAppEnv() {
        val e = assertFailsWith<ConfigException> { AppConfig.fromEnv(testEnv() + ("APP_ENV" to "staging")) }

        assertEquals(listOf("APP_ENV must be development or production, not \"staging\""), e.problems)
    }

    /** A complete production environment. Placeholder values, not real secrets. */
    private fun productionEnv(): Map<String, String> = testEnv() + mapOf(
        "APP_ENV" to "production",
        "JWT_SECRET" to "x".repeat(64),
        "SMTP_USER" to "mailer",
        "SMTP_PASSWORD" to "test-only-smtp-password",
    )

    @Test
    fun secretsNeverAppearInToString() {
        val config = AppConfig.fromEnv(testEnv())

        assertFalse("test-only-secret" in config.toString())
        assertFalse("AAAAAAAA" in config.toString())
    }

    @Test
    fun problemsNeverQuoteSecretValues() {
        val env = testEnv() + ("NOTE_ENCRYPTION_KEY" to "my-secret-value")

        val e = assertFailsWith<ConfigException> { AppConfig.fromEnv(env) }

        assertFalse("my-secret-value" in e.message.orEmpty())
    }
}
