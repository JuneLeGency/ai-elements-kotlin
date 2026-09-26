package dev.ai.elements.core

import dev.ai.elements.core.model.ChatStatus
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Part
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.ReasoningPart
import dev.ai.elements.core.model.ReasoningStep
import dev.ai.elements.core.model.SourcesPart
import dev.ai.elements.core.model.TextPart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.max

/**
 * Immutable, observable snapshot of the chat state.
 *
 * The UI layer collects [status], [messages] and [error] via `collectAsState()`
 * and re-renders reactively. [ChatController] owns all mutations and guarantees
 * every emission is a consistent snapshot (structural sharing keeps it cheap).
 */
data class ChatState(
    val status: ChatStatus = ChatStatus.IDLE,
    val messages: List<Message> = emptyList(),
    val error: String? = null,
)

/**
 * A pluggable backend that produces an assistant reply as a stream of [ChatEvent]s.
 *
 * Implementations may be backed by the Vercel AI Gateway, a local model, or a
 * mock. The [ChatController] is transport-agnostic: it only cares about the
 * event stream, so the same UI works on any platform and any provider.
 *
 * @param prompt the user's prompt.
 * @param scope the caller-provided scope; events are emitted on a background context
 *   and the stream is cancelled when the [scope] is cancelled.
 */
fun interface ChatBackend {
    fun generate(prompt: String): Channel<ChatEvent>
}

/**
 * Discrete events emitted by a [ChatBackend] while streaming a reply.
 *
 * These map 1:1 to the AI SDK's UI message-part updates, so a real SSE backend can
 * translate its tokens directly into these events.
 */
sealed interface ChatEvent {
    /** The assistant began emitting content. */
    data object Start : ChatEvent

    /** Append/replace the main text part. */
    data class TextDelta(
        val partId: String,
        val text: String,
        val done: Boolean = false,
    ) : ChatEvent

    /** Add or update a reasoning step. */
    data class ReasoningStepEvent(
        val partId: String,
        val step: ReasoningStep,
    ) : ChatEvent

    /** Finalise the reasoning part (stop streaming, record duration). */
    data class ReasoningDone(
        val partId: String,
        val durationMs: Long,
    ) : ChatEvent

    /** Attach a set of sources to the message. */
    data class Sources(
        val partId: String,
        val sources: dev.ai.elements.core.model.Source,
    ) : ChatEvent

    /** The reply finished successfully. */
    data object Done : ChatEvent

    /** The backend failed. */
    data class Error(val message: String) : ChatEvent
}

/**
 * Owns the chat state and exposes it as [StateFlow]s for the UI.
 *
 * Usage:
 * ```
 * val controller = ChatController(backend = MyBackend())
 * // in composable:
 * val state by controller.state.collectAsState()
 * ```
 *
 * [controller] is self-contained and can be created in a ViewModel or held via
 * `remember { ChatController(...) }`. Cancel it with [close] when the screen goes
 * away.
 */
