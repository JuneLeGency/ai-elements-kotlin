package dev.ai.elements.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Frame timing of the chat's hot paths, and cold startup, on a release build: each journey
 * without ahead-of-time compilation (a fresh install before the profile applies) and with the
 * Baseline Profile (what users get). Read `frameDurationCpuMs` (P50/P90/P99) and
 * `frameOverrunMs` (> 0 is a missed frame) in the results.
 */
@RunWith(Parameterized::class)
class ChatBenchmarks(private val compilation: CompilationMode) {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startup() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilation,
        startupMode = StartupMode.COLD,
        iterations = 5,
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun scrollLongConversation() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilation,
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            startActivityAndWait()
            openLongConversation()
        },
    ) {
        scrollConversation()
    }

    @Test
    fun streamLongAnswer() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilation,
        startupMode = StartupMode.WARM,
        iterations = 3,
        setupBlock = {
            startActivityAndWait()
            useOfflineAgent()
            newChat()
        },
    ) {
        send(LONG_PROMPT)
        awaitReply()
    }

    @Test
    fun openHistoryAndSwitchConversation() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilation,
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            startActivityAndWait()
            ensureTwoConversations()
        },
    ) {
        switchConversation()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun compilations() = listOf(
            CompilationMode.None(),
            CompilationMode.Partial(BaselineProfileMode.UseIfAvailable),
        )
    }
}
