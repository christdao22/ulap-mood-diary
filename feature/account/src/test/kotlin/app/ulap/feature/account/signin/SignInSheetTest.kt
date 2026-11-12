package app.ulap.feature.account.signin

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.ulap.core.designsystem.theme.UlapTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class SignInSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val steps = mutableListOf<SignInStep>()
    private var submits = 0
    private var googleTaps = 0

    private fun show(state: SignInUiState, mascotEnabled: Boolean = true) {
        compose.setContent {
            UlapTheme {
                SignInSheetContent(
                    state = state,
                    mascotEnabled = mascotEnabled,
                    actions = SignInActions(
                        onDismiss = {},
                        onContinueWithGoogle = { googleTaps++ },
                        onShowStep = { steps += it },
                        onEmailChange = {},
                        onPasswordChange = {},
                        onSubmit = { submits++ },
                    ),
                )
            }
        }
    }

    @Test
    fun `the first step offers Google and email, gently`() {
        show(SignInUiState(googleAvailable = true))
        compose.onNodeWithText("Back up your diary").assertExists()
        compose.onNodeWithText("Only if you want to. Everything works without an account too.").assertExists()

        compose.onNodeWithText("Continue with Google").performClick()
        compose.onNodeWithText("Use email instead").performClick()

        assertEquals(1, googleTaps)
        assertEquals(listOf(SignInStep.CREATE_ACCOUNT), steps)
    }

    @Test
    fun `create account shows the password hint and can switch to sign in`() {
        show(SignInUiState(googleAvailable = true, step = SignInStep.CREATE_ACCOUNT))
        compose.onNodeWithText("At least 10 characters. A short phrase works well.").assertExists()
        compose.onNodeWithText("Forgot password?").assertDoesNotExist()

        compose.onNodeWithText("Create account").performClick()
        compose.onNodeWithText("Already have an account? Sign in").performClick()

        assertEquals(1, submits)
        assertEquals(listOf(SignInStep.SIGN_IN), steps)
    }

    @Test
    fun `an error replaces the hint under its field`() {
        show(
            SignInUiState(
                googleAvailable = true,
                step = SignInStep.CREATE_ACCOUNT,
                passwordError = SignInMessage.PASSWORD_TOO_SHORT,
                emailError = SignInMessage.EMAIL_TAKEN,
            ),
        )
        compose.onNodeWithText("Use at least 10 characters. A short phrase works well.").assertExists()
        compose.onNodeWithText("There's already an account with this email. Want to sign in instead?").assertExists()
        compose.onNodeWithText("At least 10 characters. A short phrase works well.").assertDoesNotExist()
    }

    @Test
    fun `sign in links to forgot password, and the password can be shown`() {
        show(SignInUiState(googleAvailable = true, step = SignInStep.SIGN_IN, password = "secret"))

        compose.onNodeWithContentDescription("Show password").performClick()
        compose.onNodeWithContentDescription("Hide password").assertExists()
        compose.onNodeWithText("Forgot password?").performClick()

        assertEquals(listOf(SignInStep.FORGOT_PASSWORD), steps)
    }

    @Test
    fun `after a reset request it names the email without saying whether the account exists`() {
        show(SignInUiState(googleAvailable = true, step = SignInStep.RESET_SENT, email = " ana@example.com "))
        compose.onNodeWithText(
            "If there's a Ulap account for ana@example.com, a link is on its way. It works for 30 minutes.",
        ).assertExists()

        compose.onNodeWithText("Back to sign in").performClick()

        assertEquals(listOf(SignInStep.SIGN_IN), steps)
    }

    @Test
    @Config(qualifiers = "fil-w360dp-h800dp")
    fun `the sheet speaks Filipino once Filipino is the app language`() {
        show(SignInUiState(googleAvailable = true))
        compose.onNodeWithText("Magpatuloy gamit ang Google").assertExists()
    }
}
