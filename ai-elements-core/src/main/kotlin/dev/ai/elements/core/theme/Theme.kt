package dev.ai.elements.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * AI Elements design tokens — the platform-agnostic equivalents of the shadcn/ui
 * CSS variables that the web `ai-elements` registry consumes.
 *
 * The web components read `--background`, `--foreground`, `--primary`,
 * `--muted-foreground`, etc. from CSS. Here we expose them as Compose values so a
 * single source of truth styles every component.
 */
@Immutable
data class AiElementsTokens(
    val background: Color = Color(0xFFFAFAFA),
    val foreground: Color = Color(0xFF18181B),
    val card: Color = Color(0xFFFFFFFF),
    val cardForeground: Color = Color(0xFF18181B),
    val primary: Color = Color(0xFF18181B),
    val primaryForeground: Color = Color(0xFFFFFFFF),
    val secondary: Color = Color(0xFFE4E4E7),
    val secondaryForeground: Color = Color(0xFF18181B),
    val muted: Color = Color(0xFFF4F4F5),
    val mutedForeground: Color = Color(0xFF71717A),
    val accent: Color = Color(0xFFE4E4E7),
    val accentForeground: Color = Color(0xFF18181B),
    val border: Color = Color(0xFFE4E4E7),
    val input: Color = Color(0xFFE4E4E7),
    val ring: Color = Color(0xFF18181B),
    val destructive: Color = Color(0xFFEF4444),

    // AI Elements specific accents (used by Shimmer / Reasoning / Tool highlights).
    val brand: Color = Color(0xFF4F46E5),
    val brandSoft: Color = Color(0xFFEEF2FF),

    // Bubble paddings / radii used by Message.
    val messageBubbleRadius: Dp = 16.dp,
    val messageMaxWidthFraction: Float = 0.95f,
)

val LightAiTokens = AiElementsTokens(
    background = Color(0xFFFAFAFA),
    foreground = Color(0xFF18181B),
    card = Color(0xFFFFFFFF),
    cardForeground = Color(0xFF18181B),
    primary = Color(0xFF18181B),
    primaryForeground = Color(0xFFFFFFFF),
    secondary = Color(0xFFE4E4E7),
    secondaryForeground = Color(0xFF18181B),
    muted = Color(0xFFF4F4F5),
    mutedForeground = Color(0xFF71717A),
    accent = Color(0xFFE4E4E7),
    accentForeground = Color(0xFF18181B),
    border = Color(0xFFE4E4E7),
    input = Color(0xFFE4E4E7),
    ring = Color(0xFF18181B),
    destructive = Color(0xFFEF4444),
    brand = Color(0xFF4F46E5),
    brandSoft = Color(0xFFEEF2FF),
)

val DarkAiTokens = AiElementsTokens(
    background = Color(0xFF0A0A0A),
    foreground = Color(0xFFF4F4F5),
    card = Color(0xFF18181B),
    cardForeground = Color(0xFFF4F4F5),
    primary = Color(0xFFF4F4F5),
    primaryForeground = Color(0xFF18181B),
    secondary = Color(0xFF27272A),
    secondaryForeground = Color(0xFFF4F4F5),
    muted = Color(0xFF18181B),
    mutedForeground = Color(0xA1A1AA),
    accent = Color(0xFF27272A),
    accentForeground = Color(0xFFF4F4F5),
    border = Color(0xFF27272A),
    input = Color(0xFF27272A),
    ring = Color(0xFF52525B),
    destructive = Color(0xFFF87171),
    brand = Color(0xFF818CF8),
    brandSoft = Color(0xFF1E1B4B),
)

/**
 * The AI Elements Material3 Expressive theme.
 *
 * Wraps [MaterialTheme] so downstream apps only need:
 * ```
 * AiElementsTheme {
 *     ChatScreen()
 * }
 * ```
 *
 * [tokens] lets an app override the design tokens (e.g. to match a brand) without
 * touching any component.
 */
@Composable
fun AiElementsTheme(
    darkTheme: Boolean = false,
    tokens: AiElementsTokens? = null,
    content: @Composable () -> Unit,
) {
    val resolvedTokens = tokens ?: if (darkTheme) DarkAiTokens else LightAiTokens

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = resolvedTokens.brand,
            onPrimary = Color.White,
            secondary = resolvedTokens.brandSoft,
            background = resolvedTokens.background,
            surface = resolvedTokens.card,
            onBackground = resolvedTokens.foreground,
            onSurface = resolvedTokens.foreground,
            outline = resolvedTokens.border,
        )
    } else {
        androidx.compose.material3.lightColorScheme(
            primary = resolvedTokens.brand,
            onPrimary = Color.White,
            secondary = resolvedTokens.brandSoft,
            background = resolvedTokens.background,
            surface = resolvedTokens.card,
            onBackground = resolvedTokens.foreground,
            onSurface = resolvedTokens.foreground,
            outline = resolvedTokens.border,
        )
    }

    AiTokensHolder.tokens = resolvedTokens

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AiTypography,
        shapes = AiShapes,
        content = content,
    )
}

/**
 * The design tokens in effect for the current composition.
 *
 * Components read `AiTokens()` instead of `MaterialTheme.colorScheme` for the
 * AI-specific colors (brand, soft, bubble radius) that Material3 doesn't model.
 */
@Composable
fun AiTokens(): AiElementsTokens {
    return AiTokensHolder.tokens
}

@Immutable
internal object AiTokensHolder {
    var tokens: AiElementsTokens = LightAiTokens
}

/**
 * AI Elements typography — mirrors the web component text sizes.
 */
val AiTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.15.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.15.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
)

/**
 * Expressive (2025) shapes — large, soft corners for the AI-native look.
 */
val AiShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)
