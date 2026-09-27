package dev.ai.elements.genui.a2ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.ai.elements.core.chat.ChatController
import dev.ai.elements.core.model.DataPart
import dev.ai.elements.ui.chat.DataRenderer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * Renders the chat's A2UI parts ([DataPart.A2UI], whatever transport brought them) natively.
 * Register it with `AiElementsRenderers(data = mapOf(DataPart.A2UI to a2uiRenderer { chat.send(it) }))`.
 *
 * One A2UI session per part: when a part grows (the agent streams more messages) only the new ones
 * are applied, so what the user typed stays; a part that is replaced by a different list starts over.
 * A part holding `{"status": "building" | "retrying" | "failed"}` (the AG-UI middleware's pre-paint
 * lifecycle) shows as a placeholder.
 */
fun a2uiRenderer(
    catalog: A2uiCatalog = A2uiCatalog.Basic,
    catalogs: List<A2uiCatalog> = emptyList(),
    onAction: (A2uiAction) -> Unit,
): DataRenderer = DataRenderer { part -> A2uiPart(part, catalog, catalogs, onAction) }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun A2uiPart(part: DataPart, catalog: A2uiCatalog, catalogs: List<A2uiCatalog>, onAction: (A2uiAction) -> Unit) {
    val status = ((part.data as? JsonObject)?.get("status") as? JsonPrimitive)?.content
    if (status != null) {
        Row(Modifier.padding(vertical = 8.dp).testTag("a2ui-status"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (status != "failed") LoadingIndicator(Modifier.size(24.dp))
            Text(
                when (status) { "failed" -> "Could not build this view"; "retrying" -> "Retrying the view…"; else -> "Building the view…" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val messages = (part.data as? JsonArray).orEmpty()
    val session = remember(part.id, catalog) { A2uiSession(catalog, catalogs) }
    session.apply(messages)
    Column(Modifier.fillMaxWidth().testTag("a2ui-part-${part.id}"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        session.state.surfaces.forEach { A2uiSurfaceView(it, catalog = catalog, catalogs = catalogs, onAction = onAction) }
    }
}

/** An A2UI state that applies a growing message list incrementally. */
private class A2uiSession(private val catalog: A2uiCatalog, private val catalogs: List<A2uiCatalog>) {
    var state = A2uiState(catalog, catalogs)
        private set
    private var applied: List<JsonElement> = emptyList()

    fun apply(messages: List<JsonElement>) {
        if (messages == applied) return
        if (messages.size >= applied.size && messages.subList(0, applied.size) == applied) {
            messages.drop(applied.size).forEach { state.process(it) }
        } else {
            state = A2uiState(catalog, catalogs).also { fresh -> messages.forEach { fresh.process(it) } }
        }
        applied = messages
    }
}

/**
 * This action as the data parts of a user turn: the A2UI `action` message ([DataPart.A2UI]) and,
 * when the surface asked for it, its data model ([DataPart.A2UI_DATA_MODEL]).
 */
fun A2uiAction.toDataParts(): List<DataPart> = listOfNotNull(
    DataPart("a2ui-action-${UUID.randomUUID()}", DataPart.A2UI, JsonArray(listOf(toMessage()))),
    dataModel?.let { model ->
        DataPart("a2ui-model-${UUID.randomUUID()}", DataPart.A2UI_DATA_MODEL, buildJsonObject {
            put("version", A2ui.VERSION)
            put("surfaces", buildJsonObject { put(surfaceId, model) })
        })
    },
)

/**
 * Send a user's A2UI action to the agent, as a user turn the transports map onto their A2UI
 * binding (AG-UI `forwardedProps.a2uiAction`, an A2A `application/a2ui+json` part, an AI SDK
 * `data-a2ui` part). Its text is the action's `userMessage`, else its name.
 */
fun ChatController.send(action: A2uiAction): Boolean =
    send(action.userMessage ?: action.name.replace(Regex("([a-z])([A-Z])|_"), "$1 $2").trim().replaceFirstChar { it.uppercase() }, data = action.toDataParts())

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
