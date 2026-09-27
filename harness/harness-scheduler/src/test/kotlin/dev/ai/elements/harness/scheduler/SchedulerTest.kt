package dev.ai.elements.harness.scheduler

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class SchedulerTest {
    @Test
    fun parseTime_withOffset() {
        assertEquals(Instant.parse("2026-09-28T00:00:00Z"), Scheduler.parseTime("2026-09-28T08:00:00+08:00"))
    }

    @Test
    fun parseTime_withoutOffset_usesDeviceZone() {
        val expected = LocalDateTime.parse("2026-09-28T08:00:00").atZone(ZoneId.systemDefault()).toInstant()
        assertEquals(expected, Scheduler.parseTime("2026-09-28T08:00:00"))
    }
}
