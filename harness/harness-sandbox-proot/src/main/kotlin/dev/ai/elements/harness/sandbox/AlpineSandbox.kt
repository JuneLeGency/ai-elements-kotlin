package dev.ai.elements.harness.sandbox

import android.content.Context
import android.system.Os
import dev.ai.elements.harness.shell.ShellRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest

/** A pinned root filesystem image. */
data class RootfsImage(val name: String, val url: String, val sha256: String) {
    companion object {
        /** Alpine Linux 3.24.2 minirootfs for arm64 (~4 MB). */
        val ALPINE_AARCH64 = RootfsImage(
            "Alpine Linux 3.24.2 (aarch64)",
            "https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/aarch64/alpine-minirootfs-3.24.2-aarch64.tar.gz",
            "9bf70a7f18ea44094cbb5f70c58f9af129c8214745743db0e68e5502cc2ce773",
        )
    }
}

/**
 * A Linux sandbox for the agent's shell: [PRoot](https://proot-me.github.io)
 * (bundled, built from upstream termux/proot) runs an Alpine Linux root
 * filesystem as an unprivileged user-space "container". The agent gets a real
 * `/bin/sh`, BusyBox and `apk`, so it can install Python, git, compilers…
 * without root and without touching the rest of the app.
 *
 * The root filesystem is downloaded (pinned, checksum-verified) on first use
 * into app storage; [state] reports progress. [workspace] is mounted at
 * `/workspace` (the working directory), so files the agent edits with the
 * file tools and the shell are the same.
 *
 * The app must package native libraries extracted (`packaging.jniLibs.useLegacyPackaging = true`):
 * Android only lets apps execute files in their native library directory.
 */
class AlpineSandbox(
    private val context: Context,
    private val workspace: File,
    private val image: RootfsImage = RootfsImage.ALPINE_AARCH64,
    private val home: File = File(context.filesDir, "sandbox/alpine"),
) : ShellRuntime {

    sealed interface State {
        data object NotInstalled : State
        data class Installing(val progress: Float?, val step: String) : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(if (installedMarker().isFile) State.Ready else State.NotInstalled)
    val state: StateFlow<State> = _state.asStateFlow()
    private val lock = Mutex()

    private val rootfs get() = File(home, "rootfs")
    private val nativeDir get() = File(context.applicationInfo.nativeLibraryDir)
    private val proot get() = File(nativeDir, "libproot.so")

    override val description get() = "an ${image.name} sandbox (BusyBox, `apk add` to install packages; working directory /workspace)"
    override val isolated = true

    override suspend fun prepare() = lock.withLock {
        if (installedMarker().isFile) return@withLock
        check(proot.canExecute()) {
            "PRoot is not extracted from the APK: set packaging.jniLibs.useLegacyPackaging = true in the app module."
        }
        try {
            install()
            _state.value = State.Ready
        } catch (e: Exception) {
            _state.value = State.Failed(e.message ?: e.javaClass.simpleName)
            throw e
        }
    }

    override fun start(command: String): Process {
        workspace.mkdirs()
        val tmp = File(context.cacheDir, "proot-tmp").apply { mkdirs() }
        val args = listOf(
            proot.path, "--kill-on-exit", "--link2symlink", "-L", "-0",
            "-r", rootfs.path,
            "-b", "/dev", "-b", "/proc", "-b", "/sys",
            "-b", "${workspace.path}:/workspace",
            "-w", "/workspace",
            "/usr/bin/env", "-i",
            "HOME=/root", "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=dumb", "LANG=C.UTF-8", "TMPDIR=/tmp",
            "/bin/sh", "-c", command,
        )
        return ProcessBuilder(args).apply {
            environment().apply {
                clear()
                put("PROOT_LOADER", File(nativeDir, "libproot-loader.so").path)
                put("PROOT_LOADER_32", File(nativeDir, "libproot-loader32.so").path)
                put("PROOT_TMP_DIR", tmp.path)
            }
        }.start()
    }

    /** Remove the root filesystem (everything installed with `apk` is lost; the workspace is kept). */
    suspend fun reset() = lock.withLock {
        withContext(Dispatchers.IO) { home.deleteRecursively() }
        _state.value = State.NotInstalled
    }

    private suspend fun install() = withContext(Dispatchers.IO) {
        home.deleteRecursively()
        rootfs.mkdirs()
        _state.value = State.Installing(0f, "Downloading ${image.name}")
        val archive = File(home, "rootfs.tar.gz")
        download(archive)
        _state.value = State.Installing(null, "Unpacking")
        extract(archive.inputStream(), rootfs)
        archive.delete()
        // DNS inside the sandbox (Android has no /etc/resolv.conf to bind).
        File(rootfs, "etc/resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
        installedMarker().writeText(image.sha256)
    }

    private fun download(target: File) {
        val connection = URL(image.url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        try {
            check(connection.responseCode == 200) { "Download failed: HTTP ${connection.responseCode}" }
            val total = connection.contentLengthLong.takeIf { it > 0 }
            val digest = MessageDigest.getInstance("SHA-256")
            DigestInputStream(connection.inputStream, digest).use { input ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        _state.value = State.Installing(total?.let { done.toFloat() / it }, "Downloading ${image.name}")
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            check(actual == image.sha256) { "Checksum mismatch for ${image.name}" }
        } finally {
            connection.disconnect()
        }
    }

    private fun installedMarker() = File(home, ".installed")

    internal companion object {
        /** Unpack a `.tar.gz` into [target], keeping modes and symlinks; entries may not escape [target]. */
        fun extract(input: InputStream, target: File) {
            val root = target.canonicalPath + File.separator
            TarArchiveInputStream(GzipCompressorInputStream(input.buffered())).use { tar ->
                val hardLinks = mutableListOf<Pair<File, File>>()
                while (true) {
                    val entry = tar.nextEntry ?: break
                    val out = File(target, entry.name)
                    check(out.canonicalPath.startsWith(root) || out.canonicalPath + File.separator == root) { "Unsafe entry ${entry.name}" }
                    when {
                        entry.isDirectory -> out.mkdirs()
                        entry.isSymbolicLink -> { out.parentFile?.mkdirs(); out.delete(); Os.symlink(entry.linkName, out.path) }
                        entry.isLink -> hardLinks += out to File(target, entry.linkName)
                        entry.isFile -> {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { tar.copyTo(it) }
                        }
                        else -> continue
                    }
                    if (!entry.isSymbolicLink) runCatching { Os.chmod(out.path, entry.mode and 0x1FF) }
                }
                // Hard links become copies (Android app storage does not allow link()).
                hardLinks.forEach { (link, source) -> link.parentFile?.mkdirs(); source.copyTo(link, overwrite = true); link.setExecutable(source.canExecute()) }
            }
        }
    }
}
