package dev.ai.elements.ui.markdown

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.m3.elements.MarkdownCheckBox
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.markdownPadding
import org.intellij.markdown.MarkdownTokenTypes

/**
 * Renders GitHub-flavoured Markdown with Material 3 styling.
 *
 * - fenced code gets syntax highlighting and a copy action ([CodeBlock]);
 * - ```` ```mermaid ```` fences render as diagrams ([MermaidDiagram]) once the
 *   fence is closed, so half-streamed diagrams never flash parse errors.
 *
 * Safe to call with a growing string while streaming: the previous render is
 * kept on screen while the new text is parsed.
 */
@Composable
fun MarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val type = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    val code = type.bodyMedium.copy(fontFamily = FontFamily.Monospace)
    val components = remember {
        markdownComponents(
            codeFence = { model ->
                val closed = model.node.children.lastOrNull()?.type == MarkdownTokenTypes.CODE_FENCE_END
                MarkdownCodeFence(model.content, model.node, model.typography.code) { source, language, _ ->
                    if (language.equals("mermaid", ignoreCase = true)) {
                        MermaidDiagram(source, complete = closed)
                    } else {
                        CodeBlock(source, language)
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
        content = markdown,
        colors = markdownColor(
            text = scheme.onSurface,
            codeBackground = scheme.surfaceContainerHighest,
            inlineCodeBackground = scheme.surfaceContainerHighest,
            tableBackground = scheme.surfaceContainerLow,
        ),
        typography = markdownTypography(
            h1 = type.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            h2 = type.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            h3 = type.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            h4 = type.titleSmall,
            h5 = type.titleSmall,
            h6 = type.titleSmall,
            text = type.bodyLarge,
            code = code,
            inlineCode = type.bodyLarge.copy(fontFamily = FontFamily.Monospace, color = scheme.tertiary),
            quote = type.bodyLarge.copy(color = scheme.onSurfaceVariant),
            table = type.bodyMedium,
        ),
        padding = markdownPadding(block = 4.dp, listIndent = 12.dp),
        dimens = markdownDimens(codeBackgroundCornerSize = 16.dp, tableCornerSize = 16.dp, tableCellWidth = 200.dp),
        components = components,
        retainState = true,
        modifier = modifier.fillMaxWidth(),
    )
}
