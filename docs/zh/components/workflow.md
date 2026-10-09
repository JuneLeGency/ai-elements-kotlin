# 工作流

用图形展示执行步骤及其关系。

## 工作流画布 { #canvas-node-edge }

通过节点与连线展示 Agent 执行过程，支持平移与缩放。

![工作流画布](../../assets/components/workflow-canvas.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `WorkflowCanvas(nodes, edges), agentRunGraph(message)` |
| AI Elements | `Canvas, Node, Edge, Controls, Panel, Toolbar` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [WorkflowCanvas](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.workflow/-workflow-canvas.html) |

使用稳定的节点 id，连线应引用已有节点，位置单位为 dp。为画布设置有界大小；可通过 nodeContent、nodeToolbar、panel 自定义展示。

```kotlin
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ai.elements.ui.workflow.CanvasEdge
import dev.ai.elements.ui.workflow.CanvasNode
import dev.ai.elements.ui.workflow.WorkflowCanvas
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-workflow-canvas"
```

[Read this page in English](/ai-elements-kotlin/components/workflow/)
