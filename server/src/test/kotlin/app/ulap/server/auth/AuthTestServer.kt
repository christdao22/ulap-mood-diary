@file:OptIn(ExperimentalUuidApi::class)

package app.ulap.server.auth

import app.ulap.server.AppDeps
import app.ulap.server.common.RateLimitSettings
import app.ulap.server.db.TestPostgres
import app.ulap.server.module
import app.ulap.shared.api.Api
import app.ulap.shared.api.AuthResponse
import app.ulap.shared.api.EmailLoginRequest
import app.ulap.shared.api.EmailRegisterRequest
import app.ulap.shared.model.Ids
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A clock tests move by hand, so "24 hours later" takes no time at all. */
class MutableClock(private var now: Instant = Instant.parse("2026-11-10T08:00:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = now

    fun advance(by: Duration) {
        now += by
    }
}

/** Keeps every email instead of sending it, and digs the token out of the link. */
class RecordingMailer : Mailer {
    val sent = mutableListOf<MailMessage>()

    override suspend fun send(message: MailMessage) {
        sent += message
    }

    /** The token in the last `/verify?token=…` or `/reset?token=…` link sent to [to]. */
    fun lastTokenFor(to: String, path: String): String {
        val link = Regex("""/$path\?token=([A-Za-z0-9_-]+)""")
        val match = sent.asReversed().filter { it.to == to }.firstNotNullOfOrNull { link.find(it.text) }
        return assertNotNull(match, "no $path link was sent to $to").groupValues[1]
    }
}

/** Accepts the ID tokens the test registered and rejects everything else. */
class FakeGoogleVerifier : GoogleTokenVerifier {
    private val accounts = mutableMapOf<String, GoogleIdentity>()

    /** Registers a Google account and returns the ID token the app would send for it. */
    fun account(email: String = uniqueEmail(), name: String? = "Ana"): String {
        val token = "google-id-token-" + Ids.newId()
        accounts[token] =
            GoogleIdentity(subject = "google-sub-" + Ids.newId(), email = email, emailVerified = true, name = name)
        return token
    }

    override fun verify(idToken: String): GoogleIdentity? = accounts[idToken]
}

val DEVICE_A: Uuid = Uuid.parse("6a1f9b2e-1c3d-4e5f-8a9b-0c1d2e3f4a5b")
val DEVICE_B: Uuid = Uuid.parse("7b2f9b2e-1c3d-4e5f-8a9b-0c1d2e3f4a5c")
const val GOOD_PASSWORD: String = "Kalma-sa-Ulap-2026"

fun uniqueEmail(): String = "person-${Ids.newId()}@example.com"

class AuthTestContext(
    val client: HttpClient,
    val clock: MutableClock,
    val mailer: RecordingMailer,
    val google: FakeGoogleVerifier,
) {
    suspend fun register(email: String, password: String = GOOD_PASSWORD, device: Uuid = DEVICE_A): HttpResponse =
        post("/v1/auth/email/register", EmailRegisterRequest(email, password, "Ana"), device)

    /** Registers and returns the session, failing the test unless it was a 201. */
    suspend fun registered(email: String = uniqueEmail(), device: Uuid = DEVICE_A): AuthResponse {
        val response = register(email, device = device)
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        return response.auth()
    }

    suspend fun login(
        email: String,
        password: String = GOOD_PASSWORD,
        device: Uuid = DEVICE_A,
        extra: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse = post("/v1/auth/email/login", EmailLoginRequest(email, password), device) { extra() }

    suspend fun loggedIn(email: String, password: String = GOOD_PASSWORD, device: Uuid = DEVICE_A): AuthResponse {
        val response = login(email, password, device)
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return response.auth()
    }
}

/**
 * The real [module] on the shared test PostgreSQL with a movable clock, a recording mailer, a fake
 * Google and a quick Argon2 (the real parameters are covered by PasswordsTest).
 */
fun withAuthServer(rateLimits: RateLimitSettings = RateLimitSettings(), block: suspend AuthTestContext.() -> Unit) {
    val clock = MutableClock()
    val mailer = RecordingMailer()
    val google = FakeGoogleVerifier()
    val config = TestPostgres.config
    val components = AuthComponents(
        accessTokens = AccessTokens(config.jwt, clock),
        googleVerifier = google,
        mailer = mailer,
        passwordHasher = Argon2PasswordHasher(memoryKb = 4096, iterations = 1),
        commonPasswords = CommonPasswords.load(),
    )
    testApplication {
        application { module(AppDeps(config, TestPostgres.dataSource, clock, rateLimits, components)) }
        val client = createClient { install(ContentNegotiation) { json(Api.json) } }
        AuthTestContext(client, clock, mailer, google).block()
    }
}

/** A JSON POST with the usual headers. [device] null leaves `X-Device-Id` out; [token] adds a bearer. */
suspend inline fun <reified T> AuthTestContext.post(
    path: String,
    body: T,
    device: Uuid? = DEVICE_A,
    token: String? = null,
    crossinline extra: HttpRequestBuilder.() -> Unit = {},
): HttpResponse = client.post(path) {
    contentType(ContentType.Application.Json)
    device?.let { header(Api.HEADER_DEVICE_ID, it.toString()) }
    token?.let { bearerAuth(it) }
    extra()
    setBody(body)
}

suspend fun HttpResponse.auth(): AuthResponse = Api.json.decodeFromString(bodyAsText())
