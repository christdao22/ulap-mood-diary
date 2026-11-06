package app.ulap.feature.checkin.response

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import app.ulap.core.designsystem.component.BreathingCircle
import app.ulap.core.designsystem.component.UlapSecondaryButton
import app.ulap.core.designsystem.component.UlapTintCard
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.ui.R
import app.ulap.core.ui.SmallHelpsRes
import app.ulap.core.ui.counterRes
import app.ulap.shared.model.SupportType

/**
 * The card that takes the buttons' place after the primary tap (design-spec.md §7.4): the
 * breathing circle (Red), the small helps list (Blue) or "Saved to your good moments" (Yellow and
 * Green). Red and Blue go on to the "How stressed do you feel now?" card; a good moment has no
 * after-check (technical-plan.md §5), so its card ends with Done.
 */
@Composable
internal fun SupportCard(support: SupportType, onFinish: () -> Unit, modifier: Modifier = Modifier) {
    when (support) {
        SupportType.BREATHE -> BreatheCard(modifier)
        SupportType.SMALL_HELPS -> SmallHelpsCard(modifier)
        SupportType.GOOD_MOMENT -> GoodMomentCard(onFinish, modifier)
        SupportType.NONE -> Unit
    }
}

/** The circle, then "In for 4 · hold for 2 · out for 6" and "Follow the circle…" (§6.9). */
@Composable
private fun BreatheCard(modifier: Modifier = Modifier) {
    val colors = UlapTheme.colors
    UlapTintCard(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // counterText isn't a composable, so it can't call stringResource; it reads the words here.
        val resources = LocalResources.current
        BreathingCircle(counterText = { step -> resources.getString(step.phase.counterRes, step.secondsLeft) })
        Text(
            text = stringResource(R.string.help_breathe_steps),
            style = MaterialTheme.typography.titleMedium,
            color = colors.ink,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.help_breathe_sub),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.ink2,
            textAlign = TextAlign.Center,
        )
    }
}

/** A short list of small, quick things, any one of which is enough (§7.4). */
@Composable
private fun SmallHelpsCard(modifier: Modifier = Modifier) {
    val colors = UlapTheme.colors
    UlapTintCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.help_small_title),
            style = MaterialTheme.typography.titleMedium,
            color = colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        SmallHelpsRes.forEach { help -> SmallHelpRow(stringResource(help)) }
        Text(
            text = stringResource(R.string.help_small_sub),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.ink2,
        )
    }
}

/** A Blue dot, then the help. The dot only marks the row, so the words carry the meaning (§11). */
@Composable
private fun SmallHelpRow(text: String) {
    val colors = UlapTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(UlapSpacing.S)) {
        // Centered on the first line of text, which stays put when a long help wraps.
        Box(
            modifier = Modifier
                .padding(top = UlapSpacing.Xs)
                .size(UlapSpacing.Xs)
                .background(colors.blue.strong, CircleShape),
        )
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = colors.ink)
    }
}

/** "Saved to your good moments", then Done, the only way on from here (§7.4). */
@Composable
private fun GoodMomentCard(onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val colors = UlapTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(UlapSpacing.S)) {
        UlapTintCard(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.help_good_saved),
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.help_good_saved_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink2,
                textAlign = TextAlign.Center,
            )
        }
        UlapSecondaryButton(
            text = stringResource(R.string.resp_action_done),
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(widthDp = 360)
@Composable
private fun SupportCardsPreview() {
    UlapTheme {
        Column(
            modifier = Modifier.background(UlapTheme.colors.red.tint).padding(UlapSpacing.ResponseScreenPadding),
            verticalArrangement = Arrangement.spacedBy(UlapSpacing.M),
        ) {
            SupportType.entries.forEach { SupportCard(support = it, onFinish = {}) }
        }
    }
}
