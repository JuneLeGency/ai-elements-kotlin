package dev.ai.elements.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.ai.elements.core.chat.ChatEvent
import dev.ai.elements.core.chat.finishStreaming
import dev.ai.elements.core.chat.reduce
import dev.ai.elements.core.model.FilePart
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.ToolCategory
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.core.model.ToolState
import dev.ai.elements.ui.R
import dev.ai.elements.ui.code.Terminal
import dev.ai.elements.ui.code.TerminalStatus
import dev.ai.elements.ui.icons.AiIcons
import dev.ai.elements.ui.markdown.CodeBlock
import dev.ai.elements.ui.theme.AiType
import dev.ai.elements.ui.theme.LocalCodeFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

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

/** Whether [message] did work worth watching: a step with a screenshot or one that says what it does ([ToolPart.category]). */
internal fun hasComputerSteps(message: Message): Boolean = agentSteps(message).any { it.files.isNotEmpty() || it.tool.category != null }

/**
 * Plays a reply's run back from its recorded events, at their original pace, when a recording
 * exists — e.g. AG-UI's event log (`AgUiEventLog.replayOf` in `ai-elements-core`). The flow's
 * events are folded into a fresh reply as they arrive, exactly as a live run is.
 */
fun interface RunReplay {
    /** The recorded run of [message], or null when it has none. Called on the main thread; the flow is collected later. */
    fun replay(message: Message): Flow<ChatEvent>?
}

/**
 * What the agent's computer view shows (Manus-style): which reply, which step, whether it follows
 * the live run, and a replay in progress. Create with [rememberAgentComputerState]; [Chat] and
 * [Conversation] make one when none is provided.
 */
@Stable
class AgentComputerState internal constructor(
    messageId: String?,
    step: Int,
    follow: Boolean,
    internal val replaySource: RunReplay?,
) {
    /** The reply being shown, or null when the view is closed. */
    var messageId by mutableStateOf(messageId)
        private set

    /** The selected step. */
    var step by mutableIntStateOf(step)

    /** Follow the newest step while the reply streams (Manus's "back to live"). */
    var follow by mutableStateOf(follow)

    /** The reply rebuilt by a running or finished replay, shown instead of the stored one. */
    var replayed by mutableStateOf<Message?>(null)
        internal set

    internal var replayRequest by mutableIntStateOf(0)

    /** A replay is playing. */
    var replaying by mutableStateOf(false)
        internal set

    val isOpen: Boolean get() = messageId != null

    /** Opens [messageId] at [step], or following its newest step when null. */
    fun open(messageId: String, step: Int? = null) {
        if (messageId != this.messageId) replayed = null
        this.messageId = messageId
        if (step == null) follow = true else { this.step = step; follow = false }
    }

    fun close() {
        messageId = null
        replayed = null
    }

    /** Replays the open reply from its recording (see [RunReplay]). */
    fun replay() {
        replayed = null
        follow = true
        replayRequest++
    }

    fun exitReplay() {
        replayed = null
        replayRequest = 0
    }

    internal fun canReplay(message: Message): Boolean = replaySource?.replay(message) != null

    internal companion object {
        fun saver(replay: RunReplay?) = Saver<AgentComputerState, List<Any?>>(
            save = { listOf(it.messageId, it.step, it.follow) },
            restore = { AgentComputerState(it[0] as String?, it[1] as Int, it[2] as Boolean, replay) },
        )
    }
}

@Composable
fun rememberAgentComputerState(replay: RunReplay? = null): AgentComputerState =
    rememberSaveable(replay, saver = AgentComputerState.saver(replay)) { AgentComputerState(null, 0, true, replay) }

/** The agent's computer view of the enclosing [AgentComputerScaffold]. */
val LocalAgentComputer = staticCompositionLocalOf<AgentComputerState?> { null }

