@file:OptIn(ExperimentalUuidApi::class)

package app.ulap.server.auth

import app.ulap.server.config.AppConfig
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.exceptions.TokenExpiredException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Who is calling: the `sub` (user) and `did` (device) claims of a valid access token (api-spec.md §3). */
data class UserPrincipal(val userId: Uuid, val deviceId: Uuid)

/** A freshly signed access token and the moment it stops working. */
class IssuedAccessToken(val token: String, val expiresAt: Instant) {
    override fun toString(): String = "IssuedAccessToken(expiresAt=$expiresAt, token=***)"
}

/** The outcome of checking a token. Expired is separate so the app knows to refresh (api-spec.md §3). */
sealed interface AccessTokenResult {
    data class Valid(val principal: UserPrincipal) : AccessTokenResult

    data object Expired : AccessTokenResult

    data object Invalid : AccessTokenResult
}

/**
 * Access tokens (api-spec.md §3): JWTs signed with HS256 under `JWT_SECRET`, with `iss`, `aud`,
 * `sub` (user id), `did` (device id), `iat` and `exp`, valid for `ACCESS_TOKEN_TTL_MIN` minutes
 * (15 by default). Nothing is stored for them: a token is valid until it expires, and
 * `/auth/logout` only stops the refresh token, which is why they are short-lived.
 */
class AccessTokens(config: AppConfig.JwtConfig, private val clock: Clock) {
    private val algorithm = Algorithm.HMAC256(config.secret.value)
    private val issuer = config.issuer
    private val audience = config.audience
    private val ttl: Duration = Duration.ofMinutes(config.accessTokenTtlMinutes.toLong())

    // BaseVerification is the one Verification java-jwt has; building it with our clock lets tests
    // move time forward instead of waiting 15 minutes.
    private val verifier: JWTVerifier = (
        JWT.require(algorithm)
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaimPresence(CLAIM_DEVICE) as JWTVerifier.BaseVerification
        ).build(clock)

    fun issue(principal: UserPrincipal): IssuedAccessToken {
        val now = clock.instant()
        val expiresAt = now + ttl
        val token = JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(principal.userId.toString())
            .withClaim(CLAIM_DEVICE, principal.deviceId.toString())
            .withIssuedAt(now)
            .withExpiresAt(expiresAt)
            .sign(algorithm)
        return IssuedAccessToken(token, expiresAt)
    }

    fun verify(token: String): AccessTokenResult = try {
        val decoded = verifier.verify(token)
        AccessTokenResult.Valid(
            UserPrincipal(
                userId = Uuid.parse(decoded.subject),
                deviceId = Uuid.parse(decoded.getClaim(CLAIM_DEVICE).asString()),
            ),
        )
    } catch (_: TokenExpiredException) {
        AccessTokenResult.Expired
    } catch (_: JWTVerificationException) {
        AccessTokenResult.Invalid
    } catch (_: IllegalArgumentException) {
        // A subject or device claim that isn't a UUID: not one of ours.
        AccessTokenResult.Invalid
    }

    companion object {
        /** The device id claim (api-spec.md §3, Access token claims). */
        const val CLAIM_DEVICE: String = "did"
    }
}
