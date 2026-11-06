package app.ulap.core.designsystem.component

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.ulap.core.designsystem.motion.rememberReducedMotion
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme
import kotlinx.coroutines.delay

/** One part of a breath in the breathing circle, and how long it lasts (design-spec.md §6.9). */
enum class BreathPhase(val seconds: Int) {
    IN(4),
    HOLD(2),
    OUT(6),
}

/** Where a breath is: its [phase], and the whole seconds left in it (counts down to 1). */
data class BreathStep(val phase: BreathPhase, val secondsLeft: Int)

/** One breath: in 4 + hold 2 + out 6 = 12 seconds. */
val BREATH_CYCLE_SECONDS: Int = BreathPhase.entries.sumOf { it.seconds }

/** The step [elapsedSeconds] into the exercise. The cycle repeats, so any count works. */
fun breathStepAt(elapsedSeconds: Int): BreathStep {
    val second = elapsedSeconds.mod(BREATH_CYCLE_SECONDS)
    val holdStart = BreathPhase.IN.seconds
    val outStart = holdStart + BreathPhase.HOLD.seconds
    return when {
        second < holdStart -> BreathStep(BreathPhase.IN, holdStart - second)
        second < outStart -> BreathStep(BreathPhase.HOLD, outStart - second)
        else -> BreathStep(BreathPhase.OUT, BREATH_CYCLE_SECONDS - second)
    }
}

/**
 * The breathing circle (design-spec.md §6.9): a 140dp circle in the Red tint with a 3dp Red
 * border that grows for 4s, holds for 2s and shrinks for 6s, on a loop (§9).
 *
 * With "Remove animations" on, the circle stays still and a counter under it does the pacing
 * instead, e.g. "Breathe in · 3". The words live in core:ui, so the caller turns each
 * [BreathStep] into text with [counterText].
 *
 * The circle itself is decorative. The caller's text under it says what to do.
 */
@Composable
fun BreathingCircle(counterText: (BreathStep) -> String, modifier: Modifier = Modifier) {
    val reducedMotion = rememberReducedMotion()
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(UlapSpacing.S),
    ) {
        if (reducedMotion) {
            Circle(scale = { FULL_SCALE })
            BreathCounter(counterText)
        } else {
            val scale = rememberBreathScale()
            Circle(scale = { scale.value })
        }
    }
}

/**
 * Draws at [scale] of the full size. Reading the scale only while drawing means the animation
 * redraws the circle each frame without recomposing anything. The border keeps its 3dp width.
 */
@Composable
private fun Circle(scale: () -> Float) {
    val red = UlapTheme.colors.red
    Canvas(Modifier.size(CIRCLE_SIZE)) {
        val border = BORDER_WIDTH.toPx()
        val radius = size.minDimension / 2 * scale() - border / 2
        drawCircle(color = red.tint, radius = radius)
        drawCircle(color = red.strong, radius = radius, style = Stroke(width = border))
    }
}

/** Counts each phase down once a second, e.g. "Breathe in · 4", "· 3" … then "Hold · 2". */
@Composable
private fun BreathCounter(counterText: (BreathStep) -> String) {
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(ONE_SECOND_MILLIS)
            elapsedSeconds++
        }
    }
    Text(
        text = counterText(breathStepAt(elapsedSeconds)),
        style = MaterialTheme.typography.titleMedium,
        color = UlapTheme.colors.red.text,
        textAlign = TextAlign.Center,
    )
}

/** 0.6 → 1.0 over the in-breath, held, then back to 0.6 over the out-breath (§9). */
@Composable
private fun rememberBreathScale(): State<Float> {
    val inEnd = BreathPhase.IN.seconds * ONE_SECOND_MILLIS.toInt()
    val holdEnd = inEnd + BreathPhase.HOLD.seconds * ONE_SECOND_MILLIS.toInt()
    return rememberInfiniteTransition(label = "breathingCircle").animateFloat(
        initialValue = SMALL_SCALE,
        targetValue = SMALL_SCALE,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = BREATH_CYCLE_SECONDS * ONE_SECOND_MILLIS.toInt()
                SMALL_SCALE at 0 using EaseInOut
                FULL_SCALE at inEnd
                FULL_SCALE at holdEnd using EaseInOut
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "breathScale",
    )
}

private val CIRCLE_SIZE = 140.dp
private val BORDER_WIDTH = 3.dp
private const val SMALL_SCALE = 0.6f
private const val FULL_SCALE = 1f
private const val ONE_SECOND_MILLIS = 1_000L

@Preview
@Composable
private fun BreathingCirclePreview() {
    UlapTheme {
        BreathingCircle(counterText = { "${it.phase} · ${it.secondsLeft}" })
    }
}
