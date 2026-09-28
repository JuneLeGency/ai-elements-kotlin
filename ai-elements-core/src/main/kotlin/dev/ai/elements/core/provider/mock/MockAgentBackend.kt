package dev.ai.elements.core.provider.mock

import dev.ai.elements.core.agent.AgentTool
import dev.ai.elements.core.agent.BuiltinTools
import dev.ai.elements.core.agent.runTool
import dev.ai.elements.core.chat.ChatBackend
import dev.ai.elements.core.chat.ChatEvent
import dev.ai.elements.core.chat.ToolApprover
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
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
 * Naming a tool in the prompt (e.g. `notes__show_notes_board`) calls it; a JSON object after the
 * name is its arguments (`notes__book_table {"restaurant": "Sora"}`).
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
        val copy = MockCopy.of(prompt)
        val math = extractExpression(prompt)
        val named = tools.filter { prompt.contains(it.name) }.maxByOrNull { it.name.length }
        if (JSX_WORDS.any { prompt.contains(it, ignoreCase = true) }) {
            generativeUi(copy)
            return@flow
        }
        if (tools.any { it.name == "navigate" } && BROWSE_WORDS.any { prompt.contains(it, ignoreCase = true) }) {
            browse(prompt, copy)
            return@flow
        }
        val wantsCopy = tools.any { it.name == "copy_to_clipboard" } &&
            listOf("copy", "clipboard", "复制", "剪贴板", "複製", "剪貼簿", "コピー", "クリップボード").any { prompt.contains(it, ignoreCase = true) }

        // A live plan (AI SDK `data-plan` part): re-emitted with the same id as steps progress.
        val planId = "plan-${UUID.randomUUID()}"
        val stepLabels = copy.planSteps
        suspend fun plan(active: Int) = emit(
            ChatEvent.Data(
                planId,
                "plan",
                buildJsonObject {
                    put("title", copy.planTitle)
                    put("description", copy.planDescription(stepLabels.size))
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
        val intent = when {
            named != null -> MockCopy.Intent.NAMED
            wantsCopy -> MockCopy.Intent.COPY
            math != null -> MockCopy.Intent.MATH
            else -> MockCopy.Intent.TIME
        }
        streamText(copy.reasoning(prompt.take(80), intent, named?.name)) { emit(ChatEvent.ReasoningDelta(reasoningId, it)) }
        emit(ChatEvent.ReasoningEnd(reasoningId))

        val callId = "call_${UUID.randomUUID()}"
        val (toolName, args) = when {
            named != null -> named.name to prompt.substringAfter(named.name).let { rest ->
                val start = rest.indexOf('{')
                val end = rest.lastIndexOf('}')
                if (start >= 0 && end > start) rest.substring(start, end + 1) else "{}"
            }
            wantsCopy -> "copy_to_clipboard" to buildJsonObject { put("text", copy.copiedText) }.toString()
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
        val body = copy.answer(toolName, result) +
            if (listOf("long", "长", "長").any { prompt.contains(it, ignoreCase = true) }) (1..24).joinToString("", transform = copy::longSection) else ""
        streamText(body) { emit(ChatEvent.TextDelta(textId, it)) }
        emit(ChatEvent.TextEnd(textId))
        plan(3)
        emit(ChatEvent.Usage(inputTokens = history.sumOf { it.text.length } / 4 + 180, outputTokens = 420))
        emit(ChatEvent.Finish)
    }

    /**
     * A scripted browser-use run with the browser's own tools (as the reference server's scripted
     * model does): open a page, read its structure, follow the first link from the snapshot, and
     * look at the result with `screenshot`. Each step shows in the agent's computer with its
     * screenshot, the clicked link outlined.
     */
    private suspend fun FlowCollector<ChatEvent>.browse(prompt: String, copy: MockCopy) {
        // A URL is RFC 3986 characters only: prose around it (a full-width comma, CJK text) is not part of it.
        val url = Regex("""https?://[A-Za-z0-9\-._~:/?#\[\]@!$&'()*+,;=%]+""").find(prompt)?.value?.trimEnd('.', ',', ')', ';', '!', '?') ?: "https://example.com"
        suspend fun say(text: String) {
            val id = "text-${UUID.randomUUID()}"
            streamText(text) { emit(ChatEvent.TextDelta(id, it)) }
            emit(ChatEvent.TextEnd(id))
        }
        suspend fun call(name: String, args: String): String {
            val id = "call_${UUID.randomUUID()}"
            emit(ChatEvent.ToolInputStart(id, name))
            pause(10)
            return runTool(tools, approver, id, name, args)
        }
        val reasoningId = "reasoning-${UUID.randomUUID()}"
        streamText(copy.browseReasoning(url)) {
            emit(ChatEvent.ReasoningDelta(reasoningId, it))
        }
        emit(ChatEvent.ReasoningEnd(reasoningId))
        say(copy.browseOpening)
        val opened = call("navigate", buildJsonObject { put("url", url) }.toString())
        if (opened.startsWith("Error")) {
            say(copy.browseFailed(opened))
            emit(ChatEvent.Finish)
            return
        }
        val link = Regex("""- link "([^"]*)" \[ref=(e\d+)]""").find(call("snapshot", "{}"))
        if (link != null) {
            say(copy.browseFollow(link.groupValues[1]))
            call("click", buildJsonObject { put("selector", "aria-ref=${link.groupValues[2]}") }.toString())
        }
        val shot = call("screenshot", "{}")
        say(copy.browseDone(url, link?.groupValues?.get(1)?.escapeMarkdown(), shot.substringBefore('\n')))
        emit(ChatEvent.Finish)
    }

    /**
     * An answer with interface: a ```jsx fence (the AI Elements `JSXPreview` input) that apps
     * rendering JSX show as a live, native form while it streams.
     */
    private suspend fun FlowCollector<ChatEvent>.generativeUi(copy: MockCopy) {
        val id = "text-${UUID.randomUUID()}"
        streamText(copy.formIntro + "\n\n```jsx\n" + copy.bookingJsx + "\n```\n\n" + copy.formOutro) { emit(ChatEvent.TextDelta(id, it)) }
        emit(ChatEvent.TextEnd(id))
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

    companion object {
        /** Prompts that get an answer with interface (JSX). */
        private val JSX_WORDS = listOf("jsx", "generative ui", "画面を作")

        /** Prompts that start the scripted browser run (with the browser's tools available). */
        private val BROWSE_WORDS = listOf("browse", "browser", "浏览", "网页", "瀏覽", "網頁", "ブラウズ", "ブラウザ")

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
