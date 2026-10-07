package dev.ai.elements.ui.chat

import dev.ai.elements.ui.icons.AiIcons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import dev.ai.elements.core.chat.ToolDecision
import dev.ai.elements.ui.theme.AiType
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ai.elements.core.model.DataPart
import dev.ai.elements.core.model.FilePart
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Part
import dev.ai.elements.core.model.ReasoningPart
import dev.ai.elements.core.model.SourcePart
import dev.ai.elements.core.model.TextPart
import dev.ai.elements.core.model.ToolKind
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.core.model.ToolState
import dev.ai.elements.ui.R
import dev.ai.elements.ui.markdown.MarkdownContent
import dev.ai.elements.ui.theme.AiSize
import dev.ai.elements.ui.theme.AiSpacing

/**
 * A delegated agent run (the sub-agent pattern): who was asked, what they were
 * asked to do, and their live work — reasoning, tool calls (approvable in
 * place) and answer — nested under the delegating tool call.
 *
 * Protocol-independent: it renders any [ToolPart] whose [ToolPart.kind] is a
 * [ToolKind.Delegation] or that carries a nested run ([ToolPart.subagent]) — the
 * backends map their protocol onto those (on-device sub-agents, AG-UI
 * `SUBAGENT_*`, AI SDK tools whose output is a `UIMessage`…). While the run is live the header shows what
 * the agent is doing; it opens by itself when a nested tool needs approval.
 *
 * @param onToolApproval answers approval requests of the nested tools.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Subagent(
    part: ToolPart,
    modifier: Modifier = Modifier,
    onToolApproval: ((toolCallId: String, approved: Boolean) -> Unit)? = null,
) {
    val run = part.subagent
    val delegation = part.kind as? ToolKind.Delegation
    val agentName = delegation?.agent ?: part.title ?: stringResource(R.string.ai_subagent)
    val task = delegation?.task
    val needsApproval = run?.parts.orEmpty().any { it is ToolPart && it.hasPendingApproval() }
    var open by rememberSaveable(part.id) { mutableStateOf(false) }
    LaunchedEffect(needsApproval) { if (needsApproval) open = true }
    val scheme = MaterialTheme.colorScheme

    Surface(
        onClick = { open = !open },
        color = scheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().testTag("subagent-$agentName"),
    ) {
        // One line like a tool call — agent, what it is doing, status — opening to its run.
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(min = 24.dp)) {
                Icon(AiIcons.SmartToy, null, Modifier.size(16.dp), scheme.onSurfaceVariant)
                Text(agentName, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
                Text(
                    text = activity(part, run, task),
                    style = AiType.small,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).testTag("subagent-activity"),
                )
                StatusPill(part.state)
                Icon(
                    AiIcons.ExpandMore,
                    contentDescription = stringResource(if (open) R.string.ai_collapse else R.string.ai_expand),
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp).rotate(if (open) 180f else 0f),
                )
            }
            AnimatedVisibility(
                visible = open,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
            ) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(AiSpacing.m)) {
                    task?.let { TaskQuote(it) }
                    run?.let { NestedRun(it, onToolApproval) }
                    if (run == null) part.output?.let { MarkdownContent(it) }
                    part.errorText?.let { Text(it, color = scheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

/** Whether a tool call is a delegation to a sub-agent (render it with [Subagent]). */
val ToolPart.isDelegation: Boolean get() = subagent != null || kind is ToolKind.Delegation

/** A tool call rendered by the element that fits it: [Subagent] for delegations, [ToolCall] otherwise. */
@Composable
fun ToolPartView(
    part: ToolPart,
    modifier: Modifier = Modifier,
    onToolApproval: ((toolCallId: String, approved: Boolean) -> Unit)? = null,
    onToolDecision: ((toolCallId: String, decision: ToolDecision) -> Unit)? = LocalToolDecision.current,
) {
    // Legacy yes/no hosts remain usable. Rich hosts need not also supply a Boolean callback.
    val decide = onToolDecision ?: onToolApproval?.let { cb -> { id: String, decision: ToolDecision -> cb(id, decision.approved) } }
    val approve = onToolApproval ?: decide?.let { cb -> { id: String, approved: Boolean -> cb(id, ToolDecision(approved)) } }
    CompositionLocalProvider(LocalToolDecision provides onToolDecision) {
        val custom = LocalAiElementsRenderers.current.toolRenderer(part)
        if (custom != null) custom.Render(part, decide)
        else if (part.isDelegation) Subagent(part, modifier, approve)
        else ToolCall(
            part, modifier,
            onApproval = approve?.let { cb -> { approved -> cb(part.id, approved) } },
            onDecision = onToolDecision?.let { cb -> { decision -> cb(part.id, decision) } },
        )
    }
}

@Composable
private fun TaskQuote(task: String) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.secondary))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(stringResource(R.string.ai_subagent_task), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(task, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The sub-agent's parts on a rail, so nesting reads at a glance. */
@Composable
private fun NestedRun(run: Message, onToolApproval: ((String, Boolean) -> Unit)?) {
    val rail = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.height(IntrinsicSize.Min).semantics { contentDescription = "nested run" }) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(rail))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AiSpacing.s)) {
            val sources = run.parts.filterIsInstance<SourcePart>()
            run.parts.forEach { part -> NestedPart(part, sources, onToolApproval) }
            if (sources.isNotEmpty()) Sources(sources)
        }
    }
}

@Composable
private fun NestedPart(part: Part, sources: List<SourcePart>, onToolApproval: ((String, Boolean) -> Unit)?) {
    when (part) {
        is ReasoningPart -> Reasoning(part)
        is ToolPart -> ToolPartView(part, onToolApproval = onToolApproval)
        is TextPart -> if (part.text.isNotBlank()) MarkdownContent(part.text, citations = sources, streaming = part.isStreaming)
        is DataPart -> DataPartView(part)
        is FilePart -> FileAttachment(part)
        is SourcePart -> Unit
    }
}

/** One line about what the sub-agent is doing (live) or produced (done). */
@Composable
private fun activity(part: ToolPart, run: Message?, task: String?): String {
    val last = run?.parts?.lastOrNull()
    val running = part.isStreaming
    return when {
        running && last is ToolPart && last.isStreaming -> stringResource(R.string.ai_subagent_calling, last.displayName)
        running && last is ReasoningPart && last.isStreaming -> stringResource(R.string.ai_subagent_thinking)
        running && last is TextPart -> last.text.lastLine().ifEmpty { stringResource(R.string.ai_subagent_writing) }
        running && run == null -> task ?: stringResource(R.string.ai_subagent_waiting)
        running -> task ?: stringResource(R.string.ai_subagent_writing)
        else -> (run?.text?.takeIf { it.isNotBlank() } ?: part.output ?: part.errorText ?: task).orEmpty().firstLine()
    }
}

private fun ToolPart.hasPendingApproval(): Boolean =
    state == ToolState.APPROVAL_REQUESTED || subagent?.parts.orEmpty().any { it is ToolPart && it.hasPendingApproval() }

private fun String.lastLine() = trimEnd().lineSequence().lastOrNull { it.isNotBlank() }?.trim().orEmpty().stripMarkdown()
private fun String.firstLine() = trim().lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty().stripMarkdown()
private fun String.stripMarkdown() = replace(Regex("[#*_`>]+"), "").trim()

