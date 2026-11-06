package app.ulap.core.designsystem.mascot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.ulap.core.designsystem.theme.UlapColors
import app.ulap.core.designsystem.theme.UlapTheme

/**
 * One small Ulap face per mood, shown on the pick-mood tiles (design-spec.md §8.7).
 *
 * Each face is built from Ulap's body and cheeks (§8.2) plus eyes, lines (brows and mouths),
 * filled shapes and an optional extra in the empty top corner. Every face is softened the way
 * §8.5 asks: no tears, no shouting mouths, nothing that looks sick.
 *
 * docs/design/mood-faces.html draws the same paths. Change both together.
 *
 * @property moodCode The MoodCatalog code this face belongs to. MoodFaceTest checks that every
 *   mood has exactly one face.
 */
enum class MoodFace(
    val moodCode: String,
    internal val eyes: List<Eye> = emptyList(),
    internal val lines: List<String> = emptyList(),
    internal val fills: List<String> = emptyList(),
    internal val extras: List<MoodFaceExtra> = emptyList(),
) {
    // Red

    /** Startled, one step quieter than wide-eyed alarm: Ulap never panics (§8.5). */
    PANICKED(
        "panicked",
        eyes = eyePair(y = 61f, r = 3.8f),
        lines = listOf(
            "M48 54 L58 51",
            "M74 51 L84 54",
            "M63.5 75.5 A2.5 2.5 0 1 0 68.5 75.5 A2.5 2.5 0 1 0 63.5 75.5",
        ),
    ),

    /** A flat mouth keeps the squeezed eyes from reading as cute ">~<". */
    OVERWHELMED(
        "overwhelmed",
        lines = listOf("M51 59 L58 62 L51 65", "M81 59 L74 62 L81 65", "M60 75 Q66 73.5 72 75"),
    ),
    ANXIOUS(
        "anxious",
        eyes = eyePair(y = 63f, dx = 2f),
        lines = CONCERNED_BROWS + "M61 76 Q66 73 71 76",
        extras = listOf(MoodFaceExtra.DROP),
    ),
    ANGRY(
        "angry",
        eyes = eyePair(y = 62.5f),
        lines = listOf("M47 52 L58 56", "M74 56 L85 52", "M60 76 Q66 73 72 76"),
        extras = listOf(MoodFaceExtra.WISPS),
    ),

    /** Ulap's `concerned` face, unchanged (§8.3). */
    WORRIED("worried", eyes = eyePair(y = 63f), lines = CONCERNED_BROWS + "M60 75 Q66 73 72 75"),
    FRUSTRATED(
        "frustrated",
        lines = listOf("M48 55 L58 57", "M74 57 L84 55", "M60 75.5 L72 74"),
        fills = listOf("M50.5 61.5 L59.5 61.5 Q55 67.5 50.5 61.5 Z", "M72.5 61.5 L81.5 61.5 Q77 67.5 72.5 61.5 Z"),
        extras = listOf(MoodFaceExtra.SCRIBBLE),
    ),
    NERVOUS(
        "nervous",
        eyes = eyePair(y = 62f),
        lines = listOf("M48 55 L58 53", "M74 53 L84 55", "M58 75 L61.2 73 L64.4 75 L67.6 73 L70.8 75 L74 73"),
    ),

    /** The mouth goes sideways and down, an "ugh", never up into a smirk. */
    IRRITATED(
        "irritated",
        eyes = eyePair(y = 62.5f),
        lines = listOf("M48 55.5 L59 55.5", "M73 55.5 L84 55.5", "M61 74.5 Q66 74.5 71 76.5"),
    ),
    RESTLESS(
        "restless",
        eyes = eyePair(y = 60f, dx = 2f),
        lines = listOf("M63 74.5 L71 73.5"),
        extras = listOf(MoodFaceExtra.MOTION),
    ),

    // Blue

    /** Ulap's `quiet` face, unchanged and with no extra, as on the Hopeless response (§8.3, §8.7). */
    HOPELESS("hopeless", eyes = eyePair(y = 62f, r = 3f), lines = listOf("M62 74 L70 74")),
    BURNED_OUT(
        "burned_out",
        lines = listOf("M61 75 L71 75"),
        fills = listOf("M50.5 62 L59.5 62 Q55 67.5 50.5 62 Z", "M72.5 62 L81.5 62 Q77 67.5 72.5 62 Z"),
        extras = listOf(MoodFaceExtra.BATTERY, MoodFaceExtra.BATTERY_BAR),
    ),
    SAD("sad", eyes = eyePair(y = 63f), lines = listOf("M60 77 Q66 72 72 77")),

    /**
     * A small faraway cloud sets it apart from Sad and Hopeless. The eyes glance toward it, but
     * like every glance here that only shows at large sizes; on a tile the cloud does the work.
     */
    LONELY(
        "lonely",
        eyes = eyePair(y = 60.5f, r = 3.3f, dx = -2.5f),
        lines = listOf("M62 76 Q66 73.5 70 76"),
        extras = listOf(MoodFaceExtra.FAR_CLOUD),
    ),

    /** A soft line, not a wobble, so it doesn't read as "caught" or about to cry. */
    GUILTY(
        "guilty",
        eyes = eyePair(y = 65f, r = 3.2f),
        lines = CONCERNED_BROWS + "M61 76 Q66 74.5 71 76",
    ),

    /** A clear gap between lids and eyes, so at tile size it reads as downcast, never as a glare. */
    DISAPPOINTED(
        "disappointed",
        eyes = eyePair(y = 66.5f, r = 2.8f),
        lines = listOf("M50 59.5 L60 57.5", "M72 57.5 L82 59.5", "M61 76.5 Q66 73.5 71 76.5"),
    ),
    TIRED(
        "tired",
        lines = CLOSED_EYES,
        fills = listOf("M63 75.5 A3 3.5 0 1 0 69 75.5 A3 3.5 0 1 0 63 75.5 Z"),
        extras = listOf(MoodFaceExtra.ZZZ),
    ),
    BORED("bored", lines = listOf("M51 62 L59 62", "M73 62 L81 62", "M61 75 L70 75")),

    // Yellow

    /** Ulap's `happy` face, unchanged (§8.3). */
    EXCITED(
        "excited",
        lines = SMILING_EYES,
        fills = listOf(OPEN_SMILE),
        extras = listOf(MoodFaceExtra.SPARKLE_BIG, MoodFaceExtra.SPARKLE_SMALL),
    ),

    // Motivated and Proud avoid grading symbols (arrows, stars): patterns, not grades (§1, §10.2).
    MOTIVATED(
        "motivated",
        eyes = eyePair(y = 62.5f),
        lines = listOf("M48 55 Q53 52 58 54", "M74 54 Q79 52 84 55", "M58 72 Q66 79 74 72"),
        extras = listOf(MoodFaceExtra.SUN_DISC, MoodFaceExtra.SUN_RAYS),
    ),
    PROUD(
        "proud",
        lines = SMILING_EYES + SMILE + listOf("M49 54 Q54 51.5 59 53.5", "M73 53.5 Q78 51.5 83 54"),
    ),
    HAPPY("happy", eyes = eyePair(y = 62f), fills = listOf(OPEN_SMILE)),

    // Green
    CONTENT("content", lines = SMILING_EYES + SMALL_SMILE),
    GRATEFUL("grateful", lines = CLOSED_EYES + SMALL_SMILE, extras = listOf(MoodFaceExtra.HEART)),
    RELAXED("relaxed", lines = CLOSED_EYES + "M59 72 Q66 78 73 72", extras = listOf(MoodFaceExtra.BREEZE)),

    /** Ulap's `calm` face, unchanged (§8.3). */
    CALM(
        "calm",
        lines = CLOSED_EYES + SMILE,
        extras = listOf(MoodFaceExtra.LEAF, MoodFaceExtra.LEAF_VEIN),
    ),
    ;

    internal val linePaths: List<Path> by lazy { lines.map(::svgPath) }
    internal val fillPaths: List<Path> by lazy { fills.map(::svgPath) }

    companion object {
        private val byCode = entries.associateBy { it.moodCode }

        /** The face for a MoodCatalog code, or null if it has none (MoodFaceTest checks none is missing). */
        fun forMood(code: String): MoodFace? = byCode[code]
    }
}

