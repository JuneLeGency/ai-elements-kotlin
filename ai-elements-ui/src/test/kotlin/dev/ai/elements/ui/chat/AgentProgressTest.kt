package dev.ai.elements.ui.chat

import dev.ai.elements.core.chat.ChatState
import dev.ai.elements.core.chat.InputRequest
import dev.ai.elements.core.model.ChatStatus
import dev.ai.elements.core.model.DataPart
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.ReasoningPart
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.TextPart
import dev.ai.elements.core.model.ToolCategory
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.core.model.ToolState
import dev.ai.elements.ui.chat.AgentProgress.Phase
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where a run stands, from the conversation's model only. */
class AgentProgressTest {
    private val user = Message("u", Role.USER, listOf(TextPart("t", "Fix the build")))
    private fun reply(vararg parts: dev.ai.elements.core.model.Part) = Message("a", Role.ASSISTANT, parts.toList())
    private fun busy(vararg messages: Message) = ChatState(messages = listOf(user) + messages, status = ChatStatus.STREAMING)

    @Test fun phases() {
        assertNull(AgentProgress.of(ChatState()))
        assertEquals(Phase.THINKING, AgentProgress.of(ChatState(messages = listOf(user), status = ChatStatus.SUBMITTED))!!.phase)
        assertEquals(Phase.THINKING, AgentProgress.of(busy(reply(ReasoningPart("r", "…", isStreaming = true))))!!.phase)
        val running = ToolPart("c1", "run_command", ToolState.INPUT_AVAILABLE, """{"command":"ls"}""", category = ToolCategory.EXECUTE)
        AgentProgress.of(busy(reply(running)))!!.let {
            assertEquals(Phase.WORKING, it.phase)
            assertEquals("c1", it.tool!!.id)
            assertEquals(1, it.steps)
            assertTrue(it.isActive)
        }
        assertEquals(Phase.WRITING, AgentProgress.of(busy(reply(running.copy(state = ToolState.OUTPUT_AVAILABLE), TextPart("x", "Done", isStreaming = true))))!!.phase)
        val approval = running.copy(id = "c2", name = "save_note", state = ToolState.APPROVAL_REQUESTED)
        AgentProgress.of(busy(reply(approval)))!!.let { assertEquals(Phase.NEEDS_APPROVAL, it.phase); assertEquals("c2", it.tool!!.id) }
        assertEquals(Phase.NEEDS_INPUT, AgentProgress.of(busy(reply()).copy(inputRequests = listOf(InputRequest("i", "Which room?"))))!!.phase)
        AgentProgress.of(ChatState(messages = listOf(user, reply(TextPart("x", "All green.")))))!!.let {
            assertEquals(Phase.DONE, it.phase)
            assertEquals("All green.", it.reply)
            assertFalse(it.isActive)
        }
        AgentProgress.of(ChatState(messages = listOf(user, reply()), status = ChatStatus.ERROR, error = "HTTP 500"))!!.let {
            assertEquals(Phase.FAILED, it.phase)
            assertEquals("HTTP 500", it.error)
        }
    }

    @Test fun planProgress_fromAPlanPartOrSharedState() {
        val plan = Json.parseToJsonElement("""{"steps":[{"label":"Read","status":"complete"},{"label":"Fix","status":"active"},{"label":"Test"}]}""")
        AgentProgress.of(busy(reply(DataPart("p", "plan", plan))))!!.let {
            assertEquals(3, it.plan.size)
            assertEquals(1, it.planDone)
        }
        val state = Json.parseToJsonElement("""{"plan":{"steps":[{"label":"Read","status":"complete"},{"label":"Fix","status":"complete"}]}}""")
        assertEquals(2, AgentProgress.of(busy(reply(DataPart("s", DataPart.STATE, state))))!!.planDone)
    }
}
