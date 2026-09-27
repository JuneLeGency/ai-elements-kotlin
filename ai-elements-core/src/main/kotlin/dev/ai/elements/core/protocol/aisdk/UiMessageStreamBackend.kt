package dev.ai.elements.core.protocol.aisdk

import dev.ai.elements.core.chat.ChatBackend
import dev.ai.elements.core.chat.ChatEvent
import dev.ai.elements.core.chat.ToolApprover
import dev.ai.elements.core.agent.AgentTool
import dev.ai.elements.core.model.FilePart
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Part
import dev.ai.elements.core.model.ReasoningPart
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.SourcePart
import dev.ai.elements.core.model.TextPart
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.core.model.ToolState
import dev.ai.elements.core.chat.reduce
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
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
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.UUID
import dev.ai.elements.core.http.DefaultHttpClient
import dev.ai.elements.core.http.BackendJson
import dev.ai.elements.core.http.int
import dev.ai.elements.core.http.jsonPost
import dev.ai.elements.core.http.obj
import dev.ai.elements.core.agent.runTool
import dev.ai.elements.core.http.sse
import dev.ai.elements.core.http.str

/**
 * Talks to an agent server speaking a **Vercel AI SDK** streaming protocol —
 * e.g. the bundled Pydantic AI server (`server/main.py`), or any AI SDK route:
 *
 * - v5/v6 **UI Message Stream**: SSE `data: {"type": "text-delta", ...}` chunks
 *   (`toUIMessageStreamResponse()`), ending in `[DONE]`;
 * - v4 **Data Stream**: `0:"text"`, `g:"reasoning"`, `9:{toolCall}`… lines
 *   (`toDataStreamResponse()`). Detected automatically per line.
 *
 * Request: `POST endpoint` with `{trigger, id, messages: UIMessage[]}` — the
 * whole history, including tool parts, as `useChat` sends it.
 *
 * Like `useChat`, the turn continues automatically when the server hands work
 * back to the client:
 * - **tool approval** (AI SDK 6): a `tool-approval-request` asks [approver];
 *   the answer is sent back as an `approval-responded` tool part;
 * - **client-side tools**: a `tool-input-available` for one of [tools] with no
 *   server output runs the tool here and sends its `output-available` part
 *   (`sendAutomaticallyWhen: lastAssistantMessageIsCompleteWithToolCalls`).
 *
 * A tool output that is itself a `UIMessage` (a subagent streaming through
 * preliminary tool results) is shown as that tool's nested run.
 *
 * @param model optional model override, sent as `?model=` (server-specific).
 */
