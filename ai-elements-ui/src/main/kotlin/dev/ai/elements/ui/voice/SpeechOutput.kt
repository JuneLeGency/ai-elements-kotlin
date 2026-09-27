package dev.ai.elements.ui.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Speech output on the platform text-to-speech engine (Android `TextToSpeech`): reads a message
 * aloud, or a reply sentence by sentence while it streams ([enqueue]). One utterance group is
 * active at a time, named by [speakingId]; [stop] ends it.
 */
@Stable
class SpeechOutputState internal constructor(context: Context) {
    /** The id of what is being read (e.g. a message id), or null when silent. */
    var speakingId by mutableStateOf<String?>(null)
        private set

    /** False until the engine is ready, and when the device has none. */
    var isAvailable by mutableStateOf(false)
        private set

    private val pending = AtomicInteger(0)
    private var engine: TextToSpeech? = null
    private var onDone: (() -> Unit)? = null

    init {
        engine = TextToSpeech(context.applicationContext) { status -> isAvailable = status == TextToSpeech.SUCCESS }.apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = finishOne()
                @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) = finishOne()
                override fun onError(utteranceId: String?, errorCode: Int) = finishOne()
                override fun onStop(utteranceId: String?, interrupted: Boolean) = Unit
            })
        }
    }

    /** Read [text] (plain text, see [speakableText]) as [id], replacing whatever is being read. */
    fun speak(id: String, text: String, locale: Locale? = null, onDone: (() -> Unit)? = null) {
        stop()
        enqueue(id, text, locale, onDone)
    }

    /** Add [text] after what [id] is already reading (a streaming reply, sentence by sentence). */
    fun enqueue(id: String, text: String, locale: Locale? = null, onDone: (() -> Unit)? = null) {
        val tts = engine ?: return
        if (speakingId != id) { stop(); speakingId = id }
        onDone?.let { this.onDone = it }
        locale?.let { tts.language = it }
        chunks(text, TextToSpeech.getMaxSpeechInputLength()).forEach { chunk ->
            pending.incrementAndGet()
            tts.speak(chunk, TextToSpeech.QUEUE_ADD, null, "$id#${pending.get()}")
        }
        if (pending.get() == 0 && onDone != null) finish()
    }

    /** Whether [id] still has text queued or playing. */
    fun isSpeaking(id: String): Boolean = speakingId == id

    fun stop() {
        engine?.stop()
        pending.set(0)
        speakingId = null
        onDone = null
    }

    internal fun shutdown() {
        stop()
        engine?.shutdown()
        engine = null
    }

    private fun finishOne() {
        if (pending.decrementAndGet() <= 0) finish()
    }

    private fun finish() {
        // The engine calls back on its own thread; state is read by composition. More text may
        // have been queued meanwhile (a streaming reply), in which case this is not the end.
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            if (pending.get() > 0) return@post
            val done = onDone
            onDone = null
            speakingId = null
            done?.invoke()
        }
    }

    private fun chunks(text: String, max: Int): List<String> {
        val out = mutableListOf<String>()
        var rest = text.trim()
        while (rest.length > max) {
            val cut = rest.lastIndexOfAny(charArrayOf('.', '!', '?', '。', '！', '？', '\n', ' '), max - 1).takeIf { it > 0 } ?: (max - 1)
            out += rest.substring(0, cut + 1).trim()
            rest = rest.substring(cut + 1).trim()
        }
        if (rest.isNotEmpty()) out += rest
        return out
    }
}

/** Remembers a [SpeechOutputState]; the engine is released with the composition. */
@Composable
fun rememberSpeechOutputState(): SpeechOutputState {
    val context = LocalContext.current.applicationContext
    val state = remember { SpeechOutputState(context) }
    DisposableEffect(state) { onDispose { state.shutdown() } }
    return state
}

/**
 * The speech output the elements use: the messages' "Read aloud" and [VoiceMode].
 * [dev.ai.elements.ui.chat.Conversation] creates one when none is provided; provide your own to
 * share one engine across screens.
 */
val LocalSpeechOutput = staticCompositionLocalOf<SpeechOutputState?> { null }

/**
 * [markdown] as text to read aloud: code blocks, diagrams and math are left out (they are not
 * speakable), links read as their text, and Markdown markers are removed.
 */
fun speakableText(markdown: String): String = markdown
    .replace(Regex("```[\\s\\S]*?(```|$)"), " ")                 // fenced code, Mermaid, math fences
    .replace(Regex("\\$\\$[\\s\\S]*?\\$\\$"), " ")                // display math
    .replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), " ")               // images
    .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")             // links → their text
    .replace(Regex("`([^`]*)`"), "$1")                            // inline code
    .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s*"), "")                // headings
    .replace(Regex("(?m)^\\s*[-*+]\\s+"), "")                     // bullets
    .replace(Regex("(?m)^\\s*>\\s?"), "")                         // quotes
    .replace(Regex("(?m)^\\s*\\|?\\s*:?-{3,}.*$"), "")            // table separators
    .replace(Regex("(?m)^[ \\t]*\\|(.*?)\\|?[ \\t]*$")) { row ->         // table rows → "a, b"
        row.groupValues[1].split('|').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
    }
    .replace(Regex("[*_~]{1,3}([^*_~\\n]+)[*_~]{1,3}"), "$1")      // emphasis
    .replace(Regex("\\[(\\d+)]"), "")                             // citation markers
    .replace(Regex("\\s+([.,!?;:。，！？])"), "$1")                  // no space before punctuation
    .replace(Regex("[ \\t]+"), " ")
    .replace(Regex("\\n{2,}"), "\n")
    .trim()
