package dev.ai.elements.harness.shell

import dev.ai.elements.core.agent.AgentTool
import dev.ai.elements.core.agent.Capability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Where commands run. [start] launches `command` through the runtime's shell;
 * the process must end when destroyed (children included).
 */
interface ShellRuntime {
    /** Shown to the model, e.g. "Alpine Linux 3.24 (aarch64) with busybox and apk". */
    val description: String

    /** Whether commands are isolated from the app and device (a sandbox); if not, they need approval by default. */
    val isolated: Boolean

    /** One-time setup before the first command (e.g. installing a root filesystem); idempotent. */
    suspend fun prepare() = Unit

    fun start(command: String): Process
}

/**
 * The device's own `/system/bin/sh` (toybox) in [workingDirectory]: no package
 * manager and the app's permissions, so commands need approval by default.
 */
class AndroidShellRuntime(private val workingDirectory: File) : ShellRuntime {
    override val description = "Android's /system/bin/sh (toybox utilities), working directory ${workingDirectory.path}"
    override val isolated = false

    override fun start(command: String): Process =
        ProcessBuilder("/system/bin/sh", "-c", command).directory(workingDirectory.apply { mkdirs() }).start()
}

/**
 * Shell commands with the tools and output format of Pydantic AI Harness
 * `Shell`: `run_command` (labelled `[stdout]` / `[stderr]`, `[exit code: N]` on
 * failure, the tail kept when longer than [maxOutputChars]), and background
 * `start_command` / `check_command` / `stop_command`.
 *
 * @param approveCommands ask the user before each command; defaults to on unless the runtime is isolated.
 */
