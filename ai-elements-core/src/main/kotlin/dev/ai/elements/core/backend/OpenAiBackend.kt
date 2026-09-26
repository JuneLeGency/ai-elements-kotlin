package dev.ai.elements.core.backend

import dev.ai.elements.core.ChatBackend
import dev.ai.elements.core.ChatEvent
import dev.ai.elements.core.config.GatewayConfig
import dev.ai.elements.core.model.ReasoningStep
import dev.ai.elements.core.model.StepState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import java.util.UUID
import kotlin.time.TimeSource

/**
 * A [ChatBackend] for any **OpenAI-compatible** endpoint
 * (CLIProxyAPI, LiteLLM, freellmapi, Ollama `/v1`, ...).
 *
 * Talks `POST {baseUrl}/chat/completions` with `stream:true` and parses the
 * standard `data: {...}` SSE chunks, mapping `choices[0].delta.content` into
 * [ChatEvent.TextDelta]. Reasoning tokens (`delta.reasoning_content` /
 * `delta.reasoning`) are surfaced as a ReasoningPart.
 *
 * @param authProvider resolves the live auth value at request time.
 */
class OpenAiBackend(
    private val config: () -> GatewayConfig,
    private val authProvider: suspend () -> String = { "" },
    private val client: OkHttpClient = DEFAULT_CLIENT,
) : ChatBackend {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun generate(prompt: String): Channel<ChatEvent> {
        val channel = Channel<ChatEvent>(Channel.UNLIMITED)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val cfg = config()
            val started = TimeSource.Monotonic.markNow()
            val textPartId = UUID.randomUUID().toString()
            val reasoningPartId = UUID.randomUUID().toString()
            var reasoningStarted = false
            var finished = false

            val body = buildRequestBody(prompt, cfg)
            val authValue = authProvider()
            android.util.Log.i("AIElems", "OpenAi request url=${cfg.url} model=${cfg.model} auth=${if (authValue.isBlank()) "NONE" else "Bearer(${authValue.length})"}")
            try {
                channel.trySend(ChatEvent.Start)
                client.postStream(cfg.url, cfg.provider.authHeader, authValue, body).use { stream ->
                    android.util.Log.i("AIElems", "OpenAi response code=${stream.code} success=${stream.isSuccessful}")
                    if (!stream.isSuccessful) {
                        val errBody = stream.bodyText().take(300)
                        android.util.Log.e("AIElems", "OpenAi HTTP error ${stream.code}: $errBody")
                        channel.send(ChatEvent.Error("HTTP ${stream.code}: ${stream.bodyText().ifBlank { stream.message }}"))
                        channel.send(ChatEvent.Done)
                        return@launch
                    }
                    for (raw in stream.lines()) {
                        val line = raw.trim()
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data == "[DONE]") continue
                        val delta = parseDelta(data) ?: continue
                        val reasoning = delta.reasoning
                        if (!reasoning.isNullOrEmpty()) {
                            reasoningStarted = true
                            channel.send(
                                ChatEvent.ReasoningStepEvent(
                                    reasoningPartId,
                                    ReasoningStep(
                                        id = reasoningPartId,
                                        title = "Thinking",
                                        detail = reasoning,
                                        state = StepState.INCOMPLETE,
                                    ),
                                )
                            )
                        }
                        val content = delta.content
                        if (!content.isNullOrEmpty()) {
                            channel.send(ChatEvent.TextDelta(textPartId, content))
                        }
                    }
                }
                if (!finished) {
                    finished = true
                    val duration = (TimeSource.Monotonic.markNow() - started).inWholeMilliseconds
                    if (reasoningStarted) channel.send(ChatEvent.ReasoningDone(reasoningPartId, duration))
                    channel.send(ChatEvent.TextDelta(textPartId, "", done = true))
                    channel.send(ChatEvent.Done)
                }
            } catch (e: Exception) {
                android.util.Log.e("AIElems", "OpenAi exception", e)
                channel.trySend(ChatEvent.Error(e.message ?: e.toString()))
                channel.trySend(ChatEvent.Done)
            } finally {
                channel.close()
            }
        }
        return channel
    }

    private class Delta(val content: String?, val reasoning: String?)

    private fun parseDelta(data: String): Delta? = runCatching {
        val o = json.parseToJsonElement(data).jsonObject
        val choices = o["choices"]?.jsonArray ?: return null
        if (choices.isEmpty()) return null
        val deltaObj = choices.first().jsonObject["delta"]?.jsonObject ?: return null
        val content = deltaObj["content"]?.jsonPrimitive?.content
        val reasoning = (deltaObj["reasoning_content"] ?: deltaObj["reasoning"])?.jsonPrimitive?.content
        Delta(content, reasoning)
    }.getOrNull()

    private fun buildRequestBody(prompt: String, cfg: GatewayConfig): String {
        val obj = JsonObject(
            linkedMapOf(
                "model" to JsonPrimitive(cfg.model),
                "stream" to JsonPrimitive(true),
                "temperature" to JsonPrimitive(cfg.temperature),
                "messages" to JsonArray(
                    listOf(
                        JsonObject(
                            linkedMapOf(
                                "role" to JsonPrimitive("user"),
                                "content" to JsonPrimitive(prompt),
                            )
                        )
                    )
                ),
            )
        )
        return obj.toString()
    }

    companion object {
        val DEFAULT_CLIENT = OkHttpClient.Builder().build()
    }
}
