package dev.ai.elements.core.provider.openai

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
import okhttp3.OkHttpClient
import dev.ai.elements.core.model.hasContent
import dev.ai.elements.core.http.str
import dev.ai.elements.core.http.DefaultHttpClient
import dev.ai.elements.core.http.BackendJson
import dev.ai.elements.core.model.images
import dev.ai.elements.core.http.int
import dev.ai.elements.core.http.jsonPost
import dev.ai.elements.core.http.obj
import dev.ai.elements.core.agent.runTool
import dev.ai.elements.core.http.sse
import dev.ai.elements.core.http.errorMessage

/**
 * The **OpenAI Responses API** (`POST /responses`, streaming) — OpenAI's
 * current agent-oriented API, also served by Ollama and several gateways.
 *
 * Stateless (`store: false`): every step resends the conversation plus the
 * previous steps' output items (reasoning items with `encrypted_content`,
 * `function_call`s) and `function_call_output`s, so reasoning models keep
 * their chain of thought across tool calls.
 */
class OpenAiResponsesBackend(
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String = "",
    private val instructions: String = "",
    private val tools: List<AgentTool> = emptyList(),
    private val approver: ToolApprover = ToolApprover.AlwaysApprove,
    private val maxSteps: Int = 8,
    private val client: OkHttpClient = DefaultHttpClient,
    /** Extra request headers (e.g. a signed-in provider's account header). */
    private val extraHeaders: Map<String, String> = emptyMap(),
) : ChatBackend {

    override fun stream(history: List<Message>): Flow<ChatEvent> = flow {
        val input = history.filter { it.hasContent }.map { it.toInputItem() }.toMutableList()
        repeat(maxSteps) {
            val output = streamStep(JsonArray(input))
            val calls = output.filter { it.str("type") == "function_call" }
            if (calls.isEmpty()) {
                emit(ChatEvent.Finish)
                return@flow
            }
            // Echo reasoning + function_call items back, then the tool outputs.
            input += output.filter { it.str("type") in setOf("reasoning", "function_call") }
            calls.forEach { call ->
                val callId = call.str("call_id").orEmpty()
                val result = runTool(tools, approver, callId, call.str("name").orEmpty(), call.str("arguments").orEmpty())
                input += buildJsonObject {
                    put("type", "function_call_output")
                    put("call_id", callId)
                    put("output", result)
                }
            }
        }
        emit(ChatEvent.Error("Agent stopped after $maxSteps steps"))
    }.flowOn(Dispatchers.IO)

    /** One model call; returns the completed output items. */
    private suspend fun FlowCollector<ChatEvent>.streamStep(input: JsonArray): List<JsonObject> {
        val body = buildJsonObject {
            put("model", model)
            put("stream", true)
            put("store", false)
            putJsonArray("include") { add(JsonPrimitive("reasoning.encrypted_content")) }
            if (instructions.isNotBlank()) put("instructions", instructions)
            put("input", input)
            if (tools.isNotEmpty()) put("tools", buildJsonArray {
                tools.forEach { tool ->
                    addJsonObject {
                        put("type", "function")
                        put("name", tool.name)
                        put("description", tool.description)
                        put("parameters", tool.parameters)
                    }
                }
            })
        }
        val headers = extraHeaders + ("Authorization" to if (apiKey.isBlank()) "" else "Bearer $apiKey")
        val output = mutableListOf<JsonObject>()
        /** function_call item id → call_id (argument deltas are keyed by item id). */
        val callIds = mutableMapOf<String, String>()
        val openText = mutableSetOf<String>()
        val openReasoning = mutableSetOf<String>()

        client.sse(jsonPost(baseUrl.trimEnd('/') + "/responses", body, headers)).collect { sse ->
            if (sse.data == "[DONE]") return@collect
            val event = runCatching { BackendJson.parseToJsonElement(sse.data).jsonObject }.getOrNull() ?: return@collect
            val itemId = event.str("item_id").orEmpty()
            when (event.str("type") ?: sse.event) {
                "response.output_text.delta" -> {
                    openText += itemId
                    emit(ChatEvent.TextDelta(itemId, event.str("delta").orEmpty()))
                }
                "response.output_text.done" -> if (openText.remove(itemId)) emit(ChatEvent.TextEnd(itemId))
                "response.reasoning_summary_text.delta", "response.reasoning_text.delta" -> {
                    openReasoning += itemId
                    emit(ChatEvent.ReasoningDelta(itemId, event.str("delta").orEmpty()))
                }
                "response.output_item.added" -> event.obj("item")?.let { item ->
                    if (item.str("type") == "function_call") {
                        val callId = item.str("call_id").orEmpty()
                        callIds[item.str("id").orEmpty()] = callId
                        emit(ChatEvent.ToolInputStart(callId, item.str("name").orEmpty()))
                    }
                }
                "response.function_call_arguments.delta" -> callIds[itemId]?.let {
                    emit(ChatEvent.ToolInputDelta(it, event.str("delta").orEmpty()))
                }
                "response.output_item.done" -> event.obj("item")?.let { item ->
                    output += item
                    val id = item.str("id").orEmpty()
                    if (item.str("type") == "reasoning" && openReasoning.remove(id)) emit(ChatEvent.ReasoningEnd(id))
                }
                "response.completed" -> event.obj("response")?.obj("usage")?.let { usage ->
                    emit(ChatEvent.Usage(usage.int("input_tokens") ?: 0, usage.int("output_tokens") ?: 0))
                }
                "response.failed" -> throw ChatBackendException(
                    event.obj("response")?.obj("error")?.errorMessage() ?: "Response failed",
                )
                "error" -> throw ChatBackendException(event.str("message") ?: event.obj("error")?.errorMessage() ?: "Stream error")
            }
        }
        openReasoning.forEach { emit(ChatEvent.ReasoningEnd(it)) }
        openText.forEach { emit(ChatEvent.TextEnd(it)) }
        return output
    }

    private fun Message.toInputItem(): JsonElement = buildJsonObject {
        put("role", if (role == Role.USER) "user" else "assistant")
        if (images.isEmpty() || role != Role.USER) {
            put("content", text)
        } else {
            putJsonArray("content") {
                if (text.isNotBlank()) addJsonObject { put("type", "input_text"); put("text", text) }
                images.forEach { image ->
                    addJsonObject {
                        put("type", "input_image")
                        put("image_url", image.url)
                    }
                }
            }
        }
    }
}
