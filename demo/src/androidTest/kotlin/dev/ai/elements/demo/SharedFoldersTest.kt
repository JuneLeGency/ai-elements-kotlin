package dev.ai.elements.demo

import dev.ai.elements.demo.ui.SettingsPage
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
import org.junit.Before
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Assume.assumeTrue
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

    /** CI can leave a Pixel Launcher ANR above DocumentsUI; only that identified emulator overlay is handled. */
    @Before
    fun handleEmulatorLauncherOverlay() {
        if (Build.HARDWARE.contains("ranchu")) {
            device.registerWatcher("saf-pixel-launcher-anr") {
                val title = device.findObject(By.res("android", "alertTitle"))
                if (title?.text != "Pixel Launcher isn't responding") return@registerWatcher false
                val close = device.findObject(By.res("android", "aerr_close"))
                    ?: return@registerWatcher false
                close.click()
                true
            }
        }
    }

    @After
    fun cleanUp() {
        device.removeWatcher("saf-pixel-launcher-anr")
        folders.folders.value.forEach { folders.remove(it.name) }
        shell("rm -rf /sdcard/Documents/agent-share")
    }

    @Test
    fun pickedFolder_isMountedForTheFileTools() = runBlocking<Unit> {
        // It drives the system folder picker over shared storage: on a personal device only when asked
        // (`-e deviceData true`); pickers also differ between vendors.
        assumeTrue(
            "shared-storage test: pass -e deviceData true to run it on a physical device",
            android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu") ||
                InstrumentationRegistry.getArguments().getString("deviceData") == "true",
        )
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

    /** In the app: capabilities → Manage → On-device agent → "Add folder", then choose Documents/[name] in the system picker, as the user would. */
    private fun pickFolderInTheApp(name: String): Uri {
        resetDemoApp(ProviderProfile.Presets.first { it.kind == ProviderKind.MOCK })
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            compose.onNodeWithTag("capabilities-button").performClick()
            compose.onNodeWithTag("capabilities-manage").performClick()
            compose.onNodeWithTag("settings-${SettingsPage.DEVICE}").performClick()
            compose.onNodeWithTag("settings-page").performScrollToNode(hasTestTag("folder-add"))
            compose.onNodeWithTag("folder-add").performClick()

            // DocumentsUI may start at the storage root or restore the last directory.
            // A single-root picker can omit the roots button and the device-name label entirely.
            // Navigate the visible directory first; opening the roots drawer is only a fallback.
            val folder = By.text(Pattern.compile(Pattern.quote(name)))
            val documents = By.text(Pattern.compile("Documents"))
            if (!device.wait(Until.hasObject(folder), 3_000)) {
                if (!device.wait(Until.hasObject(documents), 5_000)) {
                    clickWhenShown(By.desc(Pattern.compile("(?i)show roots")), 15_000, required = false)
                    if (!device.hasObject(documents)) {
                        clickText(Pattern.compile(Pattern.quote(Build.MODEL) + "|(?i)internal storage"))
                    }
                }
                clickText(Pattern.compile("Documents"))
            }
            clickText(Pattern.compile(Pattern.quote(name)))
            clickText(Pattern.compile("(?i)use this folder"))
            clickText(Pattern.compile("(?i)allow"))

            compose.waitUntil(15_000) { folders.folders.value.isNotEmpty() }
            compose.onNodeWithTag("settings-page").performScrollToNode(hasTestTag("folder-$name"))
            return Uri.parse(folders.folders.value.single().uri)
        } finally {
            scenario.close()
        }
    }

    private fun clickText(text: Pattern) = clickWhenShown(By.text(text), 10_000, required = true)

    /** Finds and clicks; the picker re-lays out while it loads, so a found node can go stale: find it again. */
    private fun clickWhenShown(selector: androidx.test.uiautomator.BySelector, timeoutMs: Long, required: Boolean) {
        repeat(3) {
            val node = device.wait(Until.findObject(selector), timeoutMs)
            if (node == null) {
                if (required) assertNotNull("Not found in the picker: $selector", node)
                return
            }
            try {
                node.click()
                device.waitForIdle()
                return
            } catch (_: androidx.test.uiautomator.StaleObjectException) {
            }
        }
        throw AssertionError("Kept going stale in the picker: $selector")
    }
}
