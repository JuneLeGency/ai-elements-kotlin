package dev.ai.elements.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Subject
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ai.elements.core.model.DataPart
import dev.ai.elements.core.model.FilePart
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.ReasoningPart
import dev.ai.elements.core.model.SourcePart
import dev.ai.elements.core.model.TextPart
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.core.model.ToolState
import dev.ai.elements.ui.theme.AiSpacing
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.math.roundToInt

enum class NodeTone { PRIMARY, SECONDARY, TERTIARY, ERROR, NEUTRAL }

/** A node on a [WorkflowCanvas] (AI Elements `<Node>`); [position] is its top-left in canvas dp. */
data class CanvasNode(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val position: DpOffset = DpOffset.Zero,
    val icon: ImageVector? = null,
    val tone: NodeTone = NodeTone.PRIMARY,
    val status: StepStatus? = null,
)

/** A connection between two nodes (AI Elements `<Edge>`); animated edges show flowing dashes. */
data class CanvasEdge(val from: String, val to: String, val label: String? = null, val animated: Boolean = false)

/**
 * A pannable, pinch-zoomable node graph (AI Elements `<Canvas>`): dot-grid
 * background, curved edges with arrowheads and optional labels, and zoom /
 * fit controls. Edges run from a node's bottom to the next node's top.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WorkflowCanvas(
    nodes: List<CanvasNode>,
    edges: List<CanvasEdge>,
    modifier: Modifier = Modifier,
    nodeSize: DpSize = DpSize(200.dp, 64.dp),
    showControls: Boolean = true,
    onNodeClick: ((CanvasNode) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var fitted by remember(nodes.map { it.id }) { mutableStateOf(false) }
    val measurer = rememberTextMeasurer()
    val phase by rememberInfiniteTransition(label = "edges").animateFloat(
        0f, 24f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "dash",
    )
    val nodeW = with(density) { nodeSize.width.toPx() }
    val nodeH = with(density) { nodeSize.height.toPx() }
    val byId = nodes.associateBy { it.id }
    fun positionPx(node: CanvasNode) = with(density) { Offset(node.position.x.toPx(), node.position.y.toPx()) }

    BoxWithConstraints(
        modifier
            .clipToBounds()
            .background(scheme.surfaceContainerLowest)
            .testTag("workflow-canvas"),
    ) {
        val viewport = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
        fun fit() {
            if (nodes.isEmpty()) return
            val left = nodes.minOf { positionPx(it).x }
            val top = nodes.minOf { positionPx(it).y }
            val right = nodes.maxOf { positionPx(it).x } + nodeW
            val bottom = nodes.maxOf { positionPx(it).y } + nodeH
            val pad = with(density) { 32.dp.toPx() }
            scale = minOf((viewport.width - 2 * pad) / (right - left), (viewport.height - 2 * pad) / (bottom - top), 1.5f).coerceIn(0.3f, 3f)
            offset = Offset(
                (viewport.width - (right - left) * scale) / 2 - left * scale,
                (viewport.height - (bottom - top) * scale) / 2 - top * scale,
            )
        }
        if (!fitted && viewport.width > 0) {
            fit()
            fitted = true
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(0.3f, 3f)
                        offset = (offset - centroid) * (newScale / scale) + centroid + pan
                        scale = newScale
                    }
                },
        ) {
            // Dot grid in screen space, moving with the canvas.
            val step = 24.dp.toPx() * scale
            if (step > 6f) {
                var x = offset.x % step
                while (x < size.width) {
                    var y = offset.y % step
                    while (y < size.height) {
                        drawCircle(scheme.outlineVariant, radius = 1.2f, center = Offset(x, y))
                        y += step
                    }
                    x += step
                }
            }
            edges.forEach { edge ->
                val from = byId[edge.from] ?: return@forEach
                val to = byId[edge.to] ?: return@forEach
                val start = offset + (positionPx(from) + Offset(nodeW / 2, nodeH)) * scale
                val end = offset + (positionPx(to) + Offset(nodeW / 2, 0f)) * scale
                val bend = (end.y - start.y).coerceAtLeast(40f) / 2
                val path = Path().apply {
                    moveTo(start.x, start.y)
                    cubicTo(start.x, start.y + bend, end.x, end.y - bend, end.x, end.y)
                }
                val color = if (edge.animated) scheme.primary else scheme.outline
                drawPath(
                    path,
                    color,
                    style = Stroke(
                        width = 2.dp.toPx() * scale.coerceAtMost(1.5f),
                        pathEffect = if (edge.animated) PathEffect.dashPathEffect(floatArrayOf(12f, 12f), -phase) else null,
                    ),
                )
                val arrow = 6.dp.toPx() * scale
                drawPath(
                    Path().apply {
                        moveTo(end.x, end.y)
                        lineTo(end.x - arrow, end.y - arrow * 1.4f)
                        lineTo(end.x + arrow, end.y - arrow * 1.4f)
                        close()
                    },
                    color,
                )
                edge.label?.let { label ->
                    val layout = measurer.measure(label, TextStyle(fontSize = (11 * scale).coerceIn(8f, 16f).sp, color = scheme.onSurfaceVariant))
                    val mid = Offset((start.x + end.x) / 2, (start.y + end.y) / 2)
                    translate(mid.x - layout.size.width / 2f, mid.y - layout.size.height / 2f) {
                        drawRoundRect(
                            scheme.surfaceContainerHigh,
                            topLeft = Offset(-6f, -2f),
                            size = Size(layout.size.width + 12f, layout.size.height + 4f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f),
                        )
                        drawText(layout)
                    }
                }
            }
        }

        nodes.forEach { node ->
            val p = offset + positionPx(node) * scale
            NodeCard(
                node = node,
                onClick = onNodeClick?.let { { it(node) } },
                modifier = Modifier
                    .offset { IntOffset(p.x.roundToInt(), p.y.roundToInt()) }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .requiredSize(nodeSize),
            )
        }

        if (showControls) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) {
                val zoomBy = { factor: Float ->
                    val center = Offset(viewport.width / 2, viewport.height / 2)
                    val newScale = (scale * factor).coerceIn(0.3f, 3f)
                    offset = (offset - center) * (newScale / scale) + center
                    scale = newScale
                }
                FilledTonalIconButton(onClick = { zoomBy(1.25f) }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Outlined.Add, "Zoom in") }
                FilledTonalIconButton(onClick = { zoomBy(0.8f) }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Outlined.Remove, "Zoom out") }
                FilledTonalIconButton(onClick = { fit() }, shapes = IconButtonDefaults.shapes(), modifier = Modifier.testTag("canvas-fit")) {
                    Icon(Icons.Outlined.FitScreen, "Fit to screen")
                }
            }
        }
    }
}

@Composable
private fun NodeCard(node: CanvasNode, onClick: (() -> Unit)?, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (node.tone) {
        NodeTone.PRIMARY -> scheme.primaryContainer to scheme.onPrimaryContainer
        NodeTone.SECONDARY -> scheme.secondaryContainer to scheme.onSecondaryContainer
        NodeTone.TERTIARY -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        NodeTone.ERROR -> scheme.errorContainer to scheme.onErrorContainer
        NodeTone.NEUTRAL -> scheme.surfaceContainerHigh to scheme.onSurface
    }
    val body: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AiSpacing.s),
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            node.icon?.let {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(content.copy(alpha = 0.12f)),
                ) { Icon(it, null, Modifier.size(16.dp), content) }
            }
            Column(Modifier.weight(1f)) {
                Text(node.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                node.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = content.copy(alpha = 0.8f))
                }
            }
        }
    }
    val border = Modifier.border(1.dp, if (node.status == StepStatus.ACTIVE) scheme.primary else Color.Transparent, MaterialTheme.shapes.large)
    if (onClick != null) {
        Surface(onClick = onClick, color = container, contentColor = content, shape = MaterialTheme.shapes.large, modifier = modifier.then(border)) {
            Box(contentAlignment = Alignment.CenterStart) { body() }
        }
    } else {
        Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.large, modifier = modifier.then(border)) {
            Box(contentAlignment = Alignment.CenterStart) { body() }
        }
    }
}

/**
 * Lays an agent run out as a graph: prompt → reasoning / plan → tool calls
 * (parallel calls side by side) → answer → sources. Consecutive tool calls
 * form one row; every node in a row connects to every node in the next.
 */
