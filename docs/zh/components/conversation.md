# 对话组件

消息列表、单条消息、输入框及对话周边控件。

## 对话列表 { #conversation }

展示消息列表：流式生成时跟随底部，向上滚动后暂停跟随。

![对话列表](../../assets/components/conversation.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Conversation(state, onRegenerate, onToolApproval, …)` |
| AI Elements | `Conversation` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [Conversation](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-conversation.html) |

把 controller 放在 ViewModel 中。Conversation 只渲染消息列表；输入框由 PromptInput 提供，也可以直接用 Chat。审批和表单回答都需要接回 controller。

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ai.elements.core.chat.ChatController
import dev.ai.elements.ui.chat.Conversation
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-conversation"
```

## 单条消息 { #messages }

渲染用户或助手消息，以及复制、朗读、重新生成等操作。

![单条消息](../../assets/components/messages.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `MessageItem(message, onRegenerate)` |
| AI Elements | `Message` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [MessageItem](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-message-item.html) |

适合单条消息或非懒加载布局。较长的历史记录请使用 Conversation，它会按回复内容块进行懒加载。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.core.chat.ChatController
import dev.ai.elements.core.model.Message
import dev.ai.elements.ui.chat.MessageItem
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-messages"
```

## 消息输入框 { #prompt-input }

支持文本、附件、语音输入、发送、停止与排队。

![消息输入框](../../assets/components/prompt-input.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `PromptInput(value, onValueChange, onSubmit, onStop, busy)` |
| AI Elements | `PromptInput` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [PromptInput](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-prompt-input.html) |

文本和附件由调用方持有。只有 send 接受消息后才清空输入；controller 支持排队时可启用 allowQueue。

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ai.elements.core.chat.ChatController
import dev.ai.elements.ui.chat.PromptInput
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-prompt-input"
```

## 建议提问 { #suggestions }

让用户点击预设问题开始对话。

![建议提问](../../assets/components/suggestions.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Suggestions(suggestions, onSelect)` |
| AI Elements | `Suggestion` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [Suggestions](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-suggestions.html) |

选择建议只会触发 onSelect，由应用决定填入输入框还是立即发送。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.core.chat.ChatController
import dev.ai.elements.core.model.Suggestion
import dev.ai.elements.ui.chat.Suggestions
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-suggestions"
```

## 空对话欢迎页 { #empty-state }

用欢迎语和建议问题引导第一次对话。

![空对话欢迎页](../../assets/components/empty-state.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `ChatEmptyState(title, subtitle, suggestions, onSelect)` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [ChatEmptyState](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-chat-empty-state.html) |

在消息历史为空时显示。窗口高度较小时可以设置 showHero=false。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.core.chat.ChatController
import dev.ai.elements.core.model.Suggestion
import dev.ai.elements.ui.chat.ChatEmptyState
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-empty-state"
```

## 回复版本切换 { #branch }

在重新生成的多个回复版本之间切换。

![回复版本切换](../../assets/components/branch.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `BranchSelector(index, count, onSelect)` |
| AI Elements | `Branch` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [BranchSelector](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-branch-selector.html) |

index 从 0 开始。调用方需要更新当前消息；这个控件本身不会修改历史。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.core.chat.ChatController
import dev.ai.elements.core.model.Message
import dev.ai.elements.ui.chat.BranchSelector
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-branch"
```

## 对话检查点 { #checkpoint }

确认后恢复到之前的对话位置。

![对话检查点](../../assets/components/checkpoint.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Checkpoint(onRestore)` |
| AI Elements | `Checkpoint` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [Checkpoint](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-checkpoint.html) |

组件会先要求确认，再调用 onRestore。恢复 controller 的消息历史不会撤销工具已经修改的文件。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.core.chat.ChatController
import dev.ai.elements.ui.chat.Checkpoint
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-checkpoint"
```

## 待发送队列 { #queue }

展示 Agent 忙碌期间等待发送的消息。

![待发送队列](../../assets/components/queue.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Queue(items, paused, onRemove, onSendNow)` |
| AI Elements | `Queue` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [Queue](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-queue.html) |

队列顺序由 controller 管理。停止或出错会暂停自动发送；将“立即发送”接到 sendQueuedNow。

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ai.elements.core.chat.ChatController
import dev.ai.elements.ui.chat.Queue
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-queue"
```

## 在其他聊天应用打开 { #open-in-chat }

把当前问题带到用户选择的聊天应用。

![在其他聊天应用打开](../../assets/components/open-in-chat.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `OpenInChat(prompt)` |
| AI Elements | `OpenInChat` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [OpenInChat](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-open-in-chat.html) |

问题会放入外部应用的 URL。让用户主动选择目标，不要传入隐藏上下文或凭据。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.chat.OpenInChat
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-open-in-chat"
```

## 模型选择器 { #model-selector }

按提供方、能力与上下文窗口选择模型。

![模型选择器](../../assets/components/model-selector.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `ModelSelector(models, selectedId, onSelect)` |
| AI Elements | `ModelSelector` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [ModelSelector](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-model-selector.html) |

选择器只更新界面状态。backend 工厂应在下一轮读取模型选择，并保留当前 controller 和消息历史。

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.ai.elements.ui.chat.ModelOption
import dev.ai.elements.ui.chat.ModelSelector
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-model-selector"
```

## Token 使用量 { #context-token-usage }

展示已用 token 与模型上下文窗口的关系。

![Token 使用量](../../assets/components/context.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `ContextUsage(usage, contextWindow)` |
| AI Elements | `Context` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [ContextUsage](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-context-usage.html) |

传入实际统计和所选模型的窗口大小。该组件不负责分词或裁剪历史。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.core.model.Usage
import dev.ai.elements.ui.chat.ContextUsage
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-context"
```

## 加载指示器 { #loading-indicators }

等待首段输出时显示 Material 3 Expressive 加载动画。

![加载指示器](../../assets/components/loading.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `LoadingIndicator(), ContainedLoadingIndicator()` |
| AI Elements | `Loader` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [LoadingIndicator](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary) |

这些组件来自 Material 3 依赖。调用时需要声明 ExperimentalMaterial3ExpressiveApi 的 opt-in。

```kotlin
import androidx.compose.runtime.Composable
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-loading"
```

## 流动高亮文字 { #shimmer }

用流动高亮提示任务仍在进行。

![流动高亮文字](../../assets/components/shimmer.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `ShimmerText(text, active)` |
| AI Elements | `Shimmer` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [ShimmerText](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-shimmer-text.html) |

任务结束后将 active 设为 false。较长的内容更适合使用普通状态文字。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.chat.ShimmerText
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-shimmer"
```

[Read this page in English](/ai-elements-kotlin/components/conversation/)
