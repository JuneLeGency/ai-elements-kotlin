package dev.ai.elements.core.http

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Lenient accessors for provider JSON. */
internal fun JsonElement.errorMessage(): String =
    (this as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
        ?: runCatching { jsonPrimitive.contentOrNull }.getOrNull()
        ?: toString()

internal fun JsonObject.str(key: String): String? =
    runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()

internal fun JsonObject.int(key: String): Int? =
    runCatching { this[key]?.jsonPrimitive?.intOrNull }.getOrNull()

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
