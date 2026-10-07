@file:Suppress("unused", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package dev.ai.elements.demo.samples

import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import dev.ai.elements.core.chat.*
import dev.ai.elements.core.model.*
import dev.ai.elements.ui.chat.*
import dev.ai.elements.ui.code.*
import dev.ai.elements.ui.markdown.*
import dev.ai.elements.ui.voice.*
import dev.ai.elements.ui.workflow.*
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import dev.ai.elements.core.agent.AgentTool
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
import dev.ai.elements.core.model.ToolCategory
import dev.ai.elements.core.model.ToolKind
import dev.ai.elements.core.protocol.agui.AgUiBackend
import dev.ai.elements.core.protocol.agui.AgUiEventLog
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
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
fun ReplayableRuns(context: android.content.Context) {
    // --8<-- [start:replay]
    // AG-UI serialization: every run's events, one JSON log per thread.
    val log = remember { AgUiEventLog.Files(File(context.filesDir, "agui-events")) }
    val controller = rememberChat { approver ->
        AgUiBackend("https://agents.example.com/api/agui", approver = approver, eventLog = log)
    }
    // The agent's computer offers "Replay run" for replies with recorded events.
    Chat(controller, replay = { message -> log.replayOf(message) })
    // --8<-- [end:replay]
}

object ToolCategories {
    // --8<-- [start:categories]
    val listFiles = object : AgentTool {
        override val name = "list_files"
        override val description = "List the files in a folder."
        override val parameters = buildJsonObject { put("type", "object") }
        // What the call does (ACP ToolKind) and where: the computer shows it as a search of that folder.
        override fun categoryFor(arguments: JsonObject) = ToolCategory.SEARCH
        override fun locationFor(arguments: JsonObject) = arguments["path"]?.jsonPrimitive?.contentOrNull
        override suspend fun execute(arguments: JsonObject) = "README.md\nsrc/"
    }
    // --8<-- [end:categories]
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

/** Minimal component examples compiled with the demo and embedded in the documentation. */
object ComponentExamples {
    // --8<-- [start:component-conversation]
    @Composable
    fun ConversationExample(controller: ChatController) {
        val state by controller.state.collectAsStateWithLifecycle()
        Conversation(state, onToolDecision = controller::respondToApproval, onInputResponse = controller::respondToInput)
    }
    // --8<-- [end:component-conversation]

    // --8<-- [start:component-messages]
    @Composable
    fun MessagesExample(message: Message, controller: ChatController) {
        MessageItem(message, onToolDecision = controller::respondToApproval)
    }
    // --8<-- [end:component-messages]

    // --8<-- [start:component-prompt-input]
    @Composable
    fun PromptInputExample(controller: ChatController) {
        val state by controller.state.collectAsStateWithLifecycle()
        var text by rememberSaveable { mutableStateOf("") }
        PromptInput(text, { text = it }, { if (controller.send(text)) text = "" }, controller::stop, state.isBusy, allowQueue = true)
    }
    // --8<-- [end:component-prompt-input]

    // --8<-- [start:component-suggestions]
    @Composable
    fun SuggestionsExample(controller: ChatController) {
        Suggestions(listOf(Suggestion("Explain this code")), onSelect = { controller.send(it.text) })
    }
    // --8<-- [end:component-suggestions]

    // --8<-- [start:component-empty-state]
    @Composable
    fun EmptyStateExample(controller: ChatController) {
        ChatEmptyState("How can I help?", suggestions = listOf(Suggestion("Make a plan")), onSelect = { controller.send(it.text) })
    }
    // --8<-- [end:component-empty-state]

    // --8<-- [start:component-branch]
    @Composable
    fun BranchExample(message: Message, controller: ChatController) {
        BranchSelector(message.versionIndex, message.versions.size, onSelect = { controller.selectVersion(message.id, it) })
    }
    // --8<-- [end:component-branch]

    // --8<-- [start:component-checkpoint]
    @Composable
    fun CheckpointExample(messageId: String, controller: ChatController) {
        Checkpoint(onRestore = { controller.restoreCheckpoint(messageId) })
    }
    // --8<-- [end:component-checkpoint]

    // --8<-- [start:component-queue]
    @Composable
    fun QueueExample(controller: ChatController) {
        val state by controller.state.collectAsStateWithLifecycle()
        Queue(state.queue, paused = state.queuePaused, onRemove = { controller.removeQueued(it.id) }, onSendNow = { controller.sendQueuedNow(it.id) })
    }
    // --8<-- [end:component-queue]

    // --8<-- [start:component-open-in-chat]
    @Composable
    fun OpenInChatExample() {
        OpenInChat("Explain Kotlin coroutines")
    }
    // --8<-- [end:component-open-in-chat]

    // --8<-- [start:component-model-selector]
    @Composable
    fun ModelSelectorExample() {
        var selected by rememberSaveable { mutableStateOf("local") }
        ModelSelector(listOf(ModelOption("local", "Local model", "My provider")), selected, onSelect = { selected = it.id })
    }
    // --8<-- [end:component-model-selector]

    // --8<-- [start:component-context]
    @Composable
    fun ContextExample() {
        ContextUsage(Usage(inputTokens = 1200, outputTokens = 300), contextWindow = 32000)
    }
    // --8<-- [end:component-context]

    // --8<-- [start:component-loading]
    @OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun LoadingExample() {
        androidx.compose.material3.LoadingIndicator()
    }
    // --8<-- [end:component-loading]

    // --8<-- [start:component-shimmer]
    @Composable
    fun ShimmerExample() {
        ShimmerText("Preparing the answer", active = true)
    }
    // --8<-- [end:component-shimmer]

    // --8<-- [start:component-markdown]
    @Composable
    fun MarkdownExample(text: String, streaming: Boolean) {
        MarkdownContent(text, streaming = streaming)
    }
    // --8<-- [end:component-markdown]

    // --8<-- [start:component-code-block]
    @Composable
    fun CodeBlockExample() {
        CodeBlock("val answer = 42", "kotlin")
    }
    // --8<-- [end:component-code-block]

    // --8<-- [start:component-math]
    @Composable
    fun MathExample(formula: String) {
        MathBlock(formula)
    }
    // --8<-- [end:component-math]

    // --8<-- [start:component-mermaid-flowchart]
    @Composable
    fun MermaidFlowchartExample() {
        MermaidDiagram("flowchart LR\n  A[Start] --> B[Done]")
    }
    // --8<-- [end:component-mermaid-flowchart]

    // --8<-- [start:component-mermaid-sequence]
    @Composable
    fun MermaidSequenceExample() {
        MermaidDiagram("sequenceDiagram\n  App->>Agent: Run\n  Agent-->>App: Reply")
    }
    // --8<-- [end:component-mermaid-sequence]

    // --8<-- [start:component-mermaid-class]
    @Composable
    fun MermaidClassExample() {
        MermaidDiagram("classDiagram\n  class Message")
    }
    // --8<-- [end:component-mermaid-class]

    // --8<-- [start:component-mermaid-pie]
    @Composable
    fun MermaidPieExample() {
        MermaidDiagram("pie\n  \"Done\" : 3\n  \"Pending\" : 1")
    }
    // --8<-- [end:component-mermaid-pie]

    // --8<-- [start:component-mermaid-streaming]
    @Composable
    fun MermaidStreamingExample(source: String, complete: Boolean) {
        MermaidDiagram(source, complete = complete)
    }
    // --8<-- [end:component-mermaid-streaming]

    // --8<-- [start:component-reasoning]
    @Composable
    fun ReasoningExample() {
        Reasoning(ReasoningPart("thinking", "Compare the alternatives", durationMs = 1500))
    }
    // --8<-- [end:component-reasoning]

    // --8<-- [start:component-sources]
    @Composable
    fun SourcesExample() {
        Sources(listOf(SourcePart("docs", "https://kotlinlang.org/docs/", "Kotlin docs")))
    }
    // --8<-- [end:component-sources]

    // --8<-- [start:component-inline-citation]
    @Composable
    fun InlineCitationExample() {
        val sources = listOf(SourcePart("1", "https://kotlinlang.org/docs/", "Kotlin docs"))
        MarkdownContent("Read the language guide [1].", citations = sources)
    }
    // --8<-- [end:component-inline-citation]

    // --8<-- [start:component-tool-calls]
    @Composable
    fun ToolCallsExample(part: ToolPart, controller: ChatController) {
        ToolCall(part, onDecision = { controller.respondToApproval(part.id, it) })
    }
    // --8<-- [end:component-tool-calls]

    // --8<-- [start:component-sub-agents]
    @Composable
    fun SubAgentsExample(part: ToolPart, controller: ChatController) {
        ToolPartView(part, onToolDecision = controller::respondToApproval)
    }
    // --8<-- [end:component-sub-agents]

    // --8<-- [start:component-agent-computer]
    @Composable
    fun AgentComputerExample(message: Message) {
        val computer = rememberAgentComputerState()
        AgentComputerScaffold(computer, listOf(message)) {
            androidx.compose.material3.Button(onClick = { computer.open(message.id) }) { Text("Open computer") }
        }
    }
    // --8<-- [end:component-agent-computer]

    // --8<-- [start:component-agent-computer-reply]
    @Composable
    fun AgentComputerReplyExample(controller: ChatController) {
        Chat(controller)
    }
    // --8<-- [end:component-agent-computer-reply]

    // --8<-- [start:component-step-views]
    @Composable
    fun StepViewsExample(tool: ToolPart) {
        StepView(AgentStep(tool, emptyList()))
    }
    // --8<-- [end:component-step-views]

    // --8<-- [start:component-agent]
    @Composable
    fun AgentExample() {
        Agent(name = "Researcher", model = "My model", instructions = "Cite sources", tools = listOf(AgentToolSpec("search", "Search documents")))
    }
    // --8<-- [end:component-agent]

    // --8<-- [start:component-confirmation]
    @Composable
    fun ConfirmationExample(part: ToolPart, controller: ChatController) {
        ToolCall(part.copy(state = ToolState.APPROVAL_REQUESTED), onDecision = { controller.respondToApproval(part.id, it) })
    }
    // --8<-- [end:component-confirmation]

    // --8<-- [start:component-question]
    @Composable
    fun QuestionExample() {
        var answer by remember { mutableStateOf<QuestionAnswer?>(null) }
        Question("Choose a platform", listOf(QuestionOption("android", "Android")), answered = answer, onSubmit = { answer = it })
    }
    // --8<-- [end:component-question]

    // --8<-- [start:component-input-request]
    @Composable
    fun InputRequestExample(request: InputRequest, controller: ChatController) {
        InputRequestCard(request, onRespond = { controller.respondToInput(request.id, it) })
    }
    // --8<-- [end:component-input-request]

    // --8<-- [start:component-plan]
    @Composable
    fun PlanExample() {
        Plan("Release", description = "Review before publishing", steps = listOf(WorkflowStep("Review", status = StepStatus.COMPLETE), WorkflowStep("Publish", status = StepStatus.PENDING)))
    }
    // --8<-- [end:component-plan]

    // --8<-- [start:component-task]
    @Composable
    fun TaskExample() {
        Task("Update docs", listOf(WorkflowStep("Write examples", badges = listOf("README.md"))))
    }
    // --8<-- [end:component-task]

    // --8<-- [start:component-chain-of-thought]
    @Composable
    fun ChainOfThoughtExample() {
        ChainOfThought(listOf(WorkflowStep("Read the documentation", status = StepStatus.COMPLETE)))
    }
    // --8<-- [end:component-chain-of-thought]

    // --8<-- [start:component-data-parts]
    @Composable
    fun DataPartsExample(part: DataPart) {
        DataPartView(part)
    }
    // --8<-- [end:component-data-parts]

    // --8<-- [start:component-jsx-typography]
    @Composable
    fun JsxTypographyExample() {
        dev.ai.elements.genui.jsx.JsxPreview("<div><h2>Summary</h2><p>Ready for review</p></div>", bindings = buildJsonObject { put("title", "Report"); put("description", "Ready"); put("choice", "a") })
    }
    // --8<-- [end:component-jsx-typography]

    // --8<-- [start:component-jsx-form]
    @Composable
    fun JsxFormExample(onAction: (dev.ai.elements.genui.a2ui.A2uiAction) -> Unit) {
        dev.ai.elements.genui.jsx.JsxPreview("<div><input value={name}/><button onClick={save}>Save</button></div>", bindings = buildJsonObject { put("name", "Ada") }, onAction = onAction)
    }
    // --8<-- [end:component-jsx-form]

    // --8<-- [start:component-jsx-controls]
    @Composable
    fun JsxControlsExample() {
        dev.ai.elements.genui.jsx.JsxPreview("<select value={choice}><option value=\"a\">Option A</option><option value=\"b\">Option B</option></select>", bindings = buildJsonObject { put("title", "Report"); put("description", "Ready"); put("choice", "a") })
    }
    // --8<-- [end:component-jsx-controls]

    // --8<-- [start:component-jsx-cards]
    @Composable
    fun JsxCardsExample() {
        dev.ai.elements.genui.jsx.JsxPreview("<div><h3>{title}</h3><p>{description}</p></div>", bindings = buildJsonObject { put("title", "Report"); put("description", "Ready"); put("choice", "a") })
    }
    // --8<-- [end:component-jsx-cards]

    // --8<-- [start:component-jsx-streaming]
    @Composable
    fun JsxStreamingExample(partialJsx: String) {
        dev.ai.elements.genui.jsx.JsxPreview(partialJsx)
    }
    // --8<-- [end:component-jsx-streaming]

    // --8<-- [start:component-a2ui]
    @Composable
    fun A2UiExample(surface: dev.ai.elements.genui.a2ui.A2uiSurface, onAction: (dev.ai.elements.genui.a2ui.A2uiAction) -> Unit) {
        dev.ai.elements.genui.a2ui.A2uiSurfaceView(surface, onAction = onAction)
    }
    // --8<-- [end:component-a2ui]

    // --8<-- [start:component-artifact]
    @Composable
    fun ArtifactExample() {
        Artifact("answer.kt", description = "Generated code") { CodeBlock("val answer = 42", "kotlin") }
    }
    // --8<-- [end:component-artifact]

    // --8<-- [start:component-web-preview]
    @Composable
    fun WebPreviewExample() {
        WebPreview(url = "about:blank", html = "<h1>Preview</h1><p>Generated content</p>")
    }
    // --8<-- [end:component-web-preview]

    // --8<-- [start:component-image]
    @Composable
    fun ImageExample(image: FilePart) {
        FileAttachment(image)
    }
    // --8<-- [end:component-image]

    // --8<-- [start:component-attachments]
    @Composable
    fun AttachmentsExample(initialFiles: List<FilePart>) {
        var files by remember { mutableStateOf(initialFiles) }
        AttachmentStrip(files, onRemove = { files = files - it })
    }
    // --8<-- [end:component-attachments]

    // --8<-- [start:component-video]
    @Composable
    fun VideoExample(video: FilePart) {
        VideoAttachment(video)
    }
    // --8<-- [end:component-video]

    // --8<-- [start:component-document]
    @Composable
    fun DocumentExample(document: FilePart) {
        DocumentAttachment(document)
    }
    // --8<-- [end:component-document]

    // --8<-- [start:component-audio]
    @Composable
    fun AudioExample(source: String) {
        val player = rememberAudioPlayerState(source)
        Column {
            AudioPlayer(player)
            Transcription(listOf(TranscriptSegment("Hello", 0, 1500)), currentTimeMs = player.positionMs, onSeek = { player.seekTo(it) })
        }
    }
    // --8<-- [end:component-audio]

    // --8<-- [start:component-voice-mode]
    @Composable
    fun VoiceModeExample(controller: ChatController, onClose: () -> Unit) {
        VoiceMode(controller, onClose = onClose)
    }
    // --8<-- [end:component-voice-mode]

    // --8<-- [start:component-persona]
    @Composable
    fun PersonaExample() {
        Persona(PersonaState.THINKING)
    }
    // --8<-- [end:component-persona]

    // --8<-- [start:component-speech-input]
    @Composable
    fun SpeechInputExample() {
        val speech = rememberSpeechInputState()
        var transcript by rememberSaveable { mutableStateOf("") }
        Column { SpeechInput(onTranscript = { text, _ -> transcript = text }, state = speech); Text(transcript) }
    }
    // --8<-- [end:component-speech-input]

    // --8<-- [start:component-voice-selectors]
    @Composable
    fun VoiceSelectorsExample() {
        var mic by remember { mutableStateOf<android.media.AudioDeviceInfo?>(null) }
        var voice by rememberSaveable { mutableStateOf("default") }
        Column {
            MicSelector(mic, onSelect = { mic = it })
            VoiceSelector(listOf(VoiceOption("default", "Default")), voice, onSelect = { voice = it.id })
        }
    }
    // --8<-- [end:component-voice-selectors]

    // --8<-- [start:component-terminal]
    @Composable
    fun TerminalExample() {
        Terminal("Tests passed\n", title = "Test output", status = TerminalStatus.SUCCESS, exitCode = 0)
    }
    // --8<-- [end:component-terminal]

    // --8<-- [start:component-stack-trace]
    @Composable
    fun StackTraceExample() {
        StackTrace("java.lang.IllegalStateException: Missing input\n    at example.App.run(App.kt:12)")
    }
    // --8<-- [end:component-stack-trace]

    // --8<-- [start:component-test-results]
    @Composable
    fun TestResultsExample() {
        TestResults(listOf(TestSuiteResult("Example", listOf(TestCaseResult("loads", TestStatus.PASSED, 25)))), durationMs = 25)
    }
    // --8<-- [end:component-test-results]

    // --8<-- [start:component-file-tree]
    @Composable
    fun FileTreeExample() {
        var selected by rememberSaveable { mutableStateOf<String?>(null) }
        FileTree(listOf(FileNode("README.md", "README.md")), selectedPath = selected, onSelect = { selected = it.path })
    }
    // --8<-- [end:component-file-tree]

    // --8<-- [start:component-commit]
    @Composable
    fun CommitExample() {
        Commit(hash = "abcdef1234", message = "Document the API", files = listOf(CommitFile("README.md", FileChange.MODIFIED, 10, 2)))
    }
    // --8<-- [end:component-commit]

    // --8<-- [start:component-schema-display]
    @Composable
    fun SchemaDisplayExample() {
        SchemaDisplay(method = "POST", path = "/chat", parameters = listOf(SchemaParameter("messages", "array", required = true)))
    }
    // --8<-- [end:component-schema-display]

    // --8<-- [start:component-package-info]
    @Composable
    fun PackageInfoExample() {
        PackageInfo("example:library", fromVersion = "1.0.0", toVersion = "1.1.0", change = PackageChange.MINOR)
    }
    // --8<-- [end:component-package-info]

    // --8<-- [start:component-environment-variables]
    @Composable
    fun EnvironmentVariablesExample() {
        EnvironmentVariables(listOf(EnvironmentVariable("MODEL", "local", secret = false)))
    }
    // --8<-- [end:component-environment-variables]

    // --8<-- [start:component-sandbox]
    @Composable
    fun SandboxExample() {
        Sandbox("Example", listOf(SandboxTab("Code") { CodeBlock("println(42)", "kotlin") }, SandboxTab("Output") { Text("42") }))
    }
    // --8<-- [end:component-sandbox]

    // --8<-- [start:component-snippet]
    @Composable
    fun SnippetExample() {
        Snippet("./gradlew testDebugUnitTest", prefix = "$")
    }
    // --8<-- [end:component-snippet]

    // --8<-- [start:component-workflow-canvas]
    @Composable
    fun WorkflowCanvasExample() {
        WorkflowCanvas(nodes = listOf(CanvasNode("start", "Start"), CanvasNode("end", "Done", position = androidx.compose.ui.unit.DpOffset(0.dp, 140.dp))), edges = listOf(CanvasEdge("start", "end")), modifier = Modifier.height(320.dp))
    }
    // --8<-- [end:component-workflow-canvas]

}

// --8<-- [start:custom-tool-decisions]
@Composable
fun CustomToolDecisions(controller: ChatController) {
    CompositionLocalProvider(
        LocalAiElementsRenderers provides AiElementsRenderers(
            tools = mapOf("write_file" to ToolRenderer { part, respond ->
                ToolCall(part, onDecision = respond?.let { cb -> { decision -> cb(part.id, decision) } })
            }),
        ),
    ) { Chat(controller) }
}
// --8<-- [end:custom-tool-decisions]

// --8<-- [start:minimal-in-app-agent]
class AppAgentViewModel(app: android.app.Application) : androidx.lifecycle.AndroidViewModel(app) {
    private val harness = AgentHarness(
        model = ProviderProfile(
            "ollama", "Ollama", ProviderKind.OLLAMA,
            "http://10.0.2.2:11434", "qwen3:4b",
        ).model(),
        capabilities = { listOf(FileSystem(File(app.filesDir, "workspace")), Planning()) },
    )
    val chat = ChatController(backend = harness::backend, scope = viewModelScope)
}

@Composable
fun AppAgentScreen(vm: AppAgentViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    AiElementsTheme { Chat(vm.chat) }
}
// --8<-- [end:minimal-in-app-agent]
