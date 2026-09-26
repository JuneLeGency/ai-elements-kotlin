package dev.ai.elements.core

import dev.ai.elements.core.model.FilePart
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Part
import dev.ai.elements.core.model.ReasoningPart
import dev.ai.elements.core.model.SourcePart
import dev.ai.elements.core.model.TextPart
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.core.model.ToolState
import dev.ai.elements.core.model.Usage

/**
 * Folds one [ChatEvent] into the assistant [Message]. Pure, so it is trivially
 * unit-testable and every backend shares the exact same message semantics.
 *
 * Parts are keyed by id: a delta for an unknown id appends a new part at the
 * end, which preserves the interleaving order of multi-step agent runs.
 */
fun Message.reduce(event: ChatEvent, now: Long): Message = when (event) {
    is ChatEvent.TextDelta -> upsert<TextPart>(event.id, { TextPart(event.id, event.delta, isStreaming = true) }) {
        it.copy(text = it.text + event.delta, isStreaming = true)
    }

    is ChatEvent.TextEnd -> updateExisting<TextPart>(event.id) { it.copy(isStreaming = false) }

    is ChatEvent.ReasoningDelta -> upsert<ReasoningPart>(
        event.id,
        { ReasoningPart(event.id, event.delta, isStreaming = true, startedAt = now) },
    ) { it.copy(text = it.text + event.delta, isStreaming = true) }

    is ChatEvent.ReasoningEnd -> updateExisting<ReasoningPart>(event.id) { it.finish(now) }

    is ChatEvent.ToolInputStart -> upsert<ToolPart>(event.id, { ToolPart(event.id, event.name) }) { it }

    is ChatEvent.ToolInputDelta -> updateExisting<ToolPart>(event.id) { it.copy(input = it.input + event.delta) }

    is ChatEvent.ToolInputAvailable -> upsert<ToolPart>(
        event.id,
        { ToolPart(event.id, event.name, ToolState.INPUT_AVAILABLE, event.input) },
    ) { it.copy(name = event.name, state = ToolState.INPUT_AVAILABLE, input = event.input) }

    is ChatEvent.ToolOutput -> updateExisting<ToolPart>(event.id) {
        it.copy(state = ToolState.OUTPUT_AVAILABLE, output = event.output)
    }

    is ChatEvent.ToolError -> updateExisting<ToolPart>(event.id) {
        it.copy(state = ToolState.OUTPUT_ERROR, errorText = event.error)
    }

    is ChatEvent.ToolApprovalRequest -> updateExisting<ToolPart>(event.id) { it.copy(state = ToolState.APPROVAL_REQUESTED) }

    is ChatEvent.ToolDenied -> updateExisting<ToolPart>(event.id) { it.copy(state = ToolState.OUTPUT_DENIED) }

    is ChatEvent.File -> upsert<FilePart>(event.id, { FilePart(event.id, event.mediaType, event.url) }) { it }

    is ChatEvent.Usage -> copy(usage = (usage ?: Usage()) + Usage(event.inputTokens, event.outputTokens))

    is ChatEvent.SourceUrl ->
        if (parts.any { it is SourcePart && it.url == event.url }) this
        else copy(parts = parts + SourcePart(event.id, event.url, event.title))

    ChatEvent.Finish, is ChatEvent.Error -> this
}

/** Close every still-streaming part (end of turn, stop, or error). */
fun Message.finishStreaming(now: Long): Message = copy(
    parts = parts.map { part ->
        when (part) {
            is TextPart -> if (part.isStreaming) part.copy(isStreaming = false) else part
            is ReasoningPart -> if (part.isStreaming) part.finish(now) else part
            is ToolPart -> if (part.isStreaming) part.copy(state = ToolState.OUTPUT_ERROR, errorText = part.errorText ?: "Interrupted") else part
            is SourcePart, is FilePart -> part
        }
    },
)

private fun ReasoningPart.finish(now: Long) =
    copy(isStreaming = false, durationMs = durationMs ?: (now - startedAt).coerceAtLeast(0))

private inline fun <reified T : Part> Message.upsert(id: String, create: () -> T, update: (T) -> T): Message {
    val index = parts.indexOfFirst { it is T && it.id == id }
    return if (index < 0) copy(parts = parts + create())
    else copy(parts = parts.toMutableList().also { it[index] = update(it[index] as T) })
}

private inline fun <reified T : Part> Message.updateExisting(id: String, update: (T) -> T): Message {
    val index = parts.indexOfFirst { it is T && it.id == id }
    return if (index < 0) this
    else copy(parts = parts.toMutableList().also { it[index] = update(it[index] as T) })
}
