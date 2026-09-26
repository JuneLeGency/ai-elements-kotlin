package dev.ai.elements.demo

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import org.junit.Assert.fail

/**
 * Waits until the assistant's turn ends — its actions row appears — or the
 * chat shows an error, and fails right away with that error's text instead
 * of timing out without a reason.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeTestRule.awaitTurnEnd(timeoutMs: Long) {
    waitUntil(timeoutMs) {
        onAllNodesWithTag("regenerate").fetchSemanticsNodes().size == 1 ||
            onAllNodesWithTag("chat-error").fetchSemanticsNodes().isNotEmpty()
    }
    val error = onAllNodesWithTag("chat-error", useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
    if (error != null) {
        val text = onAllNodesWithTag("chat-error", useUnmergedTree = true).fetchSemanticsNodes()
            .flatMap { node -> node.children.flatMap { it.config.getOrElse(SemanticsProperties.Text) { emptyList() } } + node.config.getOrElse(SemanticsProperties.Text) { emptyList() } }
            .joinToString(" ") { it.text }
        fail("The turn ended with an error: ${text.ifBlank { "(no text)" }}")
    }
    waitUntilExactlyOneExists(hasTestTag("regenerate"), 5_000)
}
