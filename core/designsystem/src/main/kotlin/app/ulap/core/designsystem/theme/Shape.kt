package app.ulap.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Corner radii from design-spec.md §4 on Material's slots, following §12:
 * small = `radiusS` (icon buttons, small inner cards), medium = `radiusM` (mood tiles, inputs),
 * large and extraLarge = the two ends of `radiusXL` (cards, the mood header, support cards).
 */
val UlapShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(18.dp),
)

/** The radii Material has no slot for (design-spec.md §4). */
object UlapShape {
    /** `radiusL`: buttons, stat tiles, log rows. Material's buttons default to a pill, so pass this. */
    val Button: Shape = RoundedCornerShape(14.dp)

    /** `radiusBubble`: the speech bubble's bottom-left corner points at Ulap. */
    val Bubble: Shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomEnd = 18.dp,
        bottomStart = 4.dp,
    )

    /** `radiusPill`: chips, badges, the switch and bar tracks. */
    val Pill: Shape = CircleShape
}
