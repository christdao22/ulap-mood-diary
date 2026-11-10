package app.ulap.server.auth

import app.ulap.server.error
import app.ulap.shared.api.Api
import app.ulap.shared.api.EmailRegisterRequest
import app.ulap.shared.api.ErrorCode
import app.ulap.shared.api.ErrorDetail
import app.ulap.shared.api.GoogleSignInRequest
import app.ulap.shared.api.UserDto
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Signing up and in with Google and with email, and linking the other method (api-spec.md §6.1–§6.4, §6.12–§6.13). */
class SignInRoutesTest {
    @Test
    fun googleSignInCreatesTheUserOnceAndFindsItAfterwards() = withAuthServer {
        val idToken = google.account(email = "ana@example.com", name = "Ana Reyes")

        val first = post("/v1/auth/google", GoogleSignInRequest(idToken))
        assertEquals(HttpStatusCode.OK, first.status, first.bodyAsText())
        val session = first.auth()
        assertTrue(session.isNewUser)
        assertEquals("Ana Reyes", session.user.displayName)
        assertEquals("en", session.user.language)
        assertEquals(listOf("GOOGLE"), session.user.identities.map { it.provider })
        assertTrue(session.user.identities.single().emailVerified)

        val again = post("/v1/auth/google", GoogleSignInRequest(idToken, displayName = "Someone else"), DEVICE_B).auth()
        assertFalse(again.isNewUser)
        assertEquals(session.user.id, again.user.id)
        assertEquals("Ana Reyes", again.user.displayName)
        assertNotEquals(session.refreshToken, again.refreshToken)
    }

    @Test
    fun aGoogleTokenThatDoesNotVerifyIs401() = withAuthServer {
        val response = post("/v1/auth/google", GoogleSignInRequest("forged"))

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(ErrorCode.INVALID_GOOGLE_TOKEN, response.error().code)
    }

