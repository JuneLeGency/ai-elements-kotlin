package dev.ai.elements.ui.markdown

/**
 * Helpers that keep streamed Markdown cheap and clean to render (the approach
 * of Streamdown in AI Elements):
 *
 * - [split] cuts the text into top-level blocks, so blocks that are finished
 *   keep an identical string and their composables are skipped — only the
 *   block still being written is re-parsed on each update.
 * - [repairTail] temporarily closes Markdown the model hasn't finished yet
 *   (`**bold`, `` `code``, `~~strike`, a half-written link), so raw markers
 *   never flash on screen mid-stream.
 */
internal object MarkdownStreaming {

    private val fence = Regex("""^\s{0,3}(```|~~~)""")
    private val listItem = Regex("""^\s*([-*+]|\d{1,9}[.)])\s""")

    /**
     * Top-level blocks separated by blank lines, never splitting inside a
     * fenced code block, a loose list, or indented continuation lines.
     */
    fun split(markdown: String): List<String> {
        val lines = markdown.split('\n')
        val blocks = mutableListOf<String>()
        val current = StringBuilder()
        var inFence = false
        var blockIsList = false

        fun flush() {
            if (current.isNotBlank()) blocks += current.toString().trimEnd('\n')
            current.clear()
            blockIsList = false
        }

        for ((i, line) in lines.withIndex()) {
            if (fence.containsMatchIn(line)) inFence = !inFence
            if (!inFence && line.isBlank()) {
                val next = lines.drop(i + 1).firstOrNull { it.isNotBlank() }
                val continues = next != null && (
                    next.startsWith(" ") || next.startsWith("\t") || (blockIsList && listItem.containsMatchIn(next))
                    )
                if (continues) current.append('\n') else flush()
                continue
            }
            if (current.isEmpty() && listItem.containsMatchIn(line)) blockIsList = true
            current.append(line).append('\n')
        }
        flush()
        return blocks
    }

    /** Close or hide unfinished inline Markdown at the end of a streaming block. */
    fun repairTail(block: String): String {
        // An open code fence renders fine as code; leave it alone.
        if (block.lines().count { fence.containsMatchIn(it) } % 2 == 1) return block
        var text = block
        // A link still being written: "[label" or "[label](https://exa" → show just the label.
        val open = text.lastIndexOf('[')
        if (open >= 0) {
            val tail = text.substring(open)
            val complete = Regex("""^\[[^\]]*]\([^)]*\)""").containsMatchIn(tail)
            if (!complete && !tail.contains('\n')) {
                val label = tail.removePrefix("[").substringBefore(']')
                text = text.substring(0, open) + label
            }
        }
        val lastLine = text.substringAfterLast('\n')
        val code = lastLine.count { it == '`' }
        if (code % 2 == 1) return "$text`"
        // Outside inline code, balance ** and ~~.
        val prose = lastLine.split('`').filterIndexed { i, _ -> i % 2 == 0 }.joinToString("")
        text = balance(text, prose, "**")
        text = balance(text, prose, "~~")
        return text
    }

    /** An unmatched [marker]: drop it if nothing follows it yet, otherwise close it. */
    private fun balance(text: String, prose: String, marker: String): String {
        if (Regex(Regex.escape(marker)).findAll(prose).count() % 2 == 0) return text
        val trimmed = text.trimEnd()
        return if (trimmed.endsWith(marker)) trimmed.dropLast(marker.length).trimEnd() else trimmed + marker
    }
}
