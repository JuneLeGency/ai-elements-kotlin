package dev.ai.elements.mcpapps

import dev.ai.elements.core.mcp.McpApps
import dev.ai.elements.core.mcp.McpClient
import dev.ai.elements.core.mcp.McpException
import dev.ai.elements.core.mcp.McpImplementation
import dev.ai.elements.core.mcp.McpTool
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * What the host does for a view. Every method has a safe default (decline, ignore), so a host
 * implements only what it offers.
 */
interface McpAppHost {
    /** `ui/open-link` (http or https only; already checked): open [url] for the user. */
    suspend fun openLink(url: String): Boolean = false

    /** `ui/message`: add a user message with [text] (the text blocks of [content]) to the chat. */
    suspend fun message(text: String, content: JsonArray): Boolean = false

    /** `ui/update-model-context`: what the model should know in later turns (replaces the previous one). */
    suspend fun updateModelContext(content: JsonArray?, structuredContent: JsonElement?) {}

    /** `ui/request-display-mode`: switch to [mode] if possible; returns the resulting mode. */
    suspend fun requestDisplayMode(mode: String, current: String): String = current

    /** `ui/notifications/size-changed`, in CSS pixels. */
    fun sizeChanged(width: Double?, height: Double?) {}

    /** `notifications/message` from the view. */
    fun log(level: String, data: JsonElement?) {}

    /** `ui/notifications/request-teardown`: the view asks to be closed. */
    fun requestTeardown() {}

    /**
     * Whether the view may run [tool] now. The default follows the library's rule — tools that
     * change data ask the user — so read-only tools pass and others are declined unless the host
     * asks the user.
     */
    suspend fun approveToolCall(tool: McpTool, arguments: JsonObject): Boolean = tool.annotations?.readOnlyHint == true

    /** Every request and notification the view sends, for an audit trail (§"Auditable Communication"). */
    fun audit(method: String, params: JsonElement?) {}
}

/**
 * The host side of one MCP App view (MCP Apps 2026-01-26, §"Communication Protocol"): it answers
 * the view's JSON-RPC over [send] / [receive] as an MCP server that proxies [client] — the
 * connection of the server that owns the view — and drives the lifecycle (`ui/initialize`,
 * tool input and result, host context, teardown).
 *
 * The view is untrusted: it only reaches tools of its own server whose `visibility` includes
 * `app` (§"Visibility"), through [McpAppHost.approveToolCall]; links must be http(s); nothing is
 * sent to it before `ui/notifications/initialized`. Call [receive] and the host methods from one
 * coroutine context (e.g. the main thread).
 *
 * @param tool the MCP `Tool` JSON of the call that shows the view (`hostContext.toolInfo`).
 * @param send delivers one JSON-RPC message to the view.
 */
