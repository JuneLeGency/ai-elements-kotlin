package dev.ai.elements.genui.a2ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/** A2UI protocol constants. */
object A2ui {
    /** The protocol revision this renderer implements (A2UI v1.0). */
    const val VERSION = "v1.0"

    /** The Basic Catalog this renderer supports out of the box. */
    const val BASIC_CATALOG_ID = "https://a2ui.org/specification/v1_0/catalogs/basic/catalog.json"
}

/**
 * One A2UI surface: its components (an adjacency list keyed by id; `root` mounts first) and its
 * data model. Both are Compose state, so a surface re-renders as messages arrive and as inputs
 * write through two-way bindings.
 */
@Stable
class A2uiSurface internal constructor(
    val id: String,
    val catalogId: String?,
    /** Send the whole data model with every action (`createSurface.sendDataModel`). */
    val sendDataModel: Boolean,
) {
    internal val components = mutableStateMapOf<String, JsonObject>()

    /** The surface's data model (a JSON object). */
    var dataModel: JsonElement by mutableStateOf(JsonObject(emptyMap()))
        internal set

    fun component(id: String): JsonObject? = components[id]

    /** Whether the tree can render yet (a `root` component has arrived). */
    val hasRoot: Boolean get() = "root" in components

    /** The value at an absolute JSON Pointer in the data model. */
    fun read(path: String): JsonElement? = JsonPointer.get(dataModel, path)

    /** Two-way binding: write [value] at [path] locally (no network until an action). */
    fun write(path: String, value: JsonElement?) {
        dataModel = JsonPointer.set(dataModel, path, value)
    }
}

/** A user action on a surface, as the A2UI renderer-to-agent `action` message ([toMessage]). */
data class A2uiAction(
    val surfaceId: String,
    val name: String,
    val sourceComponentId: String,
    val context: JsonObject,
    val userMessage: String? = null,
    val timestamp: String = Instant.now().toString(),
    /** The surface's data model when it asked for it (`sendDataModel`); for the transport's metadata. */
    val dataModel: JsonElement? = null,
) {
    fun toMessage(): JsonObject = buildJsonObject {
        put("version", A2ui.VERSION)
        put("action", buildJsonObject {
            put("name", name)
            userMessage?.let { put("userMessage", it) }
            put("surfaceId", surfaceId)
            put("sourceComponentId", sourceComponentId)
            put("timestamp", timestamp)
            put("context", context)
        })
    }
}

/**
 * The renderer side of an A2UI session: feed it agent-to-renderer messages ([process]), render its
 * [surfaces] with [A2uiSurfaceView]. Messages it must answer (function responses, errors) come back
 * from [process] as renderer-to-agent messages for the transport to send.
 */
