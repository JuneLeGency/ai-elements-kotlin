package dev.ai.elements.core.mcp

import dev.ai.elements.core.agent.ToolCallContext
import dev.ai.elements.core.chat.ToolApprover
import dev.ai.elements.core.model.DataPart
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The MCP client against the official Python SDKs: `mcp` 2.x (2026-07-28 stateless, `server/main.py`
 * at /mcp) and `mcp` 1.x (legacy sessions, `server/legacy_mcp_server.py`). Opt-in:
 * `-PliveMcp=http://localhost:8788/mcp -PliveMcpLegacy=http://localhost:8790/mcp`
 */
class LiveMcpTest {
    private val modern: String? = System.getProperty("live.mcp")
    private val legacy: String? = System.getProperty("live.mcpLegacy")

    @Test
    fun modernServer_statelessToolsProgressAndResources() = runBlocking {
        assumeTrue(modern != null)
        val client = McpClient(modern!!)
        val tools = client.listTools()
        assertEquals(McpClient.MODERN_VERSION, client.protocolVersion)
        assertTrue(tools.map { it.name }.containsAll(listOf("convert_units", "save_note", "count_slowly")))
        val convert = tools.single { it.name == "convert_units" }
        assertEquals(true, convert.annotations?.readOnlyHint)
        assertEquals("Convert units", convert.displayName)

        val result = client.callTool(convert, buildJsonObject { put("value", 5); put("from_unit", "km"); put("to_unit", "mi") })
        assertTrue(result.toText(), result.toText().contains("3.10686 mi"))

        val progress = mutableListOf<McpProgress>()
        val counted = client.callTool(tools.single { it.name == "count_slowly" }, buildJsonObject { put("to", 3) }) { progress += it }
        assertEquals("Counted to 3.", counted.toText())
        assertEquals(listOf(1.0, 2.0, 3.0), progress.map { it.progress })

        val error = client.callTool(convert, buildJsonObject { put("value", 1); put("from_unit", "km"); put("to_unit", "kg") })
        assertTrue(error.isError)

        assertTrue(client.listResources().any { it.uri == "notes://all" })
        assertTrue(client.listPrompts().any { it.name == "summarize_notes" })
    }

    @Test
    fun legacyServer_fallsBackToAnInitializeSession() = runBlocking {
        assumeTrue(legacy != null)
        val client = McpClient(legacy!!)
        val tools = client.listTools()
        assertTrue("negotiated ${client.protocolVersion}", client.protocolVersion in McpClient.LEGACY_VERSIONS)
        assertEquals("legacy-notes", client.serverInfo?.name)
        assertEquals("echo: hi", client.callTool(tools.single { it.name == "echo" }, buildJsonObject { put("text", "hi") }).toText())

        val progress = mutableListOf<McpProgress>()
        client.callTool(tools.single { it.name == "count_slowly" }, buildJsonObject { put("to", 2) }) { progress += it }
        assertEquals(2, progress.size)
        assertTrue(client.callTool(tools.single { it.name == "fail" }, buildJsonObject { put("reason", "nope") }).isError)
        client.close()
    }

    @Test
    fun toolset_exposesServersAsApprovalAwareAgentTools() = runBlocking {
        assumeTrue(modern != null && legacy != null)
        val servers = listOf(
            McpServerConfig("notes", "Notes", modern!!),
            McpServerConfig("legacy", "Legacy", legacy!!, disabledTools = setOf("fail")),
            McpServerConfig("down", "Down", "http://127.0.0.1:1/mcp"),
        )
        val clients = servers.associate { it.id to McpClient(it.url) }
        val toolset = McpToolset(servers, { clients.getValue(it.id) }, timeoutMs = 5_000)
        val tools = toolset.tools().associateBy { it.name }
        assertTrue(tools.keys.toString(), "notes__convert_units" in tools && "legacy__echo" in tools && "legacy__fail" !in tools)
        assertEquals(false, tools.getValue("notes__convert_units").requiresApproval) // readOnlyHint
        assertEquals(true, tools.getValue("notes__save_note").requiresApproval)
        assertEquals("Convert units · Notes", tools.getValue("notes__convert_units").title)
        assertTrue(toolset.status["down"] is McpServerStatus.Failed)
        assertTrue(toolset.status["legacy"] is McpServerStatus.Connected)
        assertTrue(ToolCallContext.current() == null)
    }

    /** MCP Apps (2026-01-26) against the official SDK: UI tool metadata, app-only tools, the view resource, the chat part. */
    @Test
    fun modernServer_mcpApps() = runBlocking {
        assumeTrue(modern != null)
        val client = McpClient(modern!!, capabilities = McpApps.CLIENT_CAPABILITIES)
        val tools = client.listTools().associateBy { it.name }
        val board = tools.getValue("show_notes_board")
        assertEquals("ui://notes/board", board.uiResourceUri)
        assertEquals(setOf("model", "app"), board.visibility)
        assertEquals(setOf("app"), tools.getValue("board_notes").visibility)

        val view = client.readResource("ui://notes/board").single()
        assertEquals(McpApps.MIME_TYPE, view.mimeType)
        assertTrue(view.text!!.contains("@modelcontextprotocol/ext-apps"))
        assertEquals("[\"https://cdn.jsdelivr.net\"]", view.meta!!.obj("ui")!!.obj("csp")!!["resourceDomains"].toString())

        // The model never sees app-only tools; a UI tool's call publishes the MCP App part, then its result.
        val server = McpServerConfig("notes", "Notes", modern)
        val agentTools = McpToolset(listOf(server), { client }).tools().associateBy { it.name }
        assertTrue(agentTools.keys.toString(), "notes__show_notes_board" in agentTools && "notes__board_notes" !in agentTools)
        val updates = mutableListOf<ToolCallContext.Update>()
        withContext(ToolCallContext("call-1", ToolApprover.AlwaysApprove) { updates += it }) {
            agentTools.getValue("notes__show_notes_board").execute(buildJsonObject {})
        }
        val parts = updates.filterIsInstance<ToolCallContext.Update.Data>()
        assertEquals(listOf(DataPart.MCP_APP, DataPart.MCP_APP), parts.map { it.name })
        assertEquals(setOf("mcp-app-call-1"), parts.map { it.id }.toSet())
        val done = parts.last().data.jsonObject
        assertEquals("ui://notes/board", done["resourceUri"]!!.jsonPrimitive.content)
        assertEquals("show_notes_board", done["tool"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertTrue(parts.first().data.jsonObject["result"] == null)
        assertTrue(done["result"]!!.jsonObject["structuredContent"]!!.jsonObject.containsKey("notes"))
    }
}
