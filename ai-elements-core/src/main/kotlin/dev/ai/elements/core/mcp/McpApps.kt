package dev.ai.elements.core.mcp

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The [MCP Apps](https://github.com/modelcontextprotocol/ext-apps) extension (SEP-1865, stable
 * revision 2026-01-26): tools whose results an interactive HTML view (`ui://` resource) renders.
 * The host side — sandbox, the `ui/…` message bridge — is `ai-elements-mcp-apps`; this is what the MCP client
 * needs: the extension id, the negotiation and the view's media type.
 */
object McpApps {
    /** The extension identifier. */
    const val EXTENSION = "io.modelcontextprotocol/ui"

    /** The media type of an HTML view. */
    const val MIME_TYPE = "text/html;profile=mcp-app"

    /** The MCP Apps protocol revision the host speaks in `ui/initialize`. */
    const val PROTOCOL_VERSION = "2026-01-26"

    /** Client capabilities that advertise MCP Apps (§"Client (Host) Capabilities"); pass to [McpClient]. */
    val CLIENT_CAPABILITIES: JsonObject = buildJsonObject {
        putJsonObject("extensions") {
            putJsonObject(EXTENSION) { putJsonArray("mimeTypes") { add(MIME_TYPE) } }
        }
    }
}
