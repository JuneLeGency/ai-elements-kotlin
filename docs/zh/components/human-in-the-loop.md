# 人工确认

工具审批、选择题与信息收集表单。

## 工具审批 { #confirmation-tool-approval }

允许用户批准、拒绝、说明理由或编辑参数。

![工具审批](../assets/components/confirmation.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `ToolCall(part, onApproval)` |
| AI Elements | `Confirmation` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [ToolCall](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-tool-call.html) |

遵循 approvalAnswers，只提供后端能接收的理由或参数编辑功能。审批应恢复等待中的工具，而不是发送一条新用户消息。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.core.chat.ChatController
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.core.model.ToolState
import dev.ai.elements.ui.chat.ToolCall
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-confirmation"
```

## 选择题 { #question }

让 Agent 向用户提出单选或多选问题。

![选择题](../assets/components/question.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Question(prompt, options, multiple, onSubmit)` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [Question](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-question.html) |

保存返回的选项 id 和文字，再传给 Agent。answered 表示已经作答，可防止重复提交。

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.ai.elements.ui.chat.Question
import dev.ai.elements.ui.chat.QuestionAnswer
import dev.ai.elements.ui.chat.QuestionOption
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-question"
```

## 信息收集表单 { #input-request-form }

根据 JSON Schema 展示 Agent 请求填写的表单或确认。

![信息收集表单](../assets/components/input-request.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `InputRequestCard(request, onRespond)` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [InputRequestCard](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-input-request-card.html) |

回答时必须使用原请求 id。表单验证支持的 schema 字段；URL 请求需要用户完成外部流程。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.core.chat.ChatController
import dev.ai.elements.core.chat.InputRequest
import dev.ai.elements.ui.chat.InputRequestCard
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-input-request"
```

[Read this page in English](/ai-elements-kotlin/components/human-in-the-loop/)
