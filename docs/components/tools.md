# Tools and the agent's computer

Tool calls, delegated agents, and the agent's computer that shows each step as the agent saw it.

## Tool calls

A tool call with its input, output or error, progress, and what provides it.

![Tool calls](../assets/components/tool-calls.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `ToolCall(part, onApproval)` |
| AI Elements | `Tool` |
| Artifact | `ai-elements-ui` |
| Reference | [ToolCall](../api/ai-elements-ui/dev.ai.elements.ui.chat/-tool-call.html) |

Update the same part as input/progress/output arrives. onDecision carries reason, editedInput and remember; a card without callbacks is read-only.

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.core.chat.ChatController
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.ui.chat.ToolCall
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-tool-calls"
```

## Sub-agents

A delegated agent's run, nested in the call that started it.

![Sub-agents](../assets/components/sub-agents.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `ToolPartView(part)` |
| Artifact | `ai-elements-ui` |
| Reference | [ToolPartView](../api/ai-elements-ui/dev.ai.elements.ui.chat/-tool-part-view.html) |

Set kind=ToolKind.Delegation and subagent upstream. ToolPartView selects delegation rendering and applies custom renderers; nested approvals share the callback.

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.core.chat.ChatController
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.ui.chat.ToolPartView
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-sub-agents"
```

## Agent's computer

Each step as the agent saw it (screenshot, terminal, diff, file), with a timeline, autoplay, back to live and replay.

![Agent's computer](../assets/components/agent-computer.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `AgentComputerPanel(state, message), AgentComputerScaffold(state, messages, layout)` |
| Artifact | `ai-elements-ui` |
| Reference | [AgentComputerPanel](../api/ai-elements-ui/dev.ai.elements.ui.chat/-agent-computer-panel.html) |

Keep computer state with the screen. Auto chooses a side pane or bottom sheet from container width; use Hosted with your own adaptive layout.

```kotlin
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.ai.elements.core.model.Message
import dev.ai.elements.ui.chat.AgentComputerScaffold
import dev.ai.elements.ui.chat.rememberAgentComputerState
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-agent-computer"
```

## Agent's computer · in a reply

The live preview card under a reply that works on a computer; it opens the panel.

![Agent's computer · in a reply](../assets/components/agent-computer-reply.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Conversation / Chat (automatic)` |
| Artifact | `ai-elements-ui` |
| Reference | [Chat](../api/ai-elements-ui/dev.ai.elements.ui.chat/-chat.html) |

Chat includes the panel and preview cards. Tool category/location and file parts must be set upstream; ordinary function names alone are not UI semantics.

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.core.chat.ChatController
import dev.ai.elements.ui.chat.Chat
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-agent-computer-reply"
```

## Step views

A step by its category: a browser frame with the screenshot, a terminal, a diff.

![Step views](../assets/components/step-views.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `StepView(step)` |
| Artifact | `ai-elements-ui` |
| Reference | [StepView](../api/ai-elements-ui/dev.ai.elements.ui.chat/-step-view.html) |

Pass screenshots as the AgentStep files. Tool category selects terminal, diff or browser presentation; give the view a bounded area.

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.ui.chat.AgentStep
import dev.ai.elements.ui.chat.StepView
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-step-views"
```

## Agent

An agent's card: model, instructions, tools and output schema.

![Agent](../assets/components/agent.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Agent(name, model, instructions, tools, outputSchema)` |
| AI Elements | `Agent` |
| Artifact | `ai-elements-ui` |
| Reference | [Agent](../api/ai-elements-ui/dev.ai.elements.ui.chat/-agent.html) |

This is a descriptive card, not an agent runtime. Populate capabilities from your backend or agent card.

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.chat.Agent
import dev.ai.elements.ui.chat.AgentToolSpec
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-agent"
```