class ChatController(
    private val backend: ChatBackend,
    scope: CoroutineScope? = null,
) : AutoCloseable {

    private val internalScope: CoroutineScope
    private val ownsScope: Boolean

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    init {
        val provided = scope
        internalScope = provided ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        ownsScope = provided == null
    }

    private var activeJob: Job? = null

    /** Append a user message and start streaming the assistant reply. */
    fun send(prompt: String) {
        val trimmed = prompt.trim()
        android.util.Log.i("AIElems", "ChatController.send prompt='$trimmed' activeJob=${activeJob?.isActive}")
        if (trimmed.isEmpty()) return
        if (activeJob?.isActive == true) return

        val userMessage = Message(
            id = UUID.randomUUID().toString(),
            role = Role.USER,
            parts = listOf(TextPart(id = UUID.randomUUID().toString(), text = trimmed)),
            timestamp = System.currentTimeMillis(),
        )

        // Reset the assistant side and append the user message atomically.
        _state.update { it.copy(status = ChatStatus.SUBMITTED, error = null) }
        _state.update { st -> st.copy(messages = st.messages + userMessage) }

        activeJob = internalScope.launch {
            var assistantId = ""
            var textPartId = ""
            var reasoningPartId = ""
            var sourcesPartId = ""

            fun ensureAssistant(): Message {
                if (assistantId.isEmpty()) {
                    assistantId = UUID.randomUUID().toString()
                    textPartId = UUID.randomUUID().toString()
                    reasoningPartId = UUID.randomUUID().toString()
                    sourcesPartId = UUID.randomUUID().toString()
                }
                return Message(
                    id = assistantId,
                    role = Role.ASSISTANT,
                    timestamp = System.currentTimeMillis(),
                )
            }

            fun replaceAssistant(msg: Message) {
                val idx = _state.value.messages.indexOfFirst { it.id == msg.id }
                val current = _state.value.messages
                _state.update { st ->
                    st.copy(
                        messages = if (idx >= 0) {
                            st.messages.toMutableList().also { it[idx] = msg }
                        } else {
                            st.messages + msg
                        },
                    )
                }
            }

            _state.update { it.copy(status = ChatStatus.STREAMING) }

            val channel = backend.generate(trimmed)
            try {
                for (event in channel) {
                    when (event) {
                        is ChatEvent.Start -> {
                            if (assistantId.isEmpty()) {
                                replaceAssistant(ensureAssistant())
                            }
                        }

                        is ChatEvent.TextDelta -> {
                            val msg = ensureAssistant()
                            val partId = event.partId.ifBlank { textPartId }
                            val existing = msg.parts.firstOrNull { it is TextPart && it.id == partId } as? TextPart
                            val updated = if (existing == null) {
                                TextPart(id = partId, text = event.text, isStreaming = !event.done)
                            } else {
                                existing.copy(text = existing.text + event.text, isStreaming = !event.done)
                            }
                            replaceAssistant(msg.copy(parts = msg.parts.map { if (it is TextPart && it.id == partId) updated else it }))
                        }

                        is ChatEvent.ReasoningStepEvent -> {
                            val msg = ensureAssistant()
                            val partId = event.partId.ifBlank { reasoningPartId }
                            val newStep = event.step
                            val part = msg.parts.firstOrNull { it is ReasoningPart && it.id == partId } as? ReasoningPart
                            val newSteps = if (part == null) {
                                listOf(newStep)
                            } else {
                                val i = part.steps.indexOfFirst { it.id == newStep.id }
                                if (i >= 0) part.steps.toMutableList().also { it[i] = newStep }
                                else part.steps + newStep
                            }
                            replaceAssistant(msg.copy(parts = msg.parts.map {
                                if (it is ReasoningPart && it.id == partId) {
                                    ReasoningPart(id = partId, steps = newSteps, isStreaming = true)
                                } else it
                            }))
                        }

                        is ChatEvent.ReasoningDone -> {
                            val msg = ensureAssistant()
                            val partId = event.partId.ifBlank { reasoningPartId }
                            replaceAssistant(msg.copy(parts = msg.parts.map {
                                if (it is ReasoningPart && it.id == partId) {
                                    (it as ReasoningPart).copy(
                                        isStreaming = false,
                                        durationMs = event.durationMs,
                                        steps = it.steps.map { s -> s.copy(state = dev.ai.elements.core.model.StepState.COMPLETE) },
                                    )
                                } else it
                            }))
                        }

                        is ChatEvent.Sources -> {
                            val msg = ensureAssistant()
                            val partId = event.partId.ifBlank { sourcesPartId }
                            val sources = listOf(event.sources)
                            replaceAssistant(msg.copy(parts = msg.parts.map {
                                if (it is SourcesPart && it.id == partId) (it as SourcesPart).copy(sources = it.sources + sources)
                                else it
                            }))
                        }

                        is ChatEvent.Done -> {
                            _state.update { st ->
                                st.copy(
                                    status = ChatStatus.READY,
                                    messages = st.messages.map { m ->
                                        if (m.id == assistantId) {
                                            m.copy(parts = m.parts.map { p ->
                                                if (p is TextPart) p.copy(isStreaming = false)
                                                else if (p is ReasoningPart) p.copy(isStreaming = false)
                                                else p
                                            })
                                        } else m
                                    },
                                )
                            }
                        }

                        is ChatEvent.Error -> {
                            _state.update { it.copy(status = ChatStatus.ERROR, error = event.message) }
                        }
                    }
                }
            } finally {
                channel.close()
            }

            // If the backend ended the stream without an explicit Done, mark ready.
            _state.update { st ->
                if (st.status == ChatStatus.STREAMING) st.copy(status = ChatStatus.READY) else st
            }
        }
    }

    /** Abort the in-flight reply and return to idle. */
    fun stop() {
        activeJob?.cancel()
        activeJob = null
        _state.update { st ->
            st.copy(
                status = ChatStatus.IDLE,
                messages = st.messages.map { m ->
                    if (m.role == Role.ASSISTANT) {
                        m.copy(parts = m.parts.map { p ->
                            if (p is TextPart) p.copy(isStreaming = false)
                            else if (p is ReasoningPart) p.copy(isStreaming = false)
                            else p
                        })
                    } else m
                },
            )
        }
    }

    /** Clear the whole conversation. */
    fun reset() {
        stop()
        _state.value = ChatState()
    }

    override fun close() {
        if (ownsScope) internalScope.cancel()
    }
}

/**
 * A minimal in-memory backend that emits a canned reply token by token.
 *
 * Useful for previews, demos, and tests. Override to customise the text.
 */
class MockChatBackend(
    private val reply: String = "Hello! I'm a mock AI assistant.\n\n" +
        "I can help you build AI-native apps. Try asking me anything.",
) : ChatBackend {
    override fun generate(prompt: String): Channel<ChatEvent> {
        val channel = Channel<ChatEvent>(Channel.UNLIMITED)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            runCatching {
                channel.trySend(ChatEvent.Start)
                val partId = UUID.randomUUID().toString()
                val chunks = reply.chunked(3)
                chunks.forEachIndexed { i, chunk ->
                    if (i == chunks.lastIndex) {
                        channel.send(ChatEvent.TextDelta(partId, chunk, done = true))
                    } else {
                        channel.send(ChatEvent.TextDelta(partId, chunk))
                        kotlinx.coroutines.delay(12)
                    }
                }
                channel.send(ChatEvent.Done)
            }.onFailure {
                channel.trySend(ChatEvent.Error(it.message ?: "unknown error"))
            }
            channel.close()
        }
        return channel
    }
}
