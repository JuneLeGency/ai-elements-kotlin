package dev.ai.elements.demo

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.ai.elements.core.chat.ChatState
import dev.ai.elements.core.model.*
import dev.ai.elements.ui.chat.Conversation
import dev.ai.elements.ui.chat.PromptInput
import dev.ai.elements.ui.icons.AiIcons
import dev.ai.elements.ui.theme.AiElementsTheme
import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/** Curated documentation examples, rendered by the real components; no network or system locale changes. */
@RunWith(AndroidJUnit4::class)
class ReleaseScreenshotsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bilingualLandingScreens() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("releaseScreenshots") == "true")
        val out = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "release-screens").apply { mkdirs() }
        var language by mutableStateOf("en")
        var dark by mutableStateOf(false)
        compose.setContent {
            val base = LocalContext.current
            val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.forLanguageTag(language)) }
            val context = remember(base, language) {
                val resources = base.createConfigurationContext(configuration).resources
                object : android.content.ContextWrapper(base) { override fun getResources() = resources }
            }
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides configuration, LocalResources provides context.resources) {
                AiElementsTheme(dynamicColor = false, darkTheme = dark) {
                    val zh = language == "zh-CN"
                    val messages = remember(zh) { conversation(zh) }
                    Column(Modifier.width(400.dp).height(800.dp).background(MaterialTheme.colorScheme.background).testTag("release-preview")) {
                        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(AiIcons.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("AI Elements", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(if (zh) "Compose 示例 · 工作区" else "Compose demo · Workspace", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider()
                        Conversation(ChatState(messages = messages), modifier = Modifier.weight(1f), onToolDecision = { _, _ -> })
                        PromptInput("", {}, {}, {}, busy = false, modifier = Modifier.padding(12.dp), placeholder = if (zh) "继续对话…" else "Continue the conversation…")
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        for (lang in listOf("en", "zh-CN")) for (night in listOf(false, true)) {
            compose.runOnIdle { language = lang; dark = night }
            compose.mainClock.advanceTimeBy(3_000)
            compose.waitForIdle()
            val bitmap = compose.onNodeWithTag("release-preview").captureToImage().asAndroidBitmap()
            File(out, "landing-${if (night) "dark" else "light"}-$lang.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        assertEquals(4, out.listFiles()!!.count { it.extension == "png" })
    }

    private fun conversation(zh: Boolean): List<Message> {
        fun text(en: String, cn: String) = if (zh) cn else en
        val plan = buildJsonObject {
            put("title", text("Chat integration", "接入聊天界面"))
            put("description", text("Two steps", "两个步骤"))
            putJsonArray("steps") {
                for ((label, status) in listOf(text("Prepare the screen", "准备聊天界面") to "complete", text("Create the files", "创建接入文件") to "active")) {
                    add(buildJsonObject { put("label", label); put("status", status) })
                }
            }
        }
        return listOf(
            Message("request", Role.USER, listOf(TextPart("question", text("Add AI chat to my app.", "帮我在 App 里接入 AI 聊天。")))),
            Message("answer", Role.ASSISTANT, listOf(
                ReasoningPart("reasoning", text("Reviewed the project and selected the shared chat components.", "已检查项目结构，选择可复用的聊天组件。"), durationMs = 1800),
                ToolPart("read", "read_file", ToolState.OUTPUT_AVAILABLE, "{\"path\":\"build.gradle.kts\"}", output = "Compose enabled", title = text("Read app config", "检查应用配置"), source = text("Workspace", "工作区")),
                DataPart("plan", "plan", plan),
                TextPart("content", text("### Your chat screen is ready\n\nOne controller for streaming replies and tools.\n\n```kotlin\nAiElementsTheme { Chat(controller) }\n```", "### 聊天界面已准备好\n\n流式回复与工具审批共用一个 controller。\n\n```kotlin\nAiElementsTheme { Chat(controller) }\n```")),
                ToolPart("write", "write_file", ToolState.APPROVAL_REQUESTED, "{\"path\":\"ChatScreen.kt\"}", title = text("Create ChatScreen.kt", "创建 ChatScreen.kt"), source = text("Workspace", "工作区")),
            )),
        )
    }
}
