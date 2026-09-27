package dev.ai.elements.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.ai.elements.core.model.ToolKind
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.core.model.ToolState
import dev.ai.elements.core.model.DataPart
import dev.ai.elements.ui.chat.BranchSelector
import dev.ai.elements.ui.chat.Chat
import dev.ai.elements.ui.chat.rememberChat
import dev.ai.elements.ui.chat.DataPartView
import dev.ai.elements.ui.chat.Checkpoint
import dev.ai.elements.ui.chat.ModelOption
import dev.ai.elements.ui.chat.ModelSelector
import dev.ai.elements.ui.chat.Question
import dev.ai.elements.ui.chat.QuestionAnswer
import dev.ai.elements.ui.chat.QuestionOption
import dev.ai.elements.ui.chat.ToolCall
import dev.ai.elements.ui.chat.ToolPartView
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.TextPart
import dev.ai.elements.ui.code.EnvironmentVariable
import dev.ai.elements.ui.code.EnvironmentVariables
import dev.ai.elements.ui.theme.AiElementsTheme
import dev.ai.elements.ui.voice.TranscriptSegment
import dev.ai.elements.ui.voice.Transcription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Assert.assertTrue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import org.junit.Test
import org.junit.runner.RunWith

/** Interaction contracts of individual elements (what callers rely on). */
@RunWith(AndroidJUnit4::class)
class ElementsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun s(id: Int, vararg args: Any) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test fun chat_oneLine_sendsAndStreamsAReply() {
        lateinit var chat: dev.ai.elements.core.chat.ChatController
        compose.setContent {
            AiElementsTheme(dynamicColor = false) {
                chat = rememberChat { approver -> dev.ai.elements.core.provider.mock.MockAgentBackend(approver = approver, chunkDelayMs = 1) }
                Chat(chat)
            }
        }
        compose.onNodeWithTag("prompt-input").performClick().performTextInput("hello")
        compose.onNodeWithTag("send-button").performClick()
        compose.waitUntilExactlyOneExists(androidx.compose.ui.test.hasTestTag("regenerate"), 20_000)
        val messages = chat.state.value.messages
        assertEquals(listOf(Role.USER, Role.ASSISTANT), messages.map { it.role })
        assertEquals("hello", (messages.first().parts.single() as TextPart).text)
        assertTrue(messages.last().parts.isNotEmpty())
        compose.onNodeWithTag("prompt-input").assert(
            androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")),
        )
    }

    @Test fun workflowCanvas_customNodesToolbarAndPanel() {
        val nodes = listOf(
            dev.ai.elements.ui.workflow.CanvasNode("a", "Plan", data = "custom-a"),
            dev.ai.elements.ui.workflow.CanvasNode("b", "Act", position = androidx.compose.ui.unit.DpOffset(0.dp, 160.dp), size = androidx.compose.ui.unit.DpSize(240.dp, 80.dp)),
        )
        compose.setContent {
            AiElementsTheme(dynamicColor = false) {
                dev.ai.elements.ui.workflow.WorkflowCanvas(
                    nodes = nodes,
                    edges = listOf(dev.ai.elements.ui.workflow.CanvasEdge("a", "b", temporary = true)),
                    modifier = androidx.compose.ui.Modifier.size(360.dp, 480.dp),
                    nodeContent = { node -> androidx.compose.material3.Text("Custom ${node.data ?: node.title}") },
                    nodeToolbar = { node -> androidx.compose.material3.Text("Toolbar for ${node.id}") },
                    panel = { androidx.compose.material3.Text("Panel", androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.TopStart)) },
                )
            }
        }
        compose.onNodeWithText("Custom custom-a").assertExists()
        compose.onNodeWithText("Custom Act").assertExists()
        compose.onNodeWithText("Panel").assertExists()
        compose.onNodeWithTag("canvas-node-toolbar").assertDoesNotExist()
        compose.onNodeWithTag("canvas-node-a").performClick()
        compose.onNodeWithText("Toolbar for a").assertExists()
        compose.onNodeWithTag("canvas-node-a").performClick()
        compose.onNodeWithTag("canvas-node-toolbar").assertDoesNotExist()
    }

    @Test fun theme_acceptsABrandColorScheme() {
        val brand = androidx.compose.material3.lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF00897B))
        var primary: androidx.compose.ui.graphics.Color? = null
        compose.setContent {
            AiElementsTheme(dynamicColor = true, colorScheme = brand) { primary = androidx.compose.material3.MaterialTheme.colorScheme.primary }
        }
        compose.waitForIdle()
        assertEquals(androidx.compose.ui.graphics.Color(0xFF00897B), primary)
    }

    @Test fun renderers_overrideToolsAndDataAppWide() {
        compose.setContent {
            AiElementsTheme(dynamicColor = false) {
                androidx.compose.runtime.CompositionLocalProvider(
                    dev.ai.elements.ui.chat.LocalAiElementsRenderers provides dev.ai.elements.ui.chat.AiElementsRenderers(
                        tools = mapOf("get_weather" to dev.ai.elements.ui.chat.ToolRenderer { part, _ -> androidx.compose.material3.Text("Weather card: ${part.output}") }),
                        data = mapOf("chart" to dev.ai.elements.ui.chat.DataRenderer { androidx.compose.material3.Text("Custom chart") }),
                    ),
                ) {
                    androidx.compose.foundation.layout.Column {
                        ToolPartView(ToolPart("w", "get_weather", ToolState.OUTPUT_AVAILABLE, "{}", output = "21°C"))
                        DataPartView(DataPart("c", "chart", kotlinx.serialization.json.JsonObject(emptyMap())))
                        ToolPartView(ToolPart("n", "notes__save", ToolState.OUTPUT_AVAILABLE, "{}", output = "ok", title = "Save note", source = "Notes"))
                    }
                }
            }
        }
        compose.onNodeWithText("Weather card: 21°C").assertExists()
        compose.onNodeWithText("Custom chart").assertExists()
        compose.onNodeWithText("Notes", useUnmergedTree = true).assertExists()
    }

    @Test fun dataPart_agUiState_rendersPlanAndKeepsOtherKeysAsJson() {
        val state = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"plan":{"title":"Release","steps":[{"label":"Write notes","status":"complete"},{"label":"Tag","status":"active"}]},"cursor":3}""",
        )
        compose.setContent {
            AiElementsTheme(dynamicColor = false) { DataPartView(DataPart("s", DataPart.STATE, state)) }
        }
        compose.onNodeWithTag("data-plan").assertExists()
        compose.onNodeWithText("Write notes").assertExists()
        compose.onNodeWithText("data-state").assertExists()
        compose.onNodeWithText("Release").assertExists()
    }

    @Test fun question_multiSelect_submitsChosenIdsAndText() {
        var answer: QuestionAnswer? = null
        compose.setContent {
            AiElementsTheme(dynamicColor = false) {
                Question(
                    prompt = "Targets?",
                    options = listOf(QuestionOption("a", "Android"), QuestionOption("w", "Wear"), QuestionOption("t", "TV")),
                    multiple = true,
                    onSubmit = { answer = it },
                )
            }
        }
        compose.onNodeWithTag("question-submit").assertIsNotEnabled()
        compose.onNodeWithTag("question-option-a").performClick()
        compose.onNodeWithTag("question-option-t").performClick()
        compose.onNodeWithTag("question-other").performTextInput("iOS")
        compose.onNodeWithTag("question-submit").assertIsEnabled().performClick()
        assertEquals(QuestionAnswer(listOf("a", "t"), "iOS"), answer)
    }

    @Test fun question_singleSelect_replacesChoice() {
        var answer: QuestionAnswer? = null
        compose.setContent {
            AiElementsTheme(dynamicColor = false) {
                Question("Pick one", listOf(QuestionOption("x", "X"), QuestionOption("y", "Y")), onSubmit = { answer = it }, allowOther = false)
            }
        }
        compose.onNodeWithTag("question-option-x").performClick()
        compose.onNodeWithTag("question-option-y").performClick()
        compose.onNodeWithTag("question-submit").performClick()
        assertEquals(listOf("y"), answer?.selected)
        assertNull(answer?.text)
    }

    @Test fun toolCall_approvalButtonsReportTheDecision() {
        val decisions = mutableListOf<Boolean>()
        compose.setContent {
            AiElementsTheme(dynamicColor = false) {
                ToolCall(ToolPart("t1", "copy_to_clipboard", ToolState.APPROVAL_REQUESTED, """{"text":"hi"}"""), onApproval = { decisions += it })
            }
        }
        compose.onNodeWithTag("approve", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("deny", useUnmergedTree = true).performClick()
        assertEquals(listOf(true, false), decisions)
    }

    @Test fun checkpoint_restoresOnlyAfterConfirming() {
        var restored = 0
        compose.setContent { AiElementsTheme(dynamicColor = false) { Checkpoint(onRestore = { restored++ }) } }
        compose.onNodeWithTag("checkpoint-restore").performClick()
        assertEquals("a tap alone must not restore", 0, restored)
        compose.onNodeWithText(s(R.string.ai_cancel)).performClick()
        assertEquals(0, restored)
        compose.onNodeWithTag("checkpoint-restore").performClick()
        compose.onNodeWithTag("checkpoint-confirm").performClick()
        assertEquals(1, restored)
    }

    @Test fun branchSelector_stepsWithinBounds() {
        compose.setContent {
            var index by remember { mutableIntStateOf(0) }
            AiElementsTheme(dynamicColor = false) { BranchSelector(index, 3, onSelect = { index = it }) }
        }
        compose.onNodeWithTag("branch-label").assertExists().assertTextEquals("1 / 3")
        compose.onNodeWithContentDescription(s(R.string.ai_next_version)).performClick()
        compose.onNodeWithContentDescription(s(R.string.ai_next_version)).performClick()
        compose.onNodeWithTag("branch-label").assertTextEquals("3 / 3")
        compose.onNodeWithContentDescription(s(R.string.ai_next_version)).assertIsNotEnabled()
    }

    @Test fun environmentVariables_masksSecretsUntilRevealed() {
        compose.setContent {
            AiElementsTheme(dynamicColor = false) {
                EnvironmentVariables(listOf(EnvironmentVariable("API_KEY", "sk-secret-value"), EnvironmentVariable("MODE", "prod", secret = false)))
            }
        }
        compose.onNodeWithText("sk-secret-value").assertDoesNotExist()
        compose.onNodeWithText("prod").assertExists()
        compose.onNodeWithContentDescription(s(R.string.ai_show_value)).performClick()
        compose.onNodeWithText("sk-secret-value").assertExists()
        compose.onNodeWithContentDescription(s(R.string.ai_hide_value)).performClick()
        compose.onNodeWithText("sk-secret-value").assertDoesNotExist()
    }

    @Test fun modelSelector_filtersAndSelects() {
        var selected by mutableStateOf("a")
        compose.setContent {
            AiElementsTheme(dynamicColor = false) {
                ModelSelector(
                    models = listOf(ModelOption("a", "Alpha", "One"), ModelOption("b", "Beta", "Two"), ModelOption("g", "Gamma", "Two")),
                    selectedId = selected,
                    onSelect = { selected = it.id },
                )
            }
        }
        compose.onNodeWithTag("model-selector").performClick()
        compose.onNode(hasText(s(R.string.ai_search_models))).performTextInput("gam")
        compose.onNodeWithTag("model-option-b").assertDoesNotExist()
        compose.onNodeWithTag("model-option-g").performClick()
        compose.waitForIdle()
        assertEquals("g", selected)
        compose.onNodeWithTag("model-selector").assertExists()
        compose.onNode(hasText("Gamma")).assertExists()
    }

    @Test fun transcription_tapSeeksToTheSegmentStart() {
        val seeks = mutableListOf<Long>()
        compose.setContent {
            AiElementsTheme(dynamicColor = false) {
                Transcription(
                    listOf(TranscriptSegment("First sentence.", 0, 2_000), TranscriptSegment("Second sentence.", 2_000, 4_000)),
                    currentTimeMs = 500,
                    onSeek = { seeks += it },
                )
            }
        }
        compose.onNodeWithTag("transcription").assertExists()
        // The text is one paragraph with a link per segment: tap the second one.
        compose.onNode(hasText("Second sentence.", substring = true)).performTouchInputOnText("Second")
        assertEquals(listOf(2_000L), seeks)
    }

    @Test fun subagent_opensForNestedApproval_andAnswersWithTheNestedCallId() {
        val approvals = mutableListOf<Pair<String, Boolean>>()
        val nested = Message("sub", Role.ASSISTANT, listOf(ToolPart("inner-1", "notes__save_note", ToolState.APPROVAL_REQUESTED, "{}", title = "Save note", source = "Notes")))
        val call = ToolPart("call-1", "delegate_task", ToolState.INPUT_AVAILABLE, """{"agent_name":"researcher","task":"Save a note"}""", subagent = nested, kind = ToolKind.Delegation("researcher", "Save a note"))
        compose.setContent {
            AiElementsTheme(dynamicColor = false) { ToolPartView(call, onToolApproval = { id, ok -> approvals += id to ok }) }
        }
        compose.onNodeWithTag("subagent-researcher").assertExists()
        // Opened by itself: the task and the nested confirmation are visible.
        compose.onNodeWithText("Save a note").assertExists()
        compose.onNodeWithText(s(R.string.ai_approve)).performClick()
        assertEquals(listOf("inner-1" to true), approvals)
    }

    @Test fun subagent_collapsedSummaryShowsTheAnswer() {
        val nested = Message("sub", Role.ASSISTANT, listOf(TextPart("t", "**AG-UI** streams agent events.")))
        val call = ToolPart("call-2", "delegate_task", ToolState.OUTPUT_AVAILABLE, """{"agent_name":"researcher","task":"Explain"}""", output = "AG-UI streams agent events.", subagent = nested, kind = ToolKind.Delegation("researcher", "Explain"))
        compose.setContent { AiElementsTheme(dynamicColor = false) { ToolPartView(call) } }
        compose.onNodeWithTag("subagent-activity", useUnmergedTree = true).assertTextEquals("AG-UI streams agent events.")
    }
}

/** Taps the middle of the first occurrence of [substring] inside this Text node. */
private fun SemanticsNodeInteraction.performTouchInputOnText(substring: String) {
    val node = fetchSemanticsNode()
    val layouts = mutableListOf<TextLayoutResult>()
    node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
    val layout = layouts.first()
    val start = layout.layoutInput.text.text.indexOf(substring)
    require(start >= 0) { "'$substring' not found" }
    val box = layout.getBoundingBox(start + substring.length / 2)
    performTouchInput { click(Offset(box.center.x, box.center.y)) }
}
