package dev.ai.elements.core.mcp

import com.sun.net.httpserver.HttpServer
import dev.ai.elements.core.chat.InputRequest
import dev.ai.elements.core.chat.InputResponse
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/**
 * MCP 2026-07-28 elicitation (SEP-2322) against responses recorded from the official `mcp` SDK
 * (`server/record_fixtures.py`): the client asks the user, then retries the call with the answers
 * under the request's keys and the server's `requestState` echoed byte for byte.
 */
class McpElicitationTest {
    private lateinit var server: HttpServer
    private val requests = CopyOnWriteArrayList<JsonObject>()
    private val replies = ArrayDeque<String>()

    private fun fixture(name: String) = javaClass.getResource("/fixtures/mcp/$name")!!.readText()

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/mcp") { exchange ->
            val request = Json.parseToJsonElement(exchange.requestBody.use { String(it.readBytes()) }).jsonObject
            requests += request
            // The recorded response, under this request's id.
            val recorded = Json.parseToJsonElement(synchronized(replies) { replies.removeFirst() }).jsonObject
            val body = JsonObject(recorded + ("id" to request["id"]!!)).toString().toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
    }

    @After
    fun tearDown() = server.stop(0)

    @Test
    fun inputRequired_isAskedThenRetriedWithTheAnswersAndState() = runBlocking {
        replies += fixture("elicitation-input-required.json")
        replies += fixture("elicitation-complete.json")
        val first = Json.parseToJsonElement(fixture("elicitation-input-required.json")).jsonObject["result"]!!.jsonObject
        val key = first["inputRequests"]!!.jsonObject.keys.single()
        val client = McpClient("http://127.0.0.1:${server.address.port}/mcp")
        val asked = mutableListOf<InputRequest>()
        val answer = buildJsonObject { put("party_size", 4); put("time", "19:30"); put("seating", "outdoor"); put("remind_me", false) }

        val result = client.callTool("book_table", buildJsonObject { put("restaurant", "Sora") }, onInput = { asked += it; InputResponse.Accept(answer) })

        assertEquals("Booked a table for 4 at Sora, 19:30, outdoor.", result.toText())
        assertEquals(key, asked.single().id)
        assertEquals("object", asked.single().schema!!["type"]!!.jsonPrimitive.content)
        assertEquals("AI Elements notes", client.serverInfo?.title)
        // The declared capability, then the retry: same call, the answers under their keys, the state verbatim.
        val capabilities = requests[0]["params"]!!.jsonObject["_meta"]!!.jsonObject["io.modelcontextprotocol/clientCapabilities"]!!.jsonObject
        assertEquals(setOf("form", "url"), capabilities["elicitation"]!!.jsonObject.keys)
        val retry = requests[1]["params"]!!.jsonObject
        assertEquals("book_table", retry["name"]!!.jsonPrimitive.content)
        assertEquals(first["requestState"], retry["requestState"])
        val response = retry["inputResponses"]!!.jsonObject[key]!!.jsonObject
        assertEquals("accept", response["action"]!!.jsonPrimitive.content)
        assertEquals(answer, response["content"])
    }

    @Test
    fun declined_sendsTheActionWithoutContent() = runBlocking<Unit> {
        replies += fixture("elicitation-input-required.json")
        replies += fixture("elicitation-complete.json")
        McpClient("http://127.0.0.1:${server.address.port}/mcp").callTool("book_table", buildJsonObject { put("restaurant", "Sora") }, onInput = { InputResponse.Decline })
        val response = requests[1]["params"]!!.jsonObject["inputResponses"]!!.jsonObject.values.single().jsonObject
        assertEquals(buildJsonObject { put("action", "decline") }, response)
    }
}