    @Test
    fun theDeviceHeaderIsRequired() = withAuthServer {
        val missing = post("/v1/auth/google", GoogleSignInRequest(google.account()), device = null)
        assertEquals(HttpStatusCode.BadRequest, missing.status)
        assertEquals(ErrorCode.BAD_REQUEST, missing.error().code)

        val garbled = client.post("/v1/auth/google") {
            contentType(ContentType.Application.Json)
            header(Api.HEADER_DEVICE_ID, "phone-1")
            setBody("""{"idToken":"x"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, garbled.status)
    }

    @Test
    fun registerThenLoginWithEmail() = withAuthServer {
        val email = uniqueEmail()

        val response = register(" ${email.uppercase()} ")
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        val session = response.auth()
        assertTrue(session.isNewUser)
        assertEquals("Ana", session.user.displayName)
        val identity = session.user.identities.single()
        assertEquals("EMAIL", identity.provider)
        assertEquals(email, identity.email)
        assertFalse(identity.emailVerified)
        val mail = mailer.sent.single()
        assertEquals(email, mail.to)
        assertEquals("Confirm your email for Ulap", mail.subject)
        assertTrue(mail.text.contains("http://localhost:8080/verify?token="), mail.text)

        val login = loggedIn(email)
        assertFalse(login.isNewUser)
        assertEquals(session.user.id, login.user.id)
    }

    @Test
    fun wrongPasswordAndUnknownEmailGetTheSameAnswer() = withAuthServer {
        val email = uniqueEmail()
        registered(email)

        val wrongPassword = login(email, "not-the-password-at-all")
        val unknownEmail = login(uniqueEmail())
        val notAnEmail = login("nobody")

        listOf(wrongPassword, unknownEmail, notAnEmail).forEach { response ->
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals(ErrorCode.INVALID_CREDENTIALS, response.error().code)
        }
    }

    @Test
    fun aSecondRegistrationWithTheSameEmailIs409() = withAuthServer {
        val email = uniqueEmail()
        registered(email)

        val response = register(email.uppercase())

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(ErrorCode.EMAIL_TAKEN, response.error().code)
    }

    @Test
    fun weakPasswordsAre422AndBadFieldsAre400() = withAuthServer {
        val weak = register(uniqueEmail(), password = "1234567890")
        assertEquals(HttpStatusCode.UnprocessableEntity, weak.status)
        assertEquals(ErrorCode.WEAK_PASSWORD, weak.error().code)
        assertEquals(listOf(ErrorDetail("password", "COMMON")), weak.error().details)

        val badEmail = register("not-an-email")
        assertEquals(HttpStatusCode.BadRequest, badEmail.status)
        assertEquals(listOf(ErrorDetail("email", "INVALID")), badEmail.error().details)

        val longName =
            post("/v1/auth/email/register", EmailRegisterRequest(uniqueEmail(), GOOD_PASSWORD, "a".repeat(51)))
        assertEquals(HttpStatusCode.BadRequest, longName.status)
        assertEquals(listOf(ErrorDetail("displayName", "TOO_LONG")), longName.error().details)
        assertTrue(mailer.sent.isEmpty(), "nothing should be sent for a failed registration")
    }

    @Test
    fun theVerificationEmailFollowsAcceptLanguage() = withAuthServer {
        val email = uniqueEmail()

        val response = post("/v1/auth/email/register", EmailRegisterRequest(email, GOOD_PASSWORD)) {
            header(HttpHeaders.AcceptLanguage, "fil-PH,en;q=0.8")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("fil", response.auth().user.language)
        assertEquals("Kumpirmahin ang iyong email para sa Ulap", mailer.sent.single().subject)
    }

    @Test
    fun googleCanBeLinkedToAnEmailAccountOnce() = withAuthServer {
        val session = registered()
        val idToken = google.account()

        val linked = post("/v1/auth/link/google", GoogleSignInRequest(idToken), token = session.accessToken)
        assertEquals(HttpStatusCode.OK, linked.status, linked.bodyAsText())
        val user = Api.json.decodeFromString<UserDto>(linked.bodyAsText())
        assertEquals(listOf("EMAIL", "GOOGLE"), user.identities.map { it.provider })

        // The same link again changes nothing.
        assertEquals(
            HttpStatusCode.OK,
            post("/v1/auth/link/google", GoogleSignInRequest(idToken), token = session.accessToken).status,
        )

        // Another account can't take this Google identity.
        val other = registered()
        val stolen = post("/v1/auth/link/google", GoogleSignInRequest(idToken), token = other.accessToken)
        assertEquals(HttpStatusCode.Conflict, stolen.status)
        assertEquals(ErrorCode.IDENTITY_LINKED_ELSEWHERE, stolen.error().code)

        // And this account can't take a second Google identity.
        val second = post("/v1/auth/link/google", GoogleSignInRequest(google.account()), token = session.accessToken)
        assertEquals(HttpStatusCode.Conflict, second.status)
        assertEquals(ErrorCode.IDENTITY_LINKED_ELSEWHERE, second.error().code)

        // Google sign-in now lands on the email account.
        assertEquals(user.id, post("/v1/auth/google", GoogleSignInRequest(idToken)).auth().user.id)
    }

    @Test
    fun emailCanBeLinkedToAGoogleAccount() = withAuthServer {
        val session = post("/v1/auth/google", GoogleSignInRequest(google.account())).auth()
        val email = uniqueEmail()

        val linked =
            post("/v1/auth/link/email", EmailRegisterRequest(email, GOOD_PASSWORD), token = session.accessToken)
        assertEquals(HttpStatusCode.OK, linked.status, linked.bodyAsText())
        val user = Api.json.decodeFromString<UserDto>(linked.bodyAsText())
        assertEquals(listOf("GOOGLE", "EMAIL"), user.identities.map { it.provider })
        assertFalse(user.identities.last().emailVerified)
        assertEquals(email, mailer.sent.single().to)

        assertEquals(session.user.id, loggedIn(email).user.id)

        // A taken email, or a second email on the same account, is a conflict.
        val taken =
            post("/v1/auth/link/email", EmailRegisterRequest(email, GOOD_PASSWORD), token = registered().accessToken)
        assertEquals(ErrorCode.EMAIL_TAKEN, taken.error().code)
        val second =
            post("/v1/auth/link/email", EmailRegisterRequest(uniqueEmail(), GOOD_PASSWORD), token = session.accessToken)
        assertEquals(ErrorCode.EMAIL_TAKEN, second.error().code)
    }
}
