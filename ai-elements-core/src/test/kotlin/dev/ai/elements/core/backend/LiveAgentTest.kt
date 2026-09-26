package dev.ai.elements.core.backend

import dev.ai.elements.core.ChatBackend
import dev.ai.elements.core.ChatEvent
import dev.ai.elements.core.agent.BuiltinTools
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.TextPart
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Real agent runs against live models — every protocol, with tools.
 *
 * Skipped unless configured, e.g.:
 * ```
 * ./gradlew :ai-elements-core:testDebugUnitTest --tests '*LiveAgentTest*' \
 *   -PliveOllama=http://localhost:11435 -PliveModel=qwen3:4b -PliveAgentServer=http://localhost:8788
 * ```
 * Ollama serves the OpenAI Chat, OpenAI Responses, Anthropic Messages and its
 * native API, so one local model exercises four client protocols; the agent
 * server (backed by the same model) covers the AI SDK stream and AG-UI.
 */
class LiveAgentTest {
    private val ollama = System.getProperty("live.ollama").orEmpty()
    private val model = System.getProperty("live.model").orEmpty().ifBlank { "qwen3:4b" }
    private val agentServer = System.getProperty("live.agentServer").orEmpty()

    private val prompt = listOf(
        Message("u1", Role.USER, listOf(TextPart("t", "Use the calculate tool to compute 1234 * 5678, then tell me the result."))),
    )

    private fun run(backend: ChatBackend): List<ChatEvent> = runBlocking {
        withTimeout(240_000) { backend.stream(prompt).toList() }
    }

    private fun assertAgentRun(name: String, events: List<ChatEvent>, expectTool: Boolean = true) {
        val text = events.filterIsInstance<ChatEvent.TextDelta>().joinToString("") { it.delta }
        val tools = events.filterIsInstance<ChatEvent.ToolOutput>()
        val reasoning = events.filterIsInstance<ChatEvent.ReasoningDelta>().sumOf { it.delta.length }
        val usage = events.filterIsInstance<ChatEvent.Usage>()
        println("[$name] tools=${tools.map { it.output }} reasoningChars=$reasoning usage=$usage text=${text.take(160).replace("\n", " ")}")
        assertTrue("$name: no error events", events.none { it is ChatEvent.Error })
        if (expectTool) assertTrue("$name: tool ran", tools.any { it.output.replace(",", "").contains("7006652") })
        assertTrue("$name: answer mentions result", text.replace(",", "").replace(" ", "").contains("7006652"))
    }

    @Test
    fun openAiChatCompletions() {
        assumeTrue(ollama.isNotBlank())
        assertAgentRun("openai-chat", run(OpenAiChatBackend("$ollama/v1", model, tools = BuiltinTools)))
    }

    @Test
    fun openAiResponses() {
        assumeTrue(ollama.isNotBlank())
        assertAgentRun("openai-responses", run(OpenAiResponsesBackend("$ollama/v1", model, tools = BuiltinTools)))
    }

    @Test
    fun anthropicMessages() {
        assumeTrue(ollama.isNotBlank())
        assertAgentRun("anthropic", run(AnthropicBackend(ollama, model, "ollama", tools = BuiltinTools)))
    }

    @Test
    fun ollamaNative() {
        assumeTrue(ollama.isNotBlank())
        assertAgentRun("ollama-native", run(OllamaBackend(ollama, model, tools = BuiltinTools)))
    }

    @Test
    fun agentServerAiSdkStream() {
        assumeTrue(agentServer.isNotBlank())
        assertAgentRun("ai-sdk-stream", run(UiMessageStreamBackend("$agentServer/api/chat")))
    }

    @Test
    fun agentServerAgUi() {
        assumeTrue(agentServer.isNotBlank())
        assertAgentRun("ag-ui", run(AgUiBackend("$agentServer/api/agui")))
    }
}
