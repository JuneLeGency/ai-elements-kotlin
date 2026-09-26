package dev.ai.elements.core.skills

import android.content.res.AssetManager
import dev.ai.elements.core.agent.AgentTool
import dev.ai.elements.core.agent.Capability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Where skills come from: the immediate subfolders holding a `SKILL.md`. */
fun interface SkillLibrary {
    /** Loaded skills and, separately, the folders that failed to load (with why). */
    suspend fun load(): SkillScan

    companion object {
        /** Skills in subfolders of [dir] on the file system. */
        fun directory(dir: File) = SkillLibrary {
            withContext(Dispatchers.IO) {
                scan(dir.listFiles().orEmpty().filter { it.isDirectory }.sortedBy { it.name }.mapNotNull { folder ->
                    File(folder, SKILL_FILE).takeIf { it.isFile }?.let { Triple(folder.name, folder.path) { it.readText() } }
                })
            }
        }

        /** Skills bundled in the APK under `assets/[path]`. */
        fun assets(assets: AssetManager, path: String) = SkillLibrary {
            withContext(Dispatchers.IO) {
                val folders = assets.list(path).orEmpty().sorted()
                scan(folders.mapNotNull { name ->
                    val file = "$path/$name/$SKILL_FILE"
                    if (assets.list("$path/$name").orEmpty().contains(SKILL_FILE)) {
                        Triple(name, "asset://$path/$name") { assets.open(file).bufferedReader().use { it.readText() } }
                    } else null
                })
            }
        }

        private fun scan(entries: List<Triple<String, String, () -> String>>): SkillScan {
            val skills = mutableListOf<Skill>()
            val errors = mutableListOf<String>()
            entries.forEach { (folder, location, read) ->
                runCatching { Skill.parse(read(), folder, location) }
                    .onSuccess { skills += it }
                    .onFailure { errors += it.message ?: "$location: unreadable" }
            }
            return SkillScan(skills, errors)
        }

        const val SKILL_FILE = "SKILL.md"
    }
}

data class SkillScan(val skills: List<Skill>, val errors: List<String> = emptyList())

/**
 * Gives the agent [skills] on demand, the way Pydantic AI (Harness) loads
 * deferred capabilities: the model first sees only each skill's name and
 * description; calling `load_capability(id)` returns the skill's instructions
 * under a `# Skill: <name>` heading. Bundled files are not read and scripts
 * never run.
 *
 * Only load skills from sources you trust — a skill body becomes model
 * instructions.
 */
class Skills(val skills: List<Skill>) : Capability {

    init {
        val duplicate = skills.groupBy { it.name }.filterValues { it.size > 1 }.keys
        require(duplicate.isEmpty()) { "Duplicate skill names: $duplicate" }
    }

    private val byName = skills.associateBy { it.name }

    // Byte-stable for a given set of skills, so the prompt cache survives loads.
    override val instructions: String?
        get() = if (skills.isEmpty()) null else
            "The following capabilities are deferred and can be loaded using the `$LOAD_CAPABILITY` tool. " +
                "A capability's tools stay hidden until it is loaded:\n" +
                skills.joinToString("\n") { "- ${it.name}: ${it.description}" }

    override suspend fun tools(): List<AgentTool> = if (skills.isEmpty()) emptyList() else listOf(loader)

    private val loader = object : AgentTool {
        override val name = LOAD_CAPABILITY
        override val description = "Load a deferred capability by id, making its instructions available."
        override val parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("id") {
                    put("type", "string")
                    put("description", "The id of the capability to load.")
                }
            }
            put("required", JsonArray(listOf(JsonPrimitive("id"))))
        }

        override fun titleFor(arguments: JsonObject): String? = arguments.id()

        override suspend fun execute(arguments: JsonObject): String {
            val id = arguments.id()?.let(Skill::normalizeName).orEmpty()
            val skill = byName[id]
                ?: throw IllegalArgumentException("Unknown capability '$id'. Available: ${byName.keys.sorted().joinToString()}.")
            return "# Skill: ${skill.name}\n\n${skill.instructions}"
        }

        private fun JsonObject.id() = (this["id"] as? JsonPrimitive)?.contentOrNull
    }

    companion object {
        /** Pydantic AI's deferred-capability loader tool; UIs render calls to it as a skill. */
        const val LOAD_CAPABILITY = "load_capability"

        suspend fun from(vararg libraries: SkillLibrary): Skills =
            Skills(libraries.flatMap { it.load().skills }.distinctBy { it.name })
    }
}

/** Installs skill packages into a [SkillLibrary.directory] folder. */
object SkillInstaller {
    /**
     * Unpack a skill `.zip` (either `name/SKILL.md…` or `SKILL.md…` at its root)
     * into [libraryDir], replacing a skill of the same name. Entries escaping
     * the target (zip slip) or larger than [maxBytes] in total are rejected.
     *
     * @return the installed skill.
     */
    suspend fun installZip(zip: InputStream, libraryDir: File, maxBytes: Long = 20L shl 20): Skill = withContext(Dispatchers.IO) {
        val staging = File(libraryDir, ".staging-${System.nanoTime()}").apply { mkdirs() }
        try {
            var total = 0L
            ZipInputStream(zip).use { input ->
                generateSequence { input.nextEntry }.forEach { entry ->
                    val target = File(staging, entry.name).canonicalFile
                    require(target.path.startsWith(staging.canonicalPath + File.separator)) { "Unsafe entry ${entry.name}" }
                    if (entry.isDirectory) { target.mkdirs(); return@forEach }
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            total += n
                            require(total <= maxBytes) { "Skill package is larger than ${maxBytes shr 20} MB" }
                            out.write(buffer, 0, n)
                        }
                    }
                }
            }
            val root = staging.takeIf { File(it, SkillLibrary.SKILL_FILE).isFile }
                ?: staging.listFiles().orEmpty().singleOrNull { it.isDirectory && File(it, SkillLibrary.SKILL_FILE).isFile }
                ?: throw SkillFormatException("No SKILL.md in the package")
            val markdown = File(root, SkillLibrary.SKILL_FILE).readText()
            val folderName = if (root == staging) {
                Frontmatter.split(markdown)?.let { Frontmatter.parse(it.first)["name"] }?.let(Skill::normalizeName)
                    ?: throw SkillFormatException("A root-level SKILL.md needs a name")
            } else root.name
            val skill = Skill.parse(markdown, folderName)
            val destination = File(libraryDir, skill.name)
            destination.deleteRecursively()
            check(root.renameTo(destination)) { "Could not install ${skill.name}" }
            skill.copy(location = destination.path)
        } finally {
            staging.deleteRecursively()
        }
    }
}
