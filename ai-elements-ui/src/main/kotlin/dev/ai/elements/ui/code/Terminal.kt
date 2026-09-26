package dev.ai.elements.ui.code

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ai.elements.ui.R
import dev.ai.elements.ui.theme.AiSize
import dev.ai.elements.ui.theme.AiType
import dev.ai.elements.ui.theme.compactIconButton
import dev.ai.elements.ui.theme.isDark

/** Where a command is in its life. */
enum class TerminalStatus { RUNNING, SUCCESS, ERROR }

/**
 * Command output (AI Elements `<Terminal>`): a dark console that renders ANSI
 * colors and bold, follows new output while [status] is running, with copy
 * and an optional clear action.
 *
 * @param exitCode shown next to a finished status.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Terminal(
    output: String,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.ai_terminal),
    status: TerminalStatus? = null,
    exitCode: Int? = null,
    maxHeight: Dp = 320.dp,
    onClear: (() -> Unit)? = null,
) {
    val dark = MaterialTheme.isDark
    val colors = MaterialTheme.colorScheme
    // A console reads as a console in both themes: always a dark surface.
    val background = if (dark) colors.surfaceContainerLowest else colors.inverseSurface
    val foreground = if (dark) colors.onSurface else colors.inverseOnSurface
    val text = remember(output, dark) { AnsiText.parse(output, foreground) }
    val scroll = rememberScrollState()
    LaunchedEffect(output, status) { if (status == TerminalStatus.RUNNING) scroll.scrollTo(scroll.maxValue) }

    Surface(color = background, contentColor = foreground, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth().testTag("terminal")) {
        androidx.compose.foundation.layout.Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            ) {
                Icon(Icons.Outlined.Terminal, null, Modifier.size(18.dp), tint = foreground.copy(alpha = 0.8f))
                Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1)
                when (status) {
                    TerminalStatus.RUNNING -> LoadingIndicator(Modifier.size(24.dp), color = foreground)
                    TerminalStatus.SUCCESS -> Icon(Icons.Outlined.CheckCircle, stringResource(R.string.ai_step_done), Modifier.size(18.dp), tint = AnsiText.Green)
                    TerminalStatus.ERROR -> Icon(Icons.Outlined.ErrorOutline, stringResource(R.string.ai_tool_error), Modifier.size(18.dp), tint = AnsiText.Red)
                    null -> Unit
                }
                if (exitCode != null && status != TerminalStatus.RUNNING) {
                    Text(stringResource(R.string.ai_exit_code, exitCode), style = MaterialTheme.typography.labelSmall, color = foreground.copy(alpha = 0.7f))
                }
                CopyButton({ AnsiText.strip(output) }, tint = foreground.copy(alpha = 0.8f))
                if (onClear != null) {
                    IconButton(onClick = onClear, shapes = IconButtonDefaults.shapes(), modifier = Modifier.compactIconButton()) {
                        Icon(Icons.Outlined.DeleteSweep, stringResource(R.string.ai_clear), Modifier.size(AiSize.compactIcon), tint = foreground.copy(alpha = 0.8f))
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .background(Color.Black.copy(alpha = 0.12f))
                    .verticalScroll(scroll)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                SelectionContainer { Text(text, style = AiType.code, softWrap = false) }
            }
        }
    }
}

/** Minimal ANSI SGR rendering: reset, bold, dim, and the 16 foreground colors. */
internal object AnsiText {
    val Red = Color(0xFFFF8A80)
    val Green = Color(0xFF7FD99A)
    private val palette = listOf(
        Color(0xFF5C5F66), Red, Green, Color(0xFFF7D774), Color(0xFF8AB4F8), Color(0xFFD7A6FF), Color(0xFF7FDBE8), Color(0xFFE4E1E6),
    )
    private val bright = palette.map { it.copy(alpha = 1f) }
    private val sgr = Regex("\u001B\\[([0-9;]*)m")
    private val other = Regex("\u001B\\[[0-9;?]*[A-Za-z]")

    fun strip(text: String) = other.replace(sgr.replace(text, ""), "")

    fun parse(text: String, default: Color): AnnotatedString = buildAnnotatedString {
        var color: Color? = null
        var bold = false
        var dim = false
        var last = 0
        fun flush(end: Int) {
            if (end <= last) return
            val chunk = other.replace(text.substring(last, end), "")
            withStyle(SpanStyle(color = (color ?: default).let { if (dim) it.copy(alpha = 0.6f) else it }, fontWeight = if (bold) FontWeight.Bold else null)) { append(chunk) }
        }
        for (m in sgr.findAll(text)) {
            flush(m.range.first)
            last = m.range.last + 1
            val codes = m.groupValues[1].split(';').mapNotNull { it.toIntOrNull() }.ifEmpty { listOf(0) }
            for (code in codes) when (code) {
                0 -> { color = null; bold = false; dim = false }
                1 -> bold = true
                2 -> dim = true
                22 -> { bold = false; dim = false }
                39 -> color = null
                in 30..37 -> color = palette[code - 30]
                in 90..97 -> color = bright[code - 90]
            }
        }
        flush(text.length)
    }
}
