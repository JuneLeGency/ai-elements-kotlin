package dev.ai.elements.a2a

import dev.ai.elements.core.ChatEvent
import dev.ai.elements.core.agent.SubAgents
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.TextPart
import dev.ai.elements.core.reduce
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.a2aproject.sdk.client.http.android.AndroidA2AHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Live A2A round trips against the bundled server (`server/a2a_agent.py`, the
 * official Python `a2a-sdk`) through the official Java SDK. Opt-in:
 * `./gradlew :ai-elements-a2a:testDebugUnitTest -PliveA2a=http://localhost:8788`
 * Uses the same official Android HTTP client as the app (plain HttpURLConnection; the
 * SDK's JDK client attempts an h2c upgrade that uvicorn rejects).
 */
class LiveA2aTest {
    private val url: String? = System.getProperty("live.a2a")
    private fun agent() = A2aAgent(url!!, httpClient = AndroidA2AHttpClient())

    private fun ask(agent: A2aAgent, history: List<Message>): Pair<List<ChatEvent>, Message> = runBlocking {
        val events = A2aBackend(agent).stream(history).toList()
        var reply = Message("a", Role.ASSISTANT)
        events.forEach { reply = reply.reduce(it, 0) }
        events to reply
    }

    @Test
    fun card_describesTheAgent() = runBlocking {
        assumeTrue(url != null)
        val card = agent().card()
        assertEquals("Research agent", card.name())
        assertTrue(card.capabilities().streaming())
        assertEquals("research", card.skills().single().id())
    }

    @Test
    fun streamedTask_becomesTextProgressAndMetadata() {
        assumeTrue(url != null)
        val agent = agent()
        val (events, reply) = ask(agent, listOf(Message("u1", Role.USER, listOf(TextPart("t", "What is AG-UI?")))))
        assertTrue("no error: $events", events.none { it is ChatEvent.Error })
        assertTrue("streams text", reply.text.contains("Agent run complete"))
        assertTrue("working notes shown as a task", reply.parts.any { it is dev.ai.elements.core.model.DataPart && it.name == "task" })
        val a2a = reply.metadata!!["a2a"] as JsonObject
        assertEquals("\"TASK_STATE_COMPLETED\"", a2a["state"].toString())

        // Second turn continues the same A2A context.
        val (_, second) = ask(agent, listOf(Message("u1", Role.USER, listOf(TextPart("t", "What is AG-UI?"))), reply, Message("u2", Role.USER, listOf(TextPart("t", "And MCP?")))))
        assertEquals(a2a["contextId"], (second.metadata!!["a2a"] as JsonObject)["contextId"])
    }

    @Test
    fun remoteAgent_asSubAgent_streamsIntoTheDelegation() = runBlocking {
        assumeTrue(url != null)
        val agent = agent()
        val subAgents = SubAgents(listOf(agent.asSubAgent(agent.card())))
        assertTrue(subAgents.instructions!!.contains("research-agent"))
        val tool = subAgents.tools().single()
        val answer = tool.execute(buildJsonObject { put("agent_name", "research-agent"); put("task", "Explain A2A briefly.") })
        assertTrue(answer, answer.contains("Agent run complete"))
    }
}
