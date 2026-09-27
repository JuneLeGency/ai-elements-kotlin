package dev.ai.elements.koog

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.serialization.JSONElement
import ai.koog.serialization.JSONObject
import ai.koog.serialization.JSONPrimitive
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.kotlinx.toKotlinxJsonElement
import ai.koog.serialization.kotlinx.toKotlinxJsonObject
import ai.koog.serialization.typeToken
import dev.ai.elements.core.agent.AgentTool
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * An AI Elements [AgentTool] as a Koog tool: JSON arguments in, text out. [run]
 * defaults to the tool itself; [KoogBackend] routes it through the chat's tool
 * events (approval, progress) instead.
 */
class KoogTool(
    val tool: AgentTool,
    private val run: suspend (JsonObject) -> String = tool::execute,
) : Tool<JsonObject, String>(typeToken<JsonObject>(), typeToken<String>(), tool.koogDescriptor()) {
    override suspend fun execute(args: JsonObject): String = run(args)
    override fun decodeArgs(rawArgs: JSONObject, serializer: JSONSerializer): JsonObject = rawArgs.toKotlinxJsonObject()
    override fun encodeResult(result: String, serializer: JSONSerializer): JSONElement = JSONPrimitive(result)
    override fun decodeResult(rawResult: JSONElement, serializer: JSONSerializer): String =
        (rawResult.toKotlinxJsonElement() as? JsonPrimitive)?.contentOrNull ?: rawResult.toKotlinxJsonElement().toString()

    /** The model sees the tool's text as is, not as a JSON string literal. */
    override fun encodeResultToString(result: String, serializer: JSONSerializer): String = result
}

/** This tool's JSON Schema parameters as a Koog [ToolDescriptor]. */
fun AgentTool.koogDescriptor(): ToolDescriptor {
    val schema = parameters.toKoogType() as? ToolParameterType.Object ?: ToolParameterType.Object(emptyList())
    val (required, optional) = schema.properties.partition { it.name in schema.requiredProperties }
    return ToolDescriptor(name, description, required, optional)
}

/** JSON Schema (the subset tools use) → Koog parameter type. */
internal fun JsonObject.toKoogType(): ToolParameterType {
    (this["enum"] as? JsonArray)?.let { values -> return ToolParameterType.Enum(values.map { it.jsonPrimitive.content }.toTypedArray()) }
    (this["anyOf"] as? JsonArray ?: this["oneOf"] as? JsonArray)?.let { options ->
        return ToolParameterType.AnyOf(options.mapIndexed { i, o -> ToolParameterDescriptor("option$i", (o as JsonObject).description(), o.toKoogType()) }.toTypedArray())
    }
    val type = when (val t = this["type"]) {
        is JsonArray -> t.map { it.jsonPrimitive.content }.firstOrNull { it != "null" }
        is JsonPrimitive -> t.contentOrNull
        else -> null
    }
    return when (type) {
        "string" -> ToolParameterType.String
        "integer" -> ToolParameterType.Integer
        "number" -> ToolParameterType.Float
        "boolean" -> ToolParameterType.Boolean
        "null" -> ToolParameterType.Null
        "array" -> ToolParameterType.List((this["items"] as? JsonObject)?.toKoogType() ?: ToolParameterType.String)
        "object", null -> {
            val properties = (this["properties"] as? JsonObject).orEmpty().map { (name, schema) ->
                ToolParameterDescriptor(name, (schema as JsonObject).description(), schema.toKoogType())
            }
            val required = (this["required"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
            val additional = (this["additionalProperties"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
            ToolParameterType.Object(properties, required, additional)
        }
        else -> ToolParameterType.String
    }
}

private fun JsonObject.description() = (this["description"] as? JsonPrimitive)?.contentOrNull.orEmpty()
