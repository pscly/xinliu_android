package cc.pscly.onememos.calendar

data class WritableCalendar(
    val id: Long,
    val displayName: String,
    val subtitle: String,
    val isPrimary: Boolean,
)

data class CalendarEventSpec(
    val itemId: String,
    val title: String,
    val description: String,
    val startAtMs: Long,
    val endAtMs: Long,
    val timezoneId: String,
    val reminderMinutes: List<Int>,
)

data class CalendarSyncRequest(
    val ownerKey: String,
    val calendarId: Long,
    val syncReminders: Boolean,
    val events: List<CalendarEventSpec>,
)

data class CalendarSyncResult(
    val writtenEventCount: Int,
    val deletedEventCount: Int,
)
