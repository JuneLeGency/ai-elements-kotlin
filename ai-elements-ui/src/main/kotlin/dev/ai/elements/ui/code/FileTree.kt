package dev.ai.elements.ui.code

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ai.elements.ui.theme.isDark

/**
 * A file or folder. [path] identifies it (selection, expansion); [badge] is a
 * short status such as `M`, `A`, `+12`.
 */
@Immutable
data class FileNode(
    val name: String,
    val path: String = name,
    val children: List<FileNode>? = null,
    val badge: String? = null,
) {
    val isFolder: Boolean get() = children != null
}

/**
 * A project tree (AI Elements `<FileTree>`): folders expand in place, files
 * get type icons, the [selectedPath] is highlighted. [expanded] paths start
 * open (default: top-level folders).
 */
@Composable
fun FileTree(
    nodes: List<FileNode>,
    modifier: Modifier = Modifier,
    selectedPath: String? = null,
    expanded: Set<String> = nodes.filter { it.isFolder }.map { it.path }.toSet(),
    onSelect: ((FileNode) -> Unit)? = null,
) {
    val open = remember { mutableStateMapOf<String, Boolean>().apply { expanded.forEach { put(it, true) } } }
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("file-tree")) {
        flatten(nodes, 0) { open[it] == true }.forEach { row ->
            val node = row.node
            val isOpen = open[node.path] == true
            val selected = node.path == selectedPath
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, MaterialTheme.shapes.small)
                    .clickable(role = Role.Button) {
                        if (node.isFolder) open[node.path] = !isOpen
                        onSelect?.invoke(node)
                    }
                    .semantics {
                        this.selected = selected
                        if (node.isFolder) stateDescription = if (isOpen) "expanded" else "collapsed"
                    }
                    .heightIn(min = 40.dp)
                    .padding(start = 8.dp + 16.dp * row.depth, end = 8.dp),
            ) {
                Icon(
                    if (node.isFolder) (if (isOpen) Icons.Outlined.FolderOpen else Icons.Outlined.Folder) else fileIcon(node.name),
                    null,
                    Modifier.size(18.dp),
                    tint = if (node.isFolder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(node.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                node.badge?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = badgeColor(it))
                }
            }
        }
    }
}

private data class TreeRow(val node: FileNode, val depth: Int)

/** Visible rows in display order: folders first, then files, alphabetically. */
private fun flatten(nodes: List<FileNode>, depth: Int, isOpen: (String) -> Boolean): List<TreeRow> =
    nodes.sortedWith(compareBy({ !it.isFolder }, { it.name.lowercase() })).flatMap { node ->
        listOf(TreeRow(node, depth)) + if (node.isFolder && isOpen(node.path)) flatten(node.children!!, depth + 1, isOpen) else emptyList()
    }

@Composable
private fun badgeColor(badge: String) = when (badge.firstOrNull()) {
    'A', '+' -> if (MaterialTheme.isDark) Color(0xFF7FD99A) else Color(0xFF1E7B3A)
    'D', '-' -> MaterialTheme.colorScheme.error
    'M' -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun fileIcon(name: String): ImageVector = when (name.substringAfterLast('.', "").lowercase()) {
    "kt", "kts", "java", "js", "ts", "tsx", "jsx", "py", "rs", "go", "swift", "c", "cpp", "h", "rb", "sh" -> Icons.Outlined.Code
    "json", "yaml", "yml", "toml", "xml" -> Icons.Outlined.DataObject
    "md", "txt", "rst" -> Icons.Outlined.Description
    "png", "jpg", "jpeg", "gif", "webp", "svg" -> Icons.Outlined.Image
    "gradle", "properties", "env", "gitignore", "lock" -> Icons.Outlined.Settings
    else -> Icons.Outlined.InsertDriveFile
}