fun agentRunGraph(message: Message, prompt: String?, nodeSize: DpSize = DpSize(200.dp, 64.dp)): Pair<List<CanvasNode>, List<CanvasEdge>> {
    val rows = mutableListOf<List<CanvasNode>>()
    prompt?.let { rows += listOf(CanvasNode("prompt", "Prompt", it.lineSequence().first(), icon = Icons.Outlined.Person, tone = NodeTone.NEUTRAL)) }
    var toolRow = mutableListOf<CanvasNode>()
    fun flushTools() {
        if (toolRow.isNotEmpty()) rows += toolRow.toList()
        toolRow = mutableListOf()
    }
    message.parts.forEach { part ->
        if (part !is ToolPart) flushTools()
        when (part) {
            is ReasoningPart -> rows += listOf(
                CanvasNode(part.id, "Thinking", part.durationMs?.let { "${(it + 500) / 1000}s" } ?: "…", icon = Icons.Outlined.Psychology,
                    tone = NodeTone.SECONDARY, status = if (part.isStreaming) StepStatus.ACTIVE else StepStatus.COMPLETE),
            )
            is ToolPart -> toolRow += CanvasNode(
                part.id, part.name, part.output?.take(40) ?: part.errorText ?: part.state.name.lowercase().replace('_', ' '),
                icon = Icons.Outlined.Build,
                tone = if (part.state == ToolState.OUTPUT_ERROR || part.state == ToolState.OUTPUT_DENIED) NodeTone.ERROR else NodeTone.TERTIARY,
                status = if (part.isStreaming) StepStatus.ACTIVE else StepStatus.COMPLETE,
            )
            is TextPart -> if (part.text.isNotBlank()) rows += listOf(
                CanvasNode(part.id, "Answer", part.text.lineSequence().firstOrNull { it.isNotBlank() }?.trim('#', ' ')?.take(48),
                    icon = Icons.Outlined.Subject, status = if (part.isStreaming) StepStatus.ACTIVE else StepStatus.COMPLETE),
            )
            is DataPart -> rows += listOf(
                CanvasNode(
                    part.id,
                    part.name.replaceFirstChar { it.uppercase() },
                    ((part.data as? JsonObject)?.get("title") as? JsonPrimitive)?.contentOrNull ?: "data-${part.name}",
                    icon = Icons.Outlined.Checklist,
                    tone = NodeTone.NEUTRAL,
                ),
            )
            is FilePart -> rows += listOf(CanvasNode(part.id, "File", part.mediaType, icon = Icons.Outlined.Image, tone = NodeTone.NEUTRAL))
            is SourcePart -> Unit
        }
    }
    flushTools()
    val sources = message.parts.filterIsInstance<SourcePart>()
    if (sources.isNotEmpty()) {
        rows += listOf(CanvasNode("sources", "${sources.size} sources", sources.joinToString { host(it.url) }, icon = Icons.Outlined.Link, tone = NodeTone.NEUTRAL))
    }
    if (rows.isEmpty()) rows += listOf(CanvasNode("empty", "No steps yet", icon = Icons.Outlined.AutoAwesome))

    val gapX = 24.dp
    val rowStep = nodeSize.height + 56.dp
    val widest = rows.maxOf { it.size }
    val totalWidth = nodeSize.width * widest + gapX * (widest - 1)
    val placed = rows.mapIndexed { r, row ->
        val rowWidth = nodeSize.width * row.size + gapX * (row.size - 1)
        val startX = (totalWidth - rowWidth) / 2
        row.mapIndexed { i, node -> node.copy(position = DpOffset(startX + (nodeSize.width + gapX) * i, rowStep * r)) }
    }
    val streaming = message.isStreaming
    val edges = placed.zipWithNext().flatMapIndexed { r, (a, b) ->
        a.flatMap { from ->
            b.map { to -> CanvasEdge(from.id, to.id, animated = streaming && r == placed.lastIndex - 1) }
        }
    }
    return placed.flatten() to edges
}
