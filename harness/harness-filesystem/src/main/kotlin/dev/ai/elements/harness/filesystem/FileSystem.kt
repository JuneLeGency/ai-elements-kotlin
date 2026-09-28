package dev.ai.elements.harness.filesystem

import dev.ai.elements.core.agent.AgentTool
import dev.ai.elements.core.agent.Capability
import dev.ai.elements.core.protocol.ToolConventions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.security.MessageDigest

/**
 * Sandboxed file tools over one directory tree (an app workspace), with the
 * tools, arguments and output format of Pydantic AI Harness `FileSystem`:
 * `read_file`, `write_file`, `edit_file`, `list_directory`, `search_files`,
 * `find_files`, `create_directory`, `file_info`.
 *
 * Every path is resolved (symlinks included) and must stay inside [root];
 * paths matching [deniedPatterns] are invisible, [protectedPatterns] are
 * read-only (by default `.git`, `.env*`, keys and secrets). Dotfiles are left
 * out of listings and searches. Reads show a 12-character content hash that
 * writes and edits can pass back as `expected_hash` to refuse stale changes.
 *
 * Tools that change the workspace ask the user first unless [approveChanges]
 * is off.
 *
 * [mounts] adds folders outside the workspace — typically ones the user shared
 * through the Storage Access Framework ([DocumentFolder]) — at `/mnt/<name>`,
 * served by the same tools with the same rules. The shell cannot see them.
 *
 * @param cwd where relative paths start; inside [root].
 */