@Stable
class A2uiState(
    val catalog: A2uiCatalog = A2uiCatalog.Basic,
    /** More catalogs surfaces and components may name (A2UI mixable catalogs). */
    catalogs: List<A2uiCatalog> = emptyList(),
) {
    private val registry: Map<String, A2uiCatalog> = (catalogs + catalog).associateBy { it.id }
    private val byId = mutableStateMapOf<String, A2uiSurface>()
    private val order = mutableStateListOf<String>()

    /** Live surfaces, in creation order. */
    val surfaces: List<A2uiSurface> get() = order.mapNotNull { byId[it] }

    fun surface(id: String): A2uiSurface? = byId[id]

    /**
     * Process messages given as text: JSONL, concatenated or pretty-printed JSON objects, or a JSON
     * array of messages. Incomplete trailing input (a message still streaming) is left out.
     */
    fun processText(text: String): List<JsonObject> = messagesIn(text).flatMap { process(it) }

    /** Apply one agent-to-renderer message; returns renderer-to-agent replies (usually none). */
    fun process(message: JsonElement): List<JsonObject> {
        val envelope = message as? JsonObject ?: return listOf(error("VALIDATION_FAILED", null, "Message is not a JSON object"))
        // The list wrapper form (`agent_to_renderer_list_wrapper.json`): {"messages": [...]}.
        if (envelope.keys == setOf("messages") && envelope["messages"] is JsonArray) return (envelope["messages"] as JsonArray).flatMap { process(it) }
        // v1.0, and v0.9.x whose envelopes it shares (e.g. the AG-UI A2UI middleware's).
        val version = envelope.str("version") ?: return listOf(error("VALIDATION_FAILED", null, "Message has no 'version'"))
        if (version.removePrefix("v") !in SupportedVersions) return listOf(error("VALIDATION_FAILED", null, "Unsupported protocol version '$version'"))
        if (depth(envelope) > MAX_DEPTH) return listOf(error("VALIDATION_FAILED", null, "Message nests deeper than $MAX_DEPTH levels"))
        // An envelope carries exactly one message (A2UI §"Envelope message structure").
        val kinds = envelope.keys.filter { it in MessageKinds }
        if (kinds.size > 1) return listOf(error("VALIDATION_FAILED", null, "One message per envelope, got ${kinds.joinToString()}"))
        (envelope["createSurface"] as? JsonObject)?.let { return create(it) }
        (envelope["updateComponents"] as? JsonObject)?.let { body ->
            val surface = body.surfaceId()?.let(byId::get) ?: return listOf(error("VALIDATION_FAILED", body.surfaceId(), "Unknown surface"))
            return addComponents(surface, (body["components"] as? JsonArray).orEmpty())
        }
        (envelope["updateDataModel"] as? JsonObject)?.let { body ->
            val surface = body.surfaceId()?.let(byId::get) ?: return listOf(error("VALIDATION_FAILED", body.surfaceId(), "Unknown surface"))
            return runCatching { surface.write(body.str("path") ?: "/", body["value"]) }
                .fold({ emptyList() }, { listOf(error("VALIDATION_FAILED", surface.id, it.message ?: "Invalid data model update")) })
        }
        (envelope["deleteSurface"] as? JsonObject)?.let { body ->
            body.surfaceId()?.let { id -> byId.remove(id); order.remove(id) }
            return emptyList()
        }
        (envelope["callRendererFunction"] as? JsonObject)?.let { return listOf(callFromAgent(it)) }
        if (envelope.containsKey("agentFunctionResponse")) return emptyList()
        return listOf(error("VALIDATION_FAILED", null, "Unknown A2UI message: ${envelope.keys}"))
    }

    private fun create(body: JsonObject): List<JsonObject> {
        val id = body.surfaceId() ?: return listOf(error("VALIDATION_FAILED", null, "createSurface needs a surfaceId"))
        if (id in byId) return listOf(error("VALIDATION_FAILED", id, "Surface $id already exists"))
        val surface = A2uiSurface(id, body.str("catalogId"), (body["sendDataModel"] as? JsonPrimitive)?.contentOrNull == "true")
        (body["dataModel"] as? JsonObject)?.let { surface.dataModel = it }
        val errors = addComponents(surface, (body["components"] as? JsonArray).orEmpty())
        if (errors.isNotEmpty()) return errors
        byId[id] = surface
        order += id
        return emptyList()
    }

    /**
     * Apply one message's components atomically: if the updated tree would contain a cycle (a
     * component reaching itself through child references), nothing changes and an error is returned.
     */
    private fun addComponents(surface: A2uiSurface, components: List<JsonElement>): List<JsonObject> {
        val incoming = components.mapNotNull { it as? JsonObject }.filter { it.str("id") != null && it.str("component") != null }
        // Mixed catalogs must follow the surface's protocol version.
        val surfaceVersion = surface.catalogId?.let(registry::get)?.protocolVersion ?: A2ui.VERSION
        incoming.firstOrNull { c -> c.str("catalogId")?.let(registry::get)?.let { it.protocolVersion != surfaceVersion } == true }?.let {
            return listOf(error("VALIDATION_FAILED", surface.id, "Component '${it.str("id")}' uses catalog '${it.str("catalogId")}' of another protocol version"))
        }
        val candidate = surface.components.toMutableMap().apply { incoming.forEach { put(it.str("id")!!, it) } }
        cycle(candidate)?.let { return listOf(error("VALIDATION_FAILED", surface.id, "Component cycle through '$it'")) }
        incoming.forEach { surface.components[it.str("id")!!] = it }
        return emptyList()
    }

    /** The agent calls a renderer function: only functions the catalog exposes to agents run. */
    private fun callFromAgent(body: JsonObject): JsonObject {
        val callId = body.str("functionCallId").orEmpty()
        val call = body["callFunction"] as? JsonObject
        val name = call?.str("call")
        val function = name?.let(catalog.functions::get)
        if (function == null || !function.callableByAgent) {
            return buildJsonObject {
                put("version", A2ui.VERSION)
                put("error", buildJsonObject {
                    put("code", "INVALID_FUNCTION_CALL")
                    put("message", "Function '$name' cannot be invoked by the agent.")
                    put("functionCallId", callId)
                })
            }
        }
        val args = (call["args"] as? JsonObject).orEmpty()
        val value = runCatching { function.invoke(args, null) }
        return buildJsonObject {
            put("version", A2ui.VERSION)
            value.fold(
                onSuccess = { v -> put("rendererFunctionResponse", buildJsonObject { put("functionCallId", callId); v?.let { put("value", it) } }) },
                onFailure = { e -> put("error", buildJsonObject { put("code", "FUNCTION_ERROR"); put("message", e.message.orEmpty()); put("functionCallId", callId) }) },
            )
        }
    }

    private fun error(code: String, surfaceId: String?, message: String) = buildJsonObject {
        put("version", A2ui.VERSION)
        put("error", buildJsonObject {
            put("code", code)
            surfaceId?.let { put("surfaceId", it) }
            put("message", message)
        })
    }
}

