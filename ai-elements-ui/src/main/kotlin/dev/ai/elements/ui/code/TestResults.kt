package dev.ai.elements.ui.code

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ai.elements.ui.R
import dev.ai.elements.ui.theme.AiType
import dev.ai.elements.ui.theme.isDark

/** Outcome of a test in [TestResults]. */
enum class TestStatus { PASSED, FAILED, SKIPPED, RUNNING }

/** One test; [error] is shown under failed tests. */
@Immutable
data class TestCaseResult(val name: String, val status: TestStatus, val durationMs: Long? = null, val error: String? = null)

/** A group of tests in [TestResults]. */
@Immutable
data class TestSuiteResult(val name: String, val tests: List<TestCaseResult>)

/**
 * A test run (AI Elements `<TestResults>`): pass / fail / skip counts and a
 * proportional bar, then suites that expand to their cases. Failed suites
 * start open and show each failure's message.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TestResults(suites: List<TestSuiteResult>, modifier: Modifier = Modifier, durationMs: Long? = null) {
    val all = suites.flatMap { it.tests }
    val passed = all.count { it.status == TestStatus.PASSED }
    val failed = all.count { it.status == TestStatus.FAILED }
    val skipped = all.count { it.status == TestStatus.SKIPPED }
    val running = all.any { it.status == TestStatus.RUNNING }
    val colors = statusColors()
    ElementCard(
        title = stringResource(R.string.ai_test_results),
        subtitle = listOfNotNull(
            stringResource(R.string.ai_tests_passed, passed),
            if (failed > 0) stringResource(R.string.ai_tests_failed, failed) else null,
            if (skipped > 0) stringResource(R.string.ai_tests_skipped, skipped) else null,
            durationMs?.let(::formatDuration),
        ).joinToString(" · "),
        icon = Icons.Outlined.Science,
        iconTint = if (failed > 0) MaterialTheme.colorScheme.error else colors.pass,
        modifier = modifier.testTag("test-results"),
        actions = { if (running) LoadingIndicator(Modifier.size(28.dp)) },
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            val total = all.size.coerceAtLeast(1)
            val track = MaterialTheme.colorScheme.surfaceContainerHighest
            Canvas(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(8.dp)) {
                drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
                var x = 0f
                for ((count, color) in listOf(passed to colors.pass, failed to colors.fail, skipped to colors.skip)) {
                    val w = size.width * count / total
                    if (w > 0) drawRoundRect(color, Offset(x, 0f), Size(w, size.height), CornerRadius(size.height / 2))
                    x += w
                }
            }
            suites.forEach { SuiteRow(it, colors) }
        }
    }
}

@Composable
private fun SuiteRow(suite: TestSuiteResult, colors: StatusColors) {
    val failed = suite.tests.count { it.status == TestStatus.FAILED }
    var open by rememberSaveable(suite.name) { mutableStateOf(failed > 0) }
    Column(Modifier.animateContentSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { open = !open }.heightIn(min = 48.dp).padding(horizontal = 16.dp),
        ) {
            StatusIcon(if (failed > 0) TestStatus.FAILED else if (suite.tests.any { it.status == TestStatus.RUNNING }) TestStatus.RUNNING else TestStatus.PASSED, colors)
            Text(suite.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${suite.tests.count { it.status == TestStatus.PASSED }}/${suite.tests.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Outlined.ExpandMore, stringResource(if (open) R.string.ai_collapse else R.string.ai_expand), Modifier.size(20.dp).rotate(if (open) 180f else 0f))
        }
        if (open) suite.tests.forEach { test ->
            Column(Modifier.fillMaxWidth().padding(start = 44.dp, end = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusIcon(test.status, colors, small = true)
                    Text(test.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    test.durationMs?.let { Text(formatDuration(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                test.error?.let {
                    SelectionContainer {
                        Text(it, style = AiType.code, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 24.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusIcon(status: TestStatus, colors: StatusColors, small: Boolean = false) {
    val size = if (small) 16.dp else 20.dp
    when (status) {
        TestStatus.PASSED -> Icon(Icons.Outlined.CheckCircle, stringResource(R.string.ai_test_status_passed), Modifier.size(size), tint = colors.pass)
        TestStatus.FAILED -> Icon(Icons.Outlined.Cancel, stringResource(R.string.ai_test_status_failed), Modifier.size(size), tint = colors.fail)
        TestStatus.SKIPPED -> Icon(Icons.Outlined.Block, stringResource(R.string.ai_test_status_skipped), Modifier.size(size), tint = colors.skip)
        TestStatus.RUNNING -> LoadingIndicator(Modifier.size(size + 4.dp))
    }
}

/** Pass/fail/skip hues that sit well on the theme (green is not a Material role). */
@Immutable
private data class StatusColors(val pass: Color, val fail: Color, val skip: Color)

@Composable
private fun statusColors(): StatusColors {
    val c = MaterialTheme.colorScheme
    val dark = MaterialTheme.isDark
    return StatusColors(pass = if (dark) Color(0xFF7FD99A) else Color(0xFF1E7B3A), fail = c.error, skip = c.outline)
}
