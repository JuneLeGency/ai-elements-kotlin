package dev.ai.elements.demo.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.ai.elements.core.chat.ChatState
import dev.ai.elements.core.chat.InputRequest
import dev.ai.elements.core.chat.InputResponse
import dev.ai.elements.core.model.FilePart
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.Suggestion
import dev.ai.elements.core.model.TextPart
import dev.ai.elements.core.model.ToolCategory
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.core.model.ToolState
import dev.ai.elements.core.provider.mock.MockAgentBackend
import dev.ai.elements.genui.a2ui.A2uiState
import dev.ai.elements.genui.a2ui.A2uiSurfaceView
import dev.ai.elements.genui.jsx.JsxPreview
import dev.ai.elements.ui.chat.AgentComputerPanel
import dev.ai.elements.ui.chat.AgentStep
import dev.ai.elements.ui.chat.AttachmentStrip
import dev.ai.elements.ui.chat.ChatEmptyState
import dev.ai.elements.ui.chat.Conversation
import dev.ai.elements.ui.chat.DocumentAttachment
import dev.ai.elements.ui.chat.FileAttachment
import dev.ai.elements.ui.chat.InputRequestCard
import dev.ai.elements.ui.chat.ShimmerText
import dev.ai.elements.ui.chat.StepView
import dev.ai.elements.ui.chat.VideoAttachment
import dev.ai.elements.ui.chat.rememberAgentComputerState
import dev.ai.elements.ui.chat.rememberChat
import dev.ai.elements.ui.markdown.CodeBlock
import dev.ai.elements.ui.voice.VoiceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

// --- Agent's computer ----------------------------------------------------------------------------

private const val ComputerTerminal = "Downloading ━━━━━━━━━━━━━━━━━━━━ 100%\n" +
    "\u001B[32mChatControllerTest PASSED\u001B[0m\n\u001B[31mMarkdownTest FAILED\u001B[0m\n\u001B[1mBUILD FAILED\u001B[0m in 6s"

private const val ComputerDiff = "--- README.md\n+++ README.md\n@@ -3 +3 @@\n-Prices: Free, Pro.\n+Prices: Free, Pro, Team."

private fun computerRun(page: String) = Message(
    "g-computer", Role.ASSISTANT,
    listOf(
        TextPart("g-c0", "I'll check the pricing page, run the tests and update the README."),
        ToolPart("g-c1", "navigate", ToolState.OUTPUT_AVAILABLE, """{"url":"https://example.com/pricing"}""", output = "Pricing — Free, Pro, Team", category = ToolCategory.FETCH, location = "https://example.com/pricing"),
        FilePart("g-c1-shot", "image/png", page),
        ToolPart("g-c2", "run_command", ToolState.OUTPUT_ERROR, """{"command":"./gradlew test"}""", errorText = ComputerTerminal, title = "./gradlew test", category = ToolCategory.EXECUTE),
        ToolPart("g-c3", "edit_file", ToolState.OUTPUT_AVAILABLE, """{"path":"README.md"}""", output = ComputerDiff, category = ToolCategory.EDIT, location = "README.md"),
    ),
)

/** A browser page as the agent's screenshot would show it (drawn, so the demo works offline). */
private fun pageScreenshot(): String {
    val w = 640; val h = 360
    val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    canvas.drawColor(0xFFF7F7FA.toInt())
    paint.color = 0xFF1D1B20.toInt(); paint.textSize = 30f; canvas.drawText("Pricing", 40f, 70f, paint)
    listOf("Free" to "$0", "Pro" to "$20", "Team" to "$50").forEachIndexed { i, (plan, price) ->
        val left = 40f + i * 195f
        paint.color = 0xFFFFFFFF.toInt(); canvas.drawRoundRect(left, 100f, left + 175f, 300f, 16f, 16f, paint)
        paint.color = 0xFF6750A4.toInt(); paint.textSize = 24f; canvas.drawText(plan, left + 20f, 145f, paint)
        paint.color = 0xFF1D1B20.toInt(); paint.textSize = 36f; canvas.drawText(price, left + 20f, 200f, paint)
        paint.color = 0xFF6750A4.toInt(); canvas.drawRoundRect(left + 20f, 240f, left + 155f, 280f, 20f, 20f, paint)
    }
    val out = java.io.ByteArrayOutputStream()
    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
    return "data:image/png;base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
}

