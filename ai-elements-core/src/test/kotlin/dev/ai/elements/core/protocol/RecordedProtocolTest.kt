package dev.ai.elements.core.protocol

import kotlinx.serialization.json.JsonPrimitive
import dev.ai.elements.core.agent.AskUser
import com.sun.net.httpserver.HttpServer
import dev.ai.elements.core.chat.ChatBackend
import dev.ai.elements.core.chat.ChatEvent
import dev.ai.elements.core.chat.InputRequest
import dev.ai.elements.core.chat.InputResponse
import dev.ai.elements.core.chat.ToolApprover
import dev.ai.elements.core.chat.ToolDecision
import dev.ai.elements.core.agent.AgentTool
import dev.ai.elements.core.model.DataPart
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.TextPart
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.core.model.ToolState
import dev.ai.elements.core.chat.reduce
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import dev.ai.elements.core.protocol.aisdk.UiMessageStreamBackend
import dev.ai.elements.core.protocol.agui.AgUiBackend

/**
 * Replays protocol fixtures recorded from real implementations (`server/record_fixtures.py`:
 * Pydantic AI + Harness through the official AG-UI and Vercel AI adapters, and the official
 * `ag_ui` encoder for AG-UI 1.0 events) and checks both the mapped [ChatEvent]s and the
 * follow-up requests the client sends.
 */
class RecordedProtocolTest {

