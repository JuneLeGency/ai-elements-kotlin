package dev.ai.elements.demo.ui

import android.media.AudioDeviceInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.ai.elements.ui.chat.Agent
import dev.ai.elements.ui.chat.AgentToolSpec
import dev.ai.elements.ui.chat.ModelCapability
import dev.ai.elements.ui.chat.ModelOption
import dev.ai.elements.ui.chat.ModelSelector
import dev.ai.elements.ui.chat.Persona
import dev.ai.elements.ui.chat.PersonaState
import dev.ai.elements.ui.chat.Question
import dev.ai.elements.ui.chat.QuestionAnswer
import dev.ai.elements.ui.chat.QuestionOption
import dev.ai.elements.ui.code.Commit
import dev.ai.elements.ui.code.CommitFile
import dev.ai.elements.ui.code.EnvironmentVariable
import dev.ai.elements.ui.code.EnvironmentVariables
import dev.ai.elements.ui.code.FileChange
import dev.ai.elements.ui.code.FileNode
import dev.ai.elements.ui.code.FileTree
import dev.ai.elements.ui.code.PackageChange
import dev.ai.elements.ui.code.PackageInfo
import dev.ai.elements.ui.code.Sandbox
import dev.ai.elements.ui.code.SandboxTab
import dev.ai.elements.ui.code.SchemaDisplay
import dev.ai.elements.ui.code.SchemaParameter
import dev.ai.elements.ui.code.Snippet
import dev.ai.elements.ui.code.StackTrace
import dev.ai.elements.ui.code.Terminal
import dev.ai.elements.ui.code.TerminalStatus
import dev.ai.elements.ui.code.TestCaseResult
import dev.ai.elements.ui.code.TestResults
import dev.ai.elements.ui.code.TestStatus
import dev.ai.elements.ui.code.TestSuiteResult
import dev.ai.elements.ui.markdown.CodeBlock
import dev.ai.elements.ui.voice.AudioPlayer
import dev.ai.elements.ui.voice.MicSelector
import dev.ai.elements.ui.voice.SpeechInput
import dev.ai.elements.ui.voice.Transcription
import dev.ai.elements.ui.voice.TranscriptSegment
import dev.ai.elements.ui.voice.VoiceOption
import dev.ai.elements.ui.voice.VoiceSelector
import dev.ai.elements.ui.voice.rememberAudioPlayerState
import dev.ai.elements.ui.voice.rememberSpeechInputState

private val SampleModels = listOf(
    ModelOption("gpt-5.5", "GPT-5.5", "OpenAI", "Flagship reasoning model", setOf(ModelCapability.REASONING, ModelCapability.TOOLS, ModelCapability.VISION), 400_000),
    ModelOption("gpt-5.3-codex", "GPT-5.3 Codex", "OpenAI", "Tuned for agentic coding", setOf(ModelCapability.REASONING, ModelCapability.TOOLS), 400_000),
    ModelOption("claude-opus-5-5", "Claude Opus 5.5", "Anthropic", "Most capable Claude", setOf(ModelCapability.REASONING, ModelCapability.TOOLS, ModelCapability.VISION), 1_000_000),
    ModelOption("claude-haiku-4-5", "Claude Haiku 4.5", "Anthropic", "Fast and small", setOf(ModelCapability.TOOLS, ModelCapability.FAST), 200_000),
    ModelOption("gemini-3-pro", "Gemini 3 Pro", "Google", null, setOf(ModelCapability.REASONING, ModelCapability.VISION, ModelCapability.AUDIO), 1_000_000),
    ModelOption("qwen3:4b", "Qwen3 4B", "Ollama (local)", "Runs on your machine", setOf(ModelCapability.REASONING, ModelCapability.TOOLS, ModelCapability.FAST), 32_000),
)

private const val SampleTerminal = "\u001B[1m$ ./gradlew test\u001B[0m\n" +
    "> Task :core:compileKotlin\n" +
    "> Task :core:test\n" +
    "\u001B[32mChatControllerTest > queue_sendsInOrder PASSED\u001B[0m\n" +
    "\u001B[32mOAuthTest > pkce_matchesRfc7636Vector PASSED\u001B[0m\n" +
    "\u001B[33mLiveCodexTest > chatgptSubscription SKIPPED\u001B[0m\n" +
    "\u001B[31mMarkdownTest > table_rendersAlignment FAILED\u001B[0m\n" +
    "\u001B[2m    expected:<center> but was:<left>\u001B[0m\n" +
    "\u001B[1mBUILD FAILED\u001B[0m in 6s"

