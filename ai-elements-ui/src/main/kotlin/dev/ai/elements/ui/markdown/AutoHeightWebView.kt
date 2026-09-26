package dev.ai.elements.ui.markdown

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * A transparent WebView that renders a self-contained page (bundled JS such as
 * mermaid or KaTeX) and sizes itself to the content. Pages report back through
 * `Bridge.onRendered(heightCssPx)` / `Bridge.onError(message)`; use
 * [REPORT_HEIGHT_JS] to keep the height in sync.
 *
 * Inline ([fitWidth]) renders are **snapshotted**: once the page has painted,
 * it is captured to a bitmap, the WebView is destroyed, and the bitmap is shown
 * instead. Live WebViews in a scrolling list re-rasterise on every scroll frame
 * and cost tens of MB each; the snapshot scrolls like any image, and recycled
 * list items reuse it from [snapshotCache] without creating a WebView at all.
 *
 * @param fitWidth true for inline use (height follows content, snapshotted);
 *   false for a full-screen, pinch-zoomable live viewer.
 * @param onClick inline only: a transparent layer takes taps and leaves drags
 *   to the surrounding scrolling list.
 * @param failure shown instead when rendering fails or times out (old System
 *   WebViews cannot parse modern bundles at all).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AutoHeightWebView(
    html: String,
    cacheKey: String,
    fitWidth: Boolean,
    modifier: Modifier = Modifier,
    placeholderHeight: Dp = 160.dp,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    failure: @Composable (String) -> Unit,
) {
    BoxWithConstraints(modifier) {
        // Width is part of the key: a rotation or pane resize re-renders at the new width.
        val key = "$cacheKey|${constraints.maxWidth}"
        val cached = if (fitWidth) snapshotCache[key] else null
        var snapshot by remember(key) { mutableStateOf(cached) }
        var state by remember(key) {
            mutableStateOf<RenderState>(heightCache[key]?.let { RenderState.Rendered(it) } ?: RenderState.Loading)
        }
        val webView = remember(key) { arrayOfNulls<WebView>(1) }

        val shot = snapshot
        if (shot != null) {
            Box(Modifier.fillMaxWidth()) {
                Image(
                    bitmap = shot,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onClick != null) Box(Modifier.matchParentSize().clickable(onClickLabel = onClickLabel, onClick = onClick))
            }
            return@BoxWithConstraints
        }

        LaunchedEffect(key) {
            delay(RENDER_TIMEOUT_MS)
            if (state == RenderState.Loading) state = RenderState.Failed("Timed out — this device's WebView may be too old")
        }
        val rendered = state as? RenderState.Rendered
        if (fitWidth && rendered != null) {
            LaunchedEffect(key, rendered) {
                val view = webView[0] ?: return@LaunchedEffect
                repeat(SNAPSHOT_ATTEMPTS) {
                    delay(SNAPSHOT_SETTLE_MS) // let the new height lay out and paint
                    view.awaitVisualState()
                    val bitmap = view.capture()
                    if (bitmap != null) {
                        snapshotCache.put(key, bitmap)
                        snapshot = bitmap
                        return@LaunchedEffect
                    }
                }
            }
        }

        (state as? RenderState.Failed)?.let {
            // A bundle that never defined its global means the WebView could not even parse it.
            val outdated = "is not defined" in it.message || "SyntaxError" in it.message
            failure(if (outdated) "this device's Android System WebView is too old — update it to render" else it.message)
            return@BoxWithConstraints
        }
        Box(
            modifier = if (fitWidth) Modifier.fillMaxWidth().height(rendered?.heightDp?.dp ?: placeholderHeight) else Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { context -> createWebView(context, html, fitWidth, key) { state = it }.also { webView[0] = it } },
                update = { view ->
                    if (view.tag != html) {
                        view.tag = html
                        view.loadDataWithBaseURL(ASSET_BASE, html, "text/html", "utf-8", null)
                    }
                },
                onRelease = { view ->
                    webView[0] = null
                    view.destroy()
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (rendered == null) LoadingIndicator(Modifier.size(40.dp))
            if (onClick != null) {
                Box(Modifier.matchParentSize().clickable(onClickLabel = onClickLabel, onClick = onClick))
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    context: android.content.Context,
    html: String,
    fitWidth: Boolean,
    key: String,
    onState: (RenderState) -> Unit,
): WebView = WebView(context).apply {
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
                    heightCache[key] = height
                    onState(RenderState.Rendered(height))
                }
            }

            @JavascriptInterface
            fun onError(message: String) {
                main.post { onState(RenderState.Failed(message)) }
            }
        },
        "Bridge",
    )
    tag = html
    loadDataWithBaseURL(ASSET_BASE, html, "text/html", "utf-8", null)
}

/** Suspends until the WebView's current DOM state is ready to be drawn. */
private suspend fun WebView.awaitVisualState() = suspendCancellableCoroutine { cont ->
    postVisualStateCallback(
        0,
        object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) {
                if (cont.isActive) cont.resume(Unit)
            }
        },
    )
}

/** Draws the WebView into a bitmap; null if it isn't laid out or painted nothing yet. */
private fun WebView.capture(): ImageBitmap? {
    if (width <= 0 || height <= 0) return null
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    draw(Canvas(bitmap))
    // A snapshot taken before the first paint is fully transparent: retry later.
    val step = maxOf(1, minOf(width, height) / 24)
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            if (bitmap.getPixel(x, y) ushr 24 != 0) return bitmap.asImageBitmap()
            x += step
        }
        y += step
    }
    bitmap.recycle()
    return null
}

/** Snapshots of rendered diagrams and formulas, bounded by bytes. */
private val snapshotCache = object : LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 16).toInt()) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}

private const val SNAPSHOT_ATTEMPTS = 5
private const val SNAPSHOT_SETTLE_MS = 150L

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
