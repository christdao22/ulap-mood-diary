package app.ulap.feature.account.signin

import app.ulap.core.domain.account.AccountState
import app.ulap.core.domain.account.AuthProblem
import app.ulap.core.domain.account.GoogleSignInConfig
import app.ulap.core.testing.FakeAccountRepository
import app.ulap.core.testing.MainDispatcherRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class SignInViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val accounts = FakeAccountRepository()
    private val viewModel = SignInViewModel(accounts, GoogleSignInConfig("web-client-id"))

    private fun fill(email: String, password: String) {
        viewModel.onEmailChange(email)
        viewModel.onPasswordChange(password)
    }

    @Test
    fun `a build without a Web client ID hides Google`() {
        assertTrue(viewModel.state.value.googleAvailable)
        assertFalse(SignInViewModel(accounts, GoogleSignInConfig("")).state.value.googleAvailable)
    }

    @Test
    fun `creating an account checks the fields before sending anything`() = runTest {
        viewModel.openSheet(SignInStep.CREATE_ACCOUNT)
        fill("ana@", "short")

        viewModel.submit()

        val state = viewModel.state.value
        assertEquals(SignInMessage.EMAIL_INVALID, state.emailError)
        assertEquals(SignInMessage.PASSWORD_TOO_SHORT, state.passwordError)
        assertEquals(emptyList(), accounts.calls)
    }

    @Test
    fun `a password the same as the email is caught too`() = runTest {
        viewModel.openSheet(SignInStep.CREATE_ACCOUNT)
        fill("ana.santos@example.com", "Ana.Santos@example.com")

        viewModel.submit()

        assertEquals(SignInMessage.PASSWORD_SAME_AS_EMAIL, viewModel.state.value.passwordError)
    }

    @Test
    fun `typing again clears that field's error`() = runTest {
        viewModel.openSheet(SignInStep.CREATE_ACCOUNT)
        fill("ana@", "short")
        viewModel.submit()

        viewModel.onEmailChange("ana@example.com")

        assertNull(viewModel.state.value.emailError)
        assertEquals(SignInMessage.PASSWORD_TOO_SHORT, viewModel.state.value.passwordError)
    }

    @Test
    fun `a new account signs in, closes the sheet and forgets the password`() = runTest {
        viewModel.openSheet(SignInStep.CREATE_ACCOUNT)
        fill(" Ana@Example.com ", "Kalma-sa-Ulap-2026")

        viewModel.submit()

        assertEquals(listOf("create:ana@example.com"), accounts.calls)
        assertIs<AccountState.SignedIn>(accounts.state.value)
        val state = viewModel.state.value
        assertFalse(state.sheetOpen)
        assertEquals("", state.password)
        assertFalse(state.busy)
    }

    @Test
    fun `server answers land under the field they're about`() = runTest {
        viewModel.openSheet(SignInStep.CREATE_ACCOUNT)
        fill("ana@example.com", "Kalma-sa-Ulap-2026")

        accounts.nextProblem = AuthProblem.EMAIL_TAKEN
        viewModel.submit()
        assertEquals(SignInMessage.EMAIL_TAKEN, viewModel.state.value.emailError)

        accounts.nextProblem = AuthProblem.WEAK_PASSWORD
        viewModel.submit()
        assertEquals(SignInMessage.WEAK_PASSWORD, viewModel.state.value.passwordError)

        accounts.nextProblem = AuthProblem.OFFLINE
        viewModel.submit()
        assertEquals(SignInMessage.OFFLINE, viewModel.state.value.formError)
        assertTrue(viewModel.state.value.sheetOpen)
    }

    @Test
    fun `signing in needs a password but not the sign-up length rules`() = runTest {
        viewModel.openSheet(SignInStep.SIGN_IN)
        fill("ana@example.com", "")
        viewModel.submit()
        assertEquals(SignInMessage.PASSWORD_EMPTY, viewModel.state.value.passwordError)

        viewModel.onPasswordChange("old")
        accounts.nextProblem = AuthProblem.WRONG_EMAIL_OR_PASSWORD
        viewModel.submit()

        assertEquals(listOf("signIn:ana@example.com"), accounts.calls)
        assertEquals(SignInMessage.WRONG_EMAIL_OR_PASSWORD, viewModel.state.value.passwordError)
    }

    @Test
    fun `a second tap while a request runs sends nothing more`() = runTest {
        accounts.gate = CompletableDeferred()
        viewModel.openSheet(SignInStep.SIGN_IN)
        fill("ana@example.com", "Kalma-sa-Ulap-2026")

        viewModel.submit()
        viewModel.submit()
        assertTrue(viewModel.state.value.busy)
        assertFalse(viewModel.onGoogleStarted())
        accounts.gate?.complete(Unit)

        assertEquals(1, accounts.calls.size)
        assertFalse(viewModel.state.value.busy)
    }

    @Test
    fun `forgot password shows Check your email once the server took it`() = runTest {
        viewModel.openSheet(SignInStep.SIGN_IN)
        viewModel.showStep(SignInStep.FORGOT_PASSWORD)
        viewModel.onEmailChange("ana@example.com")

        viewModel.submit()

        assertEquals(listOf("reset:ana@example.com"), accounts.calls)
        assertEquals(SignInStep.RESET_SENT, viewModel.state.value.step)
    }

    @Test
    fun `a Google token signs in, and closing the picker says nothing`() = runTest {
        assertTrue(viewModel.onGoogleStarted())
        viewModel.onGoogleResult(GoogleIdTokenResult.Cancelled)
        assertFalse(viewModel.state.value.busy)
        assertNull(viewModel.state.value.googleError)

        assertTrue(viewModel.onGoogleStarted())
        viewModel.onGoogleResult(GoogleIdTokenResult.Token("id.token", "Ana"))

        assertEquals(listOf("google:id.token"), accounts.calls)
        assertIs<AccountState.SignedIn>(accounts.state.value)
    }

    @Test
    fun `Google problems show under the Google button`() = runTest {
        viewModel.onGoogleStarted()
        viewModel.onGoogleResult(GoogleIdTokenResult.NoGoogleAccount)
        assertEquals(SignInMessage.NO_GOOGLE_ACCOUNT, viewModel.state.value.googleError)

        viewModel.onGoogleStarted()
        accounts.nextProblem = AuthProblem.GOOGLE_NOT_ACCEPTED
        viewModel.onGoogleResult(GoogleIdTokenResult.Token("id.token", null))
        assertEquals(SignInMessage.GOOGLE_FAILED, viewModel.state.value.googleError)
        assertFalse(viewModel.state.value.busy)
    }

    @Test
    fun `the state never prints the password`() {
        viewModel.onPasswordChange("Kalma-sa-Ulap-2026")
        assertFalse("Kalma" in viewModel.state.value.toString())
    }
}
