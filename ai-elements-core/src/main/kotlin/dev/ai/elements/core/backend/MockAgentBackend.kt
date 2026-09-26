package dev.ai.elements.core.backend

import dev.ai.elements.core.ChatBackend
import dev.ai.elements.core.ChatEvent
import dev.ai.elements.core.ToolApprover
import dev.ai.elements.core.agent.AgentTool
import dev.ai.elements.core.agent.BuiltinTools
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.util.UUID

/**
 * An offline agent that exercises every part type — reasoning, a real tool call
 * (executed through [tools]), sources, and a Markdown answer with a table, code
 * and a Mermaid diagram — so the full UI can be demoed without a network.
 *
 * @param chunkDelayMs pause between streamed chunks; 0 in tests.
 */
class MockAgentBackend(
    private val tools: List<AgentTool> = BuiltinTools,
    private val approver: ToolApprover = ToolApprover.AlwaysApprove,
    private val chunkDelayMs: Long = 18,
) : ChatBackend {

    override fun stream(history: List<Message>): Flow<ChatEvent> = flow {
        val prompt = history.lastOrNull { it.role == Role.USER }?.text.orEmpty()
        val math = extractExpression(prompt)
        val wantsCopy = tools.any { it.name == "copy_to_clipboard" } &&
            listOf("copy", "clipboard", "复制", "剪贴板", "複製", "剪貼簿", "コピー", "クリップボード").any { prompt.contains(it, ignoreCase = true) }

        // A live plan (AI SDK `data-plan` part): re-emitted with the same id as steps progress.
        val planId = "plan-${UUID.randomUUID()}"
        val stepLabels = listOf("Think about the request", "Call a tool", "Write the answer")
        suspend fun plan(active: Int) = emit(
            ChatEvent.Data(
                planId,
                "plan",
                buildJsonObject {
                    put("title", "Answer plan")
                    put("description", "${stepLabels.size} steps")
                    put("streaming", active < stepLabels.size)
                    putJsonArray("steps") {
                        stepLabels.forEachIndexed { i, label ->
                            addJsonObject {
                                put("label", label)
                                put("status", when { i < active -> "complete"; i == active -> "active"; else -> "pending" })
                            }
                        }
                    }
                },
            ),
        )
        plan(0)

        val reasoningId = "reasoning-${UUID.randomUUID()}"
        streamText(
            "The user asked: \"${prompt.take(80)}\". I should " +
                (when {
                    wantsCopy -> "copy the text to the clipboard, which needs the user's approval"
                    math != null -> "evaluate the expression with the calculator tool"
                    else -> "check the current time with a tool"
                }) +
                ", then answer with a structured Markdown overview and a Mermaid diagram of the agent loop.",
        ) { emit(ChatEvent.ReasoningDelta(reasoningId, it)) }
        emit(ChatEvent.ReasoningEnd(reasoningId))

        val callId = "call_${UUID.randomUUID()}"
        val (toolName, args) = when {
            wantsCopy -> "copy_to_clipboard" to """{"text": "Hello from the AI Elements agent"}"""
            math != null -> "calculate" to """{"expression": "${math.replace("\"", "")}"}"""
            else -> "get_current_time" to """{"timezone": "Asia/Shanghai"}"""
        }
        plan(1)
        emit(ChatEvent.ToolInputStart(callId, toolName))
        pause(10)
        val result = runTool(tools, approver, callId, toolName, args)

        emit(ChatEvent.SourceUrl("s1", "https://elements.ai-sdk.dev", "AI Elements"))
        emit(ChatEvent.SourceUrl("s2", "https://m3.material.io/blog/building-with-m3-expressive", "M3 Expressive"))
        emit(ChatEvent.SourceUrl("s3", "https://mermaid.js.org/intro/", "Mermaid"))

        plan(2)
        val textId = "text-${UUID.randomUUID()}"
        val body = answer(prompt, toolName, result) +
            if (listOf("long", "长", "長").any { prompt.contains(it, ignoreCase = true) }) longTail() else ""
        streamText(body) { emit(ChatEvent.TextDelta(textId, it)) }
        emit(ChatEvent.TextEnd(textId))
        plan(3)
        emit(ChatEvent.Usage(inputTokens = history.sumOf { it.text.length } / 4 + 180, outputTokens = 420))
        emit(ChatEvent.Finish)
    }

    private suspend fun streamText(text: String, emitChunk: suspend (String) -> Unit) {
        text.chunked(6).forEach {
            emitChunk(it)
            pause(1)
        }
    }

    private suspend fun pause(factor: Int) {
        if (chunkDelayMs > 0) delay(chunkDelayMs * factor)
    }

    private fun answer(prompt: String, tool: String, result: String) = """
        |## Offline agent demo
        |
        |You said: *${prompt.take(120).escapeMarkdown()}*. The agent called **`$tool`** and got `$result`.
        |
        |### What just happened
        |1. **Reasoning** streamed into the collapsible *Thinking* block.
        |2. A **tool call** ran on-device and its input/output are shown above.
        |3. This answer streams as GitHub-flavoured Markdown.
        |
        |The UI follows AI Elements [1] with Material 3 Expressive styling [2]; diagrams render with Mermaid [3].
        |
        || Provider | Where the agent loop runs | Protocol |
        ||---|---|---|
        || Agent server | PydanticAI (server) | UI Message Stream (SSE) |
        || OpenAI-compatible | On-device | Chat Completions (SSE) |
        || Anthropic | On-device | Messages API (SSE) |
        || Offline demo | On-device | — |
        |
        |```mermaid
        |flowchart LR
        |    U([User]) --> C[ChatController]
        |    C --> B{Backend}
        |    B -->|tool_calls| T[[Agent tools]]
        |    T -->|results| B
        |    B -->|text / reasoning| R[Markdown + Mermaid UI]
        |```
        |
        |```kotlin
        |val controller = ChatController(backend = { provider.createBackend(key) }, scope)
        |controller.send("Plan a trip to Kyoto")
        |```
        |
        |> Switch providers from the chip in the top bar — the conversation is kept.
        """.trimMargin()

    /** Extra sections for "long" prompts, to exercise streaming and scrolling. */
    private fun longTail() = (1..24).joinToString("") { i ->
        "\n\n### Section $i\n\nThis is paragraph $i of a long streamed answer. It keeps growing so the " +
            "conversation must follow the bottom while you watch, and stay perfectly still once you scroll up to read. " +
            "Item **$i** has `inline code`, a [link](https://example.com/$i) and some *emphasis*."
    }

    companion object {
        private val expression = Regex("""[(\d.][\d.+\-*/^%() ]*[-+*/^%][\d.+\-*/^%() ]*[\d.)]""")

        /** The first arithmetic expression in [text], with its parentheses balanced. */
        internal fun extractExpression(text: String): String? {
            var expr = expression.find(text)?.value?.trim() ?: return null
            while (expr.count { it == ')' } > expr.count { it == '(' } && expr.endsWith(")")) expr = expr.dropLast(1).trim()
            while (expr.count { it == '(' } > expr.count { it == ')' } && expr.startsWith("(")) expr = expr.drop(1).trim()
            return expr.takeIf { e -> e.any { it.isDigit() } && e.any { it in "+-*/^%" } }
        }
    }
}

/** Backslash-escape Markdown punctuation so echoed user text renders literally (e.g. `1234 * 5678`). */
private fun String.escapeMarkdown(): String = replace(Regex("""([\\`*_\[\]<>#|])"""), """\\$1""")
