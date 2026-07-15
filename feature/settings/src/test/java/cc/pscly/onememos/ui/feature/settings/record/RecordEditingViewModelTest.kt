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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    fun suspendedWrite_keepsStableSubmittingState_andBlocksSameAndCrossFamilyWrites() =
        runBlocking {
            val capability = FakeRecordEditingSettingsCapability()
            val viewModel = RecordEditingViewModel(capability)
            capability.emit(stableSnapshot())
            await { !viewModel.uiState.value.loading }
            val delayedExecution = capability.delayNextExecution()
            val activeCommand = RecordEditingSettingsCommand.SetRegexSearchEnabled(true)

            viewModel.submit(activeCommand)
            delayedExecution.awaitStarted()

            assertTrue(viewModel.uiState.value.isSubmitting)
            capability.emit(stableSnapshot(commandInFlight = activeCommand))
            capability.emit(stableSnapshot(commandInFlight = null))
            await { viewModel.uiState.value.snapshot?.commandInFlight == null }
            assertTrue(viewModel.uiState.value.isSubmitting)

            viewModel.submit(RecordEditingSettingsCommand.SetRegexSearchEnabled(false))
            viewModel.submit(RecordEditingSettingsCommand.SetShowTagCounts(false))

            assertEquals(listOf(activeCommand), capability.executedCommands)
            assertTrue(viewModel.uiState.value.isSubmitting)

            delayedExecution.complete(RecordEditingSettingsResult.Success)
            await { !viewModel.uiState.value.isSubmitting }
            assertEquals(listOf(activeCommand), capability.executedCommands)
        }

    @Test
    fun activeCollector_receivesEverySequentialResult_withoutReplayAfterReplacement() =
        runBlocking {
            val capability = FakeRecordEditingSettingsCapability()
            val viewModel = RecordEditingViewModel(capability)
            capability.emit(stableSnapshot())
            await { !viewModel.uiState.value.loading }
            val receivedEvents = mutableListOf<SettingsUiEvent>()
            val releaseCollector = Channel<Unit>(capacity = Channel.UNLIMITED)
            val collector =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    viewModel.events.collect { event ->
                        receivedEvents += event
                        releaseCollector.receive()
                    }
                }
            val commands =
                listOf(
                    RecordEditingSettingsCommand.SetRegexSearchEnabled(true),
                    RecordEditingSettingsCommand.SetShowTagCounts(false),
                    RecordEditingSettingsCommand.SetQuickInsertTimeEnabled(true),
                )

            viewModel.submit(commands[0])
            await { receivedEvents.size == 1 && !viewModel.uiState.value.isSubmitting }
            viewModel.submit(commands[1])
            await { capability.executedCommands.size == 2 && !viewModel.uiState.value.isSubmitting }
            viewModel.submit(commands[2])
            await { capability.executedCommands.size == 3 }

            assertTrue(viewModel.uiState.value.isSubmitting)
            releaseCollector.send(Unit)
            await { receivedEvents.size == 2 && !viewModel.uiState.value.isSubmitting }
            releaseCollector.send(Unit)
            await { receivedEvents.size == 3 }

            assertEquals(
                List(3) { SettingsUiEvent.Toast(SettingsMessage.COMMAND_SUCCEEDED) },
                receivedEvents,
            )
            assertTrue(viewModel.events.replayCache.isEmpty())
            releaseCollector.send(Unit)
            collector.cancelAndJoin()
            assertNull(withTimeoutOrNull(100) { viewModel.events.first() })
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
        private var delayedExecution: DelayedExecution? = null

        override fun observe(): Flow<RecordEditingSettingsSnapshot> = snapshots

        override suspend fun execute(
            command: RecordEditingSettingsCommand,
        ): RecordEditingSettingsResult {
            executedCommands += command
            delayedExecution?.let { execution ->
                delayedExecution = null
                execution.started.complete(Unit)
                return execution.result.await()
            }
            return result
        }

        fun delayNextExecution(): DelayedExecution =
            DelayedExecution().also {
                check(delayedExecution == null)
                delayedExecution = it
            }

        suspend fun emit(snapshot: RecordEditingSettingsSnapshot) {
            snapshots.emit(snapshot)
        }
    }

    private class DelayedExecution {
        val started = CompletableDeferred<Unit>()
        val result = CompletableDeferred<RecordEditingSettingsResult>()

        suspend fun awaitStarted() {
            started.await()
        }

        fun complete(value: RecordEditingSettingsResult) {
            result.complete(value)
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
