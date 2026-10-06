package com.example.contentstudio

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsActions

/** Locate a slide even when lazy composition has not created its semantics yet. */
fun ComposeTestRule.editorNode(tag: String): SemanticsNodeInteraction {
    if (onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) {
        // Async image semantics can disappear briefly when a lazy item is composed again.
        val slide = Regex("slide-(?:image|design-preview)-(\\d+)").matchEntire(tag)?.groupValues?.get(1)
        onNodeWithTag("project-editor-list").performScrollToNode(hasTestTag(if (slide != null) "slide-item-$slide" else tag))
        if (slide != null) waitUntil(15_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }
    return onNodeWithTag(tag)
}

fun ComposeTestRule.homeNode(text: String): SemanticsNodeInteraction {
    if (onAllNodesWithTag("compact-home").fetchSemanticsNodes().isNotEmpty()) {
        onNodeWithTag("compact-home").performScrollToNode(hasText(text))
    } else {
        val lists = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
        (if (text == "Projects") lists[1] else lists[0]).performScrollToNode(hasText(text))
    }
    return onNodeWithText(text)
}

fun ComposeTestRule.projectNode(title: String): SemanticsNodeInteraction {
    waitUntil(15_000) {
        onAllNodesWithTag("compact-home").fetchSemanticsNodes().isNotEmpty() ||
            onAllNodesWithTag("wide-home").fetchSemanticsNodes().isNotEmpty()
    }
    val lists = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
    lists[lists.fetchSemanticsNodes().lastIndex].performScrollToNode(hasText(title))
    return onNodeWithText(title)
}

fun ComposeTestRule.photoNode(tag: String): SemanticsNodeInteraction {
    onNodeWithTag("photo-pdf-list").performScrollToNode(hasTestTag(tag))
    return onNodeWithTag(tag)
}