class FileSystem(
    root: File,
    cwd: File = root,
    val readOnly: Boolean = false,
    val approveChanges: Boolean = true,
    val deniedPatterns: List<String> = emptyList(),
    val protectedPatterns: List<String> = DEFAULT_PROTECTED,
    val contentHashes: Boolean = true,
    val maxReadLines: Int = 2000,
    val maxListResults: Int = 1000,
    val maxSearchResults: Int = 1000,
    val maxFindResults: Int = 1000,
    private val mounts: suspend () -> List<Mount> = { emptyList() },
) : Capability {
    private val root: Path = root.apply { mkdirs() }.toPath().toRealPath()
    private val cwd: Path = cwd.apply { mkdirs() }.toPath().toRealPath().also {
        require(it.startsWith(this.root)) { "cwd must be inside root" }
    }
    private val denied = deniedPatterns.map(::matcher)
    private val protected = protectedPatterns.map { it to matcher(it) }

    override suspend fun context(): String? = mounts().takeIf { it.isNotEmpty() }?.let { list ->
        "Folders the user shared are mounted at: " + list.joinToString { "${Mount.ROOT}/${it.name}" + if (it.writable && !readOnly) "" else " (read-only)" } +
            ". Use absolute paths for them; the shell cannot see them (copy files into the workspace first)."
    }

    override suspend fun tools(): List<AgentTool> = buildList {
        add(tool("read_file", "Read a text file with line numbers.", "path" to str("File path relative to `cwd`."), "offset" to int("Zero-based line offset to start reading from."), "limit" to int("Maximum number of lines to return (default: $maxReadLines).")) { a ->
            readFile(a.s("path"), a.i("offset") ?: 0, a.i("limit") ?: maxReadLines)
        })
        add(tool("list_directory", "List the contents of a directory.", "path" to str("Directory path relative to `cwd`.")) { a -> listDirectory(a.s("path", ".")) })
        add(tool("search_files", "Search file contents using a regular expression.", "pattern" to str("Regex pattern to search for."), "path" to str("Directory to search in, relative to `cwd`."), "include_glob" to str("If provided, match this glob against root-relative paths (e.g. '*.py').")) { a ->
            searchFiles(a.s("pattern"), a.s("path", "."), a.opt("include_glob"))
        })
        add(tool("find_files", "Find files by glob pattern (name matching, not content search).", "pattern" to str("Glob pattern to match, relative to `path` (e.g. '*.py', '**/*.json'). Absolute patterns are rejected."), "path" to str("Directory to search in, relative to `cwd`.")) { a ->
            findFiles(a.s("pattern"), a.s("path", "."))
        })
        add(tool("file_info", "Get metadata about a file or directory.", "path" to str("File or directory path relative to `cwd`.")) { a -> fileInfo(a.s("path")) })
        if (!readOnly) {
            val hash = if (contentHashes) arrayOf("expected_hash" to str("If provided, the write is rejected when the file exists and its current hash doesn't match (optimistic concurrency).")) else emptyArray()
            add(tool("write_file", "Create or overwrite a file with conflict detection.", "path" to str("File path relative to `cwd`."), "content" to str("The text content to write."), *hash, change = true, required = listOf("path", "content")) { a ->
                writeFile(a.s("path"), a.s("content"), a.opt("expected_hash"))
            })
            add(tool(
                "edit_file",
                "Edit a file by exact string replacement with conflict detection.\n\nPass one `old_text`/`new_text` pair, or several as `replacements`. Each old_text must appear exactly once in the file as edited by the previous replacements; include surrounding context lines to ensure uniqueness. The file is only written once every replacement has matched.",
                "path" to str("File path relative to `cwd`."), "old_text" to str("Exact text to find (must appear exactly once)."), "new_text" to str("Replacement text."),
                "replacements" to buildJsonObject {
                    put("type", "array")
                    put("description", "Replacements to apply in order, instead of a single pair.")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") { putJsonObject("old_text") { put("type", "string") }; putJsonObject("new_text") { put("type", "string") } }
                        put("required", JsonArray(listOf(JsonPrimitive("old_text"), JsonPrimitive("new_text"))))
                    }
                },
                *hash, change = true, required = listOf("path"),
            ) { a ->
                val pairs = (a["replacements"] as? JsonArray)?.map { r -> (r as JsonObject).s("old_text") to r.s("new_text") }
                    ?: listOf(a.s("old_text") to a.s("new_text"))
                editFile(a.s("path"), pairs, a.opt("expected_hash"))
            })
            add(tool("create_directory", "Create a directory and any missing parents.", "path" to str("Directory path relative to `cwd`."), change = true) { a -> createDirectory(a.s("path")) })
        }
    }

    // --- Operations (also callable directly, outside an agent run) ---------------------------

    suspend fun readFile(path: String, offset: Int = 0, limit: Int = maxReadLines): String = onMount(path) { mountRead(it, offset, limit) } ?: io {
        val file = resolve(path)
        if (!Files.isRegularFile(file)) throw java.io.FileNotFoundException(if (Files.isDirectory(file)) "'$path' is a directory, not a file." else "File not found: $path")
        val raw = Files.readAllBytes(file)
        if (raw.isBinary()) return@io "[Binary file: ${raw.size} bytes. Use a binary-aware tool to inspect.]"
        val lines = String(raw, Charsets.UTF_8).splitLinesKeepEnds()
        "[$path | ${lines.size} lines${if (contentHashes) " | hash:${hash(raw)}" else ""}]\n" + formatLines(lines, offset, limit)
    }

    suspend fun writeFile(path: String, content: String, expectedHash: String? = null): String = onMount(path) { mountWrite(it, content, expectedHash) } ?: io {
        val file = resolve(path, write = true)
        if (Files.exists(file) && !Files.isRegularFile(file)) throw IllegalArgumentException("Path '$path' exists and is not a regular file.")
        val parent = file.parent
        if (!Files.isDirectory(parent)) throw java.io.FileNotFoundException("Parent directory '${display(parent)}' does not exist. Use create_directory first.")
        if (expectedHash != null && Files.exists(file)) checkHash(path, Files.readAllBytes(file), expectedHash)
        val bytes = content.toByteArray(Charsets.UTF_8)
        Files.write(file, bytes)
        "Wrote ${content.length} chars (${content.splitLinesKeepEnds().size} lines) to $path.${hashSuffix(bytes)}"
    }

    suspend fun editFile(path: String, replacements: List<Pair<String, String>>, expectedHash: String? = null): String = onMount(path) { mountEdit(it, replacements, expectedHash) } ?: io {
        val file = resolve(path, write = true)
        if (!Files.isRegularFile(file)) throw java.io.FileNotFoundException("File not found: $path")
        val bytes = applyEdits(path, Files.readAllBytes(file), replacements, expectedHash)
        Files.write(file, bytes)
        "Edited $path.${hashSuffix(bytes)}"
    }

    suspend fun listDirectory(path: String = "."): String = onMount(path) { mountList(it) } ?: io {
        val dir = resolve(path, checkAllowed = false)
        if (!Files.isDirectory(dir)) throw IllegalArgumentException("Not a directory: $path")
        val entries = mutableListOf<String>()
        Files.list(dir).use { stream ->
            for (entry in stream.sorted().iterator()) {
                if (hidden(entry) || isDenied(entry)) continue
                val target = runCatching { entry.toRealPath() }.getOrNull()?.takeIf { it.startsWith(root) } ?: continue
                if (entries.size >= maxListResults) { entries += "[... truncated at $maxListResults entries]"; break }
                entries += if (Files.isDirectory(target)) "${display(entry)}/" else "${display(entry)}  (${Files.size(target)} bytes)"
            }
        }
        entries.joinToString("\n").ifEmpty { "(empty directory)" }
    }

    suspend fun searchFiles(pattern: String, path: String = ".", includeGlob: String? = null): String {
        val regex = runCatching { Regex(pattern) }.getOrElse { throw IllegalArgumentException("Invalid regex: ${it.message}") }
        val include = includeGlob?.let(::matcher)
        return onMount(path) { mountSearch(it, regex, include) } ?: io { searchWorkspace(path, regex, include) }
    }

    private fun searchWorkspace(path: String, regex: Regex, include: PathMatcher?): String {
        val results = mutableListOf<String>()
        walk(resolve(path, checkAllowed = false)) { file ->
            if (include != null && !include.matches(root.relativize(file))) return@walk true
            val raw = runCatching { Files.readAllBytes(file) }.getOrNull() ?: return@walk true
            if (raw.isBinary()) return@walk true
            String(raw, Charsets.UTF_8).lineSequence().forEachIndexed { i, line ->
                if (regex.containsMatchIn(line)) {
                    if (results.size >= maxSearchResults) { results += "[... truncated at $maxSearchResults matches]"; return@walk false }
                    results += "${display(file)}:${i + 1}:$line"
                }
            }
            true
        }
        return results.joinToString("\n").ifEmpty { "No matches found." }
    }

    suspend fun findFiles(pattern: String, path: String = "."): String {
        require(!pattern.startsWith("/")) { "Pattern '$pattern' must be relative to the search path, not absolute." }
        val glob = matcher(pattern)
        return onMount(path) { mountFind(it, pattern, glob) } ?: io { findInWorkspace(path, pattern, glob) }
    }

    private fun findInWorkspace(path: String, pattern: String, glob: PathMatcher): String {
        val base = resolve(path, checkAllowed = false)
        val results = mutableListOf<String>()
        walk(base) { file ->
            val rel = base.relativize(file)
            if (glob.matches(rel) || (!pattern.contains('/') && glob.matches(rel.fileName))) {
                if (results.size >= maxFindResults) { results += "[... truncated at $maxFindResults files]"; return@walk false }
                results += display(file)
            }
            true
        }
        return results.joinToString("\n").ifEmpty { "No files found." }
    }

    suspend fun createDirectory(path: String): String = onMount(path) { mountMkdir(it) } ?: io {
        val dir = resolve(path, write = true)
        if (Files.exists(dir) && !Files.isDirectory(dir)) throw IllegalArgumentException("Path '$path' exists and is not a directory.")
        Files.createDirectories(dir)
        "Created directory: $path"
    }

    suspend fun fileInfo(path: String): String = onMount(path) { mountInfo(it) } ?: io {
        val original = cwd.resolve(path).normalize()
        val file = resolve(path)
        if (!Files.exists(file)) throw java.io.FileNotFoundException("Path not found: $path")
        val parts = mutableListOf("path: $path", "type: ${if (Files.isDirectory(file)) "directory" else "file"}", "size: ${Files.size(file)} bytes")
        if (Files.isRegularFile(file)) {
            val raw = Files.readAllBytes(file)
            parts += "binary: ${if (raw.isBinary()) "True" else "False"}"
            if (!raw.isBinary()) {
                parts += "lines: ${String(raw, Charsets.UTF_8).lines().let { if (it.lastOrNull() == "") it.size - 1 else it.size }}"
                parts += "hash: ${hash(raw)}"
            }
        }
        if (Files.isSymbolicLink(original)) parts += "symlink_target: ${display(file)}"
        parts.joinToString("\n")
    }

    // --- Mounted folders ------------------------------------------------------------------------

    /** A path under [Mount.ROOT]: [mount] null is `/mnt` itself. */
    private class Target(val path: String, val mount: Mount?, val parts: List<String>) {
        val rel get() = parts.joinToString("/")
        val display get() = if (mount == null) Mount.ROOT else "${Mount.ROOT}/${mount.name}" + if (parts.isEmpty()) "" else "/$rel"
        fun display(sub: List<String>) = (listOf("${Mount.ROOT}/${mount!!.name}") + parts + sub).joinToString("/")
    }

    /** Runs [op] on IO when [path] is under [Mount.ROOT]; null for workspace paths. */
    private suspend fun onMount(path: String, op: (Target) -> String): String? {
        require(!path.contains('\u0000')) { "Invalid path" }
        if (path != Mount.ROOT && !path.startsWith(Mount.ROOT + "/")) return null
        val parts = path.removePrefix(Mount.ROOT).split('/').filter { it.isNotEmpty() && it != "." }
        if (".." in parts) throw SecurityException("Path '$path' uses '..'; use absolute paths under ${Mount.ROOT}.")
        val available = mounts()
        val target = if (parts.isEmpty()) Target(path, null, parts)
        else Target(path, available.firstOrNull { it.name == parts[0] } ?: throw java.io.FileNotFoundException("No folder is mounted at ${Mount.ROOT}/${parts[0]}."), parts.drop(1))
        if (target.parts.isNotEmpty() && isDenied(target.rel)) throw SecurityException("Path '$path' is not accessible.")
        return if (target.mount == null) io { available.joinToString("\n") { "${Mount.ROOT}/${it.name}/" + if (it.writable && !readOnly) "" else "  (read-only)" }.ifEmpty { "(no folders mounted)" } }
        else io { op(target) }
    }

    private fun node(t: Target, parts: List<String> = t.parts): FolderNode? =
        parts.fold(t.mount!!.root as FolderNode?) { node, name -> node?.takeIf { it.isDirectory }?.child(name) }

    private fun checkWritable(t: Target) {
        val mount = t.mount!!
        if (readOnly || !mount.writable) throw SecurityException("${Mount.ROOT}/${mount.name} is read-only.")
        val rel = java.nio.file.Paths.get(t.rel)
        protected.firstOrNull { (_, m) -> m.matches(rel) || (rel.fileName != null && m.matches(rel.fileName)) }?.let { (p, _) ->
            throw SecurityException("Path '${t.path}' is protected (matches '$p').")
        }
    }

    private fun isDenied(rel: String): Boolean {
        val p = java.nio.file.Paths.get(rel)
        return denied.any { it.matches(p) || (p.fileName != null && it.matches(p.fileName)) }
    }

    private fun mountFile(t: Target): FolderNode {
        val node = node(t) ?: throw java.io.FileNotFoundException("File not found: ${t.path}")
        if (node.isDirectory) throw java.io.FileNotFoundException("'${t.path}' is a directory, not a file.")
        return node
    }

    private fun mountRead(t: Target, offset: Int, limit: Int): String {
        val raw = mountFile(t).read()
        if (raw.isBinary()) return "[Binary file: ${raw.size} bytes. Use a binary-aware tool to inspect.]"
        val lines = String(raw, Charsets.UTF_8).splitLinesKeepEnds()
        return "[${t.path} | ${lines.size} lines${if (contentHashes) " | hash:${hash(raw)}" else ""}]\n" + formatLines(lines, offset, limit)
    }

    private fun mountWrite(t: Target, content: String, expectedHash: String?): String {
        require(t.parts.isNotEmpty()) { "Path '${t.path}' is a folder." }
        checkWritable(t)
        val parent = node(t, t.parts.dropLast(1))?.takeIf { it.isDirectory }
            ?: throw java.io.FileNotFoundException("Parent directory '${t.display.substringBeforeLast('/')}' does not exist. Use create_directory first.")
        val existing = parent.child(t.parts.last())
        if (existing != null && existing.isDirectory) throw IllegalArgumentException("Path '${t.path}' exists and is not a regular file.")
        if (expectedHash != null && existing != null) checkHash(t.path, existing.read(), expectedHash)
        val bytes = content.toByteArray(Charsets.UTF_8)
        (existing ?: parent.createFile(t.parts.last())).write(bytes)
        return "Wrote ${content.length} chars (${content.splitLinesKeepEnds().size} lines) to ${t.path}.${hashSuffix(bytes)}"
    }

    private fun mountEdit(t: Target, replacements: List<Pair<String, String>>, expectedHash: String?): String {
        checkWritable(t)
        val file = mountFile(t)
        val bytes = applyEdits(t.path, file.read(), replacements, expectedHash)
        file.write(bytes)
        return "Edited ${t.path}.${hashSuffix(bytes)}"
    }

    private fun mountList(t: Target): String {
        val dir = node(t)?.takeIf { it.isDirectory } ?: throw IllegalArgumentException("Not a directory: ${t.path}")
        val entries = mutableListOf<String>()
        for (child in dir.children().sortedBy { it.name }) {
            if (child.name.startsWith(".") || isDenied((t.parts + child.name).joinToString("/"))) continue
            if (entries.size >= maxListResults) { entries += "[... truncated at $maxListResults entries]"; break }
            entries += if (child.isDirectory) "${t.display(listOf(child.name))}/" else "${t.display(listOf(child.name))}  (${child.size} bytes)"
        }
        return entries.joinToString("\n").ifEmpty { "(empty directory)" }
    }

    /** Depth-first walk of files under [t] (sorted, dotfiles and denied paths skipped), with paths relative to [t]; [visit] returns false to stop. */
    private fun mountWalk(t: Target, visit: (List<String>, FolderNode) -> Boolean) {
        val base = node(t)?.takeIf { it.isDirectory } ?: throw IllegalArgumentException("Not a directory: ${t.path}")
        fun go(dir: FolderNode, prefix: List<String>): Boolean {
            for (child in dir.children().sortedBy { it.name }) {
                val rel = prefix + child.name
                if (child.name.startsWith(".") || isDenied((t.parts + rel).joinToString("/"))) continue
                if (child.isDirectory) { if (!go(child, rel)) return false } else if (!visit(rel, child)) return false
            }
            return true
        }
        go(base, emptyList())
    }

    private fun mountSearch(t: Target, regex: Regex, include: PathMatcher?): String {
        val results = mutableListOf<String>()
        mountWalk(t) { rel, file ->
            if (include != null && !include.matches(java.nio.file.Paths.get((t.parts + rel).joinToString("/")))) return@mountWalk true
            val raw = runCatching { file.read() }.getOrNull() ?: return@mountWalk true
            if (raw.isBinary()) return@mountWalk true
            String(raw, Charsets.UTF_8).lineSequence().forEachIndexed { i, line ->
                if (regex.containsMatchIn(line)) {
                    if (results.size >= maxSearchResults) { results += "[... truncated at $maxSearchResults matches]"; return@mountWalk false }
                    results += "${t.display(rel)}:${i + 1}:$line"
                }
            }
            true
        }
        return results.joinToString("\n").ifEmpty { "No matches found." }
    }

    private fun mountFind(t: Target, pattern: String, glob: PathMatcher): String {
        val results = mutableListOf<String>()
        mountWalk(t) { rel, _ ->
            val path = java.nio.file.Paths.get(rel.joinToString("/"))
            if (glob.matches(path) || (!pattern.contains('/') && glob.matches(path.fileName))) {
                if (results.size >= maxFindResults) { results += "[... truncated at $maxFindResults files]"; return@mountWalk false }
                results += t.display(rel)
            }
            true
        }
        return results.joinToString("\n").ifEmpty { "No files found." }
    }

    private fun mountMkdir(t: Target): String {
        require(t.parts.isNotEmpty()) { "${t.path} already exists." }
        checkWritable(t)
        t.parts.fold(t.mount!!.root) { dir, name ->
            val existing = dir.child(name)
            when {
                existing == null -> dir.createDirectory(name)
                existing.isDirectory -> existing
                else -> throw IllegalArgumentException("Path '${t.path}' exists and is not a directory.")
            }
        }
        return "Created directory: ${t.path}"
    }

    private fun mountInfo(t: Target): String {
        val node = node(t) ?: throw java.io.FileNotFoundException("Path not found: ${t.path}")
        val parts = mutableListOf("path: ${t.path}", "type: ${if (node.isDirectory) "directory" else "file"}", "size: ${node.size} bytes")
        if (!node.isDirectory) {
            val raw = node.read()
            parts += "binary: ${if (raw.isBinary()) "True" else "False"}"
            if (!raw.isBinary()) {
                parts += "lines: ${String(raw, Charsets.UTF_8).lines().let { if (it.lastOrNull() == "") it.size - 1 else it.size }}"
                parts += "hash: ${hash(raw)}"
            }
        }
        if (t.mount?.writable == false || readOnly) parts += "read_only: True"
        return parts.joinToString("\n")
    }

    // --- Sandbox --------------------------------------------------------------------------------

    /** Resolve [path] from cwd, following symlinks, and enforce containment and patterns. */
    private fun resolve(path: String, write: Boolean = false, checkAllowed: Boolean = true): Path {
        require(!path.contains('\u0000')) { "Invalid path" }
        val candidate = cwd.resolve(path).normalize()
        // Resolve the deepest existing ancestor, so new files are checked through their real parent.
        var existing = candidate
        while (!Files.exists(existing, LinkOption.NOFOLLOW_LINKS) && existing.parent != null) existing = existing.parent
        val real = existing.toRealPath().resolve(existing.relativize(candidate)).normalize()
        if (!real.startsWith(root)) throw SecurityException("Path '$path' is outside the workspace.")
        val rel = root.relativize(real)
        if (checkAllowed && isDenied(real)) throw SecurityException("Path '$path' is not accessible.")
        if (write) {
            if (readOnly) throw SecurityException("The workspace is read-only.")
            protected.firstOrNull { (_, m) -> m.matches(rel) || (rel.fileName != null && m.matches(rel.fileName)) }?.let { (p, _) ->
                throw SecurityException("Path '$path' is protected (matches '$p').")
            }
        }
        return real
    }

    private fun isDenied(path: Path): Boolean {
        val rel = runCatching { root.relativize(path.toRealPath()) }.getOrElse { root.relativize(path) }
        return denied.any { it.matches(rel) || (rel.fileName != null && it.matches(rel.fileName)) }
    }

    private fun hidden(path: Path): Boolean = root.relativize(path).any { it.toString().startsWith(".") }

    /** Depth-first walk of regular files under [dir] (sorted, dotfiles and denied paths skipped); [visit] returns false to stop. */
    private fun walk(dir: Path, visit: (Path) -> Boolean) {
        if (!Files.isDirectory(dir)) throw IllegalArgumentException("Not a directory: ${display(dir)}")
        Files.walk(dir).use { stream ->
            for (p in stream.sorted().iterator()) {
                if (p == dir || hidden(p) || isDenied(p) || !Files.isRegularFile(p)) continue
                val real = runCatching { p.toRealPath() }.getOrNull() ?: continue
                if (!real.startsWith(root)) continue
                if (!visit(p)) return
            }
        }
    }

    private fun display(path: Path): String = cwd.relativize(path).toString().ifEmpty { "." }

    private fun checkHash(path: String, raw: ByteArray, expected: String) {
        val actual = hash(raw)
        if (actual != expected) throw IllegalStateException("Conflict: $path changed since it was read (hash $actual, expected $expected). Read it again.")
    }

    /** [replacements] applied in order to text [raw]; each old text must match exactly once. */
    private fun applyEdits(path: String, raw: ByteArray, replacements: List<Pair<String, String>>, expectedHash: String?): ByteArray {
        if (raw.isBinary()) throw IllegalArgumentException("$path is a binary file; edit_file only edits text files.")
        if (expectedHash != null) checkHash(path, raw, expectedHash)
        var text = String(raw, Charsets.UTF_8)
        replacements.forEachIndexed { i, (old, new) ->
            require(old.isNotEmpty()) { "old_text must not be empty." }
            val count = text.windowedCount(old)
            val which = if (replacements.size > 1) " (replacement ${i + 1})" else ""
            require(count > 0) { "old_text$which not found in $path." }
            require(count == 1) { "old_text$which appears $count times in $path; include more context to make it unique." }
            text = text.replaceFirst(old, new)
        }
        return text.toByteArray(Charsets.UTF_8)
    }

    private fun hashSuffix(bytes: ByteArray) = if (contentHashes) " [hash:${hash(bytes)}]" else ""

    private fun formatLines(lines: List<String>, offset: Int, limit: Int): String {
        if (lines.isEmpty()) return "(empty file)\n"
        require(offset < lines.size) { "Offset $offset exceeds file length (${lines.size} lines)." }
        val shown = lines.subList(offset, minOf(lines.size, offset + limit))
        val body = StringBuilder()
        shown.forEachIndexed { i, line -> body.append((offset + i + 1).toString().padStart(6)).append('\t').append(line) }
        if (!body.endsWith("\n")) body.append('\n')
        val remaining = lines.size - offset - shown.size
        if (remaining > 0) body.append("... ($remaining more lines. Use offset=${offset + shown.size} to continue reading.)\n")
        return body.toString()
    }

    // --- Tool plumbing ------------------------------------------------------------------------------

    private fun tool(
        name: String,
        description: String,
        vararg params: Pair<String, JsonObject>,
        change: Boolean = false,
        required: List<String> = params.take(1).map { it.first },
        run: suspend (JsonObject) -> String,
    ): AgentTool = object : AgentTool {
        override val name = name
        override val description = description
        override val parameters = buildJsonObject {
            put("type", "object")
            put("properties", JsonObject(params.toMap()))
            put("required", JsonArray(required.map(::JsonPrimitive)))
        }
        override val requiresApproval = change && approveChanges
        override fun titleFor(arguments: JsonObject) = arguments.opt("path") ?: arguments.opt("pattern")
        override fun categoryFor(arguments: JsonObject) = ToolConventions.categoryOf(name, arguments)
        override fun locationFor(arguments: JsonObject) = ToolConventions.locationOf(name, arguments)
        override suspend fun execute(arguments: JsonObject) = run(arguments)
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        /** Read-only by default: git metadata, env files, keys and secrets (as in Pydantic AI Harness). */
        val DEFAULT_PROTECTED = listOf(".git/*", ".env", ".env.*", "*.pem", "*.key", "**/secrets*")

        private fun matcher(glob: String): PathMatcher = FileSystems.getDefault().getPathMatcher("glob:$glob")

        internal fun hash(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }.take(12)

        private fun ByteArray.isBinary() = take(8192).contains(0.toByte())

        private fun String.splitLinesKeepEnds(): List<String> {
            if (isEmpty()) return emptyList()
            val out = mutableListOf<String>()
            var start = 0
            forEachIndexed { i, c -> if (c == '\n') { out += substring(start, i + 1); start = i + 1 } }
            if (start < length) out += substring(start)
            return out
        }

        private fun String.windowedCount(needle: String): Int {
            var count = 0
            var i = indexOf(needle)
            while (i >= 0) { count++; i = indexOf(needle, i + 1) }
            return count
        }

        private fun str(description: String) = buildJsonObject { put("type", "string"); put("description", description) }
        private fun int(description: String) = buildJsonObject { put("type", "integer"); put("description", description) }
        private fun JsonObject.opt(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
        private fun JsonObject.s(key: String, default: String? = null) = opt(key) ?: default ?: throw IllegalArgumentException("missing $key")
        private fun JsonObject.i(key: String) = (this[key] as? JsonPrimitive)?.intOrNull
    }
}
