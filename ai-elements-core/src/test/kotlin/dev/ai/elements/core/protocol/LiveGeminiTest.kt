package dev.ai.elements.core.protocol

import dev.ai.elements.core.chat.ChatEvent
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
import dev.ai.elements.core.provider.gemini.GeminiBackend

/**
 * Opt-in: the Gemini backend against Google's own API.
 *
 *     GEMINI_API_KEY=… ./gradlew :ai-elements-core:testDebugUnitTest --tests '*LiveGeminiTest*'
 *
 * The key is read from the environment only (never a command-line argument or log line).
 */
class LiveGeminiTest {
    private val key = System.getenv("GEMINI_API_KEY").orEmpty()
    private val model = System.getenv("LIVE_GEMINI_MODEL").orEmpty().ifBlank { "gemini-2.5-flash" }

    private fun run(prompt: String) = runBlocking {
        withTimeout(120_000) {
            GeminiBackend("https://generativelanguage.googleapis.com", model, key, tools = BuiltinTools)
                .stream(listOf(Message("u1", Role.USER, listOf(TextPart("t1", prompt))))).toList()
        }
    }

    @Test fun toolLoop_streamsReasoningCallsToolAndAnswers() {
        assumeTrue("set GEMINI_API_KEY", key.isNotBlank())
        val events = run("Use the calculate tool to compute 1234 * 5678, then state the result.")
        val text = events.filterIsInstance<ChatEvent.TextDelta>().joinToString("") { it.delta }
        val tools = events.filterIsInstance<ChatEvent.ToolOutput>()
        val errors = events.filterIsInstance<ChatEvent.Error>()
        val usage = events.filterIsInstance<ChatEvent.Usage>().lastOrNull()
        println("[gemini/$model] tools=${tools.map { it.output }} reasoningChars=${events.filterIsInstance<ChatEvent.ReasoningDelta>().sumOf { it.delta.length }} usage=$usage errors=${errors.map { it.message.take(160) }} text=${text.take(140).replace("\n", " ")}")
        assertTrue("no errors: $errors", errors.isEmpty())
        assertTrue("tool ran", tools.any { it.output.contains("7006652") })
        assertTrue("answer states the result", text.replace(",", "").replace(" ", "").contains("7006652"))
        assertTrue("usage reported", usage != null)
    }
}
