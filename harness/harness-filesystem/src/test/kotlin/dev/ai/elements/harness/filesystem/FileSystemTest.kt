package dev.ai.elements.harness.filesystem

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class FileSystemTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun fs(configure: (File) -> Unit = {}): Pair<FileSystem, File> {
        val root = tmp.newFolder("ws")
        configure(root)
        return FileSystem(root) to root
    }

    @Test
    fun readFile_headerLineNumbersAndPaging() = runBlocking<Unit> {
        val (fs, root) = fs { File(it, "a.txt").writeText("one\ntwo\nthree\n") }
        val hash = FileSystem.hash(File(root, "a.txt").readBytes())
        assertEquals("[a.txt | 3 lines | hash:$hash]\n     1\tone\n     2\ttwo\n     3\tthree\n", fs.readFile("a.txt"))
        assertEquals("[a.txt | 3 lines | hash:$hash]\n     2\ttwo\n... (1 more lines. Use offset=2 to continue reading.)\n", fs.readFile("a.txt", offset = 1, limit = 1))
        File(root, "bin").writeBytes(byteArrayOf(1, 0, 2))
        assertEquals("[Binary file: 3 bytes. Use a binary-aware tool to inspect.]", fs.readFile("bin"))
        assertEquals(12, hash.length)
    }

    @Test
    fun writeAndEdit_withOptimisticConcurrency() = runBlocking<Unit> {
        val (fs, root) = fs()
        assertTrue(fs.writeFile("notes.md", "hello\nworld\n").startsWith("Wrote 12 chars (2 lines) to notes.md. [hash:"))
        val hash = FileSystem.hash(File(root, "notes.md").readBytes())
        assertTrue(fs.editFile("notes.md", listOf("world" to "there"), expectedHash = hash).startsWith("Edited notes.md. [hash:"))
        assertEquals("hello\nthere\n", File(root, "notes.md").readText())
        // A stale hash is refused; an ambiguous or missing old_text is an error; batches apply in order.
        assertThrows(IllegalStateException::class.java) { runBlocking { fs.writeFile("notes.md", "x", expectedHash = hash) } }
        fs.writeFile("dup.txt", "a a")
        assertThrows(IllegalArgumentException::class.java) { runBlocking { fs.editFile("dup.txt", listOf("a" to "b")) } }
        fs.editFile("notes.md", listOf("hello" to "hi", "hi\nthere" to "hi there"))
        assertEquals("hi there\n", File(root, "notes.md").readText())
        assertThrows(java.io.FileNotFoundException::class.java) { runBlocking { fs.writeFile("missing/dir/x.txt", "x") } }
    }

    @Test
    fun sandbox_containmentSymlinksAndProtectedFiles() = runBlocking<Unit> {
        val outside = tmp.newFile("secret.txt").apply { writeText("top secret") }
        val (fs, root) = fs {
            File(it, ".env").writeText("KEY=1")
            Files.createSymbolicLink(File(it, "escape").toPath(), outside.toPath())
        }
        assertThrows(SecurityException::class.java) { runBlocking { fs.readFile("../secret.txt") } }
        assertThrows(SecurityException::class.java) { runBlocking { fs.readFile("escape") } }
        assertThrows(SecurityException::class.java) { runBlocking { fs.writeFile(".env", "KEY=2") } }
        assertTrue(fs.readFile(".env").contains("KEY=1")) // protected files stay readable
        assertEquals("KEY=1", File(root, ".env").readText())
    }

    @Test
    fun listSearchFind_skipDotfiles() = runBlocking<Unit> {
        val (fs, _) = fs {
            File(it, "src/app").mkdirs()
            File(it, "src/app/Main.kt").writeText("fun main() = println(\"hi\")\n")
            File(it, "src/README.md").writeText("# Readme\nfun fact\n")
            File(it, ".git").mkdirs(); File(it, ".git/config").writeText("fun")
        }
        assertEquals("src/", fs.listDirectory())
        assertEquals("src/README.md  (18 bytes)\nsrc/app/", fs.listDirectory("src"))
        assertEquals("src/README.md:2:fun fact\nsrc/app/Main.kt:1:fun main() = println(\"hi\")", fs.searchFiles("^fun"))
        assertEquals("src/app/Main.kt:1:fun main() = println(\"hi\")", fs.searchFiles("fun", includeGlob = "**/*.kt"))
        assertEquals("src/app/Main.kt", fs.findFiles("**/*.kt"))
        assertEquals("src/README.md", fs.findFiles("*.md"))
        assertEquals("No matches found.", fs.searchFiles("nothing"))
        assertTrue(fs.fileInfo("src/README.md").contains("lines: 2"))
    }

    @Test
    fun tools_matchHarnessNames_andAskBeforeChanges() = runBlocking<Unit> {
        val (fs, root) = fs()
        val tools = fs.tools().associateBy { it.name }
        assertEquals(setOf("read_file", "write_file", "edit_file", "list_directory", "search_files", "find_files", "create_directory", "file_info"), tools.keys)
        assertTrue(tools.getValue("write_file").requiresApproval)
        assertFalse(tools.getValue("read_file").requiresApproval)
        tools.getValue("create_directory").execute(buildJsonObject { put("path", "a/b") })
        assertTrue(File(root, "a/b").isDirectory)
        val edit = tools.getValue("edit_file")
        tools.getValue("write_file").execute(buildJsonObject { put("path", "a/x.txt"); put("content", "1 2") })
        edit.execute(buildJsonObject {
            put("path", "a/x.txt")
            put("replacements", JsonArray(listOf(buildJsonObject { put("old_text", "1"); put("new_text", "one") }, buildJsonObject { put("old_text", "2"); put("new_text", "two") })))
        })
        assertEquals("one two", File(root, "a/x.txt").readText())
        val readOnly = FileSystem(root, readOnly = true).tools().map { it.name }
        assertFalse("write_file" in readOnly || "edit_file" in readOnly || "create_directory" in readOnly)
    }
}
