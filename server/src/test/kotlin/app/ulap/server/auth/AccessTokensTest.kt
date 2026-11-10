@file:OptIn(ExperimentalUuidApi::class)

package app.ulap.server.auth

import app.ulap.server.config.Secret
import app.ulap.server.testConfig
import app.ulap.shared.model.Ids
import com.auth0.jwt.JWT
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Access tokens: the claims of api-spec.md §3, a 15-minute life and nothing else accepted. */
class AccessTokensTest {
    private val clock = MutableClock()
    private val jwt = testConfig().jwt
    private val tokens = AccessTokens(jwt, clock)
    private val principal = UserPrincipal(Uuid.parse(Ids.newId()), DEVICE_A)

    @Test
    fun aTokenCarriesTheUserAndDeviceAndLastsFifteenMinutes() {
        val issued = tokens.issue(principal)

        assertEquals(clock.instant() + Duration.ofMinutes(15), issued.expiresAt)
        val decoded = JWT.decode(issued.token)
        assertEquals(jwt.issuer, decoded.issuer)
        assertEquals(listOf(jwt.audience), decoded.audience)
        assertEquals(principal.userId.toString(), decoded.subject)
        assertEquals(principal.deviceId.toString(), decoded.getClaim("did").asString())
        assertEquals(AccessTokenResult.Valid(principal), tokens.verify(issued.token))
    }

    @Test
    fun anExpiredTokenIsReportedAsExpiredNotInvalid() {
        val issued = tokens.issue(principal)
        clock.advance(Duration.ofMinutes(14))
        assertIs<AccessTokenResult.Valid>(tokens.verify(issued.token))

        clock.advance(Duration.ofMinutes(2))

        assertEquals(AccessTokenResult.Expired, tokens.verify(issued.token))
    }

    @Test
    fun tokensFromAnotherSecretIssuerOrAudienceAreInvalid() {
        val otherSecret = AccessTokens(jwt.copy(secret = Secret("someone-else")), clock)
        val otherAudience = AccessTokens(jwt.copy(audience = "other-app"), clock)
        val otherIssuer = AccessTokens(jwt.copy(issuer = "https://evil.example"), clock)

        listOf(otherSecret, otherAudience, otherIssuer).forEach { issuer ->
            assertEquals(AccessTokenResult.Invalid, tokens.verify(issuer.issue(principal).token))
        }
        assertEquals(AccessTokenResult.Invalid, tokens.verify("not-a-jwt"))
        assertEquals(AccessTokenResult.Invalid, tokens.verify(""))
    }
}
