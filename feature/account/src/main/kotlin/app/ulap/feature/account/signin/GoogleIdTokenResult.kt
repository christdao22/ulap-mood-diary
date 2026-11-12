package app.ulap.feature.account.signin

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/** What Google's account picker gave back. Only [Token] goes on to the server. */
internal sealed interface GoogleIdTokenResult {
    /** `toString()` hides the token (CLAUDE.md rules). */
    data class Token(val idToken: String, val displayName: String?) : GoogleIdTokenResult {
        override fun toString(): String = "Token(***)"
    }

    /** The person closed the picker. Nothing to say about it. */
    data object Cancelled : GoogleIdTokenResult

    /** This phone has no Google account to offer. */
    data object NoGoogleAccount : GoogleIdTokenResult

    data object Failed : GoogleIdTokenResult
}

/**
 * Shows Google's account picker and returns a Google ID token for [webClientId] (api-spec.md
 * §6.1: `GetGoogleIdOption` with the Web client ID as `serverClientId`). [activityContext] must be
 * the screen's Activity, so the picker opens over it.
 *
 * Every Google account on the phone is offered, not only ones used here before: this button
 * both signs in and signs up.
 */
internal suspend fun requestGoogleIdToken(activityContext: Context, webClientId: String): GoogleIdTokenResult {
    val option = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(webClientId)
        .setAutoSelectEnabled(false)
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    return try {
        val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            GoogleIdTokenResult.Token(google.idToken, google.displayName)
        } else {
            GoogleIdTokenResult.Failed
        }
    } catch (_: GetCredentialCancellationException) {
        GoogleIdTokenResult.Cancelled
    } catch (_: NoCredentialException) {
        GoogleIdTokenResult.NoGoogleAccount
    } catch (_: GetCredentialException) {
        GoogleIdTokenResult.Failed
    } catch (_: GoogleIdTokenParsingException) {
        GoogleIdTokenResult.Failed
    }
}
