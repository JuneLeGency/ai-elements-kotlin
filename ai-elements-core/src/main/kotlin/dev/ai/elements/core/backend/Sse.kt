package dev.ai.elements.core.backend

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Result of issuing a streaming HTTP request: the response is open and the
 * caller reads its body line by line.
 */
class SseStream(
    val code: Int,
    val message: String,
    val body: okhttp3.ResponseBody?,
) : AutoCloseable {
    val isSuccessful: Boolean get() = code in 200..299

    /** Read the body as a sequence of lines (blocks on the socket). */
    fun lines(): Sequence<String> {
        val b = body ?: return emptySequence()
        return b.byteStream().bufferedReader().lineSequence()
    }

    /** Consume + return the body as text (for short error payloads). */
    fun bodyText(): String = runCatching { body?.string() }.getOrNull().orEmpty()

    override fun close() {
        body?.close()
    }
}

private val JSON = "application/json".toMediaType()

/**
 * Shared transport for the gateway backends.
 *
 * Both [VercelDataStreamBackend] and [OpenAiBackend] POST a JSON body to a
 * streaming endpoint and read the response line by line. This helper owns the
 * OkHttp call + lifecycle so the backends only deal with protocol parsing.
 *
 * @param authHeader name of the auth header; omitted when [authValue] is blank.
 */
fun OkHttpClient.postStream(
    url: String,
    authHeader: String,
    authValue: String,
    body: String,
): SseStream {
    val request = Request.Builder()
        .url(url)
        .header("Content-Type", "application/json")
        .header("Accept", "text/event-stream")
        .also { if (authHeader.isNotEmpty() && authValue.isNotEmpty()) it.header(authHeader, authValue) }
        .post(body.toRequestBody(JSON))
        .build()
    val response = newCall(request).execute()
    return SseStream(response.code, response.message, response.body)
}