private const val SampleTrace = """java.lang.IllegalStateException: Tool 'calculate' returned no output
	at dev.ai.elements.core.agent.AgentLoop.runTool(AgentLoop.kt:88)
	at dev.ai.elements.core.backend.OpenAiChatBackend${'$'}stream${'$'}1.invokeSuspend(OpenAiChatBackend.kt:61)
	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:108)
	at kotlinx.coroutines.internal.LimitedDispatcher${'$'}Worker.run(LimitedDispatcher.kt:115)
	at kotlinx.coroutines.scheduling.TaskImpl.run(Tasks.kt:103)
	at kotlinx.coroutines.scheduling.CoroutineScheduler.runSafely(CoroutineScheduler.kt:584)
	at kotlinx.coroutines.scheduling.CoroutineScheduler${'$'}Worker.run(CoroutineScheduler.kt:793)
	at dev.ai.elements.demo.ChatViewModel${'$'}send${'$'}1.invokeSuspend(ChatViewModel.kt:72)"""

private val SampleTranscript = listOf(
    TranscriptSegment("Welcome to AI Elements for Compose.", 0, 2_500, "Assistant"),
    TranscriptSegment("This transcript follows the audio as it plays.", 2_500, 5_300, "Assistant"),
    TranscriptSegment("Tap any sentence to jump straight to it.", 5_300, 8_100, "Assistant"),
)

