package app.ulap.core.testing

import app.ulap.core.domain.account.AccountProfile
import app.ulap.core.domain.account.AccountRepository
import app.ulap.core.domain.account.AccountState
import app.ulap.core.domain.account.AuthProblem
import app.ulap.core.domain.account.SignInResult
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * An account in memory. A successful sign-in moves [state] to signed in, as the real one does.
 * Tests set [nextProblem] to make the next call fail, or [gate] to hold it until they complete it.
 * [calls] records each call without its password.
 */
class FakeAccountRepository(initial: AccountState = AccountState.Guest) : AccountRepository {
    override val state: MutableStateFlow<AccountState> = MutableStateFlow(initial)

    val calls = mutableListOf<String>()
    var nextProblem: AuthProblem? = null
    var isNewUser: Boolean = true
    var gate: CompletableDeferred<Unit>? = null

    /** The profile a successful sign-in shows. */
    var profile: AccountProfile = AccountProfile(
        displayName = "Ana",
        email = "ana@example.com",
        emailVerified = true,
        memberSince = Instant.parse("2026-10-01T02:00:00Z"),
    )

    override suspend fun signInWithGoogle(idToken: String, displayName: String?): SignInResult =
        signIn("google:$idToken")

    override suspend fun createAccount(email: String, password: String): SignInResult = signIn("create:$email")

    override suspend fun signInWithEmail(email: String, password: String): SignInResult = signIn("signIn:$email")

    override suspend fun sendPasswordReset(email: String): AuthProblem? = answer("reset:$email")

    override suspend fun resendVerification(): AuthProblem? = answer("resend")

    override suspend fun signOut() {
        calls += "signOut"
        state.value = AccountState.Guest
    }

    private suspend fun signIn(call: String): SignInResult = when (val problem = answer(call)) {
        null -> {
            state.value = AccountState.SignedIn(profile)
            SignInResult.SignedIn(isNewUser)
        }

        else -> SignInResult.Failed(problem)
    }

    private suspend fun answer(call: String): AuthProblem? {
        calls += call
        gate?.await()
        return nextProblem.also { nextProblem = null }
    }
}
