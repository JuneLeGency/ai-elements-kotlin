package dev.ai.elements.core.model

/**
 * Output written for a terminal, as a terminal shows it: the control functions of ECMA-48 (ISO/IEC
 * 6429, "ANSI escape codes") in the subset xterm-compatible programs use for their output, applied
 * to a screen of lines with a cursor.
 *
 * - C0 controls: `\n` (new line), `\r` (back to the start of the line, so progress bars redraw in
 *   place), `\b` (back one column), `\t` (tab stops every 8 columns); others are dropped.
 * - CSI: SGR `m` (bold, dim, italic, underline, blink as nothing, inverse, conceal, strike, the 16
 *   colors and their bright forms, 256-color `38;5;n` and true color `38;2;r;g;b`, in `;` or `:`
 *   form, foreground and background); erase in line `K` and in display `J`; cursor up / down /
 *   forward / back `A` `B` `C` `D`, next / previous line `E` `F`, column `G`, position `H` / `f`
 *   (rows relative to the first line of the output). Private modes (`?25l`…) and the rest are ignored.
 * - OSC: `8` hyperlinks (OSC 8, as in iTerm2, GNOME Terminal and Windows Terminal) become [Style.link];
 *   other OSC strings (window titles…) are dropped. Other escape sequences are dropped.
 *
 * UI elements render [lines] with their styles; [plain] is what the terminal would show as text,
 * e.g. for a model, which should not pay for escape codes and redrawn progress bars.
 */
