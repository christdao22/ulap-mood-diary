package app.ulap.feature.onboarding.privacy

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.ulap.core.designsystem.component.UlapPrimaryButton
import app.ulap.core.designsystem.component.UlapTextLink
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.ui.R
import app.ulap.feature.onboarding.OnboardingStepLayout
import app.ulap.feature.onboarding.OnboardingTitle

/** Onboarding step 3 (design-spec.md §7.1), wired to its ViewModel. */
@Composable
internal fun YourPrivacyScreen(
    onStart: () -> Unit,
    onReadPrivacyNotice: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: YourPrivacyViewModel = hiltViewModel(),
) {
    YourPrivacyContent(
        onStart = { viewModel.start(onDone = onStart) },
        onReadPrivacyNotice = onReadPrivacyNotice,
        modifier = modifier,
    )
}

/**
 * Where the diary lives, that Ulap isn't a medical or emergency service, and what starting
 * agrees to. No account screen here: signing in comes later, from Account (§7.1). Recording
 * consent with a timestamp is a Phase 8 task.
 */
@Composable
internal fun YourPrivacyContent(onStart: () -> Unit, onReadPrivacyNotice: () -> Unit, modifier: Modifier = Modifier) {
    OnboardingStepLayout(
        modifier = modifier,
        actions = {
            UlapPrimaryButton(
                text = stringResource(R.string.onboarding_start),
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
            )
            UlapTextLink(text = stringResource(R.string.onboarding_read_privacy), onClick = onReadPrivacyNotice)
        },
    ) {
        OnboardingTitle(stringResource(R.string.onboarding_privacy_title))
        listOf(
            R.string.onboarding_privacy_phone,
            R.string.onboarding_privacy_disclaimer,
            R.string.onboarding_privacy_consent,
        ).forEach { line ->
            Text(
                text = stringResource(line),
                style = MaterialTheme.typography.bodyLarge,
                color = UlapTheme.colors.ink2,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun YourPrivacyPreview() {
    UlapTheme {
        YourPrivacyContent(onStart = {}, onReadPrivacyNotice = {})
    }
}
