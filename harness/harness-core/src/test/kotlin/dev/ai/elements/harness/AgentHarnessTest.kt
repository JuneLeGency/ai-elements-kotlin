package dev.ai.elements.harness

import dev.ai.elements.core.chat.ChatBackend
import dev.ai.elements.core.chat.ChatEvent
import dev.ai.elements.core.chat.ToolApprover
import dev.ai.elements.core.agent.AgentTool
import dev.ai.elements.core.agent.StaticCapability
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.TextPart
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentHarnessTest {
    private data class Run(val tools: List<String>, val instructions: String)

    private val runs = mutableListOf<Run>()
    private val toolsOfRun = mutableListOf<List<AgentTool>>()

    /** Records what each run was given and answers with its own run number. */
    private val model = ModelBinding { tools, instructions, _ ->
        runs += Run(tools.map { it.name }, instructions)
        toolsOfRun += tools
        val n = runs.size
        ChatBackend { flow { emit(ChatEvent.TextDelta("t", "run $n")) } }
    }

    private val echo = object : AgentTool {
        override val name = "echo"
        override val description = "Echo"
        override val parameters = buildJsonObject { put("type", "object") }
        override suspend fun execute(arguments: kotlinx.serialization.json.JsonObject) = "echo"
    }

    private fun turn(harness: AgentHarness) = runBlocking {
        harness.backend(ToolApprover.AlwaysApprove).stream(listOf(Message("u", Role.USER, listOf(TextPart("t", "hi"))))).toList()
    }

    @Test
    fun composesCapabilitiesIntoOneRun() {
        turn(AgentHarness(model, capabilities = { listOf(StaticCapability("Use echo.", listOf(echo))) }))
        assertEquals(listOf(Run(listOf("echo"), "Use echo.")), runs)
    }

    @Test
    fun localSubAgents_runOnTheHarness_withinMaxDepth() = runBlocking<Unit> {
        val harness = AgentHarness(
            model,
            capabilities = { listOf(StaticCapability(tools = listOf(echo))) },
            localSubAgents = { listOf(LocalSubAgent("researcher", "Researches", "Be thorough.")) },
            maxDepth = 2,
        )
        turn(harness)
        val top = runs.single()
        assertEquals(listOf("echo", "delegate_task"), top.tools)
        assertTrue(top.instructions.contains("- researcher: Researches"))

        // Delegating runs the sub-agent on the harness: its own instructions, the harness'
        // capabilities, and no further delegation at maxDepth.
        val delegate = toolsOfRun.single().single { it.name == "delegate_task" }
        val answer = delegate.execute(buildJsonObject { put("agent_name", "researcher"); put("task", "dig") })
        assertEquals("run 2", answer)
        assertEquals(Run(listOf("echo"), "Be thorough."), runs[1])
    }

    @Test
    fun maxDepthOne_disablesDelegation() {
        turn(AgentHarness(model, localSubAgents = { listOf(LocalSubAgent("researcher", "Researches", "x")) }, maxDepth = 1))
        assertEquals(emptyList<String>(), runs.single().tools)
    }
}
