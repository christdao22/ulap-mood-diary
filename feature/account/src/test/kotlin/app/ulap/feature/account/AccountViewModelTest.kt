package app.ulap.feature.account

import app.ulap.core.domain.account.AccountState
import app.ulap.core.domain.account.AuthProblem
import app.ulap.core.model.AppLanguage
import app.ulap.core.model.UserSettings
import app.ulap.core.testing.FakeAccountRepository
import app.ulap.core.testing.FakeSettingsRepository
import app.ulap.core.testing.MainDispatcherRule
import app.ulap.feature.account.signin.SignInMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class AccountViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `the Language row follows the saved language`() = runTest {
        val settings = FakeSettingsRepository(UserSettings(language = AppLanguage.ENGLISH))
        val viewModel = AccountViewModel(settings, FakeAccountRepository())
        assertEquals(AppLanguage.ENGLISH, viewModel.language.first { it != null })

        settings.settings.update { it.copy(language = AppLanguage.BISAYA) }

        assertEquals(AppLanguage.BISAYA, viewModel.language.first { it == AppLanguage.BISAYA })
    }

    @Test
    fun `the Ulap switch follows the saved setting`() = runTest {
        val settings = FakeSettingsRepository(UserSettings(mascotEnabled = false))
        val viewModel = AccountViewModel(settings, FakeAccountRepository())
        assertFalse(viewModel.mascotEnabled.first { !it })
    }

    @Test
    fun `turning Ulap off saves it, and turning it back on saves that too`() = runTest {
        val settings = FakeSettingsRepository(UserSettings(mascotEnabled = true))
        val viewModel = AccountViewModel(settings, FakeAccountRepository())

        viewModel.setMascotEnabled(false)
        assertFalse(settings.settings.first { !it.mascotEnabled }.mascotEnabled)

        viewModel.setMascotEnabled(true)
        assertTrue(settings.settings.first { it.mascotEnabled }.mascotEnabled)
    }

    @Test
    fun `the account card follows the saved session`() = runTest {
        val accounts = FakeAccountRepository(AccountState.SignInAgain)
        val viewModel = AccountViewModel(FakeSettingsRepository(), accounts)
        assertEquals(AccountState.SignInAgain, viewModel.account.first { it != null })

        accounts.state.value = AccountState.Guest

        assertEquals(AccountState.Guest, viewModel.account.first { it == AccountState.Guest })
    }

    @Test
    fun `sending the verification link again reports how it went`() = runTest {
        val accounts = FakeAccountRepository()
        val viewModel = AccountViewModel(FakeSettingsRepository(), accounts)

        viewModel.resendVerification()
        assertEquals(ResendState.Sent, viewModel.resend.value)

        accounts.nextProblem = AuthProblem.TOO_MANY_TRIES
        viewModel.resendVerification()
        assertEquals(ResendState.Failed(SignInMessage.TOO_MANY_TRIES), viewModel.resend.value)
    }

    @Test
    fun `signing out goes through the repository`() = runTest {
        val accounts = FakeAccountRepository(AccountState.SignedIn(FakeAccountRepository().profile))
        val viewModel = AccountViewModel(FakeSettingsRepository(), accounts)

        viewModel.signOut()

        assertEquals(listOf("signOut"), accounts.calls)
        assertEquals(AccountState.Guest, accounts.state.value)
    }
}
