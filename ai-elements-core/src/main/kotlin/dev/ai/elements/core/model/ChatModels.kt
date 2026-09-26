package dev.ai.elements.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Role of a chat participant.
 *
 * Mirrors the `role` field of a `UIMessage` from the Vercel AI SDK.
 */
@Serializable
enum class Role {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL,
}

/**
 * Lifecycle status of the chat, mirroring the AI SDK `ChatStatus` union
 * (`"idle" | "submitted" | "streaming" | "ready" | "error"`).
 */
@Serializable
enum class ChatStatus {
    IDLE,
    SUBMITTED,
    STREAMING,
    READY,
    ERROR,
}

/**
 * A single step in the model's chain-of-thought / reasoning.
 *
 * [state] mirrors the AI SDK step status: `"incomplete" | "complete"`.
 *
 * @property id stable identifier, used by [ChatController] to update a step in place.
 * @property title human readable label, e.g. "Searching the web".
 * @property detail optional longer description of the step.
 * @property state whether the step is still running or finished.
 */
@Serializable
data class ReasoningStep(
    val id: String,
    val title: String,
    val detail: String? = null,
    val state: StepState = StepState.INCOMPLETE,
)

@Serializable
enum class StepState { INCOMPLETE, COMPLETE }

/**
 * A cited source / reference shown in the [Sources] component.
 *
 * @property id stable identifier.
 * @property title display title of the source.
 * @property url canonical link.
 * @property domain optional domain label, e.g. "www.x.com".
 */
@Serializable
data class Source(
    val id: String,
    val title: String,
    val url: String,
    val domain: String? = null,
)

/**
 * A suggestion chip offered to the user.
 *
 * @property id stable identifier.
 * @property text the prompt text to send when selected.
 */
@Serializable
data class Suggestion(
    val id: String,
    val text: String,
)

/**
 * A tool/function call made by the model.
 *
 * @property id stable identifier.
 * @property name name of the tool.
 * @property state execution state.
 */
@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val state: ToolCallState = ToolCallState.RUNNING,
    val input: Map<String, String>? = null,
    val output: String? = null,
)

@Serializable
enum class ToolCallState { RUNNING, SUCCESS, ERROR }

/**
 * A complete chat message.
 *
 * Mirrors the AI SDK `UIMessage` shape. The content is delivered as a stream of
 * "parts" (text, reasoning, tool calls, sources) which the UI renders in order.
 *
 * @property id stable identifier.
 * @property role who produced the message.
 * @property parts the ordered content parts.
 * @property timestamp epoch millis when the message was created.
 */
@Serializable
data class Message(
    val id: String,
    val role: Role,
    val parts: List<Part> = emptyList(),
    val timestamp: Long = 0L,
) {
    /** Convenience: concatenate all [TextPart] bodies in order. */
    val text: String
        get() = parts.mapNotNull { it.textOrNull() }.joinToString("")

    fun copyUpdateParts(transform: (List<Part>) -> List<Part>): Message =
        copy(parts = transform(parts))
}

/**
 * Sealed hierarchy of message content parts.
 *
 * This is the KMP-friendly equivalent of the AI SDK `MessagePart` discriminated
 * union (`text | reasoning | tool-* | source-url | source-document | file`).
 */
@Serializable
sealed interface Part {
    fun textOrNull(): String? = null
}

@Serializable
data class TextPart(
    val id: String,
    val text: String,
    @Transient val isStreaming: Boolean = false,
) : Part {
    override fun textOrNull(): String = text
}

@Serializable
data class ReasoningPart(
    val id: String,
    val steps: List<ReasoningStep> = emptyList(),
    @Transient val isStreaming: Boolean = false,
    @Transient val durationMs: Long? = null,
) : Part

@Serializable
data class ToolCallPart(
    val id: String,
    val call: ToolCall,
) : Part

@Serializable
data class SourcesPart(
    val id: String,
    val sources: List<Source> = emptyList(),
) : Part
