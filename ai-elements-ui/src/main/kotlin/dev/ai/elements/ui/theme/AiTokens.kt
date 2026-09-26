package dev.ai.elements.ui.theme

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Spacing on the Material 4dp grid. Components use these instead of literals
 * so an app can see (and match) the rhythm the kit is built on.
 */
object AiSpacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Vertical gap between conversation items. */
    val turn = 20.dp
}

/** Component sizes from the M3 Expressive icon-button and touch-target specs. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object AiSize {
    /** Minimum touch target for anything interactive (M3 accessibility). */
    val touchTarget = 48.dp

    /** Icon inside an extra-small icon button (M3 Expressive XS). */
    val compactIcon = IconButtonDefaults.extraSmallIconSize

    /** Small status glyphs inside chips and pills. */
    val badgeIcon = 16.dp

    /** Avatars and leading glyph containers. */
    val avatar = 28.dp
}

/**
 * An extra-small (32dp) icon button that still has a 48dp touch target:
 * the visual shrinks, the hit area doesn't.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun Modifier.compactIconButton(): Modifier =
    minimumInteractiveComponentSize().size(IconButtonDefaults.extraSmallContainerSize())

/**
 * Reading type for conversation content, derived from the app's
 * [MaterialTheme.typography] (so custom fonts carry over). Chat is long-form
 * reading on a small screen: body sits between M3 bodyMedium and bodyLarge,
 * the same density as mainstream assistant apps, and headings step down so a
 * reply doesn't read like a document title page.
 */
object AiType {
    /** Message text, in both the user bubble and assistant Markdown. */
    val body: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.15.sp)

    /** Code blocks. */
    val code: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp, letterSpacing = 0.sp)

    /** Tables and other secondary content. */
    val small: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp)

    val h1: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)

    val h2: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)

    val h3: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)

    val h4: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
}
