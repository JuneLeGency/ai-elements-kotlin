package dev.ai.elements.ui.markdown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.swithun.cmpmermaid.compose.MermaidDiagram as CmpMermaidDiagram
import com.swithun.cmpmermaid.core.GMResult
import com.swithun.cmpmermaid.core.MermaidTheme
import dev.ai.elements.ui.theme.isDark

/**
 * Experimental: Mermaid rendered natively with Compose Canvas by
 * `io.github.swithun-liu:mermaid-compose` (no WebView), themed from the M3
 * color scheme. [onResult] reports render time and errors for evaluation.
 */
@Composable
fun NativeMermaid(
    source: String,
    modifier: Modifier = Modifier,
    onResult: (millis: Long, error: String?) -> Unit = { _, _ -> },
) {
    val colors = MaterialTheme.colorScheme
    val dark = MaterialTheme.isDark
    val theme = remember(colors, dark) {
        val base = if (dark) MermaidTheme.Dark else MermaidTheme.MermaidDefault
        fun Color.hex() = "#%06X".format(toArgb() and 0xFFFFFF)
        val vars = mapOf(
            "background" to colors.surfaceContainerLow.hex(),
            "primaryColor" to colors.primaryContainer.hex(),
            "primaryTextColor" to colors.onPrimaryContainer.hex(),
            "primaryBorderColor" to colors.primary.hex(),
            "secondaryColor" to colors.secondaryContainer.hex(),
            "tertiaryColor" to colors.tertiaryContainer.hex(),
            "lineColor" to colors.outline.hex(),
            "textColor" to colors.onSurface.hex(),
            "mainBkg" to colors.primaryContainer.hex(),
            "nodeBorder" to colors.primary.hex(),
            "clusterBkg" to colors.surfaceContainerHigh.hex(),
            "actorBkg" to colors.primaryContainer.hex(),
            "actorBorder" to colors.primary.hex(),
            "actorTextColor" to colors.onPrimaryContainer.hex(),
            "signalColor" to colors.onSurface.hex(),
            "signalTextColor" to colors.onSurface.hex(),
            "noteBkgColor" to colors.tertiaryContainer.hex(),
            "noteTextColor" to colors.onTertiaryContainer.hex(),
            "edgeLabelBackground" to colors.surfaceContainerLow.hex(),
        )
        when (val r = MermaidTheme.withVariables(base, vars)) {
            is GMResult.Ok -> r.value
            is GMResult.Err -> base
        }
    }
    val started = remember(source, theme) { System.nanoTime() }
    var error by remember(source) { mutableStateOf<String?>(null) }
    // The library's Fit sizing has no intrinsic height (0dp in a Column/LazyColumn),
    // so size the host from the rendered scene's aspect ratio ourselves.
    var aspect by remember { mutableStateOf<Float?>(null) }
    Column(modifier) {
        CmpMermaidDiagram(
            source = source,
            theme = theme,
            modifier = Modifier.fillMaxWidth().then(aspect?.let { Modifier.aspectRatio(it) } ?: Modifier),
            respectSourceViewportSizing = false,
            onError = null,
            onRenderResult = { result ->
                (result as? GMResult.Ok)?.value?.let { scene ->
                    val pad = scene.viewportPadding.coerceAtLeast(0f) * 2
                    if (scene.width > 0f && scene.height > 0f) aspect = ((scene.width + pad) / (scene.height + pad)).coerceIn(0.4f, 6f)
                }
                val ms = (System.nanoTime() - started) / 1_000_000
                error = (result as? GMResult.Err)?.error?.message
                onResult(ms, error)
            },
        )
        error?.let { Text("Native error: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}
