package app.ulap.feature.account.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ulap.core.domain.account.AccountRepository
import app.ulap.core.domain.account.AuthProblem
import app.ulap.core.domain.account.GoogleSignInConfig
import app.ulap.core.domain.account.SignInResult
import app.ulap.shared.validation.AccountRules
import app.ulap.shared.validation.PasswordRules
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the sign-in sheet is (design-spec.md §7.9). */
internal enum class SignInStep {
    /** Ulap, "Back up your diary", Continue with Google and Use email instead. */
    CHOOSE,
    CREATE_ACCOUNT,
    SIGN_IN,
    FORGOT_PASSWORD,

    /** "Check your email" after a reset link was asked for. */
    RESET_SENT,
}

/** Each message the sign-in UI can show. The screen maps it to a string in the app's language. */
internal enum class SignInMessage {
    EMAIL_INVALID,
    PASSWORD_EMPTY,
    PASSWORD_TOO_SHORT,
    PASSWORD_TOO_LONG,
    PASSWORD_SAME_AS_EMAIL,
    WRONG_EMAIL_OR_PASSWORD,
    EMAIL_TAKEN,
    WEAK_PASSWORD,
    GOOGLE_FAILED,
    NO_GOOGLE_ACCOUNT,
    OFFLINE,
    TOO_MANY_TRIES,
    UPDATE_REQUIRED,
    SERVER,
}

internal data class SignInUiState(
    val googleAvailable: Boolean,
    val sheetOpen: Boolean = false,
    val step: SignInStep = SignInStep.CHOOSE,
    val email: String = "",
    val password: String = "",
    val emailError: SignInMessage? = null,
    val passwordError: SignInMessage? = null,
    /** Not about one field: offline, the server, too many tries. */
    val formError: SignInMessage? = null,
    /** A Google sign-in that didn't work, shown under the Google button wherever it was tapped. */
    val googleError: SignInMessage? = null,
    /** A request or Google's picker is open: the buttons wait, so one tap can't send twice. */
    val busy: Boolean = false,
) {
    /** Never prints the password, even in a test failure (CLAUDE.md rules). */
    override fun toString(): String =
        "SignInUiState(step=$step, sheetOpen=$sheetOpen, busy=$busy, emailError=$emailError, " +
            "passwordError=$passwordError, formError=$formError, googleError=$googleError)"
}

/**
 * The sign-in sheet and the Account card's sign-in buttons (design-spec.md §7.8, §7.9). The forms
 * check the same rules as the server (AccountRules, PasswordRules) before sending anything, so a
 * typo is caught offline. The password stays in memory only, and is cleared once it's used.
 */
@HiltViewModel
internal class SignInViewModel @Inject constructor(
    private val accounts: AccountRepository,
    googleConfig: GoogleSignInConfig,
) : ViewModel() {
    private val _state = MutableStateFlow(SignInUiState(googleAvailable = googleConfig.isAvailable))
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    /** The Web client ID Credential Manager asks Google for. Blank hides the Google buttons. */
    val googleWebClientId: String = googleConfig.webClientId

    fun openSheet(step: SignInStep) {
        _state.update {
            it.copy(sheetOpen = true, step = step, password = "", googleError = null).withoutFieldErrors()
        }
    }

    fun closeSheet() {
        _state.update { it.copy(sheetOpen = false, step = SignInStep.CHOOSE, password = "").withoutFieldErrors() }
    }

    fun showStep(step: SignInStep) {
        _state.update { it.copy(step = step, password = "").withoutFieldErrors() }
    }

    fun onEmailChange(email: String) {
        _state.update { it.copy(email = email, emailError = null, formError = null) }
    }

    fun onPasswordChange(password: String) {
        _state.update { it.copy(password = password, passwordError = null, formError = null) }
    }

    /** The primary button of the email steps. Checks the fields first, so a typo is caught offline. */
    fun submit() {
        val current = _state.value
        if (current.busy) return
        val errors = current.fieldErrors()
        val email = AccountRules.normalizeEmail(current.email)
        if (errors.any || email == null) {
            _state.update { it.copy(emailError = errors.email, passwordError = errors.password) }
            return
        }
        val password = current.password
        when (current.step) {
            SignInStep.CREATE_ACCOUNT -> send({ accounts.createAccount(email, password) }) { after(it) }

            SignInStep.SIGN_IN -> send({ accounts.signInWithEmail(email, password) }) { after(it) }

            SignInStep.FORGOT_PASSWORD -> send({ accounts.sendPasswordReset(email) }) { problem ->
                if (problem == null) copy(step = SignInStep.RESET_SENT) else copy(formError = problem.toMessage())
            }

            SignInStep.CHOOSE, SignInStep.RESET_SENT -> Unit
        }
    }

    /**
     * Google's picker is about to open. Returns false when something is already running, so the
     * screen doesn't open a second picker.
     */
    fun onGoogleStarted(): Boolean {
        if (_state.value.busy) return false
        _state.update { it.copy(busy = true, googleError = null, formError = null) }
        return true
    }

    fun onGoogleResult(result: GoogleIdTokenResult) {
        when (result) {
            is GoogleIdTokenResult.Token -> send({ accounts.signInWithGoogle(result.idToken, result.displayName) }) {
                when (it) {
                    is SignInResult.SignedIn -> signedIn()
                    is SignInResult.Failed -> copy(googleError = it.problem.toMessage())
                }
            }

            GoogleIdTokenResult.Cancelled -> _state.update { it.copy(busy = false) }

            GoogleIdTokenResult.NoGoogleAccount ->
                _state.update { it.copy(busy = false, googleError = SignInMessage.NO_GOOGLE_ACCOUNT) }

            GoogleIdTokenResult.Failed ->
                _state.update { it.copy(busy = false, googleError = SignInMessage.GOOGLE_FAILED) }
        }
    }

    /** Runs one request with the buttons waiting, then applies [then] to its answer. */
    private fun <T> send(request: suspend () -> T, then: SignInUiState.(T) -> SignInUiState) {
        _state.update { it.copy(busy = true, formError = null) }
        viewModelScope.launch {
            val result = request()
            _state.update { it.then(result).copy(busy = false) }
        }
    }
}

