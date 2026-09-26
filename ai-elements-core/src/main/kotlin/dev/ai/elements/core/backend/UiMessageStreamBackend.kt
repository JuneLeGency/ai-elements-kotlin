package dev.ai.elements.core.backend

import dev.ai.elements.core.ChatBackend
import dev.ai.elements.core.ChatEvent
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.TextPart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.addJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * Talks to an agent server speaking the **Vercel AI SDK v5 UI Message Stream
 * protocol** — e.g. the bundled PydanticAI server (`server/main.py`), or any
 * AI SDK `toUIMessageStreamResponse()` route.
 *
 * Request: `POST endpoint` with `{trigger, id, messages: UIMessage[]}`.
 * Response: SSE `data: {"type": "text-delta", ...}` chunks, ending in `[DONE]`.
 *
 * The agent (and its tools) run on the server; this backend only renders.
 *
 * @param model optional model override, sent as `?model=` (server-specific).
 */
class UiMessageStreamBackend(
    private val endpoint: String,
    private val model: String = "",
    private val apiKey: String = "",
    private val client: OkHttpClient = DefaultHttpClient,
) : ChatBackend {

    override fun stream(history: List<Message>): Flow<ChatEvent> = flow {
        val url = endpoint.toHttpUrl().newBuilder()
            .apply { if (model.isNotBlank()) addQueryParameter("model", model) }
            .build().toString()
        val headers = mapOf("Authorization" to if (apiKey.isBlank()) "" else "Bearer $apiKey")
        client.sse(jsonPost(url, requestBody(history), headers)).collect { sse ->
            if (sse.data == "[DONE]") return@collect
            val chunk = runCatching { BackendJson.parseToJsonElement(sse.data).jsonObject }.getOrNull()
                ?: return@collect
            parseChunk(chunk)?.let { emit(it) }
        }
    }

    private fun requestBody(history: List<Message>) = buildJsonObject {
        put("trigger", "submit-message")
        put("id", history.firstOrNull()?.id ?: "chat")
        putJsonArray("messages") {
            history.forEach { message ->
                val texts = message.parts.filterIsInstance<TextPart>().filter { it.text.isNotBlank() }
                if (texts.isEmpty()) return@forEach
                addJsonObject {
                    put("id", message.id)
                    put("role", if (message.role == Role.USER) "user" else "assistant")
                    putJsonArray("parts") {
                        texts.forEach { part ->
                            addJsonObject {
                                put("type", "text")
                                put("text", part.text)
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        /** Map one UI Message Stream chunk to a [ChatEvent]; null for chunks we don't render. */
        fun parseChunk(chunk: JsonObject): ChatEvent? {
            fun str(key: String) = chunk[key]?.jsonPrimitive?.contentOrNull
            val id = str("id").orEmpty()
            val toolCallId = str("toolCallId").orEmpty()
            return when (str("type")) {
                "text-delta" -> ChatEvent.TextDelta(id, str("delta").orEmpty())
                "text-end" -> ChatEvent.TextEnd(id)
                "reasoning-delta" -> ChatEvent.ReasoningDelta(id, str("delta").orEmpty())
                "reasoning-end" -> ChatEvent.ReasoningEnd(id)
                "tool-input-start" -> ChatEvent.ToolInputStart(toolCallId, str("toolName").orEmpty())
                "tool-input-delta" -> ChatEvent.ToolInputDelta(toolCallId, str("inputTextDelta").orEmpty())
                "tool-input-available" ->
                    ChatEvent.ToolInputAvailable(toolCallId, str("toolName").orEmpty(), chunk["input"].asText())
                "tool-output-available" -> ChatEvent.ToolOutput(toolCallId, chunk["output"].asText())
                "tool-output-error", "tool-input-error" -> ChatEvent.ToolError(toolCallId, str("errorText").orEmpty())
                "source-url" -> {
                    val url = str("url") ?: return null
                    ChatEvent.SourceUrl(str("sourceId") ?: url, url, str("title") ?: url)
                }
                "error" -> ChatEvent.Error(str("errorText") ?: "Server error")
                "finish" -> ChatEvent.Finish
                else -> null
            }
        }

        private fun JsonElement?.asText(): String = when (this) {
            null -> ""
            is JsonPrimitive -> contentOrNull ?: ""
            else -> toString()
        }
    }
}
