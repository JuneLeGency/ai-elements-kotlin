package dev.ai.elements.ui.code

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.Commit
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ai.elements.ui.R
import dev.ai.elements.ui.theme.AiSize
import dev.ai.elements.ui.theme.AiType
import dev.ai.elements.ui.theme.compactIconButton
import dev.ai.elements.ui.theme.isDark
import java.text.DateFormat
import java.util.Date

@Composable
private fun addedColor() = if (MaterialTheme.isDark) Color(0xFF7FD99A) else Color(0xFF1E7B3A)

// ---------------------------------------------------------------- Snippet

/**
 * A one-line command or value to copy (AI Elements `<Snippet>`), e.g.
 * `npm i ai`. [prefix] (like `$`) is shown but not copied.
 */
@Composable
fun Snippet(text: String, modifier: Modifier = Modifier, prefix: String? = null) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth().testTag("snippet")) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp, end = 2.dp)) {
            prefix?.let { Text("$it ", style = AiType.code, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            SelectionContainer(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                Text(text, style = AiType.code, softWrap = false)
            }
            CopyButton({ text })
        }
    }
}

// ---------------------------------------------------------------- Commit

enum class FileChange(val letter: String) { ADDED("A"), MODIFIED("M"), DELETED("D"), RENAMED("R") }

@Immutable
data class CommitFile(val path: String, val change: FileChange = FileChange.MODIFIED, val additions: Int = 0, val deletions: Int = 0)

/**
 * A git commit (AI Elements `<Commit>`): short hash (copyable), subject and
 * body, author and time, then changed files with +/- counts.
 */
