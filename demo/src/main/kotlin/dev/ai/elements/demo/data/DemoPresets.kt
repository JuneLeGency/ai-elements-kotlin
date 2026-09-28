package dev.ai.elements.demo.data

import dev.ai.elements.core.config.ProviderKind
import dev.ai.elements.core.config.ProviderProfile

/**
 * The demo's providers: the library's public model APIs plus the bundled reference server
 * (`server/`, Pydantic AI + Harness) over each protocol, a local Ollama and a local
 * OpenAI-compatible proxy ([CLIProxyAPI](https://github.com/router-for-me/CLIProxyAPI)).
 * `10.0.2.2` is the Android emulator's alias for the host machine; on a physical device use
 * `adb reverse` or the host's LAN address.
 */
val DemoPresets: List<ProviderProfile> = ProviderProfile.Presets.let { library ->
    val (mock, cloud) = library.partition { it.kind == ProviderKind.MOCK }
    mock + listOf(
        ProviderProfile("agent-server", "PydanticAI · AI SDK stream", ProviderKind.AGENT_SERVER, "http://10.0.2.2:8788", "", builtIn = true),
        ProviderProfile("agent-server-agui", "PydanticAI · AG-UI", ProviderKind.AG_UI, "http://10.0.2.2:8788", "", builtIn = true),
        ProviderProfile("ollama", "Ollama", ProviderKind.OLLAMA, "http://10.0.2.2:11434", "qwen3:4b", builtIn = true),
        ProviderProfile("cliproxy", "CLIProxyAPI", ProviderKind.OPENAI, "http://10.0.2.2:8317/v1", "gpt-5", builtIn = true),
    ) + cloud + listOf(
        ProviderProfile("a2a-research", "Research agent · A2A", ProviderKind.A2A, "http://10.0.2.2:8788", "", builtIn = true),
        ProviderProfile("acp-agent", "PydanticAI · ACP", ProviderKind.ACP, "ws://10.0.2.2:8788/acp", "", builtIn = true),
    )
}
