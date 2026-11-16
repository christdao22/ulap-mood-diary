package app.ulap.core.designsystem.motion

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ScreenTransitionsTest {
    private val slidePx = 21

    @Test
    fun `with motion on, a screen does more than fade in`() {
        val enter = ScreenTransitions(slidePx, reducedMotion = false).enter()

        // The slide's offset is a lambda, which has no equality, so check it's not a plain fade.
        assertNotEquals(fadeIn(tween(220, easing = FastOutSlowInEasing)), enter)
    }

    @Test
    fun `with Remove animations on it's a fade only`() {
        val transitions = ScreenTransitions(slidePx, reducedMotion = true)

        assertEquals(fadeIn(tween(220, easing = FastOutSlowInEasing)), transitions.enter())
        assertEquals(fadeIn(tween(280, easing = FastOutSlowInEasing)), transitions.enter(soft = true))
    }

    @Test
    fun `Red and Blue responses are slower`() {
        val transitions = ScreenTransitions(slidePx, reducedMotion = false)

        assertEquals(fadeOut(tween(220, easing = FastOutSlowInEasing)), transitions.exit())
        assertEquals(fadeOut(tween(280, easing = FastOutSlowInEasing)), transitions.exit(soft = true))
    }
}
