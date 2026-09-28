package dev.ai.elements.ui.code

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersTest {
    @Test fun ansi_colorsBoldAndReset() {
        val text = AnsiText.parse("\u001B[1mbold\u001B[0m plain \u001B[31mred\u001B[39m back", Color.White, Color.Black)
        assertEquals("bold plain red back", text.text)
        val bold = text.spanStyles.first { text.text.substring(it.start, it.end) == "bold" }
        assertEquals(FontWeight.Bold, bold.item.fontWeight)
        val red = text.spanStyles.first { text.text.substring(it.start, it.end) == "red" }
        assertEquals(Color(0xFFCD3131), red.item.color) // VS Code's dark terminal red
        val back = text.spanStyles.first { text.text.substring(it.start, it.end) == " back" }
        assertEquals(Color.White, back.item.color)
    }

    @Test fun ansi_stripsCursorSequences() {
        assertEquals("done", AnsiText.strip("\u001B[2K\u001B[1Gdone"))
        assertEquals("ok", AnsiText.parse("\u001B[?25lok\u001B[?25h", Color.White, Color.Black).text)
    }

    @Test fun ansi_lightPaletteLinksAndScrollback() {
        val light = AnsiText.parse("\u001B[33my\u001B[0m", Color.Black, Color.White, darkSurface = false)
        assertEquals(Color(0xFF949800), light.spanStyles.single().item.color)
        val link = AnsiText.parse("\u001B]8;;https://example.com\u001B\\docs\u001B]8;;\u001B\\", Color.White, Color.Black)
        assertEquals("docs", link.text)
        assertEquals("https://example.com", (link.getLinkAnnotations(0, 4).single().item as androidx.compose.ui.text.LinkAnnotation.Url).url)
        val long = AnsiText.parse((1..50).joinToString("\n") { "l$it" }, Color.White, Color.Black, scrollback = 10, hiddenNote = { "hidden $it" })
        assertEquals("hidden 40\nl41", long.text.lines().take(2).joinToString("\n"))
    }

    @Test fun stackTrace_jvm() {
        val trace = ParsedStackTrace.parse(
            """
            java.lang.IllegalStateException: boom
            	at com.example.app.Repo.load(Repo.kt:12)
            	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:108)
            Caused by: java.io.IOException: offline
            	... 3 more
            """.trimIndent(),
        )
        assertEquals("java.lang.IllegalStateException", trace.type)
        assertEquals("boom", trace.message)
        assertTrue(trace.frames[0].app)
        assertFalse(trace.frames[1].app)
        assertTrue(trace.frames.any { it.text.startsWith("Caused by") })
    }

    @Test fun stackTrace_python() {
        val trace = ParsedStackTrace.parse(
            """
            Traceback (most recent call last):
              File "/app/main.py", line 3, in <module>
                run()
              File "/usr/lib/python3.12/json/decoder.py", line 337, in decode
            ValueError: bad json
            """.trimIndent(),
        )
        assertEquals(2, trace.frames.size)
        assertTrue(trace.frames[0].app)
        assertTrue("source line folded into its frame", trace.frames[0].text.contains("run()"))
        assertFalse(trace.frames[1].app)
    }

    @Test fun durations() {
        assertEquals("850 ms", formatDuration(850))
        assertEquals("1.5 s", formatDuration(1_500))
        assertEquals("2 m 05 s", formatDuration(125_000))
    }
}
