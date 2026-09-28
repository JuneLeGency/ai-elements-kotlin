package dev.ai.elements.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.ai.elements.core.model.DataPart
import dev.ai.elements.ui.R
import dev.ai.elements.ui.theme.LocalCodeFontFamily
import dev.ai.elements.ui.theme.fadingHorizontalScroll
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Renders an agent-streamed `data-*` part. Built-in shapes:
 *
 * - `data-plan` `{title, description?, streaming?, steps: [{label, status?}]}` → [Plan]
 * - `data-task` `{title, items: [{label, status?, files?: [..]}]}` → [Task]
 * - `data-chain-of-thought` `{title?, steps: [{label, description?, status?, badges?}]}` → [ChainOfThought]
 *
 * - `state` (AG-UI `STATE_SNAPSHOT` / `STATE_DELTA`, already patched): its `plan`, `task` and
 *   `chain-of-thought` keys render as above, other keys as JSON
 *
 * `status` is `pending` | `active` | `complete`. Names match case-insensitively (AG-UI
 * `ACTIVITY_*` parts are named by their `activityType`). Anything else falls back to
 * a JSON card; pass [renderers] to map your own part names.
 */
@Composable
fun DataPartView(
    part: DataPart,
    modifier: Modifier = Modifier,
    renderers: Map<String, @Composable (DataPart) -> Unit> = emptyMap(),
) {
    renderers[part.name]?.let { it(part); return }
    LocalAiElementsRenderers.current.data[part.name]?.let { it.Render(part); return }
    val data = part.data as? JsonObject
    when (part.name.lowercase()) {
        DataPart.STATE -> if (data != null) Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val known = data.filterKeys { it in BUILT_IN }
            known.forEach { (key, value) -> DataPartView(DataPart("${part.id}-$key", key, value), renderers = renderers) }
            val rest = data.filterKeys { it !in BUILT_IN }
            if (rest.isNotEmpty()) JsonCard(part.copy(data = JsonObject(rest)), Modifier)
        } else JsonCard(part, modifier)
        "plan" -> if (data != null) Plan(
            title = data.string("title") ?: stringResource(R.string.ai_plan),
            description = data.string("description").orEmpty(),
            steps = data.steps("steps"),
            streaming = data["streaming"]?.jsonPrimitive?.booleanOrNull ?: false,
            modifier = modifier.testTag("data-plan"),
        ) else JsonCard(part, modifier)
        "task" -> if (data != null) Task(
            title = data.string("title") ?: stringResource(R.string.ai_task),
            items = data.steps("items"),
            modifier = modifier.testTag("data-task"),
        ) else JsonCard(part, modifier)
        "chain-of-thought" -> if (data != null) ChainOfThought(
            steps = data.steps("steps"),
            title = data.string("title") ?: stringResource(R.string.ai_chain_of_thought),
            modifier = modifier.testTag("data-chain-of-thought"),
        ) else JsonCard(part, modifier)
        else -> JsonCard(part, modifier)
    }
}

@Composable
private fun JsonCard(part: DataPart, modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("data-${part.name}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                prettyJson.encodeToString(JsonElement.serializer(), part.data),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = LocalCodeFontFamily.current),
                softWrap = false,
                modifier = Modifier.padding(top = 6.dp).fadingHorizontalScroll(),
            )
        }
    }
}

private val prettyJson = Json { prettyPrint = true }

private val BUILT_IN = setOf("plan", "task", "chain-of-thought")

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.steps(key: String): List<WorkflowStep> =
    (this[key] as? JsonArray).orEmpty().mapNotNull { element ->
        when (element) {
            is JsonPrimitive -> element.contentOrNull?.let { WorkflowStep(it) }
            is JsonObject -> WorkflowStep(
                label = element.string("label") ?: element.string("title") ?: return@mapNotNull null,
                description = element.string("description"),
                status = when (element.string("status")?.lowercase()) {
                    "complete", "completed", "done" -> StepStatus.COMPLETE
                    "active", "in_progress", "running" -> StepStatus.ACTIVE
                    else -> StepStatus.PENDING
                },
                badges = ((element["badges"] ?: element["files"]) as? JsonArray).orEmpty()
                    .mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
            )
            else -> null
        }
    }