@Composable
fun Commit(
    hash: String,
    message: String,
    modifier: Modifier = Modifier,
    author: String? = null,
    timestampMs: Long? = null,
    files: List<CommitFile> = emptyList(),
) {
    val subject = message.lineSequence().first()
    val body = message.lines().drop(1).joinToString("\n").trim()
    val added = addedColor()
    ElementCard(
        title = subject,
        subtitle = listOfNotNull(author, timestampMs?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)) }).joinToString(" · ").ifEmpty { null },
        icon = Icons.Outlined.Commit,
        modifier = modifier.testTag("commit"),
        actions = {
            Text(hash.take(7), style = AiType.code, color = MaterialTheme.colorScheme.primary)
            CopyButton({ hash })
        },
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            if (body.isNotEmpty()) {
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            if (files.isNotEmpty()) {
                Text(
                    pluralStringResource(R.plurals.ai_files_changed, files.size, files.size) +
                        "  +${files.sumOf { it.additions }} −${files.sumOf { it.deletions }}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                files.forEach { f ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(horizontal = 16.dp),
                    ) {
                        Text(
                            f.change.letter, style = AiType.code,
                            color = when (f.change) {
                                FileChange.ADDED -> added
                                FileChange.DELETED -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.tertiary
                            },
                        )
                        Text(f.path, style = AiType.code, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.StartEllipsis)
                        if (f.additions > 0) Text("+${f.additions}", style = MaterialTheme.typography.labelSmall, color = added)
                        if (f.deletions > 0) Text("−${f.deletions}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- SchemaDisplay

@Immutable
data class SchemaParameter(
    val name: String,
    val type: String,
    val required: Boolean = false,
    val description: String? = null,
    /** Where it goes: path, query, header, body. */
    val location: String? = null,
)

/**
 * An API endpoint (AI Elements `<SchemaDisplay>`): method and path, what it
 * does, its parameters, and example request / response bodies.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SchemaDisplay(
    method: String,
    path: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    parameters: List<SchemaParameter> = emptyList(),
    requestBody: String? = null,
    responseBody: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (method.uppercase()) {
        "GET" -> colors.secondaryContainer to colors.onSecondaryContainer
        "POST" -> colors.primaryContainer to colors.onPrimaryContainer
        "DELETE" -> colors.errorContainer to colors.onErrorContainer
        else -> colors.tertiaryContainer to colors.onTertiaryContainer
    }
    ElementCard(
        title = path,
        subtitle = description,
        icon = Icons.Outlined.Api,
        modifier = modifier.testTag("schema-display"),
        actions = { Pill(method.uppercase(), container, content, Modifier.padding(end = 12.dp)) },
    ) {
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (parameters.isNotEmpty()) {
                SectionLabel(stringResource(R.string.ai_parameters))
                parameters.forEach { p ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                            Text(p.name, style = AiType.code, color = colors.onSurface)
                            Text(p.type, style = AiType.code, color = colors.primary)
                            p.location?.let { Pill(it, colors.surfaceContainerHighest, colors.onSurfaceVariant) }
                            if (p.required) Text(stringResource(R.string.ai_required), style = MaterialTheme.typography.labelSmall, color = colors.error)
                        }
                        p.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
                    }
                }
            }
            requestBody?.let {
                SectionLabel(stringResource(R.string.ai_request_body))
                CodeSample(it)
            }
            responseBody?.let {
                SectionLabel(stringResource(R.string.ai_response))
                CodeSample(it)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
}

@Composable
private fun CodeSample(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        SelectionContainer {
            Text(text, style = AiType.code, softWrap = false, modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp))
        }
    }
}

// ---------------------------------------------------------------- PackageInfo

enum class PackageChange { MAJOR, MINOR, PATCH, ADDED, REMOVED }

/**
 * A dependency change (AI Elements `<PackageInfo>`): name, old → new
 * version, how big the change is, and optionally what it does.
 */
@Composable
fun PackageInfo(
    name: String,
    modifier: Modifier = Modifier,
    fromVersion: String? = null,
    toVersion: String? = null,
    change: PackageChange? = null,
    description: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    ElementCard(
        title = name,
        subtitle = description,
        icon = Icons.Outlined.Inventory2,
        modifier = modifier.testTag("package-info"),
        actions = {
            change?.let {
                val (container, content) = when (it) {
                    PackageChange.MAJOR, PackageChange.REMOVED -> colors.errorContainer to colors.onErrorContainer
                    PackageChange.MINOR, PackageChange.ADDED -> colors.primaryContainer to colors.onPrimaryContainer
                    PackageChange.PATCH -> colors.secondaryContainer to colors.onSecondaryContainer
                }
                Pill(stringResource(it.label), container, content, Modifier.padding(end = 12.dp))
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
            fromVersion?.let { Text(it, style = AiType.code, color = colors.onSurfaceVariant) }
            if (fromVersion != null && toVersion != null) Text("→", color = colors.onSurfaceVariant)
            toVersion?.let { Text(it, style = AiType.code, color = colors.primary) }
        }
    }
}

private val PackageChange.label: Int
    get() = when (this) {
        PackageChange.MAJOR -> R.string.ai_change_major
        PackageChange.MINOR -> R.string.ai_change_minor
        PackageChange.PATCH -> R.string.ai_change_patch
        PackageChange.ADDED -> R.string.ai_change_added
        PackageChange.REMOVED -> R.string.ai_change_removed
    }

// ---------------------------------------------------------------- EnvironmentVariables

@Immutable
data class EnvironmentVariable(val name: String, val value: String, val secret: Boolean = true)

/**
 * Environment variables (AI Elements `<EnvironmentVariables>`): secret values
 * stay masked until revealed one by one; each can be copied.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EnvironmentVariables(variables: List<EnvironmentVariable>, modifier: Modifier = Modifier, title: String = stringResource(R.string.ai_environment_variables)) {
    val revealed = remember { mutableStateMapOf<String, Boolean>() }
    ElementCard(title = title, icon = Icons.Outlined.Key, modifier = modifier.testTag("environment-variables")) {
        Column(Modifier.padding(vertical = 4.dp)) {
            variables.forEachIndexed { i, v ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                val show = !v.secret || revealed[v.name] == true
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(v.name, style = AiType.code, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            if (show) v.value else "•".repeat(v.value.length.coerceIn(8, 24)),
                            style = AiType.code, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (v.secret) {
                        IconButton(onClick = { revealed[v.name] = !show }, shapes = IconButtonDefaults.shapes(), modifier = Modifier.compactIconButton()) {
                            Icon(
                                if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                stringResource(if (show) R.string.ai_hide_value else R.string.ai_show_value),
                                Modifier.size(AiSize.compactIcon),
                            )
                        }
                    }
                    CopyButton({ v.value })
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Sandbox

/** One tab of a [Sandbox]. */
@Immutable
data class SandboxTab(val label: String, val content: @Composable () -> Unit)

/**
 * A run environment with tabs (AI Elements `<Sandbox>`) — e.g. Code,
 * Preview, Console — sharing one card.
 */
@Composable
fun Sandbox(title: String, tabs: List<SandboxTab>, modifier: Modifier = Modifier, subtitle: String? = null) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    ElementCard(title = title, subtitle = subtitle, modifier = modifier.testTag("sandbox")) {
        Column {
            PrimaryScrollableTabRow(selectedTabIndex = selected.coerceIn(0, (tabs.size - 1).coerceAtLeast(0)), edgePadding = 8.dp, containerColor = Color.Transparent) {
                tabs.forEachIndexed { i, tab ->
                    Tab(selected = i == selected, onClick = { selected = i }, text = { Text(tab.label) })
                }
            }
            Box(Modifier.fillMaxWidth().padding(12.dp)) { tabs.getOrNull(selected)?.content?.invoke() }
        }
    }
}
