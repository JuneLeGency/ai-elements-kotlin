package dev.ai.elements.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * UI elements are protocol-independent (AGENTS.md, package layering): they render the in-process
 * models (`core.model`, `core.chat`) and nothing else. Protocol mappings, agent conventions and
 * transports stay in `core`; if an element needs to know more, add a field to the model instead.
 */
class LayeringTest {
    private val sources = File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.toList()

    @Test
    fun uiImportsOnlyTheModelLayersOfCore() {
        val allowed = Regex("""^import dev\.ai\.elements\.core\.(model|chat)\.""")
        val offending = sources.flatMap { file ->
            file.readLines().filter { it.startsWith("import dev.ai.elements.core.") && !allowed.containsMatchIn(it) }.map { "${file.name}: $it" }
        }
        assertTrue("UI must not depend on protocol/agent/transport packages:\n${offending.joinToString("\n")}", offending.isEmpty())
    }

    @Test
    fun uiDoesNotInterpretProtocolOrConventionNames() {
        // Parsing a label back apart (e.g. "Title · server") is a private encoding too; joining for display is fine.
        val names = listOf("\"delegate_task\"", "\"load_capability\"", "AgUi", "UiMessageStream", "substringBefore(\" · \")", "substringAfter(\" · \")", "split(\" · \")")
        val offending = sources.flatMap { file ->
            file.readLines().filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") }
                .filter { line -> names.any { it in line } }.map { "${file.name}: ${it.trim()}" }
        }
        assertTrue("Map protocol conventions to model fields in core instead:\n${offending.joinToString("\n")}", offending.isEmpty())
    }
}
