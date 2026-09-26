package dev.ai.elements.core.backend

import dev.ai.elements.core.ChatBackendException
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
data class SseEvent(val event: String?, val data: String)

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
 */
internal fun OkHttpClient.sse(request: Request): Flow<SseEvent> = flow {
    val call = newCall(request)
    val cancelHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
    try {
        call.execute().use { response ->
            response.ensureSuccess()
            val source = response.body!!.source()
            var event: String? = null
            val data = StringBuilder()
            while (true) {
                val line = source.readUtf8Line() ?: break
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
                }
            }
            if (data.isNotEmpty()) emit(SseEvent(event, data.toString()))
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
    val detail = runCatching {
        val obj = BackendJson.parseToJsonElement(raw).jsonObject
        val err = obj["error"]
        when {
            err is JsonObject -> err["message"]?.jsonPrimitive?.content
            err != null -> err.jsonPrimitive.content
            else -> obj["detail"]?.toString() ?: obj["message"]?.jsonPrimitive?.content
        }
    }.getOrNull() ?: raw.take(300).ifBlank { message }
    throw ChatBackendException("HTTP $code: $detail")
}

private fun IOException.friendlyMessage(request: Request): String {
    val host = "${request.url.host}:${request.url.port}"
    return when (this) {
        is ConnectException -> "Cannot connect to $host. Is the server running and reachable from this device?"
        is UnknownHostException -> "Unknown host ${request.url.host}"
        is SocketTimeoutException -> "Timed out connecting to $host"
        else -> message ?: this::class.simpleName ?: "Network error"
    }
}
