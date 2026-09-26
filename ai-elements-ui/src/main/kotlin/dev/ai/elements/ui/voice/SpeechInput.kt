package dev.ai.elements.ui.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.ai.elements.ui.R
import java.util.Locale

/** Where [SpeechInput] is. */
enum class SpeechInputPhase { IDLE, LISTENING, PROCESSING }

/**
 * On-device speech recognition state (Android `SpeechRecognizer`), exposed so
 * a UI can show the live [level] (e.g. on a [dev.ai.elements.ui.chat.Persona]).
 */
@Stable
class SpeechInputState internal constructor(private val context: Context) {
    var phase by mutableStateOf(SpeechInputPhase.IDLE)
        private set

    /** Input loudness, 0–1, while listening. */
    var level by mutableFloatStateOf(0f)
        private set

    var error by mutableStateOf<String?>(null)
        internal set

    private var recognizer: SpeechRecognizer? = null
    internal var onText: (String, Boolean) -> Unit = { _, _ -> }

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    internal fun start(locale: Locale?) {
        error = null
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { phase = SpeechInputPhase.LISTENING }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) { level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { phase = SpeechInputPhase.PROCESSING; level = 0f }
            override fun onError(code: Int) {
                phase = SpeechInputPhase.IDLE
                level = 0f
                // "No match" / "speech timeout" just mean nothing was said.
                if (code != SpeechRecognizer.ERROR_NO_MATCH && code != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) error = "Speech recognition error $code"
            }
            override fun onResults(results: Bundle?) {
                phase = SpeechInputPhase.IDLE
                level = 0f
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { onText(it, true) }
            }
            override fun onPartialResults(partial: Bundle?) {
                partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { onText(it, false) }
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        locale?.let { intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, it.toLanguageTag()) }
        phase = SpeechInputPhase.LISTENING
        r.startListening(intent)
    }

    fun stop() {
        if (phase == SpeechInputPhase.LISTENING) {
            recognizer?.stopListening()
            phase = SpeechInputPhase.PROCESSING
        }
    }

    internal fun destroy() {
        recognizer?.destroy()
        recognizer = null
        phase = SpeechInputPhase.IDLE
    }
}

@Composable
fun rememberSpeechInputState(): SpeechInputState {
    val context = LocalContext.current.applicationContext
    val state = remember { SpeechInputState(context) }
    DisposableEffect(state) { onDispose { state.destroy() } }
    return state
}

/**
 * Voice input (AI Elements `<SpeechInput>`): tap to dictate, tap again to
 * stop. [onTranscript] receives partial text while speaking and the final
 * text (isFinal = true) at the end. Asks for the microphone permission the
 * first time; the host app must declare `RECORD_AUDIO` in its manifest.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SpeechInput(
    onTranscript: (text: String, isFinal: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    state: SpeechInputState = rememberSpeechInputState(),
    locale: Locale? = null,
) {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onTranscript)
    state.onText = { text, final -> latest(text, final) }
    val deniedText = stringResource(R.string.ai_mic_permission)
    val unavailableText = stringResource(R.string.ai_speech_unavailable)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) state.start(locale) else state.error = deniedText
    }
    val pulse by animateFloatAsState(1f + 0.18f * state.level, label = "mic-level")
    Box(modifier, contentAlignment = Alignment.Center) {
        FilledIconButton(
            onClick = {
                when {
                    state.phase == SpeechInputPhase.LISTENING -> state.stop()
                    state.phase == SpeechInputPhase.PROCESSING -> Unit
                    !state.isAvailable -> state.error = unavailableText
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> state.start(locale)
                    else -> permission.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            shapes = IconButtonDefaults.shapes(),
            colors = if (state.phase == SpeechInputPhase.LISTENING) {
                IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
            } else {
                IconButtonDefaults.filledTonalIconButtonColors()
            },
            modifier = Modifier.scale(if (state.phase == SpeechInputPhase.LISTENING) pulse else 1f).testTag("speech-input"),
        ) {
            when (state.phase) {
                SpeechInputPhase.IDLE -> Icon(Icons.Outlined.Mic, stringResource(R.string.ai_start_voice))
                SpeechInputPhase.LISTENING -> Icon(Icons.Outlined.Stop, stringResource(R.string.ai_stop_voice))
                SpeechInputPhase.PROCESSING -> LoadingIndicator(Modifier.size(24.dp))
            }
        }
    }
}