class UiMessageStreamBackend(
    private val endpoint: String,
    private val model: String = "",
    private val apiKey: String = "",
    private val tools: List<AgentTool> = emptyList(),
    private val approver: ToolApprover = ToolApprover.AlwaysApprove,
    private val client: OkHttpClient = DefaultHttpClient,
    private val maxRounds: Int = 8,
) : ChatBackend {

    override fun stream(history: List<Message>): Flow<ChatEvent> = flow {
        val url = endpoint.toHttpUrl().newBuilder()
            .apply { if (model.isNotBlank()) addQueryParameter("model", model) }
            .build().toString()
        val headers = mapOf("Authorization" to if (apiKey.isBlank()) "" else "Bearer $apiKey")
        // The reply as the server sees it, re-sent on every continuation.
        var reply = Message("assistant-${UUID.randomUUID()}", Role.ASSISTANT)
        val approvals = mutableMapOf<String, Approval>()
        val out = object : FlowCollector<ChatEvent> {
            override suspend fun emit(value: ChatEvent) {
                reply = reply.reduce(value, System.currentTimeMillis())
                this@flow.emit(value)
            }
        }

        repeat(maxRounds) { round ->
            val parser = ChunkParser()
            val messages = if (round == 0) history else history + reply
            client.sse(jsonPost(url, requestBody(messages, approvals), headers), rawLines = true).collect { sse ->
                if (sse.event == "raw") {
                    parser.legacy.parse(sse.data).forEach { out.emit(it) }
                    return@collect
                }
                if (sse.data == "[DONE]") return@collect
                val chunk = runCatching { BackendJson.parseToJsonElement(sse.data).jsonObject }.getOrNull() ?: return@collect
                parser.parse(chunk).forEach { out.emit(it) }
            }
            if (parser.failed) return@flow

            // Approval requests the server is waiting on.
            val asked = parser.approvalRequests.filterKeys { id -> reply.tool(id)?.state == ToolState.APPROVAL_REQUESTED }
            // Client-side tool calls without a result.
            val local = tools.map { it.name }.toSet()
            val calls = reply.parts.filterIsInstance<ToolPart>()
                .filter { it.state == ToolState.INPUT_AVAILABLE && it.name in local && it.id !in parser.providerExecuted && it.id !in approvals }
            if (asked.isEmpty() && calls.isEmpty()) {
                out.emit(ChatEvent.Finish)
                return@flow
            }
            asked.forEach { (toolCallId, approvalId) ->
                val approved = approver.approve(toolCallId)
                approvals[toolCallId] = Approval(approvalId, approved)
                out.emit(if (approved) ChatEvent.ToolApproved(toolCallId) else ChatEvent.ToolDenied(toolCallId))
            }
            calls.forEach { call -> out.runTool(tools, approver, call.id, call.name, call.input) }
        }
        emit(ChatEvent.Error("Agent stopped after $maxRounds rounds"))
    }

    private data class Approval(val id: String, val approved: Boolean)

    private fun Message.tool(id: String) = parts.firstOrNull { it is ToolPart && it.id == id } as? ToolPart

    private fun requestBody(history: List<Message>, approvals: Map<String, Approval>) = buildJsonObject {
        put("trigger", "submit-message")
        put("id", history.firstOrNull()?.id ?: "chat")
        putJsonArray("messages") {
            history.forEach { message ->
                val parts = message.parts.mapNotNull { it.toUiPart(message.role, approvals) }
                if (parts.isEmpty()) return@forEach
                addJsonObject {
                    put("id", message.id)
                    put("role", if (message.role == Role.USER) "user" else "assistant")
                    message.metadata?.let { put("metadata", it) }
                    put("parts", JsonArray(parts))
                }
            }
        }
    }

    /** One stream's chunk → [ChatEvent] mapping, plus what the continuation needs. */
    private class ChunkParser {
        val legacy = DataStreamV4Parser()
        val approvalRequests = linkedMapOf<String, String>()
        val providerExecuted = mutableSetOf<String>()
        var failed = false

        fun parse(chunk: JsonObject): List<ChatEvent> {
            when (chunk.str("type")) {
                "tool-approval-request" -> chunk.str("toolCallId")?.let { id -> approvalRequests[id] = chunk.str("approvalId") ?: id }
                "tool-input-start", "tool-input-available", "tool-output-available" ->
                    if ((chunk["providerExecuted"] as? JsonPrimitive)?.contentOrNull == "true") chunk.str("toolCallId")?.let(providerExecuted::add)
                "error", "abort" -> failed = chunk.str("type") == "error"
            }
            return parseChunks(chunk)
        }
    }

    companion object {
        /** Map one UI Message Stream chunk to [ChatEvent]s. */
        fun parseChunks(chunk: JsonObject): List<ChatEvent> {
            fun str(key: String) = chunk[key]?.jsonPrimitive?.contentOrNull
            val id = str("id").orEmpty()
            val toolCallId = str("toolCallId").orEmpty()
            val type = str("type").orEmpty()
            if (type.startsWith("data-")) {
                // Transient data parts are not part of the message.
                if ((chunk["transient"] as? JsonPrimitive)?.contentOrNull == "true") return emptyList()
                val data = chunk["data"] ?: return emptyList()
                return listOf(ChatEvent.Data(id.ifEmpty { type }, type.removePrefix("data-"), data))
            }
            return when (type) {
                // `finish` ends one HTTP response, not necessarily the turn (approvals and client
                // tools continue it); the backend emits Finish once the turn is really over.
                "start", "finish", "message-metadata" -> metadataEvents(chunk.obj("messageMetadata"))
                "text-delta" -> listOf(ChatEvent.TextDelta(id, str("delta").orEmpty()))
                "text-end" -> listOf(ChatEvent.TextEnd(id))
                "reasoning-delta" -> listOf(ChatEvent.ReasoningDelta(id, str("delta").orEmpty()))
                "reasoning-end" -> listOf(ChatEvent.ReasoningEnd(id))
                "tool-input-start" -> listOf(ChatEvent.ToolInputStart(toolCallId, str("toolName").orEmpty(), str("title")))
                "tool-input-delta" -> listOf(ChatEvent.ToolInputDelta(toolCallId, str("inputTextDelta").orEmpty()))
                "tool-input-available" ->
                    listOf(ChatEvent.ToolInputAvailable(toolCallId, str("toolName").orEmpty(), chunk["input"].asText(), str("title")))
                "tool-input-error" -> listOf(
                    ChatEvent.ToolInputAvailable(toolCallId, str("toolName").orEmpty(), chunk["input"].asText(), str("title")),
                    ChatEvent.ToolError(toolCallId, str("errorText").orEmpty()),
                )
                "tool-output-available" -> toolOutput(toolCallId, chunk["output"], (chunk["preliminary"] as? JsonPrimitive)?.contentOrNull == "true")
                "tool-output-error" -> listOf(ChatEvent.ToolError(toolCallId, str("errorText").orEmpty()))
                "tool-approval-request" -> listOf(ChatEvent.ToolApprovalRequest(toolCallId))
                "tool-output-denied" -> listOf(ChatEvent.ToolDenied(toolCallId))
                "source-url" -> {
                    val url = str("url") ?: return emptyList()
                    listOf(ChatEvent.SourceUrl(str("sourceId") ?: url, url, str("title") ?: url))
                }
                "file" -> {
                    val url = str("url") ?: return emptyList()
                    listOf(ChatEvent.File(url.hashCode().toString(), str("mediaType") ?: "application/octet-stream", url))
                }
                "error" -> listOf(ChatEvent.Error(str("errorText") ?: "Server error"))
                else -> emptyList()
            }
        }

        /** Map one chunk; null for chunks that produce nothing (kept for callers of the v5 API). */
        fun parseChunk(chunk: JsonObject): ChatEvent? = parseChunks(chunk).firstOrNull()

        private fun metadataEvents(metadata: JsonObject?): List<ChatEvent> {
            if (metadata == null) return emptyList()
            val usage = metadata.obj("usage")?.let { ChatEvent.Usage(it.int("inputTokens") ?: 0, it.int("outputTokens") ?: 0) }
            val rest = JsonObject(metadata - "usage")
            return listOfNotNull(usage, rest.takeIf { it.isNotEmpty() }?.let { ChatEvent.Metadata(it) })
        }

        /** A `UIMessage` output is a subagent's run (AI SDK subagents); anything else is the tool's result. */
        private fun toolOutput(toolCallId: String, output: JsonElement?, preliminary: Boolean): List<ChatEvent> {
            val nested = (output as? JsonObject)?.takeIf { it["parts"] is JsonArray && it["role"] != null }?.let(::uiMessage)
                ?: return listOf(ChatEvent.ToolOutput(toolCallId, output.asText(), preliminary))
            return buildList {
                add(ChatEvent.SubagentUpdate(toolCallId, nested))
                if (!preliminary) add(ChatEvent.ToolOutput(toolCallId, nested.parts.filterIsInstance<TextPart>().lastOrNull()?.text ?: nested.text))
            }
        }

        /** A `UIMessage` JSON object as a [Message]. */
        internal fun uiMessage(json: JsonObject): Message {
            val parts = (json["parts"] as? JsonArray).orEmpty().mapIndexedNotNull { i, element ->
                val part = element as? JsonObject ?: return@mapIndexedNotNull null
                val type = part.str("type").orEmpty()
                val state = part.str("state")
                when {
                    type == "text" -> TextPart("t$i", part.str("text").orEmpty(), isStreaming = state == "streaming")
                    type == "reasoning" -> ReasoningPart("r$i", part.str("text").orEmpty(), isStreaming = state == "streaming")
                    type == "file" -> FilePart("f$i", part.str("mediaType") ?: "application/octet-stream", part.str("url").orEmpty(), part.str("filename"))
                    type == "source-url" -> SourcePart(part.str("sourceId") ?: "s$i", part.str("url").orEmpty(), part.str("title") ?: part.str("url").orEmpty())
                    type == "dynamic-tool" || type.startsWith("tool-") -> ToolPart(
                        id = part.str("toolCallId") ?: "tool$i",
                        name = part.str("toolName") ?: type.removePrefix("tool-"),
                        state = when (state) {
                            "input-streaming" -> ToolState.INPUT_STREAMING
                            "approval-requested" -> ToolState.APPROVAL_REQUESTED
                            "output-available" -> ToolState.OUTPUT_AVAILABLE
                            "output-error" -> ToolState.OUTPUT_ERROR
                            "output-denied" -> ToolState.OUTPUT_DENIED
                            else -> ToolState.INPUT_AVAILABLE
                        },
                        input = part["input"].asText(),
                        output = part["output"]?.asText(),
                        errorText = part.str("errorText"),
                        title = part.str("title"),
                    )
                    else -> null
                }
            }
            return Message(json.str("id") ?: UUID.randomUUID().toString(), Role.ASSISTANT, parts)
        }

        private fun JsonElement?.asText(): String = when (this) {
            null -> ""
            is JsonPrimitive -> contentOrNull ?: ""
            else -> toString()
        }
    }
}

