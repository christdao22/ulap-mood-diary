package app.ulap.core.composetesting

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density

// Large-text checks for screen tests (design-spec.md §11). The touch target checks are in TouchTargetChecks.kt.

/** The largest font scale Ulap supports without clipping (design-spec.md §11). */
const val MAX_FONT_SCALE = 2f

/**
 * Shows [content] with every text size doubled, the "Largest" font size at 200%
 * (design-spec.md §11). Pass `largeText` through a screen test's `show()` helper.
 *
 * It scales linearly, as Android 8–13 do. Robolectric's `@Config(fontScale = …)` runs on
 * Android 14+, which grows big text less than small text, so it would hide the worst case.
 */
@Composable
fun LargestFontScale(enabled: Boolean = true, content: @Composable () -> Unit) {
    if (!enabled) return content()
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, MAX_FONT_SCALE), content = content)
}

/**
 * Fails if any text on the current screen is cut off: lines dropped by `maxLines`, an ellipsis,
 * or a fixed-height parent too short for it. Show the screen inside [LargestFontScale] to check
 * font scaling at 200% (design-spec.md §11), and run the test with
 * `@GraphicsMode(GraphicsMode.Mode.NATIVE)`: Robolectric's default graphics don't measure fonts.
 *
 * It checks height only. Wrapping text can't run out of width, and Compose reports the whole
 * row as the paragraph's width even for a short word, so a width check would always fail.
 *
 * @param cutOnPurpose Texts the design spec shortens on purpose, such as a Logs row's two-line
 *   note preview (§6.13). Name each one in the test with the spec section that allows it.
 */
fun SemanticsNodeInteractionsProvider.assertNoTextCutOff(cutOnPurpose: Set<String> = emptySet()) {
    val nodes = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        .fetchSemanticsNodes()
    check(nodes.isNotEmpty()) { "No text on screen. Is the content set?" }
    val cutOff = nodes.filterNot { it.text() in cutOnPurpose }.mapNotNull { node ->
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        results.firstOrNull { it.didOverflowHeight }?.let { "${node.describe()}: ${it.overflowReason()}" }
    }
    if (cutOff.isNotEmpty()) {
        throw AssertionError("Text cut off (design-spec.md §11):\n" + cutOff.joinToString("\n"))
    }
}

private fun TextLayoutResult.overflowReason(): String = if (multiParagraph.didExceedMaxLines) {
    "the lines after line ${multiParagraph.lineCount} are dropped"
} else {
    "needs ${multiParagraph.height.toInt()}px of height, has ${size.height}px"
}
