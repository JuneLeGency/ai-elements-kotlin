package dev.ai.elements.mcpapps

import com.sun.net.httpserver.HttpServer
import dev.ai.elements.core.mcp.McpClient
import dev.ai.elements.core.mcp.McpContent
import dev.ai.elements.core.mcp.McpTool
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The host bridge against a session recorded between the official MCP Apps SDKs
 * (`server/mcp_apps/record_fixtures.mjs`: `App` ↔ `AppBridge` of `@modelcontextprotocol/ext-apps`):
 * the view's messages are replayed and our replies compared with the reference host's.
 */
class McpAppBridgeTest {
    private lateinit var server: HttpServer
    private val calls = CopyOnWriteArrayList<JsonObject>()

    private val tools = Json.parseToJsonElement(
        """[
        {"name":"save_note","inputSchema":{"type":"object"},"annotations":{"readOnlyHint":false}},
        {"name":"board_notes","inputSchema":{"type":"object"},"annotations":{"readOnlyHint":true},"_meta":{"ui":{"resourceUri":"ui://notes/board","visibility":["app"]}}},
        {"name":"show_notes_board","inputSchema":{"type":"object"},"annotations":{"readOnlyHint":true},"_meta":{"ui":{"resourceUri":"ui://notes/board"}}},
        {"name":"model_only","inputSchema":{"type":"object"},"annotations":{"readOnlyHint":true},"_meta":{"ui":{"visibility":["model"]}}}
        ]""",
    )

    @Before
    fun setUp() {
        // A minimal MCP server (stateless 2026-07-28 era) that answers like the reference host's handlers.
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/mcp") { exchange ->
            val request = Json.parseToJsonElement(exchange.requestBody.use { String(it.readBytes()) }).jsonObject
            calls += request
            val params = request["params"]?.jsonObject
            val result = when (request["method"]!!.jsonPrimitive.content) {
                "tools/list" -> buildJsonObject { put("tools", tools) }
                "tools/call" -> if (params!!["name"]!!.jsonPrimitive.content == "save_note") {
                    json("""{"content":[{"type":"text","text":"Saved note “${params["arguments"]!!.jsonObject["title"]!!.jsonPrimitive.content}”."}]}""")
                } else {
                    json("""{"content":[{"type":"text","text":"1 note(s)"}],"structuredContent":{"notes":[{"title":"Milk","content":"2 litres"}]}}""")
                }
                "resources/read" -> json("""{"contents":[{"uri":"notes://all","mimeType":"text/markdown","text":"## Milk\n2 litres"}]}""")
                else -> json("{}")
            }
            val body = buildJsonObject { put("jsonrpc", "2.0"); put("id", request["id"]!!); put("result", result) }.toString().toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
    }

    @After
    fun tearDown() = server.stop(0)

    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject

    private val client get() = McpClient("http://127.0.0.1:${server.address.port}/mcp")
    private val resource = McpAppResource("ui://notes/board", "<!doctype html><html></html>", McpAppCsp(resourceDomains = listOf("https://cdn.jsdelivr.net")))
    private val tool = json("""{"name":"show_notes_board","description":"Show the notes board","inputSchema":{"type":"object","properties":{}},"_meta":{"ui":{"resourceUri":"ui://notes/board"}}}""")
    private val context = json("""{"theme":"light","displayMode":"inline","availableDisplayModes":["inline","fullscreen"],"platform":"mobile","locale":"en-US"}""")

    private class RecordingHost : McpAppHost {
        val links = mutableListOf<String>()
        val messages = mutableListOf<String>()
        var modelContext: JsonArray? = null
        val sizes = mutableListOf<Pair<Double?, Double?>>()
        val logs = mutableListOf<String>()
        val audited = mutableListOf<String>()
        var approve = true
        override suspend fun openLink(url: String) = true.also { links += url }
        override suspend fun message(text: String, content: JsonArray) = true.also { messages += text }
        override suspend fun updateModelContext(content: JsonArray?, structuredContent: JsonElement?) { modelContext = content }
        override suspend fun requestDisplayMode(mode: String, current: String) = mode
        override fun sizeChanged(width: Double?, height: Double?) { sizes += width to height }
        override fun log(level: String, data: JsonElement?) { logs += "$level ${data?.jsonPrimitive?.content}" }
        override suspend fun approveToolCall(tool: McpTool, arguments: JsonObject) = approve
        override fun audit(method: String, params: JsonElement?) { audited += method }
    }

    private fun bridge(host: McpAppHost, sent: MutableList<JsonObject>) =
        McpAppBridge(client, resource, "call-1", tool, host, context) { sent += it }

    private fun recorded(): List<Pair<String, JsonObject>> =
        javaClass.getResource("/fixtures/mcp-apps/official-session.jsonl")!!.readText().lines().filter { it.isNotBlank() }.map {
            val entry = json(it)
            entry["from"]!!.jsonPrimitive.content to entry["message"]!!.jsonObject
        }

