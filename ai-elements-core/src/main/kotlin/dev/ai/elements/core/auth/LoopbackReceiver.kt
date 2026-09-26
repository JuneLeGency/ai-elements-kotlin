package dev.ai.elements.core.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import kotlin.coroutines.coroutineContext

/**
 * A one-shot HTTP listener on the loopback interface for an OAuth redirect
 * (RFC 8252 §7.3). Bound to 127.0.0.1 only — never reachable from the network.
 *
 * Open it *before* sending the user to the browser, then [awaitCode].
 */
class LoopbackReceiver private constructor(
    private val redirect: LoopbackRedirect,
    private val server: ServerSocket,
) : Closeable {

    /**
     * Waits for the redirect and returns the authorization code after
     * checking [expectedState] (CSRF). Answers the browser with a small page
     * telling the user to return to the app. Requests to other paths (a
     * favicon, a probe) are answered 404 and ignored.
     */
    suspend fun awaitCode(expectedState: String?, timeoutMs: Long = 5 * 60_000L): String = withTimeout(timeoutMs) {
        withContext(Dispatchers.IO) {
            server.soTimeout = 500 // wake up to observe cancellation
            while (true) {
                coroutineContext.ensureActive()
                val socket = try {
                    server.accept()
                } catch (_: SocketTimeoutException) {
                    continue
                }
                socket.use {
                    socket.soTimeout = 5_000
                    val requestLine = socket.getInputStream().bufferedReader().readLine().orEmpty()
                    val target = requestLine.split(' ').getOrNull(1).orEmpty()
                    val path = target.substringBefore('?')
                    val out = socket.getOutputStream()
                    if (path != redirect.path) {
                        out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        return@use
                    }
                    val query = parseQuery(target.substringAfter('?', ""))
                    val error = query["error"]
                    val code = query["code"]
                    val ok = error == null && code != null && (expectedState == null || query["state"] == expectedState)
                    val page = if (ok) SUCCESS_PAGE else FAILURE_PAGE
                    val body = page.toByteArray()
                    out.write(
                        ("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\n" +
                            "Cache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray() + body,
                    )
                    out.flush()
                    when {
                        error != null -> throw OAuthException(query["error_description"] ?: error)
                        code == null -> throw OAuthException("The sign-in page returned no authorization code.")
                        expectedState != null && query["state"] != expectedState ->
                            throw OAuthException("Sign-in response did not match this request (state mismatch).")
                        else -> return@withContext code
                    }
                }
            }
            @Suppress("UNREACHABLE_CODE")
            error("unreachable")
        }
    }

    override fun close() = server.close()

    companion object {
        /**
         * Binds the redirect's port on 127.0.0.1. Fails with a clear message
         * when another app already holds it (the port is fixed by the
         * provider's registered redirect URI).
         */
        fun open(redirect: LoopbackRedirect): LoopbackReceiver {
            val server = ServerSocket()
            try {
                server.reuseAddress = true
                server.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), redirect.port), 1)
            } catch (e: Exception) {
                server.close()
                throw OAuthException("Port ${redirect.port} is in use by another app; close it and try again. (${e.message})")
            }
            return LoopbackReceiver(redirect, server)
        }

        internal fun parseQuery(query: String): Map<String, String> = query.split('&')
            .filter { it.isNotEmpty() }
            .associate { part ->
                val key = URLDecoder.decode(part.substringBefore('='), "UTF-8")
                key to URLDecoder.decode(part.substringAfter('=', ""), "UTF-8")
            }

        private val SUCCESS_PAGE = page("✓", "Signed in", "You can close this page and return to the app.<br>已登录，可以关闭此页面并返回应用。")
        private val FAILURE_PAGE = page("!", "Sign-in failed", "Return to the app to see what went wrong.<br>登录失败，请返回应用查看原因。")

        private fun page(mark: String, title: String, text: String) = """
            <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>$title</title><style>
            body{font-family:system-ui,sans-serif;display:flex;min-height:100vh;margin:0;align-items:center;justify-content:center;background:#f6f5fb;color:#1b1b21}
            @media (prefers-color-scheme:dark){body{background:#131318;color:#e4e1e9}}
            .c{text-align:center;padding:24px}.m{font-size:48px}h1{font-weight:600;font-size:22px}p{opacity:.75;line-height:1.5}
            </style></head><body><div class="c"><div class="m">$mark</div><h1>$title</h1><p>$text</p></div></body></html>
        """.trimIndent()
    }
}
