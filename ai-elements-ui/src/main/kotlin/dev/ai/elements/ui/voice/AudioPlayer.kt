package dev.ai.elements.ui.voice

import dev.ai.elements.ui.icons.AiIcons
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ai.elements.ui.R
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Playback state for [AudioPlayer], hoisted so other elements can follow it
 * (e.g. [Transcription] highlighting the segment being played).
 */
@Stable
class AudioPlayerState internal constructor(private val context: Context, val source: String) {
    var isReady by mutableStateOf(false)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var speed by mutableFloatStateOf(1f)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var player: MediaPlayer? = null

    internal fun prepare() {
        release()
        val p = MediaPlayer()
        player = p
        runCatching {
            p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            val uri = Uri.parse(source)
            if (uri.scheme == null) p.setDataSource(source) else p.setDataSource(context, uri)
            p.setOnPreparedListener { durationMs = it.duration.toLong().coerceAtLeast(0); isReady = true }
            p.setOnCompletionListener { isPlaying = false; positionMs = durationMs }
            p.setOnErrorListener { _, what, extra -> error = "MediaPlayer error $what/$extra"; isPlaying = false; true }
            p.prepareAsync()
        }.onFailure { error = it.message ?: it.javaClass.simpleName }
    }

    fun play() {
        val p = player ?: return
        if (!isReady) return
        if (positionMs >= durationMs && durationMs > 0) seekTo(0)
        runCatching { p.playbackParams = p.playbackParams.setSpeed(speed) }
        p.start()
        isPlaying = true
    }

    fun pause() {
        player?.takeIf { isPlaying }?.pause()
        isPlaying = false
    }

    fun toggle() = if (isPlaying) pause() else play()

    fun seekTo(ms: Long) {
        val target = ms.coerceIn(0, durationMs.coerceAtLeast(0))
        player?.seekTo(target.toInt())
        positionMs = target
    }

    fun seekBy(deltaMs: Long) = seekTo(positionMs + deltaMs)

    /** Cycles 1× → 1.5× → 2× → 1×. */
    fun cycleSpeed() {
        speed = when (speed) { 1f -> 1.5f; 1.5f -> 2f; else -> 1f }
        if (isPlaying) runCatching { player?.let { it.playbackParams = it.playbackParams.setSpeed(speed) } }
    }

    internal fun tick() { player?.takeIf { isPlaying }?.let { positionMs = it.currentPosition.toLong() } }

    internal fun release() {
        player?.release()
        player = null
        isReady = false
        isPlaying = false
    }
}

/** Remembers an [AudioPlayerState] for [source] (URL, content:// URI or file path), released with the composition. */
@Composable
fun rememberAudioPlayerState(source: String): AudioPlayerState {
    val context = LocalContext.current.applicationContext
    val state = remember(source) { AudioPlayerState(context, source) }
    DisposableEffect(state) {
        state.prepare()
        onDispose { state.release() }
    }
    LaunchedEffect(state, state.isPlaying) {
        while (state.isPlaying) {
            state.tick()
            delay(100)
        }
    }
    return state
}

/**
 * An audio player for generated speech and recordings (AI Elements
 * `<AudioPlayer>`): play / pause, ±10 s, a seek bar with times, and speed.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AudioPlayer(state: AudioPlayerState, modifier: Modifier = Modifier, title: String? = null) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth().testTag("audio-player")) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            title?.let { Text(it, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp)) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { state.seekBy(-10_000) }, enabled = state.isReady, shapes = IconButtonDefaults.shapes()) {
                    Icon(AiIcons.Replay10, stringResource(R.string.ai_seek_back))
                }
                FilledIconButton(
                    onClick = state::toggle,
                    enabled = state.isReady,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()).testTag("audio-play"),
                ) {
                    when {
                        !state.isReady && state.error == null -> LoadingIndicator(Modifier.size(24.dp))
                        state.isPlaying -> Icon(AiIcons.Pause, stringResource(R.string.ai_pause))
                        else -> Icon(AiIcons.PlayArrow, stringResource(R.string.ai_play))
                    }
                }
                IconButton(onClick = { state.seekBy(10_000) }, enabled = state.isReady, shapes = IconButtonDefaults.shapes()) {
                    Icon(AiIcons.Forward10, stringResource(R.string.ai_seek_forward))
                }
                Column(Modifier.weight(1f)) {
                    Slider(
                        value = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f,
                        onValueChange = { state.seekTo((it * state.durationMs).toLong()) },
                        enabled = state.isReady,
                    )
                    Row {
                        Text(clock(state.positionMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text(clock(state.durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = state::cycleSpeed, enabled = state.isReady) {
                    Text(if (state.speed == 1f) "1×" else "${state.speed}×", style = MaterialTheme.typography.labelLarge)
                }
            }
            state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp)) }
        }
    }
}

internal fun clock(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
    else String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}
