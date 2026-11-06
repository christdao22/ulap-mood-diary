package app.ulap.feature.logs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.ulap.core.designsystem.component.ScrollToTopButton
import app.ulap.core.designsystem.motion.rememberReducedMotion
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.ui.R
import kotlinx.coroutines.launch

/**
 * Shows once the list is a few screens down (ScrollToTopButton). Far down, it jumps instead of
 * scrolling through hundreds of rows; it always jumps when the phone asks for reduced motion.
 */
@Composable
internal fun BackToTop(listState: LazyListState, modifier: Modifier = Modifier) {
    val visible by remember { derivedStateOf { listState.firstVisibleItemIndex > SHOW_AFTER_ITEMS } }
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotion()
    AnimatedVisibility(visible = visible, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        ScrollToTopButton(
            onClick = {
                scope.launch {
                    if (reducedMotion || listState.firstVisibleItemIndex > ANIMATE_UP_TO_ITEMS) {
                        listState.scrollToItem(0)
                    } else {
                        listState.animateScrollToItem(0)
                    }
                }
            },
            contentDescription = stringResource(R.string.logs_back_to_top),
            modifier = Modifier.padding(UlapSpacing.ScreenPadding),
        )
    }
}

private const val SHOW_AFTER_ITEMS = 10
private const val ANIMATE_UP_TO_ITEMS = 30
