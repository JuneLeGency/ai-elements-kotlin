package dev.ai.elements.mcpapps

import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.ai.elements.core.mcp.McpClient
import dev.ai.elements.core.mcp.McpTool
import dev.ai.elements.mcpapps.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.Locale
import java.util.TimeZone

/**
 * What an app does with its views' requests; the chat-level part of [McpAppHost] (the view
 * handles sizing, display modes and tool approval itself).
 */
interface McpAppActions {
    /** `ui/message`: send [text] as the user's next message. Return false to decline. */
    fun message(text: String): Boolean = false

    /** `ui/update-model-context`: keep [context] for the model's next turn (replacing the view's previous one). */
    fun modelContext(context: McpAppModelContext) {}

    /** `ui/open-link`: open an http(s) `url`; null opens it with the platform's `LocalUriHandler`. */
    val openLink: ((String) -> Unit)? get() = null
}

/**
 * The live views of a chat, by tool call. A view keeps running while it scrolls out of sight or
 * its message recomposes — its sandbox, connection and state live here, not in the composition —
 * and is torn down (`ui/resource-teardown`) when evicted (beyond [capacity], least recently shown
 * first) or on [close]. [rememberMcpAppSessions] closes them with the composition.
 */
class McpAppSessions(private val capacity: Int = 8) {
    private val sessions = LinkedHashMap<String, McpAppSession>(16, 0.75f, true)
    private var live by mutableStateOf(emptyList<McpAppSession>())

    /** Whether [Dialogs] is composed (at the screen's root), so views leave their dialogs to it. */
    internal var dialogsHosted by mutableStateOf(false)

    internal fun get(key: String, create: () -> McpAppSession): McpAppSession {
        sessions[key]?.let { return it }
        val session = create()
        sessions[key] = session
        while (sessions.size > capacity) {
            val eldest = sessions.entries.first()
            sessions.remove(eldest.key)
            eldest.value.close()
        }
        live = sessions.values.toList()
        return session
    }

    /** Tear down every view. */
    fun close() {
        sessions.values.toList().forEach { it.close() }
        sessions.clear()
        live = emptyList()
    }

    /**
     * The views' dialogs — tool approvals and full screen — independent of where the views are
     * scrolled. Compose it once at the screen's root ([McpAppsHost] does).
     */
    @Composable
    fun Dialogs() {
        DisposableEffect(this) {
            dialogsHosted = true
            onDispose { dialogsHosted = false }
        }
        live.forEach { SessionDialogs(it) }
    }
}

/** [McpAppSessions] that close when this leaves the composition. */
@Composable
fun rememberMcpAppSessions(capacity: Int = 8): McpAppSessions {
    val sessions = remember { McpAppSessions(capacity) }
    DisposableEffect(sessions) { onDispose { sessions.close() } }
    return sessions
}

