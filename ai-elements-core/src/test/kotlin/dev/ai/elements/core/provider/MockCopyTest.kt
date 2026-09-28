package dev.ai.elements.core.provider

import dev.ai.elements.core.provider.mock.MockCopy
import dev.ai.elements.core.provider.mock.MockCopy.Language
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** The offline agent answers in the prompt's language, as a model would. */
class MockCopyTest {
    @Test fun languageOfThePrompt() {
        assertEquals(Language.EN, MockCopy.of("What time is it?", Locale.CHINA).language)
        assertEquals(Language.ZH_HANS, MockCopy.of("把文字复制到剪贴板", Locale.US).language)
        assertEquals(Language.ZH_HANT, MockCopy.of("把文字複製到剪貼簿", Locale.US).language)
        assertEquals(Language.JA, MockCopy.of("東京は今何時ですか", Locale.US).language)
        // Han characters common to both scripts: the device's locale decides.
        assertEquals(Language.ZH_HANT, MockCopy.of("京都", Locale.TAIWAN).language)
        assertEquals(Language.ZH_HANS, MockCopy.of("京都", Locale.CHINA).language)
    }
}
