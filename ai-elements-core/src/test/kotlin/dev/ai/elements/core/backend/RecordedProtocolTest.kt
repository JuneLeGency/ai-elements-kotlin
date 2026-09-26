package dev.ai.elements.core.backend

import com.sun.net.httpserver.HttpServer
import dev.ai.elements.core.ChatBackend
import dev.ai.elements.core.ChatEvent
import dev.ai.elements.core.ToolApprover
import dev.ai.elements.core.agent.AgentTool
import dev.ai.elements.core.model.DataPart
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.TextPart
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.core.model.ToolState
import dev.ai.elements.core.reduce
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
}
