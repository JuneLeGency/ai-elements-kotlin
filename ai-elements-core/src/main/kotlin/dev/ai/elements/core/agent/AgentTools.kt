package dev.ai.elements.core.agent

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * A function the on-device agent loop can call (OpenAI / Anthropic backends).
 * [parameters] is a JSON Schema object, shared verbatim by both APIs.
 */
interface AgentTool {
    val name: String
    val description: String
    val parameters: JsonObject

    /** Ask the user before running (rendered as a Confirmation). */
    val requiresApproval: Boolean get() = false

    /** Run the tool. Throwing reports a tool error back to the model. */
    suspend fun execute(arguments: JsonObject): String
}

/** Built-in tool: the current date and time in an IANA timezone. */
object CurrentTimeTool : AgentTool {
    override val name = "get_current_time"
    override val description = "Get the current date and time in an IANA timezone, e.g. \"Asia/Shanghai\"."
    override val parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("timezone") {
                put("type", "string")
                put("description", "IANA timezone id; defaults to UTC")
            }
        }
    }

    override suspend fun execute(arguments: JsonObject): String {
        val id = arguments["timezone"]?.jsonPrimitive?.contentOrNull ?: "UTC"
        val tz = TimeZone.getTimeZone(id)
        require(tz.id == id || id == "UTC") { "Unknown timezone: $id" }
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz (EEEE)", Locale.US)
        format.timeZone = tz
        return format.format(Date())
    }
}

/** Built-in tool: evaluates an arithmetic expression (+ - * / % ^ and parentheses) without `eval`. */
object CalculatorTool : AgentTool {
    override val name = "calculate"
    override val description = "Evaluate an arithmetic expression with + - * / % ^ and parentheses."
    override val parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("expression") {
                put("type", "string")
                put("description", "e.g. (3 + 4) * 12 / 5")
            }
        }
        putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("expression")) }
    }

    override suspend fun execute(arguments: JsonObject): String {
        val expr = requireNotNull(arguments["expression"]?.jsonPrimitive?.contentOrNull) { "missing expression" }
        return formatNumber(evaluate(expr))
    }

    fun evaluate(expression: String): Double = ExpressionParser(expression).parse()

    /** Integers as-is; otherwise 10 significant digits without trailing zeros (778516.8888888889 → 778516.8889). */
    internal fun formatNumber(v: Double): String = when {
        v.isNaN() || v.isInfinite() -> v.toString()
        v == Math.floor(v) && Math.abs(v) < 1e15 -> v.toLong().toString()
        else -> java.math.BigDecimal(v).round(java.math.MathContext(10)).stripTrailingZeros().toPlainString()
    }
}

/** The tools every on-device agent loop gets by default. */
val BuiltinTools: List<AgentTool> = listOf(CurrentTimeTool, CalculatorTool)

/** Recursive-descent parser: expr := term (('+'|'-') term)*, with ^ right-assoc. */
private class ExpressionParser(private val src: String) {
    private var pos = 0

    fun parse(): Double {
        val v = expr()
        skipWs()
        require(pos == src.length) { "Unexpected '${src[pos]}' at $pos" }
        return v
    }

    private fun expr(): Double {
        var v = term()
        while (true) {
            v = when (peek()) {
                '+' -> { pos++; v + term() }
                '-' -> { pos++; v - term() }
                else -> return v
            }
        }
    }

    private fun term(): Double {
        var v = power()
        while (true) {
            v = when (peek()) {
                '*', '×' -> { pos++; v * power() }
                '/', '÷' -> { pos++; v / power() }
                '%' -> { pos++; v % power() }
                else -> return v
            }
        }
    }

    private fun power(): Double {
        val base = unary()
        return if (peek() == '^') { pos++; Math.pow(base, power()) } else base
    }

    private fun unary(): Double = when (peek()) {
        '-' -> { pos++; -unary() }
        '+' -> { pos++; unary() }
        else -> primary()
    }

    private fun primary(): Double {
        if (peek() == '(') {
            pos++
            val v = expr()
            require(peek() == ')') { "Missing ')'" }
            pos++
            return v
        }
        skipWs()
        val start = pos
        while (pos < src.length && (src[pos].isDigit() || src[pos] == '.')) pos++
        require(pos > start) { if (pos < src.length) "Unexpected '${src[pos]}' at $pos" else "Unexpected end" }
        return src.substring(start, pos).toDouble()
    }

    private fun peek(): Char? {
        skipWs()
        return src.getOrNull(pos)
    }

    private fun skipWs() {
        while (pos < src.length && src[pos].isWhitespace()) pos++
    }
}
