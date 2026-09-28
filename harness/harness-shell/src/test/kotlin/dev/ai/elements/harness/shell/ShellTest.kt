package dev.ai.elements.harness.shell

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellTest {
    /** The host's POSIX shell stands in for Android's / the sandbox's in JVM tests. */
    private val host = object : ShellRuntime {
        override val description = "a test shell"
        override val isolated = true
        override fun start(command: String): Process = ProcessBuilder("/bin/sh", "-c", command).start()
    }
    private val shell = Shell(host)

    @Test
    fun runCommand_labelsStreams_andReportsFailures() = runBlocking<Unit> {
        assertEquals("[stdout]\nhi\n", shell.runCommand("echo hi"))
        assertEquals("[stdout]\nout\n\n[stderr]\nerr\n\n[exit code: 3]", shell.runCommand("echo out; echo err >&2; exit 3"))
        assertEquals("(no output)", shell.runCommand("true"))
        assertEquals("[Command timed out after 0.5s]", shell.runCommand("sleep 5", timeoutSeconds = 0.5))
    }

    @Test
    fun terminalOutput_readsAsTheTerminalShowsIt() = runBlocking<Unit> {
        // Colors and a progress bar redrawn with \r: the model gets the final text only.
        val out = shell.runCommand("printf '\\033[32mok\\033[0m\\n 10%%\\r 50%%\\r100%%\\n'")
        assertEquals("[stdout]\nok\n100%", out)
    }

    @Test
    fun longOutput_keepsTheTail() {
        val text = "x".repeat(100) + "END"
        val cut = Shell.truncateTail(text, 60)
        assertTrue(cut.length <= 60)
        assertTrue(cut.startsWith("[... output truncated, showing last "))
        assertTrue(cut.endsWith("END"))
    }

    @Test
    fun backgroundCommands_startCheckStop() = runBlocking<Unit> {
        val started = shell.startCommand("echo ready; sleep 30")
        val id = started.substringAfter("ID: ")
        assertTrue(started.startsWith("Started background command: 'echo ready; sleep 30'"))
        delay(300)
        assertEquals("[stdout]\nready\n\n[status: running]", shell.checkCommand(id))
        val stopped = shell.stopCommand(id)
        assertTrue(stopped, stopped.startsWith("[stdout]\nready\n\n[stopped]"))
        assertEquals("[Error: unknown command ID '$id']", shell.checkCommand(id))
    }

    @Test
    fun tools_approvalFollowsIsolation() = runBlocking<Unit> {
        assertFalse(shell.tools().single { it.name == "run_command" }.requiresApproval)
        val unsandboxed = Shell(AndroidShellRuntime(java.io.File("/tmp")))
        assertTrue(unsandboxed.tools().single { it.name == "run_command" }.requiresApproval)
        assertEquals(listOf("run_command", "start_command", "check_command", "stop_command"), shell.tools().map { it.name })
    }
}
