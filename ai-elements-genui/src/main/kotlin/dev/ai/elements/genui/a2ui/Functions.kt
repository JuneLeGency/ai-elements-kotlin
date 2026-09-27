package dev.ai.elements.genui.a2ui

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import java.net.URI
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

/** Side effects functions may ask the host for (only `openUrl` in the Basic Catalog). */
fun interface A2uiEffects {
    fun openUrl(url: String)

    companion object {
        val None = A2uiEffects { throw A2uiExpressionError("Opening URLs is not available here") }
    }
}

/**
 * A catalog function. [invoke] gets resolved args (`formatString` gets them raw, to interpolate
 * itself) and the calling scope (null when the agent calls it via `callRendererFunction`).
 */
class A2uiFunction(
    /** May the agent call it (`allowedCallers` agentOnly / rendererOrAgent)? */
    val callableByAgent: Boolean = false,
    /** Only the agent may call it; it cannot be bound in the UI. */
    val agentOnly: Boolean = false,
    val invoke: (args: JsonObject, context: DataContext?) -> JsonElement?,
)

/** The Basic Catalog's renderer-side functions (A2UI v1.0 basic catalog implementation guide). */
object BasicFunctions {
    val all: Map<String, A2uiFunction> = mapOf(
        "required" to validation { a -> a["value"].let { it != null && it != JsonNull && !(it is JsonPrimitive && it.isString && it.content.isEmpty()) && !(it is JsonArray && it.isEmpty()) } },
        // Like JavaScript's RegExp.test: a match anywhere, unless the pattern anchors itself.
        "regex" to validation { a -> Regex(a.text("pattern").orEmpty()).containsMatchIn(a.text("value").orEmpty()) },
        "length" to validation { a -> a.text("value").orEmpty().length.let { n -> (a.num("min")?.let { n >= it } ?: true) && (a.num("max")?.let { n <= it } ?: true) } },
        "numeric" to validation { a -> a.text("value")?.toDoubleOrNull()?.let { v -> (a.num("min")?.let { v >= it } ?: true) && (a.num("max")?.let { v <= it } ?: true) } ?: false },
        "email" to validation { a -> Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(a.text("value").orEmpty()) },
        "formatString" to A2uiFunction { a, c ->
            val raw = a["value"]
            val template = if (raw is JsonPrimitive && raw.isString) raw.content else c?.string(raw).orEmpty()
            JsonPrimitive(c?.interpolate(template) ?: template)
        },
        "formatNumber" to A2uiFunction { a, _ -> JsonPrimitive(number(a, NumberFormat.getNumberInstance(Locale.getDefault()))) },
        "formatCurrency" to A2uiFunction { a, _ ->
            val format = NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
                a.text("currency")?.let { runCatching { currency = Currency.getInstance(it) } }
            }
            JsonPrimitive(number(a, format))
        },
        "formatDate" to A2uiFunction { a, _ -> JsonPrimitive(formatDate(a.text("value").orEmpty(), a.text("format"))) },
        "pluralize" to A2uiFunction { a, _ ->
            val category = pluralCategory(a.num("value") ?: 0.0)
            JsonPrimitive(a.text(category) ?: a.text("other").orEmpty())
        },
        "openUrl" to A2uiFunction { a, c ->
            val url = a.text("url").orEmpty()
            val scheme = runCatching { URI(url).scheme?.lowercase() }.getOrNull()
            if (scheme != "https" && scheme != "http") throw A2uiExpressionError("openUrl only opens http(s) URLs")
            if (c == null || !c.userActivated) throw A2uiExpressionError("openUrl needs a user action")
            c.effects.openUrl(url)
            null
        },
        "and" to A2uiFunction { a, _ -> JsonPrimitive((a["values"] as? JsonArray).orEmpty().all(DataContext::truthy)) },
        "or" to A2uiFunction { a, _ -> JsonPrimitive((a["values"] as? JsonArray).orEmpty().any(DataContext::truthy)) },
        "not" to A2uiFunction { a, _ -> JsonPrimitive(!(a["value"]?.let(DataContext::truthy) ?: false)) },
    )

    private fun validation(check: (JsonObject) -> Boolean) = A2uiFunction { a, _ -> buildJsonObject { put("valid", check(a)) } }

    private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.takeUnless { it == JsonNull }?.let { DataContext.stringify(it) }
    private fun JsonObject.num(key: String) = (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }
    private fun JsonArray?.orEmpty() = this ?: JsonArray(emptyList())

    private fun number(a: JsonObject, format: NumberFormat): String {
        val value = a.num("value") ?: return a.text("value").orEmpty()
        a.num("decimals")?.toInt()?.let { format.minimumFractionDigits = it; format.maximumFractionDigits = it }
        format.isGroupingUsed = (a["grouping"] as? JsonPrimitive)?.contentOrNull != "false"
        return format.format(value)
    }

    /** TR35 patterns via java.time (`YYYY` week-year is read as calendar year, as authors mean). */
    internal fun formatDate(value: String, pattern: String?): String {
        val zone = ZoneId.systemDefault()
        val moment = runCatching { OffsetDateTime.parse(value).atZoneSameInstant(zone) }.getOrNull()
            ?: runCatching { Instant.parse(value).atZone(zone) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(value).atZone(zone) }.getOrNull()
            ?: runCatching { LocalDate.parse(value).atStartOfDay(zone) }.getOrNull()
            ?: runCatching { LocalTime.parse(value).atDate(LocalDate.now(zone)).atZone(zone) }.getOrNull()
            ?: return value
        val formatter = pattern?.let { runCatching { DateTimeFormatter.ofPattern(it.replace("Y", "y"), Locale.getDefault()) }.getOrNull() }
            ?: DateTimeFormatter.ISO_OFFSET_DATE_TIME
        return runCatching { moment.format(formatter) }.getOrDefault(value)
    }

    /** CLDR plural category for the current locale (Android ICU); English rules off-device. */
    internal fun pluralCategory(value: Double): String =
        runCatching { android.icu.text.PluralRules.forLocale(Locale.getDefault()).select(value) }.getOrNull()
            ?: if (value == 1.0) "one" else "other"
}
