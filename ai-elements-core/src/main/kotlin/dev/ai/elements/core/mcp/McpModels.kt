package dev.ai.elements.core.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Name and version of an MCP client or server (`Implementation`). */
data class McpImplementation(val name: String, val version: String, val title: String? = null)

/**
 * Hints a server gives about a tool (`ToolAnnotations`). They are *untrusted*
 * unless the server is: use them for defaults (e.g. skip the confirmation for
 * read-only tools), never as a security boundary.
 */
data class McpToolAnnotations(
    val title: String? = null,
    val readOnlyHint: Boolean? = null,
    val destructiveHint: Boolean? = null,
    val idempotentHint: Boolean? = null,
    val openWorldHint: Boolean? = null,
)

/** A tool offered by an MCP server (`tools/list`). */
data class McpTool(
    val name: String,
    val title: String? = null,
    val description: String? = null,
    val inputSchema: JsonObject,
    val outputSchema: JsonObject? = null,
    val annotations: McpToolAnnotations? = null,
    /** The tool's `_meta`, as the server sent it. */
    val meta: JsonObject? = null,
) {
    /** `title`, then `annotations.title`, then `name` (the spec's display-name precedence). */
    val displayName: String get() = title ?: annotations?.title ?: name

    /**
     * The MCP App that renders this tool's results (MCP Apps `_meta.ui.resourceUri`, or the
     * deprecated flat `_meta["ui/resourceUri"]`); only `ui://` URIs count.
     */
    val uiResourceUri: String?
        get() = (meta?.obj("ui")?.str("resourceUri") ?: meta?.str("ui/resourceUri"))?.takeIf { it.startsWith("ui://") }

    /** Who may call the tool (MCP Apps `_meta.ui.visibility`): `model` and / or `app`; both by default. */
    val visibility: Set<String>
        get() = meta?.obj("ui")?.arr("visibility")?.mapNotNull { it.string }?.toSet() ?: setOf(VISIBLE_TO_MODEL, VISIBLE_TO_APP)

    companion object {
        const val VISIBLE_TO_MODEL = "model"
        const val VISIBLE_TO_APP = "app"
    }
}

/** One item of a tool result or prompt message. */
sealed interface McpContent {
    data class Text(val text: String) : McpContent
    data class Image(val data: String, val mimeType: String) : McpContent
    data class Audio(val data: String, val mimeType: String) : McpContent

    /** A link to a resource the client may read with `resources/read`. */
    data class ResourceLink(val uri: String, val name: String?, val mimeType: String?) : McpContent

    /** A resource embedded in the result: [text] or base64 [blob], with its `_meta`. */
    data class Resource(val uri: String, val mimeType: String?, val text: String?, val blob: String?, val meta: JsonObject? = null) : McpContent
}

/** The result of `tools/call`. [isError] results are tool failures the model should see. */
data class McpToolResult(
    val content: List<McpContent>,
    val structuredContent: JsonElement? = null,
    val isError: Boolean = false,
    /** The `CallToolResult` as the server sent it (e.g. for an MCP App's `ui/notifications/tool-result`). */
    val raw: JsonObject? = null,
) {
    /**
     * The result as text for a model: text items verbatim, embedded text
     * resources inline, other media described. Falls back to the structured
     * content's JSON.
     */
    fun toText(): String {
        val text = content.joinToString("\n") {
            when (it) {
                is McpContent.Text -> it.text
                is McpContent.Image -> "[image ${it.mimeType}, ${it.data.length * 3 / 4} bytes]"
                is McpContent.Audio -> "[audio ${it.mimeType}, ${it.data.length * 3 / 4} bytes]"
                is McpContent.ResourceLink -> "[resource ${it.name ?: it.uri}](${it.uri})"
                is McpContent.Resource -> it.text ?: "[resource ${it.uri} ${it.mimeType.orEmpty()}]"
            }
        }
        return text.ifBlank { structuredContent?.toString().orEmpty() }
    }
}

