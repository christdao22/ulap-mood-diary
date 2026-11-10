package app.ulap.server.auth

import app.ulap.server.common.RateLimitRule
import app.ulap.server.common.RateLimitSettings
import app.ulap.server.common.RateLimits
import app.ulap.server.error
import app.ulap.shared.api.ErrorCode
import app.ulap.shared.api.GoogleSignInRequest
import app.ulap.shared.api.LogoutRequest
import app.ulap.shared.api.RefreshRequest
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import org.slf4j.LoggerFactory

/** Refresh, logout, the access-token check and the auth rate limits (api-spec.md §3, §6.9–§6.11, §10). */
class SessionRoutesTest {
    @Test
    fun refreshRotatesAndAReusedTokenSignsTheDeviceOut() = withAuthServer {
        val session = registered()

        val refreshed = post("/v1/auth/refresh", RefreshRequest(session.refreshToken))
        assertEquals(HttpStatusCode.OK, refreshed.status, refreshed.bodyAsText())
        val rotated = refreshed.auth()
        assertNotEquals(session.refreshToken, rotated.refreshToken)
        assertEquals(session.user.id, rotated.user.id)
        assertFalse(rotated.isNewUser)

        val reused = post("/v1/auth/refresh", RefreshRequest(session.refreshToken))
        assertEquals(HttpStatusCode.Unauthorized, reused.status)
        assertEquals(ErrorCode.REFRESH_TOKEN_REUSED, reused.error().code)

        // The reuse revoked the whole device, including the token that was just issued.
        val afterReuse = post("/v1/auth/refresh", RefreshRequest(rotated.refreshToken))
        assertEquals(ErrorCode.REFRESH_TOKEN_INVALID, afterReuse.error().code)
    }

    @Test
    fun unknownExpiredOrOtherDeviceRefreshTokensAreInvalid() = withAuthServer {
        val session = registered()

        val otherDevice = post("/v1/auth/refresh", RefreshRequest(session.refreshToken), DEVICE_B)
        assertEquals(ErrorCode.REFRESH_TOKEN_INVALID, otherDevice.error().code)
        val unknown = post("/v1/auth/refresh", RefreshRequest(OpaqueTokens.generate()))
        assertEquals(ErrorCode.REFRESH_TOKEN_INVALID, unknown.error().code)

        clock.advance(Duration.ofDays(61))
        val expired = post("/v1/auth/refresh", RefreshRequest(session.refreshToken))
        assertEquals(HttpStatusCode.Unauthorized, expired.status)
        assertEquals(ErrorCode.REFRESH_TOKEN_INVALID, expired.error().code)
    }

    @Test
    fun protectedRoutesTellExpiredFromMissingOrBadTokens() = withAuthServer {
        val session = registered()

        assertEquals(HttpStatusCode.NoContent, post("/v1/auth/logout-all", "{}", token = session.accessToken).status)

        val none = post("/v1/auth/logout-all", "{}")
        assertEquals(HttpStatusCode.Unauthorized, none.status)
        assertEquals(ErrorCode.UNAUTHENTICATED, none.error().code)
        val garbage = post("/v1/auth/logout-all", "{}", token = "nope")
        assertEquals(ErrorCode.UNAUTHENTICATED, garbage.error().code)

        clock.advance(Duration.ofMinutes(16))
        val expired = post("/v1/auth/logout-all", "{}", token = session.accessToken)
        assertEquals(HttpStatusCode.Unauthorized, expired.status)
        assertEquals(ErrorCode.TOKEN_EXPIRED, expired.error().code)
    }

    @Test
    fun logoutClosesOneDeviceAndLogoutAllClosesEveryDevice() = withAuthServer {
        val email = uniqueEmail()
        val phone = registered(email, DEVICE_A)
        val tablet = loggedIn(email, device = DEVICE_B)

        val logout = post("/v1/auth/logout", LogoutRequest(phone.refreshToken), token = phone.accessToken)
        assertEquals(HttpStatusCode.NoContent, logout.status)
        assertEquals(
            ErrorCode.REFRESH_TOKEN_INVALID,
            post("/v1/auth/refresh", RefreshRequest(phone.refreshToken)).error().code,
        )
        val tabletStill = post("/v1/auth/refresh", RefreshRequest(tablet.refreshToken), DEVICE_B)
        assertEquals(HttpStatusCode.OK, tabletStill.status)

        val phoneAgain = loggedIn(email, device = DEVICE_A)
        assertEquals(HttpStatusCode.NoContent, post("/v1/auth/logout-all", "{}", token = phoneAgain.accessToken).status)
        assertEquals(
            ErrorCode.REFRESH_TOKEN_INVALID,
            post("/v1/auth/refresh", RefreshRequest(phoneAgain.refreshToken)).error().code,
        )
        val tabletToken = tabletStill.auth().refreshToken
        assertEquals(
            ErrorCode.REFRESH_TOKEN_INVALID,
            post("/v1/auth/refresh", RefreshRequest(tabletToken), DEVICE_B).error().code,
        )
    }

    @Test
    fun emailSignInIsLimitedPerIpAndEmail() =
        withAuthServer(RateLimitSettings(emailSignIn = RateLimitRule(2, 1.minutes))) {
            val email = uniqueEmail()
            registered(email)
            assertEquals(HttpStatusCode.OK, login(email).status)

            val limited = login(email)
            assertEquals(HttpStatusCode.TooManyRequests, limited.status)
            assertEquals(ErrorCode.RATE_LIMITED, limited.error().code)
            assertTrue(assertNotNull(limited.headers[HttpHeaders.RetryAfter]).toInt() in 1..60)

            // Another email, or the same email from another address, has its own count.
            assertEquals(HttpStatusCode.Unauthorized, login(uniqueEmail()).status)
            val otherIp = login(email) { header(RateLimits.HEADER_CLIENT_IP, "203.0.113.9") }
            assertEquals(HttpStatusCode.OK, otherIp.status)
        }

    @Test
    fun googleAndRefreshAreLimitedPerIpAndDevice() =
        withAuthServer(RateLimitSettings(authToken = RateLimitRule(1, 1.minutes))) {
            val idToken = google.account()
            assertEquals(HttpStatusCode.OK, post("/v1/auth/google", GoogleSignInRequest(idToken)).status)

            val limited = post("/v1/auth/google", GoogleSignInRequest(idToken))
            assertEquals(HttpStatusCode.TooManyRequests, limited.status)
            assertEquals(ErrorCode.RATE_LIMITED, limited.error().code)
            assertNotNull(limited.headers[HttpHeaders.RetryAfter])

            assertEquals(HttpStatusCode.OK, post("/v1/auth/google", GoogleSignInRequest(idToken), DEVICE_B).status)
        }

    @Test
    fun noPasswordTokenOrEmailReachesTheLog() {
        val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        root.addAppender(appender)
        val email = uniqueEmail()
        val secrets = mutableListOf(email, GOOD_PASSWORD)
        try {
            withAuthServer {
                val session = registered(email)
                secrets += listOf(session.accessToken, session.refreshToken)
                secrets += post("/v1/auth/refresh", RefreshRequest(session.refreshToken)).auth().refreshToken
                login(email, "wrong-password-here")
                secrets += mailer.lastTokenFor(email, "verify")
            }
        } finally {
            root.detachAppender(appender)
        }

        val logged = appender.list.joinToString("\n") { it.formattedMessage + " " + (it.throwableProxy?.message ?: "") }
        assertTrue(appender.list.isNotEmpty(), "expected request log lines")
        secrets.forEach { secret -> assertFalse(logged.contains(secret), "found a secret in the log: $logged") }
    }
}