class TerminalText private constructor(
    val lines: List<List<Span>>,
    /** Lines that scrolled out of the kept [lines] (see [parse]'s `maxLines`). */
    val droppedLines: Int = 0,
) {

    /** A run of text with one style. */
    data class Span(val text: String, val style: Style)

    /** How text is drawn; null colors are the terminal's defaults. */
    data class Style(
        val foreground: Color? = null,
        val background: Color? = null,
        val bold: Boolean = false,
        val dim: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val inverse: Boolean = false,
        val conceal: Boolean = false,
        val strikethrough: Boolean = false,
        /** The OSC 8 hyperlink this text belongs to. */
        val link: String? = null,
    ) {
        companion object {
            val Default = Style()
        }
    }

    /** A terminal color: one of the 256 indexed colors (0–15 are the theme's 16) or a 24-bit one. */
    sealed interface Color {
        data class Indexed(val index: Int) : Color
        data class Rgb(val red: Int, val green: Int, val blue: Int) : Color
    }

    /** The text as the terminal shows it, without styles; trailing blanks are trimmed from each line. */
    fun plain(): String = lines.joinToString("\n") { line -> line.joinToString("") { it.text }.trimEnd() }

    companion object {
        /** Interprets [output]; [maxLines] is the scrollback: very long output keeps its last lines. */
        fun parse(output: String, maxLines: Int = 10_000): TerminalText = Screen(maxLines).apply { write(output) }.text()

        /** [output] as the terminal would show it, as plain text (see [plain]). */
        fun plain(output: String): String =
            if (output.none { it == '\u001b' || it == '\r' || it == '\b' || it == '\u009b' }) output else parse(output).plain()

        /** The RGB value of 256-color [index] 16–255 (xterm's 6×6×6 cube and gray ramp); 0–15 are themed. */
        fun indexedRgb(index: Int): Color.Rgb = when {
            index < 16 -> error("0–15 are the theme's colors")
            index < 232 -> {
                val i = index - 16
                fun level(v: Int) = if (v == 0) 0 else 55 + v * 40
                Color.Rgb(level(i / 36), level(i / 6 % 6), level(i % 6))
            }
            else -> (8 + (index - 232) * 10).let { Color.Rgb(it, it, it) }
        }
    }

    private class Cell(val text: String, val style: Style)

    private class Screen(private val maxLines: Int) {
        private val rows = mutableListOf(mutableListOf<Cell?>())
        private var row = 0
        private var col = 0
        private var style = Style.Default
        private var dropped = 0

        fun write(s: String) {
            var i = 0
            while (i < s.length) {
                val c = s[i]
                when {
                    c == '\u001b' && i + 1 < s.length -> i = escape(s, i + 1)
                    c == '\u009b' -> i = csi(s, i + 1)
                    c == '\n' -> { lineFeed(); col = 0; i++ }
                    c == '\r' -> { col = 0; i++ }
                    c == '\b' -> { col = (col - 1).coerceAtLeast(0); i++ }
                    c == '\t' -> { repeat(8 - col % 8) { put(" ") }; i++ }
                    c < ' ' || c == '\u007f' -> i++
                    Character.isHighSurrogate(c) && i + 1 < s.length -> { put(s.substring(i, i + 2)); i += 2 }
                    else -> { put(c.toString()); i++ }
                }
            }
        }

        /** After ESC at [start - 1]: returns the index after the sequence. */
        private fun escape(s: String, start: Int): Int = when (s[start]) {
            '[' -> csi(s, start + 1)
            ']' -> osc(s, start + 1)
            'P', 'X', '^', '_' -> stringEnd(s, start + 1).second // DCS, SOS, PM, APC: dropped
            else -> {
                // nF sequences (ESC, intermediates 0x20–0x2F, a final byte), e.g. `ESC ( B`.
                var j = start
                while (j < s.length && s[j] in ' '..'/') j++
                (j + 1).coerceAtMost(s.length)
            }
        }

        private fun csi(s: String, start: Int): Int {
            var j = start
            while (j < s.length && s[j] in '0'..'?') j++ // parameter bytes
            val params = s.substring(start, j)
            while (j < s.length && s[j] in ' '..'/') j++ // intermediate bytes
            if (j >= s.length) return s.length
            val final = s[j]
            if (params.startsWith('?') || params.startsWith('>') || params.startsWith('<') || params.startsWith('=')) return j + 1
            val numbers = params.split(';').map { p -> p.substringBefore(':').toIntOrNull() }
            fun n(k: Int, default: Int = 1) = numbers.getOrNull(k)?.takeIf { it > 0 } ?: default
            when (final) {
                'm' -> sgr(params)
                'K' -> eraseInLine(numbers.getOrNull(0) ?: 0)
                'J' -> eraseInDisplay(numbers.getOrNull(0) ?: 0)
                'A' -> row = (row - n(0)).coerceAtLeast(0)
                'B' -> repeat(n(0)) { lineFeed() }
                'C' -> col += n(0)
                'D' -> col = (col - n(0)).coerceAtLeast(0)
                'E' -> { repeat(n(0)) { lineFeed() }; col = 0 }
                'F' -> { row = (row - n(0)).coerceAtLeast(0); col = 0 }
                'G' -> col = n(0) - 1
                'H', 'f' -> {
                    row = (n(0) - 1 - dropped).coerceAtLeast(0)
                    while (rows.size <= row) rows += mutableListOf<Cell?>()
                    col = n(1) - 1
                }
            }
            return j + 1
        }

        private fun osc(s: String, start: Int): Int {
            val (body, end) = stringEnd(s, start)
            if (body.startsWith("8;")) {
                val uri = body.substringAfter(';').substringAfter(';')
                style = style.copy(link = uri.ifEmpty { null })
            }
            return end
        }

        /** The control string from [start] to its terminator (BEL or ST) and the index after it. */
        private fun stringEnd(s: String, start: Int): Pair<String, Int> {
            var j = start
            while (j < s.length) {
                when {
                    s[j] == '\u0007' || s[j] == '\u009c' -> return s.substring(start, j) to j + 1
                    s[j] == '\u001b' && j + 1 < s.length && s[j + 1] == '\\' -> return s.substring(start, j) to j + 2
                }
                j++
            }
            return s.substring(start) to s.length
        }

        private fun sgr(params: String) {
            if (params.isEmpty()) { style = Style.Default.copy(link = style.link); return }
            val groups = params.split(';')
            var k = 0
            fun int(v: String?) = v?.toIntOrNull() ?: 0
            while (k < groups.size) {
                val sub = groups[k].split(':')
                val code = int(sub[0])
                when (code) {
                    0 -> style = Style.Default.copy(link = style.link)
                    1 -> style = style.copy(bold = true)
                    2 -> style = style.copy(dim = true)
                    3 -> style = style.copy(italic = true)
                    4 -> style = style.copy(underline = sub.getOrNull(1) != "0")
                    7 -> style = style.copy(inverse = true)
                    8 -> style = style.copy(conceal = true)
                    9 -> style = style.copy(strikethrough = true)
                    21 -> style = style.copy(underline = true)
                    22 -> style = style.copy(bold = false, dim = false)
                    23 -> style = style.copy(italic = false)
                    24 -> style = style.copy(underline = false)
                    27 -> style = style.copy(inverse = false)
                    28 -> style = style.copy(conceal = false)
                    29 -> style = style.copy(strikethrough = false)
                    in 30..37 -> style = style.copy(foreground = Color.Indexed(code - 30))
                    39 -> style = style.copy(foreground = null)
                    in 40..47 -> style = style.copy(background = Color.Indexed(code - 40))
                    49 -> style = style.copy(background = null)
                    in 90..97 -> style = style.copy(foreground = Color.Indexed(code - 90 + 8))
                    in 100..107 -> style = style.copy(background = Color.Indexed(code - 100 + 8))
                    38, 48 -> {
                        // Extended colors: `38;5;n` / `38;2;r;g;b`, or the ITU T.416 colon forms `38:5:n` / `38:2:[id]:r:g:b`.
                        val (color, used) = if (sub.size > 1) {
                            val args = sub.drop(1)
                            when (int(args[0])) {
                                5 -> args.getOrNull(1)?.let { Color.Indexed(int(it).coerceIn(0, 255)) } to 0
                                2 -> args.drop(1).takeLast(3).takeIf { it.size == 3 }?.let { (r, g, b) -> Color.Rgb(int(r), int(g), int(b)) } to 0
                                else -> null to 0
                            }
                        } else when (int(groups.getOrNull(k + 1))) {
                            5 -> groups.getOrNull(k + 2)?.let { Color.Indexed(int(it).coerceIn(0, 255)) } to 2
                            2 -> (if (k + 4 < groups.size) Color.Rgb(int(groups[k + 2]), int(groups[k + 3]), int(groups[k + 4])) else null) to 4
                            else -> null to 1
                        }
                        k += used
                        if (color != null) style = if (code == 38) style.copy(foreground = color) else style.copy(background = color)
                    }
                }
                k++
            }
        }

        private fun put(text: String) {
            val line = rows[row]
            while (line.size < col) line += null
            val cell = Cell(text, style)
            if (col < line.size) line[col] = cell else line += cell
            col++
        }

        private fun lineFeed() {
            row++
            if (row == rows.size) rows += mutableListOf<Cell?>()
            if (rows.size > maxLines) {
                rows.removeAt(0)
                row--
                dropped++
            }
        }

        private fun eraseInLine(mode: Int) {
            val line = rows[row]
            when (mode) {
                0 -> while (line.size > col) line.removeAt(line.lastIndex)
                1 -> for (x in 0..minOf(col, line.lastIndex)) line[x] = null
                2 -> line.clear()
            }
        }

        private fun eraseInDisplay(mode: Int) {
            when (mode) {
                0 -> { eraseInLine(0); while (rows.size > row + 1) rows.removeAt(rows.lastIndex) }
                1 -> { for (r in 0 until row) rows[r].clear(); eraseInLine(1) }
                else -> { rows.forEach { it.clear() } }
            }
        }

        fun text(): TerminalText {
            // A trailing new line ends the last line rather than starting an empty one.
            val used = if (rows.size > 1 && rows.last().isEmpty()) rows.dropLast(1) else rows
            return TerminalText(droppedLines = dropped, lines = used.map { line ->
                val spans = mutableListOf<Span>()
                val sb = StringBuilder()
                var current: Style? = null
                line.forEach { cell ->
                    val s = cell?.style ?: Style.Default
                    if (s != current && sb.isNotEmpty()) { spans += Span(sb.toString(), current!!); sb.clear() }
                    current = s
                    sb.append(cell?.text ?: " ")
                }
                if (sb.isNotEmpty()) spans += Span(sb.toString(), current!!)
                spans
            })
        }
    }
}
