package dev.ai.elements.ui.markdown

import dev.ai.elements.ui.chat.LocalAiElementsRenderers
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.m3.elements.MarkdownCheckBox
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.markdownPadding
import dev.ai.elements.core.model.SourcePart
import dev.ai.elements.ui.chat.CitationPreprocessor
import dev.ai.elements.ui.chat.CitationSheet
import dev.ai.elements.ui.theme.AiSpacing
import dev.ai.elements.ui.theme.AiType
import dev.ai.elements.ui.theme.LocalCodeFontFamily
import org.intellij.markdown.MarkdownTokenTypes

/**
 * Renders GitHub-flavoured Markdown with Material 3 styling.
 *
 * - fenced code gets syntax highlighting and a copy action ([CodeBlock]);
 * - ```` ```mermaid ```` fences render as diagrams ([MermaidDiagram]) once the
 *   fence is closed, so half-streamed diagrams never flash parse errors;
 * - LaTeX: `$$…$$` / `\[…\]` render with KaTeX ([MathBlock]), inline `$…$`
 *   becomes Unicode math (see [LatexPreprocessor]).
 *
 * - `[n]` references become [InlineCitation]-style links into [citations]
 *   (the message's sources), opening a sheet with the cited sources.
 *
 * Streaming ([streaming] = true): the text is split into top-level blocks
 * ([MarkdownStreaming.split]); finished blocks keep identical strings, so their
 * composables are skipped and only the block being written re-parses — cost
 * stays linear in the answer's length. The tail block has unfinished markup
 * closed ([MarkdownStreaming.repairTail]) so raw `**` never flashes.
 */
@Composable
fun MarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier,
    citations: List<SourcePart> = emptyList(),
    streaming: Boolean = false,
) {
    val blocks = remember(markdown, citations) { markdownBlocks(markdown, citations) }
    CitationLinks(citations) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AiSpacing.m)) {
            blocks.forEachIndexed { index, block ->
                val text = if (streaming && index == blocks.lastIndex) MarkdownStreaming.repairTail(block) else block
                key(index) { MarkdownBlock(text) }
            }
        }
    }
}

/** Preprocess (citations, LaTeX) and split Markdown into top-level blocks. */
internal fun markdownBlocks(markdown: String, citations: List<SourcePart>): List<String> =
    MarkdownStreaming.split(LatexPreprocessor.process(CitationPreprocessor.process(markdown, citations)))

/** Routes `aicite:` links to a sheet of the cited sources; other links open the browser. */
@Composable
internal fun CitationLinks(citations: List<SourcePart>, content: @Composable () -> Unit) {
    val platformUriHandler = LocalUriHandler.current
    var cited by remember { mutableStateOf<List<SourcePart>>(emptyList()) }
    val uriHandler = remember(platformUriHandler, citations) {
        object : UriHandler {
            override fun openUri(uri: String) {
                if (uri.startsWith(CitationPreprocessor.SCHEME)) cited = CitationPreprocessor.resolve(uri, citations)
                else runCatching { platformUriHandler.openUri(uri) }
            }
        }
    }
    if (cited.isNotEmpty()) CitationSheet(cited, onDismiss = { cited = emptyList() })
    CompositionLocalProvider(LocalUriHandler provides uriHandler, content = content)
}

/** One top-level block. Takes only a String, so unchanged blocks are skipped. */
@Composable
internal fun MarkdownBlock(content: String) {
    val type = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    val body = AiType.body
    val components = remember {
        markdownComponents(
            codeFence = { model ->
                val closed = model.node.children.lastOrNull()?.type == MarkdownTokenTypes.CODE_FENCE_END
                MarkdownCodeFence(model.content, model.node, model.typography.code) { source, language, _ ->
                    val custom = language?.lowercase()?.let { LocalAiElementsRenderers.current.codeBlocks[it] }
                    when {
                        custom != null -> custom.Render(source, closed)
                        language.equals("mermaid", ignoreCase = true) -> MermaidDiagram(source, complete = closed)
                        language.equals("math", ignoreCase = true) && closed -> MathBlock(source)
                        else -> CodeBlock(source, language)
                    }
                }
            },
            codeBlock = { model ->
                MarkdownCodeBlock(model.content, model.node, model.typography.code) { source, language, _ ->
                    CodeBlock(source, language)
                }
            },
            checkbox = { MarkdownCheckBox(it.content, it.node, it.typography.text) },
        )
    }
    Markdown(
        content = content,
        colors = markdownColor(
            text = scheme.onSurface,
            codeBackground = scheme.surfaceContainerHighest,
            inlineCodeBackground = scheme.surfaceContainerHighest,
            tableBackground = scheme.surfaceContainerLow,
        ),
        typography = markdownTypography(
            h1 = AiType.h1,
            h2 = AiType.h2,
            h3 = AiType.h3,
            h4 = AiType.h4,
            h5 = AiType.h4,
            h6 = AiType.h4,
            text = body,
            code = AiType.code,
            inlineCode = body.copy(fontFamily = LocalCodeFontFamily.current, fontSize = 14.sp, color = scheme.tertiary),
            quote = body.copy(color = scheme.onSurfaceVariant),
            bullet = body,
            list = body,
            ordered = body,
            paragraph = body,
            table = AiType.small,
            textLink = TextLinkStyles(
                style = SpanStyle(
                    color = scheme.primary,
                    fontWeight = FontWeight.Medium,
                    background = scheme.primary.copy(alpha = 0.10f),
                ),
            ),
        ),
        padding = markdownPadding(block = 0.dp, listIndent = 8.dp, listItemTop = 2.dp, listItemBottom = 2.dp),
        dimens = markdownDimens(codeBackgroundCornerSize = 12.dp, tableCornerSize = 12.dp, tableCellWidth = 160.dp, tableCellPadding = 8.dp),
        components = components,
        retainState = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
