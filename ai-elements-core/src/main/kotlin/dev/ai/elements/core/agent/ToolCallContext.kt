package dev.ai.elements.core.agent

import dev.ai.elements.core.chat.ToolApprover
import dev.ai.elements.core.model.Message
import kotlinx.serialization.json.JsonElement
import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * The tool call an [AgentTool.execute] is running for. Agent loops install it
 * in the coroutine context, so a tool reaches it with [current] without the
 * [AgentTool] interface growing parameters:
 *
 * ```kotlin
 * override suspend fun execute(arguments: JsonObject): String {
 *     ToolCallContext.current()?.progress("Downloading…")
 *     …
 * }
 * ```
 */
class ToolCallContext internal constructor(
    /** The model's id for this call. */
    val toolCallId: String,
    /** The approver of the enclosing chat; nested agents reuse it so their tools ask the same user. */
    val approver: ToolApprover,
    private val sink: suspend (Update) -> Unit,
) : AbstractCoroutineContextElement(Key) {

    /** What a running tool reports before it returns. */
    sealed interface Update {
        /** An interim result shown in place of the output until the tool returns. */
        data class Preliminary(val output: String) : Update

        /** The current state of a delegated agent's reply. */
        data class Subagent(val message: Message) : Update

        /** A data part of the reply (same [id] replaces), e.g. a live plan. */
        data class Data(val id: String, val name: String, val data: JsonElement) : Update

        /** A file the call produced (a screenshot, an image), shown after the call. */
        data class File(val id: String, val mediaType: String, val url: String) : Update
    }

    /** Show [output] as an interim result (AI SDK preliminary tool result). */
    suspend fun progress(output: String) = sink(Update.Preliminary(output))

    /** Publish the live run of a delegated agent (rendered by the `Subagent` element). */
    suspend fun subagent(message: Message) = sink(Update.Subagent(message))

    /**
     * Add or replace a data part of the reply while the tool runs — e.g. `plan` for the
     * `Plan` element. In-process only: servers use their protocol's own channel for this.
     */
    suspend fun data(id: String, name: String, data: JsonElement) = sink(Update.Data(id, name, data))

    private var files = 0

    /**
     * Attach a file the call produced — a screenshot of what it did, a generated image — as a file
     * part right after the call (AI SDK `file` part; the steps view shows it with this call).
     * [url] is a `data:` or `http(s)` URL.
     */
    suspend fun file(mediaType: String, url: String) = sink(Update.File("$toolCallId-file-${files++}", mediaType, url))

    companion object Key : CoroutineContext.Key<ToolCallContext> {
        /** The call being executed, or null outside an agent loop (e.g. in a unit test). */
        suspend fun current(): ToolCallContext? = currentCoroutineContext()[Key]
    }
}
