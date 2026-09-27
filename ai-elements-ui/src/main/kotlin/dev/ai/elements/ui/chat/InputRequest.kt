package dev.ai.elements.ui.chat

import dev.ai.elements.ui.icons.AiIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.ai.elements.core.chat.InputRequest
import dev.ai.elements.core.chat.InputResponse
import dev.ai.elements.ui.R
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/**
 * The agent (or a tool's server) asks the user for something: a form built from the request's
 * JSON Schema, a link to complete out of band, or a plain confirmation. Protocol-neutral — the
 * same card answers an AG-UI interrupt and an MCP elicitation.
 *
 * Test tags: `input-request`, `input-field-<property>`, `input-submit`, `input-decline`,
 * `input-open-url`.
 */
@Composable
fun InputRequestCard(request: InputRequest, onRespond: (InputResponse) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth().testTag("input-request"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Icon(AiIcons.HelpOutline, null, Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(request.message, style = MaterialTheme.typography.titleSmall)
                    request.source?.let { Text(stringResource(R.string.ai_input_from, it), style = MaterialTheme.typography.bodySmall) }
                }
            }
            val url = request.url
            val schema = request.schema
            when {
                url != null -> {
                    val uriHandler = LocalUriHandler.current
                    Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Actions(
                        onDecline = { onRespond(InputResponse.Decline) },
                        primary = stringResource(R.string.ai_input_done),
                        onPrimary = { onRespond(InputResponse.Accept()) },
                    ) {
                        OutlinedButton(onClick = { uriHandler.openUri(url) }, modifier = Modifier.testTag("input-open-url")) {
                            Icon(AiIcons.OpenInNew, null, Modifier.size(18.dp))
                            Text(stringResource(R.string.ai_input_open_link), Modifier.padding(start = 6.dp))
                        }
                    }
                }
                schema != null -> SchemaForm(schema, onSubmit = { onRespond(InputResponse.Accept(it)) }, onDecline = { onRespond(InputResponse.Decline) })
                else -> Actions(onDecline = { onRespond(InputResponse.Decline) }, primary = stringResource(R.string.ai_submit), onPrimary = { onRespond(InputResponse.Accept()) })
            }
        }
    }
}

@Composable
private fun Actions(onDecline: () -> Unit, primary: String, onPrimary: () -> Unit, enabled: Boolean = true, extra: @Composable () -> Unit = {}) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
        extra()
        OutlinedButton(onClick = onDecline, modifier = Modifier.testTag("input-decline")) { Text(stringResource(R.string.ai_input_decline)) }
        Button(onClick = onPrimary, enabled = enabled, modifier = Modifier.testTag("input-submit")) { Text(primary) }
    }
}

/**
 * A form for a flat JSON Schema object (MCP elicitation's restricted schema, AG-UI
 * `responseSchema`): text (with `format` email / uri / date), numbers with `minimum` / `maximum`,
 * booleans, single choices (`enum`, `oneOf` of `const`) and multiple choices (arrays of those).
 * Defaults pre-fill; `required` fields must be set before submitting.
 */
@Composable
fun SchemaForm(schema: JsonObject, onSubmit: (JsonObject) -> Unit, onDecline: () -> Unit, modifier: Modifier = Modifier) {
    val fields = remember(schema) { SchemaField.all(schema) }
    val values = remember(schema) { mutableStateMapOf<String, Any>().apply { fields.forEach { f -> f.initial()?.let { put(f.name, it) } } } }
    val errors = fields.associate { it.name to it.error(values[it.name]) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        fields.forEach { field -> Field(field, values[field.name], errors[field.name]) { values[field.name] = it } }
        Actions(
            onDecline = onDecline,
            primary = stringResource(R.string.ai_submit),
            onPrimary = { onSubmit(buildJsonObject { fields.forEach { f -> f.toJson(values[f.name])?.let { put(f.name, it) } } }) },
            enabled = errors.values.all { it == null },
        )
    }
}