/** One view: its sandbox, its bridge and the host state the UI shows. */
internal class McpAppSession(
    context: Context,
    val client: McpClient,
    val resourceUri: String,
    val toolCallId: String,
    val tool: JsonObject,
    private val originKey: String,
) : McpAppHost {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val appContext = context

    var actions: McpAppActions = object : McpAppActions {}
    var uriHandler: UriHandler? = null

    var resource by mutableStateOf<Result<McpAppResource>?>(null)
        private set
    var contentHeight by mutableStateOf<Dp?>(null)
    var fullscreen by mutableStateOf(false)
    var approval by mutableStateOf<Pair<McpTool, CompletableDeferred<Boolean>>?>(null)
        private set

    var sandbox: McpAppSandbox? = null
        private set
    private var bridge: McpAppBridge? = null
    private var closed = false

    /** Load the view and start its sandbox (once). */
    fun start(input: JsonObject, hostContext: JsonObject) {
        if (resource != null || closed) return
        resource = Result.success(PLACEHOLDER) // loading
        scope.launch {
            val loaded = runCatching { client.readMcpApp(resourceUri) }
            resource = loaded
            val view = loaded.getOrNull() ?: return@launch
            if (closed) return@launch
            val box = McpAppSandbox(appContext, view, McpAppSandbox.originFor(originKey)) { message ->
                scope.launch { bridge?.receive(message) }
            }.also { it.webView.tag = "mcp-app-webview-$toolCallId" }
            bridge = McpAppBridge(client, view, toolCallId, tool, this@McpAppSession, hostContext) { box.send(it.toString()) }
            sandbox = box
            box.load()
            bridge?.toolInput(input)
            pendingResult?.let { bridge?.toolResult(it) }
        }
    }

    val loading: Boolean get() = resource?.getOrNull() === PLACEHOLDER

    private var pendingResult: JsonObject? = null

    /** The call's result: sent once the view has its input (before that, kept). */
    fun result(result: JsonObject) {
        pendingResult = result
        scope.launch { bridge?.toolResult(result) }
    }

    fun hostContext(context: JsonObject) = scope.launch { bridge?.setHostContext(context) }

    fun close() {
        if (closed) return
        closed = true
        approval?.second?.complete(false)
        val b = bridge
        val s = sandbox
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            runCatching { b?.teardown("The view was closed") }
            s?.destroy()
        }
        scope.cancel()
    }

    // --- McpAppHost ---------------------------------------------------------------------------

    override suspend fun openLink(url: String): Boolean {
        actions.openLink?.invoke(url) ?: uriHandler?.openUri(url) ?: return false
        return true
    }

    override suspend fun message(text: String, content: JsonArray) = actions.message(text)

    override suspend fun updateModelContext(content: JsonArray?, structuredContent: JsonElement?) =
        actions.modelContext(McpAppModelContext(toolCallId, tool.string("title") ?: tool.string("name").orEmpty(), content, structuredContent))

    override suspend fun requestDisplayMode(mode: String, current: String): String {
        fullscreen = mode == "fullscreen"
        return mode
    }

    override fun sizeChanged(width: Double?, height: Double?) {
        if (height != null && height > 0) contentHeight = height.toFloat().dp
    }

    override fun requestTeardown() {
        fullscreen = false
    }

    override suspend fun approveToolCall(tool: McpTool, arguments: JsonObject): Boolean {
        if (tool.annotations?.readOnlyHint == true) return true
        val answer = CompletableDeferred<Boolean>()
        approval = tool to answer
        return answer.await().also { approval = null }
    }

    private companion object {
        val PLACEHOLDER = McpAppResource("ui://loading", "")
    }
}

/**
 * Shows the MCP App view [resourceUri] of one tool call: it reads the view from [client] (the
 * connection of the server that owns it), runs it in a sandbox and connects it to the chat through
 * [actions]. [input] is the call's arguments and [result] its `CallToolResult` once it returns.
 *
 * Tools the view calls that are not read-only ask the user first; `ui/request-display-mode`
 * `fullscreen` opens a full-screen dialog. The view lives in [sessions], so it survives
 * recomposition and scrolling. Test tags: `mcp-app-<toolCallId>`, `mcp-app-allow` / `mcp-app-deny`
 * on the approval dialog; the WebView's tag is `mcp-app-webview-<toolCallId>`.
 */
