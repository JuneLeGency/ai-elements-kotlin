package dev.ai.elements.demo

import androidx.test.core.app.ApplicationProvider
import dev.ai.elements.core.config.ProviderProfile
import dev.ai.elements.demo.data.CapabilitySettings
import java.io.File

/**
 * The app's stores live in [DemoApplication] for the whole test process, so tests reset
 * them in place (clearing SharedPreferences behind their back would not reach them):
 * default providers, capabilities and no MCP servers or conversations; then [profile] is selected.
 */
fun resetDemoApp(profile: ProviderProfile? = null): DemoApplication {
    val app = ApplicationProvider.getApplicationContext<DemoApplication>()
    File(app.filesDir, "conversations.json").delete()
    app.providers.profiles.value.forEach { if (it.builtIn) app.providers.reset(it.id) else app.providers.remove(it.id) }
    app.agents.update { CapabilitySettings() }
    app.mcpServers.servers.value.forEach { app.mcpServers.remove(it.id) }
    profile?.let {
        app.providers.upsert(it)
        app.providers.select(it.id)
    }
    return app
}

/** Only the built-in tools (no harness capabilities, skills, MCP or agents): small prompts for small local models. */
fun DemoApplication.leanAgent(koog: Boolean = false) = agents.update {
    CapabilitySettings(
        koogRuntime = koog,
        workspaceFiles = false, sandboxShell = false, memory = false, planning = false, webBrowser = false,
        deviceTools = false, speech = false, scheduledTasks = false, skillsEnabled = false, mcpEnabled = false,
        subAgents = emptyList(), remoteAgents = emptyList(),
    )
}
