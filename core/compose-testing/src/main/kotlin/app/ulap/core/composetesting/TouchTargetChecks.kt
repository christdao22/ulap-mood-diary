package app.ulap.core.composetesting

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Touch target checks for screen tests (design-spec.md §11). The text checks are in TextChecks.kt.

/** The smallest touch target allowed on Android (design-spec.md §11). */
val MinTouchTarget: Dp = 48.dp

private val isTouchTarget = hasClickAction() or
    SemanticsMatcher.keyIsDefined(SemanticsActions.SetText) or
    SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)

/**
 * Fails if anything a finger can press on the current screen (a click action, a text field, a
 * slider) gets less than [MinTouchTarget] of touch area in either direction.
 *
 * Compose stretches a small target's touch area to 48dp around it, and reports the stretched area
 * to TalkBack, so a lone 36dp icon is fine. The stretch only works where nothing else claims the
 * space, so this also fails when two targets' touch areas overlap: a finger between them can't
 * tell which one it means. A row that holds a button is fine; only unrelated targets count.
 */
fun SemanticsNodeInteractionsProvider.assertTouchTargetsAtLeast48dp() {
    val nodes = onAllNodes(isTouchTarget).fetchSemanticsNodes()
    check(nodes.isNotEmpty()) { "No touch targets on screen. Is the content set?" }
    val problems = nodes.mapNotNull { it.tooSmall() } + overlaps(nodes)
    if (problems.isNotEmpty()) {
        throw AssertionError(
            "Touch targets under ${MinTouchTarget.value.toInt()}dp (design-spec.md §11):\n" +
                problems.joinToString("\n"),
        )
    }
}

/**
 * Fails if anything a finger can press on the current screen has nothing for TalkBack to read:
 * no text and no content description (design-spec.md §11). An icon-only button needs a
 * `contentDescription`; a text field needs a label or a description.
 */
fun SemanticsNodeInteractionsProvider.assertTouchTargetsHaveNames() {
    val nodes = onAllNodes(isTouchTarget).fetchSemanticsNodes()
    check(nodes.isNotEmpty()) { "No touch targets on screen. Is the content set?" }
    val unnamed = nodes.filter { node ->
        val description = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        val text = node.config.getOrNull(SemanticsProperties.Text).orEmpty()
        (description + text.map { it.text }).all { it.isBlank() }
    }
    if (unnamed.isNotEmpty()) {
        throw AssertionError(
            "Touch targets TalkBack can't name (design-spec.md §11):\n" +
                unnamed.joinToString("\n") { "${it.describe()} at ${it.boundsInRoot}" },
        )
    }
}

private fun SemanticsNode.tooSmall(): String? = with(layoutInfo.density) {
    val width = touchBoundsInRoot.width.toDp()
    val height = touchBoundsInRoot.height.toDp()
    if (width < MinTouchTarget || height < MinTouchTarget) {
        "${describe()}: ${width.value.toInt()} × ${height.value.toInt()}dp"
    } else {
        null
    }
}

private fun overlaps(nodes: List<SemanticsNode>): List<String> = nodes.flatMapIndexed { index, a ->
    nodes.drop(index + 1).mapNotNull { b ->
        val shared = a.touchBoundsInRoot.intersect(b.touchBoundsInRoot)
        val overlapping = shared.width > OVERLAP_TOLERANCE_PX && shared.height > OVERLAP_TOLERANCE_PX
        if (overlapping && a.rootId() == b.rootId() && !a.isRelatedTo(b)) {
            with(a.layoutInfo.density) {
                "${a.describe()} and ${b.describe()} share " +
                    "${shared.width.toDp().value.toInt()} × ${shared.height.toDp().value.toInt()}dp of touch area"
            }
        } else {
            null
        }
    }
}

/** Dialogs and popups are separate roots with their own coordinates. */
private fun SemanticsNode.rootId(): Int = generateSequence(this) { it.parent }.last().id

private fun SemanticsNode.isRelatedTo(other: SemanticsNode): Boolean =
    generateSequence(parent) { it.parent }.any { it.id == other.id } ||
        generateSequence(other.parent) { it.parent }.any { it.id == id }

/** Rounding can leave neighbours a pixel apart or a pixel over; that isn't a real overlap. */
private const val OVERLAP_TOLERANCE_PX = 1f

internal fun SemanticsNode.text(): String? = config.getOrNull(SemanticsProperties.Text)?.joinToString()

/** A label for failure messages: the node's text or content description. */
internal fun SemanticsNode.describe(): String {
    val label = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
        ?: text()
        ?: config.getOrNull(SemanticsProperties.EditableText)?.text
    return "\"${label ?: "unlabelled"}\" (node $id)"
}
