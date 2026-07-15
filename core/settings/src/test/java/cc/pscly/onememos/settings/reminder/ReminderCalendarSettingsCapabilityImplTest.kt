package cc.pscly.onememos.settings.reminder

import cc.pscly.onememos.calendar.CalendarSyncRequest
import cc.pscly.onememos.calendar.CalendarSyncResult
import cc.pscly.onememos.calendar.SystemCalendarGateway
import cc.pscly.onememos.calendar.WritableCalendar
import cc.pscly.onememos.domain.model.AppSettings
import cc.pscly.onememos.domain.model.FullSyncStage
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.QuickInsertTimeFormat
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import cc.pscly.onememos.domain.model.TodoReminderMode
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.settings.CalendarPermissionState
import cc.pscly.onememos.domain.settings.ReminderCalendarSettingsCommand
import cc.pscly.onememos.domain.settings.ReminderCalendarSettingsResult
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.domain.settings.SettingsPermission
import cc.pscly.onememos.domain.settings.SettingsPlatformAction
import cc.pscly.onememos.domain.sync.TodoReminderScheduler
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderCalendarSettingsCapabilityImplTest {
    @Test
    fun setReminderMode_updatesAndReschedules() =
        runBlocking {
            val repo = FakeSettingsRepository(AppSettings())
            val scheduler = FakeReminderScheduler()
            val gateway = FakeCalendarGateway(hasPerms = true)
            val cap = ReminderCalendarSettingsCapabilityImpl(repo, scheduler, gateway)

            val result =
                cap.execute(ReminderCalendarSettingsCommand.SetReminderMode(TodoReminderMode.EXACT))
            assertEquals(ReminderCalendarSettingsResult.Success, result)
            assertEquals(TodoReminderMode.EXACT, repo.flow.value.todoReminderMode)
            assertEquals(1, scheduler.calls.get())
        }

    @Test
    fun enableCalendar_withoutPermission_returnsRequestPermissions() =
        runBlocking {
            val repo = FakeSettingsRepository(AppSettings())
            val cap =
                ReminderCalendarSettingsCapabilityImpl(
                    repo,
                    FakeReminderScheduler(),
                    FakeCalendarGateway(hasPerms = false),
                )
            val result = cap.execute(ReminderCalendarSettingsCommand.SetCalendarEnabled(true))
            val platform = result as ReminderCalendarSettingsResult.Platform
            val action = platform.action as SettingsPlatformAction.RequestPermissions
            assertEquals(
                setOf(SettingsPermission.READ_CALENDAR, SettingsPermission.WRITE_CALENDAR),
                action.permissions,
            )
            assertEquals(false, repo.flow.value.calendarIntegrationEnabled)
        }

    @Test
    fun enableCalendar_withPermission_listsWritableAndPersists() =
        runBlocking {
            val repo = FakeSettingsRepository(AppSettings())
            val gateway =
                FakeCalendarGateway(
                    hasPerms = true,
                    calendars =
                        listOf(
                            WritableCalendar(1L, "工作", "Google", true),
                            WritableCalendar(2L, "私人", "本地", false),
                        ),
                )
            val cap = ReminderCalendarSettingsCapabilityImpl(repo, FakeReminderScheduler(), gateway)
            val result = cap.execute(ReminderCalendarSettingsCommand.SetCalendarEnabled(true))
            assertEquals(ReminderCalendarSettingsResult.Success, result)
            assertEquals(true, repo.flow.value.calendarIntegrationEnabled)

            val snap = cap.observe().first()
            assertEquals(CalendarPermissionState.GRANTED, snap.permission)
            assertEquals(2, snap.writableCalendars.size)
            assertEquals("工作 (Google)", snap.writableCalendars[0].label)
        }

    @Test
    fun enableCalendar_noWritable_mapsPlatformUnavailable() =
        runBlocking {
            val repo = FakeSettingsRepository(AppSettings())
            val cap =
                ReminderCalendarSettingsCapabilityImpl(
                    repo,
                    FakeReminderScheduler(),
                    FakeCalendarGateway(hasPerms = true, calendars = emptyList()),
                )
            val result = cap.execute(ReminderCalendarSettingsCommand.SetCalendarEnabled(true))
            assertEquals(
                ReminderCalendarSettingsResult.Failure(SettingsCapabilityError.PlatformUnavailable),
                result,
            )
        }

    @Test
    fun setCalendar_selectAndClear() =
        runBlocking {
            val repo = FakeSettingsRepository(AppSettings())
            val gateway = FakeCalendarGateway(hasPerms = true)
            val cap = ReminderCalendarSettingsCapabilityImpl(repo, FakeReminderScheduler(), gateway)

            assertEquals(
                ReminderCalendarSettingsResult.Success,
                cap.execute(ReminderCalendarSettingsCommand.SetCalendar(9L)),
            )
            assertEquals(9L, repo.flow.value.calendarIntegrationCalendarId)

            assertEquals(
                ReminderCalendarSettingsResult.Success,
                cap.execute(ReminderCalendarSettingsCommand.SetCalendar(null)),
            )
            assertEquals(null, repo.flow.value.calendarIntegrationCalendarId)
        }

    @Test
    fun setCalendarReminderSync_andSecurityException() =
        runBlocking {
            val repo = FakeSettingsRepository(AppSettings())
            val gateway = FakeCalendarGateway(hasPerms = true)
            val cap = ReminderCalendarSettingsCapabilityImpl(repo, FakeReminderScheduler(), gateway)
            assertEquals(
                ReminderCalendarSettingsResult.Success,
                cap.execute(ReminderCalendarSettingsCommand.SetCalendarReminderSync(false)),
            )
            assertEquals(false, repo.flow.value.calendarIntegrationSyncReminders)

            gateway.throwSecurity = true
            val failed = cap.execute(ReminderCalendarSettingsCommand.SetCalendarEnabled(true))
            assertEquals(
                ReminderCalendarSettingsResult.Failure(SettingsCapabilityError.PermissionDenied),
                failed,
            )
        }

    @Test
    fun applyPermissionResult_updatesState() =
        runBlocking {
            val repo = FakeSettingsRepository(AppSettings())
            val gateway =
                FakeCalendarGateway(
                    hasPerms = false,
                    calendars = listOf(WritableCalendar(3L, "主日历", "", true)),
                )
            val cap = ReminderCalendarSettingsCapabilityImpl(repo, FakeReminderScheduler(), gateway)

            // 模拟授权后 gateway 也变为有权限
            gateway.hasPermsFlag.set(true)
            val result =
                cap.execute(
                    ReminderCalendarSettingsCommand.ApplyPermissionResult(
                        setOf(SettingsPermission.READ_CALENDAR, SettingsPermission.WRITE_CALENDAR),
                    ),
                )
            assertEquals(ReminderCalendarSettingsResult.Success, result)
            val snap = cap.observe().first()
            assertEquals(CalendarPermissionState.GRANTED, snap.permission)
            assertEquals(1, snap.writableCalendars.size)
        }

    @Test
    fun concurrentReschedule_secondIsIgnoredDuplicate() =
        runBlocking {
            val scheduler = FakeReminderScheduler(holdMs = 250L)
            val cap =
                ReminderCalendarSettingsCapabilityImpl(
                    FakeSettingsRepository(AppSettings()),
                    scheduler,
                    FakeCalendarGateway(hasPerms = true),
                )
            val first =
                async(Dispatchers.IO) {
                    cap.execute(ReminderCalendarSettingsCommand.Reschedule)
                }
            while (scheduler.calls.get() == 0) {
                delay(5)
            }
            val second =
                withContext(Dispatchers.IO) {
                    cap.execute(ReminderCalendarSettingsCommand.Reschedule)
                }
            assertEquals(ReminderCalendarSettingsResult.IgnoredDuplicate, second)
            assertEquals(ReminderCalendarSettingsResult.Success, first.await())
            assertEquals(1, scheduler.calls.get())
        }

    private class FakeSettingsRepository(
        initial: AppSettings,
    ) : SettingsRepository {
        val flow = MutableStateFlow(initial)
        override val settings: Flow<AppSettings> = flow

        override suspend fun setWelcomeCompleted(completed: Boolean) = Unit
        override suspend fun setServerUrl(url: String) = Unit
        override suspend fun setToken(token: String) = Unit
        override suspend fun setLoginMode(mode: LoginMode) = Unit
        override suspend fun setCurrentUserCreator(creator: String) = Unit
        override suspend fun setDev2Unlocked(unlocked: Boolean) = Unit
        override suspend fun setDev2ShowPublicWorkspaceMemos(enabled: Boolean) = Unit
        override suspend fun setThemePalette(palette: ThemePalette) = Unit
        override suspend fun setThemeMode(mode: ThemeMode) = Unit
        override suspend fun setDefaultVisibility(visibility: MemoVisibility) = Unit
        override suspend fun setRegexSearchEnabled(enabled: Boolean) = Unit
        override suspend fun setShowTagCountsInFilter(enabled: Boolean) = Unit
        override suspend fun setQuickCaptureOverlayEnabled(enabled: Boolean) = Unit
        override suspend fun setQuickInsertTimeEnabled(enabled: Boolean) = Unit
        override suspend fun setQuickInsertTimeFormat(format: QuickInsertTimeFormat) = Unit
        override suspend fun setSealStampDurationMs(durationMs: Int) = Unit
        override suspend fun setOfflineImagePrefetchEnabled(enabled: Boolean) = Unit
        override suspend fun setOfflineImagePrefetchMaxMemos(count: Int) = Unit
        override suspend fun setOfflineImagePrefetchMaxImages(count: Int) = Unit
        override suspend fun setAttachmentCacheMaxMb(mb: Int) = Unit
        override suspend fun setAttachmentUploadMaxMb(mb: Int) = Unit

        override suspend fun setTodoReminderMode(mode: TodoReminderMode) {
            flow.value = flow.value.copy(todoReminderMode = mode)
        }

        override suspend fun setCalendarIntegrationEnabled(enabled: Boolean) {
            flow.value = flow.value.copy(calendarIntegrationEnabled = enabled)
        }

        override suspend fun setCalendarIntegrationCalendarId(calendarId: Long?) {
            flow.value = flow.value.copy(calendarIntegrationCalendarId = calendarId)
        }

        override suspend fun setCalendarIntegrationSyncReminders(enabled: Boolean) {
            flow.value = flow.value.copy(calendarIntegrationSyncReminders = enabled)
        }

        override suspend fun setLastSyncSuccess() = Unit
        override suspend fun setLastSyncError(error: String, httpCode: Int) = Unit
        override suspend fun setDevAutoTagLineKeywords(raw: String) = Unit
        override suspend fun setDevShowAutoTagLineInHome(show: Boolean) = Unit
        override suspend fun setDevShowAutoTagLineInView(show: Boolean) = Unit
        override suspend fun setDevShowAutoTagLineInEdit(show: Boolean) = Unit
        override suspend fun setDevHomeRichPreviewStickyLimit(limit: Int) = Unit
        override suspend fun setFullSyncRunning(runId: String) = Unit
        override suspend fun setFullSyncProgress(
            runId: String,
            stage: FullSyncStage,
            pagesFetched: Int,
            itemsFetched: Int,
        ) = Unit
        override suspend fun setFullSyncSuccess(
            runId: String,
            stage: FullSyncStage,
            pagesFetched: Int,
            itemsFetched: Int,
        ) = Unit
        override suspend fun setFullSyncFailed(
            runId: String,
            stage: FullSyncStage,
            pagesFetched: Int,
            itemsFetched: Int,
            error: String,
        ) = Unit
    }

    private class FakeReminderScheduler(
        private val holdMs: Long = 0L,
    ) : TodoReminderScheduler {
        val calls = AtomicInteger(0)

        override fun requestReschedule() {
            calls.incrementAndGet()
            if (holdMs > 0L) {
                Thread.sleep(holdMs)
            }
        }
    }

    private class FakeCalendarGateway(
        hasPerms: Boolean,
        private val calendars: List<WritableCalendar> = emptyList(),
    ) : SystemCalendarGateway {
        val hasPermsFlag = AtomicBoolean(hasPerms)
        var throwSecurity: Boolean = false

        override fun hasPermissions(): Boolean = hasPermsFlag.get()

        override suspend fun writableCalendars(): Result<List<WritableCalendar>> {
            if (throwSecurity) {
                return Result.failure(SecurityException("calendar denied"))
            }
            return Result.success(calendars)
        }

        override suspend fun calendarLabel(calendarId: Long): Result<String?> =
            Result.success(calendars.firstOrNull { it.id == calendarId }?.displayName)

        override suspend fun syncTodoEvents(request: CalendarSyncRequest): CalendarSyncResult =
            CalendarSyncResult(writtenEventCount = 0, deletedEventCount = 0)
    }
}
