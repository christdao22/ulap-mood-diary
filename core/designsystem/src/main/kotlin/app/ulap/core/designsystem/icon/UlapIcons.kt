package app.ulap.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Ulap's line icons (design-spec.md §5): a 1.8dp stroke with round caps and joins, no fill.
 * Drawn on a 22-unit grid, the tab bar size, so one unit is one dp there. `Icon` tints the stroke.
 *
 * The four tab icons follow §5's descriptions; the spec gives no paths, so these shapes are the
 * reference until a designer replaces them.
 */
object UlapIcons {
    /** Plus in a circle. */
    val CheckIn: ImageVector by lazy {
        lineIcon("CheckIn") {
            circle(cx = 11f, cy = 11f, r = 8.5f)
            moveTo(11f, 7.5f)
            verticalLineTo(14.5f)
            moveTo(7.5f, 11f)
            horizontalLineTo(14.5f)
        }
    }

    /** Three lines, each with a dot in front. */
    val Logs: ImageVector by lazy {
        lineIcon("Logs") {
            listOf(6f, 11f, 16f).forEach { y ->
                // A zero-length stroke with round caps draws a dot as wide as the stroke.
                moveTo(4f, y)
                horizontalLineTo(4.01f)
                moveTo(8f, y)
                horizontalLineTo(18.5f)
            }
        }
    }

    /** Four vertical bars of different heights. */
    val Reports: ImageVector by lazy {
        lineIcon("Reports") {
            listOf(4.5f to 13f, 9f to 6f, 13.5f to 10f, 18f to 4f).forEach { (x, top) ->
                moveTo(x, BASELINE)
                verticalLineTo(top)
            }
        }
    }

    /** A head and shoulders. */
    val Account: ImageVector by lazy {
        lineIcon("Account") {
            circle(cx = 11f, cy = 7.5f, r = 3.5f)
            moveTo(4.5f, 18.5f)
            curveToRelative(0f, -3.6f, 2.9f, -6f, 6.5f, -6f)
            reflectiveCurveToRelative(6.5f, 2.4f, 6.5f, 6f)
        }
    }

    /** A left chevron, for the back button on Details (design-spec.md §7.3). */
    val Back: ImageVector by lazy {
        lineIcon("Back") {
            moveTo(13.5f, 5f)
            lineTo(7.5f, 11f)
            lineTo(13.5f, 17f)
        }
    }

    /** A right chevron: the Reports "next period" button (design-spec.md §7.7) and settings rows (§7.8). */
    val Forward: ImageVector by lazy {
        lineIcon("Forward") {
            moveTo(8.5f, 5f)
            lineTo(14.5f, 11f)
            lineTo(8.5f, 17f)
        }
    }

    /** A right arrow, between the "Before" and "Now" pills on the After screen (design-spec.md §7.5). */
    val Arrow: ImageVector by lazy {
        lineIcon("Arrow") {
            moveTo(4f, 11f)
            lineTo(18f, 11f)
            moveTo(13f, 6f)
            lineTo(18f, 11f)
            lineTo(13f, 16f)
        }
    }

    /** An up arrow, for Logs' back-to-top button (design-spec.md §7.6). */
    val Up: ImageVector by lazy {
        lineIcon("Up") {
            moveTo(11f, 18f)
            lineTo(11f, 4f)
            moveTo(6f, 9f)
            lineTo(11f, 4f)
            lineTo(16f, 9f)
        }
    }

    /** A magnifying glass, at the start of the Logs search field (design-spec.md §7.6). */
    val Search: ImageVector by lazy {
        lineIcon("Search") {
            circle(cx = 9.5f, cy = 9.5f, r = 5.5f)
            moveTo(13.5f, 13.5f)
            lineTo(18f, 18f)
        }
    }

    /** An X, to clear the search field. */
    val Clear: ImageVector by lazy {
        lineIcon("Clear") {
            moveTo(6f, 6f)
            lineTo(16f, 16f)
            moveTo(16f, 6f)
            lineTo(6f, 16f)
        }
    }

    /** Three dots stacked up, for a log row's overflow menu (design-spec.md §6.13). */
    val More: ImageVector by lazy {
        lineIcon("More") {
            // Small rings: the stroke fills them in, so each dot is about 4dp wide, easy to see.
            listOf(5f, 11f, 17f).forEach { y -> circle(cx = 11f, cy = y, r = 1f) }
        }
    }

    /** An open eye: "Show password" on a password field (design-spec.md §7.9). */
    val Show: ImageVector by lazy {
        lineIcon("Show") {
            eye()
        }
    }

    /** The eye struck through: "Hide password". */
    val Hide: ImageVector by lazy {
        lineIcon("Hide") {
            eye()
            moveTo(4f, 4f)
            lineTo(18f, 18f)
        }
    }

    /**
     * An "i" in a circle, beside an error under a field. Errors are ink with this icon,
     * never Red, so a typo doesn't look like an alarm (design-spec.md §7.9) and color is never
     * the only signal (§11).
     */
    val Notice: ImageVector by lazy {
        lineIcon("Notice") {
            circle(cx = 11f, cy = 11f, r = 8f)
            // A stroke this short, with round caps, draws as a dot.
            moveTo(11f, 7.4f)
            lineTo(11f, 7.5f)
            moveTo(11f, 10.5f)
            lineTo(11f, 15f)
        }
    }

    private const val GRID = 22f
    private const val STROKE = 1.8f
    private const val BASELINE = 18f

    private fun lineIcon(name: String, pathBuilder: PathBuilder.() -> Unit): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = GRID.dp,
        defaultHeight = GRID.dp,
        viewportWidth = GRID,
        viewportHeight = GRID,
    ).path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = pathBuilder,
    ).build()

    /** An almond outline with the pupil in the middle, shared by [Show] and [Hide]. */
    private val eye: PathBuilder.() -> Unit = {
        moveTo(2f, 11f)
        quadTo(11f, 2f, 20f, 11f)
        quadTo(11f, 20f, 2f, 11f)
        close()
        circle(cx = 11f, cy = 11f, r = 2.75f)
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 2 * r, dy1 = 0f)
        arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = -2 * r, dy1 = 0f)
        close()
    }
}
