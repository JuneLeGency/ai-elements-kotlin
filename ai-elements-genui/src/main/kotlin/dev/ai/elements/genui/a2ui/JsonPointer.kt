package dev.ai.elements.genui.a2ui

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A data model write that cannot apply (e.g. through a primitive value). */
class A2uiDataError(message: String) : RuntimeException(message)

/** RFC 6901 JSON Pointers over immutable JSON, with A2UI's upsert semantics for writes. */
internal object JsonPointer {
    fun tokens(pointer: String): List<String> =
        // A trailing slash names the same location (`/foo/` is `/foo`).
        pointer.trim().removePrefix("/").removeSuffix("/").let { if (it.isEmpty()) emptyList() else it.split('/') }
            .map { it.replace("~1", "/").replace("~0", "~") }

    fun get(root: JsonElement?, pointer: String): JsonElement? =
        tokens(pointer).fold(root) { node, token ->
            when (node) {
                is JsonObject -> node[token]
                is JsonArray -> token.toIntOrNull()?.let { node.getOrNull(it) }
                else -> null
            }
        }

    /**
     * [root] with [value] at [pointer]: missing containers are created (a list when the token that
     * indexes into it is a number, an object otherwise); a null [value] removes the key or item.
     */
    fun set(root: JsonElement?, pointer: String, value: JsonElement?): JsonElement {
        val path = tokens(pointer)
        if (path.isEmpty()) return value?.takeUnless { it is JsonNull } ?: JsonObject(emptyMap())
        return setAt(root, path, value)
    }

    private fun String.isIndex() = isNotEmpty() && all { it.isDigit() }

    private fun setAt(node: JsonElement?, path: List<String>, value: JsonElement?): JsonElement {
        val token = path.first()
        val rest = path.drop(1)
        val remove = value == null || value is JsonNull
        return when (node) {
            is JsonArray -> {
                val index = token.toIntOrNull() ?: throw A2uiDataError("'$token' is not a list index")
                val items = node.toMutableList()
                if (rest.isEmpty()) {
                    when {
                        remove -> if (index in items.indices) items.removeAt(index)
                        index in items.indices -> items[index] = value!!
                        index == items.size -> items += value!!
                        else -> {
                            while (items.size < index) items += JsonNull
                            items += value!!
                        }
                    }
                } else {
                    while (items.size <= index) items += JsonNull
                    items[index] = setAt(items[index], rest, value)
                }
                JsonArray(items)
            }
            null, JsonNull -> if (token.isIndex()) setAt(JsonArray(emptyList()), path, value) else setAt(JsonObject(emptyMap()), path, value)
            is JsonPrimitive -> throw A2uiDataError("Cannot write through the value at '$token': it is not an object or a list")
            else -> {
                val map = (node as? JsonObject)?.toMutableMap() ?: mutableMapOf()
                if (rest.isEmpty()) {
                    if (remove) map.remove(token) else map[token] = value!!
                } else {
                    map[token] = setAt(map[token], rest, value)
                }
                JsonObject(map)
            }
        }
    }
}
