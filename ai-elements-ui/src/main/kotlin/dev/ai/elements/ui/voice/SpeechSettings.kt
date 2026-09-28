package dev.ai.elements.ui.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Which of the device's speech engines the voice elements use, and how they speak. Every field
 * defaults to the system's choice, so an app only sets what its user picked (e.g. from
 * [recognitionServices] and [ttsEngines] in a settings screen).
 *
 * @property recognizer a speech recognition service (`android.speech.RecognitionService`), e.g.
 *   the vendor's or Google's; null uses the system default.
 * @property onDeviceRecognition recognise on the device, without sending audio anywhere, where the
 *   platform offers it (Android 12+, [SpeechRecognizer.isOnDeviceRecognitionAvailable]).
 * @property ttsEngine a text-to-speech engine's package ([TextToSpeech.EngineInfo.name]); null uses
 *   the system default.
 * @property voice a voice of that engine ([android.speech.tts.Voice.getName]); null picks the
 *   engine's default for the language.
 * @property rate speech rate, 1 = normal.
 * @property pitch speech pitch, 1 = normal.
 */
@Immutable
data class SpeechSettings(
    val recognizer: ComponentName? = null,
    val onDeviceRecognition: Boolean = false,
    val ttsEngine: String? = null,
    val voice: String? = null,
    val rate: Float = 1f,
    val pitch: Float = 1f,
)

/** The speech engines the voice elements ([SpeechInput], "Read aloud", [VoiceMode]) use. */
val LocalSpeechSettings = staticCompositionLocalOf { SpeechSettings() }

/** An installed speech recognition service. */
data class RecognitionServiceInfo(val component: ComponentName, val label: String)

/** The speech recognition services installed on the device (needs the `<queries>` ai-elements-ui declares). */
fun recognitionServices(context: Context): List<RecognitionServiceInfo> {
    val pm = context.packageManager
    return pm.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0).map { info ->
        val service = info.serviceInfo
        RecognitionServiceInfo(ComponentName(service.packageName, service.name), info.loadLabel(pm).toString())
    }.distinctBy { it.component }
}

/** Whether on-device recognition ([SpeechSettings.onDeviceRecognition]) is offered here. */
fun isOnDeviceRecognitionAvailable(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

/** The recognizer [settings] ask for, falling back to the system default. */
internal fun createRecognizer(context: Context, settings: SpeechSettings): SpeechRecognizer = when {
    settings.onDeviceRecognition && isOnDeviceRecognitionAvailable(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
        SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
    settings.recognizer != null -> SpeechRecognizer.createSpeechRecognizer(context, settings.recognizer)
    else -> SpeechRecognizer.createSpeechRecognizer(context)
}
