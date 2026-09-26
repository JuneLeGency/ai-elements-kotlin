package dev.ai.elements.ui.markdown

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

/**
 * A transparent WebView that renders a self-contained page (bundled JS such as
 * mermaid or KaTeX) and sizes itself to the content. Pages report back through
 * `Bridge.onRendered(heightCssPx)` / `Bridge.onError(message)`; use
 * [REPORT_HEIGHT_JS] to keep the height in sync.
 *
 * @param fitWidth true for inline use (height follows content, no zoom);
 *   false for a full-screen, pinch-zoomable viewer.
 * @param onClick inline only: a transparent layer takes taps and leaves drags
 *   to the surrounding scrolling list.
 * @param failure shown instead when rendering fails or times out (old System
 *   WebViews cannot parse modern bundles at all).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun AutoHeightWebView(
    html: String,
    cacheKey: String,
    fitWidth: Boolean,
    modifier: Modifier = Modifier,
    placeholderHeight: Dp = 160.dp,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    failure: @Composable (String) -> Unit,
) {
    var state by remember(cacheKey) {
        mutableStateOf<RenderState>(heightCache[cacheKey]?.let { RenderState.Rendered(it) } ?: RenderState.Loading)
    }
    LaunchedEffect(cacheKey) {
        delay(RENDER_TIMEOUT_MS)
        if (state == RenderState.Loading) state = RenderState.Failed("Timed out — this device's WebView may be too old")
    }

    (state as? RenderState.Failed)?.let {
        // A bundle that never defined its global means the WebView could not even parse it.
        val outdated = "is not defined" in it.message || "SyntaxError" in it.message
        failure(if (outdated) "this device's Android System WebView is too old — update it to render" else it.message)
        return
    }
    val rendered = state as? RenderState.Rendered
    Box(
        modifier = if (fitWidth) modifier.height(rendered?.heightDp?.dp ?: placeholderHeight) else modifier,
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    settings.javaScriptEnabled = true
                    settings.setSupportZoom(!fitWidth)
                    settings.builtInZoomControls = !fitWidth
                    settings.displayZoomControls = false
                    isVerticalScrollBarEnabled = !fitWidth
                    isHorizontalScrollBarEnabled = !fitWidth
                    val main = Handler(Looper.getMainLooper())
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onRendered(height: Float) {
                                main.post {
                                    heightCache[cacheKey] = height
                                    state = RenderState.Rendered(height)
                                }
                            }

                            @JavascriptInterface
                            fun onError(message: String) {
                                main.post { state = RenderState.Failed(message) }
                            }
                        },
                        "Bridge",
                    )
                    tag = html
                    loadDataWithBaseURL(ASSET_BASE, html, "text/html", "utf-8", null)
                }
            },
            update = { view ->
                if (view.tag != html) {
                    view.tag = html
                    view.loadDataWithBaseURL(ASSET_BASE, html, "text/html", "utf-8", null)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (rendered == null) LoadingIndicator(Modifier.size(40.dp))
        if (onClick != null) {
            Box(Modifier.matchParentSize().clickable(onClickLabel = onClickLabel, onClick = onClick))
        }
    }
}

private sealed interface RenderState {
    data object Loading : RenderState
    data class Rendered(val heightDp: Float) : RenderState
    data class Failed(val message: String) : RenderState
}

/** Last measured heights, so recycled list items don't jump while re-rendering. */
private val heightCache = object : LinkedHashMap<String, Float>(32, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Float>?) = size > 128
}

private const val ASSET_BASE = "file:///android_asset/ai-elements/"
private const val RENDER_TIMEOUT_MS = 12_000L

/** Routes script errors (e.g. a bundle the WebView cannot parse) to the bridge; put it first in `<head>`. */
internal const val ERROR_BRIDGE_JS = "<script>window.onerror = function (m) { Bridge.onError(String(m)); };</script>"

/**
 * `aiReportHeight(el)`: report el's height whenever layout settles. The WebView
 * may start at width 0 inside lazy layouts, so it keeps observing.
 */
internal const val REPORT_HEIGHT_JS = """
function aiReportHeight(el) {
  var last = -1;
  var report = function () {
    var r = el.getBoundingClientRect();
    var h = Math.ceil(r.height) + 4;
    if (r.width > 0 && h > 8 && h !== last) { last = h; Bridge.onRendered(h); }
  };
  new ResizeObserver(report).observe(el);
  requestAnimationFrame(report);
}
"""