/** One eye dot in viewBox units. */
internal data class Eye(val x: Float, val y: Float, val r: Float)

/** Both eyes at Ulap's usual spots (55 and 77, §8.3), moved [dx] to glance sideways. */
private fun eyePair(y: Float, r: Float = 3.5f, dx: Float = 0f) = listOf(Eye(55f + dx, y, r), Eye(77f + dx, y, r))

// Face parts shared with Ulap's six expressions (design-spec.md §8.3).
private val CONCERNED_BROWS = listOf("M47 55 L58 52", "M74 52 L85 55")
private val CLOSED_EYES = listOf("M50 62 Q55 66 60 62", "M72 62 Q77 66 82 62")
private val SMILING_EYES = listOf("M50 64 Q55 58 60 64", "M72 64 Q77 58 82 64")
private const val SMILE = "M60 72 Q66 77 72 72"
private const val SMALL_SMILE = "M61 73 Q66 76 71 73"
private const val OPEN_SMILE = "M58 70 Q66 82 74 70 Z"

/** Where an extra's color comes from. Every one is an existing token (§8.7). */
internal enum class ExtraInk { HEART, SUN, LEAF, LEAF_VEIN, BODY, FAR_OUTLINE, MUTED }

/**
 * The small drawings in a face's empty top corner. Each has a fill, a line, or both;
 * [strokeWidth] is at full size and is thickened on tiles like the face lines.
 */
