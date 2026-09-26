package dev.ai.elements.core.auth

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** The authorization code + state returned by the OAuth provider. */
data class OAuthRedirect(val code: String, val state: String)

/** Raised when the callback arrives with a [state] different from the expected one. */
class OAuthStateMismatchException(expected: String, actual: String) :
    RuntimeException("OAuth state mismatch (possible CSRF): expected=$expected actual=$actual")

/** Raised when no callback arrives within the timeout window. */
class OAuthCallbackTimeoutException(val port: Int) :
    RuntimeException("OAuth loopback timed out on port $port")

/**
 * A process-local loopback HTTP server that waits for the OAuth provider to
 * redirect back to `http://localhost:<port><path>?code=...&state=...`.
 *
 * Mirrors OpenMinis' `OAuthCallbackServer` + `OAuthLoopbackCallback`: a raw
 * [ServerSocket] bound before the browser is launched (so there is no race),
 * which parses exactly one redirect, validates `state`, answers a 200 HTML page,
 * and shuts down. Only one instance is active at a time (the latest replaces any
 * previous one).
 *
 * @param port preferred loopback port.
 * @param fallbackPorts tried in order if [port] is taken.
 * @param redirectPath the path the provider is told to redirect to (e.g. `/callback`).
 */
class OAuthCallbackServer(
    private val port: Int,
    private val fallbackPorts: List<Int> = emptyList(),
    private val redirectPath: String = "/callback",
) {
    private var boundPort: Int = 0

    /**
     * Bind the socket, then suspend until the redirect arrives (or [timeoutMs]
     * elapses). [launchBrowser] is invoked only after a successful bind so the
     * listener is ready before the user is sent to authorize.
     */
    suspend fun await(
        expectedState: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        launchBrowser: suspend () -> Unit,
    ): OAuthRedirect = withTimeout(timeoutMs) {
        val sock = bind()
        launchBrowser()
        suspendCancellableCoroutine { cont: CancellableContinuation<OAuthRedirect> ->
            cont.invokeOnCancellation { runCatching { sock.close() } }
            readOneRedirect(sock, expectedState, cont)
        }
    }

    private fun bind(): ServerSocket {
        val ports = listOf(port) + fallbackPorts
        var lastError: Exception? = null
        for (p in ports) {
            try {
                val s = ServerSocket()
                s.reuseAddress = true
                s.bind(InetSocketAddress("127.0.0.1", p))
                s.soTimeout = 0
                boundPort = p
                return s
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw RuntimeException("Could not bind OAuth loopback on any of $ports", lastError)
    }

    private fun readOneRedirect(
        sock: ServerSocket,
        expectedState: String,
        cont: CancellableContinuation<OAuthRedirect>,
    ) {
        // Single-shot: the await() coroutine is the reader.
        try {
            val client: Socket = sock.accept()
            client.use { c ->
                c.soTimeout = 5_000
                val reader = c.getInputStream().bufferedReader(StandardCharsets.UTF_8)
                val requestLine = reader.readLine() ?: return
                // Drain (tiny) headers — we only need the request line + query.
                var header: String?
                while (reader.readLine().also { header = it }!!.isNotEmpty()) { /* headers */ }

                val pathAndQuery = requestLine.substringAfter(' ', "")
                val query = pathAndQuery.substringAfter('?', "")
                val params = parseQuery(query)
                val code = params["code"]
                val state = params["state"]

                respond(c, 200, "OK", callbackHtml())

                when {
                    code == null -> cont.resumeWith(Result.failure(OAuthRedirectException("callback missing ?code=")))
                    state != expectedState -> cont.resumeWith(Result.failure(OAuthStateMismatchException(expectedState, state ?: "<null>")))
                    else -> cont.resumeWith(Result.success(OAuthRedirect(code, state)))
                }
            }
        } catch (e: Throwable) {
            if (!cont.isCancelled) cont.resumeWith(Result.failure(e))
        }
    }

    private fun parseQuery(q: String): Map<String, String> =
        q.split('&').mapNotNull {
            val i = it.indexOf('=')
            if (i > 0) {
                URLDecoder.decode(it.substring(0, i), StandardCharsets.UTF_8.name()) to
                    URLDecoder.decode(it.substring(i + 1), StandardCharsets.UTF_8.name())
            } else null
        }.toMap()

    private fun respond(socket: Socket, code: Int, reason: String, html: String) {
        val body = html.toByteArray(StandardCharsets.UTF_8)
        val out = socket.getOutputStream()
        out.write(
            ("HTTP/1.1 $code $reason\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n\r\n").toByteArray(StandardCharsets.UTF_8)
        )
        out.write(body)
        out.flush()
    }

    private fun callbackHtml(): String =
        "<!doctype html><meta charset=utf-8><title>OAuth</title>" +
            "<body style='font-family:system-ui;padding:2rem'>" +
            "<h2>Authorization complete</h2><p>You can close this tab and return to the app.</p></body>"

    companion object {
        const val DEFAULT_TIMEOUT_MS = 5 * 60 * 1000L
    }
}

/** Raised when the redirect is malformed (e.g. missing code). */
class OAuthRedirectException(message: String) : RuntimeException(message)