class McpAppBridge(
    private val client: McpClient,
    private val resource: McpAppResource,
    private val toolCallId: String,
    private val tool: JsonObject,
    private val host: McpAppHost,
    hostContext: JsonObject = JsonObject(emptyMap()),
    private val hostInfo: McpImplementation = McpImplementation("ai-elements-kotlin", "0.3.0"),
    private val send: suspend (JsonObject) -> Unit,
) {
    private var context: JsonObject = withToolInfo(hostContext)
    private var initialized = false
    private val pending = mutableListOf<JsonObject>()
    private var inputSent = false
    private var resultSent = false
    private var nextId = 0L
    private val waiting = mutableMapOf<Long, CompletableDeferred<JsonObject>>()
    private var appTools: List<McpTool>? = null

    /** The view's `appCapabilities` from `ui/initialize`, once it connected. */
    var appCapabilities: JsonObject? = null
        private set

    /** The view's `appInfo` from `ui/initialize`. */
    var appInfo: JsonObject? = null
        private set

    /** The current display mode (`hostContext.displayMode`). */
    val displayMode: String get() = context.string("displayMode") ?: "inline"

    /** Handle one message from the view (malformed messages are dropped). */
    suspend fun receive(text: String) {
        val message = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return
        receive(message)
    }

    suspend fun receive(message: JsonObject) {
        if (message.string("jsonrpc") != "2.0") return
        val method = message.string("method")
        val id = message["id"]
        when {
            method != null && id != null && id !is JsonNull -> {
                host.audit(method, message["params"])
                send(respond(id, method, message["params"] as? JsonObject ?: JsonObject(emptyMap())))
            }
            method != null -> {
                host.audit(method, message["params"])
                notification(method, message["params"] as? JsonObject ?: JsonObject(emptyMap()))
            }
            // A response to one of our requests (`ui/resource-teardown`).
            id is JsonPrimitive -> id.contentOrNull?.toLongOrNull()?.let { waiting.remove(it)?.complete(message) }
        }
    }

    /** The tool's complete arguments (`ui/notifications/tool-input`, sent once). */
    suspend fun toolInput(arguments: JsonObject) {
        if (inputSent) return
        inputSent = true
        notify("ui/notifications/tool-input", buildJsonObject { put("arguments", arguments) })
    }

    /** The tool's `CallToolResult` (`ui/notifications/tool-result`, sent once, after the input). */
    suspend fun toolResult(result: JsonObject) {
        if (resultSent || !inputSent) return
        resultSent = true
        notify("ui/notifications/tool-result", result)
    }

    /** The tool call was cancelled (`ui/notifications/tool-cancelled`). */
    suspend fun toolCancelled(reason: String) {
        if (resultSent) return
        resultSent = true
        notify("ui/notifications/tool-cancelled", buildJsonObject { put("reason", reason) })
    }

    /** Update the host context; the view gets the changed fields (`ui/notifications/host-context-changed`). */
    suspend fun setHostContext(hostContext: JsonObject) {
        val next = withToolInfo(hostContext)
        val changed = next.filter { (key, value) -> context[key] != value }
        context = next
        if (changed.isNotEmpty()) notify("ui/notifications/host-context-changed", JsonObject(changed))
    }

    /**
     * Ask the view to wind down before it is removed (`ui/resource-teardown`) and wait up to
     * [timeoutMs] for its answer. Returns whether it answered.
     */
    suspend fun teardown(reason: String? = null, timeoutMs: Long = 1_000): Boolean {
        if (!initialized) return false
        val id = nextId++
        val answer = CompletableDeferred<JsonObject>()
        waiting[id] = answer
        send(buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", "ui/resource-teardown")
            putJsonObject("params") { reason?.let { put("reason", it) } }
        })
        val answered = withTimeoutOrNull(timeoutMs) { answer.await() } != null
        waiting.remove(id)
        return answered
    }

    private suspend fun respond(id: JsonElement, method: String, params: JsonObject): JsonObject = try {
        success(id, handle(method, params))
    } catch (e: RpcError) {
        failure(id, e.code, e.message.orEmpty())
    } catch (e: McpException) {
        failure(id, e.code ?: INTERNAL_ERROR, e.message.orEmpty())
    } catch (e: McpAppException) {
        failure(id, INVALID_PARAMS, e.message.orEmpty())
    }

    private suspend fun handle(method: String, params: JsonObject): JsonElement = when (method) {
        "ui/initialize" -> initialize(params)
        "ping" -> EMPTY
        "tools/call" -> callTool(params)
        "resources/read" -> readResource(params)
        "ui/open-link" -> {
            val url = params.string("url") ?: throw RpcError(INVALID_PARAMS, "Missing url")
            val scheme = url.substringBefore(':', "").lowercase()
            if (scheme != "https" && scheme != "http") throw RpcError(DENIED, "Invalid URL")
            if (!host.openLink(url)) throw RpcError(DENIED, "Link opening denied")
            EMPTY
        }
        "ui/message" -> {
            if (params.string("role") != "user") throw RpcError(INVALID_PARAMS, "Only user messages are supported")
            // The SDK sends a list of content blocks; the spec's example shows one block.
            val content = when (val c = params["content"]) {
                is JsonArray -> c
                is JsonObject -> JsonArray(listOf(c))
                else -> throw RpcError(INVALID_PARAMS, "Invalid message format")
            }
            val text = content.mapNotNull { (it as? JsonObject)?.takeIf { b -> b.string("type") == "text" }?.string("text") }.joinToString("\n")
            if (text.isBlank() || !host.message(text, content)) throw RpcError(DENIED, "Message sending denied")
            EMPTY
        }
        "ui/update-model-context" -> {
            host.updateModelContext(params["content"] as? JsonArray, params["structuredContent"])
            EMPTY
        }
        "ui/request-display-mode" -> {
            val mode = params.string("mode") ?: throw RpcError(INVALID_PARAMS, "Missing mode")
            val offered = context["availableDisplayModes"].strings()
            val declared = appCapabilities?.get("availableDisplayModes").strings()
            // Only modes both sides support (§"Display Modes" requirements); otherwise the current one.
            val result = if (mode == displayMode || mode !in offered || (declared.isNotEmpty() && mode !in declared)) displayMode
            else host.requestDisplayMode(mode, displayMode)
            if (result != displayMode) setHostContext(JsonObject(context + ("displayMode" to JsonPrimitive(result))))
            buildJsonObject { put("mode", result) }
        }
        else -> throw RpcError(METHOD_NOT_FOUND, "Method not found: $method")
    }

    private fun initialize(params: JsonObject): JsonObject {
        appCapabilities = params["appCapabilities"] as? JsonObject ?: JsonObject(emptyMap())
        appInfo = params["appInfo"] as? JsonObject
        val requested = params.string("protocolVersion")
        return buildJsonObject {
            put("protocolVersion", if (requested in SUPPORTED_VERSIONS) requested else McpApps.PROTOCOL_VERSION)
            putJsonObject("hostCapabilities") {
                putJsonObject("openLinks") {}
                putJsonObject("serverTools") {}
                putJsonObject("serverResources") {}
                putJsonObject("logging") {}
                putJsonObject("updateModelContext") { putJsonObject("text") {} }
                putJsonObject("message") { putJsonObject("text") {} }
                putJsonObject("sandbox") {
                    putJsonObject("permissions") {} // none granted
                    put("csp", resource.csp.toJson())
                }
            }
            putJsonObject("hostInfo") {
                put("name", hostInfo.name)
                put("version", hostInfo.version)
            }
            put("hostContext", context)
        }
    }

    private suspend fun callTool(params: JsonObject): JsonElement {
        val name = params.string("name") ?: throw RpcError(INVALID_PARAMS, "Missing tool name")
        val arguments = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
        val tools = appTools ?: client.listTools().also { appTools = it }
        val target = tools.firstOrNull { it.name == name && McpTool.VISIBLE_TO_APP in it.visibility }
            ?: throw RpcError(INVALID_PARAMS, "Tool $name is not available to this app")
        if (!host.approveToolCall(target, arguments)) throw RpcError(DENIED, "The user declined $name")
        return client.callTool(target, arguments).raw ?: buildJsonObject { putJsonArray("content") {} }
    }

    private suspend fun readResource(params: JsonObject): JsonElement {
        val uri = params.string("uri") ?: throw RpcError(INVALID_PARAMS, "Missing uri")
        val contents = client.readResource(uri)
        return buildJsonObject {
            putJsonArray("contents") {
                contents.forEach { c ->
                    addJsonObject {
                        put("uri", c.uri)
                        c.mimeType?.let { put("mimeType", it) }
                        c.text?.let { put("text", it) }
                        c.blob?.let { put("blob", it) }
                        c.meta?.let { put("_meta", it) }
                    }
                }
            }
        }
    }

    private suspend fun notification(method: String, params: JsonObject) {
        when (method) {
            "ui/notifications/initialized" -> if (!initialized) {
                initialized = true
                pending.toList().also { pending.clear() }.forEach { send(it) }
            }
            "ui/notifications/size-changed" -> host.sizeChanged(params.number("width"), params.number("height"))
            "notifications/message" -> host.log(params.string("level") ?: "info", params["data"])
            "ui/notifications/request-teardown" -> host.requestTeardown()
            // Anything else (including the proxy's `ui/notifications/sandbox-*`) is ignored.
        }
    }

    /** Host-to-view notifications wait for `ui/notifications/initialized` (§"Sandbox proxy" 6). */
    private suspend fun notify(method: String, params: JsonObject) {
        val message = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", method)
            put("params", params)
        }
        if (initialized) send(message) else pending += message
    }

    private fun withToolInfo(hostContext: JsonObject): JsonObject = JsonObject(
        hostContext + ("toolInfo" to buildJsonObject {
            put("id", toolCallId)
            put("tool", tool)
        }),
    )

    private class RpcError(val code: Int, message: String) : Exception(message)

    private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

    private fun JsonElement?.strings(): List<String> = (this as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()

    private fun success(id: JsonElement, result: JsonElement) = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("result", result)
    }

    private fun failure(id: JsonElement, code: Int, message: String) = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        putJsonObject("error") {
            put("code", code)
            put("message", message)
        }
    }

    companion object {
        /** MCP Apps revisions this host speaks. */
        val SUPPORTED_VERSIONS = setOf(McpApps.PROTOCOL_VERSION)

        private val EMPTY = JsonObject(emptyMap())
        private const val METHOD_NOT_FOUND = -32601
        private const val INVALID_PARAMS = -32602
        private const val INTERNAL_ERROR = -32603

        /** Implementation-defined denial (the spec's examples use -32000). */
        private const val DENIED = -32000
    }
}
