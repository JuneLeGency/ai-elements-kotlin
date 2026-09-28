package dev.ai.elements.ui.code

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ai.elements.core.model.TerminalText
import dev.ai.elements.ui.R
import dev.ai.elements.ui.icons.AiIcons
import dev.ai.elements.ui.theme.AiSize
import dev.ai.elements.ui.theme.AiType
import dev.ai.elements.ui.theme.compactIconButton
import dev.ai.elements.ui.theme.fadingHorizontalScroll
import dev.ai.elements.ui.theme.isDark

/** Where a command is in its life. */
enum class TerminalStatus { RUNNING, SUCCESS, ERROR }

/**
 * Command output (AI Elements `<Terminal>`): a dark console that renders ANSI
 * colors and bold, follows new output while [status] is running, with copy
 * and an optional clear action.
 *
 * @param exitCode shown next to a finished status.
 * @param scrollback lines kept from very long output (the last ones), as a terminal's scrollback.
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
    scrollback: Int = 1_000,
) {
    val dark = MaterialTheme.isDark
    val colors = MaterialTheme.colorScheme
    // A console reads as a console in both themes: always a dark surface.
    val background = if (dark) colors.surfaceContainerLowest else colors.inverseSurface
    val foreground = if (dark) colors.onSurface else colors.inverseOnSurface
    val hidden = stringResource(R.string.ai_earlier_lines)
    val text = remember(output, dark, scrollback) {
        AnsiText.parse(output, foreground, background, scrollback = scrollback, hiddenNote = { n -> hidden.format(n) })
    }
    val scroll = rememberScrollState()
    LaunchedEffect(output, status) { if (status == TerminalStatus.RUNNING) scroll.scrollTo(scroll.maxValue) }

    Surface(color = background, contentColor = foreground, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth().testTag("terminal")) {
        androidx.compose.foundation.layout.Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            ) {
                Icon(AiIcons.Terminal, null, Modifier.size(18.dp), tint = foreground.copy(alpha = 0.8f))
                Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1)
                when (status) {
                    TerminalStatus.RUNNING -> LoadingIndicator(Modifier.size(24.dp), color = foreground)
                    TerminalStatus.SUCCESS -> Icon(AiIcons.CheckCircle, stringResource(R.string.ai_step_done), Modifier.size(18.dp), tint = AnsiText.Green)
                    TerminalStatus.ERROR -> Icon(AiIcons.ErrorOutline, stringResource(R.string.ai_tool_error), Modifier.size(18.dp), tint = AnsiText.Red)
                    null -> Unit
                }
                if (exitCode != null && status != TerminalStatus.RUNNING) {
                    Text(stringResource(R.string.ai_exit_code, exitCode), style = MaterialTheme.typography.labelSmall, color = foreground.copy(alpha = 0.7f))
                }
                CopyButton({ AnsiText.strip(output) }, tint = foreground.copy(alpha = 0.8f))
                if (onClear != null) {
                    IconButton(onClick = onClear, shapes = IconButtonDefaults.shapes(), modifier = Modifier.compactIconButton()) {
                        Icon(AiIcons.DeleteSweep, stringResource(R.string.ai_clear), Modifier.size(AiSize.compactIcon), tint = foreground.copy(alpha = 0.8f))
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .background(Color.Black.copy(alpha = 0.12f))
                    .verticalScroll(scroll)
                    .fadingHorizontalScroll()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                SelectionContainer { Text(text, style = AiType.code, softWrap = false) }
            }
        }
    }
}

/**
 * Terminal output ([TerminalText]: colors, attributes, `\r` redraws, cursor moves, OSC 8 links) as
 * styled text. The 16 theme colors follow VS Code's integrated terminal: a dark palette for the
 * console's dark surface, a light one for output shown on light surfaces.
 */
internal object AnsiText {
    val Red = Color(0xFFF14C4C)
    val Green = Color(0xFF23D18B)

