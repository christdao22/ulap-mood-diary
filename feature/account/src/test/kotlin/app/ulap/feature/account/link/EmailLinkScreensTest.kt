package app.ulap.feature.account.link

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.ulap.core.composetesting.LargestFontScale
import app.ulap.core.composetesting.assertNoTextCutOff
import app.ulap.core.composetesting.assertTouchTargetsAtLeast48dp
import app.ulap.core.composetesting.assertTouchTargetsHaveNames
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.feature.account.signin.SignInMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class EmailLinkScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val typed = mutableListOf<String>()
    private val taps = mutableListOf<String>()

    private fun showReset(state: ResetUiState, largeText: Boolean = false) {
        compose.setContent {
            LargestFontScale(enabled = largeText) {
                UlapTheme {
                    ResetPasswordContent(
                        state = state,
                        onPasswordChange = { typed += it },
                        onSubmit = { taps += "submit" },
                        onSignIn = { taps += "signIn" },
                        onAskForNewLink = { taps += "newLink" },
                        onBack = { taps += "back" },
                    )
                }
            }
        }
    }

    private fun showVerify(state: VerifyState, largeText: Boolean = false) {
        compose.setContent {
            LargestFontScale(enabled = largeText) {
                UlapTheme {
                    VerifyEmailContent(
                        state = state,
                        onRetry = { taps += "retry" },
                        onGoToAccount = { taps += "account" },
                        onBack = { taps += "back" },
                    )
                }
            }
        }
    }

    @Test
    fun `the reset form says every phone signs out, then saves`() {
        showReset(ResetUiState())

        compose.onNodeWithText(
            "Saving it signs you out on every phone. Your check-ins stay saved, and you can sign in with the new one.",
        )
            .assertExists()
        compose.onNodeWithText("New password").performTextInput("Bag-ong-Password-2026")
        compose.onNodeWithText("Save new password").performClick()

        assertEquals(listOf("Bag-ong-Password-2026"), typed)
        assertEquals(listOf("submit"), taps)
    }

    @Test
    fun `a weak password shows under the field`() {
        showReset(ResetUiState(passwordError = SignInMessage.WEAK_PASSWORD))

        compose.onNodeWithText("That password is easy to guess. Try a longer phrase.").assertExists()
    }

    @Test
    fun `after a reset the next step is signing in`() {
        showReset(ResetUiState(step = ResetStep.DONE))

        compose.onNodeWithText("Your new password is set").assertExists()
        compose.onNodeWithText("Sign in").performClick()

        assertEquals(listOf("signIn"), taps)
    }

    @Test
    fun `a spent reset link offers a new one`() {
        showReset(ResetUiState(step = ResetStep.EXPIRED))

        compose.onNodeWithText("This link doesn't work anymore").assertExists()
        compose.onNodeWithText("Send a new link").performClick()

        assertEquals(listOf("newLink"), taps)
    }

    @Test
    fun `a confirmed email leads back to Account`() {
        showVerify(VerifyState.Verified)

        compose.onNodeWithText("Your email is confirmed").assertExists()
        compose.onNodeWithText("Go to Account").performClick()

        assertEquals(listOf("account"), taps)
    }

    @Test
    fun `offline can try again or leave it for now`() {
        showVerify(VerifyState.Failed(SignInMessage.OFFLINE))

        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Not now").performClick()

        assertEquals(listOf("retry", "back"), taps)
    }

    // Accessibility (design-spec.md §11). Native graphics measure real fonts.

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+h3000dp")
    fun `every touch target is at least 48dp and has a name on the reset form`() {
        showReset(ResetUiState(passwordError = SignInMessage.WEAK_PASSWORD))
        compose.assertTouchTargetsAtLeast48dp()
        compose.assertTouchTargetsHaveNames()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+h3000dp")
    fun `every touch target is at least 48dp and has a name when the link fails`() {
        showVerify(VerifyState.Failed(SignInMessage.OFFLINE))
        compose.assertTouchTargetsAtLeast48dp()
        compose.assertTouchTargetsHaveNames()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+en")
    fun `nothing is cut off at 200 percent text in English on the reset form`() {
        showReset(ResetUiState(passwordError = SignInMessage.WEAK_PASSWORD), largeText = true)
        compose.assertNoTextCutOff()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+fil")
    fun `nothing is cut off at 200 percent text in Filipino on the reset form`() {
        showReset(ResetUiState(passwordError = SignInMessage.WEAK_PASSWORD), largeText = true)
        compose.assertNoTextCutOff()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+b+ceb")
    fun `nothing is cut off at 200 percent text in Bisaya on the reset form`() {
        showReset(ResetUiState(passwordError = SignInMessage.WEAK_PASSWORD), largeText = true)
        compose.assertNoTextCutOff()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+en")
    fun `nothing is cut off at 200 percent text in English when the link fails`() {
        showVerify(VerifyState.Failed(SignInMessage.OFFLINE), largeText = true)
        compose.assertNoTextCutOff()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+fil")
    fun `nothing is cut off at 200 percent text in Filipino when the link fails`() {
        showVerify(VerifyState.Failed(SignInMessage.OFFLINE), largeText = true)
        compose.assertNoTextCutOff()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+b+ceb")
    fun `nothing is cut off at 200 percent text in Bisaya when the link fails`() {
        showVerify(VerifyState.Failed(SignInMessage.OFFLINE), largeText = true)
        compose.assertNoTextCutOff()
    }
}
