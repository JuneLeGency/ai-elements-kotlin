package dev.ai.elements.core.protocol

import dev.ai.elements.core.chat.reduce
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.protocol.agui.AgUiEventLog
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AgUiEventLog.compact] against cases of the reference implementation's tests
 * (`@ag-ui/client` `compact/__tests__/compact.test.ts`), and on recorded server streams: a
 * compacted log must rebuild the same reply.
 */
class AgUiCompactionTest {
    private fun events(vararg json: String) = json.map { Json.parseToJsonElement(it).jsonObject }
    private fun List<JsonObject>.types() = map { it["type"]!!.jsonPrimitive.content }

    @Test fun textContent_isConcatenated() {
        val out = AgUiEventLog.compact(events(
            """{"type":"TEXT_MESSAGE_START","messageId":"msg1","role":"user"}""",
            """{"type":"TEXT_MESSAGE_CONTENT","messageId":"msg1","delta":"Hello"}""",
            """{"type":"TEXT_MESSAGE_CONTENT","messageId":"msg1","delta":" "}""",
            """{"type":"TEXT_MESSAGE_CONTENT","messageId":"msg1","delta":"world"}""",
            """{"type":"TEXT_MESSAGE_END","messageId":"msg1"}""",
        ))
        assertEquals(listOf("TEXT_MESSAGE_START", "TEXT_MESSAGE_CONTENT", "TEXT_MESSAGE_END"), out.types())
        assertEquals("Hello world", out[1]["delta"]!!.jsonPrimitive.content)
    }

    @Test fun interleavedEvents_followTheStream() {
        val out = AgUiEventLog.compact(events(
            """{"type":"TEXT_MESSAGE_START","messageId":"msg1","role":"assistant"}""",
            """{"type":"TEXT_MESSAGE_CONTENT","messageId":"msg1","delta":"Processing"}""",
            """{"type":"CUSTOM","id":"custom1","name":"thinking"}""",
            """{"type":"TEXT_MESSAGE_CONTENT","messageId":"msg1","delta":"..."}""",
            """{"type":"CUSTOM","id":"custom2","name":"done-thinking"}""",
            """{"type":"TEXT_MESSAGE_END","messageId":"msg1"}""",
        ))
        assertEquals(listOf("TEXT_MESSAGE_START", "TEXT_MESSAGE_CONTENT", "TEXT_MESSAGE_END", "CUSTOM", "CUSTOM"), out.types())
        assertEquals("Processing...", out[1]["delta"]!!.jsonPrimitive.content)
        assertEquals(listOf("custom1", "custom2"), out.drop(3).map { it["id"]!!.jsonPrimitive.content })
    }

    @Test fun toolCallArgs_areConcatenated() {
        val out = AgUiEventLog.compact(events(
            """{"type":"TOOL_CALL_START","toolCallId":"t1","toolCallName":"search"}""",
            """{"type":"TOOL_CALL_ARGS","toolCallId":"t1","delta":"{\"q\":"}""",
            """{"type":"TOOL_CALL_ARGS","toolCallId":"t1","delta":"\"x\"}","metadata":{"a":1}}""",
            """{"type":"TOOL_CALL_END","toolCallId":"t1"}""",
        ))
        assertEquals(listOf("TOOL_CALL_START", "TOOL_CALL_ARGS", "TOOL_CALL_END"), out.types())
        assertEquals("""{"q":"x"}""", out[1]["delta"]!!.jsonPrimitive.content)
        assertEquals("""{"a":1}""", out[1]["metadata"].toString())
    }

    @Test fun snapshotAndDeltas_becomeOneSnapshot() {
        val out = AgUiEventLog.compact(events(
            """{"type":"RUN_STARTED","threadId":"t1","runId":"r1"}""",
            """{"type":"STATE_SNAPSHOT","snapshot":{"count":0,"name":"test"}}""",
            """{"type":"STATE_DELTA","delta":[{"op":"replace","path":"/count","value":1}]}""",
            """{"type":"STATE_DELTA","delta":[{"op":"replace","path":"/count","value":2}]}""",
            """{"type":"RUN_FINISHED","threadId":"t1","runId":"r1"}""",
        ))
        assertEquals(listOf("RUN_STARTED", "STATE_SNAPSHOT", "RUN_FINISHED"), out.types())
        assertEquals(Json.parseToJsonElement("""{"count":2,"name":"test"}"""), out[1]["snapshot"])
    }

    @Test fun deltasWithoutASnapshot_stayDeltas() {
        val out = AgUiEventLog.compact(events(
            """{"type":"RUN_STARTED","threadId":"t1","runId":"r1"}""",
            """{"type":"STATE_DELTA","delta":[{"op":"add","path":"/foo","value":"bar"}]}""",
            """{"type":"STATE_DELTA","delta":[{"op":"add","path":"/baz","value":42}]}""",
            """{"type":"RUN_FINISHED","threadId":"t1","runId":"r1"}""",
        ))
        assertEquals(listOf("RUN_STARTED", "STATE_DELTA", "STATE_DELTA", "RUN_FINISHED"), out.types())
    }

    @Test fun deltasAfterTheSnapshot_removeAndAdd() {
        val out = AgUiEventLog.compact(events(
            """{"type":"RUN_STARTED","threadId":"t1","runId":"r1"}""",
            """{"type":"STATE_SNAPSHOT","snapshot":{"a":1,"b":2}}""",
            """{"type":"STATE_DELTA","delta":[{"op":"remove","path":"/b"}]}""",
            """{"type":"STATE_DELTA","delta":[{"op":"add","path":"/c","value":3}]}""",
            """{"type":"RUN_FINISHED","threadId":"t1","runId":"r1"}""",
        ))
        assertEquals(Json.parseToJsonElement("""{"a":1,"c":3}"""), out[1]["snapshot"])
    }

    /**
     * Recorded server runs (sub-agent, plan state, two runs): the compacted log rebuilds the same
     * parts. Compaction moves each run's state to its end (as the reference does), so a state part
     * may change place in the reply; the parts themselves are the same.
     */
    @Test fun recordedRuns_replayTheSameAfterCompaction() {
        listOf(listOf("delegate.sse"), listOf("plan.sse"), listOf("device.sse", "device-continued.sse")).forEach { fixtures ->
            val log = fixtures.flatMap { name ->
                javaClass.getResource("/fixtures/agui/$name")!!.readText().lines()
                    .filter { it.startsWith("data: ") }.map { Json.parseToJsonElement(it.removePrefix("data: ")).jsonObject }
            }
            val compacted = AgUiEventLog.compact(log)
            assertTrue("$fixtures: ${compacted.size} < ${log.size}", compacted.size < log.size)
            fun rebuild(events: List<JsonObject>) = runBlocking { AgUiEventLog.replay(events, speed = 1_000f).toList() }
                .fold(Message("a", Role.ASSISTANT)) { m, e -> m.reduce(e, 0) }
            assertEquals(fixtures.toString(), rebuild(log).parts.sortedBy { it.id }, rebuild(compacted).parts.sortedBy { it.id })
        }
    }
}
