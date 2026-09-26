package dev.ai.elements.ui.markdown

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.swithun.cmpmermaid.compose.MermaidDiagram as CmpMermaidDiagram
import com.swithun.cmpmermaid.core.GMResult
import com.swithun.cmpmermaid.core.MermaidTheme
import com.swithun.cmpmermaid.core.SceneColor
import dev.ai.elements.ui.theme.isDark

/** How [MermaidDiagram] draws diagrams. */
enum class MermaidRenderer {
    /** The bundled mermaid.js 12 in an offline WebView: the reference renderer. */
    WebView,

    /**
     * Experimental: drawn with Compose Canvas by `cmp-mermaid` (a Kotlin port of
     * Mermaid). No WebView, layout runs off the main thread.
     */
    Native,
}

/** The renderer every [MermaidDiagram] below uses. Defaults to [MermaidRenderer.WebView]. */
val LocalMermaidRenderer = staticCompositionLocalOf { MermaidRenderer.WebView }

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

/**
 * A [MermaidRenderer.Native] diagram. Inline ([fill] = false) it takes its
 * natural size scaled by [LocalMermaidSizing], capped at the available width
 * and [MermaidSizing.maxInlineHeight]; with [fill] it fills the given space, pinch to zoom.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun NativeMermaidView(code: String, fill: Boolean, modifier: Modifier, onClick: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val dark = MaterialTheme.isDark
    val theme = remember(colors, dark) { materialMermaidTheme(colors, dark) }
    // The library sizes Fit scenes to 0 height inside a Column, so size the host from the scene.
    var natural by remember(code, theme) { mutableStateOf<Pair<Float, Float>?>(null) }
    var error by remember(code, theme) { mutableStateOf<String?>(null) }

    val diagram: @Composable (Modifier) -> Unit = { m ->
        CmpMermaidDiagram(
            source = code,
            modifier = m,
            theme = theme,
            respectSourceViewportSizing = false,
            onError = null,
            onRenderResult = { result ->
                when (result) {
                    is GMResult.Ok -> {
                        val pad = result.value.viewportPadding.coerceAtLeast(0f) * 2
                        natural = (result.value.width + pad) to (result.value.height + pad)
                    }
                    is GMResult.Err -> error = result.error.message
                }
            },
        )
    }

    error?.let { message ->
        Column(modifier) {
            Text(
                "Diagram error: ${message.take(160)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            CodeBlock(code, "mermaid")
        }
        return
    }
    if (fill) {
        diagram(modifier)
        return
    }
    val sizing = LocalMermaidSizing.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val size = natural?.takeIf { it.first > 0f && it.second > 0f }
        // One call site, so the scene laid out at the placeholder size is reused once sized.
        val sized = if (size == null) {
            Modifier.fillMaxWidth().height(96.dp)
        } else {
            val (w, h) = fitted(size.first * sizing.scale, size.second * sizing.scale, maxWidth, sizing.maxInlineHeight)
            Modifier.size(w, h).then(
                onClick?.let { Modifier.clickable(onClickLabel = "Open diagram", role = Role.Button, onClick = it) } ?: Modifier,
            )
        }
        diagram(sized)
        if (size == null) LoadingIndicator(Modifier.size(40.dp))
    }
}

private fun fitted(width: Float, height: Float, maxWidth: Dp, maxHeight: Dp): Pair<Dp, Dp> {
    val w = min(width.dp, maxWidth)
    val h = w * (height / width)
    if (h <= maxHeight) return w to h
    return w * (maxHeight / h) to maxHeight
}

/** Mermaid colors from the Material scheme, matching the WebView renderer's `base` theme variables. */
private fun materialMermaidTheme(c: ColorScheme, dark: Boolean): MermaidTheme {
    fun Color.hex() = "#%06X".format(toArgb() and 0xFFFFFF)
    fun Color.scene() = SceneColor(toArgb().toLong() and 0xFFFFFFFFL)
    // Pie slices / mindmap branches: tonal containers first, then their accents.
    val palette = listOf(
        c.primaryContainer, c.secondaryContainer, c.tertiaryContainer, c.surfaceVariant,
        c.inversePrimary, c.primary, c.secondary, c.tertiary,
    )
    val onPalette = listOf(
        c.onPrimaryContainer, c.onSecondaryContainer, c.onTertiaryContainer, c.onSurfaceVariant,
        c.onSurface, c.onPrimary, c.onSecondary, c.onTertiary,
    )
    val vars = buildMap {
        put("background", c.surfaceContainerLow.hex())
        put("primaryColor", c.primaryContainer.hex())
        put("primaryTextColor", c.onPrimaryContainer.hex())
        put("primaryBorderColor", c.primary.hex())
        put("mainBkg", c.primaryContainer.hex())
        put("nodeBorder", c.primary.hex())
        put("nodeTextColor", c.onPrimaryContainer.hex())
        put("lineColor", c.outline.hex())
        put("textColor", c.onSurface.hex())
        put("titleColor", c.onSurface.hex())
        put("edgeLabelBackground", c.surfaceContainerLow.hex())
        put("relationColor", c.outline.hex())
        put("relationLabelBackground", c.surfaceContainerLow.hex())
        put("relationLabelColor", c.onSurface.hex())
        // Gantt
        put("sectionBkgColor", c.surfaceContainerHigh.hex())
        put("sectionBkgColor2", c.surfaceContainer.hex())
        put("altSectionBkgColor", c.surfaceContainerLow.hex())
        put("taskBkgColor", c.primaryContainer.hex())
        put("taskBorderColor", c.primary.hex())
        put("taskTextColor", c.onPrimaryContainer.hex())
        put("taskTextDarkColor", c.onSurface.hex())
        put("taskTextOutsideColor", c.onSurface.hex())
        put("activeTaskBkgColor", c.secondaryContainer.hex())
        put("activeTaskBorderColor", c.secondary.hex())
        put("doneTaskBkgColor", c.surfaceVariant.hex())
        put("doneTaskBorderColor", c.outline.hex())
        put("critBkgColor", c.errorContainer.hex())
        put("critBorderColor", c.error.hex())
        put("todayLineColor", c.error.hex())
        put("gridColor", c.outlineVariant.hex())
        // Pie
        palette.forEachIndexed { i, color -> put("pie${i + 1}", color.hex()) }
        put("pieTitleTextColor", c.onSurface.hex())
        put("pieSectionTextColor", c.onSurface.hex())
        put("pieLegendTextColor", c.onSurface.hex())
        put("pieStrokeColor", c.surface.hex())
        put("pieOuterStrokeColor", c.outlineVariant.hex())
        put("pieOpacity", "1")
        // Mindmap
        put("git0", c.primary.hex())
        put("gitBranchLabel0", c.onPrimary.hex())
        palette.forEachIndexed { i, color ->
            put("cScale$i", color.hex())
            put("cScaleLabel$i", onPalette[i].hex())
            put("cScaleInv$i", onPalette[i].hex())
        }
        put("useGradient", "false")
    }
    val base = if (dark) MermaidTheme.Dark else MermaidTheme.MermaidDefault
    val themed = when (val r = MermaidTheme.withVariables(base, vars, themeName = "base")) {
        is GMResult.Ok -> r.value
        is GMResult.Err -> base
    }
    // Not exposed as variables: sequence actors and subgraphs use the group colors, notes the note colors.
    return themed.copy(
        groupFill = c.secondaryContainer.scene(),
        groupStroke = c.secondary.scene(),
        flowContainerStroke = c.outlineVariant.scene(),
        groupText = c.onSecondaryContainer.scene(),
        noteFill = c.tertiaryContainer.scene(),
        noteStroke = c.tertiary.scene(),
        noteText = c.onTertiaryContainer.scene(),
        edgeLabelFill = c.surfaceContainerLow.scene(),
        dropShadow = null,
    )
}
