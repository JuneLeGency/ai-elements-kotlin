package dev.ai.elements.core.backend

import dev.ai.elements.core.ChatBackend
import dev.ai.elements.core.ChatEvent
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import java.util.UUID

/**
 * The **AG-UI** (Agent–User Interaction) protocol: `POST` a `RunAgentInput`
 * and receive SSE events (`TEXT_MESSAGE_CONTENT`, `TOOL_CALL_START`,
 * `REASONING_MESSAGE_CONTENT`, `RUN_ERROR`…). Served by PydanticAI, LangGraph,
 * CrewAI, Mastra, and the bundled `server/main.py` at `/api/agui`.
 *
 * The agent and its tools run server-side.
 */
class AgUiBackend(
    private val endpoint: String,
    private val apiKey: String = "",
    private val threadId: String = UUID.randomUUID().toString(),
    private val client: OkHttpClient = DefaultHttpClient,
) : ChatBackend {

    override fun stream(history: List<Message>): Flow<ChatEvent> = flow {
        val body = buildJsonObject {
            put("threadId", history.firstOrNull()?.id ?: threadId)
            put("runId", UUID.randomUUID().toString())
            putJsonObject("state") {}
            putJsonArray("messages") {
                history.filter { it.text.isNotBlank() }.forEach { m ->
                    addJsonObject {
                        put("id", m.id)
                        put("role", if (m.role == Role.USER) "user" else "assistant")
                        put("content", m.text)
                    }
                }
            }
            putJsonArray("tools") {}
            putJsonArray("context") {}
            putJsonObject("forwardedProps") {}
        }
        val headers = mapOf("Authorization" to if (apiKey.isBlank()) "" else "Bearer $apiKey")
        val parser = AgUiParser()
        client.sse(jsonPost(endpoint, body, headers)).collect { sse ->
            val event = runCatching { BackendJson.parseToJsonElement(sse.data).jsonObject }.getOrNull() ?: return@collect
            parser.parse(event).forEach { emit(it) }
        }
    }
}

/** Stateful AG-UI event → [ChatEvent] mapping (tool args arrive in pieces). */
internal class AgUiParser {
    private val toolNames = mutableMapOf<String, String>()
    private val toolArgs = mutableMapOf<String, StringBuilder>()
    private var lastTextId = "text"
    private var lastReasoningId = "reasoning"

    fun parse(event: JsonObject): List<ChatEvent> {
        val messageId = event.str("messageId")
        val toolCallId = event.str("toolCallId").orEmpty()
        return when (event.str("type")) {
            "TEXT_MESSAGE_START" -> { lastTextId = messageId ?: lastTextId; emptyList() }
            "TEXT_MESSAGE_CONTENT", "TEXT_MESSAGE_CHUNK" -> {
                lastTextId = messageId ?: lastTextId
                listOf(ChatEvent.TextDelta(lastTextId, event.str("delta").orEmpty()))
            }
            "TEXT_MESSAGE_END" -> listOf(ChatEvent.TextEnd(messageId ?: lastTextId))
            "REASONING_MESSAGE_START", "THINKING_TEXT_MESSAGE_START" -> {
                lastReasoningId = messageId ?: "reasoning-${UUID.randomUUID()}"
                emptyList()
            }
            "REASONING_MESSAGE_CONTENT", "REASONING_MESSAGE_CHUNK", "THINKING_TEXT_MESSAGE_CONTENT" ->
                listOf(ChatEvent.ReasoningDelta(messageId ?: lastReasoningId, event.str("delta").orEmpty()))
            "REASONING_MESSAGE_END", "THINKING_TEXT_MESSAGE_END" -> listOf(ChatEvent.ReasoningEnd(messageId ?: lastReasoningId))
            "TOOL_CALL_START" -> {
                val name = event.str("toolCallName").orEmpty()
                toolNames[toolCallId] = name
                toolArgs[toolCallId] = StringBuilder()
                listOf(ChatEvent.ToolInputStart(toolCallId, name))
            }
            "TOOL_CALL_ARGS", "TOOL_CALL_CHUNK" -> {
                val delta = event.str("delta").orEmpty()
                toolArgs.getOrPut(toolCallId) { StringBuilder() }.append(delta)
                listOf(ChatEvent.ToolInputDelta(toolCallId, delta))
            }
            "TOOL_CALL_END" -> listOf(
                ChatEvent.ToolInputAvailable(toolCallId, toolNames[toolCallId].orEmpty(), toolArgs[toolCallId]?.toString().orEmpty().ifBlank { "{}" }),
            )
            "TOOL_CALL_RESULT" -> listOf(ChatEvent.ToolOutput(toolCallId, event.str("content") ?: event["content"].toString()))
            // Non-standard but common: servers report token usage as a CUSTOM event.
            "CUSTOM" -> if (event.str("name") == "usage") {
                listOfNotNull(event.obj("value")?.let { ChatEvent.Usage(it.int("inputTokens") ?: 0, it.int("outputTokens") ?: 0) })
            } else emptyList()
            "RUN_ERROR" -> listOf(ChatEvent.Error(event.str("message") ?: "Agent run failed"))
            "RUN_FINISHED" -> listOf(ChatEvent.Finish)
            else -> emptyList()
        }
    }
}
