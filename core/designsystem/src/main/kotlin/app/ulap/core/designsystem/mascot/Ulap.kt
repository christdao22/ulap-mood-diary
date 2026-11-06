package app.ulap.core.designsystem.mascot

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ulap.core.designsystem.motion.rememberReducedMotion
import app.ulap.core.designsystem.theme.UlapColors
import app.ulap.core.designsystem.theme.UlapTheme

/** Ulap's six faces and where each one is used (design-spec.md §8.3). */
enum class UlapExpression(internal val floats: Boolean = true) {
    /** Pick-mood greeting, empty states. */
    NEUTRAL,

    /** Red zone: concerned but calm. */
    CONCERNED,

    /** Blue zone, and the after-check when stress stayed the same or went up. */
    GENTLE,

    /** Yellow zone. */
    HAPPY,

    /** Green zone, reports, and the after-check when stress went down. */
    CALM,

    /** Hopeless. Ulap stays still: it never floats (§8.3). */
    QUIET(floats = false),
}

/** Ulap's width in each place it appears (design-spec.md §8.4). */
object UlapSize {
    val ReportBubble = 64.dp
    val DetailsHeader = 82.dp
    val Greeting = 88.dp

    /** A mood face on a pick-mood tile (§8.7). */
    val MoodTileFace = 46.dp
    val After = 150.dp
    val Response = 168.dp
}

/**
 * Ulap the cloud (design-spec.md §8), drawn from the spec's vector paths so it stays sharp at
 * every [size]. [size] is the width; the height follows the 130 × 110 viewBox.
 *
 * Ulap floats gently (§9) unless [float] is false, the expression is [UlapExpression.QUIET], or
 * the system's "Remove animations" setting is on. Only the body moves; the shadow stays put.
 *
 * [contentDescription] is read by TalkBack, e.g. "Ulap the cloud, looking concerned but calm"
 * (§8.5). Pass null when a speech bubble next to Ulap already says the same thing, so TalkBack
 * skips the picture (§11). The text lives in core:ui's strings, so callers pass it in.
 */
@Composable
fun Ulap(
    expression: UlapExpression,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = UlapSize.Greeting,
    float: Boolean = true,
) {
    val colors = UlapTheme.colors
    val face = remember(expression) { UlapFace.of(expression) }
    val floats = float && expression.floats && !rememberReducedMotion()
    val lift = if (floats) rememberFloatLift() else remember { mutableFloatStateOf(0f) }

    val semantics = if (contentDescription == null) {
        Modifier
    } else {
        Modifier.semantics {
            this.contentDescription = contentDescription
            role = Role.Image
        }
    }
    Canvas(
        modifier = modifier
            .size(width = size, height = size * (VIEWBOX_HEIGHT / VIEWBOX_WIDTH))
            .then(semantics),
    ) {
        val unit = this.size.width / VIEWBOX_WIDTH
        drawShadow(colors, unit)
        // The float is in dp (§9), not in viewBox units, so it's the same lift at every size.
        translate(top = -FLOAT_HEIGHT.toPx() * lift.value) {
            scale(scale = unit, pivot = Offset.Zero) {
                drawUlapBody(colors, face.body)
                drawFace(colors, face)
            }
        }
    }
}

/** 0 → 1 → 0 on a loop: one way is half of the 3.6s cycle, eased at both ends (§9). */
@Composable
private fun rememberFloatLift(): State<Float> = rememberInfiniteTransition(label = "ulapFloat").animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = FLOAT_HALF_CYCLE_MILLIS, easing = EaseInOut),
        repeatMode = RepeatMode.Reverse,
    ),
    label = "ulapLift",
)

private const val VIEWBOX_WIDTH = 130f
private const val VIEWBOX_HEIGHT = 110f
private val FLOAT_HEIGHT = 5.dp
private const val FLOAT_HALF_CYCLE_MILLIS = 1800

/** The shadow is drawn in pixels outside the scaled body, so it doesn't float (§8.2). */
private fun DrawScope.drawShadow(colors: UlapColors, unit: Float) {
    drawOval(
        color = colors.ink.copy(alpha = 0.08f),
        topLeft = Offset((66f - 38f) * unit, (100f - 5f) * unit),
        size = Size(76f * unit, 10f * unit),
    )
}

/** Ulap's cloud outline in viewBox units (design-spec.md §8.2). The mood faces use it too. */
internal const val ULAP_BODY_PATH = "M30 90 A22 22 0 0 1 24 47 A30 30 0 0 1 80 30 A26 26 0 0 1 112 60 " +
    "A20 20 0 0 1 104 90 Z"

/** The white cloud, its outline and both cheeks, in viewBox units (§8.2). */
internal fun DrawScope.drawUlapBody(colors: UlapColors, body: Path, strokeWidth: Float = 2.5f) {
    drawPath(body, colors.ulapBody)
    drawPath(body, colors.ulapStroke, style = Stroke(width = strokeWidth, join = StrokeJoin.Round))
    drawCircle(colors.cheek, radius = 5f, center = Offset(47f, 71f))
    drawCircle(colors.cheek, radius = 5f, center = Offset(85f, 71f))
}

