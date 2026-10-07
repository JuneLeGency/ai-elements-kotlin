package dev.ai.elements.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.ai.elements.core.chat.ChatState
import dev.ai.elements.core.chat.InputRequest
import dev.ai.elements.core.chat.InputResponse
import dev.ai.elements.core.chat.ToolDecision
import dev.ai.elements.core.model.DataPart
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.TextPart
import dev.ai.elements.core.model.ToolKind
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.core.model.ToolState
import dev.ai.elements.ui.chat.AgentComputerScaffold
import dev.ai.elements.ui.chat.BranchSelector
import dev.ai.elements.ui.chat.Chat
import dev.ai.elements.ui.chat.Checkpoint
import dev.ai.elements.ui.chat.Conversation
import dev.ai.elements.ui.chat.DataPartView
import dev.ai.elements.ui.chat.InputRequestCard
import dev.ai.elements.ui.chat.ModelOption
import dev.ai.elements.ui.chat.ModelSelector
import dev.ai.elements.ui.chat.Question
import dev.ai.elements.ui.chat.QuestionAnswer
import dev.ai.elements.ui.chat.QuestionOption
import dev.ai.elements.ui.chat.RunReplay
import dev.ai.elements.ui.chat.ToolCall
import dev.ai.elements.ui.chat.ToolPartView
import dev.ai.elements.ui.chat.rememberAgentComputerState
import dev.ai.elements.ui.chat.rememberChat
import dev.ai.elements.ui.code.EnvironmentVariable
import dev.ai.elements.ui.code.EnvironmentVariables
import dev.ai.elements.core.model.SourcePart
import dev.ai.elements.ui.chat.Sources
import dev.ai.elements.ui.theme.AiElementsTheme
import dev.ai.elements.ui.voice.TranscriptSegment
import dev.ai.elements.ui.voice.Transcription
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
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
        // The text only: a real keyboard can leave composition annotations on the (empty) value.
        compose.onNodeWithTag("prompt-input").assert(
            androidx.compose.ui.test.SemanticsMatcher("prompt cleared") { it.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.EditableText) { null }?.text == "" },
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

    @Test fun customRenderer_receivesFullDecisionWithoutBooleanCallback() {
        var received: Pair<String, dev.ai.elements.core.chat.ToolDecision>? = null
        val decision = dev.ai.elements.core.chat.ToolDecision(
            approved = true,
            reason = "Reviewed",
            editedInput = kotlinx.serialization.json.Json.parseToJsonElement("""{"path":"safe.txt"}""") as kotlinx.serialization.json.JsonObject,
            remember = true,
        )
        val tool = ToolPart("write-1", "write_file", ToolState.APPROVAL_REQUESTED)
        compose.setContent {
            AiElementsTheme(dynamicColor = false) {
                androidx.compose.runtime.CompositionLocalProvider(
                    dev.ai.elements.ui.chat.LocalAiElementsRenderers provides dev.ai.elements.ui.chat.AiElementsRenderers(
                        tools = mapOf("write_file" to dev.ai.elements.ui.chat.ToolRenderer { part, respond ->
                            androidx.compose.material3.Button(onClick = { respond?.invoke(part.id, decision) }) {
                                androidx.compose.material3.Text("Approve edited input")
                            }
                        }),
                    ),
                ) {
                    Conversation(
                        ChatState(messages = listOf(Message("reply", Role.ASSISTANT, listOf(tool)))),
                        onToolDecision = { id, answer -> received = id to answer },
                    )
                }
            }
        }
        compose.onNodeWithText("Approve edited input").performClick()
        compose.runOnIdle { assertEquals("write-1" to decision, received) }
    }

    @Test fun standaloneToolCall_acceptsFullDecisionCallback() {
        var received: dev.ai.elements.core.chat.ToolDecision? = null
        compose.setContent {
            AiElementsTheme(dynamicColor = false) {
                dev.ai.elements.ui.chat.ToolCall(
                    ToolPart("write-2", "write_file", ToolState.APPROVAL_REQUESTED, "{}"),
                    onDecision = { received = it },
                )
            }
        }
        compose.onNodeWithText("Approve", substring = false).performClick()
        compose.runOnIdle { assertEquals(true, received?.approved) }
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

    @Test fun inputRequest_schemaForm_validatesAndSubmitsTypedJson() {
        val schema = Json.parseToJsonElement(
            """{"type":"object","required":["party_size","time"],"properties":{
            "party_size":{"type":"integer","minimum":1,"maximum":12,"title":"Party Size"},
            "time":{"type":"string","title":"Time"},
            "seating":{"type":"string","enum":["indoor","outdoor"],"default":"indoor","title":"Seating"},
            "remind_me":{"type":"boolean","default":true,"title":"Remind Me"}}}""",
        ).jsonObject
        val answers = mutableListOf<InputResponse>()
        compose.setContent {
            AiElementsTheme(dynamicColor = false) {
                InputRequestCard(InputRequest("q1", "Booking a table at Sora", schema, source = "Notes"), onRespond = { answers += it })
            }
        }
        compose.onNodeWithText("Booking a table at Sora").assertExists()
        compose.onNodeWithTag("input-submit").assertIsNotEnabled() // required fields are empty
        compose.onNodeWithTag("input-field-party_size").performTextInput("20")
        compose.onNodeWithTag("input-field-time").performTextInput("19:30")
        compose.onNodeWithTag("input-submit").assertIsNotEnabled() // above the maximum
        compose.onNodeWithTag("input-field-party_size").performTextReplacement("4")
        compose.onNodeWithTag("input-option-seating-outdoor").performClick()
        compose.onNodeWithTag("input-field-remind_me").performClick()
        compose.onNodeWithTag("input-submit").assertIsEnabled().performClick()
        compose.onNodeWithTag("input-decline").performClick()
        val expected = Json.parseToJsonElement("""{"party_size":4,"time":"19:30","seating":"outdoor","remind_me":false}""").jsonObject
        assertEquals(listOf(InputResponse.Accept(expected), InputResponse.Decline), answers)
    }

    /** A Pydantic AI Harness `ask_user_question` as its standard JSON Schema: options with meanings, and the user's own answer. */
    @Test fun inputRequest_questions_optionsDescriptionsAndOwnAnswer() {
        val schema = Json.parseToJsonElement(
            """{"type":"object","required":["Database","Features"],"properties":{
            "Database":{"title":"Database","description":"Which database?","type":"string","anyOf":[
                {"const":"SQLite","title":"SQLite","description":"Local file, no server"},{"const":"Postgres","title":"Postgres"},{"type":"string"}]},
            "Features":{"title":"Features","description":"What to include?","type":"array","minItems":1,"items":{"anyOf":[
                {"const":"Sign-in","title":"Sign-in"},{"const":"Search","title":"Search"},{"type":"string"}]}}}}""",
        ).jsonObject
        val answers = mutableListOf<InputResponse>()
        compose.setContent {
            AiElementsTheme(dynamicColor = false) {
                InputRequestCard(InputRequest("q1", "Database · Features", schema), onRespond = { answers += it })
            }
        }
        compose.onNodeWithText("Local file, no server").assertExists()             // an option's meaning
        compose.onNodeWithTag("input-submit").assertIsNotEnabled()
        compose.onNodeWithTag("input-option-Database-SQLite").performClick()
        compose.onNodeWithTag("input-other-Database").performTextInput("DuckDB")   // the user's own answer replaces it
        compose.onNodeWithTag("input-option-Features-Search").performClick()
        compose.onNodeWithTag("input-option-Features-Sign-in").performClick()
        compose.onNodeWithTag("input-submit").assertIsEnabled().performClick()
        val expected = Json.parseToJsonElement("""{"Database":"DuckDB","Features":["Sign-in","Search"]}""").jsonObject
        assertEquals(listOf(InputResponse.Accept(expected)), answers)
    }

    @Test fun confirmation_deniesWithAReason_orApprovesEditedArguments() {
        val decisions = mutableListOf<Pair<String, ToolDecision>>()
        val part = ToolPart("t1", "save_note", ToolState.APPROVAL_REQUESTED, """{"title":"Milk"}""")
        compose.setContent {
            AiElementsTheme(dynamicColor = false) {
                Conversation(
                    ChatState(messages = listOf(Message("a1", Role.ASSISTANT, listOf(part)))),
                    onToolApproval = { _, _ -> },
                    onToolDecision = { id, decision -> decisions += id to decision },
                )
            }
        }
        compose.onNodeWithTag("approval-more").performClick()
        compose.onNodeWithTag("deny-with-reason", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("deny-reason").performTextInput("Not that note")
        compose.onNodeWithTag("deny-with-reason-send", useUnmergedTree = true).performClick()
        compose.onNodeWithText(s(R.string.ai_cancel)).performClick()
        compose.onNodeWithTag("approval-more").performClick()
        compose.onNodeWithTag("edit-and-approve", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("edit-arguments").performTextReplacement("not json")
        compose.onNodeWithTag("edit-and-approve-send", useUnmergedTree = true).assertIsNotEnabled()
        compose.onNodeWithTag("edit-arguments").performTextReplacement("""{"title":"Groceries"}""")
        compose.onNodeWithTag("edit-and-approve-send", useUnmergedTree = true).performClick()
        assertEquals(
            listOf(
                "t1" to ToolDecision(false, reason = "Not that note"),
                "t1" to ToolDecision(true, editedInput = Json.parseToJsonElement("""{"title":"Groceries"}""").jsonObject),
            ),
            decisions,
        )
    }

    /** Landscape with the keyboard up leaves the composer very little height: one row, nothing squashed. */
    @Test fun promptInput_inAShortSpace_isOneRowWithAFullSizeSendButton() {
        compose.setContent {
            AiElementsTheme(dynamicColor = false) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.height(64.dp)) {
                    dev.ai.elements.ui.chat.PromptInput("hi", {}, {}, {}, false, onAddAttachment = {})
                }
            }
        }
        compose.onNodeWithTag("prompt-input").assertExists()
        compose.onNodeWithTag("add-attachment").assertDoesNotExist() // the toolbar waits for more room
        val send = compose.onNodeWithTag("send-button").fetchSemanticsNode().size.height / compose.density.density
        assertTrue("send button squashed to $send dp", send >= 40f)
    }

    /** An empty composer offers voice mode where send would be; typing brings send back. */
    @Test fun promptInput_emptyOffersVoiceMode() {
        var voice = 0
        compose.setContent {
            AiElementsTheme(dynamicColor = false) {
                var text by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
                dev.ai.elements.ui.chat.PromptInput(text, { text = it }, {}, {}, false, onVoiceMode = { voice++ })
            }
        }
        compose.onNodeWithTag("voice-mode-button").performClick()
        assertEquals(1, voice)
        compose.onNodeWithTag("send-button").assertDoesNotExist()
        compose.onNodeWithTag("prompt-input").performTextInput("hi")
        compose.onNodeWithTag("send-button").assertExists()
        compose.onNodeWithTag("voice-mode-button").assertDoesNotExist()
    }

    @Test fun confirmation_alwaysAllow_remembersForTheConversation() {
        val decisions = mutableListOf<ToolDecision>()
        val part = ToolPart("t1", "save_note", ToolState.APPROVAL_REQUESTED, "{}")
        compose.setContent {
            AiElementsTheme(dynamicColor = false) {
                Conversation(
                    ChatState(messages = listOf(Message("a1", Role.ASSISTANT, listOf(part)))),
                    onToolApproval = { _, _ -> },
                    onToolDecision = { _, decision -> decisions += decision },
                )
            }
        }
        compose.onNodeWithTag("approval-more").performClick()
        compose.onNodeWithTag("approve-always", useUnmergedTree = true).performClick()
        assertEquals(listOf(ToolDecision(true, remember = true)), decisions)
    }

    /** Camera photos store their rotation in EXIF: they must show upright, and open full screen. */
    @Test fun image_exifRotation_isUpright_andOpensFullScreen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File(context.cacheDir, "exif.jpg")
        android.graphics.Bitmap.createBitmap(200, 100, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
            .compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, file.outputStream())
        android.media.ExifInterface(file.path).apply {
            setAttribute(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val bytes = file.readBytes()
        val bitmap = dev.ai.elements.ui.chat.decode(bytes, 1600)!!
        assertEquals(100 to 200, bitmap.width to bitmap.height)

        val part = dev.ai.elements.core.model.FilePart("f1", "image/jpeg", "data:image/jpeg;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
        compose.setContent { AiElementsTheme(dynamicColor = false) { dev.ai.elements.ui.chat.FileAttachment(part) } }
        compose.onNodeWithTag("file-image").performClick()
        compose.onNodeWithTag("image-viewer").assertExists()
        compose.onNodeWithTag("image-rotate").performClick()
        compose.onNodeWithTag("image-close").performClick()
        compose.onNodeWithTag("image-viewer").assertDoesNotExist()
    }

    /** A video shows its first frame at the recording's upright aspect, and plays full screen. */
    @Test fun video_showsPosterUpright_andPlaysFullScreen() {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("portrait.mp4").readBytes()  // 320x180, rotate 90
        val part = dev.ai.elements.core.model.FilePart("v1", "video/mp4", "data:video/mp4;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
        compose.setContent { AiElementsTheme(dynamicColor = false) { dev.ai.elements.ui.chat.FileAttachment(part) } }
        compose.waitUntil(10_000) {
            val size = compose.onNodeWithTag("file-video").fetchSemanticsNode().size
            size.height > size.width   // the poster loaded and the portrait ratio applied
        }
        compose.onNodeWithTag("file-video").performClick()
        compose.onNodeWithTag("video-player").assertExists()
        compose.onNodeWithTag("video-close").performClick()
        compose.onNodeWithTag("video-player").assertDoesNotExist()
    }

    /** A PDF previews its first page and page count, and reads page by page; other documents open in an app. */
    @Test fun documents_pdfPreviewAndViewer_officeOpensWith() {
        val pdf = android.graphics.pdf.PdfDocument()
        repeat(2) { i ->
            val page = pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(300, 400, i + 1).create())
            page.canvas.drawText("Page ${i + 1}", 40f, 60f, android.graphics.Paint().apply { textSize = 24f })
            pdf.finishPage(page)
        }
        val bytes = java.io.ByteArrayOutputStream().also { pdf.writeTo(it); pdf.close() }.toByteArray()
        val report = dev.ai.elements.core.model.FilePart("d1", "application/pdf", "data:application/pdf;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP), filename = "report.pdf")
        val word = dev.ai.elements.core.model.FilePart("d2", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "data:application/octet-stream;base64,AAAA", filename = "plan.docx")
        compose.setContent {
            AiElementsTheme(dynamicColor = false) {
                androidx.compose.foundation.layout.Column {
                    dev.ai.elements.ui.chat.FileAttachment(report)
                    dev.ai.elements.ui.chat.FileAttachment(word)
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("document-preview", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("PDF · 2", substring = true).assertExists()
        compose.onNodeWithText("Word", substring = true).assertExists()
        compose.onAllNodesWithTag("file-document")[0].performClick()
        compose.onNodeWithTag("pdf-viewer").assertExists()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("pdf-page-0").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("pdf-pages").performScrollToIndex(1)
        compose.onNodeWithTag("pdf-page-1").assertExists()
        compose.onNodeWithTag("pdf-close").performClick()
        compose.onNodeWithTag("pdf-viewer").assertDoesNotExist()
    }

    /** Screenshots follow their tool call as a compact strip, and open the run playback at that step. */
    @Test fun runPlayback_stepsWithScreenshots() {
        val png = android.util.Base64.encodeToString(
            java.io.ByteArrayOutputStream().also { android.graphics.Bitmap.createBitmap(60, 40, android.graphics.Bitmap.Config.ARGB_8888).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray(),
            android.util.Base64.NO_WRAP,
        )
        fun shot(id: String) = dev.ai.elements.core.model.FilePart(id, "image/png", "data:image/png;base64,$png")
        val message = Message(
            "a1", Role.ASSISTANT,
            listOf(
                ToolPart("t1", "navigate", ToolState.OUTPUT_AVAILABLE, """{"url":"https://example.com"}""", output = "Opened"),
                shot("f1"),
                ToolPart("t2", "click", ToolState.OUTPUT_AVAILABLE, """{"selector":"#signup"}""", output = "Clicked"),
                shot("f2"),
                TextPart("x", "Done."),
            ),
        )
        compose.setContent { AiElementsTheme(dynamicColor = false) { Conversation(ChatState(messages = listOf(message))) } }
        assertEquals(2, compose.onAllNodesWithTag("step-media").fetchSemanticsNodes().size)
        compose.onAllNodesWithTag("step-media")[0].onChildren()[0].performClick()
        compose.onNodeWithTag("agent-computer").assertExists()
        compose.onNodeWithTag("run-step-counter").assertTextContains("1", substring = true)
        compose.onNodeWithTag("run-next").performClick()
        compose.onNodeWithTag("run-step-counter").assertTextContains("2", substring = true)
        // A screenshot opens full screen to zoom (desktop pages are small on a phone).
        compose.onNodeWithTag("run-shot").performClick()
        compose.onNodeWithTag("image-viewer").assertExists()
        compose.onNodeWithTag("image-close").performClick()
        compose.onNodeWithTag("run-playback-close").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("agent-computer").fetchSemanticsNodes().isEmpty() }
    }

    private val codingRun = listOf(
        ToolPart("c1", "run_command", ToolState.OUTPUT_AVAILABLE, """{"command":"ls"}""", output = "README.md", title = "ls", category = dev.ai.elements.core.model.ToolCategory.EXECUTE),
        ToolPart(
            "c2", "edit_file", ToolState.OUTPUT_AVAILABLE, """{"path":"README.md"}""",
            output = "--- a/README.md\n+++ b/README.md\n@@ -1 +1 @@\n-Old\n+New",
            category = dev.ai.elements.core.model.ToolCategory.EDIT, location = "README.md",
        ),
        ToolPart("c3", "navigate", ToolState.INPUT_AVAILABLE, """{"url":"https://example.com"}""", category = dev.ai.elements.core.model.ToolCategory.FETCH, location = "https://example.com"),
    )

    /** The live card opens the computer; each step shows by what it did; "back to live" follows again. */
    @Test fun agentComputer_liveCard_categoryViews_backToLive() {
        val message = Message("a1", Role.ASSISTANT, codingRun)
        compose.setContent { AiElementsTheme(dynamicColor = false) { Conversation(ChatState(messages = listOf(message))) } }
        compose.onNodeWithTag("agent-computer-card").assertTextContains(s(R.string.ai_live), substring = true).performClick()
        // Following the live run: the newest step, a page being fetched.
        compose.onNodeWithTag("run-step-counter").assertTextContains("3", substring = true)
        compose.onNodeWithTag("run-address").assertTextEquals("https://example.com")
        compose.onNodeWithTag("run-live").assertDoesNotExist()
        compose.onNodeWithTag("run-previous").performClick()
        compose.onNodeWithTag("run-diff").assertExists()
        compose.onNodeWithTag("run-previous").performClick()
        compose.onNodeWithTag("terminal").assertExists()
        compose.onNodeWithTag("run-live").performClick()
        compose.onNodeWithTag("run-step-counter").assertTextContains("3", substring = true)
    }

    /** Wide enough, the computer is a side pane next to the conversation; narrow, a bottom sheet over it. */
    @Test fun agentComputer_sidePaneWhenWide() {
        val message = Message("a1", Role.ASSISTANT, codingRun.map { it.copy(state = ToolState.OUTPUT_AVAILABLE) })
        compose.setContent {
            AiElementsTheme(dynamicColor = false) {
                androidx.compose.foundation.layout.Box(Modifier.requiredWidth(900.dp).fillMaxHeight()) { Conversation(ChatState(messages = listOf(message))) }
            }
        }
        compose.onNodeWithTag("agent-computer-card").performClick()
        compose.onNodeWithTag("agent-computer").assertIsDisplayed()
        compose.onNodeWithTag("conversation").assertIsDisplayed()
        val pane = compose.onNodeWithTag("agent-computer").fetchSemanticsNode().boundsInRoot
        val list = compose.onNodeWithTag("conversation").fetchSemanticsNode().boundsInRoot
        assertTrue("the pane sits beside the conversation", pane.left >= list.right)
    }

    /** Hundreds of steps, a very long output and long names: everything stays usable (phone width). */
    @Test fun agentComputer_longRunsAndLongContent() {
        val long = "x".repeat(400)
        val log = (1..20_000).joinToString("\n") { "\u001b[32mline $it\u001b[0m" }
        val steps = (0 until 300).map { i ->
            ToolPart(
                "s$i", "run_command", ToolState.OUTPUT_AVAILABLE, """{"command":"step $i"}""",
                output = if (i == 299) log else "ok $i", title = "step $i $long",
                category = dev.ai.elements.core.model.ToolCategory.EXECUTE, location = "/very/$long/path",
            )
        }
        val message = Message("a1", Role.ASSISTANT, steps)
        compose.setContent {
            AiElementsTheme(dynamicColor = false) {
                androidx.compose.foundation.layout.Box(Modifier.requiredWidth(360.dp).fillMaxHeight()) { Conversation(ChatState(messages = listOf(message))) }
            }
        }
        compose.onNodeWithTag("agent-computer-card").assertIsDisplayed().performClick()
        compose.onNodeWithTag("run-step-counter").assertTextContains("300", substring = true)
        // The last step's 20 000 lines keep the terminal's scrollback, with a note.
        compose.onNodeWithTag("terminal").assertExists()
        compose.onNodeWithText(s(R.string.ai_earlier_lines, 19_001), substring = true).assertExists()
        compose.onNodeWithTag("run-steps").performScrollToIndex(0)
        compose.onNodeWithTag("run-step-0").performClick()
        compose.onNodeWithTag("run-step-counter").assertTextContains("1", substring = true)
        compose.onNodeWithTag("run-next").assertIsDisplayed()
    }

    /** A recorded run replays event by event into the computer, then returns to the stored reply. */
    @Test fun agentComputer_replaysARecordedRun() {
        val message = Message("a1", Role.ASSISTANT, codingRun.take(2))
        val events = listOf(
            dev.ai.elements.core.chat.ChatEvent.ToolInputAvailable("c1", "run_command", """{"command":"ls"}""", title = "ls", category = dev.ai.elements.core.model.ToolCategory.EXECUTE),
            dev.ai.elements.core.chat.ChatEvent.ToolOutput("c1", "README.md"),
            dev.ai.elements.core.chat.ChatEvent.Finish,
        )
        val replay = RunReplay { kotlinx.coroutines.flow.flow { events.forEach { kotlinx.coroutines.delay(300); emit(it) } } }
        compose.setContent {
            AiElementsTheme(dynamicColor = false) {
                val computer = rememberAgentComputerState(replay)
                AgentComputerScaffold(computer, listOf(message)) { Conversation(ChatState(messages = listOf(message))) }
            }
        }
        compose.onNodeWithTag("agent-computer-card").performClick()
        compose.onNodeWithTag("run-step-counter").assertTextContains("2", substring = true)
        compose.onNodeWithTag("run-replay").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("terminal").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("run-step-counter").assertTextContains(s(R.string.ai_replaying), substring = true)
        compose.onNodeWithTag("run-exit-replay").performClick()
        compose.onNodeWithTag("run-step-counter").assertTextContains("2", substring = true)
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

    /** Links in content open in a Custom Tab (the browser inside the app), not by leaving the app. */
    @Test
    fun sourceLink_opensInACustomTab() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var launched: android.content.Intent? = null
        // Intercepts the launch (a non-null result blocks it), so no browser opens during the test.
        val monitor = object : android.app.Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: android.content.Intent): android.app.Instrumentation.ActivityResult? {
                if (intent.action != android.content.Intent.ACTION_VIEW) return null
                launched = intent
                return android.app.Instrumentation.ActivityResult(0, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            compose.setContent {
                AiElementsTheme(dynamicColor = false) { Sources(listOf(SourcePart("s1", "https://example.com/doc", "Example doc"))) }
            }
            compose.onNodeWithText("Example doc").performClick()
            compose.waitUntil(5_000) { launched != null }
            assertEquals("https://example.com/doc", launched?.dataString)
            assertTrue("not a Custom Tabs intent", launched?.hasExtra(androidx.browser.customtabs.CustomTabsIntent.EXTRA_SESSION) == true)
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    /** Opening another conversation shows its end in the very first frame, without scrolling to it. */
    @Test
    fun openingAConversation_startsAtItsEnd() {
        fun chat(name: String, turns: Int) = ChatState(
            messages = (0 until turns).flatMap { i ->
                listOf(
                    Message("$name-u$i", Role.USER, listOf(TextPart("t", "$name question $i"))),
                    Message("$name-a$i", Role.ASSISTANT, listOf(TextPart("t", "$name answer $i\n\nA paragraph long enough to take a few lines on a phone screen, so that the conversation is taller than the viewport."))),
                )
            },
        )
        var state by mutableStateOf(chat("short", 2))
        compose.setContent { AiElementsTheme(dynamicColor = false) { Conversation(state) } }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        state = chat("long", 30)
        compose.mainClock.advanceTimeByFrame()
        // The first frame after the switch: the last reply is laid out at the bottom, the first is not composed.
        compose.onNodeWithText("long answer 29", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("long question 0").fetchSemanticsNodes().let { assertTrue("the start is composed: ${it.size}", it.isEmpty()) }
        compose.mainClock.autoAdvance = true
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
