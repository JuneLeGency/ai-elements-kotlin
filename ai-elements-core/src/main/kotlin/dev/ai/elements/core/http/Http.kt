package dev.ai.elements.core.http

import dev.ai.elements.core.chat.ChatBackendException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** One Server-Sent Event. [data] joins multi-line `data:` fields with `\n`. */
internal data class SseEvent(val event: String?, val data: String)

internal val BackendJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

/** Shared client: no read timeout while streaming (reasoning models can pause for minutes). */
val DefaultHttpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(0, TimeUnit.SECONDS)
    .build()

private val JSON_MEDIA = "application/json".toMediaType()

internal fun jsonPost(url: String, body: JsonElement, headers: Map<String, String>): Request =
    Request.Builder()
        .url(url)
        .post(body.toString().toRequestBody(JSON_MEDIA))
        .header("Accept", "text/event-stream")
        .apply { headers.forEach { (k, v) -> if (v.isNotEmpty()) header(k, v) } }
        .build()

/**
 * Execute [request] and stream its body as SSE events. Cancelling the collector
 * cancels the OkHttp call, which unblocks the socket read immediately.
 *
 * @param rawLines also emit lines that are not SSE fields as `SseEvent("raw", line)`
 *   (the AI SDK v4 Data Stream protocol is line-based, not SSE).
 */
internal fun OkHttpClient.sse(request: Request, rawLines: Boolean = false): Flow<SseEvent> = streamLines(request) { lines ->
    var event: String? = null
    val data = StringBuilder()
    for (line in lines) {
        when {
            line.isEmpty() -> {
                if (data.isNotEmpty()) emit(SseEvent(event, data.toString()))
                event = null
                data.clear()
            }
            line.startsWith(":") -> Unit
            line.startsWith("event:") -> event = line.substring(6).trim()
            line.startsWith("data:") -> {
                if (data.isNotEmpty()) data.append('\n')
                data.append(line.substring(5).removePrefix(" "))
            }
            line.startsWith("id:") || line.startsWith("retry:") -> Unit
            rawLines -> emit(SseEvent("raw", line))
        }
    }
    if (data.isNotEmpty()) emit(SseEvent(event, data.toString()))
}

/** Newline-delimited JSON (Ollama's native `/api/chat`): one JSON object per line. */
internal fun OkHttpClient.ndjson(request: Request): Flow<JsonObject> = streamLines(request) { lines ->
    for (line in lines) {
        if (line.isBlank()) continue
        val obj = runCatching { BackendJson.parseToJsonElement(line).jsonObject }.getOrNull() ?: continue
        emit(obj)
    }
}

private fun <T> OkHttpClient.streamLines(
    request: Request,
    parse: suspend kotlinx.coroutines.flow.FlowCollector<T>.(Sequence<String>) -> Unit,
): Flow<T> = flow {
    val call = newCall(request)
    val cancelHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
    try {
        call.execute().use { response ->
            response.ensureSuccess()
            response.ensureStreaming()
            val source = response.body!!.source()
            parse(generateSequence { source.readUtf8Line() })
        }
    } catch (e: IOException) {
        if (call.isCanceled()) throw CancellationException("Request cancelled")
        throw ChatBackendException(e.friendlyMessage(request), e)
    } finally {
        cancelHandle?.dispose()
    }
}.flowOn(Dispatchers.IO)

/** GET a JSON document (model lists, health checks). */
suspend fun OkHttpClient.getJson(url: String, headers: Map<String, String> = emptyMap()): JsonObject =
    withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get()
            .apply { headers.forEach { (k, v) -> if (v.isNotEmpty()) header(k, v) } }
            .build()
        try {
            newCall(request).execute().use { response ->
                response.ensureSuccess()
                BackendJson.parseToJsonElement(response.body!!.string()).jsonObject
            }
        } catch (e: IOException) {
            throw ChatBackendException(e.friendlyMessage(request), e)
        }
    }

private fun Response.ensureSuccess() {
    if (isSuccessful) return
    val raw = runCatching { body?.string() }.getOrNull().orEmpty()
    throw ChatBackendException("HTTP $code: ${errorDetail(raw) ?: raw.take(300).ifBlank { message }}", statusCode = code)
}

/**
 * Some gateways answer a streaming request with `200` and a plain JSON error
 * body (e.g. `{"status":"435","msg":"Model not support"}`). Surface it instead
 * of silently producing an empty reply.
 */
private fun Response.ensureStreaming() {
    val type = header("Content-Type").orEmpty().lowercase()
    if (!type.startsWith("application/json")) return
    val raw = runCatching { body?.string() }.getOrNull().orEmpty()
    throw ChatBackendException("Provider returned a non-streaming response: ${errorDetail(raw) ?: raw.take(300)}")
}

private fun errorDetail(raw: String): String? = runCatching {
    val obj = BackendJson.parseToJsonElement(raw).jsonObject
    val err = obj["error"]
    when {
        err is JsonObject -> err["message"]?.jsonPrimitive?.content
        err != null -> err.jsonPrimitive.content
        else -> obj["detail"]?.toString()
            ?: obj["message"]?.jsonPrimitive?.content
            ?: obj["msg"]?.jsonPrimitive?.content
    }
}.getOrNull()

private fun IOException.friendlyMessage(request: Request): String {
    val host = "${request.url.host}:${request.url.port}"
    return when (this) {
        is ConnectException -> "Cannot connect to $host. Is the server running and reachable from this device?"
        is UnknownHostException -> "Unknown host ${request.url.host}"
        is SocketTimeoutException -> "Timed out connecting to $host"
        else -> message ?: this::class.simpleName ?: "Network error"
    }
}
