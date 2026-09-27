package dev.ai.elements.harness.speech

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dev.ai.elements.core.agent.AgentTool
import dev.ai.elements.core.agent.Capability
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Speech output as agent tools on the platform [TextToSpeech] engine:
 * `speak` reads text aloud (and returns when it has been spoken, so the agent
 * can sequence it) and `stop_speaking` interrupts it. Nothing leaves the device
 * unless the user's TTS engine is a network one.
 *
 * The engine starts lazily on the first call; [close] releases it.
 */
class Speech(private val context: Context) : Capability, AutoCloseable {
    private val lock = Mutex()
    private var engine: TextToSpeech? = null
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val ids = AtomicInteger()

    override val instructions =
        "You can speak to the user with `speak` (text to speech on their device). " +
            "Use it when they ask you to read something aloud or talk; keep spoken text plain, without Markdown."

    override suspend fun tools(): List<AgentTool> = listOf(
        tool(
            "speak",
            "Read text aloud on the user's device. Returns when it has been spoken.",
            "text" to schema("string", "Plain text to speak (no Markdown)."),
            "language" to schema("string", "BCP 47 language tag, e.g. \"en-US\" or \"zh-CN\"; defaults to the device language."),
            "rate" to schema("number", "Speech rate, 1.0 is normal (0.5-2.0)."),
            required = listOf("text"),
        ) { a -> speak(a.s("text")!!, a.s("language"), (a["rate"] as? JsonPrimitive)?.doubleOrNull) },
        tool("stop_speaking", "Stop speaking immediately.") { stop() },
    )

    /** Speak [text] and wait until it is done (or interrupted). */
    suspend fun speak(text: String, language: String? = null, rate: Double? = null): String {
        require(text.isNotBlank()) { "Nothing to speak." }
        val tts = engine()
        val max = TextToSpeech.getMaxSpeechInputLength()
        val chunks = text.chunked(max)
        val done = chunks.mapIndexed { i, chunk ->
            val id = "speak-${ids.incrementAndGet()}"
            val finished = CompletableDeferred<Boolean>().also { pending[id] = it }
            lock.withLock {
                if (i == 0) {
                    language?.let { tag ->
                        val result = tts.setLanguage(Locale.forLanguageTag(tag))
                        check(result >= TextToSpeech.LANG_AVAILABLE) { "No voice installed for $tag." }
                    }
                    tts.setSpeechRate((rate ?: 1.0).coerceIn(0.5, 2.0).toFloat())
                }
                val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                check(tts.speak(chunk, mode, Bundle(), id) == TextToSpeech.SUCCESS) { "The speech engine rejected the text." }
            }
            finished
        }
        // Generous bound: ~12 characters per second at normal speed, plus start-up.
        val completed = withTimeout(30_000L + text.length * 250L) { done.all { it.await() } }
        return if (completed) "Spoke ${text.length} characters." else "Stopped before the end."
    }

    /** Interrupt speech; pending [speak] calls return. */
    fun stop(): String {
        engine?.stop()
        pending.values.forEach { it.complete(false) }
        pending.clear()
        return "Stopped."
    }

    override fun close() {
        stop()
        engine?.shutdown()
        engine = null
    }

    private suspend fun engine(): TextToSpeech = lock.withLock {
        engine?.let { return it }
        val ready = CompletableDeferred<Int>()
        val tts = TextToSpeech(context.applicationContext) { status -> ready.complete(status) }
        val status = withTimeout(10_000) { ready.await() }
        if (status != TextToSpeech.SUCCESS) {
            tts.shutdown()
            error("No text-to-speech engine is available on this device.")
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) { pending.remove(utteranceId)?.complete(true) }
            override fun onStop(utteranceId: String, interrupted: Boolean) { pending.remove(utteranceId)?.complete(false) }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) { pending.remove(utteranceId)?.complete(false) }
            override fun onError(utteranceId: String, errorCode: Int) { pending.remove(utteranceId)?.complete(false) }
        })
        engine = tts
        tts
    }

    private companion object {
        fun schema(type: String, description: String) = buildJsonObject { put("type", type); put("description", description) }
        fun JsonObject.s(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull

        fun tool(name: String, description: String, vararg params: Pair<String, JsonObject>, required: List<String> = emptyList(), run: suspend (JsonObject) -> String) = object : AgentTool {
            override val name = name
            override val description = description
            override val parameters = buildJsonObject {
                put("type", "object")
                put("properties", JsonObject(params.toMap()))
                put("required", JsonArray(required.map(::JsonPrimitive)))
            }
            override suspend fun execute(arguments: JsonObject) = run(arguments)
        }
    }
}
