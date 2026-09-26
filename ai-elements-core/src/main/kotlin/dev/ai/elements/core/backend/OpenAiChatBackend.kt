package dev.ai.elements.core.backend

import dev.ai.elements.core.ChatBackend
import dev.ai.elements.core.ChatBackendException
import dev.ai.elements.core.ChatEvent
import dev.ai.elements.core.ToolApprover
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

/**
 * Any **OpenAI-compatible** Chat Completions endpoint (OpenAI, CLIProxyAPI,
 * Ollama `/v1`, OpenRouter, Gemini's OpenAI endpoint, LiteLLM, vLLM…).
 *
 * Runs the agent loop on-device: when the model returns `tool_calls`, the
 * matching [AgentTool]s are executed locally (after [approver] for tools that
 * need it) and their results fed back, up to [maxSteps] model calls. Reasoning
 * deltas (`reasoning_content` / `reasoning`) become reasoning parts; image
 * attachments are sent as `image_url` content.
 */
class OpenAiChatBackend(
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String = "",
    private val systemPrompt: String = "",
    private val tools: List<AgentTool> = emptyList(),
    private val approver: ToolApprover = ToolApprover.AlwaysApprove,
    private val maxSteps: Int = 8,
    private val client: OkHttpClient = DefaultHttpClient,
) : ChatBackend {

    private class PendingCall(var id: String, var name: String, val args: StringBuilder = StringBuilder())

    override fun stream(history: List<Message>): Flow<ChatEvent> = flow {
        val messages = buildJsonArray {
            if (systemPrompt.isNotBlank()) addJsonObject { put("role", "system"); put("content", systemPrompt) }
            history.filter { it.hasContent }.forEach { m ->
                addJsonObject {
                    put("role", if (m.role == Role.USER) "user" else "assistant")
                    put("content", m.openAiContent())
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
                val result = runTool(tools, approver, call.id, call.name, call.args.toString())
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
            putJsonObject("stream_options") { put("include_usage", true) }
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
            chunk.obj("usage")?.let { usage ->
                emit(ChatEvent.Usage(usage.int("prompt_tokens") ?: 0, usage.int("completion_tokens") ?: 0))
            }
            val delta = (chunk["choices"] as? JsonArray)?.firstOrNull()?.jsonObject?.obj("delta") ?: return@collect

            val reasoning = delta.str("reasoning_content") ?: delta.str("reasoning")
            if (!reasoning.isNullOrEmpty()) {
                reasoningOpen = true
                emit(ChatEvent.ReasoningDelta(reasoningId, reasoning))
            }
            val content = delta.str("content")
            if (!content.isNullOrEmpty()) {
                if (reasoningOpen) { reasoningOpen = false; emit(ChatEvent.ReasoningEnd(reasoningId)) }
                textOpen = true
                emit(ChatEvent.TextDelta(textId, content))
            }
            (delta["tool_calls"] as? JsonArray)?.forEach { element ->
                val tc = element.jsonObject
                val index = tc.int("index") ?: calls.size
                val fn = tc.obj("function")
                val call = calls.getOrPut(index) { PendingCall("", "") }
                tc.str("id")?.takeIf { it.isNotEmpty() }?.let { call.id = it }
                fn?.str("name")?.takeIf { it.isNotEmpty() }?.let { name ->
                    if (call.id.isEmpty()) call.id = "call_${UUID.randomUUID()}"
                    call.name = name
                    emit(ChatEvent.ToolInputStart(call.id, name))
                }
                fn?.str("arguments")?.takeIf { it.isNotEmpty() }?.let { args ->
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

/** Plain string content, or a parts array when the message carries images. */
internal fun Message.openAiContent(): JsonElement {
    if (images.isEmpty()) return JsonPrimitive(text)
    return buildJsonArray {
        if (text.isNotBlank()) addJsonObject { put("type", "text"); put("text", text) }
        images.forEach { image ->
            addJsonObject {
                put("type", "image_url")
                putJsonObject("image_url") { put("url", image.url) }
            }
        }
    }
}