// --- Media ----------------------------------------------------------------------------------------

/** A one-page PDF drawn with the platform PdfDocument, as an agent's generated report would arrive. */
private fun samplePdf(): String {
    val document = android.graphics.pdf.PdfDocument()
    val page = document.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create())
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    page.canvas.apply {
        paint.textSize = 28f; paint.isFakeBoldText = true; drawText("Quarterly report", 56f, 90f, paint)
        paint.textSize = 14f; paint.isFakeBoldText = false
        listOf("Revenue grew 18% quarter over quarter.", "Active users: 12,480 (+9%).", "Churn fell to 2.1%.").forEachIndexed { i, line ->
            drawText("• $line", 56f, 140f + i * 26f, paint)
        }
        paint.color = 0xFF6750A4.toInt()
        listOf(80f, 120f, 160f, 210f).forEachIndexed { i, bar -> drawRect(80f + i * 90f, 460f - bar, 140f + i * 90f, 460f, paint) }
    }
    document.finishPage(page)
    val out = java.io.ByteArrayOutputStream()
    document.writeTo(out)
    document.close()
    return "data:application/pdf;base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
}

private fun rawUrl(context: Context, name: String) = "android.resource://${context.packageName}/raw/$name"

// --- Generative UI ----------------------------------------------------------------------------------

/** JSX samples: what a model writes in a ```jsx fence, with the bindings its data model starts from. */
internal val JsxSamples = listOf(
    Triple(
        "jsx-typography", "Text and layout",
        """
        <Card>
          <h2>Release 0.3</h2>
          <p>AI Elements now shows the <b>agent's computer</b> and replays runs.</p>
          <ul>
            <li>Terminal colours and progress bars</li>
            <li>Desktop browser screenshots</li>
          </ul>
          <hr />
          <small>Rendered natively from JSX — no WebView.</small>
        </Card>
        """.trimIndent() to "{}",
    ),
    Triple(
        "jsx-form", "Form with bindings and an action",
        """
        <Card>
          <h3>Stay in the loop</h3>
          <input name="email" placeholder="Email address" />
          <input type="checkbox" label="Weekly digest" checked={digest} />
          <div className="flex justify-end">
            <Button variant="primary" onClick={subscribe}>Subscribe</Button>
          </div>
        </Card>
        """.trimIndent() to """{"email":"","digest":true}""",
    ),
    Triple(
        "jsx-controls", "Choices, slider, date and tabs",
        """
        <Column>
          <select name="plan" label="Plan"><option value="free">Free</option><option value="pro">Pro</option><option value="team">Team</option></select>
          <Slider label="Seats" min={1} max={50} value={seats} />
          <DateTimeInput label="Start date" value={start} enableDate={true} />
          <Tabs>
            <Tab title="Overview"><p>Everything in Pro, for your whole team.</p></Tab>
            <Tab title="Billing"><p>Billed monthly per seat.</p></Tab>
          </Tabs>
        </Column>
        """.trimIndent() to """{"plan":"pro","seats":5,"start":"2026-10-01"}""",
    ),
    Triple(
        "jsx-cards", "A row of cards from data",
        """
        <Column>
          <h3>Weather in {city}</h3>
          <div className="flex gap-2">
            <Card><p><b>Mon</b></p><p>{mon}°C</p></Card>
            <Card><p><b>Tue</b></p><p>{tue}°C</p></Card>
            <Card><p><b>Wed</b></p><p>{wed}°C</p></Card>
          </div>
          <a href="https://example.com/forecast">Full forecast</a>
        </Column>
        """.trimIndent() to """{"city":"Kyoto","mon":21,"tue":19,"wed":23}""",
    ),
)

/** The JSX of a sample, streamed in as a model writes it (restarting), to show streaming rendering. */
@Composable
private fun StreamingJsx(source: String) {
    var shown by remember { mutableStateOf("") }
    LaunchedEffect(source) {
        while (true) {
            for (end in 0..source.length step 6) { shown = source.take(end); kotlinx.coroutines.delay(40) }
            shown = source
            kotlinx.coroutines.delay(2_500)
        }
    }
    JsxPreview(shown)
}

