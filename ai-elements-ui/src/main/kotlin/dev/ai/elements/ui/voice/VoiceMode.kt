package dev.ai.elements.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ai.elements.core.chat.ChatController
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.core.model.ToolState
import dev.ai.elements.ui.R
import dev.ai.elements.ui.icons.AiIcons
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.sin

/** Where a [VoiceMode] conversation is. */
enum class VoicePhase { LISTENING, THINKING, SPEAKING, MUTED, NEEDS_YOU }

/**
 * A hands-free voice conversation over any chat, like the assistant apps' voice mode: it listens
 * (platform speech recognition), sends what the user said to [controller], reads the reply aloud
 * sentence by sentence while it streams (platform text-to-speech), then listens again.
 *
 * Tap the persona to interrupt: it stops reading (or stops the reply being generated) and listens.
 * The microphone can be muted. When the agent needs the user — a tool approval or a question —
 * voice mode pauses and offers to go back to the chat ([onClose]), where the approval or form is.
 *
 * Works with every backend: it only uses [ChatController] and the platform speech engines, so no
 * protocol is involved. The host app must declare `RECORD_AUDIO`.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VoiceMode(
    controller: ChatController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    locale: Locale? = null,
    speech: SpeechOutputState = LocalSpeechOutput.current ?: rememberSpeechOutputState(),
    listening: SpeechInputState = rememberSpeechInputState(),
) {
    val context = LocalContext.current
    val state by controller.state.collectAsStateWithLifecycle()
    var phase by remember { mutableStateOf(VoicePhase.LISTENING) }
    var heard by remember { mutableStateOf("") }
    var permitted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> permitted = granted; if (!granted) onClose() }
    // Which reply is being read, and how much of it has been queued.
    var replyId by remember { mutableStateOf<String?>(null) }
    var queued by remember { mutableIntStateOf(0) }
    var restart by remember { mutableIntStateOf(0) }

    fun listen() {
        speech.stop()
        heard = ""
        phase = VoicePhase.LISTENING
        restart++
    }

    listening.onText = { text, final ->
        heard = text
        if (final && text.isNotBlank() && phase == VoicePhase.LISTENING) {
            replyId = null
            queued = 0
            phase = VoicePhase.THINKING
            controller.send(text)
        }
    }

    LaunchedEffect(Unit) { if (!permitted) permission.launch(Manifest.permission.RECORD_AUDIO) }

    // Listen whenever it is our turn; restart after silence (the recognizer stops on its own).
    LaunchedEffect(permitted, restart) {
        if (!permitted) return@LaunchedEffect
        snapshotFlow { phase to listening.phase }.collect { (p, recognizer) ->
            if (p == VoicePhase.LISTENING && recognizer == SpeechInputPhase.IDLE) {
                delay(300)
                if (phase == VoicePhase.LISTENING && listening.phase == SpeechInputPhase.IDLE) listening.start(locale)
            }
        }
    }

    // Read the reply as it streams: complete sentences as they arrive, the rest when it ends.
    LaunchedEffect(state) {
        if (phase != VoicePhase.THINKING && phase != VoicePhase.SPEAKING) return@LaunchedEffect
        val waiting = state.inputRequests.isNotEmpty() ||
            state.messages.lastOrNull()?.parts?.any { it is ToolPart && it.state == ToolState.APPROVAL_REQUESTED } == true
        if (waiting) {
            speech.stop()
            phase = VoicePhase.NEEDS_YOU
            return@LaunchedEffect
        }
        val reply = state.messages.lastOrNull()?.takeIf { it.role == Role.ASSISTANT } ?: return@LaunchedEffect
        if (replyId != reply.id) { replyId = reply.id; queued = 0 }
        val text = speakableText(reply.text)
        val end = if (state.isBusy) text.lastSentenceEnd(queued) else text.length
        if (end > queued) {
            val chunk = text.substring(queued, end).trim()
            queued = end
            if (chunk.isNotEmpty()) {
                phase = VoicePhase.SPEAKING
                speech.enqueue(reply.id, chunk, locale, onDone = if (state.isBusy) null else ({ if (phase == VoicePhase.SPEAKING) listen() }))
            }
        }
        if (!state.isBusy && queued >= text.length && speech.speakingId != reply.id) {
            // Nothing (more) to read: the user's turn.
            if (!speech.isAvailable || text.isEmpty()) listen()
            else if (phase == VoicePhase.THINKING) listen()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speech.stop()
            listening.cancel()
        }
    }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxSize().testTag("voice-mode")) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        ) {
            Spacer(Modifier.weight(1f))
            val tick by produceTick(phase == VoicePhase.SPEAKING)
            Box(
                Modifier
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        when (phase) {
                            VoicePhase.SPEAKING -> listen()
                            VoicePhase.THINKING -> { controller.stop(); listen() }
                            else -> Unit
                        }
                    }
                    .testTag("voice-persona"),
            ) {
                Persona(
                    state = when (phase) {
                        VoicePhase.LISTENING -> PersonaState.LISTENING
                        VoicePhase.THINKING -> PersonaState.THINKING
                        VoicePhase.SPEAKING -> PersonaState.SPEAKING
                        VoicePhase.MUTED, VoicePhase.NEEDS_YOU -> PersonaState.ASLEEP
                    },
                    size = 176.dp,
                    level = { if (phase == VoicePhase.SPEAKING) 0.5f + 0.4f * sin(tick / 3f) else listening.level },
                )
            }
            Spacer(Modifier.height(32.dp))
            Text(
                when (phase) {
                    VoicePhase.LISTENING -> heard.ifBlank { stringResource(R.string.ai_voice_listening) }
                    VoicePhase.THINKING -> stringResource(R.string.ai_voice_thinking)
                    VoicePhase.SPEAKING -> stringResource(R.string.ai_voice_tap_to_interrupt)
                    VoicePhase.MUTED -> stringResource(R.string.ai_voice_muted)
                    VoicePhase.NEEDS_YOU -> stringResource(R.string.ai_voice_needs_you)
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().testTag("voice-caption"),
            )
            if (phase == VoicePhase.NEEDS_YOU) {
                Button(onClick = onClose, modifier = Modifier.padding(top = 16.dp).testTag("voice-open-chat")) { Text(stringResource(R.string.ai_voice_open_chat)) }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconToggleButton(
                    checked = phase == VoicePhase.MUTED,
                    onCheckedChange = { muted -> if (muted) { listening.cancel(); speech.stop(); phase = VoicePhase.MUTED } else listen() },
                    shapes = IconButtonDefaults.toggleableShapes(),
                    modifier = Modifier.size(64.dp).testTag("voice-mute"),
                ) {
                    Icon(
                        if (phase == VoicePhase.MUTED) AiIcons.MicOff else AiIcons.Mic,
                        stringResource(if (phase == VoicePhase.MUTED) R.string.ai_voice_unmute else R.string.ai_voice_mute),
                    )
                }
                FilledIconButton(
                    onClick = onClose,
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                    modifier = Modifier.size(64.dp).testTag("voice-end"),
                ) { Icon(AiIcons.Close, stringResource(R.string.ai_voice_end)) }
            }
        }
    }
}

/** A counter that advances every frame-ish while [running], to animate the speaking persona. */
@Composable
private fun produceTick(running: Boolean) = androidx.compose.runtime.produceState(0, running) {
    while (running) { delay(50); value++ }
}

/**
 * The end of the last complete sentence after [from] in a reply that is still streaming, or [from]
 * when none has ended yet. A sentence counts once something follows its end mark, since the last
 * one may still grow.
 */
internal fun String.lastSentenceEnd(from: Int): Int {
    var end = from
    for (i in from until length - 1) {
        val c = this[i]
        val next = this[i + 1]
        if (c == '\n' || c in "。！？" || (c in ".!?" && next.isWhitespace())) end = i + 1
    }
    return end
}
