# Agent 执行结构

计划、任务、步骤摘要与结构化数据。

## 任务计划 { #plan }

展示 Agent 的计划与当前步骤。

![任务计划](../../assets/components/plan.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Plan(title, description, steps)` |
| AI Elements | `Plan` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [Plan](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-plan.html) |

进度由调用方维护，状态为 COMPLETE、ACTIVE、PENDING。展示计划不等于安排或执行任务。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.chat.Plan
import dev.ai.elements.ui.chat.StepStatus
import dev.ai.elements.ui.chat.WorkflowStep
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-plan"
```

## 任务条目 { #task }

展示任务步骤及涉及的文件。

![任务条目](../../assets/components/task.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Task(title, steps)` |
| AI Elements | `Task` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [Task](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-task.html) |

items 描述任务进展和文件标记；该组件不会修改文件。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.chat.Task
import dev.ai.elements.ui.chat.WorkflowStep
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-task"
```

## 推理步骤 { #chain-of-thought }

展示步骤摘要及其来源。

![推理步骤](../../assets/components/chain-of-thought.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `ChainOfThought(steps)` |
| AI Elements | `ChainOfThought` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [ChainOfThought](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-chain-of-thought.html) |

只展示实际提供的步骤与来源。组件本身不会请求或提取模型未返回的推理过程。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.chat.ChainOfThought
import dev.ai.elements.ui.chat.StepStatus
import dev.ai.elements.ui.chat.WorkflowStep
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-chain-of-thought"
```

## 结构化数据片段 { #data-parts-data-plan-data-task }

把已知计划、任务或自定义数据渲染到对话中。

![结构化数据片段](../../assets/components/data-parts.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `DataPartView(part)` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [DataPartView](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-data-part-view.html) |

已知 plan/task 结构会使用对应组件。自定义名称可注册到 AiElementsRenderers.data；未知结构仍会以数据视图显示。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.core.model.DataPart
import dev.ai.elements.ui.chat.DataPartView
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-data-parts"
```

[Read this page in English](/ai-elements-kotlin/components/agent-structure/)
