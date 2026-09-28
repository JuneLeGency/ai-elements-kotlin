package dev.ai.elements.harness.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Base64
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.ai.elements.core.agent.AgentTool
import dev.ai.elements.core.agent.Capability
import dev.ai.elements.core.agent.ToolCallContext
import dev.ai.elements.core.model.ToolCategory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 * A web browser for the agent, with the tools and result texts of the
 * Pydantic AI Harness browser capability (`navigate`, `snapshot`, `click`,
 * `type_text`, `press_key`, `select_option`, `hover`, `wait_for`, `get_text`,
 * `scroll`, `go_back`, `go_forward`), on an off-screen Android [WebView].
 *
 * Selectors are CSS selectors, `aria-ref=eN` handles from `snapshot`, or
 * `x,y` CSS-pixel coordinates. Only `http(s)` pages load; results are capped
 * at [maxContentTokens] (4 characters per token), keeping the head.
 *
 * `screenshot(full_page?)` returns the page as a PNG for vision models (Pydantic AI
 * `ToolReturn.content`, as the Harness browser does), and [screenshotOnNavigate] adds one to every
 * `navigate` result; both are capped at 5 MB like the Harness'. With [screenshots], every call that
 * changes the page (navigate, click, type, keys, scroll, back, forward) also attaches a smaller
 * screenshot for the chat's steps view, with the element the call acted on outlined, as agent
 * computer views do; the model does not get those.
 */
