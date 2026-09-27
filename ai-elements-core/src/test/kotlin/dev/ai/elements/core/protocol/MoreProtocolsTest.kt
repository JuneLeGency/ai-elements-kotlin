package dev.ai.elements.core.protocol

import com.sun.net.httpserver.HttpServer
import dev.ai.elements.core.chat.ChatBackend
import dev.ai.elements.core.chat.ChatBackendException
import dev.ai.elements.core.chat.ChatEvent
import dev.ai.elements.core.agent.AgentTool
import dev.ai.elements.core.agent.BuiltinTools
import dev.ai.elements.core.model.FilePart
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.TextPart
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import dev.ai.elements.core.protocol.agui.AgUiBackend
import dev.ai.elements.core.provider.anthropic.AnthropicBackend
import dev.ai.elements.core.provider.gemini.GeminiBackend
import dev.ai.elements.core.provider.ollama.OllamaBackend
import dev.ai.elements.core.provider.openai.OpenAiChatBackend
import dev.ai.elements.core.provider.openai.OpenAiResponsesBackend
import dev.ai.elements.core.protocol.aisdk.UiMessageStreamBackend

/** Fixture tests for the remaining wire protocols and gateway edge cases. */
class MoreProtocolsTest {

    private lateinit var server: HttpServer
    private val responses = ArrayDeque<Triple<Int, String, String>>()
    private val requests = CopyOnWriteArrayList<String>()
    private val base get() = "http://127.0.0.1:${server.address.port}"

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests += exchange.requestMethod + " " + exchange.requestURI + "\n" +
                exchange.requestBody.use { String(it.readBytes()) }
            val (code, type, body) = synchronized(responses) { responses.removeFirst() }
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", type)
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @After
    fun tearDown() = server.stop(0)

    private fun respond(body: String, type: String = "text/event-stream", code: Int = 200) =
        responses.addLast(Triple(code, type, body.trimIndent()))

    private val history = listOf(Message("m1", Role.USER, listOf(TextPart("p1", "Time in Tokyo?"))))
    private fun ChatBackend.events(h: List<Message> = history) = runBlocking { stream(h).toList() }
    private fun List<ChatEvent>.text() = filterIsInstance<ChatEvent.TextDelta>().joinToString("") { it.delta }

    @Test
    fun aiSdkV4DataStream_isDetectedAutomatically() {
        respond(
            """
            f:{"messageId":"step_1"}
            g:"Checking the clock."
            9:{"toolCallId":"c1","toolName":"get_current_time","args":{"timezone":"Asia/Tokyo"}}
            a:{"toolCallId":"c1","result":"12:00 JST"}
            e:{"finishReason":"tool-calls","usage":{"promptTokens":10,"completionTokens":5}}
            0:"It is "
            0:"noon.\n"
            h:{"sourceType":"url","id":"s1","url":"https://time.is","title":"Time.is"}
            d:{"finishReason":"stop"}
            """,
            type = "text/plain; charset=utf-8",
        )
        val events = UiMessageStreamBackend("$base/api/chat").events()
        assertEquals("It is noon.\n", events.text())
        assertTrue(events.contains(ChatEvent.ToolOutput("c1", "12:00 JST")))
        assertTrue(events.contains(ChatEvent.Usage(10, 5)))
        assertTrue(events.contains(ChatEvent.SourceUrl("s1", "https://time.is", "Time.is")))
        // Reasoning (step 0) and text (step 1) are separate parts, in order.
        assertEquals("r0", events.filterIsInstance<ChatEvent.ReasoningDelta>().single().id)
        assertTrue(events.filterIsInstance<ChatEvent.TextDelta>().all { it.id == "t1" })
    }

