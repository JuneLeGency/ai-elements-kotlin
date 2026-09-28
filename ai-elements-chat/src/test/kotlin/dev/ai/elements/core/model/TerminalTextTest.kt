package dev.ai.elements.core.model

import dev.ai.elements.core.model.TerminalText.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TerminalText] on output recorded from real programs (`src/test/resources/terminal`): `ls -G`
 * and `git` with color forced, curl's `#` progress bar, and a Rich progress bar with 256 colors,
 * `ESC[2K` redraws, OSC 8 links and backgrounds.
 */
class TerminalTextTest {
    private fun fixture(name: String) = javaClass.getResource("/terminal/$name")!!.readText()

    private fun TerminalText.spans() = lines.flatten()

    @Test fun ls_colorsAndBold() {
        val text = TerminalText.parse(fixture("ls-color.txt"))
        assertTrue(text.plain().lines().contains("guides"))
        val guides = text.spans().first { it.text == "guides" }
        assertEquals(Color.Indexed(6), guides.style.foreground)
        assertTrue(guides.style.bold)
        assertFalse(text.plain().contains('\u001b'))
    }

    @Test fun gitLog_decorations() {
        val text = TerminalText.parse(fixture("git-log.txt"))
        val head = text.spans().first { it.text == "HEAD" }
        assertEquals(Color.Indexed(6), head.style.foreground)
        assertTrue(head.style.bold)
        assertEquals(3, text.lines.size)
    }

    @Test fun gitDiff_addedAndRemovedLines() {
        val text = TerminalText.parse(fixture("git-diff.txt"))
        val added = text.lines.first { line -> line.joinToString("") { it.text }.startsWith("+    ") }
        assertEquals(Color.Indexed(2), added.first().style.foreground)
        assertTrue(text.plain().lines().first().startsWith("diff --git"))
    }

    @Test fun carriageReturn_redrawsTheLineInPlace() {
        val text = TerminalText.parse(fixture("curl-progress.txt"))
        assertEquals(1, text.lines.size)
        assertTrue(text.plain().endsWith("100.0%"))
        assertEquals("100%", TerminalText.plain("  0%\r 50%\r100%"))
        assertEquals("abX", TerminalText.plain("abc\bX"))
    }

    @Test fun rich_progressBarLinkAndBackground() {
        val text = TerminalText.parse(fixture("rich-progress.txt"))
        val lines = text.plain().lines()
        // Three redraws (`\r ESC[2K`) leave one finished bar, then the summary line.
        assertEquals(2, lines.size)
        assertTrue(lines[0], lines[0].startsWith("Downloading") && lines[0].contains("100%"))
        assertEquals("Done docs  err", lines[1])
        // The finished bar in true color (`38;2;114;156;31`); unfinished parts were 256-color gray.
        val bar = text.spans().first { it.text.startsWith("━") }
        assertEquals(Color.Rgb(114, 156, 31), bar.style.foreground)
        assertEquals("https://example.com", text.spans().first { it.text == "docs" }.style.link)
        assertEquals(null, text.spans().first { it.text == "Done" }.style.link)
        assertEquals(Color.Indexed(1), text.spans().first { it.text.contains("err") }.style.background)
    }

    @Test fun sgr_extendedColorsAndAttributes() {
        val spans = TerminalText.parse("\u001b[38;2;10;20;30;48;5;196;3;4;9mX\u001b[38:2::1:2:3;7mY\u001b[23;24;29;27;39;49mZ\u001b[95mB").spans()
        assertEquals(Color.Rgb(10, 20, 30), spans[0].style.foreground)
        assertEquals(Color.Indexed(196), spans[0].style.background)
        assertTrue(spans[0].style.italic && spans[0].style.underline && spans[0].style.strikethrough)
        assertEquals(Color.Rgb(1, 2, 3), spans[1].style.foreground)
        assertTrue(spans[1].style.inverse)
        assertEquals(TerminalText.Style.Default, spans[2].style)
        assertEquals(Color.Indexed(13), spans[3].style.foreground)
        assertEquals(Color.Rgb(255, 0, 0), TerminalText.indexedRgb(196))
        assertEquals(Color.Rgb(8, 8, 8), TerminalText.indexedRgb(232))
    }

    @Test fun cursorMovesAndErases() {
        // Two progress lines redrawn with cursor-up, as npm and docker do.
        val out = "a 10%\nb 10%\n\u001b[2A\u001b[2Ka 90%\n\u001b[2Kb 90%\n"
        assertEquals("a 90%\nb 90%", TerminalText.plain(out))
        // Back 3, erase to the end, return and overwrite: as a terminal shows it.
        assertEquals("XYc", TerminalText.plain("abcdef\u001b[3D\u001b[K\rXY"))
        assertEquals("title gone", TerminalText.plain("\u001b]0;window title\u0007title gone"))
        assertEquals("x", TerminalText.plain("\u001b[?25l\u001b(Bx\u001b[?25h"))
        assertEquals("a       b", TerminalText.parse("a\tb").plain())
    }

    @Test fun plainText_isUntouchedWithoutControls() {
        val s = "plain\noutput  "
        assertTrue(TerminalText.plain(s) === s)
    }
}
