package dev.ai.elements.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatexPreprocessorTest {

    @Test
    fun inlineMath_becomesUnicode() {
        assertEquals("The result of 1234 × 5678 is **7,006,652**.", LatexPreprocessor.process("The result of \$1234 \\times 5678\$ is **7,006,652**."))
        assertEquals("Area: πr² and x₁ ≤ y", LatexPreprocessor.process("Area: \$\\pi r^2\$ and \\(x_1 \\leq y\\)"))
        assertEquals("(a+b)/2", LatexPreprocessor.toUnicode("\\frac{a+b}{2}"))
        // Real qwen3 output pads the delimiters.
        assertEquals("The result of 1234 × 5678 is", LatexPreprocessor.process("The result of \$ 1234 \\times 5678 \$ is"))
    }

    @Test
    fun displayMath_becomesMathFence() {
        val out = LatexPreprocessor.process("Euler:\n\$\$\ne^{i\\pi} + 1 = 0\n\$\$\ndone")
        assertTrue(out, out.contains("```math\ne^{i\\pi} + 1 = 0\n```"))
        assertTrue(LatexPreprocessor.process("\\[ a^2 + b^2 = c^2 \\]").contains("```math\na^2 + b^2 = c^2\n```"))
    }

    @Test
    fun codeAndPrices_areUntouched() {
        val code = "```bash\necho \$HOME \$PATH\n```"
        assertEquals(code, LatexPreprocessor.process(code))
        assertEquals("Use `\$x\$` literally", LatexPreprocessor.process("Use `\$x\$` literally"))
        assertEquals("It costs \$5 and \$10.", LatexPreprocessor.process("It costs \$5 and \$10."))
    }
}