/**
 * Lays out [content] (a conversation) with the agent's computer view of [state]: a side pane
 * next to it when there is room (at least 720dp, as Material 3's supporting pane on expanded
 * widths), a bottom sheet otherwise. The width is the scaffold's own, so it adapts inside a
 * list–detail layout too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentComputerScaffold(
    state: AgentComputerState,
    messages: List<Message>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val message = state.messageId?.let { id -> messages.firstOrNull { it.id == id } }
    CompositionLocalProvider(LocalAgentComputer provides state) {
        BoxWithConstraints(modifier) {
            val wide = maxWidth >= 720.dp
            val paneWidth = (maxWidth * 0.45f).coerceIn(340.dp, 560.dp)
            // The content keeps its place in the tree whatever the width, so it keeps its state.
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight()) { content() }
                AnimatedVisibility(visible = wide && message != null, enter = expandHorizontally(), exit = shrinkHorizontally()) {
                    Row {
                        VerticalDivider()
                        if (message != null) AgentComputerPanel(state, message, Modifier.width(paneWidth).fillMaxHeight())
                    }
                }
            }
            if (wide && message != null) BackHandler(onBack = state::close)
            if (!wide && message != null) {
                ModalBottomSheet(onDismissRequest = state::close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                    AgentComputerPanel(state, message, Modifier.fillMaxWidth().fillMaxHeight(0.9f))
                }
            }
        }
    }
}

/**
 * The agent's computer: the selected step as the agent saw it — its screenshot, a terminal for a
 * command, the diff of an edit, the file it read, the page it fetched ([ToolPart.category]) — with
 * a timeline to scrub, step or play through, "back to live" while the reply streams, and a replay
 * of the recorded run when there is one ([RunReplay]).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AgentComputerPanel(state: AgentComputerState, message: Message, modifier: Modifier = Modifier) {
    // A replay rebuilds the reply event by event, as the live run did.
    LaunchedEffect(state.replayRequest, message.id) {
        if (state.replayRequest == 0) return@LaunchedEffect
        val flow = state.replaySource?.replay(message) ?: return@LaunchedEffect
        var replayed = Message(message.id, Role.ASSISTANT)
        state.replayed = replayed
        state.replaying = true
        try {
            flow.collect { event ->
                replayed = replayed.reduce(event, System.currentTimeMillis())
                state.replayed = replayed
            }
            state.replayed = replayed.finishStreaming(System.currentTimeMillis())
        } finally {
            state.replaying = false
        }
    }
    val replaying = state.replayed
    val shown = replaying ?: message
    val live = if (replaying != null) state.replaying else shown.isStreaming
    val steps = remember(shown) { agentSteps(shown) }
    val last = (steps.size - 1).coerceAtLeast(0)
    var playing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(steps.size, state.follow) { if (state.follow) state.step = last }
    LaunchedEffect(playing) {
        while (playing) {
            delay(1_500)
            if (state.step >= last) playing = false else state.step++
        }
    }
    val index = state.step.coerceIn(0, last)
    val step = steps.getOrNull(index)
    val pick = { i: Int -> state.step = i.coerceIn(0, last); state.follow = false; playing = false }
    val canReplay = remember(message.id, message.isStreaming) { !message.isStreaming && state.canReplay(message) }

    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.testTag("agent-computer")) {
        BoxWithConstraints {
        // Short panels (a phone in landscape) give the screen the room: the step chips go, the
        // timeline and previous / next still move between steps.
        val short = maxHeight < 560.dp
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                Icon(AiIcons.Computer, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(stringResource(R.string.ai_agent_computer), style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (live) LiveDot()
                        Text(
                            buildString {
                                if (replaying != null) append(stringResource(R.string.ai_replaying)).append(" · ")
                                else if (live) append(stringResource(R.string.ai_live)).append(" · ")
                                if (steps.isNotEmpty()) append(stringResource(R.string.ai_step_of, index + 1, steps.size))
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("run-step-counter"),
                        )
                    }
                }
                when {
                    replaying != null -> TextButton(onClick = state::exitReplay, modifier = Modifier.testTag("run-exit-replay")) { Text(stringResource(R.string.ai_exit_replay)) }
                    canReplay -> IconButton(onClick = state::replay, shapes = IconButtonDefaults.shapes(), modifier = Modifier.testTag("run-replay")) {
                        Icon(AiIcons.Replay, stringResource(R.string.ai_replay_run))
                    }
                }
                IconButton(onClick = state::close, shapes = IconButtonDefaults.shapes(), modifier = Modifier.testTag("run-playback-close")) {
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
                if (step == null) Text(stringResource(R.string.ai_no_steps), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else StepView(step, Modifier.fillMaxSize())
            }
            step?.let { current ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                    Icon(current.tool.icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(activity(current.tool), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    StatusPill(current.tool.state)
                }
            }
            if (steps.size > 1) Slider(
                value = index.toFloat(),
                onValueChange = { pick(it.toInt()) },
                valueRange = 0f..last.toFloat(),
                // Tick marks only while they stay readable; a long run scrubs continuously.
                steps = if (steps.size <= MAX_TICKS) (steps.size - 2).coerceAtLeast(0) else 0,
                modifier = Modifier.testTag("run-timeline"),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { pick(index - 1) }, enabled = index > 0, modifier = Modifier.testTag("run-previous")) {
                    Icon(AiIcons.SkipPrevious, stringResource(R.string.ai_previous_step))
                }
                FilledIconButton(
                    onClick = { if (index >= last) state.step = 0; playing = !playing; state.follow = false },
                    enabled = steps.size > 1,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.testTag("run-play"),
                ) { Icon(if (playing) AiIcons.Pause else AiIcons.PlayArrow, stringResource(if (playing) R.string.ai_pause else R.string.ai_play)) }
                IconButton(onClick = { pick(index + 1) }, enabled = index < last, modifier = Modifier.testTag("run-next")) {
                    Icon(AiIcons.SkipNext, stringResource(R.string.ai_next_step))
                }
                if (live && !state.follow) TextButton(onClick = { state.follow = true; playing = false }, modifier = Modifier.testTag("run-live")) {
                    LiveDot()
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ai_back_to_live))
                }
            }
            // Every step, to jump to one.
            val list = rememberLazyListState()
            LaunchedEffect(index, steps.size, short) { if (steps.isNotEmpty() && !short) list.animateScrollToItem(index) }
            if (!short) LazyRow(state = list, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp).height(72.dp).testTag("run-steps")) {
                itemsIndexed(steps) { i, s ->
                    StepChip(s, selected = i == index, modifier = Modifier.testTag("run-step-$i")) { pick(i) }
                }
            }
        }
        }
    }
}

/**
 * One step as the agent saw it, by what the call did: its screenshot (in a browser frame when it
 * acted on a web page), a terminal for a command, a diff for an edit, the file or matches it read,
 * the page text it fetched, else its output.
 */
