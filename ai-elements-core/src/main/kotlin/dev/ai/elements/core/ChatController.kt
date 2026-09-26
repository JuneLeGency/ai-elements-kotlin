package dev.ai.elements.core

import dev.ai.elements.core.model.ChatStatus
import dev.ai.elements.core.model.FilePart
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.TextPart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Immutable snapshot rendered by the UI. */
data class ChatState(
    val messages: List<Message> = emptyList(),
    val status: ChatStatus = ChatStatus.READY,
    val error: String? = null,
) {
    val isBusy: Boolean get() = status == ChatStatus.SUBMITTED || status == ChatStatus.STREAMING
}

/**
 * Owns one conversation and drives a [ChatBackend], the Kotlin counterpart of
 * the AI SDK `useChat()` hook.
 *
 * @param backend resolved on every turn, so switching provider/model applies to
 *   the next message without losing the conversation. It receives the
 *   controller's [ToolApprover], which surfaces approval requests in the UI
 *   until [respondToApproval] is called.
 * @param scope where turns run; collection happens on this scope's dispatcher
 *   (backends move their own I/O off the main thread).
 */
class ChatController(
    private val backend: (ToolApprover) -> ChatBackend,
    private val scope: CoroutineScope,
    initialMessages: List<Message> = emptyList(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val _state = MutableStateFlow(ChatState(messages = initialMessages))
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var job: Job? = null
    private val pendingApprovals = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val approver = ToolApprover { id ->
        val decision = pendingApprovals.getOrPut(id) { CompletableDeferred() }
        try {
            decision.await()
        } finally {
            pendingApprovals.remove(id)
        }
    }

    /**
     * Append a user message (text and/or [attachments]) and stream the reply.
     * Returns false when busy or empty.
     */
    fun send(text: String, attachments: List<FilePart> = emptyList()): Boolean {
        val prompt = text.trim()
        if ((prompt.isEmpty() && attachments.isEmpty()) || _state.value.isBusy) return false
        val parts = attachments + listOfNotNull(prompt.takeIf { it.isNotEmpty() }?.let { TextPart(newId(), it) })
        val user = Message(newId(), Role.USER, parts, clock())
        run(_state.value.messages + user)
        return true
    }

    /** Answer a tool approval request (AI Elements `<Confirmation>`). */
    fun respondToApproval(toolCallId: String, approved: Boolean) {
        pendingApprovals.getOrPut(toolCallId) { CompletableDeferred() }.complete(approved)
    }

    /** Drop the last assistant reply (if any) and ask again. */
    fun regenerate() {
        if (_state.value.isBusy) return
        val history = _state.value.messages.dropLastWhile { it.role == Role.ASSISTANT }
        if (history.isEmpty()) return
        run(history)
    }

    /** Abort the in-flight turn, keeping whatever has streamed so far. */
    fun stop() {
        val running = job ?: return
        job = null
        running.cancel()
        pendingApprovals.values.forEach { it.cancel() }
        pendingApprovals.clear()
        _state.update { st ->
            st.copy(messages = st.messages.finishLastAssistant(), status = ChatStatus.READY)
        }
    }

    /** Replace the conversation (e.g. when switching threads). */
    fun load(messages: List<Message>) {
        stop()
        _state.value = ChatState(messages = messages)
    }

    fun dismissError() {
        _state.update { if (it.status == ChatStatus.ERROR) it.copy(status = ChatStatus.READY, error = null) else it }
    }

    private fun run(history: List<Message>) {
        _state.value = ChatState(messages = history, status = ChatStatus.SUBMITTED)
        var assistant = Message(newId(), Role.ASSISTANT, createdAt = clock())
        var inBandError: String? = null

        job = scope.launch {
            try {
                backend(approver).stream(history).collect { event ->
                    if (event is ChatEvent.Error) {
                        inBandError = event.message
                        return@collect
                    }
                    assistant = assistant.reduce(event, clock())
                    if (assistant.parts.isNotEmpty()) {
                        _state.value = ChatState(history + assistant, ChatStatus.STREAMING)
                    }
                }
                complete(history, assistant, inBandError)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                complete(history, assistant, e.message ?: e::class.simpleName ?: "Unknown error")
            }
        }
    }

    private fun complete(history: List<Message>, assistant: Message, error: String?) {
        job = null
        val finished = assistant.finishStreaming(clock())
        val messages = if (finished.parts.isEmpty()) history else history + finished
        val failure = error ?: "The provider returned an empty response.".takeIf { finished.parts.isEmpty() }
        _state.value = ChatState(
            messages = messages,
            status = if (failure == null) ChatStatus.READY else ChatStatus.ERROR,
            error = failure,
        )
    }

    private fun List<Message>.finishLastAssistant(): List<Message> {
        val last = lastOrNull() ?: return this
        if (last.role != Role.ASSISTANT) return this
        return dropLast(1) + last.finishStreaming(clock())
    }
}
