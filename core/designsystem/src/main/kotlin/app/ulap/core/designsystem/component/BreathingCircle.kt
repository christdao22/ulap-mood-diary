package app.ulap.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.ulap.core.designsystem.motion.rememberReducedMotion
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
 * How far round the ring is, [millis] into a breath: it fills during "Inhale", stays still
 * during "Hold", then finishes the turn during "Exhale". One full turn is one breath
 * (design-spec.md §6.9).
 */
fun breathProgressAt(millis: Float): Float {
    val t = millis.mod(BREATH_CYCLE_SECONDS * ONE_SECOND_MILLIS)
    val inEnd = BreathPhase.IN.seconds * ONE_SECOND_MILLIS
    val holdEnd = inEnd + BreathPhase.HOLD.seconds * ONE_SECOND_MILLIS
    val moving = (BreathPhase.IN.seconds + BreathPhase.OUT.seconds) * ONE_SECOND_MILLIS
    val movedMillis = when {
        t < inEnd -> t
        t < holdEnd -> inEnd
        else -> inEnd + (t - holdEnd)
    }
    return movedMillis / moving
}

/**
 * The breathing circle (design-spec.md §6.9): a 140dp circle in the Red tint. A Red ring
 * sweeps clockwise from the top around it, one full turn per 12s breath: it moves for the 4s
 * inhale, pauses for the 2s hold, and finishes over the 6s exhale, then starts again (§9).
 *
 * Inside it, the part of the breath ("Inhale", "Hold", "Exhale") changes in step with the ring. The words live in core:ui, so the caller turns each
 * [BreathPhase] into text with [phaseLabel].
 *
 * With "Remove animations" on, the ring moves in one-second steps instead of gliding.
 */
@Composable
fun BreathingCircle(phaseLabel: (BreathPhase) -> String, modifier: Modifier = Modifier) {
    if (rememberReducedMotion()) {
        var elapsedSeconds by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(ONE_SECOND_MILLIS.toLong())
                elapsedSeconds++
            }
        }
        val step = breathStepAt(elapsedSeconds)
        CircleWithStep(
            progress = { breathProgressAt((elapsedSeconds.mod(BREATH_CYCLE_SECONDS) + 1) * ONE_SECOND_MILLIS) },
            step = step,
            phaseLabel = phaseLabel,
            fadeWords = false,
            modifier = modifier,
        )
    } else {
        // One clock drives both the ring and the countdown, so they never drift apart.
        val millis = rememberInfiniteTransition(label = "breathingCircle").animateFloat(
            initialValue = 0f,
            targetValue = BREATH_CYCLE_SECONDS * ONE_SECOND_MILLIS,
            animationSpec = infiniteRepeatable(
                animation = tween(BREATH_CYCLE_SECONDS * ONE_SECOND_MILLIS.toInt(), easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "breathMillis",
        )
        // Recompose the word once a second at most, not every frame.
        val second by remember { derivedStateOf { (millis.value / ONE_SECOND_MILLIS).toInt() } }
        CircleWithStep(
            progress = { breathProgressAt(millis.value) },
            step = breathStepAt(second),
            phaseLabel = phaseLabel,
            fadeWords = true,
            modifier = modifier,
        )
    }
}

/**
 * Draws the filled circle and the ring at [progress] of a full turn, with the step's word in the
 * middle. Reading the progress only while drawing means the ring redraws each
 * frame without recomposing.
 */
@Composable
private fun CircleWithStep(
    progress: () -> Float,
    step: BreathStep,
    phaseLabel: (BreathPhase) -> String,
    fadeWords: Boolean,
    modifier: Modifier = Modifier,
) {
    val red = UlapTheme.colors.red
    Box(modifier.size(CIRCLE_SIZE), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(CIRCLE_SIZE)) {
            val ring = RING_WIDTH.toPx()
            val inset = ring / 2
            drawCircle(color = red.tint, radius = size.minDimension / 2 - ring)
            drawArc(
                color = red.strong,
                startAngle = -90f,
                sweepAngle = 360f * progress(),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - ring, size.height - ring),
                style = Stroke(width = ring, cap = StrokeCap.Round),
            )
        }
        // The old word drifts up and fades out while the new one rises in from below,
        // instead of snapping. With "Remove animations" on it changes straight away.
        AnimatedContent(
            targetState = step.phase,
            transitionSpec = {
                if (fadeWords) {
                    (slideInVertically(tween(WORD_FADE_MILLIS)) { it / 2 } + fadeIn(tween(WORD_FADE_MILLIS)))
                        .togetherWith(
                            slideOutVertically(tween(WORD_FADE_MILLIS)) { -it / 2 } +
                                fadeOut(tween(WORD_FADE_MILLIS)),
                        )
                } else {
                    EnterTransition.None togetherWith ExitTransition.None
                }
            },
            contentAlignment = Alignment.Center,
            label = "breathWord",
        ) { phase ->
            Text(
                text = phaseLabel(phase),
                style = MaterialTheme.typography.titleMedium,
                color = red.text,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private val CIRCLE_SIZE = 140.dp
private val RING_WIDTH = 8.dp
private const val WORD_FADE_MILLIS = 700
private const val ONE_SECOND_MILLIS = 1_000f

@Preview
@Composable
private fun BreathingCirclePreview() {
    UlapTheme {
        BreathingCircle(phaseLabel = { it.name })
    }
}
