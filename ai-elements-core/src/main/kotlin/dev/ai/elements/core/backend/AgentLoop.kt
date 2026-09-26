package dev.ai.elements.core.backend

import dev.ai.elements.core.ChatEvent
import dev.ai.elements.core.ToolApprover
import dev.ai.elements.core.agent.AgentTool
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
 * Execute one tool call of an on-device agent loop, emitting the tool events.
 * Tools with [AgentTool.requiresApproval] wait for [approver] first.
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
    emit(ChatEvent.ToolInputAvailable(id, name, rawArgs.ifBlank { "{}" }))
    val tool = tools.firstOrNull { it.name == name }
    return try {
        requireNotNull(tool) { "Unknown tool: $name" }
        val args = if (rawArgs.isBlank()) JsonObject(emptyMap()) else BackendJson.parseToJsonElement(rawArgs).jsonObject
        if (tool.requiresApproval) {
            emit(ChatEvent.ToolApprovalRequest(id))
            if (!approver.approve(id)) {
                emit(ChatEvent.ToolDenied(id))
                return DENIED_RESULT
            }
        }
        tool.execute(args).also { emit(ChatEvent.ToolOutput(id, it)) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val message = e.message ?: e::class.simpleName.orEmpty()
        emit(ChatEvent.ToolError(id, message))
        "Error: $message"
    }
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