@Composable
private fun JsxSample(source: String, bindings: String, streaming: Boolean = false) {
    var showCode by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row2(showCode) { showCode = it }
        if (showCode) CodeBlock(source, "jsx")
        else if (streaming) StreamingJsx(source)
        else JsxPreview(source, bindings = Json.parseToJsonElement(bindings).jsonObject, onAction = { action = "${it.name} ${it.context}" })
        action?.let { Text("Action → $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun Row2(showCode: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.FilterChip(selected = !showCode, onClick = { onChange(false) }, label = { Text("Preview") })
        androidx.compose.material3.FilterChip(selected = showCode, onClick = { onChange(true) }, label = { Text("JSX") })
    }
}

/** The reference server's hotel booking form (A2UI v1.0 messages, as `a2ui_demo.booking_form` emits them). */
private val BookingForm = """
[{"version":"v1.0","createSurface":{"surfaceId":"booking-kyoto","catalogId":"https://a2ui.org/specification/v1_0/catalogs/basic/catalog.json","sendDataModel":true}},
 {"version":"v1.0","updateComponents":{"surfaceId":"booking-kyoto","components":[
  {"id":"root","component":"Card","child":"form"},
  {"id":"form","component":"Column","children":["title","subtitle","guest","date","room","actions"]},
  {"id":"title","component":"Text","text":{"call":"formatString","args":{"value":"## Stay in ${'$'}{/city}"}}},
  {"id":"subtitle","component":"Text","text":"Hotel Lumen · from ${'$'}180 / night","variant":"caption"},
  {"id":"guest","component":"TextField","label":"Guest name","value":{"path":"/booking/guest"},"checks":[{"condition":{"call":"required","args":{"value":{"path":"/booking/guest"}}},"message":"Enter the guest's name"}]},
  {"id":"date","component":"DateTimeInput","label":"Check-in","value":{"path":"/booking/date"},"enableDate":true},
  {"id":"room","component":"ChoicePicker","label":"Room","variant":"mutuallyExclusive","displayStyle":"chips","options":[{"label":"Standard","value":"standard"},{"label":"Deluxe","value":"deluxe"},{"label":"Suite","value":"suite"}],"value":{"path":"/booking/room"}},
  {"id":"actions","component":"Row","children":["book"],"justify":"end"},
  {"id":"book_label","component":"Text","text":"Book"},
  {"id":"book","component":"Button","child":"book_label","variant":"primary","action":{"event":{"name":"book_hotel","context":{"city":{"path":"/city"},"guest":{"path":"/booking/guest"}}}}}]}},
 {"version":"v1.0","updateDataModel":{"surfaceId":"booking-kyoto","path":"/","value":{"city":"Kyoto","booking":{"guest":"","date":"2026-10-01","room":"deluxe"}}}}]
"""

// --- Samples ----------------------------------------------------------------------------------------

internal val ExtraSamples: Map<String, @Composable () -> Unit> = mapOf<String, @Composable () -> Unit>(
    "Conversation" to {
        val messages = remember {
            listOf(
                Message("g-cv-u", Role.USER, listOf(TextPart("u", "What's new in 0.3?"))),
                Message("g-cv-a", Role.ASSISTANT, listOf(TextPart("a", "Three things:\n\n1. The **agent's computer**\n2. Run **replay**\n3. Terminal colours"))),
            )
        }
        Box(Modifier.fillMaxWidth().height(260.dp)) { Conversation(ChatState(messages = messages), Modifier.fillMaxSize()) }
    },
    "Empty state" to {
        ChatEmptyState(
            title = "How can I help?",
            subtitle = "Offline demo · mock agent",
            suggestions = listOf(Suggestion("Plan a trip"), Suggestion("Browse a web page")),
            onSelect = {},
            showHero = false,
        )
    },
    "Shimmer" to { ShimmerText("Thinking about your question…", style = MaterialTheme.typography.titleMedium) },
    "Agent's computer" to {
        val page = remember { pageScreenshot() }
        val message = remember(page) { computerRun(page) }
        val computer = rememberAgentComputerState()
        LaunchedEffect(Unit) { computer.open(message.id, step = 1) }
        Box(Modifier.fillMaxWidth().height(520.dp)) { AgentComputerPanel(computer, message, Modifier.fillMaxSize()) }
    },
    "Agent's computer · in a reply" to {
        val page = remember { pageScreenshot() }
        val messages = remember(page) { listOf(Message("g-cu", Role.USER, listOf(TextPart("u", "Add the Team plan to the README"))), computerRun(page)) }
        Box(Modifier.fillMaxWidth().height(420.dp)) { Conversation(ChatState(messages = messages), Modifier.fillMaxSize()) }
    },
    "Step views" to {
        val page = remember { pageScreenshot() }
        val steps = remember(page) {
            val m = computerRun(page)
            listOf(
                AgentStep(m.parts[1] as ToolPart, listOf(m.parts[2] as FilePart)),
                AgentStep(m.parts[3] as ToolPart, emptyList()),
                AgentStep(m.parts[4] as ToolPart, emptyList()),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            steps.forEach { step -> Box(Modifier.fillMaxWidth().height(200.dp)) { StepView(step, Modifier.fillMaxSize()) } }
        }
    },
    "Input request (form)" to {
        var answer by remember { mutableStateOf<String?>(null) }
        val request = remember {
            InputRequest(
                "g-ir", "Before I book, confirm the details.",
                schema = Json.parseToJsonElement(
                    """{"type":"object","required":["name","room"],"properties":{
                      "name":{"type":"string","title":"Guest name"},
                      "room":{"type":"string","title":"Room","enum":["standard","deluxe","suite"]},
                      "nights":{"type":"integer","title":"Nights","minimum":1,"maximum":14,"default":2},
                      "breakfast":{"type":"boolean","title":"Breakfast included","default":true}}}""",
                ).jsonObject,
                source = "Hotel concierge",
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InputRequestCard(request, onRespond = { answer = (it as? InputResponse.Accept)?.content?.toString() ?: it.toString() })
            answer?.let { Text("Answer → $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
        }
    },
    "A2UI surface" to {
        var action by remember { mutableStateOf<String?>(null) }
        val state = remember { A2uiState().also { s -> Json.parseToJsonElement(BookingForm).jsonArray.forEach { s.process(it) } } }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.surface("booking-kyoto")?.let { A2uiSurfaceView(it, onAction = { a -> action = "${a.name} ${a.context}" }) }
            action?.let { Text("Action → $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
        }
    },
    "Attachments" to {
        val image = remember { pageScreenshot() }
        var files by remember {
            mutableStateOf(listOf(FilePart("g-a1", "image/png", image), FilePart("g-a2", "application/pdf", "", filename = "report.pdf")))
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AttachmentStrip(files, onRemove = { removed -> files = files - removed })
            FileAttachment(FilePart("g-a3", "image/png", image), imageHeight = 140.dp)
            Text("Tap the image to zoom, pan and rotate.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    },
    "Video" to {
        val context = LocalContext.current
        VideoAttachment(FilePart("g-v", "video/mp4", rawUrl(context, "sample_video"), filename = "screen-recording.mp4"))
    },
    "Document (PDF)" to {
        val pdf = remember { samplePdf() }
        DocumentAttachment(FilePart("g-pdf", "application/pdf", pdf, filename = "quarterly-report.pdf"))
    },
    "Voice mode" to {
        var talking by remember { mutableStateOf(false) }
        val controller = rememberChat { approver -> MockAgentBackend(approver = approver) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("A hands-free conversation over the same controller: listen, send, read the reply aloud, listen again.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { talking = true }) { Text("Start voice mode") }
        }
        if (talking) Dialog(onDismissRequest = { talking = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            VoiceMode(controller, onClose = { talking = false })
        }
    },
) + JsxSamples.associate { (_, title, sample) ->
    "JSX · $title" to @Composable { JsxSample(sample.first, sample.second) }
} + mapOf(
    "JSX · Streaming" to @Composable { JsxSample(JsxSamples[2].third.first, JsxSamples[2].third.second, streaming = true) },
)
