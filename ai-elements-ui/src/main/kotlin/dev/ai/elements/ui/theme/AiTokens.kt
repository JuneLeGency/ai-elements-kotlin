package dev.ai.elements.ui.theme

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
    val turn = 24.dp
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
    val avatar = 32.dp
}

/**
 * An extra-small (32dp) icon button that still has a 48dp touch target:
 * the visual shrinks, the hit area doesn't.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun Modifier.compactIconButton(): Modifier =
    minimumInteractiveComponentSize().size(IconButtonDefaults.extraSmallContainerSize())
