package dev.ai.elements.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * Material 3 **Expressive** theme for AI Elements: expressive motion (spring
 * based), rounder shapes, and dynamic color on Android 12+; otherwise (or
 * with [dynamicColor] off) one of the generated [AiPalette] schemes.
 *
 * @param palette used when dynamic color is off or unavailable.
 * @param contrast Material 3 contrast level of the [palette] scheme.
 * @param fontFamily applied to every Material type style (null keeps the platform font).
 *   Glyphs it lacks (e.g. CJK in a Latin font) fall back to the system fonts.
 * @param codeFontFamily code blocks, inline code and tool arguments.
 * @param colorScheme your brand's colors; overrides [dynamicColor] and [palette].
 * @param typography your type scale; overrides [fontFamily].
 * @param shapes your shapes; the Material 3 Expressive set by default.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AiElementsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    palette: AiPalette = AiPalette.VIOLET,
    contrast: AiContrast = AiContrast.STANDARD,
    fontFamily: FontFamily? = null,
    codeFontFamily: FontFamily = FontFamily.Monospace,
    colorScheme: ColorScheme? = null,
    typography: Typography? = null,
    shapes: Shapes = ExpressiveShapes,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = colorScheme ?: when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> aiColorScheme(palette, darkTheme, contrast)
    }
    val type = typography ?: remember(fontFamily) { fontFamily?.let { Typography().withFontFamily(it) } ?: Typography() }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = shapes,
        typography = type,
    ) {
        CompositionLocalProvider(LocalCodeFontFamily provides codeFontFamily, content = content)
    }
}

/** True when the active scheme is dark (works with dynamic color too). */
val MaterialTheme.isDark: Boolean
    @Composable @ReadOnlyComposable
    get() = colorScheme.surface.luminance() < 0.5f

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** The font AI Elements uses for code; set through [AiElementsTheme]. */
val LocalCodeFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }

private fun Typography.withFontFamily(family: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)
