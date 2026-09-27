package dev.ai.elements.core.skills

import java.text.Normalizer

/**
 * An [Agent Skill](https://agentskills.io/specification): a folder with a
 * `SKILL.md` whose YAML frontmatter names and describes the skill and whose
 * Markdown body holds the instructions.
 *
 * Parsing follows Pydantic AI Harness `Skills`: `name` defaults to the folder
 * name and must match it; names are NFKC-normalized, at most 64 lowercase
 * letters or digits separated by single hyphens; `description` is required.
 * Other frontmatter fields are kept in [frontmatter] but not interpreted.
 */
data class Skill(
    val name: String,
    val description: String,
    val instructions: String,
    val license: String? = null,
    val compatibility: String? = null,
    val frontmatter: Map<String, String> = emptyMap(),
    /** Where it was loaded from (folder path or asset path), for display. */
    val location: String = "",
) {
    companion object {
        /** The Agent Skills description limit; longer descriptions still load. */
        const val MAX_DESCRIPTION = 1024

        private val NAME = Regex("^[\\p{Ll}\\p{Lo}\\p{Nd}]+(-[\\p{Ll}\\p{Lo}\\p{Nd}]+)*$")

        fun normalizeName(name: String): String = Normalizer.normalize(name.trim(), Normalizer.Form.NFKC)

        fun isValidName(name: String): Boolean = name.length <= 64 && NAME.matches(name)

        /**
         * Parse a `SKILL.md` found in folder [folderName].
         * @throws SkillFormatException when the file is not a valid skill.
         */
        fun parse(markdown: String, folderName: String, location: String = folderName): Skill {
            val (front, body) = Frontmatter.split(markdown)
                ?: throw SkillFormatException("$location: SKILL.md must start with YAML frontmatter (---)")
            val fields = Frontmatter.parse(front)
            val folder = normalizeName(folderName)
            val name = fields["name"]?.let(::normalizeName)?.ifEmpty { null } ?: folder
            if (!isValidName(name)) throw SkillFormatException("$location: invalid skill name '$name'")
            if (name != folder) throw SkillFormatException("$location: name '$name' must match its folder '$folder'")
            val description = fields["description"]?.trim().orEmpty()
            if (description.isEmpty()) throw SkillFormatException("$location: description is required")
            return Skill(
                name = name,
                description = description,
                instructions = body.trim(),
                license = fields["license"],
                compatibility = fields["compatibility"],
                frontmatter = fields,
                location = location,
            )
        }
    }
}

class SkillFormatException(message: String) : Exception(message)

/**
 * The YAML subset Agent Skills frontmatter uses: `key: value` pairs, quoted
 * scalars, `|` / `>` block scalars and nested maps (flattened as `a.b`).
 * Lists are kept as their raw text.
 */
internal object Frontmatter {
    fun split(markdown: String): Pair<String, String>? {
        val text = markdown.removePrefix("\uFEFF").replace("\r\n", "\n")
        if (!text.startsWith("---\n")) return null
        val end = Regex("^---[ \\t]*$", RegexOption.MULTILINE).find(text, 4) ?: return null
        return text.substring(4, end.range.first) to text.substring(end.range.last + 1)
    }

    fun parse(yaml: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        val lines = yaml.lines()
        var i = 0
        fun indentOf(line: String) = line.length - line.trimStart().length
        fun parseBlock(prefix: String, indent: Int) {
            while (i < lines.size) {
                val line = lines[i]
                if (line.isBlank() || line.trimStart().startsWith("#")) { i++; continue }
                val lineIndent = indentOf(line)
                if (lineIndent < indent) return
                val trimmed = line.trim()
                val colon = keyColon(trimmed)
                if (colon < 0) { i++; continue }
                val key = prefix + unquote(trimmed.substring(0, colon).trim())
                val rest = trimmed.substring(colon + 1).trim().substringBeforeComment()
                i++
                when {
                    rest == "|" || rest == ">" || rest.startsWith("|") || rest.startsWith(">") -> {
                        val folded = rest.startsWith(">")
                        val block = mutableListOf<String>()
                        var blockIndent = -1
                        while (i < lines.size && (lines[i].isBlank() || indentOf(lines[i]) > lineIndent)) {
                            if (blockIndent < 0 && lines[i].isNotBlank()) blockIndent = indentOf(lines[i])
                            block += if (lines[i].isBlank()) "" else lines[i].drop(blockIndent.coerceAtLeast(0))
                            i++
                        }
                        val joined = if (folded) block.joinToString(" ") { it.trim() }.replace(Regex(" {2,}"), " ") else block.joinToString("\n")
                        out[key] = joined.trimEnd()
                    }
                    rest.isEmpty() -> {
                        val next = lines.drop(i).firstOrNull { it.isNotBlank() }
                        if (next != null && indentOf(next) > lineIndent && next.trim().startsWith("- ")) {
                            val items = mutableListOf<String>()
                            while (i < lines.size && (lines[i].isBlank() || indentOf(lines[i]) > lineIndent)) {
                                lines[i].trim().takeIf { it.startsWith("- ") }?.let { items += unquote(it.removePrefix("- ").trim()) }
                                i++
                            }
                            out[key] = items.joinToString(", ")
                        } else if (next != null && indentOf(next) > lineIndent) {
                            parseBlock("$key.", indentOf(next))
                        } else out[key] = ""
                    }
                    else -> out[key] = unquote(rest)
                }
            }
        }
        parseBlock("", 0)
        return out
    }

    /** Index of the key's colon: the first `:` followed by space/end, outside quotes. */
    private fun keyColon(line: String): Int {
        var quote: Char? = null
        line.forEachIndexed { index, c ->
            when {
                quote != null -> if (c == quote) quote = null
                c == '"' || c == '\'' -> quote = c
                c == ':' && (index == line.lastIndex || line[index + 1] == ' ') -> return index
            }
        }
        return -1
    }

    private fun String.substringBeforeComment(): String =
        if (startsWith("\"") || startsWith("'")) this else substringBefore(" #").trim()

    private fun unquote(value: String): String = when {
        value.length >= 2 && value.startsWith("\"") && value.endsWith("\"") ->
            value.substring(1, value.length - 1).replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\")
        value.length >= 2 && value.startsWith("'") && value.endsWith("'") ->
            value.substring(1, value.length - 1).replace("''", "'")
        else -> value
    }
}
