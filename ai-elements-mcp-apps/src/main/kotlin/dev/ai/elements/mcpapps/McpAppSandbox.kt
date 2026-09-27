package dev.ai.elements.mcpapps

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebMessage
import android.webkit.WebMessagePort
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.security.MessageDigest

/**
 * The sandbox of one view (MCP Apps §"Sandbox proxy", §"Security Implications"): a WebView whose
 * page is a proxy served from its own `https` origin with the view's Content-Security-Policy as an
 * HTTP header. The proxy loads the view's HTML into an inner frame (which inherits that policy) and
 * relays JSON-RPC between the frame and the host over a [WebMessagePort] — no JavaScript interface
 * is exposed. The view cannot navigate the page, read files or content URIs, or get permissions.
 *
 * @param origin the sandbox origin's host name, e.g. per MCP server ([originFor]).
 * @param onMessage each JSON-RPC message from the view, on the main thread.
 */
@SuppressLint("SetJavaScriptEnabled")
internal class McpAppSandbox(
    context: Context,
    private val resource: McpAppResource,
    private val origin: String,
    private val onMessage: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var port: WebMessagePort? = null
    private var loaded = false
    private val outbox = mutableListOf<String>()

    val webView: WebView = WebView(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
        with(settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture = true
        }
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (request.url.host != origin) return null // other origins: the network, as the CSP allows
                if (request.url.path.orEmpty().trimEnd('/').isNotEmpty()) return WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), "".byteInputStream())
                return WebResourceResponse(
                    "text/html", "utf-8", 200, "OK",
                    mapOf(
                        "Content-Security-Policy" to resource.csp.header(),
                        "Cache-Control" to "no-store",
                        "Referrer-Policy" to "no-referrer",
                        "X-Content-Type-Options" to "nosniff",
                    ),
                    PROXY_HTML.byteInputStream(),
                )
            }

            // The page stays the proxy: the view opens links through `ui/open-link`.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = request.isForMainFrame

            override fun onPageFinished(view: WebView, url: String) {
                if (loaded || Uri.parse(url).host != origin) return
                loaded = true
                connect(view)
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback) = callback.invoke(origin, false, false)
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                Log.d(TAG, "${resource.uri}: ${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                return true
            }
        }
    }

    /** Load the proxy page. */
    fun load() = webView.loadUrl("https://$origin/")

    /** Send one JSON-RPC message to the view (queued until the proxy connected). */
    fun send(message: String) {
        val p = port
        if (p == null) outbox += message else p.postMessage(WebMessage(message))
    }

    fun destroy() {
        port?.close()
        port = null
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }

    private fun connect(view: WebView) {
        val (local, remote) = view.createWebMessageChannel()
        local.setWebMessageCallback(object : WebMessagePort.WebMessageCallback() {
            override fun onMessage(p: WebMessagePort, message: WebMessage) {
                val data = message.data ?: return
                if (data.contains(SANDBOX_PROXY_READY) && (runCatching { Json.parseToJsonElement(data) as? JsonObject }.getOrNull()?.string("method") == SANDBOX_PROXY_READY)) {
                    if (port == null) {
                        port = p
                        p.postMessage(WebMessage(resourceReady().toString()))
                        outbox.toList().also { outbox.clear() }.forEach { p.postMessage(WebMessage(it)) }
                    }
                    return
                }
                onMessage(data)
            }
        }, main)
        view.postWebMessage(WebMessage(PROXY_HELLO, arrayOf(remote)), Uri.parse("https://$origin"))
    }

    /** `ui/notifications/sandbox-resource-ready`: the view's HTML and policy (no permissions are granted). */
    private fun resourceReady(): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("method", "ui/notifications/sandbox-resource-ready")
        putJsonObject("params") {
            put("html", resource.html)
            put("csp", resource.csp.toJson())
        }
    }

    companion object {
        private const val TAG = "McpApp"
        private const val PROXY_HELLO = "mcp-apps-host"
        private const val SANDBOX_PROXY_READY = "ui/notifications/sandbox-proxy-ready"

        /** A sandbox origin per [key] (e.g. the MCP server's URL): views of one server share storage, no others. */
        fun originFor(key: String): String =
            MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).take(8).joinToString("") { "%02x".format(it) } + ".mcp-app.invalid"

        /**
         * The proxy (the reference host's `sandbox.ts`, adapted to a message port): it takes the port
         * the host posts once, announces itself with `ui/notifications/sandbox-proxy-ready`, writes
         * the view into the inner frame on `ui/notifications/sandbox-resource-ready`, and relays
         * everything else between the port and the frame.
         */
        val PROXY_HTML = """
            <!doctype html>
            <html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta name="color-scheme" content="light dark">
            <style>html,body{margin:0;background:transparent;overflow:hidden}iframe{display:block;border:0;width:100vw;height:100vh;background:transparent;color-scheme:inherit}</style>
            </head><body><script>
            (() => {
              let port = null;
              const inner = document.createElement("iframe");
              inner.setAttribute("sandbox", "allow-scripts allow-same-origin allow-forms");
              document.body.appendChild(inner);
              const allowOf = (p) => ["camera", "microphone", "geolocation"].filter((k) => p && p[k]).concat(p && p.clipboardWrite ? ["clipboard-write"] : []).join("; ");
              window.addEventListener("message", (event) => {
                if (!port && event.data === "$PROXY_HELLO" && event.ports && event.ports.length === 1) {
                  port = event.ports[0];
                  port.onmessage = (e) => {
                    let msg;
                    try { msg = JSON.parse(e.data); } catch (_) { return; }
                    if (msg && msg.method === "ui/notifications/sandbox-resource-ready") {
                      const p = msg.params || {};
                      if (typeof p.sandbox === "string") inner.setAttribute("sandbox", p.sandbox);
                      const allow = allowOf(p.permissions);
                      if (allow) inner.setAttribute("allow", allow);
                      const doc = inner.contentDocument;
                      doc.open(); doc.write(p.html || ""); doc.close();
                    } else if (inner.contentWindow) {
                      inner.contentWindow.postMessage(msg, "*");
                    }
                  };
                  port.postMessage(JSON.stringify({ jsonrpc: "2.0", method: "$SANDBOX_PROXY_READY", params: {} }));
                  return;
                }
                if (port && event.source === inner.contentWindow && event.origin === location.origin) {
                  const m = event.data;
                  if (m && typeof m === "object" && typeof m.method === "string" && m.method.startsWith("ui/notifications/sandbox-")) return;
                  port.postMessage(JSON.stringify(m));
                }
              });
            })();
            </script></body></html>
        """.trimIndent()
    }
}
