package app.ulap.feature.onboarding.meet

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import app.ulap.core.designsystem.component.UlapPrimaryButton
import app.ulap.core.designsystem.mascot.Ulap
import app.ulap.core.designsystem.mascot.UlapExpression
import app.ulap.core.designsystem.mascot.UlapSize
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.ui.R
import app.ulap.feature.onboarding.OnboardingStepLayout
import app.ulap.feature.onboarding.OnboardingTitle

/**
 * Onboarding step 2: meet Ulap (design-spec.md §7.1). Ulap is happy here, and it's decorative:
 * the words carry the meaning, so TalkBack skips the picture. The mascot switch isn't shown yet
 * (it lives in Account), so Ulap always appears on this step.
 */
@Composable
internal fun MeetUlapScreen(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    OnboardingStepLayout(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        actions = {
            UlapPrimaryButton(
                text = stringResource(R.string.onboarding_continue),
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Ulap(expression = UlapExpression.HAPPY, contentDescription = null, size = UlapSize.After)
        OnboardingTitle(text = stringResource(R.string.onboarding_meet_title), textAlign = TextAlign.Center)
        Text(
            text = stringResource(R.string.onboarding_meet_body),
            style = MaterialTheme.typography.bodyLarge,
            color = UlapTheme.colors.ink2,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MeetUlapPreview() {
    UlapTheme {
        MeetUlapScreen(onContinue = {})
    }
}
