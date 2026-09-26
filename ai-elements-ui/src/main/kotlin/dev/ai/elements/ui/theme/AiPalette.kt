package dev.ai.elements.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Built-in color palettes for [AiElementsTheme], each a full Material 3 color
 * scheme computed from one seed color by Google's material-color-utilities
 * (tonal-spot; [GRAPHITE] is the neutral variant), in light and dark and at
 * three contrast levels. Regenerate with `tools/generate-schemes.mjs`.
 *
 * Used when dynamic color is off or unavailable (Android 11 and below).
 */
enum class AiPalette(val seed: Color) {
    VIOLET(Color(0xFF6750A4)),
    OCEAN(Color(0xFF0B57D0)),
    JADE(Color(0xFF00897B)),
    FOREST(Color(0xFF386A20)),
    SUNSET(Color(0xFFE8710A)),
    SAKURA(Color(0xFFB4436C)),
    GRAPHITE(Color(0xFF5F6368)),
}

/** Material 3 contrast levels (standard, medium, high), for readability needs. */
enum class AiContrast { STANDARD, MEDIUM, HIGH }

/** The generated scheme for [palette]; cached, cheap to call from composition. */
fun aiColorScheme(palette: AiPalette, dark: Boolean, contrast: AiContrast = AiContrast.STANDARD): ColorScheme =
    cache.getOrPut(Triple(palette, dark, contrast)) {
        schemeOf(GeneratedSchemes[palette.ordinal][if (dark) 1 else 0][contrast.ordinal])
    }

private val cache = java.util.concurrent.ConcurrentHashMap<Triple<AiPalette, Boolean, AiContrast>, ColorScheme>()

private fun schemeOf(argb: LongArray): ColorScheme {
    val c = SchemeRoles.withIndex().associate { (i, role) -> role to Color(argb[i]) }
    fun r(role: String) = c.getValue(role)
    return ColorScheme(
        primary = r("primary"), onPrimary = r("onPrimary"),
        primaryContainer = r("primaryContainer"), onPrimaryContainer = r("onPrimaryContainer"),
        inversePrimary = r("inversePrimary"),
        secondary = r("secondary"), onSecondary = r("onSecondary"),
        secondaryContainer = r("secondaryContainer"), onSecondaryContainer = r("onSecondaryContainer"),
        tertiary = r("tertiary"), onTertiary = r("onTertiary"),
        tertiaryContainer = r("tertiaryContainer"), onTertiaryContainer = r("onTertiaryContainer"),
        background = r("background"), onBackground = r("onBackground"),
        surface = r("surface"), onSurface = r("onSurface"),
        surfaceVariant = r("surfaceVariant"), onSurfaceVariant = r("onSurfaceVariant"),
        surfaceTint = r("surfaceTint"),
        inverseSurface = r("inverseSurface"), inverseOnSurface = r("inverseOnSurface"),
        error = r("error"), onError = r("onError"),
        errorContainer = r("errorContainer"), onErrorContainer = r("onErrorContainer"),
        outline = r("outline"), outlineVariant = r("outlineVariant"), scrim = r("scrim"),
        surfaceBright = r("surfaceBright"), surfaceContainer = r("surfaceContainer"),
        surfaceContainerHigh = r("surfaceContainerHigh"), surfaceContainerHighest = r("surfaceContainerHighest"),
        surfaceContainerLow = r("surfaceContainerLow"), surfaceContainerLowest = r("surfaceContainerLowest"),
        surfaceDim = r("surfaceDim"),
        primaryFixed = r("primaryFixed"), primaryFixedDim = r("primaryFixedDim"),
        onPrimaryFixed = r("onPrimaryFixed"), onPrimaryFixedVariant = r("onPrimaryFixedVariant"),
        secondaryFixed = r("secondaryFixed"), secondaryFixedDim = r("secondaryFixedDim"),
        onSecondaryFixed = r("onSecondaryFixed"), onSecondaryFixedVariant = r("onSecondaryFixedVariant"),
        tertiaryFixed = r("tertiaryFixed"), tertiaryFixedDim = r("tertiaryFixedDim"),
        onTertiaryFixed = r("onTertiaryFixed"), onTertiaryFixedVariant = r("onTertiaryFixedVariant"),
    )
}
