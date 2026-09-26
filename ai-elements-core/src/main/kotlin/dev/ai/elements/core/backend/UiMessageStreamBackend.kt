package dev.ai.elements.core.backend

import dev.ai.elements.core.ChatBackend
import dev.ai.elements.core.ChatEvent
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.TextPart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * Talks to an agent server speaking a **Vercel AI SDK** streaming protocol —
 * e.g. the bundled PydanticAI server (`server/main.py`), or any AI SDK route:
 *
 * - v5+ **UI Message Stream**: SSE `data: {"type": "text-delta", ...}` chunks
 *   (`toUIMessageStreamResponse()`), ending in `[DONE]`;
 * - v4 **Data Stream**: `0:"text"`, `g:"reasoning"`, `9:{toolCall}`… lines
 *   (`toDataStreamResponse()`). Detected automatically per line.
 *
 * Request: `POST endpoint` with `{trigger, id, messages: UIMessage[]}`; image
 * attachments are sent as `file` parts with `data:` URLs.
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
        val legacy = DataStreamV4Parser()
        client.sse(jsonPost(url, requestBody(history), headers), rawLines = true).collect { sse ->
            if (sse.event == "raw") {
                legacy.parse(sse.data).forEach { emit(it) }
                return@collect
            }
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
                val files = if (message.role == Role.USER) message.images else emptyList()
                if (texts.isEmpty() && files.isEmpty()) return@forEach
                addJsonObject {
                    put("id", message.id)
                    put("role", if (message.role == Role.USER) "user" else "assistant")
                    putJsonArray("parts") {
                        files.forEach { file ->
                            addJsonObject {
                                put("type", "file")
                                put("mediaType", file.mediaType)
                                put("url", file.url)
                                file.filename?.let { put("filename", it) }
                            }
                        }
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
            val type = str("type").orEmpty()
            if (type.startsWith("data-")) {
                val data = chunk["data"] ?: return null
                return ChatEvent.Data(id.ifEmpty { type }, type.removePrefix("data-"), data)
            }
            return when (type) {
                "message-metadata" -> chunk.obj("messageMetadata")?.obj("usage")?.let {
                    ChatEvent.Usage(it.int("inputTokens") ?: 0, it.int("outputTokens") ?: 0)
                }
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
                "tool-output-denied" -> ChatEvent.ToolDenied(toolCallId)
                "file" -> {
                    val url = str("url") ?: return null
                    ChatEvent.File(url.hashCode().toString(), str("mediaType") ?: "application/octet-stream", url)
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

/**
 * AI SDK v4 **Data Stream** protocol: one `<code>:<json>` frame per line.
 * Text has no ids, so a new text/reasoning part starts after every step
 * boundary (`e:`) to keep parts in stream order.
 */
internal class DataStreamV4Parser {
    private var step = 0

    fun parse(line: String): List<ChatEvent> {
        val sep = line.indexOf(':')
        if (sep != 1) return emptyList()
        val payload = runCatching { BackendJson.parseToJsonElement(line.substring(2)) }.getOrNull() ?: return emptyList()
        fun text() = (payload as? JsonPrimitive)?.contentOrNull.orEmpty()
        val obj = payload as? JsonObject
        return when (line[0]) {
            '0' -> listOf(ChatEvent.TextDelta("t$step", text()))
            'g' -> listOf(ChatEvent.ReasoningDelta("r$step", text()))
            'b' -> listOfNotNull(obj?.let { ChatEvent.ToolInputStart(it.str("toolCallId").orEmpty(), it.str("toolName").orEmpty()) })
            'c' -> listOfNotNull(obj?.let { ChatEvent.ToolInputDelta(it.str("toolCallId").orEmpty(), it.str("argsTextDelta").orEmpty()) })
            '9' -> listOfNotNull(obj?.let {
                ChatEvent.ToolInputAvailable(it.str("toolCallId").orEmpty(), it.str("toolName").orEmpty(), it["args"]?.toString() ?: "{}")
            })
            'a' -> listOfNotNull(obj?.let {
                val result = it["result"]
                ChatEvent.ToolOutput(it.str("toolCallId").orEmpty(), (result as? JsonPrimitive)?.contentOrNull ?: result.toString())
            })
            'h' -> listOfNotNull(obj?.let { src ->
                src.str("url")?.let { ChatEvent.SourceUrl(src.str("id") ?: it, it, src.str("title") ?: it) }
            })
            'k' -> listOfNotNull(obj?.let { f ->
                val mime = f.str("mimeType") ?: "application/octet-stream"
                f.str("data")?.let { ChatEvent.File("f$step-${it.hashCode()}", mime, "data:$mime;base64,$it") }
            })
            '3' -> listOf(ChatEvent.Error(text().ifBlank { "Server error" }))
            'e' -> {
                val usage = obj?.obj("usage")
                step++
                listOfNotNull(
                    ChatEvent.TextEnd("t${step - 1}"),
                    ChatEvent.ReasoningEnd("r${step - 1}"),
                    usage?.let { ChatEvent.Usage(it.int("promptTokens") ?: 0, it.int("completionTokens") ?: 0) },
                )
            }
            'd' -> listOf(ChatEvent.Finish)
            else -> emptyList()
        }
    }
}
