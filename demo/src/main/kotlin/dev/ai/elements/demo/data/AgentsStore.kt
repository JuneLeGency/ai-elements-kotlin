package dev.ai.elements.demo.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A sub-agent the chat can delegate to with `delegate_task`: its own
 * instructions on a provider ([providerId], or the chat's provider when null).
 */
@Serializable
data class SubAgentDef(
    val id: String,
    val name: String,
    val description: String,
    val instructions: String,
    val providerId: String? = null,
    val enabled: Boolean = true,
)

/** A remote A2A agent the chat can delegate to (its card supplies name and skills). */
@Serializable
data class RemoteAgentDef(
    val id: String,
    val url: String,
    val enabled: Boolean = true,
    /** Cached from the agent card, for display while offline. */
    val name: String = "",
    val description: String = "",
)

/** What the chat agent may use besides its provider: skills, MCP servers (see McpServerStore), agents. */
@Serializable
data class CapabilitySettings(
    val builtinTools: Boolean = true,
    /** In-app harness: workspace files, a Linux sandbox shell, persistent memory and task planning. */
    val workspaceFiles: Boolean = true,
    val sandboxShell: Boolean = true,
    val memory: Boolean = true,
    val planning: Boolean = true,
    val webBrowser: Boolean = true,
    val deviceTools: Boolean = true,
    val skillsEnabled: Boolean = true,
    val disabledSkills: Set<String> = emptySet(),
    val mcpEnabled: Boolean = true,
    val subAgents: List<SubAgentDef> = DefaultSubAgents,
    val remoteAgents: List<RemoteAgentDef> = DefaultRemoteAgents,
)

val DefaultSubAgents = listOf(
    SubAgentDef(
        id = "researcher",
        name = "researcher",
        description = "Researches a question step by step and reports concise findings.",
        instructions = "You are a careful researcher. Work through the task, use tools when they help, and report concise findings with the key facts first.",
    ),
    SubAgentDef(
        id = "writer",
        name = "writer",
        description = "Turns notes or findings into polished prose, release notes or diagrams.",
        instructions = "You are a precise writer. Load a matching skill before writing when one fits the task.",
    ),
)

/** The bundled server's A2A agent (`server/a2a_agent.py`); `10.0.2.2` is the emulator's host alias. */
val DefaultRemoteAgents = listOf(RemoteAgentDef("research-a2a", "http://10.0.2.2:8788", name = "Research agent"))

/** Persists [CapabilitySettings] as JSON in SharedPreferences. */
class AgentsStore(context: Context) {
    private val prefs = context.getSharedPreferences("ai_elements_capabilities", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _settings = MutableStateFlow(
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString(CapabilitySettings.serializer(), it) }.getOrNull() } ?: CapabilitySettings(),
    )
    val settings: StateFlow<CapabilitySettings> = _settings.asStateFlow()

    fun update(transform: (CapabilitySettings) -> CapabilitySettings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit().putString(KEY, json.encodeToString(CapabilitySettings.serializer(), next)).apply()
    }

    private companion object {
        const val KEY = "settings"
    }
}
