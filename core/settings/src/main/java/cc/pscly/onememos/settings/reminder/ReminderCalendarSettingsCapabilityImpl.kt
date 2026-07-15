package cc.pscly.onememos.settings.reminder

import cc.pscly.onememos.calendar.SystemCalendarGateway
import cc.pscly.onememos.calendar.WritableCalendar
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.settings.CalendarPermissionState
import cc.pscly.onememos.domain.settings.CalendarSummary
import cc.pscly.onememos.domain.settings.ReminderCalendarSettingsCapability
import cc.pscly.onememos.domain.settings.ReminderCalendarSettingsCommand
import cc.pscly.onememos.domain.settings.ReminderCalendarSettingsResult
import cc.pscly.onememos.domain.settings.ReminderCalendarSettingsSnapshot
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.domain.settings.SettingsPermission
import cc.pscly.onememos.domain.settings.SettingsPlatformAction
import cc.pscly.onememos.domain.sync.TodoReminderScheduler
import cc.pscly.onememos.settings.SettingsCapabilityErrorMapper
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex

/**
 * 提醒与日历深能力：只编排设置、提醒调度与系统日历网关。
 * 权限请求返回平台动作，不持有 Activity。
 */
@Singleton
class ReminderCalendarSettingsCapabilityImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val todoReminderScheduler: TodoReminderScheduler,
    private val systemCalendarGateway: SystemCalendarGateway,
) : ReminderCalendarSettingsCapability {
    private val commandInFlight = MutableStateFlow<ReminderCalendarSettingsCommand?>(null)
    private val permissionOverride = MutableStateFlow<CalendarPermissionState?>(null)
    private val writableCalendars = MutableStateFlow<List<CalendarSummary>>(emptyList())
    private val locks = ConcurrentHashMap<String, Mutex>()

    override fun observe(): Flow<ReminderCalendarSettingsSnapshot> =
        combine(
            settingsRepository.settings,
            commandInFlight,
            permissionOverride,
            writableCalendars,
        ) { settings, inFlight, override, calendars ->
            val permission = override ?: resolvePermissionState()
            val selected =
                settings.calendarIntegrationCalendarId?.let { id ->
                    calendars.firstOrNull { it.id == id }
                        ?: CalendarSummary(id = id, label = "日历 $id")
                }
            ReminderCalendarSettingsSnapshot(
                reminderMode = settings.todoReminderMode,
                calendarEnabled = settings.calendarIntegrationEnabled,
                selectedCalendar = selected,
                syncCalendarReminders = settings.calendarIntegrationSyncReminders,
                permission = permission,
                writableCalendars = if (permission == CalendarPermissionState.GRANTED) calendars else emptyList(),
                commandInFlight = inFlight,
            )
        }.onStart {
            refreshWritableCalendars()
        }

    override suspend fun execute(command: ReminderCalendarSettingsCommand): ReminderCalendarSettingsResult {
        val lock = locks.getOrPut(command.lockKey()) { Mutex() }
        if (!lock.tryLock()) {
            return ReminderCalendarSettingsResult.IgnoredDuplicate
        }
        commandInFlight.value = command
        return try {
            when (command) {
                is ReminderCalendarSettingsCommand.SetReminderMode -> {
                    settingsRepository.setTodoReminderMode(command.mode)
                    todoReminderScheduler.requestReschedule()
                    ReminderCalendarSettingsResult.Success
                }
                is ReminderCalendarSettingsCommand.SetCalendarEnabled -> {
                    if (command.enabled && !systemCalendarGateway.hasPermissions()) {
                        return ReminderCalendarSettingsResult.Platform(
                            SettingsPlatformAction.RequestPermissions(
                                setOf(SettingsPermission.READ_CALENDAR, SettingsPermission.WRITE_CALENDAR),
                            ),
                        )
                    }
                    if (command.enabled) {
                        val calendars = loadWritableCalendars()
                        writableCalendars.value = calendars
                        if (calendars.isEmpty()) {
                            return ReminderCalendarSettingsResult.Failure(
                                SettingsCapabilityError.PlatformUnavailable,
                            )
                        }
                    }
                    settingsRepository.setCalendarIntegrationEnabled(command.enabled)
                    ReminderCalendarSettingsResult.Success
                }
                is ReminderCalendarSettingsCommand.SetCalendar -> {
                    if (command.calendarId != null && !systemCalendarGateway.hasPermissions()) {
                        return ReminderCalendarSettingsResult.Platform(
                            SettingsPlatformAction.RequestPermissions(
                                setOf(SettingsPermission.READ_CALENDAR, SettingsPermission.WRITE_CALENDAR),
                            ),
                        )
                    }
                    settingsRepository.setCalendarIntegrationCalendarId(command.calendarId)
                    refreshWritableCalendars()
                    ReminderCalendarSettingsResult.Success
                }
                is ReminderCalendarSettingsCommand.SetCalendarReminderSync -> {
                    settingsRepository.setCalendarIntegrationSyncReminders(command.enabled)
                    ReminderCalendarSettingsResult.Success
                }
                is ReminderCalendarSettingsCommand.ApplyPermissionResult -> {
                    val granted =
                        command.granted.contains(SettingsPermission.READ_CALENDAR) &&
                            command.granted.contains(SettingsPermission.WRITE_CALENDAR)
                    permissionOverride.value =
                        if (granted) {
                            CalendarPermissionState.GRANTED
                        } else {
                            CalendarPermissionState.DENIED
                        }
                    if (granted) {
                        refreshWritableCalendars()
                    } else {
                        writableCalendars.value = emptyList()
                    }
                    ReminderCalendarSettingsResult.Success
                }
                ReminderCalendarSettingsCommand.Reschedule -> {
                    todoReminderScheduler.requestReschedule()
                    ReminderCalendarSettingsResult.Success
                }
            }
        } catch (t: Throwable) {
            ReminderCalendarSettingsResult.Failure(SettingsCapabilityErrorMapper.map(t))
        } finally {
            commandInFlight.value = null
            lock.unlock()
        }
    }

    private fun resolvePermissionState(): CalendarPermissionState =
        if (systemCalendarGateway.hasPermissions()) {
            CalendarPermissionState.GRANTED
        } else {
            CalendarPermissionState.UNKNOWN
        }

    private suspend fun refreshWritableCalendars() {
        if (!systemCalendarGateway.hasPermissions() &&
            permissionOverride.value != CalendarPermissionState.GRANTED
        ) {
            writableCalendars.value = emptyList()
            return
        }
        writableCalendars.value = loadWritableCalendars()
    }

    private suspend fun loadWritableCalendars(): List<CalendarSummary> {
        val result = systemCalendarGateway.writableCalendars()
        return result.getOrElse { throw it }.map { it.toSummary() }
    }

    private fun WritableCalendar.toSummary(): CalendarSummary =
        CalendarSummary(
            id = id,
            label =
                if (subtitle.isBlank()) {
                    displayName
                } else {
                    "$displayName ($subtitle)"
                },
        )

    private fun ReminderCalendarSettingsCommand.lockKey(): String =
        when (this) {
            is ReminderCalendarSettingsCommand.SetReminderMode -> "SetReminderMode"
            is ReminderCalendarSettingsCommand.SetCalendarEnabled -> "SetCalendarEnabled"
            is ReminderCalendarSettingsCommand.SetCalendar -> "SetCalendar"
            is ReminderCalendarSettingsCommand.SetCalendarReminderSync -> "SetCalendarReminderSync"
            is ReminderCalendarSettingsCommand.ApplyPermissionResult -> "ApplyPermissionResult"
            ReminderCalendarSettingsCommand.Reschedule -> "Reschedule"
        }
}
