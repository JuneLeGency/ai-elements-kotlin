package dev.ai.elements.core.provider.anthropic

import dev.ai.elements.core.chat.ChatBackend
import dev.ai.elements.core.chat.ChatBackendException
import dev.ai.elements.core.chat.ChatEvent
import dev.ai.elements.core.chat.ToolApprover
import dev.ai.elements.core.agent.AgentTool
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import java.util.UUID
import dev.ai.elements.core.model.hasContent
import dev.ai.elements.core.http.str
import dev.ai.elements.core.http.sse
import dev.ai.elements.core.http.obj
import dev.ai.elements.core.http.jsonPost
import dev.ai.elements.core.http.int
import dev.ai.elements.core.http.errorMessage
import dev.ai.elements.core.http.DefaultHttpClient
import dev.ai.elements.core.http.BackendJson
import dev.ai.elements.core.agent.runTool
import dev.ai.elements.core.model.images

/**
 * The **Anthropic Messages API** (`POST /v1/messages`, streaming), usable with
 * api.anthropic.com or any Anthropic-compatible proxy.
 *
 * Like [OpenAiChatBackend] it runs the tool loop on-device. Thinking blocks
 * (when the model emits them) are shown as reasoning and echoed back verbatim,
 * with their signatures, on the next step as the API requires.
 */
class AnthropicBackend(
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String,
    private val systemPrompt: String = "",
    private val tools: List<AgentTool> = emptyList(),
    private val approver: ToolApprover = ToolApprover.AlwaysApprove,
    private val maxTokens: Int = 8192,
    private val maxSteps: Int = 8,
    private val client: OkHttpClient = DefaultHttpClient,
) : ChatBackend {

    /** A content block being assembled from stream deltas. */
    private class Block(val type: String, val id: String = "", val name: String = "", val start: JsonObject? = null) {
        val text = StringBuilder()
        val signature = StringBuilder()
        val eventId = "$type-${UUID.randomUUID()}"
    }

    override fun stream(history: List<Message>): Flow<ChatEvent> = flow {
        val messages = history.filter { it.hasContent }.map { m ->
            buildJsonObject {
                put("role", if (m.role == Role.USER) "user" else "assistant")
                put("content", m.anthropicContent())
            }
        }.toMutableList<JsonElement>()

        repeat(maxSteps) {
            val blocks = streamStep(JsonArray(messages))
            val toolUses = blocks.filter { it.type == "tool_use" }
            if (toolUses.isEmpty()) {
                emit(ChatEvent.Finish)
                return@flow
            }
            messages += buildJsonObject {
                put("role", "assistant")
                put("content", buildJsonArray { blocks.forEach { add(it.toContent()) } })
            }
            val results = toolUses.map { block ->
                block to runTool(tools, approver, block.id, block.name, block.text.toString())
            }
            messages += buildJsonObject {
                put("role", "user")
                put("content", buildJsonArray {
                    results.forEach { (block, output) ->
                        addJsonObject {
                            put("type", "tool_result")
                            put("tool_use_id", block.id)
                            put("content", output)
                        }
                    }
                })
            }
        }
        emit(ChatEvent.Error("Agent stopped after $maxSteps steps"))
    }.flowOn(Dispatchers.IO)

    private suspend fun FlowCollector<ChatEvent>.streamStep(messages: JsonArray): List<Block> {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            put("stream", true)
            if (systemPrompt.isNotBlank()) put("system", systemPrompt)
            put("messages", messages)
            if (tools.isNotEmpty()) put("tools", buildJsonArray {
                tools.forEach { tool ->
                    addJsonObject {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("input_schema", tool.parameters)
                    }
                }
            })
        }
        val headers = mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01")
        val blocks = sortedMapOf<Int, Block>()

        client.sse(jsonPost(baseUrl.trimEnd('/') + "/v1/messages", body, headers)).collect { sse ->
            val data = runCatching { BackendJson.parseToJsonElement(sse.data).jsonObject }.getOrNull() ?: return@collect
            val index = data.int("index") ?: -1
            when (data.str("type")) {
                "message_start" -> data.obj("message")?.obj("usage")?.int("input_tokens")?.let {
                    if (it > 0) emit(ChatEvent.Usage(it, 0))
                }
                "message_delta" -> data.obj("usage")?.let { usage ->
                    emit(ChatEvent.Usage(0, usage.int("output_tokens") ?: 0))
                }
                "content_block_start" -> {
                    val cb = data["content_block"]?.jsonObject ?: return@collect
                    val block = Block(cb.str("type").orEmpty(), cb.str("id").orEmpty(), cb.str("name").orEmpty(), cb)
                    blocks[index] = block
                    if (block.type == "tool_use") emit(ChatEvent.ToolInputStart(block.id, block.name))
                }
                "content_block_delta" -> {
                    val block = blocks[index] ?: return@collect
                    val delta = data["delta"]?.jsonObject ?: return@collect
                    when (delta.str("type")) {
                        "text_delta" -> delta.str("text")?.let {
                            block.text.append(it)
                            emit(ChatEvent.TextDelta(block.eventId, it))
                        }
                        "thinking_delta" -> delta.str("thinking")?.let {
                            block.text.append(it)
                            emit(ChatEvent.ReasoningDelta(block.eventId, it))
                        }
                        "signature_delta" -> delta.str("signature")?.let { block.signature.append(it) }
                        "input_json_delta" -> delta.str("partial_json")?.let {
                            block.text.append(it)
                            emit(ChatEvent.ToolInputDelta(block.id, it))
                        }
                    }
                }
                "content_block_stop" -> when (blocks[index]?.type) {
                    "text" -> emit(ChatEvent.TextEnd(blocks.getValue(index).eventId))
                    "thinking" -> emit(ChatEvent.ReasoningEnd(blocks.getValue(index).eventId))
                }
                "error" -> throw ChatBackendException(data["error"]?.errorMessage() ?: "Anthropic stream error")
            }
        }
        return blocks.values.toList()
    }

    private fun Block.toContent(): JsonObject = if (type !in setOf("text", "thinking", "tool_use")) {
        // e.g. redacted_thinking: echo the block exactly as received.
        start ?: buildJsonObject { put("type", type) }
    } else buildJsonObject {
        put("type", type)
        when (type) {
            "text" -> put("text", text.toString())
            "thinking" -> {
                put("thinking", text.toString())
                put("signature", signature.toString())
            }
            "tool_use" -> {
                put("id", id)
                put("name", name)
                put("input", runCatching { BackendJson.parseToJsonElement(text.toString().ifBlank { "{}" }) }
                    .getOrDefault(JsonObject(emptyMap())))
            }
        }
    }

    /** String content, or content blocks with base64 / URL image sources. */
    private fun Message.anthropicContent(): JsonElement {
        if (images.isEmpty()) return JsonPrimitive(text)
        return buildJsonArray {
            images.forEach { image ->
                addJsonObject {
                    put("type", "image")
                    putJsonObject("source") {
                        val data = image.base64Data
                        if (data != null) {
                            put("type", "base64")
                            put("media_type", image.mediaType)
                            put("data", data)
                        } else {
                            put("type", "url")
                            put("url", image.url)
                        }
                    }
                }
            }
            if (text.isNotBlank()) addJsonObject { put("type", "text"); put("text", text) }
        }
    }
}
