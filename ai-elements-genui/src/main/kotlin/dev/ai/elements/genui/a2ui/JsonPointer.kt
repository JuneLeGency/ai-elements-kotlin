package dev.ai.elements.genui.a2ui

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/** RFC 6901 JSON Pointers over immutable JSON, with A2UI's upsert semantics for writes. */
internal object JsonPointer {
    fun tokens(pointer: String): List<String> =
        pointer.trim().removePrefix("/").let { if (it.isEmpty()) emptyList() else it.split('/') }
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
     * [root] with [value] at [pointer]: missing containers are created (arrays when the next token
     * is an index into an existing array, objects otherwise); a null [value] removes the key.
     */
    fun set(root: JsonElement?, pointer: String, value: JsonElement?): JsonElement {
        val path = tokens(pointer)
        if (path.isEmpty()) return value?.takeUnless { it is JsonNull } ?: JsonObject(emptyMap())
        return setAt(root, path, value)
    }

    private fun setAt(node: JsonElement?, path: List<String>, value: JsonElement?): JsonElement {
        val token = path.first()
        val rest = path.drop(1)
        val remove = value == null || value is JsonNull
        return when (node) {
            is JsonArray -> {
                val index = token.toIntOrNull() ?: return node
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
            else -> {
                val map = (node as? JsonObject)?.toMutableMap() ?: mutableMapOf()
                if (rest.isEmpty()) {
                    if (remove) map.remove(token) else map[token] = value!!
                } else {
                    map[token] = setAt(map[token]?.takeUnless { it is JsonNull }, rest, value)
                }
                JsonObject(map)
            }
        }
    }
}