class WebBrowser(
    private val context: Context,
    val maxContentTokens: Int = 4_000,
    val actionTimeoutMs: Long = 5_000,
    val navigationTimeoutMs: Long = 60_000,
    /** The page size the browser presents, in CSS pixels; screenshots have this size. */
    val viewport: BrowserViewport = BrowserViewport.Desktop,
    val screenshots: Boolean = true,
    val screenshotOnNavigate: Boolean = false,
) : Capability {
    private val pixelsPerCss = context.resources.displayMetrics.density

    /** The off-screen view's size in device pixels, so pages see [viewport] in CSS pixels. */
    private val viewportWidth = (viewport.width * pixelsPerCss).toInt()
    private val viewportHeight = (viewport.height * pixelsPerCss).toInt()

    private val lock = Mutex()
    private var webView: WebView? = null
    private var pageLoad: CompletableDeferred<Unit>? = null

    override val instructions: String =
        "You have a web browser: `navigate` to a URL, read it with `get_text` or `snapshot` (interactive elements with " +
            "`aria-ref=eN` handles), and act with `click`, `type_text`, `press_key`, `select_option`, `hover`, `scroll`, `wait_for`, " +
            "`go_back` and `go_forward`. Prefer `aria-ref` handles from the latest snapshot as selectors."

    override suspend fun tools(): List<AgentTool> = listOf(
        tool("navigate", "Navigate to a URL and return the page's title and visible text.", "url" to str("The http(s) URL to open."), "timeout_ms" to int()) { a ->
            navigate(a.s("url")!!, a.i("timeout_ms")?.toLong()).also { result ->
                if (screenshotOnNavigate && !result.startsWith("Error:")) capture(fullPage = false)?.let { returnImage(it) }
            }
        },
        tool("screenshot", "Capture a screenshot of the current page (the viewport, or the whole page with `full_page`), returned as an image. Use it only for visual checks (charts, layout); read pages with `snapshot` or `get_text`.", "full_page" to bool("Capture the full scrollable page instead of the viewport."), "timeout_ms" to int()) { a ->
            screenshot(fullPage = (a["full_page"] as? JsonPrimitive)?.booleanOrNull ?: false)
        },
        tool("snapshot", "Return the page's interactive elements and headings with `aria-ref` handles to use as selectors.", "timeout_ms" to int()) { snapshot() },
        tool("click", "Click an element; `selector` is a CSS selector, an `aria-ref=` handle, or 'x,y' pixel coordinates.", "selector" to str("Element to click."), "timeout_ms" to int(), required = listOf("selector")) { a ->
            val selector = a.s("selector")!!
            act("click", selector) { "Clicked '$selector'. URL: ${url()}\n\n${pageText()}" }
        },
        tool("type_text", "Type into an input (replaces its value; does not submit).", "selector" to str("The input."), "text" to str("Text to type."), "sequential" to bool("Send key events per character."), "timeout_ms" to int(), required = listOf("selector", "text")) { a ->
            val selector = a.s("selector")!!
            act("type", selector, a.s("text")!!) { "Typed into '$selector'.\n\n${pageText()}" }
        },
        tool("press_key", "Press a key (Enter, Escape, Tab, ArrowDown…), optionally on an element.", "key" to str("Key name."), "selector" to str("Element to focus first."), "timeout_ms" to int(), required = listOf("key")) { a ->
            val key = a.s("key")!!
            act("key", a.s("selector") ?: "", key) { "Pressed '$key'.\n\n${pageText()}" }
        },
        tool("select_option", "Choose options in a <select> by value or label.", "selector" to str("The <select>."), "values" to buildJsonObject { put("type", "array"); put("items", buildJsonObject { put("type", "string") }) }, "timeout_ms" to int(), required = listOf("selector", "values")) { a ->
            val selector = a.s("selector")!!
            val values = (a["values"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
            act("select", selector, Json.encodeToString(JsonArray.serializer(), JsonArray(values.map(::JsonPrimitive)))) { "Selected ${values.joinToString()} in '$selector'.\n\n${pageText()}" }
        },
        tool("hover", "Hover an element, revealing hover-only menus.", "selector" to str("Element to hover."), "timeout_ms" to int(), required = listOf("selector")) { a ->
            val selector = a.s("selector")!!
            act("hover", selector) { "Hovered '$selector'.\n\n${pageText()}" }
        },
        tool("wait_for", "Wait until an element or text appears -- or, with gone=true, disappears. Pass exactly one of selector/text.", "selector" to str("CSS selector."), "text" to str("Visible text."), "gone" to bool("Wait for it to disappear."), "timeout_ms" to int()) { a ->
            waitFor(a.s("selector"), a.s("text"), (a["gone"] as? JsonPrimitive)?.booleanOrNull ?: false, a.i("timeout_ms")?.toLong() ?: actionTimeoutMs)
        },
        tool("get_text", "Return an element's text, or the whole page's visible text.", "selector" to str("Optional element."), "timeout_ms" to int()) { a ->
            val selector = a.s("selector")
            if (selector == null) pageText() else js("return __aiEl(${q(selector)})?.innerText ?? null").takeIf { it != "null" }?.let(::unquote)
                ?: throw IllegalArgumentException("No element matches '$selector'.")
        },
        tool("scroll", "Scroll one screen up/down/left/right, or to the top/bottom.", "direction" to buildJsonObject { put("type", "string"); put("enum", JsonArray(listOf("up", "down", "left", "right", "top", "bottom").map(::JsonPrimitive))) }, "timeout_ms" to int(), required = listOf("direction")) { a ->
            val direction = a.s("direction")!!
            js("""const h=innerHeight,w=innerWidth;({up:()=>scrollBy(0,-h),down:()=>scrollBy(0,h),left:()=>scrollBy(-w,0),right:()=>scrollBy(w,0),top:()=>scrollTo(0,0),bottom:()=>scrollTo(0,document.body.scrollHeight)})[${q(direction)}]();return 1""")
            "Scrolled $direction.\n\n${pageText()}"
        },
        tool("go_back", "Go back to the previous page.", "timeout_ms" to int()) {
            history(back = true)
            "Went back. URL: ${url()}\n\n${pageText()}"
        },
        tool("go_forward", "Go forward to the next page.", "timeout_ms" to int()) {
            history(back = false)
            "Went forward. URL: ${url()}\n\n${pageText()}"
        },
    )

    suspend fun navigate(url: String, timeoutMs: Long? = null): String = lock.withLock {
        val scheme = url.substringBefore(':').lowercase()
        require(scheme == "http" || scheme == "https") { "Only http(s) URLs can be opened: $url" }
        val load = CompletableDeferred<Unit>()
        withContext(Dispatchers.Main) {
            pageLoad = load
            view().loadUrl(url)
        }
        withTimeoutOrNull(timeoutMs ?: navigationTimeoutMs) { load.await() } ?: return@withLock "Error: navigation timed out: $url"
        val title = withContext(Dispatchers.Main) { view().title.orEmpty() }
        truncate("URL: ${url()}\nTitle: $title\n\n${pageTextUnlocked()}")
    }

    /** Stop and free the WebView. */
    suspend fun close() = withContext(Dispatchers.Main) { webView?.destroy(); webView = null }

    // --- Page scripting -------------------------------------------------------------------------

    private suspend fun snapshot(): String = truncate(unquote(js(SNAPSHOT_JS)))

    private suspend fun act(action: String, selector: String, arg: String = "", result: suspend () -> String): String {
        val before = pageLoad
        val error = unquote(js("return __aiAct(${q(action)}, ${q(selector)}, ${q(arg)})"))
        if (error.isNotEmpty()) throw IllegalArgumentException(error)
        // As Playwright's auto-waiting: an action that starts a navigation waits for the new page.
        val navigating = withTimeoutOrNull(NAVIGATION_START_MS) {
            while (pageLoad === before) delay(50)
            true
        } == true
        if (!navigating) delay(300) // let the page react (rendering)
        withTimeoutOrNull(navigationTimeoutMs) { awaitReady() }
        // The acted-on element's box, still set when the page stayed (a new page has a new window).
        target = runCatching {
            Json.parseToJsonElement(js("var t=window.__aiTarget||null;delete window.__aiTarget;return t")) as? JsonArray
        }.getOrNull()?.map { it.jsonPrimitive.content.toFloat() }?.takeIf { it.size == 5 }?.let { (x, y, w, h, dpr) ->
            android.graphics.RectF(x * dpr, y * dpr, (x + w) * dpr, (y + h) * dpr)
        }
        return truncate(result())
    }

    /** The element the last action targeted, in view pixels; outlined on the steps view's screenshot. */
    private var target: android.graphics.RectF? = null

    /** Harness `screenshot`: a note with the URL, and the PNG for the model (or a bounded error). */
    private suspend fun screenshot(fullPage: Boolean): String {
        val png = capture(fullPage) ?: return "Error: the page could not be captured."
        if (png.size > MAX_SCREENSHOT_BYTES) {
            return "Error: screenshot is ${png.size} bytes, over the $MAX_SCREENSHOT_BYTES byte image limit; " +
                "capture the viewport (full_page=False) or scroll and capture sections instead."
        }
        returnImage(png)
        return truncate("Screenshot captured. URL: ${url()}")
    }

    /** Returns [png] to the model (Pydantic AI `ToolReturn.content`); the chat shows it with the call. */
    private suspend fun returnImage(png: ByteArray) {
        if (png.size > MAX_SCREENSHOT_BYTES) return
        ToolCallContext.current()?.content("image/png", "data:image/png;base64," + Base64.encodeToString(png, Base64.NO_WRAP))
    }

    /**
     * The page as a PNG at the viewport's resolution: the viewport, or with [fullPage] the whole
     * document (the off-screen view is laid out at the document's height, up to [MAX_FULL_PAGE_SCREENS]
     * screens, then back).
     */
    private suspend fun capture(fullPage: Boolean): ByteArray? = runCatching {
        awaitVisualState()
        withContext(Dispatchers.Main) {
            val view = webView ?: return@withContext null
            val height = if (!fullPage) viewportHeight else {
                val content = (view.contentHeight * pixelsPerCss).toInt()
                content.coerceIn(viewportHeight, viewportHeight * MAX_FULL_PAGE_SCREENS)
            }
            val scrollY = view.scrollY
            if (height != viewportHeight) {
                view.measure(View.MeasureSpec.makeMeasureSpec(viewportWidth, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, viewportWidth, height)
                view.scrollTo(0, 0)
            }
            try {
                // At CSS-pixel resolution, as Playwright's default: `x,y` in the image are the page's coordinates.
                Bitmap.createBitmap(viewport.width, (height / pixelsPerCss).toInt(), Bitmap.Config.ARGB_8888).also {
                    Canvas(it).apply { scale(1 / pixelsPerCss, 1 / pixelsPerCss); drawPage(view) }
                }
            } finally {
                if (height != viewportHeight) {
                    view.measure(View.MeasureSpec.makeMeasureSpec(viewportWidth, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(viewportHeight, View.MeasureSpec.EXACTLY))
                    view.layout(0, 0, viewportWidth, viewportHeight)
                    view.scrollTo(0, scrollY)
                }
            }
        }?.let { bitmap ->
            withContext(Dispatchers.Default) { ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray() }
        }
    }.getOrNull()

    /** Until the page's current DOM has been rendered (WebView.VisualStateCallback), at most a moment. */
    private suspend fun awaitVisualState() = withContext(Dispatchers.Main) {
        val view = webView ?: return@withContext
        withTimeoutOrNull(VISUAL_STATE_TIMEOUT_MS) {
            suspendCancellableCoroutine { done ->
                view.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        if (done.isActive) done.resume(Unit)
                    }
                })
            }
        }
    }

    private suspend fun waitFor(selector: String?, text: String?, gone: Boolean, timeoutMs: Long): String {
        require((selector == null) != (text == null)) { "Pass exactly one of selector/text." }
        val probe = if (selector != null) "!!__aiEl(${q(selector)})" else "document.body.innerText.includes(${q(text!!)})"
        val label = selector ?: text!!
        val ok = withTimeoutOrNull(timeoutMs) {
            while (js("return $probe") != (!gone).toString()) delay(150)
            true
        } ?: throw IllegalStateException("Timed out after ${timeoutMs}ms waiting for '$label'${if (gone) " to disappear" else ""}.")
        check(ok)
        return truncate("${if (gone) "Gone" else "Found"} '$label'.\n\n${pageText()}")
    }

    private suspend fun history(back: Boolean) {
        val before = url()
        js(if (back) "history.back(); return 1" else "history.forward(); return 1")
        // history.back() is asynchronous: wait for the URL to change and the page to load.
        val moved = withTimeoutOrNull(navigationTimeoutMs) {
            while (url() == before) delay(100)
            awaitReady()
            true
        }
        check(moved == true) { if (back) "There is no previous page." else "There is no next page." }
    }

    /** Until the current document has loaded (after a navigation the old one may still be showing). */
    private suspend fun awaitReady() {
        pageLoad?.await()
        while (unquote(js("return document.readyState")) != "complete") delay(100)
    }

    private suspend fun pageText(): String = pageTextUnlocked()

    private suspend fun pageTextUnlocked(): String = unquote(js("return document.body ? document.body.innerText : ''"))

    private suspend fun url(): String = withContext(Dispatchers.Main) { view().url.orEmpty() }

    /**
     * Evaluate [body] (a function body using `return`) with the helper library loaded; returns the
     * JSON result, or `null` if the page gave no answer within [actionTimeoutMs] (a script whose page
     * unloads is never answered).
     */
    private suspend fun js(body: String): String = withTimeoutOrNull(actionTimeoutMs) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                view().evaluateJavascript("(function(){$HELPERS\n$body})()") { if (cont.isActive) cont.resume(it ?: "null") }
            }
        }
    } ?: "null"

    @SuppressLint("SetJavaScriptEnabled")
    private fun view(): WebView = webView ?: WebView(context.applicationContext).apply {
        if (viewport.desktop) {
            // Sites serve their desktop layout, as Chrome's "Desktop site" asks for it; `device-width` is the viewport.
            settings.userAgentString = desktopUserAgent(settings.userAgentString)
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = false
        }
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme?.lowercase()
                return scheme != "http" && scheme != "https"
            }
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (pageLoad?.isCompleted != false) pageLoad = CompletableDeferred()
            }
            override fun onPageFinished(view: WebView, url: String?) { pageLoad?.complete(Unit) }
        }
        // Off-screen, a hardware-rendered WebView never draws: render in software so screenshots work.
        if (screenshots) setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        // Lay it out off-screen so pages see a real viewport.
        measure(View.MeasureSpec.makeMeasureSpec(viewportWidth, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(viewportHeight, View.MeasureSpec.EXACTLY))
        layout(0, 0, viewportWidth, viewportHeight)
        webView = this
    }

    private fun truncate(text: String): String {
        val max = maxContentTokens * 4
        if (text.length <= max) return text
        val marker = "\n[... tool output truncated at $max characters]"
        return text.take(max - marker.length) + marker
    }

    private fun tool(name: String, description: String, vararg params: Pair<String, JsonObject>, required: List<String> = emptyList(), run: suspend (JsonObject) -> String) = object : AgentTool {
        override val name = name
        override val description = description
        override val parameters = buildJsonObject {
            put("type", "object")
            put("properties", JsonObject(params.toMap()))
            put("required", JsonArray((if (required.isEmpty() && params.firstOrNull()?.first in setOf("url", "direction", "key")) listOf(params.first().first) else required).map(::JsonPrimitive)))
        }
        override fun titleFor(arguments: JsonObject) = arguments.s("url") ?: arguments.s("selector") ?: arguments.s("text")
        override fun categoryFor(arguments: JsonObject) = if (name in FETCHING) ToolCategory.FETCH else ToolCategory.OTHER
        override fun locationFor(arguments: JsonObject) = arguments.s("url")
        override suspend fun execute(arguments: JsonObject) = run(arguments).also { if (name in PAGE_CHANGING) attachScreenshot() }
    }

    /** The viewport as a JPEG on the current call (see [screenshots]); failures never fail the call. */
    private suspend fun attachScreenshot() {
        if (!screenshots) return
        val call = ToolCallContext.current() ?: return
        val jpeg = runCatching {
            awaitVisualState()
            val outline = target.also { target = null }
            withContext(Dispatchers.Main) {
                val view = webView ?: return@withContext null
                val scale = viewport.width.coerceAtMost(SCREENSHOT_WIDTH).toFloat() / viewportWidth
                val bitmap = Bitmap.createBitmap((viewportWidth * scale).toInt(), (viewportHeight * scale).toInt(), Bitmap.Config.ARGB_8888)
                Canvas(bitmap).apply {
                    scale(scale, scale)
                    drawPage(view)
                    outline?.let { drawTarget(it, 1 / scale) }
                }
                bitmap
            }?.let { bitmap ->
                withContext(Dispatchers.Default) {
                    ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 70, it) }.toByteArray()
                }
            }
        }.getOrNull() ?: return
        call.file("image/jpeg", "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP))
    }

    /** The page as the view shows it: a parent draws a scrolled view shifted by its scroll offset. */
    private fun Canvas.drawPage(view: WebView) {
        val saved = save()
        translate(-view.scrollX.toFloat(), -view.scrollY.toFloat())
        view.draw(this)
        restoreToCount(saved)
    }

    /** Outlines the element an action targeted, the way agent computer views mark where the agent acted. */
    private fun Canvas.drawTarget(box: android.graphics.RectF, px: Float) {
        val pad = 4 * px
        val rect = android.graphics.RectF(box.left - pad, box.top - pad, box.right + pad, box.bottom + pad)
        val radius = 8 * px
        drawRoundRect(rect, radius, radius, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = TARGET_COLOR; alpha = 48 })
        drawRoundRect(rect, radius, radius, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = TARGET_COLOR; style = android.graphics.Paint.Style.STROKE; strokeWidth = 4 * px })
    }

    internal companion object {
        /**
         * The user agent of Chrome's "Desktop site" mode (Linux desktop Chrome, reduced UA format) with
         * this WebView's Chrome major version.
         */
        fun desktopUserAgent(mobile: String): String {
            val major = Regex("Chrome/(\\d+)").find(mobile)?.groupValues?.get(1) ?: "130"
            return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 Safari/537.36"
        }

        /** Widest screenshot for the steps view (they are shown small, and opened full screen). */
        const val SCREENSHOT_WIDTH = 960
        const val TARGET_COLOR = 0xFFFF5722.toInt()
        const val VISUAL_STATE_TIMEOUT_MS = 2_000L

        /** How long an action may take to start the navigation it causes (a link, a submit). */
        const val NAVIGATION_START_MS = 1_000L

        /** Largest screenshot returned to the model: the strictest mainstream per-image limit (as the Harness). */
        const val MAX_SCREENSHOT_BYTES = 5_000_000
        const val MAX_FULL_PAGE_SCREENS = 10
        /** Calls that retrieve a page or its content (ACP `fetch`); the others act on the page. */
        val FETCHING = setOf("navigate", "go_back", "go_forward", "snapshot", "get_text", "screenshot")

        val PAGE_CHANGING = setOf("navigate", "click", "type_text", "press_key", "select_option", "scroll", "go_back", "go_forward")

        fun str(description: String) = buildJsonObject { put("type", "string"); put("description", description) }
        fun bool(description: String) = buildJsonObject { put("type", "boolean"); put("description", description) }
        fun int() = buildJsonObject { put("type", "integer"); put("description", "Timeout in milliseconds.") }
        fun JsonObject.s(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
        fun JsonObject.i(key: String) = (this[key] as? JsonPrimitive)?.intOrNull
        fun q(value: String) = JsonPrimitive(value).toString()
        fun unquote(json: String): String = runCatching { Json.parseToJsonElement(json).jsonPrimitive.contentOrNull }.getOrNull() ?: ""

        /** Element lookup (CSS, aria-ref, x,y) and actions, injected with every script. */
        const val HELPERS = """
function __aiEl(sel){
  if(sel.startsWith('aria-ref=')) return document.querySelector('[data-ai-ref="'+sel.slice(9)+'"]');
  var m=sel.match(/^\s*(\d+(?:\.\d+)?)\s*,\s*(\d+(?:\.\d+)?)\s*$/); if(m) return document.elementFromPoint(+m[1],+m[2]);
  try{return document.querySelector(sel);}catch(e){return null;}
}
function __aiAct(action,sel,arg){
  var el=sel?__aiEl(sel):document.activeElement||document.body;
  if(!el) return "No element matches '"+sel+"'.";
  el.scrollIntoView({block:'center'});
  var r=el.getBoundingClientRect();window.__aiTarget=[r.left,r.top,r.width,r.height,devicePixelRatio];
  // Actions that may navigate run after this script returns: a page that unloads while a script runs
  // never answers evaluateJavascript.
  if(action==='click'){setTimeout(function(){el.click()},0);return '';}
  if(action==='hover'){['mouseover','mouseenter','mousemove'].forEach(function(t){el.dispatchEvent(new MouseEvent(t,{bubbles:true}))});return '';}
  if(action==='type'){el.focus();var d=Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el),'value');if(d&&d.set)d.set.call(el,arg);else el.value=arg;
    setTimeout(function(){el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}))},0);return '';}
  if(action==='select'){var vals=JSON.parse(arg);var found=0;Array.prototype.forEach.call(el.options||[],function(o){o.selected=vals.indexOf(o.value)>=0||vals.indexOf(o.text)>=0;if(o.selected)found++;});
    if(!found) return 'No option matches '+arg+'.';setTimeout(function(){el.dispatchEvent(new Event('change',{bubbles:true}))},0);return '';}
  if(action==='key'){el.focus&&el.focus();setTimeout(function(){var o={key:arg,bubbles:true};el.dispatchEvent(new KeyboardEvent('keydown',o));el.dispatchEvent(new KeyboardEvent('keyup',o));
    if(arg==='Enter'){var f=el.form||(el.closest&&el.closest('form'));if(f){if(f.requestSubmit)f.requestSubmit();else f.submit();}}},0);return '';}
  return 'Unknown action '+action;
}
"""

        /** Headings, links and form controls with `aria-ref` handles, one per line. */
        const val SNAPSHOT_JS = """
var n=0,out=['- document "'+document.title+'"'];
document.querySelectorAll('h1,h2,h3,a[href],button,input,textarea,select,[role=button],[role=link],[role=checkbox],[role=tab],[role=menuitem]').forEach(function(el){
  var r=el.getBoundingClientRect(); if(!r.width&&!r.height) return;
  var ref='e'+(++n); el.setAttribute('data-ai-ref',ref);
  var tag=el.tagName.toLowerCase(), role=el.getAttribute('role')||({a:'link',button:'button',select:'combobox',textarea:'textbox',h1:'heading',h2:'heading',h3:'heading'}[tag])||(tag==='input'?((el.type==='checkbox'||el.type==='radio')?el.type:(el.type==='submit'?'button':'textbox')):tag);
  var name=(el.getAttribute('aria-label')||el.innerText||el.value||el.placeholder||el.name||'').trim().replace(/\s+/g,' ').slice(0,80);
  out.push('- '+role+' "'+name+'" [ref='+ref+']'+(role==='heading'?' [level='+tag.slice(1)+']':'')+(el.checked?' [checked]':''));
});
return out.join('\n');
"""
    }
}

/**
 * The page size a [WebBrowser] presents, in CSS pixels, and whether it asks sites for their desktop
 * layout. [Desktop] is Playwright's default viewport (as the Pydantic AI Harness browser uses);
 * [Mobile] a typical phone.
 */
data class BrowserViewport(val width: Int, val height: Int, val desktop: Boolean) {
    companion object {
        val Desktop = BrowserViewport(1280, 720, desktop = true)
        val Mobile = BrowserViewport(412, 915, desktop = false)
    }
}