@Composable
fun McpAppView(
    client: McpClient,
    resourceUri: String,
    toolCallId: String,
    tool: JsonObject,
    input: JsonObject,
    result: JsonObject?,
    actions: McpAppActions,
    modifier: Modifier = Modifier,
    sessions: McpAppSessions = rememberMcpAppSessions(),
    /** The tallest the view grows inline. */
    maxHeight: Dp = 600.dp,
    /** The sandbox origin's key: views of one key share an origin (and its storage). */
    originKey: String = client.url,
) {
    val context = LocalContext.current
    val session = remember(sessions, toolCallId) {
        sessions.get(toolCallId) { McpAppSession(context, client, resourceUri, toolCallId, tool, originKey) }
    }
    session.actions = actions
    session.uriHandler = LocalUriHandler.current
    val colors = MaterialTheme.colorScheme

    Column(modifier.fillMaxWidth().testTag("mcp-app-$toolCallId")) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val hostContext = hostContext(colors, session.fullscreen, maxWidth, maxHeight)
            LaunchedEffect(session) { session.start(input, hostContext) }
            LaunchedEffect(session, hostContext) { session.hostContext(hostContext) }
            LaunchedEffect(session, result) { result?.let { session.result(it) } }

            val loaded = session.resource
            val failure = loaded?.exceptionOrNull()
            when {
                failure != null -> Text(
                    "Could not show this app: ${failure.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                loaded == null || session.loading || session.sandbox == null ->
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                else -> {
                    val sandbox = session.sandbox!!
                    val height = (session.contentHeight ?: 160.dp).coerceIn(48.dp, maxHeight)
                    val border = loaded.getOrNull()?.prefersBorder != false
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (border) colors.surfaceContainerLow else Color.Transparent,
                        border = if (border) BorderStroke(1.dp, colors.outlineVariant) else null,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Box {
                            if (!session.fullscreen) WebViewSlot(sandbox, Modifier.fillMaxWidth().height(height))
                            else Box(Modifier.fillMaxWidth().height(height))
                            if (session.contentHeight == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }

    if (!sessions.dialogsHosted) SessionDialogs(session)
}

/** A view's full-screen dialog and its pending tool approval. */
@Composable
private fun SessionDialogs(session: McpAppSession) {
    val colors = MaterialTheme.colorScheme
    val sandbox = session.sandbox
    if (session.fullscreen && sandbox != null) {
        Dialog(
            // Back to inline: the host context (display mode, container) follows `fullscreen`.
            onDismissRequest = { session.fullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize(), color = colors.surface) {
                Box(Modifier.systemBarsPadding()) {
                    WebViewSlot(sandbox, Modifier.fillMaxSize())
                    IconButton(
                        onClick = { session.fullscreen = false },
                        modifier = Modifier.padding(4.dp).background(colors.surfaceContainerHigh, MaterialTheme.shapes.extraLarge).testTag("mcp-app-exit-fullscreen"),
                    ) {
                        Icon(painterResource(android.R.drawable.ic_menu_close_clear_cancel), contentDescription = stringResource(R.string.mcpapps_exit_full_screen))
                    }
                }
            }
        }
    }

    session.approval?.let { (tool, answer) ->
        AlertDialog(
            onDismissRequest = { answer.complete(false) },
            title = { Text(stringResource(R.string.mcpapps_allow_title, tool.displayName)) },
            text = { Text(stringResource(R.string.mcpapps_allow_text, tool.displayName)) },
            confirmButton = { TextButton(onClick = { answer.complete(true) }, modifier = Modifier.testTag("mcp-app-allow")) { Text(stringResource(R.string.mcpapps_allow)) } },
            dismissButton = { TextButton(onClick = { answer.complete(false) }, modifier = Modifier.testTag("mcp-app-deny")) { Text(stringResource(R.string.mcpapps_deny)) } },
        )
    }
}

@Composable
private fun WebViewSlot(sandbox: McpAppSandbox, modifier: Modifier) {
    // One WebView moves between slots (inline, full screen, a recomposed item): a new slot takes it
    // from the old one. Releasing a slot must not detach it, as the new slot may already hold it.
    AndroidView(
        factory = { sandbox.webView.also { (it.parent as? ViewGroup)?.removeView(it) } },
        modifier = modifier,
    )
}

/** The host context of a view (§"Host Context in McpUiInitializeResult") from the Material theme. */
private fun hostContext(colors: ColorScheme, fullscreen: Boolean, width: Dp, maxHeight: Dp): JsonObject = buildJsonObject {
    put("theme", if (colors.surface.luminance() < 0.5f) "dark" else "light")
    putJsonObject("styles") {
        putJsonObject("variables") {
            fun hex(c: Color) = "#%06x".format(c.toArgb() and 0xFFFFFF)
            put("--color-background-primary", hex(colors.surface))
            put("--color-background-secondary", hex(colors.surfaceContainer))
            put("--color-background-tertiary", hex(colors.surfaceContainerHigh))
            put("--color-background-inverse", hex(colors.inverseSurface))
            put("--color-background-danger", hex(colors.errorContainer))
            put("--color-background-info", hex(colors.secondaryContainer))
            put("--color-text-primary", hex(colors.onSurface))
            put("--color-text-secondary", hex(colors.onSurfaceVariant))
            put("--color-text-inverse", hex(colors.inverseOnSurface))
            put("--color-text-danger", hex(colors.error))
            put("--color-text-info", hex(colors.primary))
            put("--color-border-primary", hex(colors.outline))
            put("--color-border-secondary", hex(colors.outlineVariant))
            put("--color-ring-primary", hex(colors.primary))
            put("--font-sans", "system-ui, sans-serif")
            put("--font-mono", "monospace")
        }
    }
    put("displayMode", if (fullscreen) "fullscreen" else "inline")
    putJsonArray("availableDisplayModes") { add("inline"); add("fullscreen") }
    putJsonObject("containerDimensions") {
        put("width", width.value.toInt())
        if (!fullscreen) put("maxHeight", maxHeight.value.toInt())
    }
    put("locale", Locale.getDefault().toLanguageTag())
    put("timeZone", TimeZone.getDefault().id)
    put("platform", "mobile")
    put("userAgent", "ai-elements-kotlin")
    putJsonObject("deviceCapabilities") { put("touch", true); put("hover", false) }
}
