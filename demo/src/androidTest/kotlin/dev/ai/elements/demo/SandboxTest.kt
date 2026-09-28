package dev.ai.elements.demo

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.ai.elements.harness.sandbox.AlpineSandbox
import dev.ai.elements.harness.shell.Shell
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The Linux sandbox inside the real app process (targetSdk 36, W^X): PRoot from the native
 * library directory runs an Alpine root filesystem downloaded on first use.
 */
@RunWith(AndroidJUnit4::class)
class SandboxTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun alpineSandbox_runsCommands_andSharesTheWorkspace() = runBlocking<Unit> {
        // The bundled PRoot and root filesystem are arm64 (phones, tablets, Apple-silicon emulators).
        org.junit.Assume.assumeTrue("The Alpine sandbox needs an arm64 device", "arm64-v8a" in android.os.Build.SUPPORTED_ABIS)
        val workspace = File(context.filesDir, "sandbox-test-workspace").apply { deleteRecursively(); mkdirs() }
        val sandbox = AlpineSandbox(context, workspace)
        val shell = Shell(sandbox, defaultTimeoutSeconds = 120.0)

        val uname = shell.runCommand("uname -m && cat /etc/alpine-release && apk --version")
        assertTrue(uname, uname.contains("aarch64") && uname.contains("3.24") && uname.contains("apk-tools"))
        assertEquals(AlpineSandbox.State.Ready, sandbox.state.value)

        // Files written in the sandbox are the app's workspace files, and vice versa.
        shell.runCommand("echo from-linux > /workspace/hello.txt")
        assertEquals("from-linux\n", File(workspace, "hello.txt").readText())
        File(workspace, "data.csv").writeText("a,b\n1,2\n")
        assertEquals("[stdout]\n2\n", shell.runCommand("wc -l < data.csv | tr -d ' '"))

        val failed = shell.runCommand("ls /nonexistent")
        assertTrue(failed, failed.contains("[exit code: 1]"))
    }
}
