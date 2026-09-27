@file:Suppress("unused", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package dev.ai.elements.demo.samples

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.ai.elements.a2a.A2aAgent
import dev.ai.elements.a2a.A2aBackend
import dev.ai.elements.a2a.asSubAgent
import dev.ai.elements.acp.AcpAgent
import dev.ai.elements.acp.AcpBackend
import dev.ai.elements.core.chat.ChatBackend
import dev.ai.elements.core.chat.ChatController
import dev.ai.elements.core.chat.ChatEvent
import dev.ai.elements.core.chat.InputResponse
import dev.ai.elements.core.chat.ToolDecision
import dev.ai.elements.core.config.McpServerStore
import dev.ai.elements.core.config.ProviderKind
import dev.ai.elements.core.config.ProviderProfile
import dev.ai.elements.core.mcp.McpApps
import dev.ai.elements.core.mcp.McpClient
import dev.ai.elements.core.model.DataPart
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.ToolKind
import dev.ai.elements.core.protocol.agui.AgUiBackend
import dev.ai.elements.core.protocol.aisdk.UiMessageStreamBackend
import dev.ai.elements.core.skills.SkillLibrary
import dev.ai.elements.core.skills.Skills
import dev.ai.elements.genui.a2ui.a2uiRenderer
import dev.ai.elements.genui.a2ui.send
import dev.ai.elements.genui.jsx.jsxCodeBlocks
import dev.ai.elements.harness.AgentHarness
import dev.ai.elements.harness.LocalSubAgent
import dev.ai.elements.harness.filesystem.FileSystem
import dev.ai.elements.harness.memory.FileMemoryStore
import dev.ai.elements.harness.memory.Memory
import dev.ai.elements.harness.model
import dev.ai.elements.harness.planning.Planning
import dev.ai.elements.harness.sandbox.AlpineSandbox
import dev.ai.elements.harness.shell.Shell
import dev.ai.elements.mcpapps.McpAppActions
import dev.ai.elements.mcpapps.McpAppsHost
import dev.ai.elements.ui.chat.AiElementsRenderers
import dev.ai.elements.ui.chat.Chat
import dev.ai.elements.ui.chat.Conversation
import dev.ai.elements.ui.chat.DataRenderer
import dev.ai.elements.ui.chat.FileLoader
import dev.ai.elements.ui.chat.LocalAiElementsRenderers
import dev.ai.elements.ui.chat.LocalFileLoader
import dev.ai.elements.ui.chat.PromptInput
import dev.ai.elements.ui.chat.ToolRenderer
import dev.ai.elements.ui.chat.rememberChat
import dev.ai.elements.ui.theme.AiElementsTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

// The code shown on the documentation site (docs/, `--8<--` sections) and in the README, compiled
// with the demo so it cannot rot. Keep each section self-explanatory: it is read out of context.

@Composable
fun MinimalChat() {
    // --8<-- [start:minimal]
    AiElementsTheme {
        Chat(rememberChat { approver -> AgUiBackend("https://agents.example.com/api/agui", approver = approver) })
    }
    // --8<-- [end:minimal]
}

// --8<-- [start:viewmodel]
class ChatViewModel : ViewModel() {
    // An AG-UI agent (Pydantic AI, LangGraph, CrewAI, Mastra…). Tools that need approval pause
    // the run as an AG-UI interrupt; the UI shows a Confirmation and resumes it.
    val chat = ChatController(backend = { approver ->
        AgUiBackend("https://agents.example.com/api/agui", approver = approver)
    }, scope = viewModelScope)
}

@Composable
fun ChatScreen(vm: ChatViewModel) {
    val state by vm.chat.state.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    AiElementsTheme {
        Column(Modifier.fillMaxSize().imePadding()) {
            Conversation(
                state = state,
                modifier = Modifier.weight(1f),
                onRegenerate = vm.chat::regenerate,
                onToolApproval = vm.chat::respondToApproval,
                onToolDecision = vm.chat::respondToApproval,   // deny with a reason, edit then approve
                onInputResponse = vm.chat::respondToInput,     // forms the agent asks the user to fill
                onSelectVersion = vm.chat::selectVersion,
                onRestoreCheckpoint = vm.chat::restoreCheckpoint,
            )
            PromptInput(
                value = input,
                onValueChange = { input = it },
                onSubmit = { if (vm.chat.send(input)) input = "" },
                onStop = vm.chat::stop,
                busy = state.isBusy,
                allowQueue = true,
            )
        }
    }
}
// --8<-- [end:viewmodel]

object Backends {
    fun all(context: Context) = listOf<(dev.ai.elements.core.chat.ToolApprover) -> ChatBackend>(
        // --8<-- [start:backends]
        // AI SDK 5 / 6 UI Message Stream (any `toUIMessageStreamResponse()` server)
        { approver -> UiMessageStreamBackend("https://agents.example.com/api/chat", approver = approver) },
        // AG-UI 1.x (Pydantic AI, LangGraph, CrewAI, Mastra, …)
        { approver -> AgUiBackend("https://agents.example.com/api/agui", approver = approver) },
        // A2A 1.0 remote agent (ai-elements-a2a)
        { _ -> A2aBackend(A2aAgent("https://agents.example.com")) },
        // Agent Client Protocol coding agent (ai-elements-acp)
        { approver -> AcpBackend(AcpAgent.webSocket("wss://dev-box.example.com/acp"), approver) },
        // A model API on the device, with the built-in agent loop
        { approver ->
            ProviderProfile("openai", "OpenAI", ProviderKind.OPENAI_RESPONSES, "https://api.openai.com/v1", "gpt-5.5")
                .createBackend(apiKey = "…", approver = approver)
        },
        // --8<-- [end:backends]
    )
}

object A2aSamples {
    fun harness(model: dev.ai.elements.harness.ModelBinding) {
        // --8<-- [start:a2a-subagent]
        val researcher = A2aAgent("https://agents.example.com")
        val harness = AgentHarness(
            model = model,
            // Remote agents the model can delegate to with `delegate_task`; an unreachable one is skipped.
            remoteSubAgents = { listOfNotNull(researcher.cardOrNull()?.let { card -> researcher.asSubAgent(card) }) },
        )
        // --8<-- [end:a2a-subagent]
    }
}

object AcpSamples {
    fun agents(): List<AcpAgent> = listOf(
        // --8<-- [start:acp-agents]
        // An agent on another machine, over the ACP Kotlin SDK's WebSocket transport
        AcpAgent.webSocket("wss://dev-box.example.com/acp", cwd = "/home/me/project"),
        // An agent the app starts itself, over stdio (the transport the ACP spec defines)
        AcpAgent.process(listOf("npx", "@zed-industries/claude-code-acp"), cwd = "/workspace"),
        // --8<-- [end:acp-agents]
    )

    // --8<-- [start:acp-chat]
    class CodingAgentViewModel : ViewModel() {
        private val agent = AcpAgent.webSocket("wss://dev-box.example.com/acp", cwd = "/home/me/project")

        // Each conversation is one ACP session; permission requests show as approvals.
        val chat = ChatController(backend = { approver -> AcpBackend(agent, approver) }, scope = viewModelScope)

        override fun onCleared() = agent.close()
    }
    // --8<-- [end:acp-chat]
}

// --8<-- [start:custom-backend]
class MyBackend(private val client: MyClient) : ChatBackend {
    override fun stream(history: List<Message>): Flow<ChatEvent> = flow {
        client.run(history.last().text).collect { e ->
            when (e) {
                is MyText -> emit(ChatEvent.TextDelta("answer", e.chunk))
                is MyToolCall -> emit(ChatEvent.ToolInputAvailable(e.id, e.name, e.argsJson, kind = ToolKind.Function))
                is MyToolResult -> emit(ChatEvent.ToolOutput(e.id, e.text))
                is MyHandoff -> emit(ChatEvent.ToolInputAvailable(e.id, "handoff", "{}", kind = ToolKind.Delegation(e.agent, e.task)))
            }
        }
        emit(ChatEvent.Finish)
    }
}
// --8<-- [end:custom-backend]

@Composable
fun CustomRendering(controller: ChatController, myHttp: MyHttp) {
    // --8<-- [start:renderers]
    CompositionLocalProvider(
        LocalAiElementsRenderers provides AiElementsRenderers(
            tools = mapOf("get_weather" to ToolRenderer { part, _ -> WeatherCard(part.output) }),
            data = mapOf("chart" to DataRenderer { part -> MyChart(part.data) }),   // `data-chart` parts
        ),
        LocalFileLoader provides FileLoader { url -> myHttp.bytes(url) },         // attachment previews
    ) {
        Chat(controller)
    }
    // --8<-- [end:renderers]
}

@Composable
fun GenerativeUi(controller: ChatController) {
    // --8<-- [start:genui]
    CompositionLocalProvider(
        LocalAiElementsRenderers provides AiElementsRenderers(
            data = mapOf(DataPart.A2UI to a2uiRenderer { action -> controller.send(action) }),
            codeBlocks = mapOf("jsx" to jsxCodeBlocks { action -> controller.send(action) }),   // ```jsx fences → JsxPreview
        ),
    ) {
        Chat(controller)
    }
    // --8<-- [end:genui]
}

object HumanInTheLoop {
    fun answers(chat: ChatController) {
        // --8<-- [start:hitl]
        chat.respondToApproval("call-1", approved = true)
        chat.respondToApproval("call-2", ToolDecision(approved = false, reason = "Not on the production database"))
        chat.respondToInput("booking-1", InputResponse.Accept(buildJsonObject { put("guests", 2) }))
        // --8<-- [end:hitl]
    }
}

object McpSamples {
    suspend fun client() {
        // --8<-- [start:mcp-client]
        val mcp = McpClient("https://mcp.example.com/mcp")          // 2026-07-28, falls back to 2025-xx sessions
        val tools = mcp.listTools()
        val result = mcp.callTool(
            "book_table",
            buildJsonObject { put("restaurant", "Noma") },
            onInput = { request -> InputResponse.Accept(buildJsonObject { put("guests", 2) }) },  // elicitation
            onProgress = { progress -> println("${progress.fraction}") },                         // notifications/progress
        )
        // --8<-- [end:mcp-client]
    }
}

@Composable
fun McpAppsChat(context: Context, controller: ChatController) {
    // --8<-- [start:mcp-apps]
    val servers = McpServerStore(context, clientCapabilities = McpApps.CLIENT_CAPABILITIES) // advertise MCP Apps
    val actions = object : McpAppActions {
        override fun message(text: String) = controller.send(text)                // `ui/message`
    }
    McpAppsHost({ id -> servers.client(id) }, actions) { apps ->
        CompositionLocalProvider(LocalAiElementsRenderers provides AiElementsRenderers(data = mapOf(DataPart.MCP_APP to apps))) {
            Chat(controller)
        }
    }
    // --8<-- [end:mcp-apps]
}

object InAppAgent {
    fun harness(context: Context, key: String, mcpServers: McpServerStore, viewModel: ViewModel): ChatController {
        // --8<-- [start:in-app-agent]
        val workspace = File(context.filesDir, "workspace")
        val harness = AgentHarness(
            model = ProviderProfile("openai", "OpenAI", ProviderKind.OPENAI_RESPONSES, "https://api.openai.com/v1", "gpt-5.5")
                .model(apiKey = key),
            capabilities = {
                listOf(
                    FileSystem(workspace),                                  // workspace files
                    Shell(AlpineSandbox(context, workspace)),               // Linux sandbox at /workspace
                    Memory(FileMemoryStore(File(context.filesDir, "memory"))),
                    Planning(),
                    Skills.from(SkillLibrary.assets(context.assets, "skills")),
                    mcpServers.toolset(),                                   // McpServerStore
                )
            },
            localSubAgents = { listOf(LocalSubAgent("researcher", "Researches a topic", "Be thorough; cite sources.")) },
        )
        val chat = ChatController(backend = harness::backend, scope = viewModel.viewModelScope)
        // --8<-- [end:in-app-agent]
        return chat
    }
}

// Stand-ins for the app's own types in the samples above.
interface MyClient { fun run(prompt: String): Flow<MyEvent> }
sealed interface MyEvent
class MyText(val chunk: String) : MyEvent
class MyToolCall(val id: String, val name: String, val argsJson: String) : MyEvent
class MyToolResult(val id: String, val text: String) : MyEvent
class MyHandoff(val id: String, val agent: String, val task: String) : MyEvent
interface MyHttp { suspend fun bytes(url: String): ByteArray? }
@Composable fun WeatherCard(output: String?) = Text(output.orEmpty())
@Composable fun MyChart(data: JsonElement) = Text(data.toString())
