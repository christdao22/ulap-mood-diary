package app.ulap.server.auth

import app.ulap.server.error
import app.ulap.shared.api.ErrorCode
import app.ulap.shared.api.ForgotPasswordRequest
import app.ulap.shared.api.RefreshRequest
import app.ulap.shared.api.ResetPasswordRequest
import app.ulap.shared.api.VerifyEmailRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verification and reset links: single use, short-lived, and quiet about who has an account (api-spec.md §6.5–§6.8). */
class AccountEmailRoutesTest {
    @Test
    fun theVerificationLinkWorksOnce() = withAuthServer {
        val email = uniqueEmail()
        registered(email)
        val token = mailer.lastTokenFor(email, "verify")

        val verified = post("/v1/auth/email/verify", VerifyEmailRequest(token))
        assertEquals(HttpStatusCode.NoContent, verified.status)
        assertTrue(loggedIn(email).user.identities.single().emailVerified)

        val again = post("/v1/auth/email/verify", VerifyEmailRequest(token))
        assertEquals(HttpStatusCode.Gone, again.status)
        assertEquals(ErrorCode.TOKEN_USED_OR_EXPIRED, again.error().code)
        assertEquals(HttpStatusCode.Gone, post("/v1/auth/email/verify", VerifyEmailRequest("never-issued")).status)
    }

    @Test
    fun aVerificationLinkExpiresAfter24Hours() = withAuthServer {
        val email = uniqueEmail()
        registered(email)
        val token = mailer.lastTokenFor(email, "verify")

        clock.advance(Duration.ofHours(25))

        assertEquals(HttpStatusCode.Gone, post("/v1/auth/email/verify", VerifyEmailRequest(token)).status)
        assertFalse(loggedIn(email).user.identities.single().emailVerified)
    }

    @Test
    fun resendSendsANewLinkAndIsLimitedToThreeAnHour() = withAuthServer {
        val email = uniqueEmail()
        val session = registered(email)
        val firstToken = mailer.lastTokenFor(email, "verify")

        repeat(3) {
            val resend = post("/v1/auth/email/resend-verification", "{}", token = session.accessToken)
            assertEquals(HttpStatusCode.NoContent, resend.status)
        }
        assertEquals(4, mailer.sent.size)
        val limited = post("/v1/auth/email/resend-verification", "{}", token = session.accessToken)
        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        assertTrue(limited.headers[HttpHeaders.RetryAfter] != null)

        val newToken = mailer.lastTokenFor(email, "verify")
        assertFalse(newToken == firstToken)
        assertEquals(HttpStatusCode.NoContent, post("/v1/auth/email/verify", VerifyEmailRequest(newToken)).status)
        // Once one link was used, the earlier ones are done too.
        assertEquals(HttpStatusCode.Gone, post("/v1/auth/email/verify", VerifyEmailRequest(firstToken)).status)

        // Verified: resend has nothing to do, and says so quietly. (Two hours on, the access token is new.)
        clock.advance(Duration.ofHours(2))
        val nothing = post("/v1/auth/email/resend-verification", "{}", token = loggedIn(email).accessToken)
        assertEquals(HttpStatusCode.NoContent, nothing.status)
        assertEquals(4, mailer.sent.size)
    }

    @Test
    fun forgotNeverSaysWhetherTheAccountExists() = withAuthServer {
        val unknown = post("/v1/auth/password/forgot", ForgotPasswordRequest(uniqueEmail()))
        assertEquals(HttpStatusCode.NoContent, unknown.status)
        val notAnEmail = post("/v1/auth/password/forgot", ForgotPasswordRequest("nobody"))
        assertEquals(HttpStatusCode.NoContent, notAnEmail.status)
        assertTrue(mailer.sent.isEmpty())

        val email = uniqueEmail()
        registered(email)
        val known = post("/v1/auth/password/forgot", ForgotPasswordRequest(email.uppercase())) {
            header(HttpHeaders.AcceptLanguage, "ceb")
        }
        assertEquals(HttpStatusCode.NoContent, known.status)
        val mail = mailer.sent.last()
        assertEquals(email, mail.to)
        assertEquals("I-reset ang imong Ulap password", mail.subject)
        assertTrue(mail.text.contains("/reset?token="))
    }

    @Test
    fun resetChangesThePasswordAndSignsEveryDeviceOut() = withAuthServer {
        val email = uniqueEmail()
        val phone = registered(email, DEVICE_A)
        val tablet = loggedIn(email, device = DEVICE_B)
        post("/v1/auth/password/forgot", ForgotPasswordRequest(email))
        val token = mailer.lastTokenFor(email, "reset")

        // A weak password is refused before the link is spent.
        val weak = post("/v1/auth/password/reset", ResetPasswordRequest(token, "short"))
        assertEquals(HttpStatusCode.UnprocessableEntity, weak.status)
        assertEquals(ErrorCode.WEAK_PASSWORD, weak.error().code)

        val reset = post("/v1/auth/password/reset", ResetPasswordRequest(token, "Bag-ong-Password-2026"))
        assertEquals(HttpStatusCode.NoContent, reset.status)

        assertEquals(HttpStatusCode.Unauthorized, login(email, GOOD_PASSWORD).status)
        val fresh = loggedIn(email, "Bag-ong-Password-2026")
        assertTrue(fresh.user.identities.single().emailVerified, "opening the reset link proves the inbox")
        assertEquals(
            ErrorCode.REFRESH_TOKEN_INVALID,
            post("/v1/auth/refresh", RefreshRequest(phone.refreshToken)).error().code,
        )
        assertEquals(
            ErrorCode.REFRESH_TOKEN_INVALID,
            post("/v1/auth/refresh", RefreshRequest(tablet.refreshToken), DEVICE_B).error().code,
        )

        val again = post("/v1/auth/password/reset", ResetPasswordRequest(token, "Another-Password-2026"))
        assertEquals(HttpStatusCode.Gone, again.status)
        assertEquals(ErrorCode.TOKEN_USED_OR_EXPIRED, again.error().code)
    }

    @Test
    fun aResetLinkExpiresAfter30MinutesAndUnknownTokensAre410() = withAuthServer {
        val email = uniqueEmail()
        registered(email)
        post("/v1/auth/password/forgot", ForgotPasswordRequest(email))
        val token = mailer.lastTokenFor(email, "reset")

        clock.advance(Duration.ofMinutes(31))

        val expired = post("/v1/auth/password/reset", ResetPasswordRequest(token, "Bag-ong-Password-2026"))
        assertEquals(HttpStatusCode.Gone, expired.status)
        assertEquals(
            HttpStatusCode.Gone,
            post("/v1/auth/password/reset", ResetPasswordRequest("nope", "Bag-ong-Password-2026")).status,
        )
        // A verify link can't be used as a reset link.
        val verifyToken = mailer.lastTokenFor(email, "verify")
        assertEquals(
            HttpStatusCode.Gone,
            post("/v1/auth/password/reset", ResetPasswordRequest(verifyToken, "Bag-ong-Password-2026")).status,
        )
        assertEquals(HttpStatusCode.OK, login(email).status)
    }
}
