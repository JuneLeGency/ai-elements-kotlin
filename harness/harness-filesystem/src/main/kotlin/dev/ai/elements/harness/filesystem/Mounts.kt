package dev.ai.elements.harness.filesystem

/**
 * A node of a folder tree that is not a local path, e.g. a folder the user shared
 * through the Storage Access Framework ([DocumentFolder]). [FileSystem] serves it
 * with the same tools as the workspace.
 */
interface FolderNode {
    val name: String
    val isDirectory: Boolean
    val size: Long

    fun children(): List<FolderNode>
    fun child(name: String): FolderNode? = children().firstOrNull { it.name == name }
    fun read(): ByteArray
    fun write(bytes: ByteArray)
    fun createFile(name: String): FolderNode
    fun createDirectory(name: String): FolderNode
}

/** A folder mounted into [FileSystem] at `/mnt/<name>`; [writable] false makes it read-only. */
data class Mount(val name: String, val root: FolderNode, val writable: Boolean = true) {
    init {
        require(NAME.matches(name)) { "Mount names use letters, digits, '.', '_' and '-': $name" }
    }

    companion object {
        /** Where mounts appear in the agent's paths. */
        const val ROOT = "/mnt"
        private val NAME = Regex("[A-Za-z0-9._-]{1,64}")

        /** [raw] (e.g. a folder's display name) made into a valid mount name. */
        fun nameFrom(raw: String): String =
            raw.replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-', '.').take(64).ifEmpty { "folder" }
    }
}
