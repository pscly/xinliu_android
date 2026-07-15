package cc.pscly.onememos.ui.feature.settings.record

import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.QuickInsertTimeFormat
import cc.pscly.onememos.domain.settings.RecordEditingSettingsCapability
import cc.pscly.onememos.domain.settings.RecordEditingSettingsCommand
import cc.pscly.onememos.domain.settings.RecordEditingSettingsResult
import cc.pscly.onememos.domain.settings.RecordEditingSettingsSnapshot
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.ui.feature.settings.common.SettingsMessage
import cc.pscly.onememos.ui.feature.settings.common.SettingsUiEvent
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
class RecordEditingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun initialLoading_thenObservedSnapshotBecomesStableState() =
        runBlocking {
            val capability = FakeRecordEditingSettingsCapability()
            val viewModel = RecordEditingViewModel(capability)

            assertTrue(viewModel.uiState.value.loading)
            assertNull(viewModel.uiState.value.snapshot)

            val snapshot = stableSnapshot()
            capability.emit(snapshot)
            await { viewModel.uiState.value.snapshot == snapshot }

            assertFalse(viewModel.uiState.value.loading)
            assertEquals(snapshot, viewModel.uiState.value.snapshot)
            assertNull(viewModel.uiState.value.persistentError)
        }

    @Test
    fun submit_dispatchesAllFiveDomainCommands() =
        runBlocking {
            val capability = FakeRecordEditingSettingsCapability()
            val viewModel = RecordEditingViewModel(capability)
            capability.emit(stableSnapshot())
            await { !viewModel.uiState.value.loading }
            val commands =
                listOf(
                    RecordEditingSettingsCommand.SetDefaultVisibility(MemoVisibility.PUBLIC),
                    RecordEditingSettingsCommand.SetRegexSearchEnabled(true),
                    RecordEditingSettingsCommand.SetShowTagCounts(false),
                    RecordEditingSettingsCommand.SetQuickInsertTimeEnabled(true),
                    RecordEditingSettingsCommand.SetQuickInsertTimeFormat(QuickInsertTimeFormat.TIME_ONLY),
                )

            commands.forEach(viewModel::submit)
            await { capability.executedCommands.size == commands.size }

            assertEquals(commands, capability.executedCommands)
        }

    @Test
    fun failedCommand_mapsDomainErrorToPersistentState() =
        runBlocking {
            val capability = FakeRecordEditingSettingsCapability()
            val viewModel = RecordEditingViewModel(capability)
            capability.result =
                RecordEditingSettingsResult.Failure(SettingsCapabilityError.StorageFailure)
            capability.emit(stableSnapshot())
            await { !viewModel.uiState.value.loading }

            viewModel.submit(RecordEditingSettingsCommand.SetShowTagCounts(false))
            await { viewModel.uiState.value.persistentError != null }

            assertEquals(
                SettingsCapabilityError.StorageFailure,
                viewModel.uiState.value.persistentError,
            )
        }

    @Test
    fun successfulCommand_emitsTransientConfirmationWithoutReplay() =
        runBlocking {
            val capability = FakeRecordEditingSettingsCapability()
            val viewModel = RecordEditingViewModel(capability)
            capability.emit(stableSnapshot())
            await { !viewModel.uiState.value.loading }
            val firstEvent =
                async(start = CoroutineStart.UNDISPATCHED) {
                    viewModel.events.first()
                }

            viewModel.submit(RecordEditingSettingsCommand.SetRegexSearchEnabled(true))

            assertEquals(
                SettingsUiEvent.Toast(SettingsMessage.COMMAND_SUCCEEDED),
                firstEvent.await(),
            )
            assertTrue(viewModel.events.replayCache.isEmpty())
            assertNull(withTimeoutOrNull(100) { viewModel.events.first() })
        }

    @Test
    fun matchingCommandInFlight_blocksDuplicateButAllowsOtherCommand() =
        runBlocking {
            val capability = FakeRecordEditingSettingsCapability()
            val viewModel = RecordEditingViewModel(capability)
            capability.emit(
                stableSnapshot(
                    commandInFlight = RecordEditingSettingsCommand.SetRegexSearchEnabled(true),
                ),
            )
            await { viewModel.uiState.value.snapshot?.commandInFlight != null }

            viewModel.submit(RecordEditingSettingsCommand.SetRegexSearchEnabled(false))
            viewModel.submit(RecordEditingSettingsCommand.SetShowTagCounts(false))
            await { capability.executedCommands.isNotEmpty() }

            assertEquals(
                listOf(RecordEditingSettingsCommand.SetShowTagCounts(false)),
                capability.executedCommands,
            )
        }

    private suspend fun await(condition: suspend () -> Boolean) {
        withTimeout(2_000L) {
            while (!condition()) {
                delay(10)
            }
        }
    }

    private fun stableSnapshot(
        commandInFlight: RecordEditingSettingsCommand? = null,
    ): RecordEditingSettingsSnapshot =
        RecordEditingSettingsSnapshot(
            defaultVisibility = MemoVisibility.PRIVATE,
            regexSearchEnabled = false,
            showTagCounts = true,
            quickInsertTimeEnabled = false,
            quickInsertTimeFormat = QuickInsertTimeFormat.FULL_DATETIME,
            commandInFlight = commandInFlight,
        )

    private class FakeRecordEditingSettingsCapability : RecordEditingSettingsCapability {
        private val snapshots = MutableSharedFlow<RecordEditingSettingsSnapshot>(replay = 1)
        val executedCommands = mutableListOf<RecordEditingSettingsCommand>()
        var result: RecordEditingSettingsResult = RecordEditingSettingsResult.Success

        override fun observe(): Flow<RecordEditingSettingsSnapshot> = snapshots

        override suspend fun execute(
            command: RecordEditingSettingsCommand,
        ): RecordEditingSettingsResult {
            executedCommands += command
            return result
        }

        suspend fun emit(snapshot: RecordEditingSettingsSnapshot) {
            snapshots.emit(snapshot)
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
