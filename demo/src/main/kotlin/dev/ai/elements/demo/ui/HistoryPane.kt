package dev.ai.elements.demo.ui

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ai.elements.core.model.Role
import dev.ai.elements.demo.R
import dev.ai.elements.demo.data.Conversation
import dev.ai.elements.ui.theme.AiSize
import java.util.Calendar

/**
 * Conversation history: search, date groups (Today / Yesterday / Previous 7
 * days / Earlier) and dense two-line rows — title, a preview of the last reply
 * and when it happened — so a phone drawer shows ~10 chats instead of ~6.
 * Delete is on long-press (and as an accessibility action), not a button on
 * every row.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HistoryPane(
    conversations: List<Conversation>,
    currentId: String,
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Pinned under the list (e.g. Components and Settings on phones, as chat apps put them in the drawer). */
    footer: (@Composable () -> Unit)? = null,
) {
    val query = rememberTextFieldState()
    var searching by rememberSaveable { mutableStateOf(false) }
    val summaries = remember(conversations) { conversations.map(::summarize) }
    val filtered by remember(summaries) {
        derivedStateOf {
            val q = query.text.trim()
            if (q.isEmpty()) summaries
            else summaries.filter { it.title.contains(q, ignoreCase = true) || it.searchText.contains(q, ignoreCase = true) }
        }
    }
    val now = remember(conversations) { System.currentTimeMillis() }

    Column(modifier.fillMaxHeight().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Outlined.EditNote, null) },
                text = { Text(stringResource(R.string.new_chat)) },
                modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            if (conversations.size > 3 && !searching) {
                IconButton(onClick = { searching = true }, shapes = IconButtonDefaults.shapes(), modifier = Modifier.testTag("history-search-open")) {
                    Icon(Icons.Outlined.Search, stringResource(R.string.search_chats))
                }
            }
        }
        // Opened on demand: a field that is always there takes focus (and the keyboard) when the drawer opens.
        if (searching) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            SearchBarDefaults.InputField(
                state = query,
                onSearch = {},
                expanded = false,
                onExpandedChange = {},
                placeholder = { Text(stringResource(R.string.search_chats)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    IconButton(onClick = { query.edit { replace(0, length, "") }; searching = false }) {
                        Icon(Icons.Outlined.Close, stringResource(R.string.close_search))
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).focusRequester(focus).testTag("history-search"),
            )
        }
        if (filtered.isEmpty()) {
            Text(
                if (conversations.isEmpty()) stringResource(R.string.history_empty) else stringResource(R.string.history_no_match),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("history-list")) {
            filtered.groupBy { dateGroup(it.updatedAt, now) }.forEach { (group, rows) ->
                item(key = "header-$group", contentType = "header") {
                    Text(
                        stringResource(group.label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(rows, key = { it.id }, contentType = { "chat" }) { row ->
                    HistoryRow(
                        row = row,
                        selected = row.id == currentId,
                        time = formatTime(row.updatedAt, now, group),
                        onOpen = { onOpen(row.id) },
                        onDelete = { onDelete(row.id) },
                    )
                }
            }
        }
        footer?.let {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            it()
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(row: ChatSummary, selected: Boolean, time: String, onOpen: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val deleteLabel = stringResource(R.string.delete)
    val colors = MaterialTheme.colorScheme
    Box {
        Surface(
            color = if (selected) colors.secondaryContainer else Color.Transparent,
            contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AiSize.touchTarget)
                .semantics {
                    customActions = listOf(CustomAccessibilityAction(deleteLabel) { onDelete(); true })
                }
                .testTag("history-row"),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .combinedClickable(onClick = onOpen, onLongClick = { menu = true }, onLongClickLabel = stringResource(R.string.more_options))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.title.ifBlank { stringResource(R.string.new_chat) },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        time,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                if (row.preview.isNotEmpty()) {
                    Text(
                        row.preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(AiSize.compactIcon)) },
                onClick = { menu = false; onDelete() },
            )
        }
    }
}

/** What one history row shows; computed once per conversation list, not per frame. */
private data class ChatSummary(
    val id: String,
    val title: String,
    val preview: String,
    val searchText: String,
    val updatedAt: Long,
)

private fun summarize(c: Conversation): ChatSummary {
    val lastReply = c.messages.lastOrNull { it.role == Role.ASSISTANT && it.text.isNotBlank() }
        ?: c.messages.lastOrNull { it.text.isNotBlank() }
    return ChatSummary(
        id = c.id,
        title = c.title,
        preview = lastReply?.text?.let(::plainPreview).orEmpty(),
        searchText = c.messages.joinToString("\n") { it.text },
        updatedAt = c.updatedAt,
    )
}

/** First readable line of Markdown: no fences, headings, list or emphasis markers. */
private fun plainPreview(markdown: String): String =
    markdown.replace(Regex("```[\\s\\S]*?(```|$)"), " ")
        .lineSequence()
        .map { it.trim() }
        .filterNot { it.startsWith("#") } // headings repeat across replies; the first sentence says more
        .map { it.trimStart('>', '-', '*', '|', ' ').stripInlineMarkdown() }
        .firstOrNull { it.isNotBlank() && !it.all { ch -> ch == '-' || ch == '|' || ch == ':' || ch == ' ' } }
        .orEmpty()
        .take(120)

/**
 * Drop emphasis/code and citation markers but keep literal punctuation:
 * backslash-escaped characters (`\*`) and a spaced `*` (as in `2 * 3`) survive.
 */
private fun String.stripInlineMarkdown(): String {
    // Park escaped characters behind a private-use marker so the marker rules skip them.
    val parked = replace(Regex("""\\(\p{Punct})""")) { "\uE000" + it.groupValues[1] }
    return parked
        .replace(Regex("""\[\d+]|`|(?<!\uE000)\*\*|(?<!\uE000)__"""), "")
        .replace(Regex("""(?<=^|\s)(?<!\uE000)[*_](?=\S)|(?<=[^\s\uE000])[*_](?=\s|$|\p{Punct})"""), "")
        .replace("\uE000", "")
}

private enum class DateGroup(@StringRes val label: Int) {
    TODAY(R.string.group_today),
    YESTERDAY(R.string.group_yesterday),
    WEEK(R.string.group_week),
    EARLIER(R.string.group_earlier),
}

private fun startOfDay(millis: Long, daysAgo: Int = 0): Long = Calendar.getInstance().run {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    add(Calendar.DAY_OF_YEAR, -daysAgo)
    timeInMillis
}

private fun dateGroup(time: Long, now: Long): DateGroup = when {
    time >= startOfDay(now) -> DateGroup.TODAY
    time >= startOfDay(now, 1) -> DateGroup.YESTERDAY
    time >= startOfDay(now, 7) -> DateGroup.WEEK
    else -> DateGroup.EARLIER
}

@Composable
private fun formatTime(time: Long, now: Long, group: DateGroup): String {
    val context = LocalContext.current
    return remember(time, group) {
        when (group) {
            DateGroup.TODAY, DateGroup.YESTERDAY -> DateFormat.getTimeFormat(context).format(time)
            DateGroup.WEEK -> DateUtils.formatDateTime(context, time, DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY)
            DateGroup.EARLIER -> DateUtils.formatDateTime(
                context, time,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or
                    (if (startOfDay(time) < startOfDay(now, 300)) DateUtils.FORMAT_SHOW_YEAR else DateUtils.FORMAT_NO_YEAR),
            )
        }
    }
}
