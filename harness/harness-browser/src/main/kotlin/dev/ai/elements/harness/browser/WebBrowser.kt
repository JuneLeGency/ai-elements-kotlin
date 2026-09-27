package dev.ai.elements.harness.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.ai.elements.core.agent.AgentTool
import dev.ai.elements.core.agent.Capability
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * A web browser for the agent, with the tools and result texts of the
 * Pydantic AI Harness browser capability (`navigate`, `snapshot`, `click`,
 * `type_text`, `press_key`, `select_option`, `hover`, `wait_for`, `get_text`,
 * `scroll`, `go_back`, `go_forward`), on an off-screen Android [WebView].
 *
 * Selectors are CSS selectors, `aria-ref=eN` handles from `snapshot`, or
 * `x,y` CSS-pixel coordinates. Only `http(s)` pages load; results are capped
 * at [maxContentTokens] (4 characters per token), keeping the head.
 */
class WebBrowser(
    private val context: Context,
    val maxContentTokens: Int = 4_000,
    val actionTimeoutMs: Long = 5_000,
    val navigationTimeoutMs: Long = 60_000,
    private val viewportWidth: Int = 1080,
    private val viewportHeight: Int = 1920,
) : Capability {
    private val lock = Mutex()
    private var webView: WebView? = null
    private var pageLoad: CompletableDeferred<Unit>? = null

    override val instructions: String =
        "You have a web browser: `navigate` to a URL, read it with `get_text` or `snapshot` (interactive elements with " +
            "`aria-ref=eN` handles), and act with `click`, `type_text`, `press_key`, `select_option`, `hover`, `scroll`, `wait_for`, " +
            "`go_back` and `go_forward`. Prefer `aria-ref` handles from the latest snapshot as selectors."

    override suspend fun tools(): List<AgentTool> = listOf(
        tool("navigate", "Navigate to a URL and return the page's title and visible text.", "url" to str("The http(s) URL to open."), "timeout_ms" to int()) { a ->
            navigate(a.s("url")!!, a.i("timeout_ms")?.toLong())
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
        val error = unquote(js("return __aiAct(${q(action)}, ${q(selector)}, ${q(arg)})"))
        if (error.isNotEmpty()) throw IllegalArgumentException(error)
        delay(300) // let the page react (navigation, rendering)
        withTimeoutOrNull(navigationTimeoutMs) { awaitReady() }
        return truncate(result())
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

    /** Evaluate [body] (a function body using `return`) with the helper library loaded; returns the JSON result. */
    private suspend fun js(body: String): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            view().evaluateJavascript("(function(){$HELPERS\n$body})()") { cont.resume(it ?: "null") }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun view(): WebView = webView ?: WebView(context.applicationContext).apply {
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
        override suspend fun execute(arguments: JsonObject) = run(arguments)
    }

    private companion object {
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
  if(action==='click'){el.click();return '';}
  if(action==='hover'){['mouseover','mouseenter','mousemove'].forEach(function(t){el.dispatchEvent(new MouseEvent(t,{bubbles:true}))});return '';}
  if(action==='type'){el.focus();var d=Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el),'value');if(d&&d.set)d.set.call(el,arg);else el.value=arg;
    el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));return '';}
  if(action==='select'){var vals=JSON.parse(arg);var found=0;Array.prototype.forEach.call(el.options||[],function(o){o.selected=vals.indexOf(o.value)>=0||vals.indexOf(o.text)>=0;if(o.selected)found++;});
    if(!found) return 'No option matches '+arg+'.';el.dispatchEvent(new Event('change',{bubbles:true}));return '';}
  if(action==='key'){el.focus&&el.focus();var o={key:arg,bubbles:true};el.dispatchEvent(new KeyboardEvent('keydown',o));el.dispatchEvent(new KeyboardEvent('keyup',o));
    if(arg==='Enter'){var f=el.form||(el.closest&&el.closest('form'));if(f){if(f.requestSubmit)f.requestSubmit();else f.submit();}}return '';}
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
