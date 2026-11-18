package app.ulap.server.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.google.api.client.http.HttpTransport
import com.google.api.client.testing.http.MockHttpTransport
import com.google.api.client.testing.http.MockLowLevelHttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The real Google ID token check behind `POST /auth/google` and `/auth/link/google` (api-spec.md
 * §6.1, §15): the signature, `aud`, `iss` and `exp`. The route tests use a fake verifier, so this
 * is where those checks are proven. Google's key endpoint is swapped for one that serves a
 * certificate made for this test, so it runs offline.
 */
class GoogleApiTokenVerifierTest {
    private val verifier = GoogleApiTokenVerifier(WEB_CLIENT_ID, servingKeys(googleKey.certificate))

    @Test
    fun aTokenGoogleSignedForUlapIsAccepted() {
        assertEquals(
            GoogleIdentity(subject = SUBJECT, email = "ana@example.com", emailVerified = true, name = "Ana Reyes"),
            verifier.verify(token()),
        )
    }

    @Test
    fun bothOfGooglesIssuerSpellingsAreAccepted() {
        for (issuer in listOf("accounts.google.com", "https://accounts.google.com")) {
            assertEquals(SUBJECT, verifier.verify(token(issuer = issuer))?.subject, issuer)
        }
    }

    @Test
    fun aTokenForAnotherAppIsRejected() {
        assertNull(verifier.verify(token(audience = "someone-elses-client-id.apps.googleusercontent.com")))
    }

    @Test
    fun aTokenFromAnotherIssuerIsRejected() {
        assertNull(verifier.verify(token(issuer = "https://accounts.example.com")))
    }

    @Test
    fun anExpiredTokenIsRejected() {
        // Google allows 5 minutes of clock skew, so an hour ago is well past it.
        val anHourAgo = Instant.now().minusSeconds(3600)
        assertNull(verifier.verify(token(issuedAt = anHourAgo.minusSeconds(3600), expiresAt = anHourAgo)))
    }

    @Test
    fun aTokenSignedWithAKeyGoogleDidNotPublishIsRejected() {
        val other = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val forged = Algorithm.RSA256(other.public as RSAPublicKey, other.private as RSAPrivateKey)

        assertNull(verifier.verify(token(signedWith = forged)))
    }

    @Test
    fun somethingThatIsNotAGoogleIdTokenIsRejected() {
        for (garbage in listOf("forged", "", "a.b.c", token().dropLast(4))) {
            assertNull(verifier.verify(garbage), garbage)
        }
    }

    @Test
    fun whenGoogleKeysCantBeFetchedTheTokenIsRejected() {
        val offline = MockHttpTransport.Builder()
            .setLowLevelHttpResponse(MockLowLevelHttpResponse().setStatusCode(503))
            .build()

        assertNull(GoogleApiTokenVerifier(WEB_CLIENT_ID, offline).verify(token()))
    }

    private fun token(
        audience: String = WEB_CLIENT_ID,
        issuer: String = "https://accounts.google.com",
        issuedAt: Instant = Instant.now().minusSeconds(60),
        expiresAt: Instant = Instant.now().plusSeconds(3600),
        signedWith: Algorithm = googleKey.algorithm,
    ): String = JWT.create()
        .withKeyId(KEY_ID)
        .withIssuer(issuer)
        .withAudience(audience)
        .withSubject(SUBJECT)
        .withIssuedAt(issuedAt)
        .withExpiresAt(expiresAt)
        .withClaim("email", "ana@example.com")
        .withClaim("email_verified", true)
        .withClaim("name", "Ana Reyes")
        .sign(signedWith)

    private companion object {
        const val WEB_CLIENT_ID = "ulap-test.apps.googleusercontent.com"
        const val SUBJECT = "110169484474386276334"
        const val KEY_ID = "test-key"
        const val STORE_PASSWORD = "ulap-test-only"

        /** Made once per run: keytool takes about a second. */
        val googleKey: SigningKey by lazy { selfSignedKey() }

        /**
         * A transport that answers Google's certificate URL the way Google does: a JSON object of
         * key id → PEM certificate, cached for an hour.
         */
        fun servingKeys(certificate: X509Certificate): HttpTransport {
            val pem = "-----BEGIN CERTIFICATE-----\n" +
                Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(certificate.encoded) +
                "\n-----END CERTIFICATE-----\n"
            val body = buildJsonObject { put(KEY_ID, pem) }.toString()
            return MockHttpTransport.Builder()
                .setLowLevelHttpResponse(
                    MockLowLevelHttpResponse()
                        .setContentType("application/json; charset=UTF-8")
                        .addHeader("Cache-Control", "public, max-age=3600")
                        .setContent(body),
                )
                .build()
        }

        /**
         * A fresh RSA key and self-signed certificate from the JDK's own keytool. The JDK has no
         * public API for making a certificate, and this way no private key is committed and no
         * library is added just for tests.
         */
        fun selfSignedKey(): SigningKey {
            val dir = Files.createTempDirectory("ulap-google-key")
            try {
                val store = dir.resolve("key.p12")
                val keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString()
                val process = ProcessBuilder(
                    keytool, "-genkeypair", "-alias", KEY_ID, "-keyalg", "RSA", "-keysize", "2048",
                    "-dname", "CN=Ulap test", "-validity", "2", "-storetype", "PKCS12",
                    "-keystore", store.toString(), "-storepass", STORE_PASSWORD, "-keypass", STORE_PASSWORD,
                ).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText()
                check(process.waitFor() == 0) { "keytool failed: $output" }

                val keyStore = KeyStore.getInstance("PKCS12")
                Files.newInputStream(store).use { keyStore.load(it, STORE_PASSWORD.toCharArray()) }
                return SigningKey(
                    privateKey = keyStore.getKey(KEY_ID, STORE_PASSWORD.toCharArray()) as RSAPrivateKey,
                    certificate = keyStore.getCertificate(KEY_ID) as X509Certificate,
                )
            } finally {
                dir.toFile().deleteRecursively()
            }
        }
    }

    class SigningKey(privateKey: RSAPrivateKey, val certificate: X509Certificate) {
        val algorithm: Algorithm = Algorithm.RSA256(certificate.publicKey as RSAPublicKey, privateKey)
    }
}
