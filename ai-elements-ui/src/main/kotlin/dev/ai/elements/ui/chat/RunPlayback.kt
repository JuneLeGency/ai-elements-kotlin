package dev.ai.elements.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.ai.elements.core.model.FilePart
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.core.model.ToolState
import dev.ai.elements.ui.R
import dev.ai.elements.ui.icons.AiIcons
import dev.ai.elements.ui.theme.LocalCodeFontFamily
import kotlinx.coroutines.delay

/** One step of an agent run: a tool call and the files (screenshots) it produced, in message order. */
@Immutable
data class AgentStep(val tool: ToolPart, val files: List<FilePart>)

/**
 * The steps of [message]: each tool call with the image files that follow it (a screenshot of what
 * the call did — AI SDK `file` parts, AG-UI media tool results, on-device tools' files), whatever
 * protocol produced them.
 */
fun agentSteps(message: Message): List<AgentStep> {
    val steps = mutableListOf<AgentStep>()
    message.parts.forEach { part ->
        when {
            part is ToolPart -> steps += AgentStep(part, emptyList())
            part is FilePart && part.isImage && steps.isNotEmpty() -> steps[steps.lastIndex] = steps.last().let { it.copy(files = it.files + part) }
        }
    }
    return steps
}

/** Opens the run playback of a message at a step ([Conversation] provides it). */
internal val LocalOpenRunPlayback = staticCompositionLocalOf<((messageId: String, step: Int) -> Unit)?> { null }

/**
 * An agent run, step by step, like watching the agent's computer: the selected step's screenshot
 * (or its output when it made none) large, what the step was, and a timeline to scrub, step or
 * play through. While [message] is still streaming it follows the newest step until the user picks
 * one. Works on any saved conversation, so past runs play back the same way.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AgentRunPlayback(message: Message, onDismiss: () -> Unit, initialStep: Int = 0) {
    val steps = remember(message) { agentSteps(message) }
    var index by rememberSaveable { mutableIntStateOf(initialStep) }
    var follow by rememberSaveable { mutableStateOf(message.isStreaming) }
    var playing by rememberSaveable { mutableStateOf(false) }
    val last = (steps.size - 1).coerceAtLeast(0)
    LaunchedEffect(steps.size, follow) { if (follow) index = last }
    LaunchedEffect(playing) {
        while (playing) {
            delay(1_500)
            if (index >= last) playing = false else index++
        }
    }
    val step = steps.getOrNull(index.coerceIn(0, last))
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize().testTag("run-playback")) {
            Column(Modifier.safeDrawingPadding().padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.ai_run_playback), style = MaterialTheme.typography.titleLarge)
                        if (steps.isNotEmpty()) Text(
                            stringResource(R.string.ai_step_of, index + 1, steps.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("run-step-counter"),
                        )
                    }
                    IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes(), modifier = Modifier.testTag("run-playback-close")) {
                        Icon(AiIcons.Close, stringResource(R.string.ai_close))
                    }
                }
                // The "screen": what the step showed.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .testTag("run-screen"),
                ) {
                    val shot = step?.files?.lastOrNull()
                    when {
                        step == null -> Text(stringResource(R.string.ai_no_steps), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        shot != null -> FileImage(shot, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, maxDecodePx = 2048)
                        else -> SelectionContainer {
                            Text(
                                (step.tool.errorText ?: step.tool.output ?: step.tool.input).ifBlank { "…" },
                                fontFamily = LocalCodeFontFamily.current,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                            )
                        }
                    }
                }
                step?.let { current ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                        Icon(current.tool.icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(current.tool.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        StatusPill(current.tool.state)
                    }
                    Text(
                        current.tool.input.compactJson(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = LocalCodeFontFamily.current,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Timeline.
                if (steps.size > 1) Slider(
                    value = index.toFloat(),
                    onValueChange = { index = it.toInt(); follow = false; playing = false },
                    valueRange = 0f..last.toFloat(),
                    steps = (steps.size - 2).coerceAtLeast(0),
                    modifier = Modifier.testTag("run-timeline"),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { index = (index - 1).coerceAtLeast(0); follow = false; playing = false }, enabled = index > 0, modifier = Modifier.testTag("run-previous")) {
                        Icon(AiIcons.SkipPrevious, stringResource(R.string.ai_previous_step))
                    }
                    FilledIconButton(
                        onClick = { if (index >= last) index = 0; playing = !playing; follow = false },
                        enabled = steps.size > 1,
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.testTag("run-play"),
                    ) { Icon(if (playing) AiIcons.Pause else AiIcons.PlayArrow, stringResource(if (playing) R.string.ai_pause else R.string.ai_play)) }
                    IconButton(onClick = { index = (index + 1).coerceAtMost(last); follow = false; playing = false }, enabled = index < last, modifier = Modifier.testTag("run-next")) {
                        Icon(AiIcons.SkipNext, stringResource(R.string.ai_next_step))
                    }
                }
                // Every step, to jump to one.
                val list = rememberLazyListState()
                LaunchedEffect(index) { if (steps.isNotEmpty()) list.animateScrollToItem(index) }
                LazyRow(state = list, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp).height(72.dp).testTag("run-steps")) {
                    itemsIndexed(steps) { i, s ->
                        StepChip(s, selected = i == index, modifier = Modifier.testTag("run-step-$i")) { index = i; follow = false; playing = false }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepChip(step: AgentStep, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) scheme.secondaryContainer else scheme.surfaceContainerHigh,
        modifier = modifier.widthIn(min = 96.dp, max = 160.dp).height(72.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            step.files.lastOrNull()?.let { FileImage(it, Modifier.size(56.dp, 60.dp).clip(MaterialTheme.shapes.small)) }
            Column(Modifier.weight(1f, fill = false)) {
                Text(step.tool.displayName, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val failed = step.tool.state == ToolState.OUTPUT_ERROR || step.tool.state == ToolState.OUTPUT_DENIED
                if (failed) Text("!", color = scheme.error, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * The screenshots of one step under its tool call: small, so a run of many steps stays readable;
 * tapping one opens the run playback there.
 */
@Composable
internal fun StepMedia(messageId: String, stepIndex: Int, files: List<FilePart>, modifier: Modifier = Modifier) {
    val open = LocalOpenRunPlayback.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier.horizontalScroll(rememberScrollState()).testTag("step-media")) {
        files.forEach { file ->
            FileImage(
                file,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 120.dp, height = 80.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(Color.Black)
                    .then(if (open != null) Modifier.clickable(onClickLabel = stringResource(R.string.ai_run_playback)) { open(messageId, stepIndex) } else Modifier),
            )
        }
    }
}
