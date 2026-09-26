package dev.ai.elements.core.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentToolsTest {
    @Test fun calculatorFormatsResults() {
        assertEquals("7006652", CalculatorTool.formatNumber(1234.0 * 5678))
        assertEquals("778516.8889", CalculatorTool.formatNumber(1234.0 * 5678 / 9))
        assertEquals("0.3333333333", CalculatorTool.formatNumber(1.0 / 3))
        assertEquals("2.5", CalculatorTool.formatNumber(2.5))
        assertEquals("Infinity", CalculatorTool.formatNumber(1.0 / 0))
    }
}
