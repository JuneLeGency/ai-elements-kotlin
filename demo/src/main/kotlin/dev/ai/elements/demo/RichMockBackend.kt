package dev.ai.elements.demo

import dev.ai.elements.core.ChatBackend
import dev.ai.elements.core.ChatEvent
import dev.ai.elements.core.model.ReasoningStep
import dev.ai.elements.core.model.Source
import dev.ai.elements.core.model.StepState
import dev.ai.elements.core.model.ToolCall
import dev.ai.elements.core.model.ToolCallState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * A richer mock backend for the demo. Unlike [dev.ai.elements.core.MockChatBackend],
 * it streams:
 * 1. a chain-of-thought (reasoning) with several steps,
 * 2. a tool call (web search),
 * 3. a sources list,
 * 4. and the final text answer token by token.
 *
 * This exercises every core AI Elements component in one flow, exactly like the
 * web "Research Assistant" example.
 */
class RichMockBackend : ChatBackend {
    override fun generate(prompt: String): Channel<ChatEvent> {
        val channel = Channel<ChatEvent>(Channel.UNLIMITED)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            runCatching {
                channel.send(ChatEvent.Start)
                delay(200)

                // 1) Reasoning (chain of thought) — steps appear, then complete.
                val reasoningPartId = UUID.randomUUID().toString()
                val step1 = "s1"
                val step2 = "s2"
                val step3 = "s3"

                channel.send(ChatEvent.ReasoningStepEvent(reasoningPartId, ReasoningStep(step1, "Parsing the question", "Identifying the key entities in: \"$prompt\"", StepState.INCOMPLETE)))
                delay(600)
                channel.send(ChatEvent.ReasoningStepEvent(reasoningPartId, ReasoningStep(step1, "Parsing the question", "Identified the key entities.", StepState.COMPLETE)))
                delay(150)

                channel.send(ChatEvent.ReasoningStepEvent(reasoningPartId, ReasoningStep(step2, "Searching the web", "Querying for authoritative sources…", StepState.INCOMPLETE)))
                delay(700)
                channel.send(ChatEvent.ReasoningStepEvent(reasoningPartId, ReasoningStep(step2, "Searching the web", "Found 3 relevant sources.", StepState.COMPLETE)))
                delay(150)

                channel.send(ChatEvent.ReasoningStepEvent(reasoningPartId, ReasoningStep(step3, "Synthesising an answer", "Combining the findings into a concise response.", StepState.INCOMPLETE)))
                delay(500)
                channel.send(ChatEvent.ReasoningStepEvent(reasoningPartId, ReasoningStep(step3, "Synthesising an answer", "Draft ready.", StepState.COMPLETE)))
                delay(100)

                channel.send(ChatEvent.ReasoningDone(reasoningPartId, 1950))

                // 3) Sources
                val sourcesPartId = UUID.randomUUID().toString()
                channel.send(
                    ChatEvent.Sources(
                        sourcesPartId,
                        Source("src1", "AI Elements — Vercel", "https://elements.ai-sdk.dev", "elements.ai-sdk.dev"),
                    ),
                )
                channel.send(
                    ChatEvent.Sources(
                        sourcesPartId,
                        Source("src2", "Compose Multiplatform docs", "https://docs.oracle.com", "docs.oracle.com"),
                    ),
                )

                // 4) Final answer, streamed token by token.
                val textPartId = UUID.randomUUID().toString()
                val answer = buildString {
                    appendLine("Here's what I found about **$prompt**:")
                    appendLine()
                    appendLine("1. **Compose Multiplatform** lets you write one Kotlin/Compose UI " +
                        "that runs on Android, iOS and desktop.")
                    appendLine("2. **AI Elements** (this library) ports the popular web " +
                        "AI-native components — Conversation, Message, Reasoning, Sources — to Compose.")
                    appendLine("3. The architecture uses **Material3 Expressive** theming, " +
                        "**kotlinx.serialization** for the message model and **Coroutines/Flow** for streaming state.")
                    appendLine()
                    append("In short: you get an AI-native UI kit for Kotlin with Google's latest stack. " +
                        "Try the suggestions below to see more.")
                }
                answer.chunked(4).forEachIndexed { i, chunk ->
                    if (i == answer.chunked(4).lastIndex) {
                        channel.send(ChatEvent.TextDelta(textPartId, chunk, done = true))
                    } else {
                        channel.send(ChatEvent.TextDelta(textPartId, chunk))
                        delay(18)
                    }
                }

                channel.send(ChatEvent.Done)
            }.onFailure {
                channel.trySend(ChatEvent.Error(it.message ?: "unknown error"))
            }
            channel.close()
        }
        return channel
    }
}
