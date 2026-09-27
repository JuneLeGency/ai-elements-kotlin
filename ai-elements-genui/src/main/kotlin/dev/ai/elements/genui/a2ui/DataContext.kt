package dev.ai.elements.genui.a2ui

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Where a component evaluates its dynamic values (A2UI "evaluation scope"): absolute paths resolve
 * from the surface's data model; relative paths from [base], the item a list template is rendering
 * (at [index]). Resolves `Dynamic*` values: literals, `{"path": …}` bindings and `{"call": …}`
 * function calls, including `formatString` `${…}` interpolation.
 */
class DataContext internal constructor(
    val surface: A2uiSurface,
    private val functions: Map<String, A2uiFunction>,
    /** The collection item this scope renders, e.g. `/employees/1`; empty at the root. */
    val base: String = "",
    val index: Int? = null,
    internal val effects: A2uiEffects = A2uiEffects.None,
    /** True only while handling a user's action (A2UI user-activation rule, e.g. for `openUrl`). */
    val userActivated: Boolean = false,
) {
    /** A path made absolute in this scope. */
    fun absolute(path: String): String = when {
        path.startsWith("/") -> path
        base.isEmpty() -> "/$path"
        path.isEmpty() -> base
        else -> "$base/$path"
    }

    /** The scope of item [i] of the list at [path] (a template instance). */
    fun item(path: String, i: Int) = DataContext(surface, functions, "${absolute(path)}/$i", i, effects)

    /** This scope while handling a user action. */
    internal fun activated() = DataContext(surface, functions, base, index, effects, userActivated = true)

    /** A dynamic value resolved to plain JSON (null when unbound or not yet sent). */
    fun resolve(value: JsonElement?): JsonElement? = when {
        value is JsonObject && value.isBinding() -> surface.read(absolute(value.str("path")!!))
        value is JsonObject && value.isCall() -> call(value)
        value is JsonArray -> JsonArray(value.map { resolve(it) ?: JsonNull })
        else -> value
    }

    fun string(value: JsonElement?): String? = resolve(value)?.let(::stringify)

    fun boolean(value: JsonElement?): Boolean? = resolve(value)?.let(::truthy)

    fun number(value: JsonElement?): Double? = (resolve(value) as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }

    fun stringList(value: JsonElement?): List<String> = when (val v = resolve(value)) {
        is JsonArray -> v.map(::stringify)
        null, JsonNull -> emptyList()
        else -> listOf(stringify(v))
    }

    /** Run a function call in this scope; `@index` is the system function of template items. */
    fun call(call: JsonObject): JsonElement? {
        val name = call.str("call") ?: return null
        val args = (call["args"] as? JsonObject).orEmpty()
        if (name == INDEX) {
            val offset = (args["offset"] as? JsonPrimitive)?.doubleOrNull?.toInt() ?: 0
            return index?.let { JsonPrimitive(it + offset) }
        }
        val function = functions[name] ?: throw A2uiExpressionError("Unknown function '$name'")
        if (function.agentOnly) throw A2uiExpressionError("Function '$name' can only be called by the agent")
        val resolved = if (name == "formatString") args else JsonObject(args.mapValues { (_, v) -> resolve(v) ?: JsonNull })
        return function.invoke(resolved, this)
    }

    /** `formatString`: `${…}` blocks with paths, literals and nested calls; `\${` is a literal `${`. */
    fun interpolate(template: String): String = Interpolator(template, this).run()

    internal companion object {
        const val INDEX = "@index"

        fun JsonObject.isBinding() = size == 1 && this["path"] is JsonPrimitive
        fun JsonObject.isCall() = this["call"] is JsonPrimitive

        /** A2UI type conversion: numbers/booleans as usual, null → "", objects/arrays → JSON. */
        fun stringify(value: JsonElement): String = when (value) {
            JsonNull -> ""
            is JsonPrimitive -> if (value.isString) value.content else value.content.let { c ->
                c.toDoubleOrNull()?.takeIf { it % 1.0 == 0.0 && !c.contains('e', true) }?.let { it.toLong().toString() } ?: c
            }
            else -> value.toString()
        }

        /** Booleans as themselves, a validation result by its `valid`, anything else by presence. */
        fun truthy(value: JsonElement): Boolean = when (value) {
            JsonNull -> false
            is JsonPrimitive -> value.booleanOrNull ?: value.content.isNotEmpty()
            is JsonObject -> (value["valid"] as? JsonPrimitive)?.booleanOrNull ?: true
            is JsonArray -> value.isNotEmpty()
        }
    }
}

