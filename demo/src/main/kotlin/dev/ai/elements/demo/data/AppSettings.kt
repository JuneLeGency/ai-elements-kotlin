package dev.ai.elements.demo.data

import android.content.Context
import dev.ai.elements.ui.theme.AiContrast
import dev.ai.elements.ui.theme.AiPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class DiagramSize { SMALL, MEDIUM, LARGE }

/** UI fonts: the system's (sans and serif, e.g. Noto Sans / Serif CJK) and bundled ones (SIL OFL 1.1, see assets/licenses/FONTS.md). */
enum class AppFont { SYSTEM, SERIF, GEIST, INTER, WENKAI }

/** Code fonts (bundled ones SIL OFL 1.1); FOLLOW pairs with the UI font. */
enum class CodeFont { FOLLOW, SYSTEM, GEIST_MONO, JETBRAINS_MONO, FIRA_CODE }

/** In-app text size, multiplied onto the system font scale. */
enum class TextSize(val scale: Float) { SMALL(0.9f), DEFAULT(1f), LARGE(1.1f), EXTRA_LARGE(1.25f) }

data class Appearance(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    /** Draw Mermaid with Compose Canvas (cmp-mermaid) instead of the WebView. */
    val nativeMermaid: Boolean = false,
    val diagramSize: DiagramSize = DiagramSize.MEDIUM,
    val font: AppFont = AppFont.SYSTEM,
    val codeFont: CodeFont = CodeFont.FOLLOW,
    val textSize: TextSize = TextSize.DEFAULT,
    /** Offer subscription sign-ins through vendors' CLI clients (see OAuthProvider). */
    val subscriptionSignIn: Boolean = false,
    val palette: AiPalette = AiPalette.VIOLET,
    val contrast: AiContrast = AiContrast.STANDARD,
    /** Speech engines (see [dev.ai.elements.ui.voice.SpeechSettings]); null / default = the system's. */
    val speechRecognizer: String? = null,
    val onDeviceRecognition: Boolean = false,
    val ttsEngine: String? = null,
    val ttsVoice: String? = null,
    val speechRate: Float = 1f,
)

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("ai_elements_demo", Context.MODE_PRIVATE)

    private val _appearance = MutableStateFlow(
        Appearance(
            themeMode = runCatching { ThemeMode.valueOf(prefs.getString("theme", null)!!) }.getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = prefs.getBoolean("dynamic_color", true),
            nativeMermaid = prefs.getBoolean("native_mermaid", false),
            diagramSize = runCatching { DiagramSize.valueOf(prefs.getString("diagram_size", null)!!) }.getOrDefault(DiagramSize.MEDIUM),
            font = runCatching { AppFont.valueOf(prefs.getString("font", null)!!) }.getOrDefault(AppFont.SYSTEM),
            codeFont = runCatching { CodeFont.valueOf(prefs.getString("code_font", null)!!) }.getOrDefault(CodeFont.FOLLOW),
            textSize = runCatching { TextSize.valueOf(prefs.getString("text_size", null)!!) }.getOrDefault(TextSize.DEFAULT),
            subscriptionSignIn = prefs.getBoolean("subscription_sign_in", false),
            palette = runCatching { AiPalette.valueOf(prefs.getString("palette", null)!!) }.getOrDefault(AiPalette.VIOLET),
            contrast = runCatching { AiContrast.valueOf(prefs.getString("contrast", null)!!) }.getOrDefault(AiContrast.STANDARD),
            speechRecognizer = prefs.getString("speech_recognizer", null),
            onDeviceRecognition = prefs.getBoolean("on_device_recognition", false),
            ttsEngine = prefs.getString("tts_engine", null),
            ttsVoice = prefs.getString("tts_voice", null),
            speechRate = prefs.getFloat("speech_rate", 1f),
        ),
    )
    val appearance: StateFlow<Appearance> = _appearance.asStateFlow()

    fun update(appearance: Appearance) {
        _appearance.value = appearance
        prefs.edit()
            .putString("theme", appearance.themeMode.name)
            .putBoolean("dynamic_color", appearance.dynamicColor)
            .putBoolean("native_mermaid", appearance.nativeMermaid)
            .putString("diagram_size", appearance.diagramSize.name)
            .putString("font", appearance.font.name)
            .putString("code_font", appearance.codeFont.name)
            .putString("text_size", appearance.textSize.name)
            .putBoolean("subscription_sign_in", appearance.subscriptionSignIn)
            .putString("palette", appearance.palette.name)
            .putString("contrast", appearance.contrast.name)
            .putString("speech_recognizer", appearance.speechRecognizer)
            .putBoolean("on_device_recognition", appearance.onDeviceRecognition)
            .putString("tts_engine", appearance.ttsEngine)
            .putString("tts_voice", appearance.ttsVoice)
            .putFloat("speech_rate", appearance.speechRate)
            .apply()
    }
}

/** The speech engines the user picked, for the voice elements. */
val Appearance.speech: dev.ai.elements.ui.voice.SpeechSettings
    get() = dev.ai.elements.ui.voice.SpeechSettings(
        recognizer = speechRecognizer?.let { android.content.ComponentName.unflattenFromString(it) },
        onDeviceRecognition = onDeviceRecognition,
        ttsEngine = ttsEngine,
        voice = ttsVoice,
        rate = speechRate,
    )
