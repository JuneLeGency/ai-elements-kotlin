package dev.ai.elements.mcpapps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.ai.elements.core.mcp.McpClient
import dev.ai.elements.core.model.DataPart
import dev.ai.elements.ui.chat.DataRenderer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * What a view wants the model to know (`ui/update-model-context`): its latest [content] blocks
 * and [structuredContent]. Attach [toDataPart] to the next user turn; each view's newest replaces
 * its previous one.
 */
data class McpAppModelContext(
    val toolCallId: String,
    val app: String,
    val content: JsonArray?,
    val structuredContent: JsonElement?,
) {
    /** The text for the model: the text blocks, else the structured content as JSON. */
    val text: String
        get() = content.orEmpty().mapNotNull { (it as? JsonObject)?.takeIf { b -> b.string("type") == "text" }?.string("text") }
            .joinToString("\n").ifBlank { structuredContent?.toString().orEmpty() }

    /** As a [DataPart.MODEL_CONTEXT] part for the next user turn. */
    fun toDataPart(): DataPart = DataPart(
        "mcp-app-context-$toolCallId",
        DataPart.MODEL_CONTEXT,
        buildJsonObject {
            put("description", "What the $app app shows the user")
            put("value", text)
        },
    )
}

/**
 * Renders [DataPart.MCP_APP] parts — added by MCP tools that have a view (`_meta.ui.resourceUri`)
 * — as their MCP App ([McpAppView]). [clientFor] returns the connection of the part's server (by
 * the server id the part names; reuse the instances the agent uses); a part whose server is
 * unknown renders nothing. The views live in [sessions]: keep one per chat screen.
 *
 * Prefer [McpAppsHost], which also shows the views' dialogs at the screen's root.
 */
fun mcpAppRenderer(clientFor: (serverId: String) -> McpClient?, actions: McpAppActions, sessions: McpAppSessions): DataRenderer =
    DataRenderer { part -> McpAppPart(part, clientFor, actions, sessions) }

/**
 * MCP Apps for a chat screen: [content] gets the renderer for [DataPart.MCP_APP] (register it in
 * `AiElementsRenderers.data`), and the views' dialogs — tool approvals, full screen — show at this
 * level, whether or not their view is scrolled into sight. Views are torn down with this.
 *
 * ```kotlin
 * McpAppsHost({ id -> clients[id] }, actions) { apps ->
 *     CompositionLocalProvider(LocalAiElementsRenderers provides AiElementsRenderers(data = mapOf(DataPart.MCP_APP to apps))) {
 *         Chat(controller)
 *     }
 * }
 * ```
 */
@Composable
fun McpAppsHost(
    clientFor: (serverId: String) -> McpClient?,
    actions: McpAppActions,
    content: @Composable (renderer: DataRenderer) -> Unit,
) {
    val sessions = rememberMcpAppSessions()
    val renderer = remember(sessions) { mcpAppRenderer(clientFor, actions, sessions) }
    content(renderer)
    sessions.Dialogs()
}

@Composable
private fun McpAppPart(part: DataPart, clientFor: (String) -> McpClient?, actions: McpAppActions, sessions: McpAppSessions) {
    val app = part.data as? JsonObject ?: return
    val client = app.string("server")?.let(clientFor) ?: return
    McpAppView(
        client = client,
        resourceUri = app.string("resourceUri") ?: return,
        toolCallId = app.string("toolCallId") ?: part.id,
        tool = app["tool"] as? JsonObject ?: JsonObject(mapOf("name" to JsonPrimitive(""))),
        input = app["input"] as? JsonObject ?: JsonObject(emptyMap()),
        result = app["result"] as? JsonObject,
        actions = actions,
        sessions = sessions,
    )
}