/** One chat part as a `UIMessage` part (tool parts carry their state, input, output and approval). */
private fun Part.toUiPart(role: Role, approvals: Map<String, Any>): JsonObject? = when (this) {
    is TextPart -> if (text.isBlank()) null else buildJsonObject { put("type", "text"); put("text", text) }
    is ReasoningPart -> if (role == Role.USER || text.isBlank()) null else buildJsonObject { put("type", "reasoning"); put("text", text) }
    is FilePart -> if (role == Role.USER && !isImage && base64Data == null && !url.startsWith("http")) null else buildJsonObject {
        put("type", "file")
        put("mediaType", mediaType)
        put("url", url)
        filename?.let { put("filename", it) }
    }
    is SourcePart -> if (role == Role.USER) null else buildJsonObject {
        put("type", "source-url"); put("sourceId", id); put("url", url); put("title", title)
    }
    is ToolPart -> if (role == Role.USER) null else toUiToolPart(approvals[id])
    else -> null
}

private fun ToolPart.toUiToolPart(approval: Any?): JsonObject? {
    val input = runCatching { BackendJson.parseToJsonElement(input.ifBlank { "{}" }) }.getOrElse { JsonPrimitive(input) }
    val approvalFields = approval?.let {
        val (id, approved) = it.toString().removePrefix("Approval(").removeSuffix(")").split(", ").associate { kv -> kv.substringBefore('=') to kv.substringAfter('=') }
            .let { m -> m["id"].orEmpty() to (m["approved"] == "true") }
        buildJsonObject { put("id", id); put("approved", approved) }
    }
    val state = when {
        approvalFields != null && state != ToolState.OUTPUT_AVAILABLE && state != ToolState.OUTPUT_ERROR -> "approval-responded"
        state == ToolState.OUTPUT_AVAILABLE -> "output-available"
        state == ToolState.OUTPUT_ERROR -> "output-error"
        state == ToolState.OUTPUT_DENIED -> "output-denied"
        state == ToolState.APPROVAL_REQUESTED -> return null
        state == ToolState.INPUT_STREAMING -> return null
        else -> "input-available"
    }
    return buildJsonObject {
        put("type", "tool-$name")
        put("toolCallId", id)
        put("state", state)
        put("input", input)
        title?.let { put("title", it) }
        when (state) {
            "output-available" -> put("output", output?.let { runCatching { BackendJson.parseToJsonElement(it) }.getOrNull()?.takeIf { j -> j !is JsonPrimitive } ?: JsonPrimitive(it) } ?: JsonPrimitive(""))
            "output-error" -> put("errorText", errorText.orEmpty())
        }
        approvalFields?.let { put("approval", it) }
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