/** A resource listed by `resources/list`. */
data class McpResource(val uri: String, val name: String, val title: String? = null, val description: String? = null, val mimeType: String? = null)

/** A prompt template listed by `prompts/list`. */
data class McpPrompt(val name: String, val title: String? = null, val description: String? = null, val arguments: List<McpPromptArgument> = emptyList())

data class McpPromptArgument(val name: String, val description: String? = null, val required: Boolean = false)

/** A progress notification for a running request (`notifications/progress`). */
data class McpProgress(val progress: Double, val total: Double? = null, val message: String? = null) {
    /** 0..1 when the server reported a total. */
    val fraction: Double? get() = total?.takeIf { it > 0 }?.let { (progress / it).coerceIn(0.0, 1.0) }
}

/** A JSON-RPC error from the server, or a transport failure ([code] null). */
open class McpException(message: String, val code: Int? = null, val data: JsonElement? = null, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * The server requires authorization (HTTP 401) and no usable credentials are
 * configured. [resourceMetadataUrl] (RFC 9728) and [scope] come from the
 * `WWW-Authenticate` challenge and feed [McpOAuth.discover].
 */
class McpAuthRequiredException(
    val serverUrl: String,
    val resourceMetadataUrl: String?,
    val scope: String?,
) : McpException("$serverUrl requires sign-in")

internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
internal fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

internal fun JsonObject.toTool(): McpTool? {
    val name = str("name") ?: return null
    return McpTool(
        name = name,
        title = str("title"),
        description = str("description"),
        inputSchema = obj("inputSchema") ?: JsonObject(mapOf("type" to JsonPrimitive("object"))),
        outputSchema = obj("outputSchema"),
        annotations = obj("annotations")?.let {
            McpToolAnnotations(it.str("title"), it.bool("readOnlyHint"), it.bool("destructiveHint"), it.bool("idempotentHint"), it.bool("openWorldHint"))
        },
        meta = obj("_meta"),
    )
}

/** The tool as MCP `Tool` JSON (for an MCP App's `hostContext.toolInfo`). */
fun McpTool.toJson(): JsonObject = buildJsonObject {
    put("name", name)
    title?.let { put("title", it) }
    description?.let { put("description", it) }
    put("inputSchema", inputSchema)
    outputSchema?.let { put("outputSchema", it) }
    annotations?.let { a ->
        put("annotations", buildJsonObject {
            a.title?.let { put("title", it) }
            a.readOnlyHint?.let { put("readOnlyHint", it) }
            a.destructiveHint?.let { put("destructiveHint", it) }
            a.idempotentHint?.let { put("idempotentHint", it) }
            a.openWorldHint?.let { put("openWorldHint", it) }
        })
    }
    meta?.let { put("_meta", it) }
}

internal fun JsonObject.toContent(): McpContent? = when (str("type")) {
    "text" -> McpContent.Text(str("text").orEmpty())
    "image" -> McpContent.Image(str("data").orEmpty(), str("mimeType") ?: "image/png")
    "audio" -> McpContent.Audio(str("data").orEmpty(), str("mimeType") ?: "audio/wav")
    "resource_link" -> McpContent.ResourceLink(str("uri").orEmpty(), str("name"), str("mimeType"))
    "resource" -> obj("resource")?.let { McpContent.Resource(it.str("uri").orEmpty(), it.str("mimeType"), it.str("text"), it.str("blob"), it.obj("_meta")) }
    else -> null
}

internal fun JsonObject.toToolResult() = McpToolResult(
    content = arr("content")?.mapNotNull { (it as? JsonObject)?.toContent() }.orEmpty(),
    structuredContent = this["structuredContent"],
    isError = bool("isError") ?: false,
    raw = this,
)

internal val JsonElement.string: String? get() = (this as? JsonPrimitive)?.contentOrNull

internal fun JsonElement.primitiveOrNull(): JsonPrimitive? = runCatching { jsonPrimitive }.getOrNull()