/** What the form's own checks found (AccountRules, PasswordRules), before anything is sent. */
private data class FieldErrors(val email: SignInMessage?, val password: SignInMessage?) {
    val any: Boolean get() = email != null || password != null
}

private fun SignInUiState.fieldErrors(): FieldErrors {
    val normalized = AccountRules.normalizeEmail(email)
    val passwordError = when (step) {
        SignInStep.CREATE_ACCOUNT -> PasswordRules.check(password, normalized)?.toMessage()

        // No length rules at sign-in: an older account's password must still work.
        SignInStep.SIGN_IN -> SignInMessage.PASSWORD_EMPTY.takeIf { password.isEmpty() }

        SignInStep.CHOOSE, SignInStep.FORGOT_PASSWORD, SignInStep.RESET_SENT -> null
    }
    return FieldErrors(email = SignInMessage.EMAIL_INVALID.takeIf { normalized == null }, password = passwordError)
}

/**
 * Success closes the sheet: the Account screen then shows the profile, read from the saved
 * session. The password is dropped either way it ends.
 */
private fun SignInUiState.signedIn() = copy(sheetOpen = false, step = SignInStep.CHOOSE, password = "")

private fun SignInUiState.after(result: SignInResult) = when (result) {
    is SignInResult.SignedIn -> signedIn()
    is SignInResult.Failed -> withProblem(result.problem)
}

private fun SignInUiState.withoutFieldErrors() = copy(emailError = null, passwordError = null, formError = null)

/** Puts each problem where design-spec.md §7.9 wants it: under the field it's about, if any. */
private fun SignInUiState.withProblem(problem: AuthProblem): SignInUiState = when (problem) {
    AuthProblem.EMAIL_TAKEN -> copy(emailError = SignInMessage.EMAIL_TAKEN)
    AuthProblem.WEAK_PASSWORD -> copy(passwordError = SignInMessage.WEAK_PASSWORD)
    AuthProblem.WRONG_EMAIL_OR_PASSWORD -> copy(passwordError = SignInMessage.WRONG_EMAIL_OR_PASSWORD)
    else -> copy(formError = problem.toMessage())
}

internal fun AuthProblem.toMessage(): SignInMessage = when (this) {
    AuthProblem.WRONG_EMAIL_OR_PASSWORD -> SignInMessage.WRONG_EMAIL_OR_PASSWORD
    AuthProblem.EMAIL_TAKEN -> SignInMessage.EMAIL_TAKEN
    AuthProblem.WEAK_PASSWORD -> SignInMessage.WEAK_PASSWORD
    AuthProblem.GOOGLE_NOT_ACCEPTED -> SignInMessage.GOOGLE_FAILED
    AuthProblem.OFFLINE -> SignInMessage.OFFLINE
    AuthProblem.TOO_MANY_TRIES -> SignInMessage.TOO_MANY_TRIES
    AuthProblem.UPDATE_REQUIRED -> SignInMessage.UPDATE_REQUIRED
    AuthProblem.SERVER -> SignInMessage.SERVER
}

private fun PasswordRules.Problem.toMessage(): SignInMessage = when (this) {
    PasswordRules.Problem.TOO_SHORT -> SignInMessage.PASSWORD_TOO_SHORT

    PasswordRules.Problem.TOO_LONG -> SignInMessage.PASSWORD_TOO_LONG

    PasswordRules.Problem.SAME_AS_EMAIL -> SignInMessage.PASSWORD_SAME_AS_EMAIL

    // The app has no common-password list; the server answers WEAK_PASSWORD instead.
    PasswordRules.Problem.COMMON -> SignInMessage.WEAK_PASSWORD
}
