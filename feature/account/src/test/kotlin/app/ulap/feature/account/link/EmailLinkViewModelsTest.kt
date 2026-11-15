package app.ulap.feature.account.link

import androidx.lifecycle.SavedStateHandle
import app.ulap.core.domain.account.AuthProblem
import app.ulap.core.testing.FakeAccountRepository
import app.ulap.core.testing.MainDispatcherRule
import app.ulap.feature.account.signin.SignInMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule

class EmailLinkViewModelsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val accounts = FakeAccountRepository()

    private fun link(token: String) = SavedStateHandle(mapOf("token" to token))

    @Test
    fun `opening the verification link sends its token once`() {
        val viewModel = VerifyEmailViewModel(link("Zk3"), accounts)

        assertEquals(VerifyState.Verified, viewModel.state.value)
        assertEquals(listOf("verify:Zk3"), accounts.calls)
    }

    @Test
    fun `a spent verification link says so, and a link without a token reads as spent`() {
        accounts.nextProblem = AuthProblem.LINK_USED_OR_EXPIRED
        assertEquals(VerifyState.Expired, VerifyEmailViewModel(link("Zk3"), accounts).state.value)

        assertEquals(VerifyState.Expired, VerifyEmailViewModel(link(""), accounts).state.value)
        assertEquals(listOf("verify:Zk3"), accounts.calls)
    }

    @Test
    fun `offline can try again with the same link`() {
        accounts.nextProblem = AuthProblem.OFFLINE
        val viewModel = VerifyEmailViewModel(link("Zk3"), accounts)
        assertEquals(VerifyState.Failed(SignInMessage.OFFLINE), viewModel.state.value)

        viewModel.retry()

        assertEquals(VerifyState.Verified, viewModel.state.value)
    }

    @Test
    fun `after process death a verified link isn't sent again`() {
        val handle = link("Zk3")
        VerifyEmailViewModel(handle, accounts)

        val restored = VerifyEmailViewModel(handle, accounts)

        assertEquals(VerifyState.Verified, restored.state.value)
        assertEquals(listOf("verify:Zk3"), accounts.calls)
    }

    @Test
    fun `a short password is caught before anything is sent`() {
        val viewModel = ResetPasswordViewModel(link("Rr8"), accounts)
        viewModel.onPasswordChange("short")

        viewModel.submit()

        assertEquals(SignInMessage.PASSWORD_TOO_SHORT, viewModel.state.value.passwordError)
        assertEquals(emptyList(), accounts.calls)
    }

    @Test
    fun `a new password is saved and dropped from memory`() {
        val viewModel = ResetPasswordViewModel(link("Rr8"), accounts)
        viewModel.onPasswordChange("Bag-ong-Password-2026")

        viewModel.submit()

        assertEquals(ResetUiState(step = ResetStep.DONE), viewModel.state.value)
        assertEquals(listOf("resetPassword:Rr8"), accounts.calls)
    }

    @Test
    fun `the server's answers keep the form or end it`() {
        val viewModel = ResetPasswordViewModel(link("Rr8"), accounts)
        viewModel.onPasswordChange("Bag-ong-Password-2026")

        accounts.nextProblem = AuthProblem.WEAK_PASSWORD
        viewModel.submit()
        assertEquals(SignInMessage.WEAK_PASSWORD, viewModel.state.value.passwordError)

        accounts.nextProblem = AuthProblem.OFFLINE
        viewModel.submit()
        assertEquals(SignInMessage.OFFLINE, viewModel.state.value.formError)
        assertEquals(ResetStep.FORM, viewModel.state.value.step)

        accounts.nextProblem = AuthProblem.LINK_USED_OR_EXPIRED
        viewModel.submit()
        assertEquals(ResetUiState(step = ResetStep.EXPIRED), viewModel.state.value)
    }

    @Test
    fun `one tap sends once`() {
        val gate = CompletableDeferred<Unit>()
        accounts.gate = gate
        val viewModel = ResetPasswordViewModel(link("Rr8"), accounts)
        viewModel.onPasswordChange("Bag-ong-Password-2026")

        viewModel.submit()
        viewModel.submit()
        gate.complete(Unit)

        assertEquals(listOf("resetPassword:Rr8"), accounts.calls)
    }

    @Test
    fun `a reset link without a token reads as spent`() {
        assertEquals(ResetStep.EXPIRED, ResetPasswordViewModel(link(""), accounts).state.value.step)
    }

    @Test
    fun `the password never shows in toString`() {
        assertEquals("ResetUiState(step=FORM, busy=false)", ResetUiState(password = "Bag-ong-Password-2026").toString())
    }
}
