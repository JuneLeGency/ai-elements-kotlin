package dev.ai.elements.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Who produced a [Message]. Mirrors the AI SDK `UIMessage.role`. */
@Serializable
enum class Role { USER, ASSISTANT }

/** Chat lifecycle, mirroring the AI SDK `useChat().status`. */
enum class ChatStatus {
    /** Idle; a new message can be sent. */
    READY,

    /** Request sent, nothing received yet. */
    SUBMITTED,

    /** Parts are streaming in. */
    STREAMING,

    /** The last request failed; see [dev.ai.elements.core.ChatState.error]. */
    ERROR,
}

/**
 * A chat message: an ordered list of [Part]s, the same shape as the AI SDK
 * `UIMessage`. Assistant messages usually interleave reasoning, tool calls and
 * text across several agent steps.
 */
@Serializable
data class Message(
    val id: String,
    val role: Role,
    val parts: List<Part> = emptyList(),
    val createdAt: Long = 0L,
) {
    /** Concatenated text of all [TextPart]s. */
    val text: String
        get() = parts.filterIsInstance<TextPart>().joinToString("\n\n") { it.text }

    val isStreaming: Boolean
        get() = parts.any { it.isStreaming }
}

@Serializable
sealed interface Part {
    val id: String
    val isStreaming: Boolean get() = false
}

@Serializable
@SerialName("text")
data class TextPart(
    override val id: String,
    val text: String,
    override val isStreaming: Boolean = false,
) : Part

@Serializable
@SerialName("reasoning")
data class ReasoningPart(
    override val id: String,
    val text: String,
    override val isStreaming: Boolean = false,
    val startedAt: Long = 0L,
    val durationMs: Long? = null,
) : Part

@Serializable
enum class ToolState { INPUT_STREAMING, INPUT_AVAILABLE, OUTPUT_AVAILABLE, OUTPUT_ERROR }

/**
 * One tool invocation. [id] is the tool call id; [input] is the raw JSON
 * arguments and [output] the tool result as text.
 */
@Serializable
@SerialName("tool")
data class ToolPart(
    override val id: String,
    val name: String,
    val state: ToolState = ToolState.INPUT_STREAMING,
    val input: String = "",
    val output: String? = null,
    val errorText: String? = null,
) : Part {
    override val isStreaming: Boolean
        get() = state == ToolState.INPUT_STREAMING || state == ToolState.INPUT_AVAILABLE
}

@Serializable
@SerialName("source")
data class SourcePart(
    override val id: String,
    val url: String,
    val title: String = url,
) : Part

/** A prompt suggestion chip. */
data class Suggestion(val text: String, val label: String = text)
