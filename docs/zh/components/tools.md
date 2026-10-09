# 工具与工作面板

工具调用、子 Agent，以及执行过程中的截图、终端与文件视图。

## 工具调用 { #tool-calls }

展示工具输入、输出、错误、进度和来源。

![工具调用](../assets/components/tool-calls.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `ToolCall(part, onApproval)` |
| AI Elements | `Tool` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [ToolCall](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-tool-call.html) |

用相同的 part id 更新输入、进度与输出。onDecision 可携带理由、编辑后的参数及记住决定；不提供回调时只读展示。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.core.chat.ChatController
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.ui.chat.ToolCall
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-tool-calls"
```

## 子 Agent { #sub-agents }

展示委派任务及其嵌套执行过程。

![子 Agent](../assets/components/sub-agents.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `ToolPartView(part)` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [ToolPartView](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-tool-part-view.html) |

在上游设置 ToolKind.Delegation 和 subagent。ToolPartView 会选择委派视图并应用自定义 renderer；嵌套审批共用回调。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.core.chat.ChatController
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.ui.chat.ToolPartView
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-sub-agents"
```

## Agent 工作面板 { #agents-computer }

按步骤查看截图、终端、差异与文件，支持时间线和回放。

![Agent 工作面板](../assets/components/agent-computer.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `AgentComputerPanel(state, message), AgentComputerScaffold(state, messages, layout)` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [AgentComputerPanel](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-agent-computer-panel.html) |

将面板状态与页面一起保存。Auto 按容器宽度选择侧栏或底部抽屉；已有自适应布局时可使用 Hosted。

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

## 回复中的工作预览 { #agents-computer-in-a-reply }

在涉及电脑操作的回复下方自动展示工作预览。

![回复中的工作预览](../assets/components/agent-computer-reply.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Conversation / Chat (automatic)` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [Chat](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-chat.html) |

Chat 已包含预览卡和工作面板。工具分类、位置及文件内容应在上游设置；普通工具名称本身不构成 UI 语义。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.core.chat.ChatController
import dev.ai.elements.ui.chat.Chat
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-agent-computer-reply"
```

## 执行步骤视图 { #step-views }

按工具分类展示浏览器、终端或差异内容。

![执行步骤视图](../assets/components/step-views.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `StepView(step)` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [StepView](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-step-view.html) |

截图通过 AgentStep.files 传入。工具分类决定展示方式；请为视图提供有界布局区域。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.ui.chat.AgentStep
import dev.ai.elements.ui.chat.StepView
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-step-views"
```

## Agent 信息卡 { #agent }

展示 Agent 的模型、指令、工具与输出结构。

![Agent 信息卡](../assets/components/agent.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Agent(name, model, instructions, tools, outputSchema)` |
| AI Elements | `Agent` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [Agent](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-agent.html) |

这是说明卡片，不是 Agent 运行时。数据应来自 backend 配置或远端 Agent Card。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.chat.Agent
import dev.ai.elements.ui.chat.AgentToolSpec
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-agent"
```

[Read this page in English](/ai-elements-kotlin/components/tools/)
