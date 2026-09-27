package dev.ai.elements.core.protocol

import dev.ai.elements.core.chat.ChatBackend
import dev.ai.elements.core.chat.ChatEvent
import dev.ai.elements.core.chat.ToolApprover
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import dev.ai.elements.core.protocol.aisdk.UiMessageStreamBackend
import dev.ai.elements.core.protocol.agui.AgUiBackend

/**
 * Every Pydantic AI Harness capability of the reference server (`server/main.py`), end to end
 * over both open protocols (AG-UI 1.0 and AI SDK 6). The server's offline scripted model picks
 * the capability from a keyword, so this needs no model key. Opt-in:
 * `./gradlew :ai-elements-core:testDebugUnitTest --tests '*LiveHarnessServerTest*' -PliveAgentServer=http://localhost:8788`
 */
class LiveHarnessServerTest {
    private val server = System.getProperty("live.agentServer").orEmpty()

    private class Protocol(val name: String, val create: (List<AgentTool>, ToolApprover) -> ChatBackend)

    private val protocols get() = listOf(
        Protocol("ag-ui") { tools, approver -> AgUiBackend("$server/api/agui", tools = tools, approver = approver) },
        Protocol("ai-sdk") { tools, approver -> UiMessageStreamBackend("$server/api/chat", tools = tools, approver = approver) },
    )

    private fun run(backend: ChatBackend, prompt: String): Pair<List<ChatEvent>, Message> = runBlocking {
        val events = backend.stream(listOf(Message("u-$prompt", Role.USER, listOf(TextPart("t", prompt))))).toList()
        var reply = Message("a", Role.ASSISTANT)
        events.forEach { reply = reply.reduce(it, 0) }
        events to reply
    }

    private fun Message.tool(name: String) = parts.filterIsInstance<ToolPart>().first { it.name == name }

    @Test
    fun subAgents_delegateTask() {
        assumeTrue(server.isNotEmpty())
        protocols.forEach { p ->
            val (events, reply) = run(p.create(emptyList(), ToolApprover.AlwaysApprove), "please delegate")
            assertTrue("${p.name}: $events", events.none { it is ChatEvent.Error })
            val call = reply.tool("delegate_task")
            assertEquals(p.name, ToolState.OUTPUT_AVAILABLE, call.state)
            assertTrue(p.name, call.input.contains("researcher"))
            assertTrue("${p.name}: ${call.output}", call.output!!.contains("AG-UI"))
        }
    }

    @Test
    fun planning_isMirroredToTheUi() {
        assumeTrue(server.isNotEmpty())
        protocols.forEach { p ->
            val (_, reply) = run(p.create(emptyList(), ToolApprover.AlwaysApprove), "make a plan")
            assertEquals(p.name, ToolState.OUTPUT_AVAILABLE, reply.tool("write_plan").state)
            val data = reply.parts.filterIsInstance<DataPart>().first { it.name == "plan" || it.name == "state" }
            val plan = if (data.name == "state") (data.data as JsonObject)["plan"] as JsonObject else data.data as JsonObject
            assertTrue("${p.name}: $plan", plan.toString().contains("Looking up the docs"))
        }
    }

    @Test
    fun skills_loadCapability() {
        assumeTrue(server.isNotEmpty())
        protocols.forEach { p ->
            val (_, reply) = run(p.create(emptyList(), ToolApprover.AlwaysApprove), "use a skill")
            val load = reply.tool("load_capability")
            assertTrue("${p.name}: ${load.output}", load.output!!.contains("# Skill: mermaid-diagrams"))
        }
    }

    @Test
    fun mcpWriteTool_needsApproval_approvedAndDenied() {
        assumeTrue(server.isNotEmpty())
        protocols.forEach { p ->
            val asked = mutableListOf<String>()
            val (_, approved) = run(p.create(emptyList(), ToolApprover { asked += it; true }), "save a note")
            assertEquals(p.name, 1, asked.size)
            val saved = approved.tool("save_note")
            assertEquals("${p.name}: $saved", ToolState.OUTPUT_AVAILABLE, saved.state)
            assertTrue("${p.name}: ${saved.output}", saved.output!!.contains("Saved note"))

            val (_, denied) = run(p.create(emptyList(), ToolApprover { false }), "save a note")
            assertEquals(p.name, ToolState.OUTPUT_DENIED, denied.tool("save_note").state)
        }
    }

    @Test
    fun frontendTool_runsOnTheDevice_agUi() {
        assumeTrue(server.isNotEmpty())
        val deviceInfo = object : AgentTool {
            override val name = "get_device_info"
            override val description = "Device model and Android version"
            override val parameters = buildJsonObject { put("type", "object") }
            override suspend fun execute(arguments: JsonObject) = "Pixel 10, Android 17"
        }
        val (events, reply) = run(AgUiBackend("$server/api/agui", tools = listOf(deviceInfo)), "device info")
        assertTrue("$events", events.none { it is ChatEvent.Error })
        assertEquals("Pixel 10, Android 17", reply.tool("get_device_info").output)
        assertTrue("the agent continued after the frontend tool: ${reply.text}", reply.text.contains("Pixel 10"))
    }
}
