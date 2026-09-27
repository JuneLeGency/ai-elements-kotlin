package dev.ai.elements.harness.memory

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MemoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun memory(dir: File = tmp.newFolder("mem")) = Memory(FileMemoryStore(dir)) to dir

    private suspend fun Memory.call(name: String, args: JsonObject) = tools().single { it.name == name }.execute(args)

    @Test
    fun writeAppendReplaceRemove_likeHarness() = runBlocking<Unit> {
        val (memory, dir) = memory()
        val created = Json.parseToJsonElement(memory.call("write_memory", buildJsonObject { put("content", "- likes tea") })).jsonObject
        assertEquals("created", created["status"]!!.jsonPrimitive.content)
        assertEquals("MEMORY.md", created["file"]!!.jsonPrimitive.content)
        memory.call("write_memory", buildJsonObject { put("content", "- lives in Hangzhou") })
        assertEquals("- likes tea\n- lives in Hangzhou\n", File(dir, "MEMORY.md").readText())
        memory.call("write_memory", buildJsonObject { put("content", "- likes coffee"); put("old_text", "- likes tea") })
        memory.call("write_memory", buildJsonObject { put("content", ""); put("old_text", "- lives in Hangzhou\n") })
        assertEquals("- likes coffee\n", File(dir, "MEMORY.md").readText())
        assertThrows(IllegalArgumentException::class.java) { runBlocking { memory.call("write_memory", buildJsonObject { put("content", "x"); put("old_text", "absent") }) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { memory.call("write_memory", buildJsonObject { put("content", "x"); put("file", "../evil") }) } }
    }

    @Test
    fun topicFiles_readSearchDelete_mainIsProtected() = runBlocking<Unit> {
        val (memory, _) = memory()
        memory.call("write_memory", buildJsonObject { put("content", "Use pnpm, not npm."); put("file", "tooling") })
        assertEquals("Use pnpm, not npm.\n", memory.call("read_memory", buildJsonObject { put("file", "tooling.md") }))
        val found = Json.parseToJsonElement(memory.call("search_memory", buildJsonObject { put("query", "pnpm") })).jsonObject
        assertEquals("tooling.md", found["matches"]!!.jsonArray.single().jsonObject["file"]!!.jsonPrimitive.content)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { memory.call("delete_memory", buildJsonObject { put("file", "MEMORY.md") }) } }
        val deleted = Json.parseToJsonElement(memory.call("delete_memory", buildJsonObject { put("file", "tooling") })).jsonObject
        assertEquals("deleted", deleted["status"]!!.jsonPrimitive.content)
    }

    @Test
    fun context_injectsTheNotebookInsideMemoryMarkers() = runBlocking<Unit> {
        val (memory, _) = memory()
        assertNull(memory.context())
        memory.call("write_memory", buildJsonObject { put("content", "- prefers Chinese answers") })
        memory.call("write_memory", buildJsonObject { put("content", "notes"); put("file", "project") })
        assertEquals("<memory>\n### MEMORY.md\n\n- prefers Chinese answers\n\n### Other memory files\n\n- project.md\n</memory>", memory.context())
        assertTrue(memory.instructions.startsWith("This is your persistent memory"))
        assertNull(Memory(FileMemoryStore(tmp.newFolder("x")), injectMemory = false).context())
    }

    @Test
    fun store_compareAndSwap() = runBlocking<Unit> {
        val store = FileMemoryStore(tmp.newFolder("cas"))
        val v1 = store.write("a.md", "one", null)
        assertThrows(MemoryConflictException::class.java) { runBlocking { store.write("a.md", "two", null) } }
        store.write("a.md", "two", v1)
        assertThrows(MemoryConflictException::class.java) { runBlocking { store.write("a.md", "three", v1) } }
    }
}
