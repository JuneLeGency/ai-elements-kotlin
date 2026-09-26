package dev.ai.elements.core.backend

import dev.ai.elements.core.ChatBackend
import dev.ai.elements.core.ChatEvent
import dev.ai.elements.core.config.GatewayConfig
import dev.ai.elements.core.model.ReasoningStep
import dev.ai.elements.core.model.Source
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import java.net.URI
import java.util.UUID
import kotlin.time.TimeSource

/**
 * A [ChatBackend] that consumes the **Vercel AI Data Stream Protocol**.
 *
 * Each SSE line is a single-byte code + `:` + payload:
 * ```
 * 0:{"id":...}                       start
 * 9:<raw text delta>                 text
 * a:<raw reasoning delta>            reasoning
 * d:{"url":...,"title":...}          source-url
 * e:{"toolCallId":...,...}           tool-input-start
 * 8:{"toolName":...,"input":...}     tool-input
 * 3:{"message":...}                  error
 * 2:{"finishReason":...,"usage":{...}}   finish
 * ```
 *
 * The parser is lenient: unknown codes are ignored so the same client works
 * against any AI-SDK-compatible server.
 *
 * @param config supplies the endpoint + model per request.
 * @param authProvider resolves the live auth value (API key or OAuth token) at
 *   request time, so a refreshed token is used without rebuilding the backend.
 */
class VercelDataStreamBackend(
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
            val sourcesPartId = UUID.randomUUID().toString()
            var reasoningStarted = false
            var finished = false

            val body = buildRequestBody(prompt, cfg)
            try {
                channel.trySend(ChatEvent.Start)
                client.postStream(cfg.url, cfg.provider.authHeader, authProvider(), body).use { stream ->
                    if (!stream.isSuccessful) {
                        channel.send(ChatEvent.Error("HTTP ${stream.code}: ${stream.bodyText().ifBlank { stream.message }}"))
                        channel.send(ChatEvent.Done)
                        return@launch
                    }
                    for (raw in stream.lines()) {
                        val line = raw.trimEnd('\r')
                        val sep = line.indexOf(':')
                        if (line.isEmpty() || sep <= 0) continue
                        val code = line.substring(0, sep).trim()
                        val payload = line.substring(sep + 1)
                        when (code) {
                            "0" -> { /* start acknowledged */ }
                            "9" -> channel.send(ChatEvent.TextDelta(textPartId, payload))
                            "a" -> {
                                reasoningStarted = true
                                channel.send(
                                    ChatEvent.ReasoningStepEvent(
                                        reasoningPartId,
                                        ReasoningStep(
                                            id = reasoningPartId,
                                            title = "Thinking",
                                            detail = payload,
                                            state = StepState.INCOMPLETE,
                                        ),
                                    )
                                )
                            }
                            "d" -> {
                                val src = parseSource(payload)
                                if (src != null) channel.send(ChatEvent.Sources(sourcesPartId, src))
                            }
                            "e", "8" -> { /* tool parts: not rendered yet */ }
                            "3" -> {
                                val m = runCatching {
                                    json.parseToJsonElement(payload).jsonObject["message"]?.jsonPrimitive?.content
                                }.getOrNull()
                                channel.send(ChatEvent.Error(m ?: "stream error"))
                            }
                            "2" -> {
                                if (!finished) {
                                    finished = true
                                    val duration = (TimeSource.Monotonic.markNow() - started).inWholeMilliseconds
                                    if (reasoningStarted) channel.send(ChatEvent.ReasoningDone(reasoningPartId, duration))
                                    channel.send(ChatEvent.TextDelta(textPartId, "", done = true))
                                    channel.send(ChatEvent.Done)
                                }
                            }
                            else -> { /* unknown code: ignore for forward-compat */ }
                        }
                    }
                }
                if (!finished) channel.send(ChatEvent.Done)
            } catch (e: Exception) {
                channel.trySend(ChatEvent.Error(e.message ?: e.toString()))
                channel.trySend(ChatEvent.Done)
            } finally {
                channel.close()
            }
        }
        return channel
    }

    private fun buildRequestBody(prompt: String, cfg: GatewayConfig): String {
        val obj = JsonObject(
            linkedMapOf(
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
                "model" to JsonPrimitive(cfg.model),
                "stream" to JsonPrimitive(true),
            )
        )
        return obj.toString()
    }

    private fun parseSource(payload: String): Source? = runCatching {
        val o = json.parseToJsonElement(payload).jsonObject
        val url = o["url"]?.jsonPrimitive?.content ?: return null
        val title = o["title"]?.jsonPrimitive?.content ?: url
        Source(
            id = UUID.randomUUID().toString(),
            title = title,
            url = url,
            domain = runCatching { URI(url).host }.getOrNull(),
        )
    }.getOrNull()

    companion object {
        val DEFAULT_CLIENT = OkHttpClient.Builder().build()
    }
}
