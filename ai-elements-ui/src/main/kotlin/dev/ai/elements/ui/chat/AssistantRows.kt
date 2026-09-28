package dev.ai.elements.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.ai.elements.core.model.DataPart
import dev.ai.elements.core.model.FilePart
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Part
import dev.ai.elements.core.model.ReasoningPart
import dev.ai.elements.core.model.SourcePart
import dev.ai.elements.core.model.TextPart
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.ui.markdown.CitationLinks
import dev.ai.elements.ui.markdown.MarkdownBlock
import dev.ai.elements.ui.markdown.MarkdownStreaming
import dev.ai.elements.ui.markdown.markdownBlocks
import dev.ai.elements.ui.theme.AiSize

/**
 * One lazily composed slice of an assistant reply. A long answer becomes many
 * list items — each reasoning/tool/data part, each top-level Markdown block,
 * and a footer — so a [androidx.compose.foundation.lazy.LazyColumn] only
 * composes, measures and draws the slices near the viewport, and a streaming
 * reply only ever re-renders its last block.
 */
@Immutable
internal sealed interface AssistantRow {
    val key: String

    /** The row that carries the avatar. */
    val first: Boolean

    /** Spin the avatar (only meaningful on the [first] row). */
    val streaming: Boolean
    val contentType: String

    /**
     * Rows carry only what they render (never the whole [Message]), so an
     * unchanged block compares equal and is skipped while the reply grows.
     */
    data class PartRow(
        val messageId: String,
        val part: Part,
        override val first: Boolean,
        override val streaming: Boolean,
    ) : AssistantRow {
        override val key = "$messageId/${part.id}"
        override val contentType = part::class.simpleName.orEmpty()
    }

    data class BlockRow(
        val messageId: String,
        val partId: String,
        val index: Int,
        val text: String,
        val citations: List<SourcePart>,
        override val first: Boolean,
        override val streaming: Boolean,
    ) : AssistantRow {
        override val key = "$messageId/$partId/$index"
        override val contentType = "markdown"
    }

    /** The screenshots a tool call produced ([StepMedia]), right under the call. */
    data class StepMediaRow(
        val messageId: String,
        val stepIndex: Int,
        val files: List<FilePart>,
        override val first: Boolean,
        override val streaming: Boolean,
    ) : AssistantRow {
        override val key = "$messageId/step-media/$stepIndex"
        override val contentType = "step-media"
    }

    /** Sources and actions; the only row that needs the whole message. */
    data class FooterRow(val message: Message, override val first: Boolean) : AssistantRow {
        override val key = "${message.id}/footer"
        override val contentType = "footer"
        override val streaming get() = message.isStreaming
    }
}

/** Split an assistant [message] into [AssistantRow]s, in display order. */
internal fun assistantRows(message: Message): List<AssistantRow> {
    val sources = message.parts.filterIsInstance<SourcePart>()
    val rows = mutableListOf<AssistantRow>()
    var step = -1
    var afterTool = false
    message.parts.forEach { part ->
        // Images right after a tool call are its screenshots: one compact row per step.
        if (part is FilePart && part.isImage && afterTool) {
            val previous = rows.lastOrNull() as? AssistantRow.StepMediaRow
            if (previous != null && previous.stepIndex == step) rows[rows.lastIndex] = previous.copy(files = previous.files + part)
            else rows += AssistantRow.StepMediaRow(message.id, step, listOf(part), first = rows.isEmpty(), streaming = false)
            return@forEach
        }
        afterTool = part is ToolPart
        if (part is ToolPart) step++
        when (part) {
            is TextPart -> if (part.text.isNotBlank()) {
                val blocks = markdownBlocks(part.text, sources)
                blocks.forEachIndexed { i, block ->
                    val text = if (part.isStreaming && i == blocks.lastIndex) MarkdownStreaming.repairTail(block) else block
                    val first = rows.isEmpty()
                    rows += AssistantRow.BlockRow(message.id, part.id, i, text, sources, first, first && message.isStreaming)
                }
            }
            is SourcePart -> Unit
            is ReasoningPart, is ToolPart, is DataPart, is FilePart -> {
                val first = rows.isEmpty()
                rows += AssistantRow.PartRow(message.id, part, first, first && message.isStreaming)
            }
        }
    }
    if (sources.isNotEmpty() || (!message.isStreaming && message.parts.isNotEmpty())) {
        rows += AssistantRow.FooterRow(message, first = rows.isEmpty())
    }
    return rows
}

/**
 * Renders one [AssistantRow] at the full width of the conversation — like the
 * mainstream assistant apps, replies have no avatar column taking width from them.
 */
@Composable
internal fun AssistantRowItem(
    row: AssistantRow,
    modifier: Modifier = Modifier,
    prompt: String? = null,
    onRegenerate: (() -> Unit)? = null,
    onToolApproval: ((toolCallId: String, approved: Boolean) -> Unit)? = null,
    onSelectVersion: ((index: Int) -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().testTag("assistant-row")) {
        Box(Modifier.weight(1f)) {
            when (row) {
                is AssistantRow.BlockRow -> CitationLinks(row.citations) { MarkdownBlock(row.text) }
                is AssistantRow.PartRow -> when (val part = row.part) {
                    is ReasoningPart -> Reasoning(part)
                    is ToolPart -> ToolPartView(part, onToolApproval = onToolApproval)
                    is DataPart -> DataPartView(part)
                    is FilePart -> FileAttachment(part)
                    is TextPart, is SourcePart -> Unit
                }
                is AssistantRow.StepMediaRow -> StepMedia(row.messageId, row.stepIndex, row.files)
                is AssistantRow.FooterRow -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Sources(row.message.parts.filterIsInstance<SourcePart>())
                    if (!row.message.isStreaming) MessageActions(row.message, prompt, onRegenerate, onSelectVersion)
                }
            }
        }
    }
}
