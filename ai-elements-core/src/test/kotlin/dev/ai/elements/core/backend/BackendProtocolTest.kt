package dev.ai.elements.core.backend

import com.sun.net.httpserver.HttpServer
import dev.ai.elements.core.ChatBackend
import dev.ai.elements.core.ChatBackendException
import dev.ai.elements.core.ChatEvent
import dev.ai.elements.core.agent.BuiltinTools
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.TextPart
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Drives each real backend against an in-process HTTP server replaying recorded
 * protocol fixtures (one response per request, in order), and asserts the
 * resulting [ChatEvent] sequence and the requests sent.
 */
class BackendProtocolTest {

    private lateinit var server: HttpServer
    private val responses = ArrayDeque<Pair<Int, String>>()
    private val requests = CopyOnWriteArrayList<String>()
    private val base get() = "http://127.0.0.1:${server.address.port}"

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests += exchange.requestMethod + " " + exchange.requestURI + "\n" +
                exchange.requestBody.use { String(it.readBytes()) }
            val (code, body) = synchronized(responses) { responses.removeFirst() }
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @After
    fun tearDown() = server.stop(0)

    private fun respond(body: String, code: Int = 200) = responses.addLast(code to body)

    private val history = listOf(Message("m1", Role.USER, listOf(TextPart("p1", "What time is it?"))))

    private fun ChatBackend.events() = runBlocking { stream(history).toList() }

    @Test
    fun uiMessageStream_mapsChunks() {
        respond(
            """
            data: {"type":"start"}

            data: {"type":"reasoning-delta","id":"r1","delta":"hmm"}

            data: {"type":"reasoning-end","id":"r1"}

            data: {"type":"tool-input-start","toolCallId":"c1","toolName":"get_current_time"}

            data: {"type":"tool-input-available","toolCallId":"c1","toolName":"get_current_time","input":{"timezone":"UTC"}}

            data: {"type":"tool-output-available","toolCallId":"c1","output":"12:00"}

            data: {"type":"text-delta","id":"t1","delta":"Line one\nline two"}

            data: {"type":"text-end","id":"t1"}

            data: {"type":"source-url","sourceId":"s","url":"https://x.dev","title":"X"}

            data: {"type":"finish"}

            data: [DONE]

            """.trimIndent(),
        )
        val events = UiMessageStreamBackend("$base/api/chat", model = "demo").events()
        assertEquals(
            listOf(
                ChatEvent.ReasoningDelta("r1", "hmm"),
                ChatEvent.ReasoningEnd("r1"),
                ChatEvent.ToolInputStart("c1", "get_current_time"),
                ChatEvent.ToolInputAvailable("c1", "get_current_time", """{"timezone":"UTC"}"""),
                ChatEvent.ToolOutput("c1", "12:00"),
                ChatEvent.TextDelta("t1", "Line one\nline two"),
                ChatEvent.TextEnd("t1"),
                ChatEvent.SourceUrl("s", "https://x.dev", "X"),
                ChatEvent.Finish,
            ),
            events,
        )
        val request = requests.single()
        assertTrue(request, request.startsWith("POST /api/chat?model=demo"))
        assertTrue(request, request.contains(""""parts":[{"type":"text","text":"What time is it?"}]"""))
    }

    @Test
    fun openAi_runsToolLoopOnDevice() {
        respond(
            """
            data: {"choices":[{"delta":{"reasoning_content":"need a tool"}}]}

            data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"calculate","arguments":""}}]}}]}

            data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"expression\":"}}]}}]}

            data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"6*7\"}"}}]}}]}

            data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}

            data: [DONE]

            """.trimIndent(),
        )
        respond(
            """
            data: {"choices":[{"delta":{"content":"It is "}}]}

            data: {"choices":[{"delta":{"content":"42."}}]}

            data: [DONE]

            """.trimIndent(),
        )
        val events = OpenAiChatBackend(base, "m", apiKey = "k", tools = BuiltinTools).events()

        assertTrue(events.contains(ChatEvent.ToolInputAvailable("call_1", "calculate", """{"expression":"6*7"}""")))
        assertTrue(events.contains(ChatEvent.ToolOutput("call_1", "42")))
        val text = events.filterIsInstance<ChatEvent.TextDelta>().joinToString("") { it.delta }
        assertEquals("It is 42.", text)
        assertEquals(ChatEvent.Finish, events.last())

        assertEquals(2, requests.size)
        val second = requests[1]
        assertTrue(second, second.contains(""""role":"tool","tool_call_id":"call_1","content":"42""""))
        assertTrue(second, second.contains(""""tool_calls":[{"id":"call_1""""))
    }

    @Test
    fun anthropic_streamsThinkingToolsAndText() {
        respond(
            """
            event: message_start
            data: {"type":"message_start","message":{"id":"msg_1"}}

            event: content_block_start
            data: {"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":""}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"Let me check."}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"sig"}}

            event: content_block_stop
            data: {"type":"content_block_stop","index":0}

            event: content_block_start
            data: {"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"toolu_1","name":"calculate","input":{}}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"expression\": \"2^10\"}"}}

            event: content_block_stop
            data: {"type":"content_block_stop","index":1}

            event: message_delta
            data: {"type":"message_delta","delta":{"stop_reason":"tool_use"}}

            """.trimIndent(),
        )
        respond(
            """
            event: content_block_start
            data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"1024"}}

            event: content_block_stop
            data: {"type":"content_block_stop","index":0}

            """.trimIndent(),
        )
        val events = AnthropicBackend(base, "claude", "key", tools = BuiltinTools).events()

        assertTrue(events.any { it is ChatEvent.ReasoningDelta && it.delta == "Let me check." })
        assertTrue(events.contains(ChatEvent.ToolOutput("toolu_1", "1024")))
        assertEquals("1024", events.filterIsInstance<ChatEvent.TextDelta>().single().delta)
        assertEquals(ChatEvent.Finish, events.last())

        val second = requests[1]
        assertTrue(second, second.contains(""""type":"thinking","thinking":"Let me check.","signature":"sig""""))
        assertTrue(second, second.contains(""""type":"tool_result","tool_use_id":"toolu_1","content":"1024""""))
    }

    @Test
    fun httpError_surfacesServerMessage() {
        respond("""{"error":{"message":"Invalid API key"}}""", code = 401)
        val error = runCatching { OpenAiChatBackend(base, "m").events() }.exceptionOrNull()
        assertTrue(error is ChatBackendException)
        assertEquals("HTTP 401: Invalid API key", error!!.message)
    }

    @Test
    fun connectionRefused_isFriendly() {
        val port = server.address.port
        server.stop(0)
        responses.clear()
        val error = runCatching { OpenAiChatBackend("http://127.0.0.1:$port", "m").events() }.exceptionOrNull()
        assertTrue(error?.message.orEmpty(), error?.message.orEmpty().startsWith("Cannot connect to 127.0.0.1:$port"))
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { it.start() }
    }
}
