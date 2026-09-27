package dev.ai.elements.core.provider.gemini

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
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import java.util.UUID
import dev.ai.elements.core.model.hasContent
import dev.ai.elements.core.http.str
import dev.ai.elements.core.http.sse
import dev.ai.elements.core.http.obj
import dev.ai.elements.core.http.jsonPost
import dev.ai.elements.core.http.errorMessage
import dev.ai.elements.core.http.DefaultHttpClient
import dev.ai.elements.core.http.BackendJson
import dev.ai.elements.core.agent.runTool
import dev.ai.elements.core.model.images
import dev.ai.elements.core.http.int

/**
 * Google **Gemini** native API (`:streamGenerateContent?alt=sse`).
 *
 * Thought summaries (`thought: true` parts) become reasoning; function calls
 * run on-device. Model parts — including `thoughtSignature`s — are echoed back
 * verbatim on the next step, as Gemini requires for multi-step tool use.
 */
class GeminiBackend(
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String,
    private val systemPrompt: String = "",
    private val tools: List<AgentTool> = emptyList(),
    private val approver: ToolApprover = ToolApprover.AlwaysApprove,
    private val maxSteps: Int = 8,
    private val client: OkHttpClient = DefaultHttpClient,
) : ChatBackend {

    override fun stream(history: List<Message>): Flow<ChatEvent> = flow {
        val contents = history.filter { it.hasContent }.map { it.toContent() }.toMutableList()
        repeat(maxSteps) { step ->
            val parts = streamStep(JsonArray(contents), step)
            val calls = parts.mapNotNull { it.obj("functionCall") }
            if (calls.isEmpty()) {
                emit(ChatEvent.Finish)
                return@flow
            }
            contents += buildJsonObject {
                put("role", "model")
                put("parts", JsonArray(parts))
            }
            val responses = calls.map { call ->
                val id = call.str("id") ?: "call_${UUID.randomUUID()}"
                val name = call.str("name").orEmpty()
                val result = runTool(tools, approver, id, name, call["args"]?.toString() ?: "{}")
                buildJsonObject {
                    putJsonObject("functionResponse") {
                        call.str("id")?.let { put("id", it) }
                        put("name", name)
                        putJsonObject("response") { put("result", result) }
                    }
                }
            }
            contents += buildJsonObject {
                put("role", "user")
                put("parts", JsonArray(responses))
            }
        }
        emit(ChatEvent.Error("Agent stopped after $maxSteps steps"))
    }.flowOn(Dispatchers.IO)

    /** One model call; returns every model part it produced. */
    private suspend fun FlowCollector<ChatEvent>.streamStep(contents: JsonArray, step: Int): List<JsonObject> {
        val body = buildJsonObject {
            put("contents", contents)
            if (systemPrompt.isNotBlank()) putJsonObject("systemInstruction") {
                putJsonArray("parts") { addJsonObject { put("text", systemPrompt) } }
            }
            if (tools.isNotEmpty()) putJsonArray("tools") {
                addJsonObject {
                    putJsonArray("functionDeclarations") {
                        tools.forEach { tool ->
                            addJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", tool.parameters)
                            }
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                putJsonObject("thinkingConfig") { put("includeThoughts", true) }
            }
        }
        val url = baseUrl.trimEnd('/') + "/v1beta/models/$model:streamGenerateContent?alt=sse"
        val textId = "text-$step-${UUID.randomUUID()}"
        val reasoningId = "reasoning-$step-${UUID.randomUUID()}"
        var reasoningOpen = false
        var textOpen = false
        var usage: JsonObject? = null
        val parts = mutableListOf<JsonObject>()

        client.sse(jsonPost(url, body, mapOf("x-goog-api-key" to apiKey))).collect { sse ->
            val chunk = runCatching { BackendJson.parseToJsonElement(sse.data).jsonObject }.getOrNull() ?: return@collect
            chunk.obj("error")?.let { throw ChatBackendException(it.errorMessage()) }
            chunk.obj("usageMetadata")?.let { usage = it }
            val candidate = (chunk["candidates"] as? JsonArray)?.firstOrNull()?.jsonObject ?: return@collect
            val chunkParts = candidate.obj("content")?.get("parts") as? JsonArray ?: return@collect
            chunkParts.forEach { element ->
                val part = element.jsonObject
                parts += part
                val text = part.str("text")
                when {
                    part.obj("functionCall") != null -> {
                        val call = part.obj("functionCall")!!
                        // Stable id for the UI even when Gemini omits one.
                        val id = call.str("id") ?: "call_${UUID.randomUUID()}".also { generated ->
                            parts[parts.lastIndex] = JsonObject(part + ("functionCall" to JsonObject(call + ("id" to JsonPrimitive(generated)))))
                        }
                        emit(ChatEvent.ToolInputStart(id, call.str("name").orEmpty()))
                    }
                    text.isNullOrEmpty() -> Unit
                    part["thought"] == JsonPrimitive(true) -> {
                        reasoningOpen = true
                        emit(ChatEvent.ReasoningDelta(reasoningId, text))
                    }
                    else -> {
                        if (reasoningOpen) { reasoningOpen = false; emit(ChatEvent.ReasoningEnd(reasoningId)) }
                        textOpen = true
                        emit(ChatEvent.TextDelta(textId, text))
                    }
                }
            }
        }
        if (reasoningOpen) emit(ChatEvent.ReasoningEnd(reasoningId))
        if (textOpen) emit(ChatEvent.TextEnd(textId))
        usage?.let {
            emit(ChatEvent.Usage(it.int("promptTokenCount") ?: 0, (it.int("candidatesTokenCount") ?: 0) + (it.int("thoughtsTokenCount") ?: 0)))
        }
        return parts
    }

    private fun Message.toContent(): JsonElement = buildJsonObject {
        put("role", if (role == Role.USER) "user" else "model")
        putJsonArray("parts") {
            images.forEach { image ->
                addJsonObject {
                    val data = image.base64Data
                    if (data != null) putJsonObject("inlineData") {
                        put("mimeType", image.mediaType)
                        put("data", data)
                    } else putJsonObject("fileData") {
                        put("mimeType", image.mediaType)
                        put("fileUri", image.url)
                    }
                }
            }
            if (text.isNotBlank()) addJsonObject { put("text", text) }
        }
    }
}
