package dev.ai.elements.ui.markdown

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.viewinterop.AndroidView
import dev.ai.elements.ui.R
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

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
        val timedOut = stringResource(R.string.ai_webview_timeout)
        val outdatedWebView = stringResource(R.string.ai_webview_outdated)
        val cached = if (fitWidth) snapshotCache[key] else null
        var snapshot by remember(key) { mutableStateOf(cached) }
        var state by remember(key) {
            mutableStateOf<RenderState>(heightCache[key]?.let { RenderState.Rendered(it) } ?: RenderState.Loading)
        }
        val webView = remember(key) { arrayOfNulls<WebView>(1) }
        // Window bounds of the live WebView, clipped by every ancestor (the list viewport, panes).
        val host = remember(key) { arrayOfNulls<LayoutCoordinates>(1) }

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
            if (state == RenderState.Loading) state = RenderState.Failed(timedOut)
        }
        val rendered = state as? RenderState.Rendered
        if (fitWidth && rendered != null) {
            LaunchedEffect(key, rendered) {
                val view = webView[0] ?: return@LaunchedEffect
                // Until it is captured (e.g. while scrolled partly out of view) the live WebView stays.
                // Frames around a resize (placeholder → measured height) can be drawn at a stale
                // scale, so only two identical consecutive captures count as the settled render.
                var previous: ImageBitmap? = null
                repeat(SNAPSHOT_ATTEMPTS) {
                    delay(SNAPSHOT_SETTLE_MS) // let the new height lay out and paint
                    view.awaitVisualState()
                    val captured = if (Build.VERSION.SDK_INT >= 26) {
                        host[0]?.fullyVisibleRectInWindow()?.let { view.pixelCopy(it) }
                    } else {
                        view.capture()
                    }
                    val settled = previous?.takeIf { captured != null && it.asAndroidBitmap().sameAs(captured.asAndroidBitmap()) }
                    previous = captured
                    val bitmap = settled
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
            failure(if (outdated) outdatedWebView else it.message)
            return@BoxWithConstraints
        }
        Box(
            modifier = (if (fitWidth) Modifier.fillMaxWidth().height(rendered?.heightDp?.dp ?: placeholderHeight) else Modifier.fillMaxSize())
                .onGloballyPositioned { host[0] = it },
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { context -> createWebView(context, html, fitWidth, key) { state = it }.also { webView[0] = it } },
                update = { view ->
                    if (view.tag != html) {
                        view.tag = html
                        view.loadWhenSized(html)
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
    context: Context,
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
    loadWhenSized(html)
}

/**
 * Loads [html] once the view has a real size. A WebView created inside a lazy
 * layout is first laid out at 0×0; a page that loads (and lays out) at zero
 * width can keep a stale page scale once the view is resized, which showed as
 * an enlarged corner of the diagram in the inline card, intermittently.
 */
private fun WebView.loadWhenSized(html: String) {
    if (width > 0 && height > 0) {
        loadDataWithBaseURL(ASSET_BASE, html, "text/html", "utf-8", null)
        return
    }
    addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
        override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
            if (r - l <= 0 || b - t <= 0) return
            v.removeOnLayoutChangeListener(this)
            if (v.tag == html) (v as WebView).loadDataWithBaseURL(ASSET_BASE, html, "text/html", "utf-8", null)
        }
    })
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

/**
 * The window rect of [this] if no ancestor clips any of it (a list viewport,
 * a pane, the screen edge), else null. PixelCopy reads the composited window,
 * so a partly hidden view would capture whatever covers it.
 */
private fun LayoutCoordinates.fullyVisibleRectInWindow(): Rect? {
    if (!isAttached) return null
    val visible = boundsInWindow()
    val whole = Rect(positionInWindow(), size.toSize())
    val fully = visible.width >= whole.width - 1f && visible.height >= whole.height - 1f && !visible.isEmpty
    return if (fully) visible else null
}

/**
 * Copies the WebView's on-screen pixels (Android 8+). Unlike [capture], this
 * doesn't depend on the WebView's software-draw path, which on some ROMs
 * (seen on MIUI with WebView 153) renders at the wrong scale — an enlarged
 * corner of the diagram instead of the whole thing.
 */
@RequiresApi(26)
private suspend fun WebView.pixelCopy(rect: Rect): ImageBitmap? {
    val window = context.findActivity()?.window ?: return null
    delay(2 * FRAME_MS) // let the painted DOM reach the screen
    val src = android.graphics.Rect(rect.left.roundToInt(), rect.top.roundToInt(), rect.right.roundToInt(), rect.bottom.roundToInt())
    if (src.width() <= 0 || src.height() <= 0) return null
    val bitmap = Bitmap.createBitmap(src.width(), src.height(), Bitmap.Config.ARGB_8888)
    return suspendCancellableCoroutine { cont ->
        PixelCopy.request(
            window, src, bitmap,
            { result -> if (cont.isActive) cont.resume(if (result == PixelCopy.SUCCESS) bitmap.asImageBitmap() else null) },
            Handler(Looper.getMainLooper()),
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Draws the WebView into a bitmap (Android 7); null if it isn't laid out or painted nothing yet. */
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

/** ~6s of retries: covers a diagram that renders while scrolled partly out of view. */
private const val SNAPSHOT_ATTEMPTS = 40
private const val SNAPSHOT_SETTLE_MS = 150L
private const val FRAME_MS = 17L

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