/** The complete top-level JSON values in [text] (arrays are flattened into their elements). */
internal fun messagesIn(text: String): List<JsonElement> {
    val out = mutableListOf<JsonElement>()
    var depth = 0
    var start = -1
    var inString = false
    var escaped = false
    text.forEachIndexed { i, c ->
        when {
            inString -> when {
                escaped -> escaped = false
                c == '\\' -> escaped = true
                c == '"' -> inString = false
            }
            c == '"' -> inString = true
            c == '{' || c == '[' -> { if (depth == 0) start = i; depth++ }
            c == '}' || c == ']' -> {
                depth--
                if (depth == 0 && start >= 0) {
                    runCatching { Json.parseToJsonElement(text.substring(start, i + 1)) }.getOrNull()?.let { value ->
                        if (value is JsonArray) out.addAll(value) else out.add(value)
                    }
                    start = -1
                }
            }
        }
    }
    return out
}

private const val MAX_DEPTH = 64

private fun depth(e: JsonElement): Int = when (e) {
    is JsonObject -> 1 + (e.values.maxOfOrNull(::depth) ?: 0)
    is JsonArray -> 1 + (e.maxOfOrNull(::depth) ?: 0)
    else -> 0
}

/** Component ids a component references as children (`child`, `children`, templates, tabs, modal parts). */
internal fun childIds(component: JsonObject): List<String> = buildList {
    component.forEach { (key, value) ->
        when {
            key == "children" && value is JsonArray -> value.forEach { (it as? JsonPrimitive)?.contentOrNull?.let(::add) }
            key == "children" && value is JsonObject -> value.str("componentId")?.let(::add)
            key in setOf("child", "trigger", "content") && value is JsonPrimitive && value.isString -> add(value.content)
            key == "tabs" && value is JsonArray -> value.forEach { (it as? JsonObject)?.str("child")?.let(::add) }
        }
    }
}

/** The id of a component on a cycle, if the adjacency list has one. */
private fun cycle(components: Map<String, JsonObject>): String? {
    val done = mutableSetOf<String>()
    val path = mutableSetOf<String>()
    fun visit(id: String): String? {
        if (id in path) return id
        if (!done.add(id)) return null
        path += id
        val found = components[id]?.let(::childIds)?.firstNotNullOfOrNull(::visit)
        path -= id
        return found
    }
    return components.keys.firstNotNullOfOrNull(::visit)
}

private val SupportedVersions = setOf("1.0", "0.9", "0.9.1")

private val MessageKinds = setOf("createSurface", "updateComponents", "updateDataModel", "deleteSurface", "callRendererFunction", "agentFunctionResponse")

/** The message's `surfaceId`, which must be a JSON string. */
private fun JsonObject.surfaceId(): String? = (this["surfaceId"] as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
internal fun JsonObject?.orEmpty(): JsonObject = this ?: JsonObject(emptyMap())
internal fun parseJsonObject(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject
