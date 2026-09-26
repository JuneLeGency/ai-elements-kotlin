package dev.ai.elements.ui.markdown

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonPrimitive

/**
 * Display math rendered with the bundled, offline KaTeX (like Streamdown in
 * AI Elements' `<Response>`). Falls back to the TeX source if KaTeX fails.
 */
@Composable
fun MathBlock(tex: String, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurface
    val source = tex.trim()
    val cacheKey = "katex|${color.toArgb()}|$source"
    val html = remember(cacheKey) {
        val encoded = JsonPrimitive(source).toString().replace("</", "<\\/")
        val hex = "#%06X".format(color.toArgb() and 0xFFFFFF)
        """
        <!doctype html><html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
        $ERROR_BRIDGE_JS
        <link rel="stylesheet" href="katex/katex.min.css">
        <script src="katex/katex.min.js"></script>
        <script>$REPORT_HEIGHT_JS</script>
        <style>
          html,body{margin:0;padding:0;background:transparent;color:$hex;}
          #c{overflow-x:auto;overflow-y:hidden;padding:2px 0;}
          .katex-display{margin:0;}
          .katex{font-size:1.15em;}
        </style>
        </head><body><div id="c"></div>
        <script>
          try {
            var c = document.getElementById('c');
            katex.render($encoded, c, { displayMode: true, throwOnError: true, output: 'html' });
            aiReportHeight(c);
          } catch (e) { Bridge.onError(String((e && e.message) || e)); }
        </script></body></html>
        """.trimIndent()
    }
    AutoHeightWebView(
        html = html,
        cacheKey = cacheKey,
        fitWidth = true,
        placeholderHeight = 48.dp,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("math"),
    ) { CodeBlock(source, "latex") }
}

/**
 * Rewrites LaTeX in model output so the Markdown renderer can show it:
 * `$$…$$` / `\[…\]` blocks become ```` ```math ```` fences (→ [MathBlock]) and
 * inline `$…$` / `\(…\)` become Unicode (`\times` → ×, `x^2` → x²…). Fenced
 * code and inline code spans are left untouched.
 */
internal object LatexPreprocessor {
    private val displayDollar = Regex("""\$\$([\s\S]+?)\$\$""")
    private val displayBracket = Regex("""\\\[([\s\S]+?)\\\]""")
    private val inlineParen = Regex("""\\\((.+?)\\\)""")
    // $…$ with no space just inside the delimiters, so prices like "$5 and $10" survive…
    private val inlineDollar = Regex("""(?<![\\$\w])\$(?!\s)([^$\n]+?)(?<!\s)\$(?![\w$])""")

    // …or padded ("$ 1234 \times 5678 $", common in LLM output) when it contains a TeX command.
    private val inlineDollarPadded = Regex("""(?<![\\$\w])\$\s+([^$\n]*\\[A-Za-z][^$\n]*?)\s+\$(?![\w$])""")

    fun process(markdown: String): String {
        if ('$' !in markdown && "\\(" !in markdown && "\\[" !in markdown) return markdown
        val out = StringBuilder()
        var inFence = false
        val prose = StringBuilder()
        fun flushProse() {
            if (prose.isNotEmpty()) out.append(processProse(prose.toString()))
            prose.clear()
        }
        markdown.split('\n').forEachIndexed { index, line ->
            val newline = if (index == 0) "" else "\n"
            if (line.trimStart().startsWith("```") || line.trimStart().startsWith("~~~")) {
                flushProse()
                inFence = !inFence
                out.append(newline).append(line)
            } else if (inFence) {
                out.append(newline).append(line)
            } else {
                prose.append(newline).append(line)
            }
        }
        flushProse()
        return out.toString()
    }

    private fun processProse(text: String): String {
        val fenced = displayBracket.replace(displayDollar.replace(text) { mathFence(it.groupValues[1]) }) {
            mathFence(it.groupValues[1])
        }
        // Keep inline code spans verbatim while converting inline math.
        return fenced.split('`').mapIndexed { i, chunk ->
            if (i % 2 == 1) chunk
            else listOf(inlineParen, inlineDollarPadded, inlineDollar).fold(chunk) { acc, regex ->
                regex.replace(acc) { toUnicode(it.groupValues[1]) }
            }
        }.joinToString("`")
    }

    private fun mathFence(tex: String) = "\n```math\n${tex.trim()}\n```\n"

    private val symbols = mapOf(
        "\\times" to "×", "\\cdot" to "·", "\\div" to "÷", "\\pm" to "±", "\\mp" to "∓",
        "\\leq" to "≤", "\\le" to "≤", "\\geq" to "≥", "\\ge" to "≥", "\\neq" to "≠", "\\ne" to "≠",
        "\\approx" to "≈", "\\equiv" to "≡", "\\sim" to "∼", "\\propto" to "∝", "\\infty" to "∞",
        "\\sum" to "∑", "\\prod" to "∏", "\\int" to "∫", "\\partial" to "∂", "\\nabla" to "∇",
        "\\rightarrow" to "→", "\\to" to "→", "\\leftarrow" to "←", "\\Rightarrow" to "⇒", "\\Leftrightarrow" to "⇔",
        "\\in" to "∈", "\\notin" to "∉", "\\subset" to "⊂", "\\cup" to "∪", "\\cap" to "∩", "\\forall" to "∀",
        "\\exists" to "∃", "\\emptyset" to "∅", "\\degree" to "°", "\\circ" to "°", "\\ldots" to "…", "\\dots" to "…",
        "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ", "\\epsilon" to "ε", "\\theta" to "θ",
        "\\lambda" to "λ", "\\mu" to "μ", "\\pi" to "π", "\\sigma" to "σ", "\\phi" to "φ", "\\omega" to "ω",
        "\\Delta" to "Δ", "\\Sigma" to "Σ", "\\Omega" to "Ω", "\\Pi" to "Π",
        "\\left" to "", "\\right" to "", "\\," to " ", "\\;" to " ", "\\quad" to "  ", "\\!" to "",
    )
    private val superscripts = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵', '6' to '⁶', '7' to '⁷',
        '8' to '⁸', '9' to '⁹', '+' to '⁺', '-' to '⁻', 'n' to 'ⁿ', 'i' to 'ⁱ', '(' to '⁽', ')' to '⁾',
    )
    private val subscripts = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄', '5' to '₅', '6' to '₆', '7' to '₇',
        '8' to '₈', '9' to '₉', '+' to '₊', '-' to '₋', 'i' to 'ᵢ', 'n' to 'ₙ',
    )

    internal fun toUnicode(tex: String): String {
        var s = tex
        s = Regex("""\\(?:text|mathrm|mathbf|operatorname)\{([^{}]*)\}""").replace(s) { it.groupValues[1] }
        s = Regex("""\\frac\{([^{}]*)\}\{([^{}]*)\}""").replace(s) { "${wrap(it.groupValues[1])}/${wrap(it.groupValues[2])}" }
        s = Regex("""\\sqrt\{([^{}]*)\}""").replace(s) { "√${wrap(it.groupValues[1])}" }
        // Longest command first so \leq wins over \le. After a letter-like symbol
        // (\pi r) the space only terminates the command, so it is dropped.
        symbols.entries.sortedByDescending { it.key.length }.forEach { (k, v) ->
            val trailing = if (v.singleOrNull()?.isLetter() == true) " ?" else ""
            s = Regex(Regex.escape(k) + "(?![A-Za-z])" + trailing).replace(s, Regex.escapeReplacement(v))
        }
        s = Regex("""\^\{([^{}]*)\}|\^(\w)""").replace(s) { script(it.groupValues[1] + it.groupValues[2], superscripts, "^") }
        s = Regex("""_\{([^{}]*)\}|_(\w)""").replace(s) { script(it.groupValues[1] + it.groupValues[2], subscripts, "_") }
        return s.replace("{", "").replace("}", "").trim()
    }

    private fun wrap(x: String) = if (x.length > 1 && x.any { !it.isLetterOrDigit() }) "($x)" else x

    private fun script(text: String, table: Map<Char, Char>, marker: String): String =
        if (text.all { it in table }) text.map { table.getValue(it) }.joinToString("") else "$marker($text)"
}