    private val dark = listOf(
        0xFF000000, 0xFFCD3131, 0xFF0DBC79, 0xFFE5E510, 0xFF2472C8, 0xFFBC3FBC, 0xFF11A8CD, 0xFFE5E5E5,
        0xFF666666, 0xFFF14C4C, 0xFF23D18B, 0xFFF5F543, 0xFF3B8EEA, 0xFFD670D6, 0xFF29B8DB, 0xFFFFFFFF,
    ).map(::Color)
    private val light = listOf(
        0xFF000000, 0xFFCD3131, 0xFF00BC00, 0xFF949800, 0xFF0451A5, 0xFFBC05BC, 0xFF0598BC, 0xFF555555,
        0xFF666666, 0xFFCD3131, 0xFF14CE14, 0xFFB5BA00, 0xFF0451A5, 0xFFBC05BC, 0xFF0598BC, 0xFFA5A5A5,
    ).map(::Color)

    fun strip(text: String) = TerminalText.plain(text)

    /** Whether [text] has anything a terminal would interpret (escape sequences, redraws). */
    fun isTerminalOutput(text: String) = text.any { it == '\u001b' || it == '\r' || it == '\b' }

    /**
     * [text] styled; with [scrollback], only its last lines (terminals keep a scrollback of about a
     * thousand lines: xterm, VS Code), preceded by [hiddenNote] with the number of lines left out.
     */
    fun parse(
        text: String,
        default: Color,
        background: Color,
        darkSurface: Boolean = true,
        scrollback: Int = Int.MAX_VALUE,
        hiddenNote: (Int) -> String = { "… $it" },
    ): AnnotatedString {
        val palette = if (darkSurface) dark else light
        fun color(c: TerminalText.Color?): Color? = when (c) {
            null -> null
            is TerminalText.Color.Indexed -> if (c.index < 16) palette[c.index] else TerminalText.indexedRgb(c.index).let { Color(it.red, it.green, it.blue) }
            is TerminalText.Color.Rgb -> Color(c.red, c.green, c.blue)
        }
        val terminal = TerminalText.parse(text, maxLines = scrollback)
        return buildAnnotatedString {
            if (terminal.droppedLines > 0) {
                withStyle(SpanStyle(color = default.copy(alpha = 0.6f), fontStyle = FontStyle.Italic)) { append(hiddenNote(terminal.droppedLines)) }
                append('\n')
            }
            terminal.lines.forEachIndexed { i, line ->
                if (i > 0) append('\n')
                line.forEach { span ->
                    val st = span.style
                    var fg = color(st.foreground) ?: default
                    var bg = color(st.background)
                    if (st.bold && st.foreground is TerminalText.Color.Indexed && (st.foreground as TerminalText.Color.Indexed).index < 8) {
                        fg = palette[(st.foreground as TerminalText.Color.Indexed).index + 8] // bold as bright, as xterm does
                    }
                    if (st.inverse) { val f = fg; fg = bg ?: background; bg = f }
                    if (st.dim) fg = fg.copy(alpha = fg.alpha * 0.6f)
                    if (st.conceal) fg = Color.Transparent
                    val style = SpanStyle(
                        color = fg,
                        background = bg ?: Color.Unspecified,
                        fontWeight = if (st.bold) FontWeight.Bold else null,
                        fontStyle = if (st.italic) FontStyle.Italic else null,
                        textDecoration = listOfNotNull(
                            TextDecoration.Underline.takeIf { st.underline || st.link != null },
                            TextDecoration.LineThrough.takeIf { st.strikethrough },
                        ).takeIf { it.isNotEmpty() }?.let(TextDecoration::combine),
                    )
                    val link = st.link
                    if (link != null) withLink(LinkAnnotation.Url(link)) { withStyle(style) { append(span.text) } }
                    else withStyle(style) { append(span.text) }
                }
            }
        }
    }
}
