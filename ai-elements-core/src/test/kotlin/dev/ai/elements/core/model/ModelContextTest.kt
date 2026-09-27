package dev.ai.elements.core.model

import com.sun.net.httpserver.HttpServer
import dev.ai.elements.core.protocol.agui.AgUiBackend
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetSocketAddress

/** [DataPart.MODEL_CONTEXT] reaches the model: inline for model APIs, `RunAgentInput.context` for AG-UI. */
class ModelContextTest {
    private val context = DataPart("c", DataPart.MODEL_CONTEXT, buildJsonObject { put("description", "Notes board (MCP App)"); put("value", "Shows 1 note: Milk") })
    private val turn = Message("u1", Role.USER, listOf(TextPart("t", "What's on it?"), context))

    @Test
    fun modelApis_readTheContextBeforeTheUsersWords() {
        val inlined = listOf(turn, Message("a", Role.ASSISTANT, listOf(context))).inlineModelContext()
        assertEquals("<context description=\"Notes board (MCP App)\">\nShows 1 note: Milk\n</context>\n\nWhat's on it?", inlined[0].text)
        assertEquals("", inlined[1].text) // only user turns carry context
        assertEquals("What's on it?", turn.text) // the stored turn is unchanged
    }

    @Test
    fun agUi_sendsTheTurnsContextInRunAgentInput() = runBlocking<Unit> {
        var body = ""
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                body = exchange.requestBody.use { String(it.readBytes()) }
                val reply = "data: {\"type\":\"RUN_STARTED\",\"threadId\":\"t\",\"runId\":\"r\"}\n\ndata: {\"type\":\"RUN_FINISHED\",\"threadId\":\"t\",\"runId\":\"r\"}\n\n".toByteArray()
                exchange.sendResponseHeaders(200, reply.size.toLong())
                exchange.responseBody.use { it.write(reply) }
            }
            start()
        }
        try {
            AgUiBackend("http://127.0.0.1:${server.address.port}/", context = listOf("App" to "AI Elements demo")).stream(listOf(turn)).toList()
        } finally {
            server.stop(0)
        }
        val sent = Json.parseToJsonElement(body).jsonObject["context"]!!.jsonArray
        assertEquals(listOf("App", "Notes board (MCP App)"), sent.map { it.jsonObject["description"]!!.jsonPrimitive.content })
        assertEquals("Shows 1 note: Milk", (sent as JsonArray)[1].jsonObject["value"]!!.jsonPrimitive.content)
    }
}
