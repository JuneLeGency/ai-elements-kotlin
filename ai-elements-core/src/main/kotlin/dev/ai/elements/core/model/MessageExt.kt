package dev.ai.elements.core.model

/** Image attachments of a message (only images are sent to models). */
internal val Message.images: List<FilePart>
    get() = parts.filterIsInstance<FilePart>().filter { it.isImage }

internal val Message.hasContent: Boolean
    get() = text.isNotBlank() || images.isNotEmpty()
