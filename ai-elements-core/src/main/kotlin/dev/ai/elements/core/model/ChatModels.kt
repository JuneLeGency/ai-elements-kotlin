package dev.ai.elements.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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

    /** The last request failed; see [dev.ai.elements.core.chat.ChatState.error]. */
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
    val usage: Usage? = null,
    /**
     * Other versions of this reply (AI Elements `<Branch>`): regenerating keeps
     * the previous answer here instead of discarding it. Oldest first.
     */
    val alternatives: List<Message> = emptyList(),
    /**
     * App- or protocol-defined metadata (AI SDK `UIMessage.metadata`), e.g. the
     * A2A `contextId` / `taskId` a remote agent's reply belongs to.
     */
    val metadata: JsonObject? = null,
) {
    /** All versions in creation order, this one included. */
    val versions: List<Message>
        get() = (alternatives + copy(alternatives = emptyList())).sortedBy { it.createdAt }

    /** 0-based position of this version within [versions]. */
    val versionIndex: Int
        get() = versions.indexOfFirst { it.id == id }

    /** Concatenated text of all [TextPart]s. */
    val text: String
        get() = parts.filterIsInstance<TextPart>().joinToString("\n\n") { it.text }

    val isStreaming: Boolean
        get() = parts.any { it.isStreaming }
}

/** One piece of a [Message]: text, reasoning, a tool call, a file, a source or custom data. */
@Serializable
sealed interface Part {
    val id: String
    val isStreaming: Boolean get() = false
}

/** Answer text (Markdown); [isStreaming] while tokens are still arriving. */
@Serializable
@SerialName("text")
data class TextPart(
    override val id: String,
    val text: String,
    override val isStreaming: Boolean = false,
) : Part

/** The model's reasoning; [durationMs] is how long it thought, once finished. */
@Serializable
@SerialName("reasoning")
data class ReasoningPart(
    override val id: String,
    val text: String,
    override val isStreaming: Boolean = false,
    val startedAt: Long = 0L,
    val durationMs: Long? = null,
) : Part

/** Token usage of one assistant turn (summed across agent steps). */
@Serializable
data class Usage(val inputTokens: Int = 0, val outputTokens: Int = 0) {
    val totalTokens: Int get() = inputTokens + outputTokens
    operator fun plus(other: Usage) = Usage(inputTokens + other.inputTokens, outputTokens + other.outputTokens)
}

/** Life cycle of a tool call: input streaming → available → (approval) → output / error / denied. */
@Serializable
enum class ToolState {
    INPUT_STREAMING,
    INPUT_AVAILABLE,

    /** Waiting for the user to approve or deny (AI Elements `<Confirmation>`). */
    APPROVAL_REQUESTED,
    OUTPUT_AVAILABLE,
    OUTPUT_ERROR,
    OUTPUT_DENIED,
}

/**
 * One tool invocation. [id] is the tool call id; [input] is the raw JSON
 * arguments and [output] the tool result as text.
 *
 * @property title human-readable label (AI SDK `title`), e.g. an MCP tool's
 *   title and server; UIs fall back to [name].
 * @property preliminary [output] is an interim result that later updates will
 *   replace (AI SDK preliminary tool results).
 * @property subagent the live run of a delegated agent (AI SDK subagent
 *   pattern): the nested reply with its own reasoning, tool calls and text.
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
    val title: String? = null,
    val preliminary: Boolean = false,
    val subagent: Message? = null,
) : Part {
    override val isStreaming: Boolean
        get() = state == ToolState.INPUT_STREAMING || state == ToolState.INPUT_AVAILABLE ||
            state == ToolState.APPROVAL_REQUESTED

    /** [title] or, failing that, the tool [name]. */
    val displayName: String get() = title ?: name
}

/** A cited source (URL and title), shown by [dev.ai.elements.ui.chat.Sources] and inline citations. */
@Serializable
@SerialName("source")
data class SourcePart(
    override val id: String,
    val url: String,
    val title: String = url,
) : Part

/**
 * A file: a user attachment or a model-generated file. [url] is an `https:` URL
 * or a `data:` URL (base64), as in the AI SDK `FileUIPart`.
 */
@Serializable
@SerialName("file")
data class FilePart(
    override val id: String,
    val mediaType: String,
    val url: String,
    val filename: String? = null,
) : Part {
    val isImage: Boolean get() = mediaType.startsWith("image/")

    /** Base64 payload of a `data:` URL, or null for remote URLs. */
    val base64Data: String? get() = if (url.startsWith("data:")) url.substringAfter("base64,", "").ifEmpty { null } else null
}

/**
 * Structured, app-defined data streamed by the agent — the AI SDK `data-*`
 * parts (e.g. `data-plan`, `data-task`). A later part with the same [id]
 * replaces the earlier one, so agents can update it live.
 *
 * @property name the type without the `data-` prefix.
 */
@Serializable
@SerialName("data")
data class DataPart(
    override val id: String,
    val name: String,
    val data: JsonElement,
) : Part

/** A prompt suggestion chip. */
data class Suggestion(val text: String, val label: String = text)
