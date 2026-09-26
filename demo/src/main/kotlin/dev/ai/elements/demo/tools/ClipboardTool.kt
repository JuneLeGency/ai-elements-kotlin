package dev.ai.elements.demo.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import dev.ai.elements.core.agent.AgentTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * An app-defined tool with a side effect on the device, so it asks the user
 * first ([requiresApproval]) — the chat shows a Confirmation card.
 */
class ClipboardTool(context: Context) : AgentTool {
    private val clipboard = context.getSystemService(ClipboardManager::class.java)

    override val name = "copy_to_clipboard"
    override val description = "Copy text to the user's clipboard. Requires the user's approval."
    override val requiresApproval = true
    override val parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("text") {
                put("type", "string")
                put("description", "The text to copy")
            }
        }
        putJsonArray("required") { add(JsonPrimitive("text")) }
    }

    override suspend fun execute(arguments: JsonObject): String {
        val text = requireNotNull(arguments["text"]?.jsonPrimitive?.contentOrNull) { "missing text" }
        withContext(Dispatchers.Main) { clipboard.setPrimaryClip(ClipData.newPlainText("AI Elements", text)) }
        return "Copied ${text.length} characters to the clipboard."
    }
}