    private lateinit var server: HttpServer
    private val responses = ArrayDeque<String>()
    private val requests = CopyOnWriteArrayList<JsonObject>()
    private val base get() = "http://127.0.0.1:${server.address.port}"

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests += Json.parseToJsonElement(exchange.requestBody.use { String(it.readBytes()) }).jsonObject
            val body = synchronized(responses) { responses.removeFirst() }.toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
    }

    @After
    fun tearDown() = server.stop(0)

    private fun replay(vararg fixtures: String) = fixtures.forEach { name ->
        responses.addLast(javaClass.getResource("/fixtures/$name")!!.readText())
    }

    private fun run(backend: ChatBackend, prompt: String = "hi"): Pair<List<ChatEvent>, Message> = runBlocking {
        val events = backend.stream(listOf(Message("u1", Role.USER, listOf(TextPart("t", prompt))))).toList()
        var reply = Message("a", Role.ASSISTANT)
        events.forEach { reply = reply.reduce(it, 0) }
        events to reply
    }

    private fun Message.tool(name: String) = parts.filterIsInstance<ToolPart>().first { it.name == name }
    private fun agUi(tools: List<AgentTool> = emptyList(), approver: ToolApprover = ToolApprover.AlwaysApprove) =
        AgUiBackend("$base/api/agui", tools = tools, approver = approver)
    private fun aiSdk(approver: ToolApprover = ToolApprover.AlwaysApprove) = UiMessageStreamBackend("$base/api/chat", approver = approver)

    // --- AG-UI --------------------------------------------------------------------------

    @Test
    fun agUi_delegateTask_isAToolCallWithTheSubAgentsAnswer() {
        replay("agui/delegate.sse")
        val (_, reply) = run(agUi())
        val call = reply.tool("delegate_task")
        assertEquals(ToolState.OUTPUT_AVAILABLE, call.state)
        assertTrue(call.input.contains("\"agent_name\":\"researcher\""))
        assertTrue(call.output!!.contains("AG-UI"))
        assertTrue(reply.text.isNotBlank())
    }

    @Test
    fun agUi_plan_arrivesAsStateSnapshot() {
        replay("agui/plan.sse")
        val (_, reply) = run(agUi())
        val state = reply.parts.filterIsInstance<DataPart>().single { it.name == AgUiBackend.STATE_PART }.data.jsonObject
        val steps = state["plan"]!!.jsonObject["steps"]!!.jsonArray
        assertEquals(listOf("complete", "active", "pending"), steps.map { it.jsonObject["status"]!!.jsonPrimitive.content })
    }

    @Test
    fun agUi_skill_loadCapabilityReturnsInstructions() {
        replay("agui/skill.sse")
        val (_, reply) = run(agUi())
        assertTrue(reply.tool("load_capability").output!!.contains("# Skill: mermaid-diagrams"))
    }

    @Test
    fun agUi_interrupt_asksTheUser_thenResumes() {
        replay("agui/note.sse", "agui/note-resumed.sse")
        val asked = mutableListOf<String>()
        val (events, reply) = run(agUi(approver = ToolApprover { asked += it; true }))
        val call = reply.tool("save_note")
        assertEquals(listOf(call.id), asked)
        assertTrue(events.any { it is ChatEvent.ToolApprovalRequest } && events.any { it is ChatEvent.ToolApproved })
        assertEquals(ToolState.OUTPUT_AVAILABLE, call.state)

        // The follow-up run resumes the interrupt and replays the pending tool call.
        val resume = requests[1]["resume"]!!.jsonArray.single().jsonObject
        assertEquals("int-${call.id}", resume["interruptId"]!!.jsonPrimitive.content)
        assertEquals("resolved", resume["status"]!!.jsonPrimitive.content)
        assertEquals("true", resume["payload"]!!.jsonObject["approved"]!!.jsonPrimitive.content)
        val assistant = requests[1]["messages"]!!.jsonArray.map { it.jsonObject }.single { it["role"]!!.jsonPrimitive.content == "assistant" && it["toolCalls"] != null }
        assertEquals(call.id, assistant["toolCalls"]!!.jsonArray.single().jsonObject["id"]!!.jsonPrimitive.content)
    }

    @Test
    fun agUi_frontendTool_runsOnDevice_thenContinues() {
        replay("agui/device.sse", "agui/device-continued.sse")
        val tool = object : AgentTool {
            override val name = "get_device_info"
            override val description = "Device model and Android version"
            override val parameters = buildJsonObject { put("type", "object") }
            override suspend fun execute(arguments: JsonObject) = "Pixel 10, Android 17"
        }
        val (_, reply) = run(agUi(tools = listOf(tool)))
        assertEquals("Pixel 10, Android 17", reply.tool("get_device_info").output)
        // Advertised as a frontend tool; the result went back as a `tool` message.
        assertEquals("get_device_info", requests[0]["tools"]!!.jsonArray.single().jsonObject["name"]!!.jsonPrimitive.content)
        val toolMessage = requests[1]["messages"]!!.jsonArray.map { it.jsonObject }.single { it["role"]!!.jsonPrimitive.content == "tool" }
        assertEquals("Pixel 10, Android 17", toolMessage["content"]!!.jsonPrimitive.content)
        assertTrue(reply.text.contains("Pixel 10"))
    }

    // --- Pydantic AI Harness `ask_user_question`, answered on the device ------------------

    /** Answers every question with the second option (the multi-select one with its first two). */
    private val answering = object : ToolApprover {
        override suspend fun approve(toolCallId: String) = true
        override suspend fun input(request: InputRequest): InputResponse {
            val properties = request.schema!!["properties"]!!.jsonObject
            return InputResponse.Accept(buildJsonObject {
                put("Database", "Postgres")
                put("Features", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("Sign-in"), JsonPrimitive("Search"))))
            }).also { assertEquals(setOf("Database", "Features"), properties.keys) }
        }
    }

    @Test
    fun agUi_askUser_frontendTool_answeredOnTheDevice() {
        replay("agui/ask.sse", "agui/ask-continued.sse")
        val (_, reply) = run(agUi(tools = listOf(AskUser.tool), approver = answering))
        val call = reply.tool("ask_user_question")
        assertEquals(ToolState.OUTPUT_AVAILABLE, call.state)
        assertEquals("""{"Database":["Postgres"],"Features":["Sign-in","Search"]}""", call.output)
        // Advertised with Harness's schema; the answer went back as the tool message.
        assertTrue(requests[0]["tools"]!!.jsonArray.any { it.jsonObject["name"]!!.jsonPrimitive.content == "ask_user_question" })
        val toolMessage = requests[1]["messages"]!!.jsonArray.map { it.jsonObject }.single { it["role"]!!.jsonPrimitive.content == "tool" }
        assertEquals(call.output, toolMessage["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun aiSdk_askUser_clientSideTool_answeredOnTheDevice() {
        replay("aisdk/ask.sse", "aisdk/ask-answered.sse")
        val (_, reply) = run(UiMessageStreamBackend("$base/api/chat", tools = listOf(AskUser.tool), approver = answering))
        val call = reply.tool("ask_user_question")
        assertEquals("""{"Database":["Postgres"],"Features":["Sign-in","Search"]}""", call.output)
        // The output went back as the tool part's `output-available`, as useChat's addToolResult does.
        val part = requests[1]["messages"]!!.jsonArray.flatMap { m -> m.jsonObject["parts"]?.jsonArray.orEmpty() }
            .map { it.jsonObject }.single { it["type"]!!.jsonPrimitive.content == "tool-ask_user_question" }
        assertEquals("output-available", part["state"]!!.jsonPrimitive.content)
        assertTrue(reply.text.isNotBlank())
    }

    // --- Screenshots of what a tool did (steps with media) ---------------------------------

    @Test
    fun agUi_toolResultMediaParts_becomeFilesAfterTheCall() {
        replay("agui/tool-media.sse")
        val (_, reply) = run(agUi())
        val call = reply.tool("browse")
        assertEquals("Opened https://example.com", call.output)
        val files = reply.parts.filterIsInstance<dev.ai.elements.core.model.FilePart>()
        assertEquals(listOf("image/png", "image/png"), files.map { it.mediaType })
        assertTrue(files[0].url.startsWith("data:image/png;base64,iVBOR"))
        assertEquals("https://example.com/full.png", files[1].url)
        // In order: the call, then its screenshots, then the answer.
        val order = reply.parts.map { it::class.simpleName }
        assertTrue(order.indexOf("ToolPart") < order.indexOf("FilePart") && order.indexOf("FilePart") < order.lastIndexOf("TextPart"))
    }

    @Test
    fun aiSdk_toolFileChunk_followsTheCall() {
        replay("aisdk/browse.sse")
        val (_, reply) = run(aiSdk())
        val call = reply.tool("browse")
        assertTrue(call.output!!.startsWith("Opened https://example.com/pricing"))
        val file = reply.parts.filterIsInstance<dev.ai.elements.core.model.FilePart>().single()
        assertEquals("image/png", file.mediaType)
        assertTrue(reply.parts.indexOf(call) < reply.parts.indexOf(file))
    }

    // --- A2UI (generative UI) on each transport's binding -------------------------------

    private val bookAction = Json.parseToJsonElement(
        """{"version":"v1.0","action":{"name":"book_hotel","surfaceId":"booking-kyoto","sourceComponentId":"book","timestamp":"2026-09-27T10:00:00Z","context":{"city":"Kyoto","guest":"Jane","date":"2026-10-01","room":["deluxe"]}}}""",
    )

    /** Asks for the form, submits it, and returns the form reply and the reply to the action. */
    private fun a2uiRoundTrip(backend: ChatBackend): Pair<Message, Message> = runBlocking {
        val ask = Message("u1", Role.USER, listOf(TextPart("t", "find me a hotel")))
        var form = Message("a1", Role.ASSISTANT)
        backend.stream(listOf(ask)).toList().forEach { form = form.reduce(it, 0) }
        val submit = Message("u2", Role.USER, listOf(TextPart("t", "Book Hotel Lumen for Jane"), DataPart("d", DataPart.A2UI, JsonArray(listOf(bookAction)))))
        var done = Message("a2", Role.ASSISTANT)
        backend.stream(listOf(ask, form, submit)).toList().forEach { done = done.reduce(it, 0) }
        form to done
    }

    private fun assertBookingForm(form: Message) {
        val surface = form.parts.filterIsInstance<DataPart>().single { it.name == DataPart.A2UI }.data.jsonArray
        assertEquals("booking-kyoto", surface[0].jsonObject["createSurface"]!!.jsonObject["surfaceId"]!!.jsonPrimitive.content)
        assertEquals(ToolState.OUTPUT_AVAILABLE, form.tool("show_booking_form").state)
    }

    @Test
    fun agUi_a2uiSurface_thenActionInForwardedProps() {
        replay("agui/hotel.sse", "agui/hotel-action.sse")
        val (form, done) = a2uiRoundTrip(agUi())
        assertBookingForm(form)
        assertEquals(bookAction.jsonObject["action"], requests[1]["forwardedProps"]!!.jsonObject["a2uiAction"]!!.jsonObject["userAction"])
        assertTrue(done.tool("confirm_booking").output!!.contains("Booked a deluxe room at Hotel Lumen for Jane"))
    }

    @Test
    fun aiSdk_a2uiDataPart_thenActionAsDataPart() {
        replay("aisdk/hotel.sse", "aisdk/hotel-action.sse")
        val (form, done) = a2uiRoundTrip(aiSdk())
        assertBookingForm(form)
        val parts = requests[1]["messages"]!!.jsonArray.last().jsonObject["parts"]!!.jsonArray
        assertEquals(JsonArray(listOf(bookAction)), parts.single { it.jsonObject["type"]!!.jsonPrimitive.content == "data-a2ui" }.jsonObject["data"])
        assertTrue(done.tool("confirm_booking").output!!.contains("Booked a deluxe room at Hotel Lumen for Jane"))
    }

    @Test
    fun agUi_v1_subagentStateActivityAndUsage() {
        replay("agui/subagent-state-activity.sse")
        val (events, reply) = run(agUi())
        val call = reply.tool("delegate_task")
        assertEquals("AG-UI streams agent events.", call.subagent!!.text)
        assertEquals("AG-UI streams agent events.", call.output)
        // Subagent text stays inside the delegation, not in the parent reply.
        assertEquals("Delegating.", reply.text)

        val state = reply.parts.filterIsInstance<DataPart>().single { it.name == AgUiBackend.STATE_PART }.data.jsonObject
        assertEquals("complete", state["plan"]!!.jsonObject["steps"]!!.jsonArray[0].jsonObject["status"]!!.jsonPrimitive.content)
        val activity = reply.parts.filterIsInstance<DataPart>().single { it.name == "search" }.data.jsonObject
        assertEquals("3", activity["results"]!!.jsonPrimitive.content)
        assertEquals(ChatEvent.Usage(120, 40), events.filterIsInstance<ChatEvent.Usage>().single())
    }

    // --- AI SDK 6 -------------------------------------------------------------------------

    @Test
    fun aiSdk_delegatePlanSkill() {
        replay("aisdk/delegate.sse")
        assertTrue(run(aiSdk()).second.tool("delegate_task").output!!.contains("AG-UI"))

        replay("aisdk/plan.sse")
        val plan = run(aiSdk()).second.parts.filterIsInstance<DataPart>().last { it.name == "plan" }.data.jsonObject
        assertEquals(3, plan["steps"]!!.jsonArray.size)

        replay("aisdk/skill.sse")
        assertTrue(run(aiSdk()).second.tool("load_capability").output!!.contains("# Skill: mermaid-diagrams"))
    }

    @Test
    fun aiSdk_toolApproval_sendsApprovalResponded() {
        replay("aisdk/note.sse", "aisdk/note-approved.sse")
        val (events, reply) = run(aiSdk())
        val call = reply.tool("save_note")
        assertEquals(ToolState.OUTPUT_AVAILABLE, call.state)
        assertEquals(1, events.count { it is ChatEvent.Finish })

        val parts = requests[1]["messages"]!!.jsonArray.last().jsonObject["parts"] as JsonArray
        val tool = parts.map { it.jsonObject }.single { it["type"]!!.jsonPrimitive.content == "tool-save_note" }
        assertEquals("approval-responded", tool["state"]!!.jsonPrimitive.content)
        assertEquals(call.id, tool["approval"]!!.jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("true", tool["approval"]!!.jsonObject["approved"]!!.jsonPrimitive.content)
    }

    // --- Human in the loop beyond yes / no ---------------------------------------------------

    /** A human in the loop that answers from fixed values. */
    private class Human(
        val decision: ToolDecision = ToolDecision(true),
        val answer: InputResponse = InputResponse.Cancel,
    ) : ToolApprover {
        val asked = mutableListOf<InputRequest>()
        override suspend fun approve(toolCallId: String) = decision.approved
        override suspend fun decide(toolCallId: String) = decision
        override suspend fun input(request: InputRequest) = answer.also { asked += request }
    }

    @Test
    fun agUi_inputInterrupt_asksWithItsSchema_thenResumesWithThePayload() {
        replay("agui/input-interrupt.sse", "agui/input-resumed.sse")
        val trip = buildJsonObject { put("destination", "Kyoto"); put("nights", 5); put("budget", "mid") }
        val human = Human(answer = InputResponse.Accept(trip))
        val (_, reply) = run(agUi(approver = human))
        val question = human.asked.single()
        assertEquals("trip-details", question.id)
        assertEquals("Where to, and for how long?", question.message)
        assertEquals(listOf("destination", "nights"), question.schema!!["required"]!!.jsonArray.map { it.jsonPrimitive.content })
        val resume = requests[1]["resume"]!!.jsonArray.single().jsonObject
        assertEquals("resolved", resume["status"]!!.jsonPrimitive.content)
        assertEquals(trip, resume["payload"])
        assertTrue(reply.text, reply.text.contains("Planning 5 nights in Kyoto"))
    }

    @Test
    fun agUi_inputInterrupt_declined_isCancelled() {
        replay("agui/input-interrupt.sse", "agui/input-resumed.sse")
        run(agUi(approver = Human(answer = InputResponse.Decline)))
        val resume = requests[1]["resume"]!!.jsonArray.single().jsonObject
        assertEquals("cancelled", resume["status"]!!.jsonPrimitive.content)
        assertTrue(resume["payload"] == null)
    }

    /** Pydantic AI advertises `{approved, editedArgs, reason}` on the interrupt; edits and reasons go back in it. */
    @Test
    fun agUi_approval_withEditedArgsOrAReason() {
        replay("agui/note.sse", "agui/note-resumed.sse")
        val edited = buildJsonObject { put("title", "Groceries"); put("content", "Milk, eggs") }
        run(agUi(approver = Human(ToolDecision(true, editedInput = edited))))
        val payload = requests[1]["resume"]!!.jsonArray.single().jsonObject["payload"]!!.jsonObject
        assertEquals("true", payload["approved"]!!.jsonPrimitive.content)
        assertEquals(edited, payload["editedArgs"])

        requests.clear()
        replay("agui/note.sse", "agui/note-resumed.sse")
        val (events, _) = run(agUi(approver = Human(ToolDecision(false, reason = "Not that note"))))
        val denied = requests[1]["resume"]!!.jsonArray.single().jsonObject["payload"]!!.jsonObject
        assertEquals("false", denied["approved"]!!.jsonPrimitive.content)
        assertEquals("Not that note", denied["reason"]!!.jsonPrimitive.content)
        assertTrue(denied["editedArgs"] == null)
        assertEquals("Not that note", events.filterIsInstance<ChatEvent.ToolDenied>().single().reason)
    }

    @Test
    fun aiSdk_denial_carriesTheReason() {
        replay("aisdk/note.sse", "aisdk/note-approved.sse")
        run(aiSdk(approver = Human(ToolDecision(false, reason = "Not now"))))
        val parts = requests[1]["messages"]!!.jsonArray.last().jsonObject["parts"] as JsonArray
        val approval = parts.map { it.jsonObject }.single { it["type"]!!.jsonPrimitive.content == "tool-save_note" }["approval"]!!.jsonObject
        assertEquals("false", approval["approved"]!!.jsonPrimitive.content)
        assertEquals("Not now", approval["reason"]!!.jsonPrimitive.content)
    }
}