    @Test
    fun openAiResponses_runsToolLoopAndEchoesItems() {
        respond(
            """
            event: response.output_item.added
            data: {"type":"response.output_item.added","item":{"id":"rs_1","type":"reasoning","summary":[]}}

            event: response.reasoning_summary_text.delta
            data: {"type":"response.reasoning_summary_text.delta","item_id":"rs_1","delta":"Need the tool."}

            event: response.output_item.done
            data: {"type":"response.output_item.done","item":{"id":"rs_1","type":"reasoning","encrypted_content":"enc"}}

            event: response.output_item.added
            data: {"type":"response.output_item.added","item":{"id":"fc_1","type":"function_call","call_id":"call_1","name":"calculate"}}

            event: response.function_call_arguments.delta
            data: {"type":"response.function_call_arguments.delta","item_id":"fc_1","delta":"{\"expression\":\"2+2\"}"}

            event: response.output_item.done
            data: {"type":"response.output_item.done","item":{"id":"fc_1","type":"function_call","call_id":"call_1","name":"calculate","arguments":"{\"expression\":\"2+2\"}"}}

            event: response.completed
            data: {"type":"response.completed","response":{"usage":{"input_tokens":30,"output_tokens":12}}}
            """,
        )
        respond(
            """
            event: response.output_text.delta
            data: {"type":"response.output_text.delta","item_id":"msg_1","delta":"Four."}

            event: response.output_text.done
            data: {"type":"response.output_text.done","item_id":"msg_1"}

            event: response.completed
            data: {"type":"response.completed","response":{"usage":{"input_tokens":50,"output_tokens":3}}}
            """,
        )
        val events = OpenAiResponsesBackend(base, "m", tools = BuiltinTools).events()
        assertTrue(events.contains(ChatEvent.ReasoningDelta("rs_1", "Need the tool.")))
        assertTrue(events.contains(ChatEvent.ToolOutput("call_1", "4")))
        assertEquals("Four.", events.text())
        assertEquals(ChatEvent.Finish, events.last())
        val second = requests[1]
        assertTrue(second, second.contains(""""encrypted_content":"enc""""))
        assertTrue(second, second.contains(""""type":"function_call_output","call_id":"call_1","output":"4""""))
    }

    @Test
    fun ollamaNative_ndjsonWithThinkingToolsAndUsage() {
        respond(
            """
            {"message":{"role":"assistant","content":"","thinking":"hmm"},"done":false}
            {"message":{"role":"assistant","content":"","tool_calls":[{"id":"call_a","function":{"name":"calculate","arguments":{"expression":"3*3"}}}]},"done":false}
            {"message":{"role":"assistant","content":""},"done":true,"prompt_eval_count":20,"eval_count":7}
            """,
            type = "application/x-ndjson",
        )
        respond(
            """
            {"message":{"role":"assistant","content":"Nine"},"done":false}
            {"message":{"role":"assistant","content":""},"done":true,"prompt_eval_count":40,"eval_count":2}
            """,
            type = "application/x-ndjson",
        )
        val events = OllamaBackend(base, "qwen3", tools = BuiltinTools).events()
        assertTrue(events.any { it is ChatEvent.ReasoningDelta && it.delta == "hmm" })
        assertTrue(events.contains(ChatEvent.ToolOutput("call_a", "9")))
        assertEquals("Nine", events.text())
        assertEquals(listOf(ChatEvent.Usage(20, 7), ChatEvent.Usage(40, 2)), events.filterIsInstance<ChatEvent.Usage>())
        assertTrue(requests[1], requests[1].contains(""""role":"tool","tool_name":"calculate","content":"9""""))
    }

    @Test
    fun gemini_thoughtsFunctionCallsAndSignatures() {
        respond(
            """
            data: {"candidates":[{"content":{"role":"model","parts":[{"text":"Plan: use the tool.","thought":true}]}}]}

            data: {"candidates":[{"content":{"role":"model","parts":[{"functionCall":{"name":"calculate","args":{"expression":"5*5"}},"thoughtSignature":"sig=="}]}}],"usageMetadata":{"promptTokenCount":11,"candidatesTokenCount":4,"thoughtsTokenCount":6}}
            """,
        )
        respond(
            """
            data: {"candidates":[{"content":{"role":"model","parts":[{"text":"25"}]},"finishReason":"STOP"}]}
            """,
        )
        val events = GeminiBackend(base, "gemini-x", "key", tools = BuiltinTools).events()
        assertTrue(events.any { it is ChatEvent.ReasoningDelta && it.delta == "Plan: use the tool." })
        assertEquals("25", events.filterIsInstance<ChatEvent.ToolOutput>().single().output)
        assertEquals("25", events.text())
        assertTrue(events.contains(ChatEvent.Usage(11, 10)))
        val first = requests[0]
        assertTrue(first, first.startsWith("POST /v1beta/models/gemini-x:streamGenerateContent?alt=sse"))
        val second = requests[1]
        assertTrue(second, second.contains(""""thoughtSignature":"sig==""""))
        assertTrue(second, second.contains(""""functionResponse":{"""))
    }

    @Test
    fun agUi_mapsEvents() {
        respond(
            """
            data: {"type":"RUN_STARTED","threadId":"t","runId":"r"}

            data: {"type":"REASONING_MESSAGE_START","messageId":"rm1","role":"reasoning"}

            data: {"type":"REASONING_MESSAGE_CONTENT","messageId":"rm1","delta":"think"}

            data: {"type":"REASONING_MESSAGE_END","messageId":"rm1"}

            data: {"type":"TOOL_CALL_START","toolCallId":"tc1","toolCallName":"get_current_time"}

            data: {"type":"TOOL_CALL_ARGS","toolCallId":"tc1","delta":"{\"timezone\":\"UTC\"}"}

            data: {"type":"TOOL_CALL_END","toolCallId":"tc1"}

            data: {"type":"TOOL_CALL_RESULT","messageId":"x","toolCallId":"tc1","content":"12:00"}

            data: {"type":"TEXT_MESSAGE_START","messageId":"tm1","role":"assistant"}

            data: {"type":"TEXT_MESSAGE_CONTENT","messageId":"tm1","delta":"Noon."}

            data: {"type":"TEXT_MESSAGE_END","messageId":"tm1"}

            data: {"type":"RUN_FINISHED","threadId":"t","runId":"r"}
            """,
        )
        val events = AgUiBackend("$base/api/agui").events()
        assertEquals(
            listOf(
                ChatEvent.ReasoningDelta("rm1", "think"),
                ChatEvent.ReasoningEnd("rm1"),
                ChatEvent.ToolInputStart("tc1", "get_current_time"),
                ChatEvent.ToolInputDelta("tc1", """{"timezone":"UTC"}"""),
                ChatEvent.ToolInputAvailable("tc1", "get_current_time", """{"timezone":"UTC"}"""),
                ChatEvent.ToolOutput("tc1", "12:00"),
                ChatEvent.TextDelta("tm1", "Noon."),
                ChatEvent.TextEnd("tm1"),
                ChatEvent.Finish,
            ),
            events,
        )
        assertTrue(requests[0], requests[0].contains(""""messages":[{"id":"m1","role":"user","content":"Time in Tokyo?"}]"""))
    }

    @Test
    fun jsonErrorWith200_isReportedNotSwallowed() {
        respond("""{"status":"435","msg":"Model not support","body":null}""", type = "application/json")
        val error = runCatching { OpenAiChatBackend(base, "m").events() }.exceptionOrNull()
        assertTrue(error is ChatBackendException)
        assertEquals("Provider returned a non-streaming response: Model not support", error!!.message)
    }

    @Test
    fun deniedTool_isNotExecutedAndModelIsTold() {
        var ran = false
        val dangerous = object : AgentTool {
            override val name = "delete_files"
            override val description = "Deletes files"
            override val parameters: JsonObject = buildJsonObject { put("type", "object") }
            override val requiresApproval = true
            override suspend fun execute(arguments: JsonObject): String { ran = true; return "deleted" }
        }
        respond(
            """
            data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c9","function":{"name":"delete_files","arguments":"{}"}}]}}]}

            data: [DONE]
            """,
        )
        respond("""
            data: {"choices":[{"delta":{"content":"OK, I won't."}}]}

            data: [DONE]
            """)
        val events = OpenAiChatBackend(base, "m", tools = listOf(dangerous), approver = { false }).events()
        assertTrue(!ran)
        assertTrue(events.contains(ChatEvent.ToolApprovalRequest("c9")))
        assertTrue(events.contains(ChatEvent.ToolDenied("c9")))
        assertTrue(requests[1], requests[1].contains("denied"))
    }

    @Test
    fun imageAttachments_useEachProvidersFormat() {
        val withImage = listOf(
            Message(
                "m1", Role.USER,
                listOf(FilePart("f", "image/png", "data:image/png;base64,AAAA"), TextPart("t", "What is this?")),
            ),
        )
        val done = "data: [DONE]\n"
        respond(done); OpenAiChatBackend(base, "m").events(withImage)
        respond(""); AnthropicBackend(base, "m", "k").events(withImage)
        respond("", type = "application/x-ndjson"); OllamaBackend(base, "m").events(withImage)
        respond(""); GeminiBackend(base, "m", "k").events(withImage)
        assertTrue(requests[0], requests[0].contains(""""type":"image_url","image_url":{"url":"data:image/png;base64,AAAA"}"""))
        assertTrue(requests[1], requests[1].contains(""""source":{"type":"base64","media_type":"image/png","data":"AAAA"}"""))
        assertTrue(requests[2], requests[2].contains(""""images":["AAAA"]"""))
        assertTrue(requests[3], requests[3].contains(""""inlineData":{"mimeType":"image/png","data":"AAAA"}"""))
    }

    @Test
    fun uiStream_dataPartsAndUsageMetadata() {
        respond(
            """
            data: {"type":"data-plan","id":"p1","data":{"title":"Plan","steps":[]}}

            data: {"type":"message-metadata","messageMetadata":{"usage":{"inputTokens":12,"outputTokens":34}}}

            data: {"type":"finish"}
            """,
        )
        val events = UiMessageStreamBackend("$base/api/chat").events()
        val data = events.filterIsInstance<ChatEvent.Data>().single()
        assertEquals("p1", data.id)
        assertEquals("plan", data.name)
        assertTrue(events.contains(ChatEvent.Usage(12, 34)))
    }
}