/** Parses one SVG path string (viewBox units) into a Compose [Path]. */
internal fun svgPath(svg: String): Path = PathParser().parsePathString(svg).toPath()

private fun DrawScope.drawFace(colors: UlapColors, face: UlapFace) {
    val faceStroke = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    face.eyeDots.forEach { drawCircle(colors.ink, radius = face.eyeRadius, center = it) }
    face.strokes.forEach { drawPath(it, colors.ink, style = faceStroke) }
    face.fills.forEach {
        drawPath(it, colors.ink)
        drawPath(it, colors.ink, style = faceStroke)
    }
    when (face.expression) {
        UlapExpression.GENTLE -> drawPath(face.accent.single(), colors.heart)

        UlapExpression.HAPPY -> face.accent.forEach { drawPath(it, colors.yellow.strong) }

        UlapExpression.CALM -> {
            drawPath(face.accent[0], colors.leaf)
            drawPath(
                face.accent[1],
                colors.green.strong,
                style = Stroke(width = 1.5f, cap = StrokeCap.Round),
            )
        }

        else -> Unit
    }
}

/** One expression's shapes in viewBox units (design-spec.md §8.2–§8.3). */
private class UlapFace(
    val expression: UlapExpression,
    val eyeDots: List<Offset> = emptyList(),
    val eyeRadius: Float = 3.5f,
    strokeSvg: List<String> = emptyList(),
    fillSvg: List<String> = emptyList(),
    accentSvg: List<String> = emptyList(),
) {
    val body = path(ULAP_BODY_PATH)

    /** Eyes, brows and mouths drawn as ink lines. */
    val strokes = strokeSvg.map(::path)

    /** Ink shapes filled in, like the happy open smile. */
    val fills = fillSvg.map(::path)

    /** The heart, sparkles or leaf, in their own colors. */
    val accent = accentSvg.map(::path)

    companion object {
        private const val SMILE = "M60 72 Q66 77 72 72"
        private val CLOSED_EYES = listOf("M50 62 Q55 66 60 62", "M72 62 Q77 66 82 62")

        // The spec names the accents and their colors but gives no paths. These are drawn to sit
        // in the empty top-right corner (and, for a sparkle, the top-left), clear of the body.
        private const val HEART = "M113 29 C105 24 105 17.5 109.5 16.5 C111.5 16 112.8 17.3 113 18.8 " +
            "C113.2 17.3 114.5 16 116.5 16.5 C121 17.5 121 24 113 29 Z"
        private const val SPARKLE_BIG = "M113 13 Q113 20 120 20 Q113 20 113 27 Q113 20 106 20 Q113 20 113 13 Z"
        private const val SPARKLE_SMALL = "M19 25 Q19 30 24 30 Q19 30 19 35 Q19 30 14 30 Q19 30 19 25 Z"
        private const val LEAF = "M104 31 Q105 16 121 13 Q120 29 104 31 Z"
        private const val LEAF_VEIN = "M105 30 Q112 22 120 14"

        fun of(expression: UlapExpression): UlapFace = when (expression) {
            UlapExpression.NEUTRAL -> UlapFace(
                expression,
                eyeDots = listOf(Offset(55f, 62f), Offset(77f, 62f)),
                strokeSvg = listOf(SMILE),
            )

            UlapExpression.CONCERNED -> UlapFace(
                expression,
                eyeDots = listOf(Offset(55f, 63f), Offset(77f, 63f)),
                strokeSvg = listOf("M47 55 L58 52", "M74 52 L85 55", "M60 75 Q66 73 72 75"),
            )

            UlapExpression.GENTLE -> UlapFace(
                expression,
                strokeSvg = CLOSED_EYES + "M61 73 Q66 76 71 73",
                accentSvg = listOf(HEART),
            )

            UlapExpression.HAPPY -> UlapFace(
                expression,
                strokeSvg = listOf("M50 64 Q55 58 60 64", "M72 64 Q77 58 82 64"),
                fillSvg = listOf("M58 70 Q66 82 74 70 Z"),
                accentSvg = listOf(SPARKLE_BIG, SPARKLE_SMALL),
            )

            UlapExpression.CALM -> UlapFace(
                expression,
                strokeSvg = CLOSED_EYES + SMILE,
                accentSvg = listOf(LEAF, LEAF_VEIN),
            )

            UlapExpression.QUIET -> UlapFace(
                expression,
                eyeDots = listOf(Offset(55f, 62f), Offset(77f, 62f)),
                eyeRadius = 3f,
                strokeSvg = listOf("M62 74 L70 74"),
            )
        }

        private fun path(svg: String): Path = svgPath(svg)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F8)
@Composable
private fun UlapExpressionsPreview() {
    UlapTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UlapExpression.entries.forEach { Ulap(it, contentDescription = null, float = false) }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFCECEA)
@Composable
private fun UlapResponsePreview() {
    UlapTheme {
        Ulap(UlapExpression.CONCERNED, contentDescription = null, size = UlapSize.Response)
    }
}
