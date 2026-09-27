package dev.ai.elements.core.chat

import dev.ai.elements.core.model.ChatStatus
import dev.ai.elements.core.model.FilePart
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.TextPart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** A prompt sent while the agent was busy, waiting its turn (AI Elements `<Queue>`). */
data class QueuedMessage(val id: String, val text: String, val attachments: List<FilePart> = emptyList())

/** Immutable snapshot rendered by the UI. */
data class ChatState(
    val messages: List<Message> = emptyList(),
    val status: ChatStatus = ChatStatus.READY,
    val error: String? = null,
    val queue: List<QueuedMessage> = emptyList(),
    /** True after stop/error: queued prompts wait for the user instead of auto-sending. */
    val queuePaused: Boolean = false,
) {
    val isBusy: Boolean get() = status == ChatStatus.SUBMITTED || status == ChatStatus.STREAMING
}

/**
 * Owns one conversation and drives a [ChatBackend], the Kotlin counterpart of
 * the AI SDK `useChat()` hook.
 *
 * Beyond send/stop it supports a message queue (send while busy), reply
 * versions (regenerate keeps the old answer as a branch) and checkpoints
 * (rewind the conversation to an earlier turn).
 *
 * @param backend resolved on every turn, so switching provider/model applies to
 *   the next message without losing the conversation. It receives the
 *   controller's [ToolApprover], which surfaces approval requests in the UI
 *   until [respondToApproval] is called.
 * **Threading:** call it from one thread and give it a [scope] on a
 * single-threaded dispatcher — `viewModelScope` (main thread) is the intended
 * setup. The controller keeps its in-flight turn in plain fields, so a
 * multi-threaded dispatcher such as `Dispatchers.Default` would race.
 *
 * @param scope where turns run; collection happens on this scope's dispatcher
 *   (backends move their own I/O off the main thread).
 * @param publishIntervalMs streamed deltas are coalesced and published at most
 *   this often (one frame by default), so a model emitting hundreds of tokens
 *   per second never recomposes the UI per token. Structural events (tool
 *   calls, part ends, errors) publish immediately. 0 publishes every event.
 */
