package dev.ai.elements.core.backend

import dev.ai.elements.core.ChatBackend
import dev.ai.elements.core.ChatBackendException
import dev.ai.elements.core.ChatEvent
import dev.ai.elements.core.agent.AgentTool
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import java.util.UUID

/**
 * Any **OpenAI-compatible** Chat Completions endpoint (OpenAI, CLIProxyAPI,
 * Ollama, OpenRouter, Gemini's OpenAI endpoint, LiteLLM, vLLM…).
 *
 * Runs the agent loop on-device: when the model returns `tool_calls`, the
 * matching [AgentTool]s are executed locally and their results fed back, up to
 * [maxSteps] model calls. Reasoning deltas (`reasoning_content` / `reasoning`)
 * are surfaced as reasoning parts.
 */
class OpenAiChatBackend(
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String = "",
    private val systemPrompt: String = "",
    private val tools: List<AgentTool> = emptyList(),
    private val maxSteps: Int = 8,
    private val client: OkHttpClient = DefaultHttpClient,
) : ChatBackend {

    private class PendingCall(var id: String, var name: String, val args: StringBuilder = StringBuilder())

    override fun stream(history: List<Message>): Flow<ChatEvent> = flow {
        val messages = buildJsonArray {
            if (systemPrompt.isNotBlank()) addJsonObject { put("role", "system"); put("content", systemPrompt) }
            history.forEach { m ->
                val text = m.text
                if (text.isNotBlank()) addJsonObject {
                    put("role", if (m.role == Role.USER) "user" else "assistant")
                    put("content", text)
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
                            put("id", call.id)
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", call.name)
                                put("arguments", call.args.toString().ifBlank { "{}" })
                            }
                        }
                    }
                }
            }
            calls.forEach { call ->
                val result = runTool(tools, call.id, call.name, call.args.toString())
                messages += buildJsonObject {
                    put("role", "tool")
                    put("tool_call_id", call.id)
                    put("content", result)
                }
            }
        }
        emit(ChatEvent.Error("Agent stopped after $maxSteps steps"))
    }.flowOn(Dispatchers.IO)

    /** One model call; returns the tool calls it requested (empty = final answer). */
    private suspend fun FlowCollector<ChatEvent>.streamStep(messages: JsonArray, step: Int): List<PendingCall> {
        val body = buildJsonObject {
            put("model", model)
            put("stream", true)
            put("messages", messages)
            if (tools.isNotEmpty()) put("tools", buildJsonArray { tools.forEach { add(it.toOpenAi()) } })
        }
        val headers = mapOf("Authorization" to if (apiKey.isBlank()) "" else "Bearer $apiKey")
        val textId = "text-$step-${UUID.randomUUID()}"
        val reasoningId = "reasoning-$step-${UUID.randomUUID()}"
        var reasoningOpen = false
        var textOpen = false
        val calls = sortedMapOf<Int, PendingCall>()

        client.sse(jsonPost(baseUrl.trimEnd('/') + "/chat/completions", body, headers)).collect { sse ->
            if (sse.data == "[DONE]") return@collect
            val chunk = runCatching { BackendJson.parseToJsonElement(sse.data).jsonObject }.getOrNull() ?: return@collect
            chunk["error"]?.let { throw ChatBackendException(it.errorMessage()) }
            val delta = chunk["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("delta") as? JsonObject
                ?: return@collect

            val reasoning = (delta["reasoning_content"] ?: delta["reasoning"])?.let {
                runCatching { it.jsonPrimitive.contentOrNull }.getOrNull()
            }
            if (!reasoning.isNullOrEmpty()) {
                reasoningOpen = true
                emit(ChatEvent.ReasoningDelta(reasoningId, reasoning))
            }
            val content = runCatching { delta["content"]?.jsonPrimitive?.contentOrNull }.getOrNull()
            if (!content.isNullOrEmpty()) {
                if (reasoningOpen) { reasoningOpen = false; emit(ChatEvent.ReasoningEnd(reasoningId)) }
                textOpen = true
                emit(ChatEvent.TextDelta(textId, content))
            }
            (delta["tool_calls"] as? JsonArray)?.forEach { element ->
                val tc = element.jsonObject
                val index = tc["index"]?.jsonPrimitive?.int ?: calls.size
                val fn = tc["function"] as? JsonObject
                val call = calls.getOrPut(index) { PendingCall("", "") }
                tc["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { call.id = it }
                fn?.get("name")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { name ->
                    if (call.id.isEmpty()) call.id = "call_${UUID.randomUUID()}"
                    call.name = name
                    emit(ChatEvent.ToolInputStart(call.id, name))
                }
                fn?.get("arguments")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { args ->
                    call.args.append(args)
                    if (call.id.isNotEmpty()) emit(ChatEvent.ToolInputDelta(call.id, args))
                }
            }
        }
        if (reasoningOpen) emit(ChatEvent.ReasoningEnd(reasoningId))
        if (textOpen) emit(ChatEvent.TextEnd(textId))
        return calls.values.filter { it.name.isNotEmpty() }
    }
}

internal fun AgentTool.toOpenAi() = buildJsonObject {
    put("type", "function")
    putJsonObject("function") {
        put("name", name)
        put("description", description)
        put("parameters", parameters)
    }
}

/** Execute a tool call, emitting input/output events. Returns the text fed back to the model. */
internal suspend fun FlowCollector<ChatEvent>.runTool(
    tools: List<AgentTool>,
    id: String,
    name: String,
    rawArgs: String,
): String {
    emit(ChatEvent.ToolInputAvailable(id, name, rawArgs.ifBlank { "{}" }))
    val tool = tools.firstOrNull { it.name == name }
    return try {
        requireNotNull(tool) { "Unknown tool: $name" }
        val args = if (rawArgs.isBlank()) JsonObject(emptyMap()) else BackendJson.parseToJsonElement(rawArgs).jsonObject
        tool.execute(args).also { emit(ChatEvent.ToolOutput(id, it)) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val message = e.message ?: e::class.simpleName.orEmpty()
        emit(ChatEvent.ToolError(id, message))
        "Error: $message"
    }
}

internal fun kotlinx.serialization.json.JsonElement.errorMessage(): String =
    (this as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
        ?: runCatching { jsonPrimitive.contentOrNull }.getOrNull()
        ?: toString()
