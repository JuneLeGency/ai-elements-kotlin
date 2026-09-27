package dev.ai.elements.ui

import dev.ai.elements.ui.voice.lastSentenceEnd
import dev.ai.elements.ui.voice.speakableText
import org.junit.Assert.assertEquals
import org.junit.Test

/** What "Read aloud" and voice mode say. */
class SpeechTextTest {
    @Test fun markdown_readsAsProse() {
        val markdown = """
            ## Plan
            - **Read** the [docs](https://example.com) and `ChatController` [1].
            ```kotlin
            val x = 1
            ```
            ```mermaid
            flowchart LR
            ```
            | Name | Web |
            |---|---|
            | Message | yes |
            Done!
        """.trimIndent()
        assertEquals(
            listOf("Plan", "Read the docs and ChatController.", "Name, Web", "Message, yes", "Done!"),
            speakableText(markdown).lines().map { it.trim() }.filter { it.isNotEmpty() },
        )
    }

    @Test fun streamingReply_readsCompleteSentencesOnly() {
        val text = "First sentence. Second one! Third is still"
        assertEquals("First sentence. Second one!".length, text.lastSentenceEnd(0))
        assertEquals(0, "No end yet".lastSentenceEnd(0))
        assertEquals("你好。".length, "你好。再见".lastSentenceEnd(0))
        assertEquals(0, "Version 3.5 is out".lastSentenceEnd(0)) // a decimal point is not an end
    }
}
