package app.ulap.core.designsystem.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Screen transitions (design-spec.md §9): the new screen fades in while it slides up 8dp, in
 * 220ms with standard easing, and the old one fades out. Red and Blue responses are `soft`:
 * slower (280ms), so a heavy moment isn't rushed. With "Remove animations" on
 * ([rememberReducedMotion]) it's a fade only, with no movement.
 *
 * NavHost's transition lambdas aren't composable, so the app root makes this once with
 * [rememberScreenTransitions] and hands it to the screens that need their own.
 */
@Immutable
class ScreenTransitions(private val slidePx: Int, val reducedMotion: Boolean) {
    fun enter(soft: Boolean = false): EnterTransition {
        val fade = fadeIn(spec(soft))
        return if (reducedMotion) fade else fade + slideInVertically(spec(soft)) { slidePx }
    }

    fun exit(soft: Boolean = false): ExitTransition = fadeOut(spec(soft))

    private fun <T> spec(soft: Boolean) =
        tween<T>(if (soft) SOFT_DURATION_MILLIS else DURATION_MILLIS, easing = FastOutSlowInEasing)

    companion object {
        const val DURATION_MILLIS = 220
        const val SOFT_DURATION_MILLIS = 280
    }
}

@Composable
fun rememberScreenTransitions(): ScreenTransitions {
    val slidePx = with(LocalDensity.current) { SlideDistance.roundToPx() }
    val reducedMotion = rememberReducedMotion()
    return remember(slidePx, reducedMotion) { ScreenTransitions(slidePx, reducedMotion) }
}

private val SlideDistance = 8.dp
