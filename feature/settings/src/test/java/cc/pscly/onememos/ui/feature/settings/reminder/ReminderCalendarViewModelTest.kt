package cc.pscly.onememos.ui.feature.settings.reminder

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
import cc.pscly.onememos.ui.feature.settings.common.SettingsMessage
import cc.pscly.onememos.ui.feature.settings.common.SettingsPlatformResult
import cc.pscly.onememos.ui.feature.settings.common.SettingsUiEvent
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderCalendarViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun userIntents_executeExactReminderCalendarCommands() =
        runBlocking {
            val fake = FakeCapability(snapshot())
            val viewModel = ReminderCalendarViewModel(fake)
            val cases =
                listOf(
                    ReminderCalendarUserIntent.SetReminderMode(TodoReminderMode.EXACT) to
                        ReminderCalendarSettingsCommand.SetReminderMode(TodoReminderMode.EXACT),
                    ReminderCalendarUserIntent.SetCalendarEnabled(true) to
                        ReminderCalendarSettingsCommand.SetCalendarEnabled(true),
                    ReminderCalendarUserIntent.SelectCalendar(7L) to
                        ReminderCalendarSettingsCommand.SetCalendar(7L),
                    ReminderCalendarUserIntent.ClearCalendar to
                        ReminderCalendarSettingsCommand.SetCalendar(null),
                    ReminderCalendarUserIntent.SetCalendarReminderSync(false) to
                        ReminderCalendarSettingsCommand.SetCalendarReminderSync(false),
                    ReminderCalendarUserIntent.Reschedule to ReminderCalendarSettingsCommand.Reschedule,
                )

            cases.forEachIndexed { index, (intent, expected) ->
                viewModel.onIntent(intent)
                await { fake.commands.size == index + 1 }
                assertEquals(expected, fake.commands[index])
            }
        }

    @Test
    fun platformPermission_isOneShot_andResultReturnsAsExplicitIntent() =
        runBlocking {
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
            val viewModel = ReminderCalendarViewModel(fake)
            val firstEvents = CopyOnWriteArrayList<SettingsUiEvent>()
            val firstCollector =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    viewModel.events.collect(firstEvents::add)
                }

            viewModel.onIntent(ReminderCalendarUserIntent.SetCalendarEnabled(true))
            await { firstEvents.isNotEmpty() }
            assertEquals(listOf(SettingsUiEvent.Platform(request)), firstEvents)
            firstCollector.cancel()
            assertNull(withTimeoutOrNull(100L) { viewModel.events.first() })

            val resumedEvents = CopyOnWriteArrayList<SettingsUiEvent>()
            val resumedCollector =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    viewModel.events.collect(resumedEvents::add)
                }
            val permissions =
                SettingsPlatformResult.Permissions(
                    granted = setOf(SettingsPermission.READ_CALENDAR, SettingsPermission.WRITE_CALENDAR),
                    denied = emptySet(),
                )
            viewModel.onIntent(ReminderCalendarUserIntent.ApplyPlatformResult(permissions))
            await { fake.commands.size == 3 && resumedEvents.isNotEmpty() }

            assertEquals(
                listOf(
                    ReminderCalendarSettingsCommand.SetCalendarEnabled(true),
                    ReminderCalendarSettingsCommand.ApplyPermissionResult(permissions.granted),
                    ReminderCalendarSettingsCommand.SetCalendarEnabled(true),
                ),
                fake.commands,
            )
            assertEquals(listOf(SettingsUiEvent.Toast(SettingsMessage.COMMAND_SUCCEEDED)), resumedEvents)
            resumedCollector.cancel()
        }

    @Test
    fun deniedPermission_isStableError_andDoesNotResumeEnable() =
        runBlocking {
            val fake = FakeCapability(snapshot(permission = CalendarPermissionState.UNKNOWN))
            fake.responder = { command ->
                when (command) {
                    is ReminderCalendarSettingsCommand.SetCalendarEnabled ->
                        ReminderCalendarSettingsResult.Platform(
                            SettingsPlatformAction.RequestPermissions(
                                setOf(SettingsPermission.READ_CALENDAR, SettingsPermission.WRITE_CALENDAR),
                            ),
                        )
                    else -> ReminderCalendarSettingsResult.Success
                }
            }
            val viewModel = ReminderCalendarViewModel(fake)
            val events = CopyOnWriteArrayList<SettingsUiEvent>()
            val collector =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    viewModel.events.collect(events::add)
                }
            viewModel.onIntent(ReminderCalendarUserIntent.SetCalendarEnabled(true))
            await { events.any { it is SettingsUiEvent.Platform } }

            viewModel.onIntent(
                ReminderCalendarUserIntent.ApplyPlatformResult(
                    SettingsPlatformResult.Permissions(
                        granted = setOf(SettingsPermission.READ_CALENDAR),
                        denied = setOf(SettingsPermission.WRITE_CALENDAR),
                    ),
                ),
            )
            await { viewModel.uiState.value.persistentError != null }
            await { events.contains(SettingsUiEvent.Toast(SettingsMessage.PERMISSION_DENIED)) }
            fake.snapshots.value = snapshot(permission = CalendarPermissionState.DENIED)

            assertEquals(SettingsCapabilityError.PermissionDenied, viewModel.uiState.value.persistentError)
            assertEquals(2, fake.commands.size)
            assertTrue(events.contains(SettingsUiEvent.Toast(SettingsMessage.PERMISSION_DENIED)))
            collector.cancel()
        }

    @Test
    fun commandFailure_remainsVisibleAcrossSnapshotUpdates() =
        runBlocking {
            val fake = FakeCapability(snapshot())
            fake.responder = {
                ReminderCalendarSettingsResult.Failure(SettingsCapabilityError.PlatformUnavailable)
            }
            val viewModel = ReminderCalendarViewModel(fake)
            viewModel.onIntent(ReminderCalendarUserIntent.Reschedule)
            await { viewModel.uiState.value.persistentError != null }

            fake.snapshots.value = snapshot(reminderMode = TodoReminderMode.EXACT)
            assertEquals(SettingsCapabilityError.PlatformUnavailable, viewModel.uiState.value.persistentError)
            assertEquals(TodoReminderMode.EXACT, viewModel.uiState.value.snapshot?.reminderMode)
        }

    @Test
    fun snapshotCommandInFlight_isExposedForDuplicateDisable() =
        runBlocking {
            val inFlight = ReminderCalendarSettingsCommand.Reschedule
            val viewModel = ReminderCalendarViewModel(FakeCapability(snapshot(commandInFlight = inFlight)))
            await { viewModel.uiState.value.snapshot != null }
            assertEquals(inFlight, viewModel.uiState.value.snapshot?.commandInFlight)
        }

    @Test
    fun permissionRequest_pendingStateSuppressesDuplicateUntilResult() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            runTest(dispatcher) {
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
                val viewModel = ReminderCalendarViewModel(fake)
                val events = CopyOnWriteArrayList<SettingsUiEvent>()
                val collector =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        viewModel.events.collect(events::add)
                    }
                runCurrent()

                viewModel.onIntent(ReminderCalendarUserIntent.SetCalendarEnabled(true))
                runCurrent()

                assertTrue(viewModel.uiState.value.platformRequestPending)
                assertEquals(
                    ReminderCalendarSettingsCommand.SetCalendarEnabled(true),
                    viewModel.uiState.value.pendingPlatformCommand,
                )
                assertEquals(listOf(ReminderCalendarSettingsCommand.SetCalendarEnabled(true)), fake.commands)
                assertEquals(listOf(SettingsUiEvent.Platform(request)), events)

                viewModel.onIntent(ReminderCalendarUserIntent.SetCalendarEnabled(true))
                runCurrent()

                assertEquals(listOf(ReminderCalendarSettingsCommand.SetCalendarEnabled(true)), fake.commands)
                assertEquals(listOf(SettingsUiEvent.Platform(request)), events)

                val permissions =
                    SettingsPlatformResult.Permissions(
                        granted = request.permissions,
                        denied = emptySet(),
                    )
                viewModel.onIntent(ReminderCalendarUserIntent.ApplyPlatformResult(permissions))
                runCurrent()

                assertFalse(viewModel.uiState.value.platformRequestPending)
                assertNull(viewModel.uiState.value.pendingPlatformCommand)
                assertEquals(
                    listOf(
                        ReminderCalendarSettingsCommand.SetCalendarEnabled(true),
                        ReminderCalendarSettingsCommand.ApplyPermissionResult(permissions.granted),
                        ReminderCalendarSettingsCommand.SetCalendarEnabled(true),
                    ),
                    fake.commands,
                )
                assertEquals(1, events.count { it == SettingsUiEvent.Toast(SettingsMessage.COMMAND_SUCCEEDED) })

                viewModel.onIntent(ReminderCalendarUserIntent.ApplyPlatformResult(permissions))
                runCurrent()

                assertEquals(3, fake.commands.size)
                assertEquals(1, events.count { it == SettingsUiEvent.Toast(SettingsMessage.COMMAND_SUCCEEDED) })
                collector.cancel()
            }
        } finally {
            Dispatchers.setMain(Dispatchers.Unconfined)
        }
    }

    @Test
    fun duplicatePermissionResult_withoutPendingCommand_isIgnored() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            runTest(dispatcher) {
                val fake = FakeCapability(snapshot(permission = CalendarPermissionState.GRANTED))
                val viewModel = ReminderCalendarViewModel(fake)
                val events = CopyOnWriteArrayList<SettingsUiEvent>()
                val collector =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        viewModel.events.collect(events::add)
                    }
                runCurrent()

                viewModel.onIntent(
                    ReminderCalendarUserIntent.ApplyPlatformResult(
                        SettingsPlatformResult.Permissions(
                            granted = setOf(SettingsPermission.READ_CALENDAR, SettingsPermission.WRITE_CALENDAR),
                            denied = emptySet(),
                        ),
                    ),
                )
                runCurrent()

                assertFalse(viewModel.uiState.value.platformRequestPending)
                assertNull(viewModel.uiState.value.notice)
                assertTrue(fake.commands.isEmpty())
                assertTrue(events.isEmpty())
                collector.cancel()
            }
        } finally {
            Dispatchers.setMain(Dispatchers.Unconfined)
        }
    }

    private fun snapshot(
        reminderMode: TodoReminderMode = TodoReminderMode.SMART,
        permission: CalendarPermissionState = CalendarPermissionState.GRANTED,
        commandInFlight: ReminderCalendarSettingsCommand? = null,
    ) =
        ReminderCalendarSettingsSnapshot(
            reminderMode = reminderMode,
            calendarEnabled = false,
            selectedCalendar = CalendarSummary(7L, "工作"),
            syncCalendarReminders = true,
            permission = permission,
            writableCalendars = listOf(CalendarSummary(7L, "工作"), CalendarSummary(9L, "生活")),
            commandInFlight = commandInFlight,
        )

    private suspend fun await(condition: suspend () -> Boolean) {
        withTimeout(2_000L) {
            while (!condition()) {
                delay(10L)
            }
        }
    }

    private class FakeCapability(initial: ReminderCalendarSettingsSnapshot) :
        ReminderCalendarSettingsCapability {
        val snapshots = MutableStateFlow(initial)
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

    class MainDispatcherRule : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(Dispatchers.Unconfined)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }
}
