package dev.ai.elements.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.ai.elements.core.theme.AiTokens
import dev.ai.elements.ui.confirmation.Confirmation
import dev.ai.elements.ui.confirmation.ConfirmationState
import dev.ai.elements.ui.image.GeneratedImage
import dev.ai.elements.ui.plan.Plan
import dev.ai.elements.ui.snippet.Snippet
import dev.ai.elements.ui.task.Task
import dev.ai.elements.ui.task.TaskItem
import dev.ai.elements.ui.task.TaskItemFile
import dev.ai.elements.ui.terminal.Terminal

/**
 * A scrollable gallery showcasing the library's "extra" components (beyond the
 * core chat flow): Task, Plan, Confirmation, GeneratedImage, Terminal, Snippet.
 */
@Composable
fun ComponentsGallery(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        GallerySection("Task") {
            Task(
                title = "Researching the Kotlin 2.3 release notes",
                defaultOpen = true,
            ) {
                TaskItem("Reading the official blog post")
                TaskItem("Cross-checking the changelog on GitHub")
                TaskItemFile("kotlin-2.3-release-notes.md")
            }
        }

        GallerySection("Plan") {
            var streaming by remember { mutableStateOf(false) }
            Plan(
                title = "Refactor the auth flow",
                description = "Split session handling from token refresh.",
                isStreaming = streaming,
                defaultOpen = true,
            ) {
                PlanStep("Extract the session store into its own module")
                PlanStep("Move token refresh to a background worker")
                PlanStep("Add unit tests for the refresh edge cases")
            }
            Text(
                text = if (streaming) "streaming…" else "done",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp),
            )
        }

        GallerySection("Confirmation") {
            var state by remember { mutableStateOf(ConfirmationState.APPROVAL_REQUESTED) }
            var approved by remember { mutableStateOf(false) }
            Confirmation(
                state = state,
                approved = approved,
                requestText = "run_shell wants to execute `git status`. Approve it?",
                acceptedText = "Approved — the tool ran successfully.",
                rejectedText = "Rejected — the tool did not run.",
                reason = "Approved by the user",
                onApprove = { approved = true; state = ConfirmationState.APPROVAL_RESPONDED },
                onDeny = { approved = false; state = ConfirmationState.APPROVAL_RESPONDED },
            )
            Row(
                modifier = Modifier.padding(top = 8.dp, start = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GalleryMiniButton("Reset") {
                    state = ConfirmationState.APPROVAL_REQUESTED
                    approved = false
                }
            }
        }

        GallerySection("GeneratedImage") {
            // A tiny 1x1 red PNG, base64-encoded, to exercise the decoder.
            val redPixel = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
            GeneratedImage(
                base64 = redPixel,
                mediaType = "image/png",
                alt = "Sample generated image",
            )
            Text(
                text = "A 1×1 red PNG decoded from base64.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp),
            )
        }

        GallerySection("Terminal") {
            var cleared by remember { mutableStateOf(false) }
            Terminal(
                output = """
                    $ npx ai-elements init
                    ✔ Created ai-elements-core
                    ✔ Created ai-elements-ui
                    ✔ Created demo
                    $ ./gradlew :demo:assembleDebug
                    BUILD SUCCESSFUL in 40s
                """.trimIndent(),
                isStreaming = false,
                onClear = { cleared = !cleared },
            )
        }

        GallerySection("Snippet") {
            Snippet(
                code = "./gradlew :demo:assembleDebug",
                label = "bash",
            )
            Snippet(
                code = "kotlin = \"2.2.20\"",
                label = "toml",
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun GallerySection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        content()
    }
}

@Composable
private fun PlanStep(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun GalleryMiniButton(
    label: String,
    onClick: () -> Unit,
) {
    val tokens = AiTokens()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondary)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
