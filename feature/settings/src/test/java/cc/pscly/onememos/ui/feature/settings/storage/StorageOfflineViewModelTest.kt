package cc.pscly.onememos.ui.feature.settings.storage

import cc.pscly.onememos.domain.model.CacheStats
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.domain.settings.StorageOfflineSettingsCapability
import cc.pscly.onememos.domain.settings.StorageOfflineSettingsCommand
import cc.pscly.onememos.domain.settings.StorageOfflineSettingsResult
import cc.pscly.onememos.domain.settings.StorageOfflineSettingsSnapshot
import cc.pscly.onememos.ui.feature.settings.common.SettingsConfirmation
import cc.pscly.onememos.ui.feature.settings.common.SettingsUiEvent
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class StorageOfflineViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun entryObservesSnapshot_withoutAutomaticStatisticsRefresh() =
        runBlocking {
            val fake = FakeStorageCapability()
            val viewModel = StorageOfflineViewModel(fake)
            val collection = launch { viewModel.uiState.collect {} }
            try {
                await { viewModel.uiState.value.snapshot != null }
                assertEquals(fake.snapshots.value, viewModel.uiState.value.snapshot)
                assertFalse(viewModel.uiState.value.loading)
                assertTrue(fake.commands.isEmpty())
            } finally {
                collection.cancel()
            }
        }

    @Test
    fun settingActionsDispatchPrefetchAndLimitCommands() =
        runBlocking {
            val fake = FakeStorageCapability()
            val viewModel = StorageOfflineViewModel(fake)

            viewModel.setImagePrefetchEnabled(false)
            viewModel.setPrefetchMemoLimit(24)
            viewModel.setPrefetchImageLimit(48)
            viewModel.setAttachmentCacheLimitMb(512)

            await { fake.commands.size == 4 }
            assertEquals(
                listOf(
                    StorageOfflineSettingsCommand.SetImagePrefetchEnabled(false),
                    StorageOfflineSettingsCommand.SetPrefetchMemoLimit(24),
                    StorageOfflineSettingsCommand.SetPrefetchImageLimit(48),
                    StorageOfflineSettingsCommand.SetAttachmentCacheLimitMb(512),
                ),
                fake.commands.toList(),
            )
        }

    @Test
    fun refreshStatsRunsOnlyAfterExplicitIntent() =
        runBlocking {
            val fake = FakeStorageCapability()
            val viewModel = StorageOfflineViewModel(fake)
            assertTrue(fake.commands.isEmpty())

            viewModel.refreshStats()

            await { fake.commands.isNotEmpty() }
            assertEquals(listOf(StorageOfflineSettingsCommand.RefreshStats), fake.commands.toList())
        }

    @Test
    fun clearRequestsEmitThreeConfirmationEvents() =
        runBlocking {
            val viewModel = StorageOfflineViewModel(FakeStorageCapability())
            val events = mutableListOf<SettingsUiEvent>()
            val collection =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    viewModel.events.take(3).toList(events)
                }

            viewModel.requestClearImageCache()
            yield()
            viewModel.requestClearAttachmentCache()
            yield()
            viewModel.requestClearAllCache()

            collection.join()
            assertEquals(
                listOf(
                    SettingsUiEvent.Confirm(SettingsConfirmation.CLEAR_IMAGE_CACHE),
                    SettingsUiEvent.Confirm(SettingsConfirmation.CLEAR_ATTACHMENT_CACHE),
                    SettingsUiEvent.Confirm(SettingsConfirmation.CLEAR_ALL_CACHE),
                ),
                events,
            )
        }

    @Test
    fun confirmedCleanupExecutesCommands_andUsesPostCleanupStatistics() =
        runBlocking {
            val freshStats =
                CacheStats(
                    databaseBytes = 1_024,
                    imageCacheBytes = 0,
                    attachmentCacheBytes = 0,
                    otherCacheBytes = 128,
                )
            val fake = FakeStorageCapability()
            fake.executeBlock = { command ->
                if (command.isCleanup()) {
                    fake.snapshots.value = fake.snapshots.value.copy(cacheStats = freshStats)
                }
                StorageOfflineSettingsResult.Success
            }
            val viewModel = StorageOfflineViewModel(fake)
            val collection = launch { viewModel.uiState.collect {} }
            try {
                viewModel.confirmClearImageCache()
                await { fake.commands.size == 1 }
                viewModel.confirmClearAttachmentCache()
                await { fake.commands.size == 2 }
                viewModel.confirmClearAllCache()
                await { fake.commands.size == 3 }

                assertEquals(
                    listOf(
                        StorageOfflineSettingsCommand.ClearImageCache,
                        StorageOfflineSettingsCommand.ClearAttachmentCache,
                        StorageOfflineSettingsCommand.ClearAllCache,
                    ),
                    fake.commands.toList(),
                )
                await { viewModel.uiState.value.snapshot?.cacheStats == freshStats }
                assertSame(freshStats, viewModel.uiState.value.snapshot?.cacheStats)
            } finally {
                collection.cancel()
            }
        }

    @Test
    fun cleanupFailureRetainsOldStatistics_andExposesPersistentError() =
        runBlocking {
            val oldStats =
                CacheStats(
                    databaseBytes = 10,
                    imageCacheBytes = 20,
                    attachmentCacheBytes = 30,
                    otherCacheBytes = 40,
                )
            val fake = FakeStorageCapability(initial = snapshot(cacheStats = oldStats))
            fake.executeBlock = {
                StorageOfflineSettingsResult.Failure(SettingsCapabilityError.StorageFailure)
            }
            val viewModel = StorageOfflineViewModel(fake)
            val collection = launch { viewModel.uiState.collect {} }
            try {
                await { viewModel.uiState.value.snapshot != null }
                viewModel.confirmClearImageCache()
                await { viewModel.uiState.value.persistentError != null }

                assertSame(oldStats, viewModel.uiState.value.snapshot?.cacheStats)
                assertEquals(
                    SettingsCapabilityError.StorageFailure,
                    viewModel.uiState.value.persistentError,
                )
            } finally {
                collection.cancel()
            }
        }

    @Test
    fun cleanupInFlightDisablesDuplicateSubmission() =
        runBlocking {
            val release = CompletableDeferred<Unit>()
            val fake = FakeStorageCapability()
            fake.executeBlock = { command ->
                fake.snapshots.value = fake.snapshots.value.copy(commandInFlight = command)
                release.await()
                fake.snapshots.value = fake.snapshots.value.copy(commandInFlight = null)
                StorageOfflineSettingsResult.Success
            }
            val viewModel = StorageOfflineViewModel(fake)
            val collection = launch { viewModel.uiState.collect {} }
            try {
                viewModel.confirmClearAllCache()
                await {
                    viewModel.uiState.value.snapshot?.commandInFlight ==
                        StorageOfflineSettingsCommand.ClearAllCache
                }
                viewModel.confirmClearAllCache()
                delay(50)

                assertTrue(viewModel.uiState.value.cleanupDisabled)
                assertEquals(1, fake.commands.count { it == StorageOfflineSettingsCommand.ClearAllCache })
                release.complete(Unit)
                await { !viewModel.uiState.value.cleanupDisabled }
            } finally {
                release.complete(Unit)
                collection.cancel()
            }
        }

    @Test
    fun eventsUseReplayZero_andDoNotReachLaterCollector() =
        runBlocking {
            val viewModel = StorageOfflineViewModel(FakeStorageCapability())
            val firstEvent = CompletableDeferred<SettingsUiEvent>()
            val firstCollector =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    firstEvent.complete(viewModel.events.first())
                }
            viewModel.requestClearImageCache()
            assertEquals(
                SettingsUiEvent.Confirm(SettingsConfirmation.CLEAR_IMAGE_CACHE),
                firstEvent.await(),
            )
            firstCollector.cancel()

            val replayed = withTimeoutOrNull(100) { viewModel.events.first() }
            assertNull(replayed)
        }

    @Test
    fun rapidLimitCommits_areSerialized_andFinalValueIsNotDropped() =
        runBlocking {
            val releaseFirst = CompletableDeferred<Unit>()
            val fake = FakeStorageCapability()
            fake.executeBlock = { command ->
                val active = fake.activeExecutions.incrementAndGet()
                fake.maxActiveExecutions.updateAndGet { current -> maxOf(current, active) }
                try {
                    if (command == StorageOfflineSettingsCommand.SetPrefetchMemoLimit(20)) {
                        releaseFirst.await()
                    }
                    StorageOfflineSettingsResult.Success
                } finally {
                    fake.activeExecutions.decrementAndGet()
                }
            }
            val viewModel = StorageOfflineViewModel(fake)

            viewModel.setPrefetchMemoLimit(20)
            viewModel.setPrefetchMemoLimit(37)

            await { fake.commands.isNotEmpty() }
            delay(50)
            assertEquals(1, fake.commands.size)
            releaseFirst.complete(Unit)
            await { fake.commands.size == 2 }
            assertEquals(
                listOf(
                    StorageOfflineSettingsCommand.SetPrefetchMemoLimit(20),
                    StorageOfflineSettingsCommand.SetPrefetchMemoLimit(37),
                ),
                fake.commands.toList(),
            )
            assertEquals(1, fake.maxActiveExecutions.get())
        }

    @Test
    fun refreshWhileCleanupPending_isSuppressed_andPageStaysDisabled() =
        runBlocking {
            val releaseCleanup = CompletableDeferred<Unit>()
            val fake = FakeStorageCapability()
            fake.executeBlock = { command ->
                if (command == StorageOfflineSettingsCommand.ClearAllCache) {
                    releaseCleanup.await()
                }
                StorageOfflineSettingsResult.Success
            }
            val viewModel = StorageOfflineViewModel(fake)

            viewModel.confirmClearAllCache()
            assertTrue(viewModel.uiState.value.cleanupDisabled)
            viewModel.refreshStats()
            delay(50)

            assertEquals(listOf(StorageOfflineSettingsCommand.ClearAllCache), fake.commands.toList())
            assertTrue(viewModel.uiState.value.cleanupDisabled)
            releaseCleanup.complete(Unit)
            await { !viewModel.uiState.value.cleanupDisabled }
        }

    @Test
    fun unrelatedSuccess_doesNotClearCleanupError_untilStorageRecoverySucceeds() =
        runBlocking {
            val fake = FakeStorageCapability()
            fake.executeBlock = { command ->
                when (command) {
                    StorageOfflineSettingsCommand.ClearImageCache ->
                        StorageOfflineSettingsResult.Failure(SettingsCapabilityError.StorageFailure)
                    else -> StorageOfflineSettingsResult.Success
                }
            }
            val viewModel = StorageOfflineViewModel(fake)

            viewModel.confirmClearImageCache()
            await { viewModel.uiState.value.persistentError != null }
            viewModel.setImagePrefetchEnabled(false)
            await { fake.commands.size == 2 }
            assertEquals(SettingsCapabilityError.StorageFailure, viewModel.uiState.value.persistentError)

            viewModel.confirmClearAttachmentCache()
            await { fake.commands.size == 3 && !viewModel.uiState.value.operationDisabled }
            assertEquals(SettingsCapabilityError.StorageFailure, viewModel.uiState.value.persistentError)

            viewModel.refreshStats()
            await { fake.commands.size == 4 }
            await { viewModel.uiState.value.persistentError == null }
        }

    private suspend fun await(
        timeoutMs: Long = 2_000L,
        condition: suspend () -> Boolean,
    ) {
        withTimeout(timeoutMs) {
            while (!condition()) {
                delay(10)
            }
        }
    }

    private class FakeStorageCapability(
        initial: StorageOfflineSettingsSnapshot = snapshot(),
    ) : StorageOfflineSettingsCapability {
        val snapshots = MutableStateFlow(initial)
        val commands = Collections.synchronizedList(mutableListOf<StorageOfflineSettingsCommand>())
        val activeExecutions = AtomicInteger(0)
        val maxActiveExecutions = AtomicInteger(0)
        var executeBlock: suspend (StorageOfflineSettingsCommand) -> StorageOfflineSettingsResult = {
            StorageOfflineSettingsResult.Success
        }

        override fun observe(): Flow<StorageOfflineSettingsSnapshot> = snapshots

        override suspend fun execute(command: StorageOfflineSettingsCommand): StorageOfflineSettingsResult {
            commands += command
            return executeBlock(command)
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

    private companion object {
        fun snapshot(cacheStats: CacheStats? = null) =
            StorageOfflineSettingsSnapshot(
                imagePrefetchEnabled = true,
                prefetchMemoLimit = 12,
                prefetchImageLimit = 36,
                attachmentCacheLimitMb = 256,
                cacheStats = cacheStats,
            )

        fun StorageOfflineSettingsCommand.isCleanup(): Boolean =
            this == StorageOfflineSettingsCommand.ClearImageCache ||
                this == StorageOfflineSettingsCommand.ClearAttachmentCache ||
                this == StorageOfflineSettingsCommand.ClearAllCache
    }
}
