package dev.ai.elements.ui.code

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.ai.elements.ui.R
import dev.ai.elements.ui.theme.AiType

/** One parsed frame; [app] frames are the caller's own code. */
@Immutable
data class StackFrame(val text: String, val app: Boolean)

/** An exception: its type, message and frames (JVM, Python, JS and Swift traces all parse). */
@Immutable
data class ParsedStackTrace(val type: String, val message: String, val frames: List<StackFrame>) {
    companion object {
        private val jvmHeader = Regex("""^(?:Exception in thread "[^"]*" )?((?:[\w$]+\.)*[\w$]+(?:Exception|Error|Throwable)[\w$]*):?\s*(.*)$""")
        private val pyHeader = Regex("""^(\w+(?:Error|Exception|Warning)):\s*(.*)$""")
        private val jsHeader = Regex("""^(\w*Error):\s*(.*)$""")
        private val library = Regex("""\b(java\.|javax\.|kotlin\.|kotlinx\.|android\.|androidx\.|com\.android\.|dalvik\.|sun\.|jdk\.|node:|node_modules|site-packages|/usr/lib/)""")

        /** Parses [trace]; frames are lines starting with `at`, `File "…"`, or `#n`. */
        fun parse(trace: String): ParsedStackTrace {
            val lines = trace.trim().lines()
            var type = ""
            var message = ""
            val frames = mutableListOf<StackFrame>()
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("Traceback")) continue
                val isFrame = line.startsWith("at ") || line.startsWith("File \"") || line.matches(Regex("""^#\d+ .*""")) ||
                    line.startsWith("... ") || line.startsWith("Caused by:")
                if (isFrame) {
                    frames += StackFrame(line, app = !library.containsMatchIn(line) && !line.startsWith("..."))
                } else if (type.isEmpty()) {
                    val m = jvmHeader.find(line) ?: pyHeader.find(line) ?: jsHeader.find(line)
                    if (m != null) {
                        type = m.groupValues[1]
                        message = m.groupValues[2]
                    } else {
                        message = line
                    }
                } else if (frames.isEmpty()) {
                    message = listOf(message, line).filter { it.isNotEmpty() }.joinToString("\n")
                } else if (frames.isNotEmpty() && !isFrame) {
                    // Python puts the source line under each `File` frame.
                    frames[frames.lastIndex] = frames.last().copy(text = frames.last().text + "\n    " + line)
                }
            }
            return ParsedStackTrace(type.ifEmpty { "Error" }, message, frames)
        }
    }
}

/**
 * A readable exception (AI Elements `<StackTrace>`): type and message up
 * front, the app's own frames emphasised, library frames collapsed until
 * asked for, with copy of the raw trace.
 */
@Composable
fun StackTrace(trace: String, modifier: Modifier = Modifier, collapsedFrames: Int = 6) {
    val parsed = remember(trace) { ParsedStackTrace.parse(trace) }
    var showAll by rememberSaveable(trace) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    ElementCard(
        title = parsed.type.substringAfterLast('.'),
        subtitle = pluralStringResource(R.plurals.ai_stack_frames, parsed.frames.size, parsed.frames.size),
        icon = Icons.Outlined.BugReport,
        iconTint = colors.error,
        modifier = modifier.testTag("stack-trace"),
        actions = { CopyButton({ trace }) },
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (parsed.message.isNotBlank()) {
                SelectionContainer { Text(parsed.message, style = MaterialTheme.typography.bodyMedium, color = colors.error) }
            }
            val shown = if (showAll) parsed.frames else parsed.frames.let { f ->
                // Keep the app's frames visible even when library frames are folded away.
                (f.take(collapsedFrames) + f.drop(collapsedFrames).filter { it.app }).distinct()
            }
            SelectionContainer {
                Text(
                    buildAnnotatedString {
                        shown.forEachIndexed { i, frame ->
                            if (i > 0) append('\n')
                            withStyle(
                                SpanStyle(
                                    color = if (frame.app) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontWeight = if (frame.app) FontWeight.SemiBold else null,
                                ),
                            ) { append(frame.text) }
                        }
                    },
                    style = AiType.code,
                    softWrap = false,
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                )
            }
            if (shown.size < parsed.frames.size || showAll) {
                TextButton(onClick = { showAll = !showAll }) {
                    Text(if (showAll) stringResource(R.string.ai_show_less) else stringResource(R.string.ai_show_all_frames, parsed.frames.size))
                }
            }
        }
    }
}