class Shell(
    private val runtime: ShellRuntime,
    val defaultTimeoutSeconds: Double = 30.0,
    val maxOutputChars: Int = 50_000,
    val approveCommands: Boolean = !runtime.isolated,
) : Capability {
    private class Background(val command: String, val process: Process, val stdout: StringBuffer, val stderr: StringBuffer, val pumps: List<Thread>)

    private val background = ConcurrentHashMap<String, Background>()

    override val instructions: String
        get() = "You can run shell commands in ${runtime.description} with `run_command`. " +
            "Use `start_command` for long-running processes (servers, watchers), `check_command` to read their output, " +
            "and always `stop_command` them when done."

    override suspend fun tools(): List<AgentTool> = listOf(
        tool("run_command", "Execute a shell command and return its output.", mapOf(
            "command" to prop("string", "The shell command to run."),
            "timeout_seconds" to prop("number", "Maximum seconds to wait (default: ${defaultTimeoutSeconds.toInt()})."),
        ), listOf("command"), approval = approveCommands) { a -> runCommand(a.s("command")!!, (a["timeout_seconds"] as? JsonPrimitive)?.doubleOrNull) },
        tool("start_command", "Start a long-running command in the background (e.g. a server or watcher).\n\nCallers MUST call `stop_command(command_id)` when done to terminate the process and clean up temporary output files.", mapOf(
            "command" to prop("string", "The shell command to run in the background."),
        ), listOf("command"), approval = approveCommands) { a -> startCommand(a.s("command")!!) },
        tool("check_command", "Check the status and recent output of a background command.", mapOf(
            "command_id" to prop("string", "The ID returned by start_command."),
        ), listOf("command_id")) { a -> checkCommand(a.s("command_id")!!) },
        tool("stop_command", "Stop a background command and return its final output.", mapOf(
            "command_id" to prop("string", "The ID returned by start_command."),
        ), listOf("command_id")) { a -> stopCommand(a.s("command_id")!!) },
    )

    suspend fun runCommand(command: String, timeoutSeconds: Double? = null): String {
        runtime.prepare()
        val timeout = timeoutSeconds ?: defaultTimeoutSeconds
        val process = withContext(Dispatchers.IO) { runtime.start(command) }
        return try {
            val output = coroutineScope {
                val out = async(Dispatchers.IO) { process.inputStream.readAll() }
                val err = async(Dispatchers.IO) { process.errorStream.readAll() }
                val finished = withTimeoutOrNull((timeout * 1000).toLong()) {
                    runInterruptible(Dispatchers.IO) { process.waitFor() }
                }
                if (finished == null) {
                    process.destroyTree()
                    out.cancel(); err.cancel()
                    return@coroutineScope null
                }
                Triple(out.await(), err.await(), finished)
            } ?: return "[Command timed out after ${format(timeout)}s]"
            val (stdout, stderr, exit) = output
            val parts = listOfNotNull(stdout.takeIf { it.isNotEmpty() }?.let { "[stdout]\n$it" }, stderr.takeIf { it.isNotEmpty() }?.let { "[stderr]\n$it" })
            var result = parts.joinToString("\n").ifEmpty { "(no output)" }
            if (exit != 0) result += "\n[exit code: $exit]"
            truncateTail(result, maxOutputChars)
        } catch (e: CancellationException) {
            process.destroyTree()
            throw e
        }
    }

    suspend fun startCommand(command: String): String {
        runtime.prepare()
        val id = UUID.randomUUID().toString().replace("-", "").take(12)
        val process = withContext(Dispatchers.IO) { runtime.start(command) }
        val stdout = StringBuffer()
        val stderr = StringBuffer()
        val pumps = listOf(process.inputStream to stdout, process.errorStream to stderr).map { (stream, sink) ->
            Thread { runCatching { stream.bufferedReader().use { r -> val buf = CharArray(4096); while (true) { val n = r.read(buf); if (n < 0) break; sink.append(buf, 0, n) } } } }
                .apply { isDaemon = true; name = "shell-$id"; start() }
        }
        background[id] = Background(command, process, stdout, stderr, pumps)
        return "Started background command: '$command'\nID: $id"
    }

    fun checkCommand(id: String): String {
        val bg = background[id] ?: return "[Error: unknown command ID '$id']"
        val finished = !bg.process.isAlive
        val parts = mutableListOf(sections(bg).ifEmpty { "(no output yet)" }, "[status: ${if (finished) "finished" else "running"}]")
        if (finished) parts += "[exit code: ${bg.process.exitValue()}]"
        return truncateTail(parts.joinToString("\n"), maxOutputChars)
    }

    suspend fun stopCommand(id: String): String {
        val bg = background.remove(id) ?: return "[Error: unknown command ID '$id']"
        withContext(Dispatchers.IO) {
            if (bg.process.isAlive) { bg.process.destroyTree(); bg.process.waitFor(5, TimeUnit.SECONDS) }
            bg.pumps.forEach { it.join(1_000) }
        }
        val parts = mutableListOf(sections(bg).ifEmpty { "(no output)" }, "[stopped]")
        runCatching { bg.process.exitValue() }.getOrNull()?.let { parts += "[exit code: $it]" }
        return truncateTail(parts.joinToString("\n"), maxOutputChars)
    }

    /** Stops every background command (call when the harness goes away). */
    fun close() = background.keys.toList().forEach { id -> background.remove(id)?.process?.destroyTree() }

    private fun sections(bg: Background) = listOfNotNull(
        bg.stdout.toString().takeIf { it.isNotEmpty() }?.let { "[stdout]\n$it" },
        bg.stderr.toString().takeIf { it.isNotEmpty() }?.let { "[stderr]\n$it" },
    ).joinToString("\n")

    private fun tool(name: String, description: String, properties: Map<String, JsonObject>, required: List<String>, approval: Boolean = false, run: suspend (JsonObject) -> String) = object : AgentTool {
        override val name = name
        override val description = description
        override val parameters = buildJsonObject {
            put("type", "object")
            put("properties", JsonObject(properties))
            put("required", JsonArray(required.map(::JsonPrimitive)))
        }
        override val requiresApproval = approval
        override fun titleFor(arguments: JsonObject) = arguments.s("command")?.lineSequence()?.first()?.take(60) ?: arguments.s("command_id")
        override suspend fun execute(arguments: JsonObject) = run(arguments)
    }

    companion object {
        /** Keep the end of [text] (errors land there), marking what was cut, within [maxChars] (Harness `truncate_tail`). */
        fun truncateTail(text: String, maxChars: Int): String {
            if (text.length <= maxChars) return text
            fun marker(n: Int) = "[... output truncated, showing last $n chars]\n"
            var tail = (maxChars - marker(maxChars).length).coerceAtLeast(0)
            if (tail + 1 + marker(tail + 1).length <= maxChars) tail += 1
            return marker(tail) + text.takeLast(tail)
        }

        private fun format(seconds: Double) = if (seconds == Math.floor(seconds)) "${seconds.toInt()}.0" else seconds.toString()
        private fun InputStream.readAll() = bufferedReader().use { it.readText() }
        private fun prop(type: String, description: String) = buildJsonObject { put("type", type); put("description", description) }
        private fun JsonObject.s(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull

        /** Destroy the process and its descendants (a shell's children survive a plain destroy()). */
        internal fun Process.destroyTree() {
            runCatching {
                val pid = javaClass.getDeclaredField("pid").apply { isAccessible = true }.getInt(this)
                ProcessBuilder("/system/bin/sh", "-c", "pkill -KILL -P $pid").start().waitFor(2, TimeUnit.SECONDS)
            }
            destroyForcibly()
        }
    }
}
