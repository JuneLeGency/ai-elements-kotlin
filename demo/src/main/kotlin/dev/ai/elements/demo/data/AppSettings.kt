package dev.ai.elements.demo.data

import android.content.Context
import dev.ai.elements.ui.theme.AiContrast
import dev.ai.elements.ui.theme.AiPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class DiagramSize { SMALL, MEDIUM, LARGE }

/** Bundled UI fonts (all SIL OFL 1.1, see assets/licenses/FONTS.md). */
enum class AppFont { SYSTEM, GEIST, INTER, WENKAI }

/** In-app text size, multiplied onto the system font scale. */
enum class TextSize(val scale: Float) { SMALL(0.9f), DEFAULT(1f), LARGE(1.1f), EXTRA_LARGE(1.25f) }

data class Appearance(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    /** Draw Mermaid with Compose Canvas (cmp-mermaid) instead of the WebView. */
    val nativeMermaid: Boolean = false,
    val diagramSize: DiagramSize = DiagramSize.MEDIUM,
    val font: AppFont = AppFont.SYSTEM,
    val textSize: TextSize = TextSize.DEFAULT,
    /** Offer subscription sign-ins through vendors' CLI clients (see OAuthProvider). */
    val subscriptionSignIn: Boolean = false,
    val palette: AiPalette = AiPalette.VIOLET,
    val contrast: AiContrast = AiContrast.STANDARD,
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
            textSize = runCatching { TextSize.valueOf(prefs.getString("text_size", null)!!) }.getOrDefault(TextSize.DEFAULT),
            subscriptionSignIn = prefs.getBoolean("subscription_sign_in", false),
            palette = runCatching { AiPalette.valueOf(prefs.getString("palette", null)!!) }.getOrDefault(AiPalette.VIOLET),
            contrast = runCatching { AiContrast.valueOf(prefs.getString("contrast", null)!!) }.getOrDefault(AiContrast.STANDARD),
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
            .putString("text_size", appearance.textSize.name)
            .putBoolean("subscription_sign_in", appearance.subscriptionSignIn)
            .putString("palette", appearance.palette.name)
            .putString("contrast", appearance.contrast.name)
            .apply()
    }
}
