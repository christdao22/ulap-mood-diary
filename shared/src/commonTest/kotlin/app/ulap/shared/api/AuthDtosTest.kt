package app.ulap.shared.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Instant

class AuthDtosTest {
    private val user = UserDto(
        id = "3f0c5b1e-6c1a-4e4e-9d6b-2c1f0f6b8a10",
        displayName = "Christian",
        language = "en",
        mascotEnabled = true,
        identities = listOf(IdentityDto("GOOGLE", "christian@example.com", emailVerified = true)),
        createdAt = Instant.parse("2026-10-01T02:00:00Z"),
    )

    @Test
    fun userMatchesTheApiSpecShape() {
        // api-spec.md §5.1, with timestamps written to the millisecond (§2, Format).
        assertEquals(
            """{"id":"3f0c5b1e-6c1a-4e4e-9d6b-2c1f0f6b8a10","displayName":"Christian","language":"en",""" +
                """"mascotEnabled":true,"identities":[{"provider":"GOOGLE","email":"christian@example.com",""" +
                """"emailVerified":true}],"createdAt":"2026-10-01T02:00:00.000Z"}""",
            Api.json.encodeToString(user),
        )
    }

    @Test
    fun timestampsReadBackWithOrWithoutAFraction() {
        val expected = Instant.parse("2026-11-04T10:30:00Z")
        listOf("2026-11-04T10:30:00Z", "2026-11-04T10:30:00.000Z", "2026-11-04T18:30:00+08:00").forEach { text ->
            val decoded = Api.json.decodeFromString<UserDto>(
                """{"id":"x","language":"en","mascotEnabled":true,"identities":[],"createdAt":"$text"}""",
            )
            assertEquals(expected, decoded.createdAt, text)
        }
    }

    @Test
    fun secretsNeverAppearInToString() {
        val response = AuthResponse(
            accessToken = "access-secret",
            accessTokenExpiresAt = Instant.parse("2026-11-04T10:30:00Z"),
            refreshToken = "refresh-secret",
            refreshTokenExpiresAt = Instant.parse("2027-01-03T10:15:00Z"),
            user = user,
            isNewUser = true,
        )
        val printed = listOf(
            response.toString(),
            EmailRegisterRequest("ana@example.com", "pass-secret").toString(),
            EmailLoginRequest("ana@example.com", "pass-secret").toString(),
            ResetPasswordRequest("token-secret", "pass-secret").toString(),
            RefreshRequest("refresh-secret").toString(),
            VerifyEmailRequest("token-secret").toString(),
            GoogleSignInRequest("id-secret").toString(),
            LogoutRequest("refresh-secret").toString(),
        ).joinToString()

        listOf("access-secret", "refresh-secret", "pass-secret", "token-secret", "id-secret").forEach {
            assertFalse(printed.contains(it), "found $it in $printed")
        }
    }
}
