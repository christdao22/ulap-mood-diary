package app.ulap.feature.onboarding.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import app.ulap.core.designsystem.component.UlapIconButton
import app.ulap.core.designsystem.icon.UlapIcons
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.ui.R

/** One part of the notice: a heading and its paragraph. */
private data class NoticeSection(val title: Int, val body: Int)

private val Sections = listOf(
    NoticeSection(R.string.privacy_collect_title, R.string.privacy_collect_body),
    NoticeSection(R.string.privacy_why_title, R.string.privacy_why_body),
    NoticeSection(R.string.privacy_where_title, R.string.privacy_where_body),
    NoticeSection(R.string.privacy_never_title, R.string.privacy_never_body),
    NoticeSection(R.string.privacy_delete_title, R.string.privacy_delete_body),
)

/**
 * The privacy notice, opened from onboarding step 3 and later from Account (design-spec.md §7.1,
 * §7.8). The text is a plain first draft built from README §14; the written notice (what is
 * collected, why, how long, deletion, contact) replaces it in Phase 8. It's text only, so it
 * works offline.
 */
@Composable
internal fun PrivacyNoticeScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(UlapTheme.colors.bg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(UlapSpacing.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(UlapSpacing.M),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(UlapSpacing.S),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UlapIconButton(onClick = onBack, contentDescription = stringResource(R.string.privacy_back)) {
                Icon(imageVector = UlapIcons.Back, contentDescription = null)
            }
            Text(
                text = stringResource(R.string.privacy_title),
                style = MaterialTheme.typography.headlineSmall,
                color = UlapTheme.colors.ink,
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(
            text = stringResource(R.string.privacy_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = UlapTheme.colors.ink2,
        )
        Sections.forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(UlapSpacing.Xxs)) {
                Text(
                    text = stringResource(section.title),
                    style = MaterialTheme.typography.titleMedium,
                    color = UlapTheme.colors.ink,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(section.body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = UlapTheme.colors.ink2,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PrivacyNoticePreview() {
    UlapTheme {
        PrivacyNoticeScreen(onBack = {})
    }
}
