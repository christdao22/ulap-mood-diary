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
        val env = testEnv() + ("APP_ENV" to "production")

        val e = assertFailsWith<ConfigException> { AppConfig.fromEnv(env) }

        assertTrue(e.problems.single().startsWith("JWT_SECRET"))
    }

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
