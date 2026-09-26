package dev.ai.elements.core.mcp

import dev.ai.elements.core.backend.DefaultHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.encoding.Base64

/**
 * A [Model Context Protocol](https://modelcontextprotocol.io) client over the
 * **Streamable HTTP** transport.
 *
 * It speaks the current revision ([MODERN_VERSION], stateless: every request
 * carries its protocol version, client info and capabilities in `_meta`, and
 * the `MCP-Protocol-Version`, `Mcp-Method`, `Mcp-Name` and `Mcp-Param-*`
 * headers mirror them) and falls back to the legacy, session-based revisions
 * (`initialize` handshake, `Mcp-Session-Id`) when the server turns out to be
 * one — the "dual-era" client of the spec. The era is detected on the first
 * request and cached. The deprecated HTTP+SSE transport is not supported.
 *
 * Each request is its own POST; the reply is a JSON object or an SSE stream
 * carrying `notifications/progress` before the response. Cancelling the
 * calling coroutine closes the stream, which is the cancellation signal.
 *
 * The client declares no optional client capabilities (sampling, elicitation,
 * roots), so conforming servers never ask for them.
 *
 * @param headers sent with every request, e.g. a static API key header.
 * @param auth supplies (and refreshes) bearer tokens; see [McpAuth].
 */
