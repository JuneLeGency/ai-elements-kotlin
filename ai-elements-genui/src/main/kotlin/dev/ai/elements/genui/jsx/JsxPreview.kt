package dev.ai.elements.genui.jsx

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.ai.elements.genui.a2ui.A2ui
import dev.ai.elements.genui.a2ui.A2uiAction
import dev.ai.elements.genui.a2ui.A2uiCatalog
import dev.ai.elements.genui.a2ui.A2uiState
import dev.ai.elements.genui.a2ui.A2uiSurfaceView
import dev.ai.elements.ui.chat.CodeBlockRenderer
import dev.ai.elements.ui.markdown.CodeBlock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Renders model-written JSX natively (AI Elements `jsx-preview`): the JSX compiles onto A2UI
 * components ([JsxCompiler]) and renders with [catalog], while it streams. [bindings] are the
 * values `{name}` can read; inputs write back into them locally, and handlers such as
 * `onClick={save}` arrive in [onAction] (name `save`, with the current data model).
 */
@Composable
fun JsxPreview(
    jsx: String,
    modifier: Modifier = Modifier,
    bindings: JsonObject = JsonObject(emptyMap()),
    catalog: A2uiCatalog = A2uiCatalog.Basic,
    onAction: (A2uiAction) -> Unit = {},
) {
    val compiler = remember(catalog) { JsxCompiler(catalog.components.keys) }
    // One surface for the whole stream: later JSX replaces the components, what the user typed stays.
    val state = remember(catalog, bindings) {
        A2uiState(catalog).also { it.process(buildJsonObject {
            put("version", A2ui.VERSION)
            put("createSurface", buildJsonObject { put("surfaceId", SURFACE); put("sendDataModel", true); put("dataModel", bindings) })
        }) }
    }
    remember(jsx, state) {
        state.process(buildJsonObject {
            put("version", A2ui.VERSION)
            put("updateComponents", buildJsonObject { put("surfaceId", SURFACE); put("components", JsonArray(compiler.components(jsx))) })
        })
    }
    state.surface(SURFACE)?.let { A2uiSurfaceView(it, modifier.testTag("jsx-preview"), catalog, onAction) }
}

/**
 * A code-block renderer that previews ```` ```jsx ```` fences in chat Markdown; register it with
 * `AiElementsRenderers(codeBlocks = mapOf("jsx" to jsxCodeBlocks(), "tsx" to jsxCodeBlocks()))`.
 * The preview has a "Code" toggle to show the source.
 */
fun jsxCodeBlocks(onAction: (A2uiAction) -> Unit = {}): CodeBlockRenderer = CodeBlockRenderer { source, _ ->
    var showCode by rememberSaveable(source.take(64)) { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            TextButton(onClick = { showCode = !showCode }) {
                Text(if (showCode) "Preview" else "Code", style = MaterialTheme.typography.labelMedium)
            }
            if (showCode) CodeBlock(source, "jsx") else JsxPreview(source, onAction = onAction)
        }
    }
}

private const val SURFACE = "jsx-preview"
