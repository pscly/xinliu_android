package cc.pscly.onememos.calendar

interface SystemCalendarGateway {
    fun hasPermissions(): Boolean

    suspend fun writableCalendars(): Result<List<WritableCalendar>>

    suspend fun calendarLabel(calendarId: Long): Result<String?>

    suspend fun syncTodoEvents(request: CalendarSyncRequest): CalendarSyncResult
}
