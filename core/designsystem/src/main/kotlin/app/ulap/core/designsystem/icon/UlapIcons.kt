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

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 2 * r, dy1 = 0f)
        arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = -2 * r, dy1 = 0f)
        close()
    }
}
