package app.ulap.feature.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.ulap.core.designsystem.component.UlapPrimaryButton
import app.ulap.core.designsystem.mascot.Ulap
import app.ulap.core.designsystem.mascot.UlapExpression
import app.ulap.core.designsystem.mascot.UlapSize
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.ui.R

// What Logs shows when there's no row to show (design-spec.md §7.6).

/**
 * Before the first check-in: Ulap (neutral) + "Your check-ins will show up here." + primary "Check
 * in now" (design-spec.md §7.6). Ulap is decorative here: the text says it all (§11).
 */
@Composable
internal fun EmptyDiary(showUlap: Boolean, onCheckInNow: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = UlapSpacing.Xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(UlapSpacing.M),
    ) {
        if (showUlap) {
            Ulap(expression = UlapExpression.NEUTRAL, contentDescription = null, size = UlapSize.Greeting)
        }
        Text(
            text = stringResource(R.string.logs_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = UlapTheme.colors.ink,
            textAlign = TextAlign.Center,
        )
        UlapPrimaryButton(
            text = stringResource(R.string.logs_check_in_now),
            onClick = onCheckInNow,
            modifier = Modifier.padding(top = UlapSpacing.Xs),
        )
    }
}

/** "No check-ins match. Try another word or zone." (design-spec.md §7.6). */
@Composable
internal fun NoMatch() {
    Text(
        text = stringResource(R.string.logs_no_match),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = UlapSpacing.Xl),
        style = MaterialTheme.typography.bodyMedium,
        color = UlapTheme.colors.muted,
        textAlign = TextAlign.Center,
    )
}
