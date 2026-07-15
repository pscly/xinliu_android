package cc.pscly.onememos.ui.feature.settings.appearance

import cc.pscly.onememos.domain.model.*
import cc.pscly.onememos.domain.settings.*
import cc.pscly.onememos.ui.feature.settings.common.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceInteractionViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun observe_exposesOnlyAppearanceSnapshot_andConstructorInjectsOnlyCapability() = runBlocking {
        val snapshot = defaultSnapshot()
        val (viewModel, fake) = fixture(snapshot)

        assertEquals(snapshot, viewModel.uiState.value.snapshot)
        assertNull(viewModel.uiState.value.persistentError)
        assertEquals(1, fake.observeCalls)
        assertEquals(listOf(AppearanceInteractionSettingsCapability::class.java),
            AppearanceInteractionViewModel::class.java.declaredConstructors.single().parameterTypes.toList())
    }

    @Test
    fun allPalettesModesOverlayAndDurations_executeTypedCommands() = runBlocking {
        val (viewModel, fake) = fixture()
        val commands = ThemePalette.entries.map(AppearanceInteractionSettingsCommand::SetThemePalette) +
                ThemeMode.entries.map(AppearanceInteractionSettingsCommand::SetThemeMode) +
                listOf(
                    AppearanceInteractionSettingsCommand.SetQuickCaptureOverlayEnabled(false),
                    AppearanceInteractionSettingsCommand.SetSealStampDurationMs(200),
                    AppearanceInteractionSettingsCommand.SetSealStampDurationMs(600),
                    AppearanceInteractionSettingsCommand.SetSealStampDurationMs(2_000),
                )

        commands.forEach { viewModel.onIntent(it.toIntent()) }

        await { fake.commands.size == commands.size }
        assertEquals(commands, fake.commands)
    }

    @Test
    fun latestIntentInSameFamily_runsAfterDelayedCommandInsteadOfBeingIgnored() = runBlocking {
        val firstCommand = AppearanceInteractionSettingsCommand.SetThemePalette(ThemePalette.INDIGO)
        val latestCommand = AppearanceInteractionSettingsCommand.SetThemePalette(ThemePalette.CYBER)
        val releaseFirst = CompletableDeferred<Unit>()
        val latestApplied = CompletableDeferred<Unit>()
        var familyLocked = false
        val (viewModel, fake) = fixture(execute = { command ->
            if (familyLocked) {
                AppearanceInteractionSettingsResult.IgnoredDuplicate
            } else {
                familyLocked = true
                try {
                    if (command == firstCommand) releaseFirst.await()
                    if (command == latestCommand) latestApplied.complete(Unit)
                    successResult
                } finally {
                    familyLocked = false
                }
            }
        })

        viewModel.onIntent(firstCommand.toIntent())
        await { fake.commands == listOf(firstCommand) }
        viewModel.onIntent(latestCommand.toIntent())
        releaseFirst.complete(Unit)

        withTimeout(1_000) { latestApplied.await() }
        assertEquals(listOf(firstCommand, latestCommand), fake.commands)
    }

    @Test
    fun overlayPermissionAction_grantedResultRetriesEnable_andEmitsSuccessOnce() = runBlocking {
        val (viewModel, fake) = fixture(execute = results(platformResult, successResult))

        assertEquals(SettingsUiEvent.Platform(overlayAction), viewModel.send(enableOverlayIntent))
        assertEquals(successToast, viewModel.send(permissionResult(granted = true)))
        assertEquals(listOf(enableOverlayCommand, enableOverlayCommand), fake.commands)
        assertNull(viewModel.uiState.value.persistentError)
    }

    @Test
    fun overlayPermissionPending_doesNotLaunchOverlappingPlatformRequest() = runBlocking {
        var executionCount = 0
        val (viewModel, fake) = fixture(execute = {
            executionCount += 1
            if (executionCount == 1) platformResult else successResult
        })

        assertEquals(SettingsUiEvent.Platform(overlayAction), viewModel.send(enableOverlayIntent))
        assertTrue(viewModel.uiState.value.overlayPermissionPending)
        viewModel.onIntent(enableOverlayIntent)

        assertEquals(listOf(enableOverlayCommand), fake.commands)
    }

    @Test
    fun duplicateOverlayCallback_withoutPendingRequestIsIgnored() = runBlocking {
        val (viewModel, fake) = fixture(execute = results(platformResult, successResult))

        assertEquals(SettingsUiEvent.Platform(overlayAction), viewModel.send(enableOverlayIntent))
        assertEquals(successToast, viewModel.send(permissionResult(granted = true)))
        viewModel.onIntent(permissionResult(granted = true))

        assertEquals(listOf(enableOverlayCommand, enableOverlayCommand), fake.commands)
    }

    @Test
    fun overlayPermissionDenied_isStableErrorWithPermissionMessage() = runBlocking {
        val (viewModel, fake) = fixture(execute = results(platformResult))

        assertEquals(SettingsUiEvent.Platform(overlayAction), viewModel.send(enableOverlayIntent))
        assertEquals(permissionDeniedToast, viewModel.send(permissionResult(granted = false)))
        assertEquals(SettingsCapabilityError.PermissionDenied, viewModel.uiState.value.persistentError)
        assertEquals(1, fake.commands.size)
    }

    @Test
    fun disableIntent_invalidatesGrantedCallbackRetry_beforeCapabilityExecution() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (viewModel, fake) = fixture(
            execute = results(platformResult, successResult, successResult),
        )
        viewModel.onIntent(enableOverlayIntent)
        runCurrent()

        assertTrue(viewModel.uiState.value.overlayPermissionPending)
        viewModel.onIntent(permissionResult(granted = true))
        viewModel.onIntent(disableOverlayIntent)
        advanceUntilIdle()

        assertEquals(listOf(enableOverlayCommand, disableOverlayCommand), fake.commands)
        assertFalse(viewModel.uiState.value.snapshot?.quickCaptureOverlayEnabled ?: true)
    }

    @Test
    fun disableIntent_invalidatesDelayedEnablePlatformResult() = runBlocking {
        val delayedEnable = CompletableDeferred<AppearanceInteractionSettingsResult>()
        val (viewModel, fake) = fixture(execute = { command ->
            if (command == enableOverlayCommand) delayedEnable.await() else successResult
        })
        viewModel.onIntent(enableOverlayIntent)
        await { fake.commands == listOf(enableOverlayCommand) }
        val event = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000) { viewModel.events.first() }
        }
        viewModel.onIntent(disableOverlayIntent)

        delayedEnable.complete(platformResult)
        assertEquals(successToast, event.await())
        viewModel.onIntent(permissionResult(granted = true))

        assertEquals(listOf(enableOverlayCommand, disableOverlayCommand), fake.commands)
        assertFalse(viewModel.uiState.value.snapshot?.quickCaptureOverlayEnabled ?: true)
    }

    @Test
    fun failureRemainsStable_untilSuccessfulCommandClearsIt() = runBlocking {
        val failure = AppearanceInteractionSettingsResult.Failure(SettingsCapabilityError.StorageFailure)
        val (viewModel) = fixture(execute = results(failure, successResult))

        assertEquals(failureToast, viewModel.send(durationIntent(200)))
        assertEquals(SettingsCapabilityError.StorageFailure, viewModel.uiState.value.persistentError)
        assertEquals(successToast, viewModel.send(durationIntent(600)))
        assertNull(viewModel.uiState.value.persistentError)
    }

    @Test
    fun platformAction_keepsStableErrorUntilRetriedCommandSucceeds() = runBlocking {
        val failure = AppearanceInteractionSettingsResult.Failure(SettingsCapabilityError.StorageFailure)
        val (viewModel) = fixture(execute = results(failure, platformResult, successResult))

        assertEquals(failureToast, viewModel.send(durationIntent(200)))
        assertEquals(SettingsUiEvent.Platform(overlayAction), viewModel.send(enableOverlayIntent))
        assertEquals(SettingsCapabilityError.StorageFailure, viewModel.uiState.value.persistentError)
        assertEquals(successToast, viewModel.send(permissionResult(granted = true)))
        assertNull(viewModel.uiState.value.persistentError)
    }

    @Test
    fun matchingCommandInFlight_disablesDuplicateSubmission() = runBlocking {
        val command = AppearanceInteractionSettingsCommand.SetThemeMode(ThemeMode.DARK)
        val (viewModel, fake) = fixture(defaultSnapshot(commandInFlight = command))

        viewModel.onIntent(AppearanceInteractionUserIntent.SetThemeMode(ThemeMode.DARK))

        assertTrue(viewModel.uiState.value.isCommandInFlight(command))
        assertTrue(fake.commands.isEmpty())
        assertTrue(viewModel.events.replayCache.isEmpty())
    }

    @Test
    fun platformEvent_hasReplayZero_andIsNotDeliveredToLateCollector() = runBlocking {
        val (viewModel) = fixture(execute = results(platformResult))

        assertEquals(SettingsUiEvent.Platform(overlayAction), viewModel.send(enableOverlayIntent))

        assertNull(withTimeoutOrNull(100) { viewModel.events.first() })
        assertTrue(viewModel.events.replayCache.isEmpty())
    }

    @Test
    fun ignoredDuplicate_emitsNoMessageAndKeepsStableState() = runBlocking {
        val (viewModel, fake) = fixture(execute = results(AppearanceInteractionSettingsResult.IgnoredDuplicate))

        viewModel.onIntent(AppearanceInteractionUserIntent.SetThemePalette(ThemePalette.INDIGO))
        await { fake.commands.isNotEmpty() }

        assertNull(withTimeoutOrNull(100) { viewModel.events.first() })
        assertNull(viewModel.uiState.value.persistentError)
        assertFalse(viewModel.uiState.value.loading)
    }

    private suspend fun fixture(snapshot: AppearanceInteractionSettingsSnapshot = defaultSnapshot(),
        execute: suspend (AppearanceInteractionSettingsCommand) -> AppearanceInteractionSettingsResult = { successResult },
    ): Fixture {
        val fake = FakeAppearanceCapability(snapshot, execute)
        val viewModel = AppearanceInteractionViewModel(fake)
        await { !viewModel.uiState.value.loading }
        return Fixture(viewModel, fake)
    }

    private suspend fun AppearanceInteractionViewModel.send(intent: AppearanceInteractionUserIntent) = coroutineScope {
        val event = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(2_000) { events.first() } }
        onIntent(intent)
        event.await()
    }

    private fun results(vararg values: AppearanceInteractionSettingsResult):
        suspend (AppearanceInteractionSettingsCommand) -> AppearanceInteractionSettingsResult =
        values.iterator().let { iterator -> { iterator.next() } }

    private fun AppearanceInteractionSettingsCommand.toIntent(): AppearanceInteractionUserIntent = when (this) {
        is AppearanceInteractionSettingsCommand.SetThemePalette -> AppearanceInteractionUserIntent.SetThemePalette(palette)
        is AppearanceInteractionSettingsCommand.SetThemeMode -> AppearanceInteractionUserIntent.SetThemeMode(mode)
        is AppearanceInteractionSettingsCommand.SetQuickCaptureOverlayEnabled -> AppearanceInteractionUserIntent.SetQuickCaptureOverlayEnabled(enabled)
        is AppearanceInteractionSettingsCommand.SetSealStampDurationMs -> AppearanceInteractionUserIntent.SetSealStampDurationMs(value)
    }

    private suspend fun await(condition: () -> Boolean) = withTimeout(2_000) { while (!condition()) delay(10) }

    private data class Fixture(val viewModel: AppearanceInteractionViewModel, val fake: FakeAppearanceCapability)

    private class FakeAppearanceCapability(
        initialSnapshot: AppearanceInteractionSettingsSnapshot, private val executeBlock:
            suspend (AppearanceInteractionSettingsCommand) -> AppearanceInteractionSettingsResult,
    ) : AppearanceInteractionSettingsCapability {
        private val snapshots = MutableStateFlow(initialSnapshot)
        val commands = mutableListOf<AppearanceInteractionSettingsCommand>()
        var observeCalls = 0

        override fun observe(): Flow<AppearanceInteractionSettingsSnapshot> = snapshots.also { observeCalls += 1 }

        override suspend fun execute(command: AppearanceInteractionSettingsCommand): AppearanceInteractionSettingsResult {
            commands += command
            return executeBlock(command).also { result ->
                if (result == AppearanceInteractionSettingsResult.Success && command is
                    AppearanceInteractionSettingsCommand.SetQuickCaptureOverlayEnabled
                ) snapshots.update { it.copy(quickCaptureOverlayEnabled = command.enabled) }
            }
        }
    }

    class MainDispatcherRule : TestWatcher() {
        override fun starting(description: Description) = Dispatchers.setMain(Dispatchers.Unconfined)
        override fun finished(description: Description) = Dispatchers.resetMain()
    }

    private companion object {
        val overlayAction = SettingsPlatformAction.OpenOverlayPermissionSettings("cc.pscly.onememos")
        val platformResult = AppearanceInteractionSettingsResult.Platform(overlayAction)
        val successResult = AppearanceInteractionSettingsResult.Success
        val successToast = SettingsUiEvent.Toast(SettingsMessage.COMMAND_SUCCEEDED)
        val failureToast = SettingsUiEvent.Toast(SettingsMessage.COMMAND_FAILED)
        val permissionDeniedToast = SettingsUiEvent.Toast(SettingsMessage.PERMISSION_DENIED)
        val enableOverlayCommand = AppearanceInteractionSettingsCommand.SetQuickCaptureOverlayEnabled(true)
        val disableOverlayCommand = AppearanceInteractionSettingsCommand.SetQuickCaptureOverlayEnabled(false)
        val enableOverlayIntent = AppearanceInteractionUserIntent.SetQuickCaptureOverlayEnabled(true)
        val disableOverlayIntent = AppearanceInteractionUserIntent.SetQuickCaptureOverlayEnabled(false)

        fun permissionResult(granted: Boolean) = AppearanceInteractionUserIntent.ApplyPlatformResult(
            SettingsPlatformResult.OverlayPermissionChanged(granted),
        )

        fun durationIntent(value: Int) = AppearanceInteractionUserIntent.SetSealStampDurationMs(value)

        fun defaultSnapshot(commandInFlight: AppearanceInteractionSettingsCommand? = null) = AppearanceInteractionSettingsSnapshot(
                themePalette = ThemePalette.PAPER_INK,
                themeMode = ThemeMode.FOLLOW_SYSTEM,
                quickCaptureOverlayEnabled = false,
                sealStampDurationMs = 600,
                commandInFlight = commandInFlight,
            )
    }
}