@Composable
fun StepView(step: AgentStep, modifier: Modifier = Modifier) {
    val tool = step.tool
    val shot = step.files.lastOrNull()
    val raw = tool.errorText ?: tool.output
    val result = raw?.capped()
    when {
        shot != null -> Column(modifier) {
            tool.location?.takeIf(::isWebUrl)?.let { AddressBar(it) }
            // A desktop page is small on a phone: tap for the full-screen viewer (zoom, pan).
            var zoomed by remember(shot.id) { mutableStateOf(false) }
            FileImage(
                shot,
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable(onClickLabel = stringResource(R.string.ai_view_image)) { zoomed = true }
                    .testTag("run-shot"),
                contentScale = ContentScale.Fit,
                maxDecodePx = 2048,
            )
            if (zoomed) ImageViewer(shot, onDismiss = { zoomed = false })
        }
        tool.category == ToolCategory.EXECUTE -> Terminal(
            // A terminal keeps the end of its output (its scrollback), not the beginning.
            output = "$ ${tool.title ?: tool.input}\n${raw?.takeLast(MAX_TERMINAL_CHARS).orEmpty()}",
            status = when (tool.state) {
                ToolState.OUTPUT_AVAILABLE -> TerminalStatus.SUCCESS
                ToolState.OUTPUT_ERROR, ToolState.OUTPUT_DENIED -> TerminalStatus.ERROR
                else -> TerminalStatus.RUNNING
            },
            maxHeight = 4_000.dp,
            modifier = modifier,
        )
        tool.category == ToolCategory.FETCH -> Column(modifier) {
            tool.location?.let { AddressBar(it) }
            PlainOutput(result ?: tool.input.capped(), Modifier.weight(1f))
        }
        tool.category in FILE_CATEGORIES -> Column(modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
            tool.location?.let { path ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(tool.icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(path, style = MaterialTheme.typography.labelLarge, fontFamily = LocalCodeFontFamily.current, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val text = result ?: tool.input.capped().compactJson()
            if (isUnifiedDiff(text)) DiffText(text) else CodeBlock(text, language = tool.location?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() && result != null })
        }
        else -> PlainOutput(result ?: tool.input.capped().compactJson(), modifier)
    }
}

/**
 * The live preview of a reply's computer, under the reply while the agent works (and after, to
 * reopen it): the latest screenshot or what kind of step is running, what it is doing, and the
 * step count. Tapping opens the [AgentComputerPanel].
 */
@Composable
internal fun AgentComputerCard(message: Message, modifier: Modifier = Modifier) {
    val computer = LocalAgentComputer.current ?: return
    val steps = remember(message) { agentSteps(message) }
    val current = steps.lastOrNull() ?: return
    val shot = steps.lastOrNull { it.files.isNotEmpty() }?.files?.last()
    Surface(
        onClick = { computer.open(message.id) },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth().testTag("agent-computer-card"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(10.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(width = 96.dp, height = 64.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                if (shot != null) FileImage(shot, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(current.tool.icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.ai_agent_computer), style = MaterialTheme.typography.labelLarge)
                    if (message.isStreaming) {
                        LiveDot()
                        Text(stringResource(R.string.ai_live), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(activity(current.tool), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    pluralStringResource(R.plurals.ai_steps, steps.size, steps.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(AiIcons.OpenInFull, stringResource(R.string.ai_agent_computer), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** What a step is doing, for people: "Running command · ls -la", "Reading · src/App.kt". */
@Composable
private fun activity(tool: ToolPart): String {
    val verb = when (tool.category) {
        ToolCategory.READ -> R.string.ai_activity_read
        ToolCategory.EDIT -> R.string.ai_activity_edit
        ToolCategory.DELETE -> R.string.ai_activity_delete
        ToolCategory.MOVE -> R.string.ai_activity_move
        ToolCategory.SEARCH -> R.string.ai_activity_search
        ToolCategory.EXECUTE -> R.string.ai_activity_execute
        ToolCategory.THINK -> R.string.ai_activity_think
        ToolCategory.FETCH -> R.string.ai_activity_fetch
        ToolCategory.SWITCH_MODE -> R.string.ai_activity_switch_mode
        ToolCategory.OTHER, null -> null
    }
    val what = tool.location ?: tool.displayName
    return if (verb == null) what else stringResource(verb) + " · " + what
}

@Composable
private fun LiveDot() {
    Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
}

@Composable
private fun AddressBar(url: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Icon(AiIcons.Language, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.weight(1f)) {
                Text(
                    url,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).testTag("run-address"),
                )
            }
        }
    }
}

@Composable
private fun PlainOutput(text: String, modifier: Modifier = Modifier) {
    SelectionContainer(modifier) {
        Text(
            text.ifBlank { "…" },
            fontFamily = LocalCodeFontFamily.current,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        )
    }
}

/** A unified diff with added lines green and removed lines red (ACP diffs arrive as one). */
@Composable
private fun DiffText(diff: String) {
    val scheme = MaterialTheme.colorScheme
    val added = Color(0xFF2E7D32)
    val text = remember(diff, scheme) { diffAnnotated(diff, added, scheme.error, scheme.onSurfaceVariant) }
    SelectionContainer {
        Text(text, style = AiType.code, softWrap = false, modifier = Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()).testTag("run-diff"))
    }
}

internal fun diffAnnotated(diff: String, added: Color, removed: Color, meta: Color): AnnotatedString = buildAnnotatedString {
    diff.lines().forEachIndexed { i, line ->
        if (i > 0) append('\n')
        val style = when {
            line.startsWith("+++") || line.startsWith("---") || line.startsWith("@@") -> SpanStyle(color = meta)
            line.startsWith("+") -> SpanStyle(color = added, background = added.copy(alpha = 0.12f))
            line.startsWith("-") -> SpanStyle(color = removed, background = removed.copy(alpha = 0.12f))
            else -> null
        }
        if (style == null) append(line) else withStyle(style) { append(line) }
    }
}

internal fun isUnifiedDiff(text: String): Boolean = text.lineSequence().any { it.startsWith("@@ ") } && text.lineSequence().any { it.startsWith("+") || it.startsWith("-") }

private fun isWebUrl(location: String) = location.startsWith("https://") || location.startsWith("http://")

/** Longest text a step view shows: highlighting and layout stay fast on very large outputs. */
private const val MAX_VIEW_CHARS = 100_000

/** Longest terminal output a step view reads (its end); the terminal then keeps its scrollback. */
private const val MAX_TERMINAL_CHARS = 1_000_000

/** Most steps the timeline marks with ticks. */
private const val MAX_TICKS = 20

private fun String.capped() = if (length <= MAX_VIEW_CHARS) this else take(MAX_VIEW_CHARS) + "\n…"

private val FILE_CATEGORIES = setOf(ToolCategory.READ, ToolCategory.EDIT, ToolCategory.DELETE, ToolCategory.MOVE, ToolCategory.SEARCH)

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
            val shot = step.files.lastOrNull()
            if (shot != null) FileImage(shot, Modifier.size(56.dp, 60.dp).clip(MaterialTheme.shapes.small))
            else Icon(step.tool.icon, null, Modifier.size(20.dp), tint = scheme.onSurfaceVariant)
            Column(Modifier.weight(1f, fill = false)) {
                Text(step.tool.location?.substringAfterLast('/')?.ifEmpty { null } ?: step.tool.displayName, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val failed = step.tool.state == ToolState.OUTPUT_ERROR || step.tool.state == ToolState.OUTPUT_DENIED
                if (failed) Text("!", color = scheme.error, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * The screenshots of one step under its tool call: small, so a run of many steps stays readable;
 * tapping one opens the agent's computer there.
 */
@Composable
internal fun StepMedia(messageId: String, stepIndex: Int, files: List<FilePart>, modifier: Modifier = Modifier) {
    val computer = LocalAgentComputer.current
    // Lazy: a step can attach many screenshots.
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier.testTag("step-media")) {
        items(files, key = { it.id }) { file ->
            FileImage(
                file,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 120.dp, height = 80.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(Color.Black)
                    .then(if (computer != null) Modifier.clickable(onClickLabel = stringResource(R.string.ai_agent_computer)) { computer.open(messageId, stepIndex) } else Modifier),
            )
        }
    }
}
