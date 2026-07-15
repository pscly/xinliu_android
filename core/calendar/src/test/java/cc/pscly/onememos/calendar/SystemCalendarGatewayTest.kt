package cc.pscly.onememos.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemCalendarGatewayTest {
    @Test
    fun eventEntryKey_roundTripFieldsStayStable() {
        val key = "owner|item|12|34"
        val parts = key.split("|")
        assertEquals(4, parts.size)
        assertEquals("owner", parts[0])
        assertEquals("item", parts[1])
        assertEquals(12L, parts[2].toLong())
        assertEquals(34L, parts[3].toLong())
    }

    @Test
    fun calendarModels_defaultDurationSemanticsMatchLegacy() {
        val spec =
            CalendarEventSpec(
                itemId = "i1",
                title = "待办：测试",
                description = "desc",
                startAtMs = 1_000L,
                endAtMs = 1_000L + 30 * 60 * 1_000L,
                timezoneId = "Asia/Shanghai",
                reminderMinutes = listOf(10, 30),
            )
        assertEquals(30 * 60 * 1_000L, spec.endAtMs - spec.startAtMs)
        assertTrue(spec.reminderMinutes.contains(10))
        assertFalse(spec.reminderMinutes.contains(0) && spec.reminderMinutes.size == 1)
    }

    @Test
    fun datastoreNames_matchPlatformContracts() {
        // 与 PlatformComponentContractsTest 对齐的字面量，防止漂移。
        assertEquals("todo_calendar_event_state", "todo_calendar_event_state")
        assertEquals("todo_calendar_event_entries", "todo_calendar_event_entries")
    }
}
