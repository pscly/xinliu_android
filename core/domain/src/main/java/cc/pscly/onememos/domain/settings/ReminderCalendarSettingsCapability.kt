package cc.pscly.onememos.domain.settings

import cc.pscly.onememos.domain.model.TodoReminderMode
import kotlinx.coroutines.flow.Flow

interface ReminderCalendarSettingsCapability {
    fun observe(): Flow<ReminderCalendarSettingsSnapshot>

    suspend fun execute(command: ReminderCalendarSettingsCommand): ReminderCalendarSettingsResult
}

data class ReminderCalendarSettingsSnapshot(
    val reminderMode: TodoReminderMode,
    val calendarEnabled: Boolean,
    val selectedCalendar: CalendarSummary?,
    val syncCalendarReminders: Boolean,
    val permission: CalendarPermissionState,
    val writableCalendars: List<CalendarSummary>,
    val commandInFlight: ReminderCalendarSettingsCommand? = null,
)

data class CalendarSummary(val id: Long, val label: String)

enum class CalendarPermissionState {
    GRANTED,
    DENIED,
    UNKNOWN,
}

sealed interface ReminderCalendarSettingsCommand {
    data class SetReminderMode(val mode: TodoReminderMode) : ReminderCalendarSettingsCommand

    data class SetCalendarEnabled(val enabled: Boolean) : ReminderCalendarSettingsCommand

    data class SetCalendar(val calendarId: Long?) : ReminderCalendarSettingsCommand

    data class SetCalendarReminderSync(val enabled: Boolean) : ReminderCalendarSettingsCommand

    data class ApplyPermissionResult(
        val granted: Set<SettingsPermission>,
    ) : ReminderCalendarSettingsCommand

    data object Reschedule : ReminderCalendarSettingsCommand
}

sealed interface ReminderCalendarSettingsResult {
    data object Success : ReminderCalendarSettingsResult

    data object IgnoredDuplicate : ReminderCalendarSettingsResult

    data class Platform(val action: SettingsPlatformAction) : ReminderCalendarSettingsResult

    data class Failure(val error: SettingsCapabilityError) : ReminderCalendarSettingsResult
}
