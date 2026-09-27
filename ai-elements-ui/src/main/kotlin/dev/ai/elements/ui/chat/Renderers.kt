package dev.ai.elements.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import dev.ai.elements.core.model.DataPart
import dev.ai.elements.core.model.ToolPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/** Renders one tool call in place of the built-in [ToolCall] / [Subagent]. */
fun interface ToolRenderer {
    @Composable
    fun Render(part: ToolPart, onToolApproval: ((toolCallId: String, approved: Boolean) -> Unit)?)
}

/** Renders one data part in place of the built-in [DataPartView] shapes. */
fun interface DataRenderer {
    @Composable
    fun Render(part: DataPart)
}

/**
 * App-wide overrides for how parts render — the extension point for customising the chat
 * without forking [Conversation] or the message rows. Provide it with
 * `CompositionLocalProvider(LocalAiElementsRenderers provides AiElementsRenderers(...))`.
 *
 * ```kotlin
 * AiElementsRenderers(
 *     tools = mapOf("get_weather" to ToolRenderer { part, _ -> WeatherCard(part.output) }),
 *     data = mapOf("chart" to DataRenderer { part -> MyChart(part.data) }),
 * )
 * ```
 *
 * @property tools by tool name ([ToolPart.name]); checked before the built-in elements.
 * @property data by data part name ([DataPart.name], without `data-`), including keys of the
 *   agent's shared state ([DataPart.STATE]).
 * @property tool a catch-all consulted after [tools]: return null to keep the built-in rendering.
 */
@Immutable
class AiElementsRenderers(
    val tools: Map<String, ToolRenderer> = emptyMap(),
    val data: Map<String, DataRenderer> = emptyMap(),
    val tool: ((ToolPart) -> ToolRenderer?)? = null,
) {
    internal fun toolRenderer(part: ToolPart): ToolRenderer? = tools[part.name] ?: tool?.invoke(part)
}

val LocalAiElementsRenderers = staticCompositionLocalOf { AiElementsRenderers() }

/**
 * Loads the bytes behind a file URL for attachment previews (`data:` URLs are decoded without it).
 * Replace it to use your app's HTTP stack, cache or authentication.
 */
fun interface FileLoader {
    suspend fun load(url: String): ByteArray?
}

val LocalFileLoader = staticCompositionLocalOf<FileLoader> {
    FileLoader { url -> withContext(Dispatchers.IO) { URL(url).openStream().use { it.readBytes() } } }
}
