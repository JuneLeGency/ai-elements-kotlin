package dev.ai.elements.core.backend

import dev.ai.elements.core.ChatEvent
import dev.ai.elements.core.ToolApprover
import dev.ai.elements.core.agent.AgentTool
import dev.ai.elements.core.agent.ToolCallContext
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import dev.ai.elements.core.model.FilePart
import dev.ai.elements.core.model.Message
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Text fed back to the model when the user denies a tool call. */
internal const val DENIED_RESULT = "The user denied this tool call. Do not retry it; continue without it."

/**
 * Execute one tool call of an agent loop, emitting the tool events. Tools with
 * [AgentTool.requiresApproval] wait for [approver] first. While the tool runs,
 * what it reports through [ToolCallContext] (interim output, a nested agent
 * run) is streamed as [ChatEvent.ToolOutput] (preliminary) and
 * [ChatEvent.SubagentUpdate].
 *
 * @return the text fed back to the model.
 */
internal suspend fun FlowCollector<ChatEvent>.runTool(
    tools: List<AgentTool>,
    approver: ToolApprover,
    id: String,
    name: String,
    rawArgs: String,
): String {
    val tool = tools.firstOrNull { it.name == name }
    val args = runCatching {
        if (rawArgs.isBlank()) JsonObject(emptyMap()) else BackendJson.parseToJsonElement(rawArgs).jsonObject
    }
    emit(ChatEvent.ToolInputAvailable(id, name, rawArgs.ifBlank { "{}" }, args.getOrNull()?.let { tool?.titleFor(it) } ?: tool?.title))
    return try {
        requireNotNull(tool) { "Unknown tool: $name" }
        val arguments = args.getOrElse { throw IllegalArgumentException("Invalid JSON arguments: ${it.message}") }
        if (tool.requiresApproval) {
            emit(ChatEvent.ToolApprovalRequest(id))
            if (!approver.approve(id)) {
                emit(ChatEvent.ToolDenied(id))
                return DENIED_RESULT
            }
            emit(ChatEvent.ToolApproved(id))
        }
        executeReporting(tool, arguments, id, approver).also { emit(ChatEvent.ToolOutput(id, it)) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val message = e.message ?: e::class.simpleName.orEmpty()
        emit(ChatEvent.ToolError(id, message))
        "Error: $message"
    }
}

/**
 * Runs the tool in a child coroutine carrying its [ToolCallContext] and
 * relays its updates from this (the flow's) coroutine — a flow may only emit
 * from the coroutine collecting it.
 */
private suspend fun FlowCollector<ChatEvent>.executeReporting(
    tool: AgentTool,
    arguments: JsonObject,
    id: String,
    approver: ToolApprover,
): String = coroutineScope {
    val updates = Channel<ToolCallContext.Update>(Channel.CONFLATED)
    val context = ToolCallContext(id, approver) { updates.send(it) }
    val result = async(context) {
        try {
            tool.execute(arguments)
        } finally {
            updates.close()
        }
    }
    for (update in updates) {
        when (update) {
            is ToolCallContext.Update.Preliminary -> emit(ChatEvent.ToolOutput(id, update.output, preliminary = true))
            is ToolCallContext.Update.Subagent -> emit(ChatEvent.SubagentUpdate(id, update.message))
        }
    }
    result.await()
}

/** Image attachments of a message (only images are sent to models). */
internal val Message.images: List<FilePart>
    get() = parts.filterIsInstance<FilePart>().filter { it.isImage }

internal val Message.hasContent: Boolean
    get() = text.isNotBlank() || images.isNotEmpty()

internal fun JsonElement.errorMessage(): String =
    (this as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
        ?: runCatching { jsonPrimitive.contentOrNull }.getOrNull()
        ?: toString()

internal fun JsonObject.str(key: String): String? =
    runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()

internal fun JsonObject.int(key: String): Int? =
    runCatching { this[key]?.jsonPrimitive?.intOrNull }.getOrNull()

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

