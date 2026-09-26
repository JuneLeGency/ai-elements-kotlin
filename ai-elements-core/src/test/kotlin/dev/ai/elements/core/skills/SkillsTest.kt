package dev.ai.elements.core.skills

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SkillsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val md = """
        ---
        name: pdf-tools
        description: >
          Extract text from PDFs
          and fill forms.
        license: Apache-2.0
        metadata:
          author: someone
        allowed-tools:
          - read_file
          - run_command
        ---
        # PDF

        Use the tools.
    """.trimIndent()

    @Test
    fun parse_frontmatterAndBody() {
        val skill = Skill.parse(md, "pdf-tools")
        assertEquals("pdf-tools", skill.name)
        assertEquals("Extract text from PDFs and fill forms.", skill.description)
        assertEquals("Apache-2.0", skill.license)
        assertEquals("someone", skill.frontmatter["metadata.author"])
        assertEquals("read_file, run_command", skill.frontmatter["allowed-tools"])
        assertEquals("# PDF\n\nUse the tools.", skill.instructions)
    }

    @Test
    fun parse_rulesMatchPydanticAiHarness() {
        // name defaults to the folder; NFKC-normalized; must match the folder.
        assertEquals("notes", Skill.parse("---\ndescription: d\n---\nbody", "notes").name)
        assertEquals("ｃａｆｅ", "ｃａｆｅ") // full-width input …
        assertEquals("cafe", Skill.parse("---\nname: ｃａｆｅ\ndescription: d\n---\n", "cafe").name) // … normalizes to ASCII
        assertThrows(SkillFormatException::class.java) { Skill.parse("---\nname: other\ndescription: d\n---\n", "notes") }
        assertThrows(SkillFormatException::class.java) { Skill.parse("---\nname: Bad_Name\ndescription: d\n---\n", "Bad_Name") }
        assertThrows(SkillFormatException::class.java) { Skill.parse("---\nname: a--b\ndescription: d\n---\n", "a--b") }
        assertThrows(SkillFormatException::class.java) { Skill.parse("---\nname: notes\n---\n", "notes") }
        assertThrows(SkillFormatException::class.java) { Skill.parse("no frontmatter", "notes") }
    }

    @Test
    fun loadCapability_catalogAndInstructions() = runBlocking<Unit> {
        val skills = Skills(listOf(Skill.parse(md, "pdf-tools"), Skill.parse("---\ndescription: Notes\n---\nTake notes.", "notes")))
        assertEquals(
            "The following capabilities are deferred and can be loaded using the `load_capability` tool. " +
                "A capability's tools stay hidden until it is loaded:\n- pdf-tools: Extract text from PDFs and fill forms.\n- notes: Notes",
            skills.instructions,
        )
        val loader = skills.tools().single()
        assertEquals("load_capability", loader.name)
        assertEquals("# Skill: notes\n\nTake notes.", loader.execute(buildJsonObject { put("id", "notes") }))
        assertEquals("notes", loader.titleFor(buildJsonObject { put("id", "notes") }))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { loader.execute(buildJsonObject { put("id", "nope") }) } }
    }

    private fun zip(vararg entries: Pair<String, String>): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip -> entries.forEach { (name, text) -> zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() } }
    }.toByteArray()

    @Test
    fun install_zipWithFolderOrRootSkill_andDirectoryLibrary() = runBlocking<Unit> {
        val library = tmp.newFolder("skills")
        SkillInstaller.installZip(zip("pdf-tools/SKILL.md" to md, "pdf-tools/references/a.md" to "ref").inputStream(), library)
        SkillInstaller.installZip(zip("SKILL.md" to "---\nname: notes\ndescription: Notes\n---\nx").inputStream(), library)
        assertTrue(File(library, "pdf-tools/references/a.md").isFile)
        val scan = SkillLibrary.directory(library).load()
        assertEquals(listOf("notes", "pdf-tools"), scan.skills.map { it.name })
        assertTrue(scan.errors.isEmpty())
        assertTrue(library.listFiles()!!.none { it.name.startsWith(".staging") })
    }

    @Test
    fun install_rejectsZipSlip() {
        val library = tmp.newFolder("skills")
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { SkillInstaller.installZip(zip("../evil/SKILL.md" to md).inputStream(), library) }
        }
        assertTrue(!File(library.parentFile, "evil").exists())
    }
}
