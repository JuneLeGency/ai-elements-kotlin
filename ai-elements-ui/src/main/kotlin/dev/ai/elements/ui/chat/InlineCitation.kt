package dev.ai.elements.ui.chat

import dev.ai.elements.ui.icons.AiIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ai.elements.core.model.SourcePart
import dev.ai.elements.ui.R
import dev.ai.elements.ui.theme.AiSize
import java.net.URI

/**
 * An inline citation badge (AI Elements `<InlineCitation>`): the first
 * source's host plus "+N" for the rest; tapping opens the cited sources.
 */
@Composable
fun InlineCitation(sources: List<SourcePart>, modifier: Modifier = Modifier) {
    if (sources.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Surface(
        onClick = { open = true },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.minimumInteractiveComponentSize().testTag("inline-citation"),
    ) {
        Text(
            citationLabel(sources),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
    if (open) CitationSheet(sources, onDismiss = { open = false })
}

/** Bottom sheet listing cited sources, each opening in the browser. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitationSheet(sources: List<SourcePart>, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.ai_sources), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
            sources.forEach { source ->
                Surface(
                    onClick = { runCatching { uriHandler.openUri(source.url) } },
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().testTag("citation-source"),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(28.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(host(source.url).take(1).uppercase(), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(source.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                host(source.url),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(AiIcons.OpenInNew, stringResource(R.string.ai_open), Modifier.size(AiSize.compactIcon))
                    }
                }
            }
        }
    }
}

internal fun host(url: String): String =
    runCatching { URI(url).host?.removePrefix("www.") }.getOrNull() ?: url

internal fun citationLabel(sources: List<SourcePart>): String =
    host(sources.first().url) + if (sources.size > 1) " +${sources.size - 1}" else ""

/**
 * Turns `[1]`, `[2][3]` style references in model text into tappable links
 * `aicite:1,2` labelled like [InlineCitation]. Code, links and footnote
 * definitions are left alone; out-of-range numbers stay as plain text.
 */
internal object CitationPreprocessor {
    const val SCHEME = "aicite:"
    private val run = Regex("""(?<![\]\w!\\])((?:\[\d{1,2}]\s?)+)(?![(:\[])""")
    private val number = Regex("""\[(\d{1,2})]""")

    fun process(markdown: String, sources: List<SourcePart>): String {
        if (sources.isEmpty() || '[' !in markdown) return markdown
        var inFence = false
        return markdown.split('\n').joinToString("\n") { line ->
            if (line.trimStart().startsWith("```") || line.trimStart().startsWith("~~~")) {
                inFence = !inFence
                line
            } else if (inFence) {
                line
            } else {
                line.split('`').mapIndexed { i, chunk -> if (i % 2 == 1) chunk else replace(chunk, sources) }.joinToString("`")
            }
        }
    }

    private fun replace(text: String, sources: List<SourcePart>): String = run.replace(text) { match ->
        val indices = number.findAll(match.value).map { it.groupValues[1].toInt() }.filter { it in 1..sources.size }.distinct().toList()
        if (indices.isEmpty()) return@replace match.value
        val trailing = if (match.value.endsWith(" ")) " " else ""
        val label = citationLabel(indices.map { sources[it - 1] })
        "[$label]($SCHEME${indices.joinToString(",")})$trailing"
    }

    /** Sources referenced by an `aicite:` URI. */
    fun resolve(uri: String, sources: List<SourcePart>): List<SourcePart> =
        uri.removePrefix(SCHEME).split(',').mapNotNull { it.trim().toIntOrNull()?.let { n -> sources.getOrNull(n - 1) } }
}
