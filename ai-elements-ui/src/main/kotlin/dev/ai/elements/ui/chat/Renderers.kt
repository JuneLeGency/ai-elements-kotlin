package dev.ai.elements.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import dev.ai.elements.core.chat.ToolDecision
import dev.ai.elements.core.model.DataPart
import dev.ai.elements.core.model.ToolPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Renders one tool call in place of the built-in [ToolCall] / [Subagent].
 * The decision callback carries approval, denial reason, edited arguments and remember choice.
 * A null callback means read-only rendering: do not show approval actions. Respect
 * [ToolPart.approvalAnswers] when offering edits or a reason.
 */
fun interface ToolRenderer {
    @Composable
    fun Render(part: ToolPart, onToolDecision: ((toolCallId: String, decision: ToolDecision) -> Unit)?)
}

/** Renders a fenced code block of one language (```` ```lang ````) in place of the code view. */
fun interface CodeBlockRenderer {
    /** [complete] is false while the fence is still streaming. */
    @Composable
    fun Render(source: String, complete: Boolean)
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
 * @property codeBlocks by fence language (lower case), e.g. a live preview for `jsx` fences.
 */
@Immutable
class AiElementsRenderers(
    val tools: Map<String, ToolRenderer> = emptyMap(),
    val data: Map<String, DataRenderer> = emptyMap(),
    val tool: ((ToolPart) -> ToolRenderer?)? = null,
    val codeBlocks: Map<String, CodeBlockRenderer> = emptyMap(),
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
