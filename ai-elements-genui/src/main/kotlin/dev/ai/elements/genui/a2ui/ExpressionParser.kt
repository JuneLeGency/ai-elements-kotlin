package dev.ai.elements.genui.a2ui

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal

/** A `formatString` template that does not parse. */
class A2uiParseError(message: String) : RuntimeException(message)

/**
 * The client-side expression parser behind `formatString` (A2UI basic catalog implementation
 * guide; conformance suite `core/expressions.yaml`). A template parses to parts: literal strings
 * (adjacent literals joined), JSON literals, data bindings `{"path": …}` and function calls
 * `{"call": …, "args": {…}, "returnType": "any"}`. `\${` is a literal `${`.
 */
object ExpressionParser {
    private const val MAX_DEPTH = 32

    fun parse(template: String): List<JsonElement> = Parser(template).template()

    private class Parser(private val s: String) {
        private var i = 0

        fun template(): List<JsonElement> {
            val parts = mutableListOf<JsonElement>()
            val literal = StringBuilder()
            fun flush() { if (literal.isNotEmpty()) { parts += JsonPrimitive(literal.toString()); literal.clear() } }
            while (i < s.length) {
                when {
                    s.startsWith("\\\${", i) -> { literal.append("\${"); i += 3 }
                    s.startsWith("\${", i) -> {
                        val value = interpolation(0)
                        if (value is JsonPrimitive && value.isString) literal.append(value.content)
                        else { flush(); parts += value }
                    }
                    else -> literal.append(s[i++])
                }
            }
            flush()
            return parts
        }

        /** `${ expression }` at [i]. */
        private fun interpolation(depth: Int): JsonElement {
            if (depth > MAX_DEPTH) throw A2uiParseError("Max recursion depth exceeded")
            i += 2
            spaces()
            val value = expression(depth + 1)
            spaces()
            if (i >= s.length) throw A2uiParseError("Unclosed interpolation")
            if (s[i] != '}') throw A2uiParseError("Unexpected characters in interpolation at $i")
            i++
            return value
        }

        private fun expression(depth: Int): JsonElement {
            if (depth > MAX_DEPTH) throw A2uiParseError("Max recursion depth exceeded")
            if (i >= s.length) throw A2uiParseError("Unclosed interpolation")
            val c = s[i]
            return when {
                s.startsWith("\${", i) -> interpolation(depth)
                c == '\'' || c == '"' -> JsonPrimitive(quoted(c))
                c == '-' || c.isDigit() -> number()
                c == '/' || c.isLetter() || c == '_' || c == '@' -> {
                    val name = path()
                    spaces()
                    when {
                        i < s.length && s[i] == '(' -> call(name, depth)
                        name == "true" -> JsonPrimitive(true)
                        name == "false" -> JsonPrimitive(false)
                        name == "null" -> kotlinx.serialization.json.JsonNull
                        else -> JsonObject(mapOf("path" to JsonPrimitive(name)))
                    }
                }
                else -> throw A2uiParseError("Unexpected characters in interpolation at $i")
            }
        }

        private fun call(name: String, depth: Int): JsonElement {
            i++ // (
            val args = linkedMapOf<String, JsonElement>()
            spaces()
            if (i < s.length && s[i] == ')') { i++; return callOf(name, args) }
            while (true) {
                spaces()
                val arg = path()
                if (arg.isEmpty()) throw A2uiParseError("Expected an argument name at $i")
                spaces()
                if (i >= s.length || s[i] != ':') throw A2uiParseError("Expected ':' after argument name '$arg'")
                i++
                spaces()
                args[arg] = expression(depth + 1)
                spaces()
                if (i >= s.length) throw A2uiParseError("Expected ',' or ')' after function arguments")
                when (s[i]) {
                    ',' -> i++
                    ')' -> { i++; return callOf(name, args) }
                    else -> throw A2uiParseError("Expected ',' or ')' after function arguments")
                }
            }
        }

        private fun callOf(name: String, args: Map<String, JsonElement>) = JsonObject(
            mapOf("call" to JsonPrimitive(name), "args" to JsonObject(args), "returnType" to JsonPrimitive("any")),
        )

        private fun path(): String {
            val start = i
            while (i < s.length && (s[i].isLetterOrDigit() || s[i] in "_-./@")) i++
            return s.substring(start, i)
        }

        private fun number(): JsonPrimitive {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '.' || ((s[i] == '+' || s[i] == '-') && s[i - 1].lowercaseChar() == 'e'))) i++
            val text = s.substring(start, i)
            val value = text.removeSuffix(".").toBigDecimalOrNull()?.takeIf { text.count { it == '.' } <= 1 && !text.endsWith("..") }
                ?: throw A2uiParseError("Invalid number literal '$text'")
            return JsonPrimitive(value.stripTrailingZeros().let { if (it.scale() <= 0) it.toBigInteger() as Number else it.toDouble() })
        }

        private fun quoted(quote: Char): String {
            val sb = StringBuilder()
            i++
            while (i < s.length && s[i] != quote) {
                if (s[i] == '\\' && i + 1 < s.length) {
                    i++
                    sb.append(when (s[i]) { 'n' -> '\n'; 't' -> '\t'; 'r' -> '\r'; else -> s[i] })
                    i++
                } else sb.append(s[i++])
            }
            if (i >= s.length) throw A2uiParseError("Unclosed string literal")
            i++
            return sb.toString()
        }

        private fun spaces() { while (i < s.length && s[i].isWhitespace()) i++ }
    }

    private fun String.toBigDecimalOrNull(): BigDecimal? = runCatching { BigDecimal(this) }.getOrNull()
}