class ChatController(
    private val backend: (ToolApprover) -> ChatBackend,
    private val scope: CoroutineScope,
    initialMessages: List<Message> = emptyList(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val publishIntervalMs: Long = 32,
) {
    private val _state = MutableStateFlow(ChatState(messages = initialMessages))
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var job: Job? = null

    /** The in-flight reply, including deltas not yet published (read by [stop]). */
    private var live: Pair<List<Message>, Message>? = null
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
     * Send a user message (text and/or [attachments]). While a reply is
     * streaming the message is queued and sent when the turn finishes.
     * Returns false only when there is nothing to send.
     */
    fun send(text: String, attachments: List<FilePart> = emptyList()): Boolean {
        val prompt = text.trim()
        if (prompt.isEmpty() && attachments.isEmpty()) return false
        if (_state.value.isBusy) {
            _state.update { it.copy(queue = it.queue + QueuedMessage(newId(), prompt, attachments)) }
            return true
        }
        run(_state.value.messages + userMessage(prompt, attachments))
        return true
    }

    fun removeQueued(id: String) {
        _state.update { it.copy(queue = it.queue.filterNot { q -> q.id == id }) }
    }

    /** Send a queued prompt now (when idle), e.g. after the queue paused on stop/error. */
    fun sendQueuedNow(id: String) {
        val item = _state.value.queue.firstOrNull { it.id == id } ?: return
        if (_state.value.isBusy) return
        _state.update { it.copy(queue = it.queue - item, queuePaused = false, error = null) }
        run(_state.value.messages + userMessage(item.text, item.attachments))
    }

    /** Answer a tool approval request (AI Elements `<Confirmation>`). */
    fun respondToApproval(toolCallId: String, approved: Boolean) {
        pendingApprovals.getOrPut(toolCallId) { CompletableDeferred() }.complete(approved)
    }

    /** Ask again; the previous reply is kept as another version of the answer. */
    fun regenerate() {
        if (_state.value.isBusy) return
        val messages = _state.value.messages
        val previous = messages.lastOrNull()?.takeIf { it.role == Role.ASSISTANT }
        val history = messages.dropLastWhile { it.role == Role.ASSISTANT }
        if (history.isEmpty()) return
        run(history, previousVersions = previous?.versions.orEmpty())
    }

    /** Show another version of an assistant reply (AI Elements `<Branch>`). */
    fun selectVersion(messageId: String, index: Int) {
        if (_state.value.isBusy) return
        _state.update { st ->
            st.copy(
                messages = st.messages.map { m ->
                    if (m.id != messageId) return@map m
                    val all = m.versions
                    val chosen = all.getOrNull(index) ?: return@map m
                    chosen.copy(alternatives = all - chosen)
                },
            )
        }
    }

    /**
     * Rewind the conversation to just after [messageId] (AI Elements
     * `<Checkpoint>`); later messages are dropped.
     */
    fun restoreCheckpoint(messageId: String) {
        if (_state.value.isBusy) return
        val index = _state.value.messages.indexOfFirst { it.id == messageId }
        if (index < 0) return
        _state.update { it.copy(messages = it.messages.take(index + 1), status = ChatStatus.READY, error = null) }
    }

    /** Abort the in-flight turn, keeping whatever has streamed so far. Queued prompts pause. */
    fun stop() {
        val running = job ?: return
        job = null
        running.cancel()
        pendingApprovals.values.forEach { it.cancel() }
        pendingApprovals.clear()
        val inFlight = live
        live = null
        _state.update { st ->
            st.copy(
                messages = inFlight?.takeIf { it.second.parts.isNotEmpty() }
                    ?.let { (history, reply) -> history + reply.finishStreaming(clock()) }
                    ?: st.messages.finishLastAssistant(),
                status = ChatStatus.READY,
                queuePaused = st.queue.isNotEmpty(),
            )
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

    private fun userMessage(text: String, attachments: List<FilePart>): Message {
        val parts = attachments + listOfNotNull(text.takeIf { it.isNotEmpty() }?.let { TextPart(newId(), it) })
        return Message(newId(), Role.USER, parts, clock())
    }

    private fun run(history: List<Message>, previousVersions: List<Message> = emptyList()) {
        _state.update { it.copy(messages = history, status = ChatStatus.SUBMITTED, error = null, queuePaused = false) }
        var assistant = Message(newId(), Role.ASSISTANT, createdAt = clock(), alternatives = previousVersions)
        var inBandError: String? = null

        job = scope.launch {
            var dirty = false
            fun publish() {
                dirty = false
                if (assistant.parts.isNotEmpty()) {
                    _state.update { it.copy(messages = history + assistant, status = ChatStatus.STREAMING) }
                }
            }
            val flusher = if (publishIntervalMs > 0) launch {
                while (true) {
                    delay(publishIntervalMs)
                    if (dirty) publish()
                }
            } else null
            try {
                backend(approver).stream(history).collect { event ->
                    if (event is ChatEvent.Error) {
                        inBandError = event.message
                        return@collect
                    }
                    assistant = assistant.reduce(event, clock())
                    live = history to assistant
                    val isDelta = event is ChatEvent.TextDelta || event is ChatEvent.ReasoningDelta ||
                        event is ChatEvent.ToolInputDelta || event is ChatEvent.SubagentUpdate ||
                        (event is ChatEvent.ToolOutput && event.preliminary)
                    // The first content leaves SUBMITTED right away; later deltas are coalesced.
                    if (flusher == null || !isDelta || _state.value.status == ChatStatus.SUBMITTED) publish() else dirty = true
                }
                flusher?.cancel()
                complete(history, assistant, inBandError)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                flusher?.cancel()
                complete(history, assistant, e.message ?: e::class.simpleName ?: "Unknown error")
            }
        }
    }

    private fun complete(history: List<Message>, assistant: Message, error: String?) {
        job = null
        live = null
        val finished = assistant.finishStreaming(clock())
        val failure = error ?: "The provider returned an empty response.".takeIf { finished.parts.isEmpty() }
        // A failed regenerate must not lose the earlier versions.
        val reply = when {
            finished.parts.isNotEmpty() -> finished
            finished.alternatives.isNotEmpty() -> finished.alternatives.last().copy(alternatives = finished.alternatives.dropLast(1))
            else -> null
        }
        _state.update { st ->
            st.copy(
                messages = history + listOfNotNull(reply),
                status = if (failure == null) ChatStatus.READY else ChatStatus.ERROR,
                error = failure,
                queuePaused = failure != null && st.queue.isNotEmpty(),
            )
        }
        // Successful turn: the next queued prompt goes out automatically.
        val next = _state.value.queue.firstOrNull()
        if (failure == null && next != null) {
            _state.update { it.copy(queue = it.queue.drop(1)) }
            run(_state.value.messages + userMessage(next.text, next.attachments))
        }
    }

    private fun List<Message>.finishLastAssistant(): List<Message> {
        val last = lastOrNull() ?: return this
        if (last.role != Role.ASSISTANT) return this
        return dropLast(1) + last.finishStreaming(clock())
    }
}
