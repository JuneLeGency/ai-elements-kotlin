package dev.ai.elements.harness.filesystem

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileNotFoundException

/** A [FolderNode] over a Storage Access Framework document (a folder the user picked, or a file in it). */
class DocumentFolder(private val context: Context, private val document: DocumentFile) : FolderNode {
    override val name: String get() = document.name.orEmpty()
    override val isDirectory: Boolean get() = document.isDirectory
    override val size: Long get() = document.length()

    override fun children(): List<FolderNode> = document.listFiles().map { DocumentFolder(context, it) }
    override fun child(name: String): FolderNode? = document.findFile(name)?.let { DocumentFolder(context, it) }

    override fun read(): ByteArray =
        (context.contentResolver.openInputStream(document.uri) ?: throw FileNotFoundException("Cannot open $name")).use { it.readBytes() }

    override fun write(bytes: ByteArray) {
        // "wt" truncates; some providers only support "w".
        val out = runCatching { context.contentResolver.openOutputStream(document.uri, "wt") }.getOrNull()
            ?: context.contentResolver.openOutputStream(document.uri, "w")
            ?: throw FileNotFoundException("Cannot write $name")
        out.use { it.write(bytes) }
    }

    override fun createFile(name: String): FolderNode {
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
        return DocumentFolder(context, document.createFile(mime, name) ?: throw FileNotFoundException("The folder does not allow creating $name"))
    }

    override fun createDirectory(name: String): FolderNode =
        DocumentFolder(context, document.createDirectory(name) ?: throw FileNotFoundException("The folder does not allow creating $name"))
}

/** A folder the user shared with the agent. */
data class SharedFolder(val name: String, val uri: String, val writable: Boolean)

/**
 * Folders the user shares with the agent through the Storage Access Framework,
 * mounted into [FileSystem] at `/mnt/<name>`:
 *
 * ```kotlin
 * val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(folders::add) }
 * val files = FileSystem(workspace, mounts = folders::mounts)
 * ```
 *
 * Access survives restarts (persisted URI permissions) until [remove]d.
 */
class SharedFolders(context: Context) {
    private val context = context.applicationContext
    private val prefs = this.context.getSharedPreferences("ai_elements_shared_folders", Context.MODE_PRIVATE)
    private val _folders = MutableStateFlow(load())
    val folders: StateFlow<List<SharedFolder>> = _folders.asStateFlow()

    /** Keep access to the folder at [uri] (from `OpenDocumentTree`) and mount it; returns its mount. */
    fun add(uri: Uri, writable: Boolean = true): SharedFolder {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or (if (writable) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        context.contentResolver.takePersistableUriPermission(uri, flags)
        _folders.value.firstOrNull { it.uri == uri.toString() }?.let { existing ->
            val updated = existing.copy(writable = writable)
            update(_folders.value.map { if (it.uri == existing.uri) updated else it })
            return updated
        }
        val base = Mount.nameFrom(DocumentFile.fromTreeUri(context, uri)?.name ?: "folder")
        val taken = _folders.value.map { it.name }.toSet()
        val name = generateSequence(1) { it + 1 }.map { if (it == 1) base else "$base-$it" }.first { it !in taken }
        return SharedFolder(name, uri.toString(), writable).also { update(_folders.value + it) }
    }

    /** Unmount [name] and give up access to it. */
    fun remove(name: String) {
        val folder = _folders.value.firstOrNull { it.name == name } ?: return
        runCatching {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(folder.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION or (if (folder.writable) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0))
        }
        update(_folders.value - folder)
    }

    /** The folders still accessible, as [Mount]s for [FileSystem]. */
    fun mounts(): List<Mount> {
        val granted = context.contentResolver.persistedUriPermissions.associateBy { it.uri.toString() }
        return _folders.value.mapNotNull { folder ->
            val permission = granted[folder.uri]?.takeIf { it.isReadPermission } ?: return@mapNotNull null
            val root = DocumentFile.fromTreeUri(context, Uri.parse(folder.uri)) ?: return@mapNotNull null
            Mount(folder.name, DocumentFolder(context, root), writable = folder.writable && permission.isWritePermission)
        }
    }

    private fun update(list: List<SharedFolder>) {
        _folders.value = list
        val json = JSONArray(list.map { JSONObject().put("name", it.name).put("uri", it.uri).put("writable", it.writable) })
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    private fun load(): List<SharedFolder> = runCatching {
        val array = JSONArray(prefs.getString(KEY, null) ?: return emptyList())
        (0 until array.length()).map { i -> array.getJSONObject(i).let { SharedFolder(it.getString("name"), it.getString("uri"), it.optBoolean("writable", true)) } }
    }.getOrDefault(emptyList())

    private companion object {
        const val KEY = "folders"
    }
}