class McpClient(
    val url: String,
    private val headers: Map<String, String> = emptyMap(),
    private val auth: McpAuth? = null,
    private val http: OkHttpClient = DefaultHttpClient,
    private val clientInfo: McpImplementation = McpImplementation("ai-elements-kotlin", "0.3.0"),
) {
    private sealed interface Era {
        data object Modern : Era
        data class Legacy(val version: String, val sessionId: String?) : Era
    }

    private val eraLock = Mutex()
    @Volatile private var era: Era? = null
    private val ids = AtomicLong()

    /** Name and version the server reported (legacy `initialize` or `server/discover`). */
    @Volatile var serverInfo: McpImplementation? = null
        private set

    /** Usage hints the server gives the model, if any. */
    @Volatile var instructions: String? = null
        private set

    /** The negotiated protocol version, once known. */
    val protocolVersion: String? get() = when (val e = era) {
        Era.Modern -> MODERN_VERSION
        is Era.Legacy -> e.version
        null -> null
    }

    /** All tools, following pagination. Tools with invalid `x-mcp-header` annotations are dropped, as the spec requires. */
    suspend fun listTools(): List<McpTool> = paginate("tools/list", "tools") { it.toTool() }
        .filter { tool -> mcpHeaderParams(tool.inputSchema) != null }

    /**
     * Call [tool] with [arguments]. [onProgress] receives the server's
     * progress notifications while it runs.
     */
    suspend fun callTool(tool: McpTool, arguments: JsonObject, onProgress: (suspend (McpProgress) -> Unit)? = null): McpToolResult {
        val paramHeaders = mcpHeaderParams(tool.inputSchema).orEmpty().mapNotNull { (path, header) ->
            valueAt(arguments, path)?.let { "Mcp-Param-$header" to encodeHeaderValue(it) }
        }.toMap()
        val params = buildJsonObject {
            put("name", tool.name)
            put("arguments", arguments)
        }
        val result = request("tools/call", params, name = tool.name, extraHeaders = paramHeaders, onProgress = onProgress)
        if (result["inputRequests"] != null) {
            throw McpException("${tool.name} asked for user input, which this client does not support.")
        }
        return result.toToolResult()
    }

    /** Call a tool by name (no `x-mcp-header` mirroring; prefer the [McpTool] overload). */
    suspend fun callTool(name: String, arguments: JsonObject, onProgress: (suspend (McpProgress) -> Unit)? = null): McpToolResult =
        callTool(McpTool(name = name, inputSchema = JsonObject(emptyMap())), arguments, onProgress)

    suspend fun listResources(): List<McpResource> = paginate("resources/list", "resources") {
        val uri = it.str("uri") ?: return@paginate null
        McpResource(uri, it.str("name") ?: uri, it.str("title"), it.str("description"), it.str("mimeType"))
    }

    /** The contents of the resource at [uri] (text and/or base64 blobs). */
    suspend fun readResource(uri: String): List<McpContent.Resource> =
        request("resources/read", buildJsonObject { put("uri", uri) }, name = uri)
            .arr("contents")?.mapNotNull { (it as? JsonObject)?.let { c -> McpContent.Resource(c.str("uri") ?: uri, c.str("mimeType"), c.str("text"), c.str("blob")) } }
            .orEmpty()

    suspend fun listPrompts(): List<McpPrompt> = paginate("prompts/list", "prompts") { p ->
        val name = p.str("name") ?: return@paginate null
        McpPrompt(
            name, p.str("title"), p.str("description"),
            p.arr("arguments")?.mapNotNull { a -> (a as? JsonObject)?.let { McpPromptArgument(it.str("name").orEmpty(), it.str("description"), it.bool("required") ?: false) } }.orEmpty(),
        )
    }

    /** Render prompt [name]; returns its messages as (role, content) pairs. */
    suspend fun getPrompt(name: String, arguments: Map<String, String> = emptyMap()): List<Pair<String, McpContent>> =
        request("prompts/get", buildJsonObject {
            put("name", name)
            putJsonObject("arguments") { arguments.forEach { (k, v) -> put(k, v) } }
        }, name = name).arr("messages")?.mapNotNull { m ->
            val msg = m as? JsonObject ?: return@mapNotNull null
            val content = msg.obj("content")?.toContent() ?: return@mapNotNull null
            (msg.str("role") ?: "user") to content
        }.orEmpty()

    /** Ends a legacy session (`DELETE`); a no-op for the stateless revision. */
    suspend fun close() {
        val legacy = era as? Era.Legacy ?: return
        val session = legacy.sessionId ?: return
        runCatching {
            withContext(Dispatchers.IO) {
                http.newCall(baseRequest().header("Mcp-Session-Id", session).delete().build()).execute().close()
            }
        }
        era = null
    }

    // --- JSON-RPC over Streamable HTTP ---------------------------------------------------------

    private suspend fun <T> paginate(method: String, key: String, map: (JsonObject) -> T?): List<T> {
        val out = mutableListOf<T>()
        var cursor: String? = null
        var pages = 0
        do {
            val result = request(method, buildJsonObject { cursor?.let { put("cursor", it) } })
            result.arr(key)?.forEach { item -> (item as? JsonObject)?.let(map)?.let(out::add) }
            cursor = result.str("nextCursor")?.takeIf { it.isNotEmpty() }
        } while (cursor != null && ++pages < MAX_PAGES)
        return out
    }

    /**
     * Send one request and return its `result`, detecting the server's era
     * on first use and retrying once after a token refresh or an expired
     * legacy session.
     */
    private suspend fun request(
        method: String,
        params: JsonObject,
        name: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        onProgress: (suspend (McpProgress) -> Unit)? = null,
    ): JsonObject {
        var refreshed = false
        var reinitialized = false
        while (true) {
            val current = era ?: eraLock.withLock { era } ?: Era.Modern
            val outcome = send(current, method, params, name, extraHeaders, onProgress)
            when (outcome) {
                is Outcome.Result -> {
                    if (era == null) era = current
                    return outcome.result
                }
                is Outcome.Unauthorized -> {
                    if (!refreshed && auth?.refresh() == true) { refreshed = true; continue }
                    val challenge = parseChallenge(outcome.wwwAuthenticate)
                    throw McpAuthRequiredException(url, challenge["resource_metadata"], challenge["scope"])
                }
                is Outcome.Failure -> {
                    val legacyServer = current == Era.Modern && era == null && outcome.status in setOf(400, 404, 405) &&
                        (outcome.error == null || outcome.error.code !in MODERN_ERROR_CODES)
                    val unsupported = outcome.error?.code == UNSUPPORTED_PROTOCOL_VERSION && era == null
                    if (legacyServer || unsupported) {
                        initializeLegacy(seen = null)
                        continue
                    }
                    // A legacy session the server forgot: start a new one, once.
                    if (current is Era.Legacy && current.sessionId != null && outcome.status == 404 && !reinitialized) {
                        reinitialized = true
                        initializeLegacy(seen = current)
                        continue
                    }
                    throw outcome.error?.let { McpException(it.message, it.code, it.data) }
                        ?: McpException("MCP $method failed: HTTP ${outcome.status} ${outcome.body.take(200)}")
                }
            }
        }
    }

    /** Start a legacy session unless another request already replaced [seen]. */
    private suspend fun initializeLegacy(seen: Era?) = eraLock.withLock {
        if (era is Era.Legacy && era != seen) return@withLock
        val params = buildJsonObject {
            put("protocolVersion", LEGACY_VERSIONS.first())
            putJsonObject("capabilities") {}
            putJsonObject("clientInfo") {
                put("name", clientInfo.name)
                put("version", clientInfo.version)
            }
        }
        val pending = Era.Legacy(LEGACY_VERSIONS.first(), null)
        val outcome = send(pending, "initialize", params, null, emptyMap(), null)
        val result = when (outcome) {
            is Outcome.Result -> outcome.result
            is Outcome.Unauthorized -> {
                val challenge = parseChallenge(outcome.wwwAuthenticate)
                throw McpAuthRequiredException(url, challenge["resource_metadata"], challenge["scope"])
            }
            is Outcome.Failure -> throw outcome.error?.let { McpException("initialize: ${it.message}", it.code, it.data) }
                ?: McpException("$url is not an MCP server (HTTP ${outcome.status})")
        }
        val version = result.str("protocolVersion") ?: LEGACY_VERSIONS.first()
        if (version !in LEGACY_VERSIONS) throw McpException("Unsupported MCP protocol version $version (the HTTP+SSE transport of 2024-11-05 is not supported)")
        result.obj("serverInfo")?.let { serverInfo = McpImplementation(it.str("name").orEmpty(), it.str("version").orEmpty(), it.str("title")) }
        instructions = result.str("instructions")
        val legacy = Era.Legacy(version, outcome.sessionId)
        era = legacy
        notifyLegacy(legacy, "notifications/initialized")
    }

    private suspend fun notifyLegacy(legacy: Era.Legacy, method: String, params: JsonObject? = null) {
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", method)
            params?.let { put("params", it) }
        }
        runCatching {
            withContext(Dispatchers.IO) {
                http.newCall(baseRequest().legacyHeaders(legacy).post(body.toString().toRequestBody(JSON)).build()).execute().close()
            }
        }
    }

    private sealed interface Outcome {
        data class Result(val result: JsonObject, val sessionId: String?) : Outcome
        data class Unauthorized(val wwwAuthenticate: String?) : Outcome
        data class Failure(val status: Int, val error: RpcError?, val body: String) : Outcome
    }

    private data class RpcError(val code: Int, val message: String, val data: JsonElement?)

    private sealed interface Frame {
        data class Head(val status: Int, val contentType: String, val sessionId: String?, val wwwAuthenticate: String?) : Frame
        data class Body(val text: String) : Frame
        data class Event(val data: String) : Frame
        data object End : Frame
    }

    private suspend fun send(
        era: Era,
        method: String,
        params: JsonObject,
        name: String?,
        extraHeaders: Map<String, String>,
        onProgress: (suspend (McpProgress) -> Unit)?,
    ): Outcome {
        val id = ids.incrementAndGet()
        val progressToken = if (onProgress != null) "p$id" else null
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", withMeta(params, era, progressToken))
        }
        val builder = baseRequest().post(body.toString().toRequestBody(JSON))
        when (era) {
            Era.Modern -> {
                builder.header("MCP-Protocol-Version", MODERN_VERSION).header("Mcp-Method", method)
                name?.let { builder.header("Mcp-Name", encodeHeaderValue(JsonPrimitive(it))) }
                extraHeaders.forEach { (k, v) -> builder.header(k, v) }
            }
            is Era.Legacy -> builder.legacyHeaders(era)
        }
        auth?.token()?.let { builder.header("Authorization", "Bearer $it") }

        var head: Frame.Head? = null
        var outcome: Outcome? = null
        exchange(builder.build()).first { frame ->
            when (frame) {
                is Frame.Head -> {
                    head = frame
                    if (frame.status == 401) outcome = Outcome.Unauthorized(frame.wwwAuthenticate)
                }
                is Frame.Body -> outcome = if (head!!.status in 200..299) parseResponse(frame.text, id, head!!) else failure(head!!.status, frame.text)
                is Frame.Event -> {
                    val message = runCatching { Json.parseToJsonElement(frame.data).jsonObject }.getOrNull()
                    if (message != null) outcome = handleStreamed(message, id, head!!, era, progressToken, onProgress)
                }
                Frame.End -> if (outcome == null) outcome = if (head!!.status in 200..299) {
                    Outcome.Failure(head!!.status, null, "the server closed the stream without a response")
                } else Outcome.Failure(head!!.status, null, "")
            }
            outcome != null
        }
        return outcome!!
    }

    private suspend fun handleStreamed(
        message: JsonObject,
        id: Long,
        head: Frame.Head,
        era: Era,
        progressToken: String?,
        onProgress: (suspend (McpProgress) -> Unit)?,
    ): Outcome? {
        val method = message.str("method")
        return when {
            method == "notifications/progress" -> {
                val p = message.obj("params")
                if (p != null && p["progressToken"]?.primitiveOrNull()?.contentOrNull == progressToken) {
                    onProgress?.invoke(McpProgress(
                        p["progress"]?.primitiveOrNull()?.doubleOrNull ?: 0.0,
                        p["total"]?.primitiveOrNull()?.doubleOrNull,
                        p.str("message"),
                    ))
                }
                null
            }
            // Legacy servers may send requests on the stream; decline them (we declared no capabilities).
            method != null && message["id"] != null && era is Era.Legacy -> {
                replyLegacy(era, message["id"]!!, method)
                null
            }
            method != null -> null
            message["id"]?.primitiveOrNull()?.longOrNull == id -> responseOutcome(message, head)
            else -> null
        }
    }

    private suspend fun replyLegacy(era: Era.Legacy, requestId: JsonElement, method: String) {
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", requestId)
            if (method == "ping") putJsonObject("result") {} else putJsonObject("error") {
                put("code", -32601)
                put("message", "Method not supported by this client: $method")
            }
        }
        runCatching {
            withContext(Dispatchers.IO) {
                http.newCall(baseRequest().legacyHeaders(era).post(body.toString().toRequestBody(JSON)).build()).execute().close()
            }
        }
    }

    private fun parseResponse(text: String, id: Long, head: Frame.Head): Outcome {
        val message = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return Outcome.Failure(head.status, null, text)
        if (message["id"]?.primitiveOrNull()?.longOrNull != id) return Outcome.Failure(head.status, null, "response id mismatch")
        return responseOutcome(message, head)
    }

    private fun responseOutcome(message: JsonObject, head: Frame.Head): Outcome {
        message.obj("error")?.let { return Outcome.Failure(head.status, it.toRpcError(), message.toString()) }
        return Outcome.Result(message.obj("result") ?: JsonObject(emptyMap()), head.sessionId)
    }

    private fun failure(status: Int, text: String): Outcome {
        val error = runCatching { Json.parseToJsonElement(text).jsonObject.obj("error")?.toRpcError() }.getOrNull()
        return Outcome.Failure(status, error, text)
    }

    private fun JsonObject.toRpcError() = RpcError(
        this["code"]?.primitiveOrNull()?.intOrNull ?: 0,
        str("message") ?: "MCP error",
        this["data"],
    )

    private fun withMeta(params: JsonObject, era: Era, progressToken: String?): JsonObject {
        val meta = buildJsonObject {
            params.obj("_meta")?.forEach { (k, v) -> put(k, v) }
            progressToken?.let { put("progressToken", it) }
            if (era == Era.Modern) {
                put("io.modelcontextprotocol/protocolVersion", MODERN_VERSION)
                putJsonObject("io.modelcontextprotocol/clientInfo") {
                    put("name", clientInfo.name)
                    put("version", clientInfo.version)
                }
                putJsonObject("io.modelcontextprotocol/clientCapabilities") {}
            }
        }
        if (meta.isEmpty()) return params
        return JsonObject(params + ("_meta" to meta))
    }

    private suspend fun baseRequest(): Request.Builder = Request.Builder()
        .url(url)
        .header("Accept", "application/json, text/event-stream")
        .apply { headers.forEach { (k, v) -> if (v.isNotEmpty()) header(k, v) } }

    private fun Request.Builder.legacyHeaders(legacy: Era.Legacy): Request.Builder = apply {
        header("MCP-Protocol-Version", legacy.version)
        legacy.sessionId?.let { header("Mcp-Session-Id", it) }
    }

    /** POST and stream the reply as frames; cancelling the collector cancels the call. */
    private fun exchange(request: Request): Flow<Frame> = flow {
        val call = http.newCall(request)
        val cancel = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                val type = response.header("Content-Type").orEmpty().lowercase()
                emit(Frame.Head(response.code, type, response.header("Mcp-Session-Id"), response.header("WWW-Authenticate")))
                val body = response.body ?: run { emit(Frame.End); return@use }
                if (type.startsWith("text/event-stream")) {
                    val source = body.source()
                    val data = StringBuilder()
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        when {
                            line.isEmpty() -> if (data.isNotEmpty()) { emit(Frame.Event(data.toString())); data.clear() }
                            line.startsWith("data:") -> {
                                if (data.isNotEmpty()) data.append('\n')
                                data.append(line.substring(5).removePrefix(" "))
                            }
                        }
                    }
                    if (data.isNotEmpty()) emit(Frame.Event(data.toString()))
                    emit(Frame.End)
                } else {
                    val text = body.string()
                    if (text.isBlank()) emit(Frame.End) else emit(Frame.Body(text))
                }
            }
        } catch (e: IOException) {
            if (call.isCanceled()) throw CancellationException("MCP request cancelled")
            throw McpException("Cannot reach MCP server ${request.url.host}: ${e.message}", cause = e)
        } finally {
            cancel?.dispose()
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        /** The stateless revision this client speaks first. */
        const val MODERN_VERSION = "2026-07-28"

        /** Session-based revisions it falls back to, newest first. */
        val LEGACY_VERSIONS = listOf("2025-11-25", "2025-06-18", "2025-03-26")

        private const val UNSUPPORTED_PROTOCOL_VERSION = -32022
        private const val HEADER_MISMATCH = -32020

        /** JSON-RPC errors only a modern server returns: they mean "modern, but fix the request", not "fall back". */
        private val MODERN_ERROR_CODES = setOf(UNSUPPORTED_PROTOCOL_VERSION, HEADER_MISMATCH, -32021, -32601)

        private const val MAX_PAGES = 50
        private val JSON = "application/json".toMediaType()
        private val Base64Std = Base64.Default

        /** RFC 9110 visible ASCII plus space/tab, without leading/trailing whitespace and not the sentinel itself. */
        internal fun encodeHeaderValue(value: JsonPrimitive): String {
            val text = when {
                value.isString -> value.content
                value.booleanOrNull != null -> value.booleanOrNull.toString()
                else -> value.content
            }
            val plain = text.isNotEmpty() && text == text.trim() && text.all { it == '\t' || it.code in 0x20..0x7E } &&
                !(text.startsWith("=?base64?") && text.endsWith("?="))
            return if (plain) text else "=?base64?" + Base64Std.encode(text.toByteArray(Charsets.UTF_8)) + "?="
        }

        private val TOKEN = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")

        /**
         * The `x-mcp-header` annotations of a tool's input schema as
         * (property path → header name), or null when an annotation is invalid
         * (the tool must then be ignored). Only chains of `properties` are
         * searched, which is exactly where annotations are allowed.
         */
        internal fun mcpHeaderParams(schema: JsonObject): Map<List<String>, String>? {
            val out = linkedMapOf<List<String>, String>()
            fun walk(node: JsonObject, path: List<String>): Boolean {
                val header = node["x-mcp-header"]
                if (header != null) {
                    val name = (header as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return false
                    val type = node.str("type")
                    if (path.isEmpty() || !TOKEN.matches(name) || type !in setOf("string", "integer", "boolean")) return false
                    if (out.values.any { it.equals(name, ignoreCase = true) }) return false
                    out[path] = name
                }
                val properties = node.obj("properties") ?: return true
                return properties.all { (key, child) -> (child as? JsonObject)?.let { walk(it, path + key) } ?: true }
            }
            return if (walk(schema, emptyList())) out else null
        }

        private fun valueAt(arguments: JsonObject, path: List<String>): JsonPrimitive? {
            var node: JsonElement = arguments
            path.forEach { key -> node = (node as? JsonObject)?.get(key) ?: return null }
            return (node as? JsonPrimitive)?.takeIf { it !is JsonNull }
        }

        /** Parameters of a `WWW-Authenticate: Bearer k="v", …` challenge. */
        internal fun parseChallenge(header: String?): Map<String, String> {
            if (header == null) return emptyMap()
            return Regex("([A-Za-z_]+)=\"([^\"]*)\"|([A-Za-z_]+)=([^,\\s]+)").findAll(header).associate { m ->
                if (m.groupValues[1].isNotEmpty()) m.groupValues[1] to m.groupValues[2] else m.groupValues[3] to m.groupValues[4]
            }
        }
    }
}