internal enum class MoodFaceExtra(
    val svg: String,
    val fill: ExtraInk? = null,
    val stroke: ExtraInk? = null,
    val strokeWidth: Float = 0f,
) {
    // The heart, sparkles and leaf are Ulap.kt's accents (§8.3).
    HEART(
        "M113 29 C105 24 105 17.5 109.5 16.5 C111.5 16 112.8 17.3 113 18.8 C113.2 17.3 114.5 16 116.5 16.5 " +
            "C121 17.5 121 24 113 29 Z",
        fill = ExtraInk.HEART,
    ),
    SPARKLE_BIG("M113 13 Q113 20 120 20 Q113 20 113 27 Q113 20 106 20 Q113 20 113 13 Z", fill = ExtraInk.SUN),
    SPARKLE_SMALL("M19 25 Q19 30 24 30 Q19 30 19 35 Q19 30 14 30 Q19 30 19 25 Z", fill = ExtraInk.SUN),
    LEAF("M104 31 Q105 16 121 13 Q120 29 104 31 Z", fill = ExtraInk.LEAF),
    LEAF_VEIN("M105 30 Q112 22 120 14", stroke = ExtraInk.LEAF_VEIN, strokeWidth = 1.5f),

    /** Grey, not blue: a blue drop by a cloud reads as rain, a crying cloud (§8.5). */
    DROP("M113 12 Q105 23 108.5 27.5 Q113 31.5 117.5 27.5 Q121 23 113 12 Z", fill = ExtraInk.MUTED),

    /** Two rising wisps. Vertical, so they differ from Relaxed's breeze and Restless's motion lines. */
    WISPS(
        "M108 30 Q104 25 108 21 Q112 17 108 12 M117 30 Q113 25 117 21 Q121 17 117 12",
        stroke = ExtraInk.MUTED,
        strokeWidth = 2.2f,
    ),
    SCRIBBLE(
        "M104 24 C107 13 121 13 118 21 C115 29 105 24 110 17 C114 12 123 17 119 25",
        stroke = ExtraInk.MUTED,
        strokeWidth = 2.2f,
    ),
    MOTION("M18 30 Q13 36 18 42 M10 32 Q6 37 10 42", stroke = ExtraInk.MUTED, strokeWidth = 2.2f),
    BATTERY(
        "M103 15 H117 Q119 15 119 17 V24 Q119 26 117 26 H103 Q101 26 101 24 V17 Q101 15 103 15 Z M121.5 18.5 V22.5",
        stroke = ExtraInk.MUTED,
        strokeWidth = 2f,
    ),
    BATTERY_BAR("M104 18 H108 V23 H104 Z", fill = ExtraInk.MUTED),
    FAR_CLOUD(
        "M12 33 A4 4 0 0 1 11 25 A6.5 6.5 0 0 1 23 21.5 A5 5 0 0 1 29 28 A3.5 3.5 0 0 1 27 33 Z",
        fill = ExtraInk.BODY,
        stroke = ExtraInk.FAR_OUTLINE,
        strokeWidth = 1.8f,
    ),
    ZZZ("M104 19 L112 19 L104 27 L112 27 M115 8 L121 8 L115 14 L121 14", stroke = ExtraInk.MUTED, strokeWidth = 2.4f),
    SUN_DISC("M108.5 20 A4.5 4.5 0 1 0 117.5 20 A4.5 4.5 0 1 0 108.5 20 Z", fill = ExtraInk.SUN),
    SUN_RAYS("M113 10 V12.5 M113 27.5 V30 M103 20 H105.5 M120.5 20 H123", stroke = ExtraInk.SUN, strokeWidth = 2.2f),
    BREEZE(
        "M101 18 Q106 14 111 18 Q116 22 121 18 M105 27 Q109 24 113 27 Q117 30 121 27",
        stroke = ExtraInk.LEAF,
        strokeWidth = 2.2f,
    ),
    ;

    val path: Path by lazy { svgPath(svg) }
}

