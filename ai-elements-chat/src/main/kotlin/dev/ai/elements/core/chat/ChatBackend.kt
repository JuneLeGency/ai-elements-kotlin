package dev.ai.elements.core.chat

import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.ToolKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll

/**
 * Produces one assistant turn as a cold stream of [ChatEvent]s.
 *
 * The flow is collected by [ChatController]; cancelling the collection (the
 * user pressed stop) must cancel the underlying request. Failures are reported
 * by throwing — the controller turns them into [dev.ai.elements.core.model.ChatStatus.ERROR].
 *
 * @param history the whole conversation so far, ending with the new user message.
 */
fun interface ChatBackend {
    fun stream(history: List<Message>): Flow<ChatEvent>
}

/**
 * Protocol-neutral stream events. They map 1:1 onto the AI SDK UI Message
 * Stream chunk types, so every backend (UI Message Stream, OpenAI, Anthropic,
 * mock) translates its wire format into the same small vocabulary.
 */
sealed interface ChatEvent {
    data class TextDelta(val id: String, val delta: String) : ChatEvent
    data class TextEnd(val id: String) : ChatEvent

    data class ReasoningDelta(val id: String, val delta: String) : ChatEvent
    data class ReasoningEnd(val id: String) : ChatEvent

    data class ToolInputStart(val id: String, val name: String, val title: String? = null) : ChatEvent
    data class ToolInputDelta(val id: String, val delta: String) : ChatEvent
    data class ToolInputAvailable(
        val id: String,
        val name: String,
        val input: String,
        val title: String? = null,
        val kind: ToolKind? = null,
        val source: String? = null,
    ) : ChatEvent

    /** A tool result; [preliminary] results are replaced by later ones (AI SDK preliminary tool results). */
    data class ToolOutput(val id: String, val output: String, val preliminary: Boolean = false) : ChatEvent
    data class ToolError(val id: String, val error: String) : ChatEvent

    /** Snapshot of a delegated agent's run inside tool call [id] (see [dev.ai.elements.core.agent.SubagentTool]). */
    data class SubagentUpdate(val id: String, val message: Message) : ChatEvent

    /** The backend is waiting for [ToolApprover] to approve this tool call. */
    data class ToolApprovalRequest(val id: String) : ChatEvent
    data class ToolDenied(val id: String) : ChatEvent

    /** The user approved; the tool runs (or the server continues) next. */
    data class ToolApproved(val id: String) : ChatEvent

    data class SourceUrl(val id: String, val url: String, val title: String) : ChatEvent

    /** A model-generated file (e.g. an image). */
    data class File(val id: String, val mediaType: String, val url: String) : ChatEvent

    /** An AI SDK `data-*` part; same id replaces. */
    data class Data(val id: String, val name: String, val data: kotlinx.serialization.json.JsonElement) : ChatEvent

    /** Merged into [Message.metadata] (AI SDK `message-metadata`). */
    data class Metadata(val metadata: kotlinx.serialization.json.JsonObject) : ChatEvent

    /** Token usage of one model call; summed per turn. */
    data class Usage(val inputTokens: Int, val outputTokens: Int) : ChatEvent

    /** Explicit end of the turn. Optional: a completed flow also ends the turn. */
    data object Finish : ChatEvent

    /** An in-band error reported by the server. */
    data class Error(val message: String) : ChatEvent
}

/**
 * Gate for tools that need a human in the loop (`AgentTool.requiresApproval`).
 * [ChatController] implements it by surfacing a Confirmation in the UI.
 */
fun interface ToolApprover {
    suspend fun approve(toolCallId: String): Boolean

    companion object {
        val AlwaysApprove = ToolApprover { true }
    }
}

/** Thrown by backends for transport / HTTP failures. */
class ChatBackendException(
    message: String,
    cause: Throwable? = null,
    /** HTTP status when the provider answered with an error status. */
    val statusCode: Int? = null,
) : Exception(message, cause)

/**
 * A backend whose construction needs I/O — e.g. listing MCP tools or fetching
 * an A2A agent card — built fresh for every turn inside the turn's coroutine,
 * so failures surface as the turn's error.
 */
fun deferredBackend(create: suspend () -> ChatBackend): ChatBackend = ChatBackend { history ->
    kotlinx.coroutines.flow.flow { emitAll(create().stream(history)) }
}
