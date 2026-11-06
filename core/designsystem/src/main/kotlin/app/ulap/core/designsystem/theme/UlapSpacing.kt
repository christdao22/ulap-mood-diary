package app.ulap.core.designsystem.theme

import androidx.compose.ui.unit.dp

/**
 * The spacing scale and layout sizes from design-spec.md §4, so screens never hard-code a dp
 * value (CLAUDE.md). Steps are named by size; the layout values say where they go.
 */
object UlapSpacing {
    val Xxs = 4.dp
    val Xs = 8.dp
    val S = 12.dp
    val M = 16.dp
    val L = 20.dp
    val Xl = 24.dp
    val Xxl = 32.dp
    val Xxxl = 48.dp

    /** Side padding on most screens. */
    val ScreenPadding = M

    /** Side padding on the response screens. */
    val ResponseScreenPadding = L

    /** Between cards. */
    val CardGap = M

    /** Inside a card. The spec allows 12–16; 16 unless the card is dense. */
    val CardPadding = M

    /** Inside a card on a zone tint, e.g. the response screen's support cards (16–20, §6.7). */
    val TintCardPadding = L

    /** Inside the NCMH hotline card (design-spec.md §6.8). Off the 4dp scale on purpose: the spec says 18. */
    val HotlineCardPadding = 18.dp

    /** Between tiles in the mood grid. */
    val MoodGridGap = Xs

    /** The 1dp `line` border that separates surfaces instead of a shadow. */
    val Border = 1.dp
}
