package dev.ai.elements.demo.samples

import android.content.Context
import dev.ai.elements.a2a.A2aAgent
import dev.ai.elements.a2a.A2aBackend
import dev.ai.elements.core.chat.ChatBackend
import dev.ai.elements.core.chat.ToolApprover
import dev.ai.elements.core.config.McpServerStore
import dev.ai.elements.core.config.ProviderKind
import dev.ai.elements.core.config.ProviderProfile
import dev.ai.elements.core.protocol.agui.AgUiBackend
import dev.ai.elements.core.protocol.aisdk.UiMessageStreamBackend
import dev.ai.elements.core.skills.SkillLibrary
import dev.ai.elements.core.skills.Skills
import dev.ai.elements.harness.AgentHarness
import dev.ai.elements.harness.LocalSubAgent
import dev.ai.elements.harness.filesystem.FileSystem
import dev.ai.elements.harness.memory.FileMemoryStore
import dev.ai.elements.harness.memory.Memory
import dev.ai.elements.harness.model
import dev.ai.elements.harness.planning.Planning
import dev.ai.elements.harness.sandbox.AlpineSandbox
import dev.ai.elements.harness.shell.Shell
import java.io.File

/** The README's quick starts, compiled with the demo so they cannot rot. */
@Suppress("unused")
internal object ReadmeSamples {
    fun pureClient(approver: ToolApprover): List<ChatBackend> = listOf(
        AgUiBackend("https://agents.example.com/api/agui", approver = approver),
        UiMessageStreamBackend("https://agents.example.com/api/chat", approver = approver),
        A2aBackend(A2aAgent("https://agents.example.com")),
    )

    fun inAppAgent(context: Context, key: String, mcpServers: McpServerStore): AgentHarness {
        val workspace = File(context.filesDir, "workspace")
        return AgentHarness(
            model = ProviderProfile("openai", "OpenAI", ProviderKind.OPENAI_RESPONSES, "https://api.openai.com/v1", "gpt-5.5")
                .model(apiKey = key),
            capabilities = {
                listOf(
                    FileSystem(workspace),
                    Shell(AlpineSandbox(context, workspace)),
                    Memory(FileMemoryStore(File(context.filesDir, "memory"))),
                    Planning(),
                    Skills.from(SkillLibrary.assets(context.assets, "skills")),
                    mcpServers.toolset(),
                )
            },
            localSubAgents = { listOf(LocalSubAgent("researcher", "Researches a topic", "Be thorough; cite sources.")) },
        )
    }
}
