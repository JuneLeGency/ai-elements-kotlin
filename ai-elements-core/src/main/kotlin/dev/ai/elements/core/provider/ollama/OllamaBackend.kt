package dev.ai.elements.core.provider.ollama

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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.OkHttpClient
import java.util.UUID
import dev.ai.elements.core.provider.openai.toOpenAi
import dev.ai.elements.core.model.hasContent
import dev.ai.elements.core.http.str
import dev.ai.elements.core.http.obj
import dev.ai.elements.core.http.ndjson
import dev.ai.elements.core.http.jsonPost
import dev.ai.elements.core.http.int
import dev.ai.elements.core.http.DefaultHttpClient
import dev.ai.elements.core.agent.runTool
import dev.ai.elements.core.model.images

/**
 * Ollama's **native** chat API (`POST /api/chat`, newline-delimited JSON).
 * Unlike its `/v1` OpenAI shim it exposes `think` (reasoning) directly and
 * takes images as raw base64. The agent loop runs on-device.
 */
class OllamaBackend(
    private val baseUrl: String,
    private val model: String,
    private val systemPrompt: String = "",
    private val tools: List<AgentTool> = emptyList(),
    private val approver: ToolApprover = ToolApprover.AlwaysApprove,
    private val think: Boolean = true,
    private val maxSteps: Int = 8,
    private val client: OkHttpClient = DefaultHttpClient,
) : ChatBackend {

    private class Call(val id: String, val name: String, val arguments: JsonObject)

    override fun stream(history: List<Message>): Flow<ChatEvent> = flow {
        val messages = buildJsonArray {
            if (systemPrompt.isNotBlank()) addJsonObject { put("role", "system"); put("content", systemPrompt) }
            history.filter { it.hasContent }.forEach { m ->
                addJsonObject {
                    put("role", if (m.role == Role.USER) "user" else "assistant")
                    put("content", m.text)
                    val images = m.images.mapNotNull { it.base64Data }
                    if (images.isNotEmpty()) putJsonArray("images") { images.forEach { add(JsonPrimitive(it)) } }
                }
            }
        }.toMutableList()

        repeat(maxSteps) { step ->
            val calls = streamStep(JsonArray(messages), step)
            if (calls.isEmpty()) {
                emit(ChatEvent.Finish)
                return@flow
            }
            messages += buildJsonObject {
                put("role", "assistant")
                put("content", "")
                putJsonArray("tool_calls") {
                    calls.forEach { call ->
                        addJsonObject {
                            put("function", buildJsonObject {
                                put("name", call.name)
                                put("arguments", call.arguments)
                            })
                        }
                    }
                }
            }
            calls.forEach { call ->
                val result = runTool(tools, approver, call.id, call.name, call.arguments.toString())
                messages += buildJsonObject {
                    put("role", "tool")
                    put("tool_name", call.name)
                    put("content", result)
                }
            }
        }
        emit(ChatEvent.Error("Agent stopped after $maxSteps steps"))
    }.flowOn(Dispatchers.IO)

    private suspend fun FlowCollector<ChatEvent>.streamStep(messages: JsonArray, step: Int): List<Call> {
        val body = buildJsonObject {
            put("model", model)
            put("stream", true)
            put("think", think)
            put("messages", messages)
            if (tools.isNotEmpty()) put("tools", buildJsonArray { tools.forEach { add(it.toOpenAi()) } })
        }
        val textId = "text-$step-${UUID.randomUUID()}"
        val reasoningId = "reasoning-$step-${UUID.randomUUID()}"
        var reasoningOpen = false
        var textOpen = false
        val calls = mutableListOf<Call>()

        client.ndjson(jsonPost(baseUrl.trimEnd('/') + "/api/chat", body, emptyMap())).collect { chunk ->
            chunk.str("error")?.let { throw ChatBackendException(it) }
            val message = chunk.obj("message")
            message?.str("thinking")?.takeIf { it.isNotEmpty() }?.let {
                reasoningOpen = true
                emit(ChatEvent.ReasoningDelta(reasoningId, it))
            }
            message?.str("content")?.takeIf { it.isNotEmpty() }?.let {
                if (reasoningOpen) { reasoningOpen = false; emit(ChatEvent.ReasoningEnd(reasoningId)) }
                textOpen = true
                emit(ChatEvent.TextDelta(textId, it))
            }
            (message?.get("tool_calls") as? JsonArray)?.forEach { element ->
                val tc = element.jsonObject
                val fn = tc.obj("function") ?: return@forEach
                val call = Call(
                    id = tc.str("id") ?: "call_${UUID.randomUUID()}",
                    name = fn.str("name").orEmpty(),
                    arguments = fn.obj("arguments") ?: JsonObject(emptyMap()),
                )
                calls += call
                emit(ChatEvent.ToolInputStart(call.id, call.name))
            }
            if (chunk["done"] == JsonPrimitive(true)) {
                emit(ChatEvent.Usage(chunk.int("prompt_eval_count") ?: 0, chunk.int("eval_count") ?: 0))
            }
        }
        if (reasoningOpen) emit(ChatEvent.ReasoningEnd(reasoningId))
        if (textOpen) emit(ChatEvent.TextEnd(textId))
        return calls
    }
}