/** An expression that cannot be evaluated (unknown function, disallowed URL…). */
class A2uiExpressionError(message: String) : RuntimeException(message)

/** Parses and evaluates one `formatString` template. */
private class Interpolator(private val text: String, private val context: DataContext) {
    private var i = 0

    fun run(): String = buildString {
        while (i < text.length) {
            when {
                text.startsWith("\\\${", i) -> { append("\${"); i += 3 }
                text.startsWith("\${", i) -> {
                    i += 2
                    // A block that cannot be evaluated renders empty; the rest of the string still shows.
                    val value = runCatching { expression() }.getOrNull()
                    skipSpaces()
                    if (i < text.length && text[i] == '}') i++
                    append(value?.let(DataContext::stringify).orEmpty())
                }
                else -> append(text[i++])
            }
        }
    }

    private fun expression(): JsonElement? {
        skipSpaces()
        if (i >= text.length) return null
        val c = text[i]
        return when {
            // A nested block used as a value, e.g. `formatDate(value: ${/now}, …)`.
            text.startsWith("\${", i) -> {
                i += 2
                val inner = expression()
                skipSpaces()
                if (i < text.length && text[i] == '}') i++
                inner
            }
            c == '\'' || c == '"' -> JsonPrimitive(quoted(c))
            c == '-' || c.isDigit() -> JsonPrimitive(number())
            else -> {
                val token = identifier()
                if (token.isEmpty()) { i++; return null } // unrecognised character: skip it, always make progress
                skipSpaces()
                when {
                    token == "true" -> JsonPrimitive(true)
                    token == "false" -> JsonPrimitive(false)
                    token == "null" -> JsonNull
                    i < text.length && text[i] == '(' -> { i++; context.call(callOf(token, arguments())) }
                    else -> context.resolve(JsonObject(mapOf("path" to JsonPrimitive(token))))
                }
            }
        }
    }

    /** `name: value, …)` → args object (values already evaluated). */
    private fun arguments(): JsonObject {
        val args = linkedMapOf<String, JsonElement>()
        while (true) {
            skipSpaces()
            if (i >= text.length) break
            if (text[i] == ')') { i++; break }
            if (text[i] == '}') break // unterminated call: let the block end
            val start = i
            val name = identifier()
            skipSpaces()
            if (i < text.length && text[i] == ':') i++
            args[name] = expression() ?: JsonNull
            skipSpaces()
            if (i < text.length && text[i] == ',') i++
            if (i == start) i++ // never loop in place on malformed input
        }
        return JsonObject(args)
    }

    private fun callOf(name: String, args: JsonObject) = JsonObject(mapOf("call" to JsonPrimitive(name), "args" to args))

    private fun identifier(): String {
        val start = i
        while (i < text.length && (text[i].isLetterOrDigit() || text[i] in "/_@.~-")) i++
        return text.substring(start, i)
    }

    private fun number(): Double {
        val start = i
        i++
        while (i < text.length && (text[i].isDigit() || text[i] == '.')) i++
        return text.substring(start, i).toDouble()
    }

    private fun quoted(quote: Char): String = buildString {
        i++
        while (i < text.length && text[i] != quote) {
            if (text[i] == '\\' && i + 1 < text.length) i++
            append(text[i++])
        }
        i++
    }

    private fun skipSpaces() { while (i < text.length && text[i].isWhitespace()) i++ }
}
