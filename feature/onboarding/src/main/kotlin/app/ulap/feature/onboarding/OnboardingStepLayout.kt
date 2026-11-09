package app.ulap.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme

/**
 * The frame every onboarding step shares (design-spec.md §7.1): the step's [content] at the top,
 * its [actions] at the bottom. It scrolls as one, so long Filipino or Bisaya text at 200% font
 * size still reaches the buttons (§10.5, §11). Onboarding has no tab bar, so it pads itself
 * clear of the system bars.
 */
@Composable
internal fun OnboardingStepLayout(
    actions: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(UlapTheme.colors.bg)
            .safeDrawingPadding(),
    ) {
        // At least the screen's height, so the actions sit at the bottom on a tall phone; taller
        // when the text needs it, and then the whole step scrolls.
        val screenHeight = maxHeight
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = screenHeight)
                .padding(UlapSpacing.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(UlapSpacing.M),
            horizontalAlignment = horizontalAlignment,
        ) {
            Spacer(Modifier.height(UlapSpacing.Xl))
            content()
            Spacer(Modifier.weight(1f))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(UlapSpacing.Xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = actions,
            )
        }
    }
}

/** A step's title, read by TalkBack as a heading. */
@Composable
internal fun OnboardingTitle(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        color = UlapTheme.colors.ink,
        textAlign = textAlign,
        modifier = modifier.semantics { heading() },
    )
}
