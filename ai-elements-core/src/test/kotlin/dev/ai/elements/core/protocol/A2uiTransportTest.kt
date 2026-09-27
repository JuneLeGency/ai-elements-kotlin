package dev.ai.elements.core.protocol

import com.sun.net.httpserver.HttpServer
import dev.ai.elements.core.chat.ChatEvent
import dev.ai.elements.core.model.DataPart
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.TextPart
import dev.ai.elements.core.protocol.agui.AgUiBackend
import dev.ai.elements.core.protocol.agui.a2uiAction
import dev.ai.elements.core.protocol.aisdk.UiMessageStreamBackend
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A2UI over the transports, mapped onto the neutral [DataPart.A2UI] part: AG-UI as the official
 * `@ag-ui/a2ui-middleware` emits it (an `a2ui-surface` ACTIVITY_SNAPSHOT carrying
 * `a2ui_operations`; user actions in `forwardedProps.a2uiAction.userAction`), and the AI SDK's
 * `data-*` parts for a user's action.
 */
class A2uiTransportTest {
    private lateinit var server: HttpServer
    private val requests = CopyOnWriteArrayList<String>()
    private var reply = ""
    private val base get() = "http://127.0.0.1:${server.address.port}"

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests += exchange.requestBody.use { String(it.readBytes()) }
            val bytes = reply.toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @After
    fun tearDown() = server.stop(0)

    private val operations = """[
        {"version":"v0.9","createSurface":{"surfaceId":"hotels","catalogId":"https://a2ui.org/specification/v0_9/basic_catalog.json"}},
        {"version":"v0.9","updateComponents":{"surfaceId":"hotels","components":[{"id":"root","component":"Text","text":"Hotel Lumen"}]}}
    ]"""

    private fun sse(vararg events: String) = events.joinToString("") { "data: $it\n\n" }

    private val action = Json.parseToJsonElement(
        """{"version":"v1.0","action":{"name":"book","surfaceId":"hotels","sourceComponentId":"book_btn","timestamp":"2026-09-27T10:00:00Z","context":{"hotel":"Lumen"}}}""",
    ).jsonObject

    private val actionTurn = Message(
        "u2", Role.USER,
        listOf(TextPart("t", "Book Lumen"), DataPart("a1", DataPart.A2UI, JsonArray(listOf(action)))),
    )

    @Test
    fun agUi_a2uiSurfaceActivity_becomesTheNeutralPart() = runBlocking<Unit> {
        reply = sse(
            """{"type":"RUN_STARTED","threadId":"t","runId":"r"}""",
            """{"type":"ACTIVITY_SNAPSHOT","messageId":"a2ui-surface-call1","activityType":"a2ui-surface","content":{"status":"building"},"replace":true}""",
            """{"type":"ACTIVITY_SNAPSHOT","messageId":"a2ui-surface-call1","activityType":"a2ui-surface","content":{"a2ui_operations":${Json.parseToJsonElement(operations)}},"replace":true}""",
            """{"type":"RUN_FINISHED","threadId":"t","runId":"r"}""",
        )
        val events = AgUiBackend(base).stream(listOf(Message("u", Role.USER, listOf(TextPart("t", "hotels"))))).toList()
        val data = events.filterIsInstance<ChatEvent.Data>().filter { it.id == "a2ui-surface-call1" }
        assertEquals(listOf(DataPart.A2UI, DataPart.A2UI), data.map { it.name })
        assertEquals(Json.parseToJsonElement("""{"status":"building"}"""), data[0].data) // pre-paint lifecycle
        assertEquals(Json.parseToJsonElement(operations), data[1].data)
    }

    @Test
    fun agUi_userAction_goesInForwardedProps() = runBlocking<Unit> {
        reply = sse("""{"type":"RUN_STARTED","threadId":"t","runId":"r"}""", """{"type":"RUN_FINISHED","threadId":"t","runId":"r"}""")
        AgUiBackend(base).stream(listOf(actionTurn)).toList()
        val body = Json.parseToJsonElement(requests.single()).jsonObject
        assertEquals(action["action"], body["forwardedProps"]!!.jsonObject["a2uiAction"]!!.jsonObject["userAction"])
    }

    @Test
    fun aiSdk_userAction_isADataPart() = runBlocking<Unit> {
        reply = sse("""{"type":"start"}""", """{"type":"finish"}""")
        UiMessageStreamBackend("$base/api/chat").stream(listOf(actionTurn)).toList()
        val parts = Json.parseToJsonElement(requests.single()).jsonObject["messages"]!!.jsonArray.last().jsonObject["parts"]!!.jsonArray
        val data = parts.single { it.jsonObject["type"]!!.jsonPrimitive.content == "data-a2ui" }.jsonObject
        assertEquals(JsonArray(listOf(action)), data["data"])
        assertTrue(parts.any { it.jsonObject["type"]!!.jsonPrimitive.content == "text" })
    }

    @Test
    fun a2uiAction_onlyFromTheUsersTurn() {
        assertEquals(action["action"], actionTurn.a2uiAction())
        assertEquals(null, Message("a", Role.ASSISTANT, actionTurn.parts).a2uiAction())
        assertEquals(null, Message("u", Role.USER, listOf(TextPart("t", "hi"))).a2uiAction())
    }

}
