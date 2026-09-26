package dev.ai.elements.demo.data

import android.content.Context
import android.net.Uri
import dev.ai.elements.core.skills.Skill
import dev.ai.elements.core.skills.SkillInstaller
import dev.ai.elements.core.skills.SkillLibrary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** A skill and where it came from. */
data class SkillEntry(val skill: Skill, val bundled: Boolean)

/**
 * Agent Skills available to the chat: those bundled in the APK (`assets/skills`,
 * the repository's `/skills`, shared with the reference server) and those the
 * user installed from a `.zip` (in app storage).
 */
class SkillsRepository(private val context: Context) {
    private val installedDir = File(context.filesDir, "skills").apply { mkdirs() }
    private val bundled = SkillLibrary.assets(context.assets, "skills")
    private val installed = SkillLibrary.directory(installedDir)

    private val _skills = MutableStateFlow<List<SkillEntry>>(emptyList())
    val skills: StateFlow<List<SkillEntry>> = _skills.asStateFlow()

    private val _errors = MutableStateFlow<List<String>>(emptyList())
    /** Skill folders that failed to load, with why. */
    val errors: StateFlow<List<String>> = _errors.asStateFlow()

    suspend fun refresh() {
        val fromApk = bundled.load()
        val fromUser = installed.load()
        val userNames = fromUser.skills.map { it.name }.toSet()
        // An installed skill overrides a bundled one of the same name.
        _skills.value = fromApk.skills.filter { it.name !in userNames }.map { SkillEntry(it, bundled = true) } +
            fromUser.skills.map { SkillEntry(it, bundled = false) }
        _errors.value = fromApk.errors + fromUser.errors
    }

    /** Install a skill package picked by the user (Storage Access Framework). */
    suspend fun install(uri: Uri): Skill {
        val skill = context.contentResolver.openInputStream(uri)!!.use { SkillInstaller.installZip(it, installedDir) }
        refresh()
        return skill
    }

    suspend fun uninstall(name: String) {
        File(installedDir, name).takeIf { it.parentFile == installedDir }?.deleteRecursively()
        refresh()
    }
}
