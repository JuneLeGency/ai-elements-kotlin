package dev.ai.elements.core.agent

import dev.ai.elements.core.ChatBackend
import dev.ai.elements.core.ChatEvent
import dev.ai.elements.core.ToolApprover
import dev.ai.elements.core.backend.runTool
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.TextPart
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.core.model.ToolState
import dev.ai.elements.core.reduce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SubAgentsTest {

    /** A delegate that calls one tool needing approval, then answers. */
    private val researcher = SubAgent("researcher", "Researches topics") { approver ->
        ChatBackend { history ->
            flow {
                emit(ChatEvent.ReasoningDelta("r", "thinking"))
                emit(ChatEvent.ReasoningEnd("r"))
                emit(ChatEvent.ToolInputAvailable("inner", "fetch", "{}"))
                emit(ChatEvent.ToolApprovalRequest("inner"))
                val ok = approver.approve("inner")
                emit(if (ok) ChatEvent.ToolOutput("inner", "page") else ChatEvent.ToolDenied("inner"))
                emit(ChatEvent.TextDelta("t", "Findings about " + history.single().text))
                emit(ChatEvent.TextEnd("t"))
            }
        }
    }

    @Test
    fun instructionsAndSchema_matchPydanticAiHarness() = runBlocking<Unit> {
        val subAgents = SubAgents(listOf(researcher))
        assertEquals(
            "You can delegate self-contained tasks to these sub-agents using the `delegate_task` tool. Each runs in its own " +
                "fresh context and does not see this conversation, so pass everything it needs.\n\nAvailable sub-agents:\n- researcher: Researches topics",
            subAgents.instructions,
        )
        val tool = subAgents.tools().single()
        assertEquals("delegate_task", tool.name)
        assertTrue(tool.parameters.toString().contains("\"enum\":[\"researcher\"]"))
        assertThrows(IllegalArgumentException::class.java) { SubAgents(listOf(researcher, researcher)) }
        assertThrows(IllegalArgumentException::class.java) { SubAgent("bad name", "x") { error("unused") } }
    }

    @Test
    fun delegation_streamsTheNestedRun_andSharesTheApprover() = runBlocking<Unit> {
        val asked = mutableListOf<String>()
        val approver = ToolApprover { asked += it; true }
        // Run the delegate tool inside a real agent loop so ToolCallContext is installed.
        val loop = ChatBackend { _ ->
            flow {
                val tools = SubAgents(listOf(researcher)).tools()
                val args = buildJsonObject { put("agent_name", "researcher"); put("task", "AG-UI") }.toString()
                runTool(tools, approver, "call-1", "delegate_task", args)
            }
        }
        val events = loop.stream(listOf(Message("u", Role.USER, listOf(TextPart("t", "go"))))).toList()
        var reply = Message("a", Role.ASSISTANT)
        events.forEach { reply = reply.reduce(it, 0) }

        val call = reply.parts.filterIsInstance<ToolPart>().single()
        assertEquals("researcher", call.title)
        assertEquals(ToolState.OUTPUT_AVAILABLE, call.state)
        assertEquals("Findings about AG-UI", call.output)
        val nested = call.subagent!!
        assertEquals("Findings about AG-UI", nested.text)
        assertEquals(ToolState.OUTPUT_AVAILABLE, (nested.parts.single { it is ToolPart } as ToolPart).state)
        assertEquals(listOf("inner"), asked)
        assertTrue(events.count { it is ChatEvent.SubagentUpdate } >= 2)
        assertTrue(nested.parts.none { it.isStreaming })
    }

    @Test
    fun unknownAgent_isAToolError() = runBlocking<Unit> {
        val tool = SubAgents(listOf(researcher)).tools().single()
        val e = runCatching { tool.execute(buildJsonObject { put("agent_name", "nobody"); put("task", "x") }) }.exceptionOrNull()
        assertTrue(e?.message.orEmpty().contains("Available sub-agents: researcher"))
        Unit
    }
}