@OptIn(ExperimentalLayoutApi::class)
internal val NewGallerySamples: List<Pair<String, @Composable () -> Unit>> = listOf(
    "Persona" to {
        var state by remember { mutableStateOf(PersonaState.THINKING) }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Persona(state, size = 112.dp, level = { if (state == PersonaState.SPEAKING) ((System.nanoTime() / 90_000_000L) % 5) / 4f else 0.3f })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PersonaState.entries.forEach { s ->
                    FilterChip(selected = s == state, onClick = { state = s }, label = { Text(s.name.lowercase()) })
                }
            }
        }
    },
    "Speech input" to {
        val speech = rememberSpeechInputState()
        var heard by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SpeechInput(onTranscript = { text, _ -> heard = text }, state = speech)
            Text(
                heard.ifEmpty { speech.error ?: "Tap the mic and speak" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (heard.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
    },
    "Audio player + Transcription" to {
        val context = LocalContext.current
        val player = rememberAudioPlayerState("android.resource://${context.packageName}/raw/sample_speech")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AudioPlayer(player, title = "Generated speech")
            Transcription(SampleTranscript, currentTimeMs = if (player.positionMs > 0 || player.isPlaying) player.positionMs else -1, onSeek = { player.seekTo(it); player.play() })
        }
    },
    "Mic & voice selectors" to {
        val context = LocalContext.current
        var mic by remember { mutableStateOf<AudioDeviceInfo?>(null) }
        var voice by remember { mutableStateOf("aria") }
        val sample = "android.resource://${context.packageName}/raw/sample_speech"
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MicSelector(selected = mic, onSelect = { mic = it })
            VoiceSelector(
                voices = listOf(
                    VoiceOption("aria", "Aria", "Warm, conversational", "en-US", sample),
                    VoiceOption("sol", "Sol", "Bright, upbeat", "en-GB", sample),
                    VoiceOption("xiaoyu", "晓雨", "温和、清晰", "zh-CN", sample),
                    VoiceOption("haru", "Haru", "落ち着いた声", "ja-JP", sample),
                ),
                selectedId = voice,
                onSelect = { voice = it.id },
            )
        }
    },
    "Model selector" to {
        var model by remember { mutableStateOf("gpt-5.5") }
        ModelSelector(SampleModels, model, onSelect = { model = it.id })
    },
    "Question" to {
        var answer by remember { mutableStateOf<QuestionAnswer?>(null) }
        Question(
            prompt = "Which platforms should the release target?",
            description = "Pick all that apply — I'll set up the build for each.",
            options = listOf(
                QuestionOption("android", "Android", "Phones, tablets and foldables"),
                QuestionOption("wear", "Wear OS"),
                QuestionOption("tv", "Android TV"),
            ),
            multiple = true,
            answered = answer,
            onSubmit = { answer = it },
        )
    },
    "Agent" to {
        Agent(
            name = "Research assistant",
            model = "gpt-5.5",
            instructions = "Answer with sources. Use search_docs before answering product questions; never guess prices.",
            tools = listOf(
                AgentToolSpec("search_docs", "Search the product documentation", """{ "query": "string", "limit": "integer?" }"""),
                AgentToolSpec("get_current_time", "Current time in a timezone", """{ "timezone": "string" }"""),
            ),
            outputSchema = """{ "answer": "string", "sources": ["url"] }""",
        )
    },
    "Terminal" to {
        var output by remember { mutableStateOf(SampleTerminal) }
        Terminal(output, title = "./gradlew test", status = TerminalStatus.ERROR, exitCode = 1, onClear = { output = "" })
    },
    "Stack trace" to { StackTrace(SampleTrace) },
    "Test results" to {
        TestResults(
            durationMs = 6_240,
            suites = listOf(
                TestSuiteResult("ChatControllerTest", listOf(
                    TestCaseResult("queue_sendsInOrder", TestStatus.PASSED, 42),
                    TestCaseResult("regenerate_keepsVersions", TestStatus.PASSED, 31),
                    TestCaseResult("stop_keepsDeltas", TestStatus.PASSED, 12),
                )),
                TestSuiteResult("MarkdownTest", listOf(
                    TestCaseResult("table_rendersAlignment", TestStatus.FAILED, 88, "expected:<center> but was:<left>"),
                    TestCaseResult("math_inline", TestStatus.PASSED, 20),
                )),
                TestSuiteResult("LiveCodexTest", listOf(TestCaseResult("chatgptSubscription", TestStatus.SKIPPED))),
            ),
        )
    },
    "File tree" to {
        var selected by remember { mutableStateOf("ai-elements-ui/src/chat/Message.kt") }
        FileTree(
            nodes = listOf(
                FileNode("ai-elements-ui", "ai-elements-ui", listOf(
                    FileNode("src", "ai-elements-ui/src", listOf(
                        FileNode("chat", "ai-elements-ui/src/chat", listOf(
                            FileNode("Message.kt", "ai-elements-ui/src/chat/Message.kt", badge = "M"),
                            FileNode("Persona.kt", "ai-elements-ui/src/chat/Persona.kt", badge = "A"),
                        )),
                        FileNode("theme", "ai-elements-ui/src/theme", listOf(FileNode("AiPalette.kt", "ai-elements-ui/src/theme/AiPalette.kt", badge = "A"))),
                    )),
                    FileNode("build.gradle.kts", "ai-elements-ui/build.gradle.kts"),
                )),
                FileNode("README.md", "README.md", badge = "M"),
            ),
            expanded = setOf("ai-elements-ui", "ai-elements-ui/src", "ai-elements-ui/src/chat"),
            selectedPath = selected,
            onSelect = { if (!it.isFolder) selected = it.path },
        )
    },
    "Commit" to {
        Commit(
            hash = "4d2332057f0e9b1c2a3d4e5f60718293a4b5c6d7",
            message = "Add Persona and voice elements\n\nPersona morphs M3 Expressive shapes per state; SpeechInput wraps the platform recognizer.",
            author = "AI Elements Bot",
            timestampMs = System.currentTimeMillis() - 3_600_000,
            files = listOf(
                CommitFile("ai-elements-ui/src/chat/Persona.kt", FileChange.ADDED, 142),
                CommitFile("ai-elements-ui/src/voice/SpeechInput.kt", FileChange.ADDED, 171),
                CommitFile("demo/src/ui/GalleryScreen.kt", FileChange.MODIFIED, 12, 3),
            ),
        )
    },
    "Schema display" to {
        SchemaDisplay(
            method = "POST",
            path = "/api/chat",
            description = "Stream a reply as an AI SDK UI message stream",
            parameters = listOf(
                SchemaParameter("messages", "UIMessage[]", required = true, description = "Conversation so far", location = "body"),
                SchemaParameter("model", "string", description = "Override the server default", location = "body"),
                SchemaParameter("Authorization", "Bearer", location = "header"),
            ),
            requestBody = """{ "messages": [{ "role": "user", "parts": [{ "type": "text", "text": "Hi" }] }] }""",
            responseBody = """data: {"type":"text-delta","id":"t1","delta":"Hello"}""",
        )
    },
    "Package info" to {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PackageInfo("androidx.compose.material3:material3", fromVersion = "1.4.0", toVersion = "1.5.0-alpha29", change = PackageChange.MINOR, description = "Material 3 Expressive components")
            PackageInfo("com.squareup.okhttp3:okhttp", fromVersion = "4.12.0", toVersion = "5.1.0", change = PackageChange.MAJOR)
        }
    },
    "Environment variables" to {
        EnvironmentVariables(
            listOf(
                EnvironmentVariable("AGENT_BASE_URL", "http://localhost:11435/v1", secret = false),
                EnvironmentVariable("AGENT_MODEL", "qwen3:4b", secret = false),
                EnvironmentVariable("OPENAI_API_KEY", "sk-proj-demo-0000000000000000"),
            ),
        )
    },
    "Sandbox" to {
        Sandbox(
            title = "fibonacci.kt",
            subtitle = "Ran in 0.4 s",
            tabs = listOf(
                SandboxTab("Code") { CodeBlock("fun fib(n: Int): Long =\n    if (n < 2) n.toLong() else fib(n - 1) + fib(n - 2)\n\nprintln((0..10).map(::fib))", "kotlin") },
                SandboxTab("Console") { Terminal("[0, 1, 1, 2, 3, 5, 8, 13, 21, 34, 55]", title = "stdout", status = TerminalStatus.SUCCESS, exitCode = 0) },
            ),
        )
    },
    "Snippet" to {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Snippet("implementation(\"dev.ai.elements:ai-elements-ui:0.3.0\")")
            Snippet("./gradlew :demo:installDebug", prefix = "$")
        }
    },
)