    @Test
    fun officialSession_repliesLikeTheReferenceHost() = runBlocking {
        val session = recorded()
        val reference = session.filter { it.first == "host" }.map { it.second }
        val host = RecordingHost()
        val sent = mutableListOf<JsonObject>()
        val bridge = bridge(host, sent)
        // The tool's input and result exist before the view connects: they wait for `initialized`.
        bridge.toolInput(JsonObject(emptyMap()))
        bridge.toolResult(json("""{"content":[{"type":"text","text":"No notes yet."}],"structuredContent":{"notes":[]}}"""))
        assertTrue(sent.isEmpty())

        session.filter { it.first == "view" }.map { it.second }.takeWhile { it["id"]?.jsonPrimitive?.content != "0" || it["method"] != null }
            .forEach { bridge.receive(it.toString()) }

        // ui/initialize: same protocol version, at least the reference host's capabilities, the call's toolInfo.
        val init = sent.first()["result"]!!.jsonObject
        val refInit = reference.first()["result"]!!.jsonObject
        assertEquals(refInit["protocolVersion"], init["protocolVersion"])
        assertTrue(init["hostCapabilities"]!!.jsonObject.keys.containsAll(refInit["hostCapabilities"]!!.jsonObject.keys))
        assertEquals(tool, init["hostContext"]!!.jsonObject["toolInfo"]!!.jsonObject["tool"])
        assertEquals("""{"resourceDomains":["https://cdn.jsdelivr.net"]}""", init["hostCapabilities"]!!.jsonObject["sandbox"]!!.jsonObject["csp"].toString())

        // Then the queued tool input and result, exactly as the reference host sent them.
        assertEquals(reference[1], sent[1])
        assertEquals(reference[2], sent[2])

        // Every request of the view gets the reference host's result.
        val ours = sent.filter { it["id"] != null && it["method"] == null }.associateBy { it["id"]!!.jsonPrimitive.content }
        reference.filter { it["id"] != null && it["method"] == null && it["id"]!!.jsonPrimitive.content != "0" }.forEach { ref ->
            val id = ref["id"]!!.jsonPrimitive.content
            assertEquals("reply to request $id", ref["result"], ours.getValue(id)["result"])
        }

        assertEquals(listOf("https://modelcontextprotocol.io/extensions/apps"), host.links)
        assertEquals(listOf("Summarize the notes on my board"), host.messages)
        assertEquals("The notes board shows 1 note(s): Milk", host.modelContext!!.first().jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals(listOf(360.0 to 420.0), host.sizes)
        assertEquals(listOf("info board rendered"), host.logs)
        assertEquals("fullscreen", bridge.displayMode)
        assertTrue(host.audited.containsAll(listOf("ui/initialize", "tools/call", "ui/message", "ui/open-link")))
        assertEquals(listOf("save_note", "board_notes"), calls.filter { it["method"]!!.jsonPrimitive.content == "tools/call" }.map { it["params"]!!.jsonObject["name"]!!.jsonPrimitive.content })

        // Host context changes reach the view as the changed fields.
        sent.clear()
        bridge.setHostContext(JsonObject(context + mapOf("theme" to JsonPrimitive("dark"), "displayMode" to JsonPrimitive("fullscreen"))))
        assertEquals("""{"theme":"dark"}""", sent.single()["params"].toString())

        // Teardown: the reference request, answered by the view's recorded response.
        sent.clear()
        val refTeardown = reference.last()
        val teardown = coroutineScope {
            val answered = async { bridge.teardown(timeoutMs = 2_000) }
            yield()
            assertEquals(refTeardown["method"], sent.single()["method"])
            bridge.receive(session.last().second.toString())
            answered.await()
        }
        assertTrue(teardown)
    }

    private suspend fun McpAppBridge.initialize() {
        receive("""{"jsonrpc":"2.0","id":0,"method":"ui/initialize","params":{"appInfo":{"name":"v","version":"1"},"appCapabilities":{},"protocolVersion":"2026-01-26"}}""")
        receive("""{"jsonrpc":"2.0","method":"ui/notifications/initialized"}""")
    }

    private fun List<JsonObject>.error(id: Int) = single { it["id"]?.jsonPrimitive?.int == id }["error"]?.jsonObject

    @Test
    fun theViewReachesOnlyAppVisibleToolsOfItsServer_andAsksBeforeWrites() = runBlocking {
        val host = RecordingHost()
        val sent = mutableListOf<JsonObject>()
        val bridge = bridge(host, sent).apply { initialize() }
        bridge.receive("""{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"model_only","arguments":{}}}""")
        bridge.receive("""{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"unknown","arguments":{}}}""")
        host.approve = false
        bridge.receive("""{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"save_note","arguments":{"title":"x","content":"y"}}}""")
        assertEquals(-32602, sent.error(1)!!["code"]!!.jsonPrimitive.int)
        assertEquals(-32602, sent.error(2)!!["code"]!!.jsonPrimitive.int)
        assertEquals(-32000, sent.error(3)!!["code"]!!.jsonPrimitive.int)
        assertTrue(calls.none { it["method"]!!.jsonPrimitive.content == "tools/call" })

        // The default host lets read-only tools through and declines the rest.
        val defaults = object : McpAppHost {}
        val tools = McpClient("http://127.0.0.1:${server.address.port}/mcp").listTools().associateBy { it.name }
        assertTrue(defaults.approveToolCall(tools.getValue("board_notes"), JsonObject(emptyMap())))
        assertFalse(defaults.approveToolCall(tools.getValue("save_note"), JsonObject(emptyMap())))
    }

    @Test
    fun linksMessagesAndUnknownMethods_areChecked() = runBlocking {
        val host = RecordingHost()
        val sent = mutableListOf<JsonObject>()
        val bridge = bridge(host, sent).apply { initialize() }
        bridge.receive("""{"jsonrpc":"2.0","id":1,"method":"ui/open-link","params":{"url":"javascript:alert(1)"}}""")
        bridge.receive("""{"jsonrpc":"2.0","id":2,"method":"ui/message","params":{"role":"assistant","content":[{"type":"text","text":"hi"}]}}""")
        bridge.receive("""{"jsonrpc":"2.0","id":3,"method":"ui/message","params":{"role":"user","content":{"type":"text","text":"one block"}}}""")
        bridge.receive("""{"jsonrpc":"2.0","id":4,"method":"sampling/createMessage","params":{}}""")
        bridge.receive("""{"jsonrpc":"2.0","id":5,"method":"ui/request-display-mode","params":{"mode":"pip"}}""")
        bridge.receive("""not json""")
        bridge.receive("""{"id":6,"method":"ping"}""") // not JSON-RPC 2.0
        assertEquals(-32000, sent.error(1)!!["code"]!!.jsonPrimitive.int)
        assertEquals(-32602, sent.error(2)!!["code"]!!.jsonPrimitive.int)
        assertEquals(listOf("one block"), host.messages)
        assertEquals(-32601, sent.error(4)!!["code"]!!.jsonPrimitive.int)
        // pip is not offered by the host: the current mode stays.
        assertEquals("inline", sent.single { it["id"]?.jsonPrimitive?.int == 5 }["result"]!!.jsonObject["mode"]!!.jsonPrimitive.content)
        assertTrue(sent.none { it["id"]?.jsonPrimitive?.int == 6 })
        assertTrue(host.links.isEmpty())
    }

    @Test
    fun csp_followsTheSpecAndRejectsInjection() {
        assertEquals(
            "default-src 'none'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; connect-src 'self'; " +
                "img-src 'self' data:; font-src 'self'; media-src 'self' data:; frame-src 'none'; object-src 'none'; base-uri 'self'",
            McpAppCsp().header(),
        )
        val csp = McpAppCsp(
            connectDomains = listOf("https://api.example.com", "wss://live.example.com:8443"),
            resourceDomains = listOf("https://*.cdn.example.com", "https://evil.com; script-src *", "'unsafe-eval'", "*"),
            frameDomains = listOf("https://www.youtube.com"),
        )
        val header = csp.header()
        assertTrue(header, header.contains("connect-src 'self' https://api.example.com wss://live.example.com:8443"))
        assertTrue(header, header.contains("script-src 'self' 'unsafe-inline' https://*.cdn.example.com;"))
        assertTrue(header, header.contains("frame-src https://www.youtube.com"))
        assertFalse(header, header.contains("evil") || header.contains("unsafe-eval") || header.contains(" *;"))
    }

    @Test
    fun resources_mustBeUiHtmlViews() {
        val meta = json("""{"ui":{"csp":{"connectDomains":["https://api.example.com"]},"permissions":{"camera":{}},"prefersBorder":false}}""")
        val ok = McpAppResource.from("ui://a/b", listOf(McpContent.Resource("ui://a/b", "text/html;profile=mcp-app", "<html></html>", null, meta)))
        assertEquals(listOf("https://api.example.com"), ok.csp.connectDomains)
        assertEquals(setOf("camera"), ok.permissions)
        assertEquals(false, ok.prefersBorder)
        val blob = McpAppResource.from("ui://a/b", listOf(McpContent.Resource("ui://a/b", "text/html;profile=mcp-app", null, "PGh0bWw+PC9odG1sPg==")))
        assertEquals("<html></html>", blob.html)
        listOf(
            McpContent.Resource("ui://a/b", "text/html", "<html></html>", null),
            McpContent.Resource("ui://a/b", "text/html;profile=mcp-app", "  ", null),
        ).forEach { content -> assertTrue(runCatching { McpAppResource.from("ui://a/b", listOf(content)) }.exceptionOrNull() is McpAppException) }
        assertTrue(runCatching { McpAppResource.from("https://a/b", emptyList()) }.exceptionOrNull() is McpAppException)
    }
}
