package dev.ai.elements.koog

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.streaming.StreamFrame
import dev.ai.elements.core.agent.AgentTool
import dev.ai.elements.core.agent.runTool
import dev.ai.elements.core.chat.ChatBackend
import dev.ai.elements.core.chat.ChatEvent
import dev.ai.elements.core.chat.ToolApprover
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.TextPart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.channelFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * A [JetBrains Koog](https://docs.koog.ai) agent as a [ChatBackend]: each turn runs a Koog
 * `AIAgent` on [executor] / [model] with [tools] and [instructions], and its lifecycle
 * (LLM calls and streaming frames, tool calls) is mapped onto chat events. Tools run
 * through AI Elements' tool handling, so approvals, progress and sub-agent updates work
 * as with the built-in loop.
 *
 * Plug it into an in-app harness as its model binding — Koog then drives the harness'
 * capabilities (files, sandbox, skills, MCP…):
 *
 * ```kotlin
 * val harness = AgentHarness(
 *     model = { tools, instructions, approver -> KoogBackend(simpleOpenAIExecutor(key), OpenAIModels.Chat.GPT5, tools, instructions, approver) },
 *     capabilities = { listOf(fileSystem, shell, skills) },
 * )
 * ```
 *
 * Earlier turns are passed to Koog as text (user and assistant messages); [strategy]
 * defaults to Koog's `singleRunStrategy()`. A streaming strategy's frames are forwarded
 * as they arrive; otherwise each LLM response appears when it completes.
 */
class KoogBackend(
    private val executor: PromptExecutor,
    private val model: LLModel,
    private val tools: List<AgentTool> = emptyList(),
    private val instructions: String = "",
    private val approver: ToolApprover = ToolApprover.AlwaysApprove,
    private val maxIterations: Int = 50,
    private val strategy: AIAgentGraphStrategy<String, String> = singleRunStrategy(),
) : ChatBackend {

    override fun stream(history: List<Message>): Flow<ChatEvent> = channelFlow {
        val events = FlowCollector<ChatEvent> { send(it) }
        val ids = AtomicInteger()
        // Koog reports a call's id before running it; tools of one name run in call order.
        val pendingIds = ConcurrentHashMap<String, ConcurrentLinkedQueue<String>>()
        var textId: String? = null
        var streamed = false

        suspend fun endText() {
            textId?.let { send(ChatEvent.TextEnd(it)) }
            textId = null
        }

        suspend fun text(delta: String) {
            val id = textId ?: "koog-text-${ids.incrementAndGet()}".also { textId = it }
            send(ChatEvent.TextDelta(id, delta))
        }

        val koogTools = tools.map { tool ->
            KoogTool(tool) { args ->
                endText()
                val id = pendingIds[tool.name]?.poll() ?: "koog-call-${ids.incrementAndGet()}"
                events.runTool(tools, approver, id, tool.name, args.toString())
            }
        }
        val conversation = history.dropLast(1)
        val input = history.lastOrNull { it.role == Role.USER }?.text().orEmpty()
        val agent = AIAgent(
            promptExecutor = executor,
            agentConfig = AIAgentConfig(
                prompt = prompt("ai-elements") {
                    if (instructions.isNotBlank()) system(instructions)
                    conversation.forEach { message ->
                        val content = message.text()
                        if (content.isBlank()) return@forEach
                        when (message.role) {
                            Role.USER -> user(content)
                            Role.ASSISTANT -> assistant(content)
                            else -> Unit
                        }
                    }
                },
                model = model,
                maxAgentIterations = maxIterations,
            ),
            strategy = strategy,
            toolRegistry = ToolRegistry { tools(koogTools) },
        ) {
            install(EventHandler) {
                onToolCallStarting { ctx ->
                    ctx.toolCallId?.let { pendingIds.getOrPut(ctx.toolName) { ConcurrentLinkedQueue() }.add(it) }
                }
                onLLMStreamingStarting { streamed = true }
                onLLMStreamingFrameReceived { ctx ->
                    when (val frame = ctx.streamFrame) {
                        is StreamFrame.TextDelta -> text(frame.text)
                        is StreamFrame.ReasoningDelta -> frame.text?.let { send(ChatEvent.ReasoningDelta("koog-reasoning", it)) }
                        else -> Unit
                    }
                }
                onLLMCallCompleted { ctx ->
                    val response = ctx.response ?: return@onLLMCallCompleted
                    if (!streamed) response.textContent().takeIf { it.isNotBlank() }?.let { text(it) }
                    endText()
                    val usage = response.metaInfo
                    if (usage.inputTokensCount != null || usage.outputTokensCount != null) {
                        send(ChatEvent.Usage(usage.inputTokensCount ?: 0, usage.outputTokensCount ?: 0))
                    }
                }
            }
        }
        try {
            agent.run(input)
            endText()
            send(ChatEvent.Finish)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            send(ChatEvent.Error(e.message ?: e::class.simpleName.orEmpty()))
        } finally {
            agent.close()
        }
    }

    private fun Message.text() = parts.filterIsInstance<TextPart>().joinToString("") { it.text }
}
