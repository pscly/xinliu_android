package cc.pscly.onememos.ui.feature.settings.reminder

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import cc.pscly.onememos.domain.model.TodoReminderMode
import cc.pscly.onememos.domain.settings.CalendarPermissionState
import cc.pscly.onememos.domain.settings.CalendarSummary
import cc.pscly.onememos.domain.settings.ReminderCalendarSettingsCapability
import cc.pscly.onememos.domain.settings.ReminderCalendarSettingsCommand
import cc.pscly.onememos.domain.settings.ReminderCalendarSettingsResult
import cc.pscly.onememos.domain.settings.ReminderCalendarSettingsSnapshot
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.domain.settings.SettingsPermission
import cc.pscly.onememos.domain.settings.SettingsPlatformAction
import cc.pscly.onememos.ui.feature.settings.common.LocalSettingsPlatformActionDispatcher
import cc.pscly.onememos.ui.feature.settings.common.SettingsPlatformActionDispatcher
import cc.pscly.onememos.ui.feature.settings.common.SettingsPlatformResult
import cc.pscly.onememos.ui.theme.OneMemosTheme
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReminderCalendarScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun controls_areAccessible_andInvokeOnlyTypedUserIntents() {
        val intents = CopyOnWriteArrayList<ReminderCalendarUserIntent>()
        setContent(uiState(snapshot = snapshot())) { intents += it }

        val targets =
            listOf(
                "settings_reminder_mode_smart",
                "settings_reminder_mode_exact",
                "settings_reminder_calendar_enabled",
                "settings_reminder_calendar_7",
                "settings_reminder_calendar_9",
                "settings_reminder_clear_calendar",
                "settings_reminder_sync_reminders",
                "settings_reminder_reschedule",
            )
        targets.forEach { composeRule.onNodeWithTag(it).assertHeightIsAtLeast(48.dp) }

        composeRule.onNodeWithTag("settings_reminder_mode_exact").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_reminder_calendar_enabled").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_reminder_calendar_9").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_reminder_clear_calendar").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_reminder_sync_reminders").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_reminder_reschedule").performScrollTo().performClick()

        assertEquals(
            listOf(
                ReminderCalendarUserIntent.SetReminderMode(TodoReminderMode.EXACT),
                ReminderCalendarUserIntent.SetCalendarEnabled(false),
                ReminderCalendarUserIntent.SelectCalendar(9L),
                ReminderCalendarUserIntent.ClearCalendar,
                ReminderCalendarUserIntent.SetCalendarReminderSync(false),
                ReminderCalendarUserIntent.Reschedule,
            ),
            intents,
        )
    }

    @Test
    fun deniedPermission_neverAppearsEnabled_andStatusIsLive() {
        setContent(
            uiState(
                snapshot =
                    snapshot(
                        calendarEnabled = true,
                        permission = CalendarPermissionState.DENIED,
                    ),
            ),
        ) {}

        composeRule.onNodeWithTag("settings_reminder_calendar_enabled").assertIsOff()
        composeRule
            .onNodeWithTag("settings_reminder_sync_reminders")
            .assertIsOff()
            .assertIsNotEnabled()
        composeRule.onNodeWithText("日历权限未授予").performScrollTo().assertIsDisplayed()
        assertEquals(
            LiveRegionMode.Polite,
            composeRule
                .onNodeWithTag("settings_reminder_permission_status")
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.LiveRegion),
        )
    }

    @Test
    fun platformPermission_dispatchesOnce_andLauncherResultReturnsToViewModel() {
        val fake = FakeCapability(snapshot(permission = CalendarPermissionState.UNKNOWN))
        val request =
            SettingsPlatformAction.RequestPermissions(
                setOf(SettingsPermission.READ_CALENDAR, SettingsPermission.WRITE_CALENDAR),
            )
        fake.responder = { command ->
            if (command is ReminderCalendarSettingsCommand.SetCalendarEnabled &&
                fake.commands.count { it is ReminderCalendarSettingsCommand.SetCalendarEnabled } == 1
            ) {
                ReminderCalendarSettingsResult.Platform(request)
            } else {
                ReminderCalendarSettingsResult.Success
            }
        }
        val dispatches = AtomicInteger(0)
        val dispatcher =
            object : SettingsPlatformActionDispatcher {
                override fun dispatch(
                    action: SettingsPlatformAction,
                    onResult: (SettingsPlatformResult) -> Unit,
                ) {
                    dispatches.incrementAndGet()
                    assertEquals(request, action)
                    onResult(
                        SettingsPlatformResult.Permissions(
                            granted = request.permissions,
                            denied = emptySet(),
                        ),
                    )
                }
            }
        composeRule.setContent {
            CompositionLocalProvider(LocalSettingsPlatformActionDispatcher provides dispatcher) {
                OneMemosTheme { ReminderCalendarScreen(viewModel = ReminderCalendarViewModel(fake)) }
            }
        }

        composeRule.onNodeWithTag("settings_reminder_calendar_enabled").performClick()
        composeRule.waitUntil(2_000L) { fake.commands.size == 3 }
        assertEquals(1, dispatches.get())
        assertTrue(fake.commands[1] is ReminderCalendarSettingsCommand.ApplyPermissionResult)
        assertEquals(ReminderCalendarSettingsCommand.SetCalendarEnabled(true), fake.commands[2])
    }

    @Test
    fun stableErrorAndResult_haveLiveAnnouncements() {
        setContent(
            uiState(
                snapshot = snapshot(),
                persistentError = SettingsCapabilityError.PlatformUnavailable,
                notice = ReminderCalendarNotice.PERMISSION_GRANTED,
            ),
        ) {}

        composeRule.onNodeWithText("未找到可写日历").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("日历权限已授予").performScrollTo().assertIsDisplayed()
        for (tag in listOf("settings_reminder_error", "settings_reminder_result")) {
            assertEquals(
                LiveRegionMode.Polite,
                composeRule
                    .onNodeWithTag(tag)
                    .fetchSemanticsNode()
                    .config
                    .getOrNull(SemanticsProperties.LiveRegion),
            )
        }
    }

    @Test
    fun largeFont_threePlannedWidths_remainSingleColumnAtMost720() {
        var size by mutableStateOf(DpSize(360.dp, 800.dp))
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 2f)) {
                OneMemosTheme {
                    Box(Modifier.requiredSize(size).testTag("window_host")) {
                        ReminderCalendarContent(
                            uiState = uiState(snapshot = snapshot()),
                            onIntent = {},
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        for (next in listOf(DpSize(360.dp, 800.dp), DpSize(600.dp, 960.dp), DpSize(840.dp, 900.dp))) {
            size = next
            composeRule.waitForIdle()
            val contentWidth =
                composeRule.onNodeWithTag("settings_reminder_content").fetchSemanticsNode().boundsInRoot.width
            assertTrue("窗口 ${next.width} 的内容宽度不得超过 720dp", contentWidth <= 720f)
            val smartTop = composeRule.onNodeWithTag("settings_reminder_mode_smart").fetchSemanticsNode().boundsInRoot.top
            val exactTop = composeRule.onNodeWithTag("settings_reminder_mode_exact").fetchSemanticsNode().boundsInRoot.top
            assertTrue("窗口 ${next.width} 应保持提醒模式单列", smartTop < exactTop)
        }
    }

    private fun setContent(
        state: ReminderCalendarUiState,
        onIntent: (ReminderCalendarUserIntent) -> Unit,
    ) {
        composeRule.setContent {
            OneMemosTheme { ReminderCalendarContent(state, onIntent, Modifier.fillMaxSize()) }
        }
    }

    private fun snapshot(
        calendarEnabled: Boolean = true,
        permission: CalendarPermissionState = CalendarPermissionState.GRANTED,
    ) =
        ReminderCalendarSettingsSnapshot(
            reminderMode = TodoReminderMode.SMART,
            calendarEnabled = calendarEnabled,
            selectedCalendar = CalendarSummary(7L, "工作"),
            syncCalendarReminders = true,
            permission = permission,
            writableCalendars = listOf(CalendarSummary(7L, "工作"), CalendarSummary(9L, "生活")),
        )

    private fun uiState(
        snapshot: ReminderCalendarSettingsSnapshot,
        persistentError: SettingsCapabilityError? = null,
        notice: ReminderCalendarNotice? = null,
    ) = ReminderCalendarUiState(false, snapshot, persistentError, notice)

    private class FakeCapability(initial: ReminderCalendarSettingsSnapshot) :
        ReminderCalendarSettingsCapability {
        private val snapshots = MutableStateFlow(initial)
        val commands = CopyOnWriteArrayList<ReminderCalendarSettingsCommand>()
        var responder: suspend (ReminderCalendarSettingsCommand) -> ReminderCalendarSettingsResult = {
            ReminderCalendarSettingsResult.Success
        }

        override fun observe(): Flow<ReminderCalendarSettingsSnapshot> = snapshots

        override suspend fun execute(command: ReminderCalendarSettingsCommand): ReminderCalendarSettingsResult {
            commands += command
            return responder(command)
        }
    }
}
