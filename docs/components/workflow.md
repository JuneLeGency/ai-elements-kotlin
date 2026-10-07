# Workflow

An agent run as a graph.

## Canvas / Node / Edge

An agent run as a graph: nodes, edges, labels, pan and zoom.

![Canvas / Node / Edge](../assets/components/workflow-canvas.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `WorkflowCanvas(nodes, edges), agentRunGraph(message)` |
| AI Elements | `Canvas, Node, Edge, Controls, Panel, Toolbar` |
| Artifact | `ai-elements-ui` |
| Reference | [WorkflowCanvas](../api/ai-elements-ui/dev.ai.elements.ui.workflow/-workflow-canvas.html) |

Use stable node ids and edges referring to them; positions are dp. Give the canvas a bounded size. Use nodeContent/nodeToolbar/panel slots to customize.

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
