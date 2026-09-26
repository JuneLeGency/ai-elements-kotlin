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
 * Opt-in live runs through a multi-format gateway (e.g. CLIProxyAPI), which
 * serves real models behind the OpenAI, Anthropic and Gemini wire formats —
 * the one place the Gemini backend meets a real server.
 *
 *     LIVE_PROXY_KEY=… ./gradlew :ai-elements-core:testDebugUnitTest --tests '*LiveProxyTest*' -PliveProxy=http://localhost:8317
 *
 * The key comes from the environment only, so it never appears in a command line or log.
 */
class LiveProxyTest {
    private val proxy = System.getProperty("live.proxy").orEmpty().trimEnd('/')
    private val key = System.getenv("LIVE_PROXY_KEY").orEmpty()
    private val gemini = System.getProperty("live.proxyGemini").orEmpty().ifBlank { "gemini-2.5-flash" }
    private val other = System.getProperty("live.proxyModel").orEmpty().ifBlank { "qwen3-max" }

    private val prompt = listOf(Message("u1", Role.USER, listOf(TextPart("t1", "Use the calculate tool to compute 1234 * 5678, then state the result."))))

    private fun run(backend: ChatBackend): List<ChatEvent> = runBlocking { withTimeout(180_000) { backend.stream(prompt).toList() } }

    private fun check(name: String, events: List<ChatEvent>) {
        val text = events.filterIsInstance<ChatEvent.TextDelta>().joinToString("") { it.delta }
        val tools = events.filterIsInstance<ChatEvent.ToolOutput>()
        val errors = events.filterIsInstance<ChatEvent.Error>()
        println("[$name] tools=${tools.map { it.output }} usage=${events.filterIsInstance<ChatEvent.Usage>().lastOrNull()} errors=${errors.map { it.message.take(120) }} text=${text.take(120).replace("\n", " ")}")
        assertTrue("$name: no errors $errors", errors.isEmpty())
        assertTrue("$name: tool ran", tools.any { it.output.replace(",", "").contains("7006652") })
        assertTrue("$name: answer states the result", text.replace(",", "").replace(" ", "").contains("7006652"))
    }

    private fun ready() = assumeTrue("set -PliveProxy and LIVE_PROXY_KEY", proxy.isNotBlank() && key.isNotBlank())

    @Test fun geminiNative() {
        ready()
        check("gemini-native/$gemini", run(GeminiBackend(proxy, gemini, key, tools = BuiltinTools)))
    }

    @Test fun anthropicMessages() {
        ready()
        check("anthropic/$other", run(AnthropicBackend(proxy, other, key, tools = BuiltinTools)))
    }

    @Test fun openAiChat() {
        ready()
        check("openai-chat/$other", run(OpenAiChatBackend("$proxy/v1", other, key, tools = BuiltinTools)))
    }

    @Test fun openAiResponses() {
        ready()
        check("openai-responses/$other", run(OpenAiResponsesBackend("$proxy/v1", other, key, tools = BuiltinTools)))
    }
}
