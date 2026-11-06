package app.ulap.feature.checkin.response

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import app.ulap.core.designsystem.component.LevelSlider
import app.ulap.core.designsystem.component.UlapTertiaryButton
import app.ulap.core.designsystem.component.UlapTintCard
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.ui.R
import app.ulap.core.ui.wordRes
import app.ulap.shared.model.Zone
import app.ulap.shared.rules.LEVEL_RANGE
import app.ulap.shared.rules.StressLabels

/**
 * "When you're ready" / "How stressed do you feel now?", the stress slider and Check in, at the
 * bottom of the Response screen for Red and Blue (design-spec.md §7.4). Nothing here is required:
 * the person can leave without checking in.
 */
@Composable
internal fun StressCheckCard(
    zone: Zone,
    stress: Int,
    onStressChange: (Int) -> Unit,
    onCheckIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UlapTheme.colors
    UlapTintCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.check_ready),
            style = MaterialTheme.typography.labelMedium,
            color = colors.ink2,
        )
        Text(
            text = stringResource(R.string.check_title),
            style = MaterialTheme.typography.titleMedium,
            color = colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        val stressWord = stringResource(StressLabels.of(stress).wordRes)
        LevelSlider(
            label = stringResource(R.string.slider_stress),
            value = stress,
            onValueChange = onStressChange,
            levelWord = stressWord,
            startLabel = stringResource(R.string.slider_stress_start),
            endLabel = stringResource(R.string.slider_stress_end),
            stateDescription = stringResource(R.string.slider_state, stress, LEVEL_RANGE.last, stressWord),
            zone = zone,
        )
        UlapTertiaryButton(
            text = stringResource(R.string.check_action),
            onClick = onCheckIn,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(widthDp = 360)
@Composable
private fun StressCheckCardPreview() {
    UlapTheme {
        var stress by remember { mutableIntStateOf(8) }
        StressCheckCard(
            zone = Zone.RED,
            stress = stress,
            onStressChange = { stress = it },
            onCheckIn = {},
            modifier = Modifier
                .background(UlapTheme.colors.red.tint)
                .padding(UlapSpacing.ResponseScreenPadding),
        )
    }
}
