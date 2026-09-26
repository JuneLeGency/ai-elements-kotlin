package dev.ai.elements.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownStreamingTest {

    @Test
    fun split_keepsFencesListsAndTablesWhole() {
        val md = "# Title\n\nPara one.\n\n```kotlin\nval a = 1\n\nval b = 2\n```\n\n- a\n\n- b\n\n| x | y |\n|---|---|\n| 1 | 2 |\n\nEnd."
        assertEquals(
            listOf("# Title", "Para one.", "```kotlin\nval a = 1\n\nval b = 2\n```", "- a\n\n- b", "| x | y |\n|---|---|\n| 1 | 2 |", "End."),
            MarkdownStreaming.split(md),
        )
    }

    @Test
    fun split_finishedBlocksAreStableWhileTheTailGrows() {
        val a = MarkdownStreaming.split("First para.\n\nSecond is gro")
        val b = MarkdownStreaming.split("First para.\n\nSecond is growing now")
        assertEquals(a.first(), b.first())
    }

    @Test
    fun repairTail_closesOrHidesUnfinishedMarkup() {
        assertEquals("1. **Reaso**", MarkdownStreaming.repairTail("1. **Reaso"))
        assertEquals("Start", MarkdownStreaming.repairTail("Start **"))
        assertEquals("Run `ls -l`", MarkdownStreaming.repairTail("Run `ls -l"))
        assertEquals("See the docs", MarkdownStreaming.repairTail("See [the docs](https://exam"))
        assertEquals("See the", MarkdownStreaming.repairTail("See [the"))
        assertEquals("Done [ok](https://a.b).", MarkdownStreaming.repairTail("Done [ok](https://a.b)."))
        assertEquals("~~old~~", MarkdownStreaming.repairTail("~~old"))
        assertEquals("```py\nprint(1", MarkdownStreaming.repairTail("```py\nprint(1"))
    }
}
