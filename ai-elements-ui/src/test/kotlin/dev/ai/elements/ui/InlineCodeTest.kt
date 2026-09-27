package dev.ai.elements.ui

import dev.ai.elements.ui.markdown.keepShortCodeTogether
import org.junit.Assert.assertEquals
import org.junit.Test

class InlineCodeTest {
    @Test
    fun shortInlineCode_keepsItsWordsTogether_fencesUntouched() {
        val md = "Use `inline code` and `a much longer piece of inline code here`.\n```\nval a = `x y`\n```\n`p q`"
        val out = keepShortCodeTogether(md)
        assertEquals(
            "Use `inline code` and `a much longer piece of inline code here`.\n```\nval a = `x y`\n```\n`p q`",
            out,
        )
    }
}