/**
 * [face] drawn [UlapSize.MoodTileFace] wide for a mood tile (design-spec.md §8.7).
 *
 * It's decorative: the tile's sentence already names the mood, so TalkBack skips the face (§11).
 * No shadow and no float. The viewBox is cropped to the cloud so the face draws as big as it can.
 */
@Composable
fun MoodFaceIcon(face: MoodFace, modifier: Modifier = Modifier) {
    val colors = UlapTheme.colors
    Canvas(
        modifier = modifier.size(
            width = UlapSize.MoodTileFace,
            height = UlapSize.MoodTileFace * (CROP_HEIGHT / CROP_WIDTH),
        ),
    ) {
        scale(scale = size.width / CROP_WIDTH, pivot = Offset.Zero) {
            translate(left = -CROP_LEFT, top = -CROP_TOP) {
                drawUlapBody(colors, BodyPath, strokeWidth = BODY_STROKE)
                drawMoodFace(colors, face)
            }
        }
    }
}

private fun DrawScope.drawMoodFace(colors: UlapColors, face: MoodFace) {
    val faceStroke = Stroke(width = FACE_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)
    face.eyes.forEach { drawCircle(colors.ink, radius = it.r + EYE_BOOST, center = Offset(it.x, it.y)) }
    face.linePaths.forEach { drawPath(it, colors.ink, style = faceStroke) }
    face.fillPaths.forEach {
        drawPath(it, colors.ink)
        drawPath(it, colors.ink, style = faceStroke)
    }
    face.extras.forEach { extra ->
        extra.fill?.let { drawPath(extra.path, colors.of(it)) }
        extra.stroke?.let {
            val stroke = Stroke(extra.strokeWidth * EXTRA_STROKE_BOOST, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawPath(extra.path, colors.of(it), style = stroke)
        }
    }
}

private fun UlapColors.of(ink: ExtraInk): Color = when (ink) {
    ExtraInk.HEART -> heart
    ExtraInk.SUN -> yellow.strong
    ExtraInk.LEAF -> leaf
    ExtraInk.LEAF_VEIN -> green.strong
    ExtraInk.BODY -> ulapBody
    ExtraInk.FAR_OUTLINE -> outlineButton
    ExtraInk.MUTED -> placeholder
}

private val BodyPath: Path by lazy { svgPath(ULAP_BODY_PATH) }

// The cropped viewBox: x 2–128, y 6–94 (§8.7).
private const val CROP_LEFT = 2f
private const val CROP_TOP = 6f
private const val CROP_WIDTH = 126f
private const val CROP_HEIGHT = 88f

// Lines drawn thicker than on the big Ulap so they read at 46dp (§8.7).
private const val FACE_STROKE = 4.4f
private const val BODY_STROKE = 3.2f
private const val EYE_BOOST = 0.5f
private const val EXTRA_STROKE_BOOST = 1.35f

private const val PREVIEW_COLUMNS = 6

@Preview(showBackground = true, backgroundColor = 0xFFE8EFFC, widthDp = 360)
@Composable
private fun MoodFacesPreview() {
    UlapTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MoodFace.entries.chunked(PREVIEW_COLUMNS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { MoodFaceIcon(it) } }
            }
        }
    }
}
