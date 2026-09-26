package dev.ai.elements.core

import dev.ai.elements.core.model.Message
import kotlinx.coroutines.flow.Flow

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

    data class ToolInputStart(val id: String, val name: String) : ChatEvent
    data class ToolInputDelta(val id: String, val delta: String) : ChatEvent
    data class ToolInputAvailable(val id: String, val name: String, val input: String) : ChatEvent
    data class ToolOutput(val id: String, val output: String) : ChatEvent
    data class ToolError(val id: String, val error: String) : ChatEvent

    data class SourceUrl(val id: String, val url: String, val title: String) : ChatEvent

    /** Explicit end of the turn. Optional: a completed flow also ends the turn. */
    data object Finish : ChatEvent

    /** An in-band error reported by the server. */
    data class Error(val message: String) : ChatEvent
}

/** Thrown by backends for transport / HTTP failures. */
class ChatBackendException(message: String, cause: Throwable? = null) : Exception(message, cause)
