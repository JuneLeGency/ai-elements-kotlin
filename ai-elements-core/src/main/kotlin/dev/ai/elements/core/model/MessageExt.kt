package dev.ai.elements.core.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Image attachments of a message (only images are sent to models). */
internal val Message.images: List<FilePart>
    get() = parts.filterIsInstance<FilePart>().filter { it.isImage }

internal val Message.hasContent: Boolean
    get() = text.isNotBlank() || images.isNotEmpty()

/** The [DataPart.MODEL_CONTEXT] parts of a message as `description` to `value`. */
internal val Message.modelContext: List<Pair<String, String>>
    get() = parts.filterIsInstance<DataPart>().filter { it.name == DataPart.MODEL_CONTEXT }.mapNotNull { part ->
        val obj = part.data as? JsonObject ?: return@mapNotNull null
        val value = (obj["value"] as? JsonPrimitive)?.contentOrNull ?: obj["value"]?.toString() ?: return@mapNotNull null
        ((obj["description"] as? JsonPrimitive)?.contentOrNull ?: "Context") to value
    }

/**
 * [DataPart.MODEL_CONTEXT] parts of user turns as text the model reads before the user's words
 * (for model APIs, which have no context channel of their own).
 */
internal fun List<Message>.inlineModelContext(): List<Message> = map { message ->
    val context = message.takeIf { it.role == Role.USER }?.modelContext.orEmpty()
    if (context.isEmpty()) message
    else message.copy(parts = context.mapIndexed { i, (description, value) -> TextPart("${message.id}-context-$i", "<context description=\"$description\">\n$value\n</context>") } + message.parts)
}
