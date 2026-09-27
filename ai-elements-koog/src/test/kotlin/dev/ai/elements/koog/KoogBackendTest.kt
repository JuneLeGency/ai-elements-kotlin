package dev.ai.elements.koog

import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.MetaLLMProvider
import dev.ai.elements.core.agent.AgentTool
import dev.ai.elements.core.chat.ChatEvent
import dev.ai.elements.core.chat.ToolApprover
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.TextPart
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KoogBackendTest {
    private val model = LLModel(MetaLLMProvider, "mock", listOf(LLMCapability.Tools, LLMCapability.Completion))
    private val calls = mutableListOf<String>()

    private fun tool(name: String, approval: Boolean = false) = object : AgentTool {
        override val name = name
        override val description = "Echoes text."
        override val parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("text") { put("type", "string"); put("description", "What to echo.") }
                putJsonObject("times") { put("type", "integer") }
            }
            putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("text")) }
        }
        override val requiresApproval = approval
        override suspend fun execute(arguments: JsonObject): String {
            calls += name
            return "echo: ${arguments["text"]!!.jsonPrimitive.content}"
        }
    }

    private fun user(text: String) = Message("u", Role.USER, listOf(TextPart("t", text)))

    private fun run(tool: AgentTool, approver: ToolApprover) = runBlocking {
        val executor = getMockExecutor {
            mockLLMToolCall(KoogTool(tool), buildJsonObject { put("text", "hi") }, toolCallId = "call-1") onRequestEquals "say hi"
            mockLLMAnswer("Done.").asDefaultResponse
        }
        KoogBackend(executor, model, listOf(tool), "Be brief.", approver).stream(listOf(user("say hi"))).toList()
    }

    @Test
    fun descriptor_mapsJsonSchema() {
        val descriptor = tool("echo").koogDescriptor()
        assertEquals(listOf("text"), descriptor.requiredParameters.map { it.name })
        assertEquals(ToolParameterType.String, descriptor.requiredParameters.single().type)
        assertEquals(ToolParameterType.Integer, descriptor.optionalParameters.single().type)
    }

    @Test
    fun toolCall_runsThroughTheChatsToolEvents() {
        val events = run(tool("echo"), ToolApprover.AlwaysApprove)
        assertEquals(listOf("echo"), calls)
        val input = events.filterIsInstance<ChatEvent.ToolInputAvailable>().single()
        assertEquals("call-1", input.id)
        assertEquals("echo", input.name)
        assertEquals(ChatEvent.ToolOutput("call-1", "echo: hi"), events.filterIsInstance<ChatEvent.ToolOutput>().single())
        assertEquals("Done.", events.filterIsInstance<ChatEvent.TextDelta>().joinToString("") { it.delta })
        assertEquals(ChatEvent.Finish, events.last())
    }

    @Test
    fun approval_denied_skipsTheTool() {
        val events = run(tool("delete_all", approval = true), ToolApprover { false })
        assertTrue(calls.isEmpty())
        assertTrue(events.contains(ChatEvent.ToolApprovalRequest("call-1")))
        assertTrue(events.contains(ChatEvent.ToolDenied("call-1")))
        assertEquals(ChatEvent.Finish, events.last())
    }
}
