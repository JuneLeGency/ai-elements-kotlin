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

    /** The user denied the call, optionally saying why. */
    data class ToolDenied(val id: String, val reason: String? = null) : ChatEvent

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
 * The human in the loop. Backends ask it to approve tool calls (`AgentTool.requiresApproval`,
 * AI SDK tool approval, AG-UI interrupts) and for information only the user can give (AG-UI
 * interrupts with a `responseSchema`, MCP elicitation). [ChatController] implements it by
 * surfacing a Confirmation or an input form in the UI.
 */
fun interface ToolApprover {
    suspend fun approve(toolCallId: String): Boolean

    /** The user's full decision on a tool call: approval, a reason, edited arguments. */
    suspend fun decide(toolCallId: String): ToolDecision = ToolDecision(approve(toolCallId))

    /** Ask the user for [request]; hosts that cannot ask answer [InputResponse.Cancel]. */
    suspend fun input(request: InputRequest): InputResponse = InputResponse.Cancel

    companion object {
        val AlwaysApprove = ToolApprover { true }
    }
}

/**
 * A user's answer to a tool approval request.
 *
 * @property reason why they denied (or a note on approving); passed to the agent.
 * @property editedInput arguments the user changed before approving; they replace the proposed ones.
 */
data class ToolDecision(
    val approved: Boolean,
    val reason: String? = null,
    val editedInput: kotlinx.serialization.json.JsonObject? = null,
)

/**
 * A request for information only the user can give — the neutral form of an AG-UI interrupt with
 * a `responseSchema` and of MCP elicitation.
 *
 * @property message what is asked, for the user.
 * @property schema a JSON Schema (`type: object`) of the answer; the UI renders it as a form.
 *   Null asks for a plain confirmation.
 * @property url for out-of-band input: the user completes it at this URL (MCP URL elicitation).
 * @property source who asks, e.g. an MCP server's name.
 */
data class InputRequest(
    val id: String,
    val message: String,
    val schema: kotlinx.serialization.json.JsonObject? = null,
    val url: String? = null,
    val source: String? = null,
)

/** The user's answer to an [InputRequest]. */
sealed interface InputResponse {
    /** Submitted: [content] matches the request's schema (null for a URL or a plain confirmation). */
    data class Accept(val content: kotlinx.serialization.json.JsonObject? = null) : InputResponse

    /** Explicitly declined. */
    data object Decline : InputResponse

    /** Dismissed without a choice (or the host cannot ask). */
    data object Cancel : InputResponse
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
