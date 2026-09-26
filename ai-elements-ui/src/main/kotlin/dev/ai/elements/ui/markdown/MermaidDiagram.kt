package dev.ai.elements.ui.markdown

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.ai.elements.ui.theme.compactIconButton
import dev.ai.elements.ui.theme.isDark
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive

/**
 * Renders Mermaid source as a diagram using the bundled, offline `mermaid.js`
 * inside a transparent WebView that sizes itself to the rendered SVG.
 *
 * Mermaid is themed from the current Material color scheme. Tap to open a
 * full-screen, pinch-zoomable viewer. Invalid syntax falls back to the source.
 *
 * @param complete false while the fence is still streaming; shows a placeholder.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MermaidDiagram(
    source: String,
    modifier: Modifier = Modifier,
    complete: Boolean = true,
) {
    val code = source.trim()
    var fullscreen by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("mermaid"),
    ) {
        // No animateContentSize here: inside a lazy list it would report an animating
        // size to the list while the snapshot draws at full size.
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
            ) {
                Icon(Icons.Outlined.AccountTree, null, Modifier.size(16.dp), MaterialTheme.colorScheme.primary)
                Text(
                    "Mermaid",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(
                    onClick = { fullscreen = true },
                    enabled = complete,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.compactIconButton(),
                ) {
                    Icon(Icons.Outlined.OpenInFull, "Open diagram full screen", Modifier.size(16.dp))
                }
            }
            if (!complete) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().height(120.dp).padding(16.dp),
                ) {
                    LoadingIndicator(Modifier.size(40.dp))
                    Text("Drawing diagram…", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                MermaidWebView(
                    code = code,
                    fitWidth = true,
                    onClick = { fullscreen = true },
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                )
            }
        }
    }

    if (fullscreen) {
        Dialog(
            onDismissRequest = { fullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
                Box(Modifier.systemBarsPadding()) {
                    MermaidWebView(code = code, fitWidth = false, modifier = Modifier.fillMaxSize())
                    FilledTonalIconButton(
                        onClick = { fullscreen = false },
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                    ) { Icon(Icons.Outlined.Close, "Close") }
                }
            }
        }
    }
}

@Composable
private fun MermaidWebView(code: String, fitWidth: Boolean, modifier: Modifier, onClick: (() -> Unit)? = null) {
    val dark = MaterialTheme.isDark
    val colors = MaterialTheme.colorScheme
    val cacheKey = "mermaid|$dark|$fitWidth|$code"
    val html = remember(cacheKey) {
        buildHtml(
            code = code,
            dark = dark,
            fitWidth = fitWidth,
            vars = mapOf(
                "primaryColor" to colors.primaryContainer,
                "primaryTextColor" to colors.onPrimaryContainer,
                "primaryBorderColor" to colors.primary,
                "secondaryColor" to colors.secondaryContainer,
                "tertiaryColor" to colors.tertiaryContainer,
                "lineColor" to colors.outline,
                "textColor" to colors.onSurface,
                "mainBkg" to colors.primaryContainer,
                "nodeBorder" to colors.primary,
                "clusterBkg" to colors.surfaceContainerHigh,
                "actorBkg" to colors.primaryContainer,
                "actorBorder" to colors.primary,
                "actorTextColor" to colors.onPrimaryContainer,
                "signalColor" to colors.onSurface,
                "signalTextColor" to colors.onSurface,
                "noteBkgColor" to colors.tertiaryContainer,
                "noteTextColor" to colors.onTertiaryContainer,
                "edgeLabelBackground" to colors.surfaceContainerLow,
            ),
        )
    }
    AutoHeightWebView(
        html = html,
        cacheKey = cacheKey,
        fitWidth = fitWidth,
        modifier = modifier,
        contentDescription = "Mermaid diagram",
        onClick = onClick,
        onClickLabel = "Open diagram",
    ) { message ->
        Column(modifier) {
            Text(
                "Diagram error: ${message.take(160)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            CodeBlock(code, "mermaid")
        }
    }
}

private fun Color.hex() = "#%06X".format(toArgb() and 0xFFFFFF)

private fun buildHtml(code: String, dark: Boolean, fitWidth: Boolean, vars: Map<String, Color>): String {
    val themeVars = vars.entries.joinToString(",") { (k, v) -> "\"$k\":\"${v.hex()}\"" }
    // JSON-encode the source and neutralise any closing script tag inside it.
    val source = JsonPrimitive(code).toString().replace("</", "<\\/")
    val svgCss = if (fitWidth) "max-width:100%;height:auto;" else "width:100%;max-width:none;height:auto;"
    val viewport = if (fitWidth) "width=device-width,initial-scale=1,user-scalable=no"
    else "width=device-width,initial-scale=1,minimum-scale=0.25,maximum-scale=8,user-scalable=yes"
    return """
        <!doctype html><html><head><meta charset="utf-8">
        <meta name="viewport" content="$viewport">
        <style>
          html,body{margin:0;padding:0;background:transparent;${if (fitWidth) "" else "height:100%;"}}
          #c{display:flex;justify-content:center;align-items:center;box-sizing:border-box;
             ${if (fitWidth) "" else "min-height:100%;padding:64px 12px 12px;"}}
          #c svg{$svgCss}
        </style>
        $ERROR_BRIDGE_JS
        <script src="mermaid.min.js"></script>
        <script>$REPORT_HEIGHT_JS</script>
        </head><body><div id="c"></div>
        <script>
          (async function () {
            try {
              mermaid.initialize({
                startOnLoad: false, securityLevel: 'strict', theme: 'base',
                darkMode: $dark, fontFamily: 'Roboto, sans-serif',
                themeVariables: { fontSize: '14px', $themeVars }
              });
              const { svg } = await mermaid.render('d' + Date.now(), $source);
              const c = document.getElementById('c');
              c.innerHTML = svg;
              aiReportHeight(c);
            } catch (e) {
              Bridge.onError(String((e && e.message) || e));
            }
          })();
        </script></body></html>
    """.trimIndent()
}
