package cc.pscly.onememos.worker

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TodoExternalActionsActivityTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun planClockLaunch_todayFutureUsesAlarm() {
        val now = ZonedDateTime.of(LocalDate.of(2026, 7, 15), LocalTime.of(10, 0), zone)
        val plan = TodoClockLaunchPlanner.plan(dueAtLocal = "2026-07-15 11:30:00", now = now)
        assertEquals(TodoClockLaunchPlanner.Kind.SET_ALARM, plan.kind)
        assertEquals(11, plan.hour)
        assertEquals(30, plan.minute)
    }

    @Test
    fun planClockLaunch_within24hNonTodayUsesTimer() {
        val now = ZonedDateTime.of(LocalDate.of(2026, 7, 15), LocalTime.of(22, 0), zone)
        val plan = TodoClockLaunchPlanner.plan(dueAtLocal = "2026-07-16 08:00:00", now = now)
        assertEquals(TodoClockLaunchPlanner.Kind.SET_TIMER, plan.kind)
        assertEquals(10 * 60 * 60, plan.seconds)
    }

    @Test
    fun planClockLaunch_expiredUsesAlarm() {
        val now = ZonedDateTime.of(LocalDate.of(2026, 7, 15), LocalTime.of(12, 0), zone)
        val plan = TodoClockLaunchPlanner.plan(dueAtLocal = "2026-07-15 09:15:00", now = now)
        assertEquals(TodoClockLaunchPlanner.Kind.SET_ALARM, plan.kind)
        assertEquals(9, plan.hour)
        assertEquals(15, plan.minute)
    }

    @Test
    fun planClockLaunch_invalidDueShowsAlarms() {
        val plan = TodoClockLaunchPlanner.plan(dueAtLocal = "not-a-date")
        assertEquals(TodoClockLaunchPlanner.Kind.SHOW_ALARMS, plan.kind)
    }

    @Test
    fun extras_andFallbackRouteConstantsStayStable() {
        assertEquals("cc.pscly.onememos.extra.TODO_TITLE", TodoExternalActionsActivity.EXTRA_TODO_TITLE)
        assertEquals("cc.pscly.onememos.extra.TODO_DUE_AT_LOCAL", TodoExternalActionsActivity.EXTRA_DUE_AT_LOCAL)
        assertEquals("cc.pscly.onememos.extra.START_ROUTE", "cc.pscly.onememos.extra.START_ROUTE")
    }
}
