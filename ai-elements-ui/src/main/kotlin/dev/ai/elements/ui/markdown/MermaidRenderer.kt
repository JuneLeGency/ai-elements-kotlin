package dev.ai.elements.ui.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws Mermaid source for [MermaidDiagram], which supplies the card, the
 * streaming placeholder and the full-screen viewer around it.
 *
 * The default, [MermaidRenderer.WebView], runs the bundled mermaid.js. The
 * optional `ai-elements-mermaid-native` artifact adds `NativeMermaidRenderer`
 * (Compose Canvas, no WebView). Provide one with [LocalMermaidRenderer].
 */
@Stable
interface MermaidRenderer {
    /**
     * @param fullScreen false: inline, as wide as allowed and sized per
     *   [LocalMermaidSizing], tap → [onClick]; true: fill [modifier]'s bounds, pan and zoom.
     */
    @Composable
    fun Diagram(code: String, fullScreen: Boolean, modifier: Modifier, onClick: (() -> Unit)?)

    companion object {
        /** Bundled mermaid.js 12 in an offline WebView — the reference renderer. */
        val WebView: MermaidRenderer get() = WebViewMermaidRenderer
    }
}

/** The renderer every [MermaidDiagram] below uses. */
val LocalMermaidRenderer = staticCompositionLocalOf<MermaidRenderer> { MermaidRenderer.WebView }

/**
 * Size of inline diagrams, for both renderers. Mermaid lays out at 16px text,
 * which at 1px = 1dp reads larger than body copy, so the default is 0.75.
 *
 * @param scale diagram units to dp (0.75 → 12dp labels); never wider than the column.
 * @param maxInlineHeight inline diagrams shrink to fit this height. Full screen is unscaled.
 */
@Immutable
data class MermaidSizing(val scale: Float = 0.75f, val maxInlineHeight: Dp = 400.dp) {
    companion object {
        val Small = MermaidSizing(scale = 0.6f, maxInlineHeight = 320.dp)
        val Medium = MermaidSizing()
        val Large = MermaidSizing(scale = 1f, maxInlineHeight = 560.dp)
    }
}

val LocalMermaidSizing = staticCompositionLocalOf { MermaidSizing() }
