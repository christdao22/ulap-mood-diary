package app.ulap.server.auth

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import java.io.IOException
import java.security.GeneralSecurityException

/** What Ulap keeps from a verified Google ID token. [subject] is Google's stable id for the account. */
data class GoogleIdentity(val subject: String, val email: String?, val emailVerified: Boolean, val name: String?)

/**
 * Checks a Google ID token from the app (api-spec.md §6.1): the signature against Google's
 * public keys, `aud` equal to our Web client id, `iss` from Google and `exp` in the future.
 * Returns null for anything that fails, and never says why: the app only learns
 * `INVALID_GOOGLE_TOKEN`. Blocking: call it off the request thread.
 */
fun interface GoogleTokenVerifier {
    fun verify(idToken: String): GoogleIdentity?

    companion object {
        /** The real verifier, or one that rejects everything when `GOOGLE_WEB_CLIENT_ID` is unset (README §6.6). */
        fun from(webClientId: String?): GoogleTokenVerifier =
            if (webClientId == null) GoogleSignInDisabled else GoogleApiTokenVerifier(webClientId)
    }
}

/** Without a Web client id there's no audience to check, so Google sign-in is off and email sign-in still works. */
object GoogleSignInDisabled : GoogleTokenVerifier {
    override fun verify(idToken: String): GoogleIdentity? = null
}

/** Google's own verifier: it fetches and caches Google's signing keys and checks `aud`, `iss` and `exp`. */
class GoogleApiTokenVerifier(webClientId: String) : GoogleTokenVerifier {
    private val verifier = GoogleIdTokenVerifier.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance())
        .setAudience(listOf(webClientId))
        .build()

    override fun verify(idToken: String): GoogleIdentity? = try {
        verifier.verify(idToken)?.payload?.let { payload ->
            GoogleIdentity(
                subject = payload.subject,
                email = payload.email,
                emailVerified = payload.emailVerified ?: false,
                name = payload["name"] as? String,
            )
        }
    } catch (_: GeneralSecurityException) {
        null
    } catch (_: IOException) {
        // Google's keys couldn't be fetched: the token can't be trusted either.
        null
    } catch (_: IllegalArgumentException) {
        // Not a JWT at all.
        null
    }
}