@Composable
private fun Field(field: SchemaField, value: Any?, error: FieldError?, onChange: (Any) -> Unit) {
    val label = field.title + if (field.required) " *" else ""
    val tag = Modifier.testTag("input-field-${field.name}")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (field.kind) {
            SchemaField.Kind.BOOLEAN -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = value == true, onCheckedChange = { onChange(it) }, modifier = tag)
            }
            SchemaField.Kind.CHOICE, SchemaField.Kind.CHOICES -> {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                val selected = (value as? Set<*>).orEmpty()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = tag) {
                    field.options.forEach { (id, title) ->
                        FilterChip(
                            selected = id in selected,
                            onClick = {
                                onChange(
                                    if (field.kind == SchemaField.Kind.CHOICE) setOf(id)
                                    else if (id in selected) selected - id else selected + id,
                                )
                            },
                            label = { Text(title) },
                            modifier = Modifier.testTag("input-option-${field.name}-$id"),
                        )
                    }
                }
            }
            SchemaField.Kind.TEXT, SchemaField.Kind.NUMBER -> OutlinedTextField(
                value = value as? String ?: "",
                onValueChange = { onChange(it) },
                label = { Text(label) },
                singleLine = true,
                isError = error != null && error != FieldError.Missing,
                keyboardOptions = KeyboardOptions(keyboardType = field.keyboard),
                modifier = tag.fillMaxWidth(),
            )
        }
        val hint = when (error) {
            null, FieldError.Missing -> field.description
            FieldError.NotANumber -> stringResource(R.string.ai_input_invalid_number)
            FieldError.NotAnEmail -> stringResource(R.string.ai_input_invalid_email)
            is FieldError.Below -> stringResource(R.string.ai_input_min, error.limit)
            is FieldError.Above -> stringResource(R.string.ai_input_max, error.limit)
        }
        hint?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = if (error != null && error != FieldError.Missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal sealed interface FieldError {
    data object Missing : FieldError
    data object NotANumber : FieldError
    data object NotAnEmail : FieldError
    data class Below(val limit: String) : FieldError
    data class Above(val limit: String) : FieldError
}

/** One property of a flat object schema, as the form renders and validates it. */
internal data class SchemaField(
    val name: String,
    val title: String,
    val description: String?,
    val kind: Kind,
    val required: Boolean,
    val integer: Boolean = false,
    val format: String? = null,
    val options: List<Pair<String, String>> = emptyList(),
    val minimum: Double? = null,
    val maximum: Double? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val default: JsonElement? = null,
) {
    enum class Kind { TEXT, NUMBER, BOOLEAN, CHOICE, CHOICES }

    val keyboard: KeyboardType
        get() = when {
            kind == Kind.NUMBER -> if (integer) KeyboardType.Number else KeyboardType.Decimal
            format == "email" -> KeyboardType.Email
            format == "uri" -> KeyboardType.Uri
            else -> KeyboardType.Text
        }

    /** The pre-filled value: the schema's `default`. */
    fun initial(): Any? = when (kind) {
        Kind.BOOLEAN -> (default as? JsonPrimitive)?.booleanOrNull ?: false
        Kind.CHOICE -> (default as? JsonPrimitive)?.contentOrNull?.let { setOf(it) }
        Kind.CHOICES -> (default as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.toSet()
        Kind.TEXT, Kind.NUMBER -> (default as? JsonPrimitive)?.contentOrNull
    }

    fun error(value: Any?): FieldError? {
        val text = (value as? String)?.trim()
        val empty = when (kind) {
            Kind.BOOLEAN -> false
            Kind.CHOICE, Kind.CHOICES -> (value as? Set<*>).isNullOrEmpty()
            Kind.TEXT, Kind.NUMBER -> text.isNullOrEmpty()
        }
        if (empty) return if (required) FieldError.Missing else null
        return when (kind) {
            Kind.NUMBER -> {
                val n = text!!.toDoubleOrNull()?.takeIf { !integer || it % 1.0 == 0.0 } ?: return FieldError.NotANumber
                when {
                    minimum != null && n < minimum -> FieldError.Below(minimum.show())
                    maximum != null && n > maximum -> FieldError.Above(maximum.show())
                    else -> null
                }
            }
            Kind.TEXT -> when {
                format == "email" && !text!!.matches(Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) -> FieldError.NotAnEmail
                minLength != null && text!!.length < minLength -> FieldError.Below(minLength.toString())
                maxLength != null && text!!.length > maxLength -> FieldError.Above(maxLength.toString())
                else -> null
            }
            else -> null
        }
    }

    /** The value as JSON for the answer; null leaves an unset optional field out. */
    fun toJson(value: Any?): JsonElement? = when (kind) {
        Kind.BOOLEAN -> JsonPrimitive(value == true)
        Kind.CHOICE -> (value as? Set<*>)?.firstOrNull()?.let { JsonPrimitive(it.toString()) }
        Kind.CHOICES -> (value as? Set<*>)?.takeIf { it.isNotEmpty() }?.let { set -> JsonArray(options.map { it.first }.filter { it in set }.map(::JsonPrimitive)) }
        Kind.NUMBER -> (value as? String)?.trim()?.toDoubleOrNull()?.let { if (integer) JsonPrimitive(it.toLong()) else JsonPrimitive(it) }
        Kind.TEXT -> (value as? String)?.takeIf { it.isNotBlank() }?.let(::JsonPrimitive)
    }

    companion object {
        private fun Double.show() = if (this % 1.0 == 0.0) toLong().toString() else toString()

        /** The fields of [schema]'s `properties`, in order. */
        fun all(schema: JsonObject): List<SchemaField> {
            val required = (schema["required"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty().toSet()
            val properties = schema["properties"] as? JsonObject ?: return emptyList()
            return properties.mapNotNull { (name, value) -> (value as? JsonObject)?.let { of(name, it, name in required) } }
        }

        private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
        private fun JsonObject.num(key: String) = (this[key] as? JsonPrimitive)?.doubleOrNull

        /** `enum` (+ `enumNames`) or `oneOf` / `anyOf` of `{const, title}`. */
        private fun options(p: JsonObject): List<Pair<String, String>> {
            (p["enum"] as? JsonArray)?.let { values ->
                val names = (p["enumNames"] as? JsonArray)?.map { (it as? JsonPrimitive)?.contentOrNull }
                return values.mapIndexedNotNull { i, v -> (v as? JsonPrimitive)?.contentOrNull?.let { it to (names?.getOrNull(i) ?: it) } }
            }
            val alternatives = (p["oneOf"] ?: p["anyOf"]) as? JsonArray ?: return emptyList()
            return alternatives.mapNotNull { (it as? JsonObject)?.let { o -> o.str("const")?.let { c -> c to (o.str("title") ?: c) } } }
        }

        fun of(name: String, p: JsonObject, required: Boolean): SchemaField {
            val type = p.str("type")
            val base = SchemaField(
                name = name,
                title = p.str("title") ?: name,
                description = p.str("description"),
                kind = Kind.TEXT,
                required = required,
                default = p["default"],
            )
            val single = options(p)
            val items = p["items"] as? JsonObject
            return when {
                type == "boolean" -> base.copy(kind = Kind.BOOLEAN)
                type == "array" && items != null -> base.copy(kind = Kind.CHOICES, options = options(items))
                single.isNotEmpty() -> base.copy(kind = Kind.CHOICE, options = single)
                type == "integer" || type == "number" -> base.copy(kind = Kind.NUMBER, integer = type == "integer", minimum = p.num("minimum"), maximum = p.num("maximum"))
                else -> base.copy(format = p.str("format"), minLength = p.num("minLength")?.toInt(), maxLength = p.num("maxLength")?.toInt())
            }
        }
    }
}
