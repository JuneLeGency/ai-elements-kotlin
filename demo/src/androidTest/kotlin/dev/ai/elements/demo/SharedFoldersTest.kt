package dev.ai.elements.demo

import android.net.Uri
import android.os.Build
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.ai.elements.harness.filesystem.FileSystem
import dev.ai.elements.core.config.ProviderKind
import dev.ai.elements.core.config.ProviderProfile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.regex.Pattern

/**
 * harness-filesystem over the Storage Access Framework, end to end: the user picks a
 * folder from the app's settings in the system picker (driven with UiAutomator), and the file tools read and write it at /mnt/<name>.
 */
@RunWith(AndroidJUnit4::class)
class SharedFoldersTest {
    private val app = ApplicationProvider.getApplicationContext<DemoApplication>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    @get:Rule val compose = createEmptyComposeRule()
    private val folders get() = app.runtime.sharedFolders

    /** Run [command] through `sh` as the shell user (redirects and quotes work). */
    private fun shell(command: String): String {
        val (stdout, stdin) = instrumentation.uiAutomation.executeShellCommandRw("sh")
        android.os.ParcelFileDescriptor.AutoCloseOutputStream(stdin).use { it.write("$command\n".toByteArray()) }
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(stdout).use { String(it.readBytes()) }
    }

    @After
    fun cleanUp() {
        folders.folders.value.forEach { folders.remove(it.name) }
        shell("rm -rf /sdcard/Documents/agent-share")
    }

    @Test
    fun pickedFolder_isMountedForTheFileTools() = runBlocking<Unit> {
        shell("mkdir -p /sdcard/Documents/agent-share/notes")
        shell("echo buy milk > /sdcard/Documents/agent-share/notes/todo.txt")

        pickFolderInTheApp("agent-share")
        assertEquals("agent-share", folders.folders.value.single().name)

        val fs = FileSystem(File(app.cacheDir, "saf-ws"), approveChanges = false, mounts = { folders.mounts() })
        assertEquals("/mnt/agent-share/notes/", fs.listDirectory("/mnt/agent-share"))
        assertTrue(fs.readFile("/mnt/agent-share/notes/todo.txt").contains("1\tbuy milk"))
        fs.editFile("/mnt/agent-share/notes/todo.txt", listOf("milk" to "eggs"))
        fs.createDirectory("/mnt/agent-share/out")
        fs.writeFile("/mnt/agent-share/out/report.md", "# Report\n")
        assertEquals("/mnt/agent-share/notes/todo.txt:1:buy eggs", fs.searchFiles("eggs", "/mnt/agent-share"))

        // The changes are real files in shared storage.
        assertEquals("buy eggs\n", shell("cat /sdcard/Documents/agent-share/notes/todo.txt"))
        assertEquals("# Report\n", shell("cat /sdcard/Documents/agent-share/out/report.md"))
        assertTrue(fs.context()!!.contains("/mnt/agent-share"))
    }

    /** In the app: capabilities → Manage → "Add folder", then choose Documents/[name] in the system picker, as the user would. */
    private fun pickFolderInTheApp(name: String): Uri {
        resetDemoApp(ProviderProfile.Presets.first { it.kind == ProviderKind.MOCK })
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            compose.onNodeWithTag("capabilities-button").performClick()
            compose.onNodeWithTag("capabilities-manage").performClick()
            compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("folder-add"))
            compose.onNodeWithTag("folder-add").performClick()

            // DocumentsUI: roots drawer → internal storage → Documents → name → Use this folder → Allow.
            device.wait(Until.findObject(By.desc(Pattern.compile("(?i)show roots"))), 15_000)?.click()
            clickText(Pattern.compile(Pattern.quote(Build.MODEL) + "|(?i)internal storage"))
            clickText(Pattern.compile("Documents"))
            clickText(Pattern.compile(Pattern.quote(name)))
            clickText(Pattern.compile("(?i)use this folder"))
            clickText(Pattern.compile("(?i)allow"))

            compose.waitUntil(15_000) { folders.folders.value.isNotEmpty() }
            compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("folder-$name"))
            return Uri.parse(folders.folders.value.single().uri)
        } finally {
            scenario.close()
        }
    }

    private fun clickText(text: Pattern) {
        val node = device.wait(Until.findObject(By.text(text)), 10_000)
        assertNotNull("Not found in the picker: $text", node)
        node.click()
        device.waitForIdle()
    }
}
