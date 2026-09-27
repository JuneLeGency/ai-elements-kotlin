package dev.ai.elements.core.agent

import dev.ai.elements.core.chat.ChatEvent
import dev.ai.elements.core.chat.ToolApprover
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
import dev.ai.elements.core.http.BackendJson

/** Text fed back to the model when the user denies a tool call. */
internal const val DENIED_RESULT = "The user denied this tool call. Do not retry it; continue without it."

/**
 * Execute one tool call of an agent loop, emitting the tool events. Tools with
 * [AgentTool.requiresApproval] wait for [approver] first. While the tool runs,
 * what it reports through [ToolCallContext] (interim output, a nested agent
 * run) is streamed as [ChatEvent.ToolOutput] (preliminary) and
 * [ChatEvent.SubagentUpdate].
 *
 * Public for agent runtimes that run their own loop and plug into AI Elements
 * (e.g. `ai-elements-koog`), so tool calls look and behave the same everywhere.
 *
 * @return the text fed back to the model.
 */
suspend fun FlowCollector<ChatEvent>.runTool(
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
    val parsed = args.getOrNull()
    emit(
        ChatEvent.ToolInputAvailable(
            id, name, rawArgs.ifBlank { "{}" },
            title = parsed?.let { tool?.titleFor(it) } ?: tool?.title,
            kind = parsed?.let { tool?.kindFor(it) },
            source = tool?.source,
        ),
    )
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
    val updates = Channel<ToolCallContext.Update>(Channel.BUFFERED)
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
            is ToolCallContext.Update.Data -> emit(ChatEvent.Data(update.id, update.name, update.data))
        }
    }
    result.await()
}

