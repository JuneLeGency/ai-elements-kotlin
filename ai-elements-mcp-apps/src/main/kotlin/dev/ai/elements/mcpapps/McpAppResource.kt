package dev.ai.elements.mcpapps

import dev.ai.elements.core.mcp.McpApps
import dev.ai.elements.core.mcp.McpClient
import dev.ai.elements.core.mcp.McpContent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.putJsonArray
import kotlin.io.encoding.Base64

/** A view that cannot be shown: not a `ui://` resource, not `text/html;profile=mcp-app`, or empty. */
class McpAppException(message: String) : Exception(message)

/**
 * The origins a view may reach (MCP Apps `McpUiResourceCsp`). Empty lists are the secure default:
 * no connections, no external resources, no nested frames, same-origin base URIs.
 */
data class McpAppCsp(
    val connectDomains: List<String> = emptyList(),
    val resourceDomains: List<String> = emptyList(),
    val frameDomains: List<String> = emptyList(),
    val baseUriDomains: List<String> = emptyList(),
) {
    /**
     * The `Content-Security-Policy` for the view (§"Content Security Policy Enforcement"). Only
     * well-formed origins are kept, so a server cannot inject directives or keywords.
     */
    fun header(): String {
        val resources = resourceDomains.origins()
        return listOf(
            "default-src 'none'",
            "script-src 'self' 'unsafe-inline'$resources",
            "style-src 'self' 'unsafe-inline'$resources",
            "connect-src 'self'${connectDomains.origins()}",
            "img-src 'self' data:$resources",
            "font-src 'self'$resources",
            "media-src 'self' data:$resources",
            "frame-src ${frameDomains.origins().trim().ifEmpty { "'none'" }}",
            "object-src 'none'",
            "base-uri ${baseUriDomains.origins().trim().ifEmpty { "'self'" }}",
        ).joinToString("; ")
    }

    /** As MCP `McpUiResourceCsp` JSON (the host's `hostCapabilities.sandbox.csp`). */
    fun toJson(): JsonObject = buildJsonObject {
        mapOf("connectDomains" to connectDomains, "resourceDomains" to resourceDomains, "frameDomains" to frameDomains, "baseUriDomains" to baseUriDomains)
            .forEach { (key, domains) -> domains.filter(::isOrigin).takeIf { it.isNotEmpty() }?.let { valid -> putJsonArray(key) { valid.forEach { add(JsonPrimitive(it)) } } } }
    }

    private fun List<String>.origins(): String = filter(::isOrigin).joinToString("") { " $it" }

    companion object {
        // scheme://host[:port], with an optional leading `*.` wildcard label; nothing else (no paths, quotes or `;`).
        private val ORIGIN = Regex("""^(https?|wss?)://(\*\.)?[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*(:\d{1,5})?$""")

        fun isOrigin(value: String): Boolean = ORIGIN.matches(value)

        internal fun from(json: JsonObject?): McpAppCsp {
            fun list(key: String) = (json?.get(key) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
            return McpAppCsp(list("connectDomains"), list("resourceDomains"), list("frameDomains"), list("baseUriDomains"))
        }
    }
}

/**
 * An MCP App view read from its server (`resources/read` of a `ui://` URI, §"UI Resource Format").
 *
 * @property permissions the browser features the view asks for (`camera`, `microphone`,
 *   `geolocation`, `clipboardWrite`); hosts may grant them, this one grants none.
 * @property prefersBorder whether the view wants the host's border and background (null: host decides).
 * @property domain a dedicated origin the view asked for (host-specific; not used here).
 */
data class McpAppResource(
    val uri: String,
    val html: String,
    val csp: McpAppCsp = McpAppCsp(),
    val permissions: Set<String> = emptySet(),
    val prefersBorder: Boolean? = null,
    val domain: String? = null,
) {
    companion object {
        /** The view from `resources/read` [contents], validated as the spec requires. */
        fun from(uri: String, contents: List<McpContent.Resource>): McpAppResource {
            if (!uri.startsWith("ui://")) throw McpAppException("$uri is not a ui:// resource")
            val content = contents.firstOrNull { it.uri == uri } ?: contents.firstOrNull()
                ?: throw McpAppException("$uri has no content")
            val mime = content.mimeType?.replace(" ", "")
            if (mime != McpApps.MIME_TYPE) throw McpAppException("$uri is ${content.mimeType}, not ${McpApps.MIME_TYPE}")
            val html = content.text ?: content.blob?.let { runCatching { Base64.decode(it).decodeToString() }.getOrNull() }
            if (html.isNullOrBlank()) throw McpAppException("$uri is empty")
            val ui = content.meta?.get("ui") as? JsonObject
            return McpAppResource(
                uri = uri,
                html = html,
                csp = McpAppCsp.from(ui?.get("csp") as? JsonObject),
                permissions = (ui?.get("permissions") as? JsonObject)?.keys.orEmpty(),
                prefersBorder = (ui?.get("prefersBorder") as? JsonPrimitive)?.booleanOrNull,
                domain = (ui?.get("domain") as? JsonPrimitive)?.contentOrNull,
            )
        }
    }
}

/** Read and validate the MCP App view at [uri]. */
suspend fun McpClient.readMcpApp(uri: String): McpAppResource = McpAppResource.from(uri, readResource(uri))

internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
