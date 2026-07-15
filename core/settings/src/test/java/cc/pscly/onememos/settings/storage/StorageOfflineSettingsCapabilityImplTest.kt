package cc.pscly.onememos.settings.storage

import cc.pscly.onememos.domain.model.AppSettings
import cc.pscly.onememos.domain.model.CacheStats
import cc.pscly.onememos.domain.model.FullSyncStage
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.QuickInsertTimeFormat
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import cc.pscly.onememos.domain.model.TodoReminderMode
import cc.pscly.onememos.domain.repository.CacheRepository
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.domain.settings.StorageOfflineSettingsCommand
import cc.pscly.onememos.domain.settings.StorageOfflineSettingsResult
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageOfflineSettingsCapabilityImplTest {
    @Test
    fun observe_mapsFourSettings_andDoesNotScanUntilRefresh() =
        runBlocking {
            val repo =
                FakeSettingsRepository(
                    AppSettings(
                        offlineImagePrefetchEnabled = false,
                        offlineImagePrefetchMaxMemos = 12,
                        offlineImagePrefetchMaxImages = 34,
                        attachmentCacheMaxMb = 256,
                    ),
                )
            val cache = FakeCacheRepository()
            val cap = StorageOfflineSettingsCapabilityImpl(repo, cache)

            val before = cap.observe().first()
            assertEquals(false, before.imagePrefetchEnabled)
            assertEquals(12, before.prefetchMemoLimit)
            assertEquals(34, before.prefetchImageLimit)
            assertEquals(256, before.attachmentCacheLimitMb)
            assertNull(before.cacheStats)
            assertEquals(0, cache.statsCalls.get())

            assertEquals(
                StorageOfflineSettingsResult.Success,
                cap.execute(StorageOfflineSettingsCommand.RefreshStats),
            )
            assertEquals(1, cache.statsCalls.get())
            assertEquals(cache.stats, cap.observe().first().cacheStats)
        }

    @Test
    fun execute_eachSetter_andInvalidNegative() =
        runBlocking {
            val repo = FakeSettingsRepository(AppSettings())
            val cap = StorageOfflineSettingsCapabilityImpl(repo, FakeCacheRepository())

            assertEquals(
                StorageOfflineSettingsResult.Success,
                cap.execute(StorageOfflineSettingsCommand.SetImagePrefetchEnabled(false)),
            )
            assertEquals(false, repo.flow.value.offlineImagePrefetchEnabled)

            assertEquals(
                StorageOfflineSettingsResult.Success,
                cap.execute(StorageOfflineSettingsCommand.SetPrefetchMemoLimit(8)),
            )
            assertEquals(8, repo.flow.value.offlineImagePrefetchMaxMemos)

            assertEquals(
                StorageOfflineSettingsResult.Success,
                cap.execute(StorageOfflineSettingsCommand.SetPrefetchImageLimit(9)),
            )
            assertEquals(9, repo.flow.value.offlineImagePrefetchMaxImages)

            assertEquals(
                StorageOfflineSettingsResult.Success,
                cap.execute(StorageOfflineSettingsCommand.SetAttachmentCacheLimitMb(128)),
            )
            assertEquals(128, repo.flow.value.attachmentCacheMaxMb)

            assertEquals(
                StorageOfflineSettingsResult.Failure(SettingsCapabilityError.InvalidInput),
                cap.execute(StorageOfflineSettingsCommand.SetPrefetchMemoLimit(-1)),
            )
            assertEquals(
                StorageOfflineSettingsResult.Failure(SettingsCapabilityError.InvalidInput),
                cap.execute(StorageOfflineSettingsCommand.SetPrefetchImageLimit(-2)),
            )
            assertEquals(
                StorageOfflineSettingsResult.Failure(SettingsCapabilityError.InvalidInput),
                cap.execute(StorageOfflineSettingsCommand.SetAttachmentCacheLimitMb(-3)),
            )
        }

    @Test
    fun clearCommands_refreshStatsAfterSuccess() =
        runBlocking {
            val cache = FakeCacheRepository()
            val cap = StorageOfflineSettingsCapabilityImpl(FakeSettingsRepository(AppSettings()), cache)

            assertEquals(
                StorageOfflineSettingsResult.Success,
                cap.execute(StorageOfflineSettingsCommand.ClearImageCache),
            )
            assertEquals(1, cache.clearImageCalls.get())
            assertEquals(1, cache.statsCalls.get())

            assertEquals(
                StorageOfflineSettingsResult.Success,
                cap.execute(StorageOfflineSettingsCommand.ClearAttachmentCache),
            )
            assertEquals(1, cache.clearAttachmentCalls.get())
            assertEquals(2, cache.statsCalls.get())

            assertEquals(
                StorageOfflineSettingsResult.Success,
                cap.execute(StorageOfflineSettingsCommand.ClearAllCache),
            )
            assertEquals(1, cache.clearAllCalls.get())
            assertEquals(3, cache.statsCalls.get())
            assertEquals(cache.stats, cap.observe().first().cacheStats)
        }

    @Test
    fun singleFailure_keepsOldStats_andMapsStorageFailure() =
        runBlocking {
            val cache = FakeCacheRepository()
            val cap = StorageOfflineSettingsCapabilityImpl(FakeSettingsRepository(AppSettings()), cache)
            assertEquals(
                StorageOfflineSettingsResult.Success,
                cap.execute(StorageOfflineSettingsCommand.RefreshStats),
            )
            val old = cache.stats
            assertEquals(old, cap.observe().first().cacheStats)

            cache.failClear = true
            val result = cap.execute(StorageOfflineSettingsCommand.ClearImageCache)
            assertEquals(
                StorageOfflineSettingsResult.Failure(SettingsCapabilityError.StorageFailure),
                result,
            )
            // 失败时不应丢掉旧统计
            assertEquals(old, cap.observe().first().cacheStats)
        }

    @Test
    fun cacheDeletionFailure_reachesCapability_andSkipsStatisticsRefresh() =
        runBlocking {
            val cache = FakeCacheRepository()
            cache.clearAllBlock = { throw IOException("deleteRecursively returned false") }
            val cap = StorageOfflineSettingsCapabilityImpl(FakeSettingsRepository(AppSettings()), cache)

            assertEquals(
                StorageOfflineSettingsResult.Failure(SettingsCapabilityError.StorageFailure),
                cap.execute(StorageOfflineSettingsCommand.ClearAllCache),
            )
            assertEquals(1, cache.clearAllCalls.get())
            assertEquals(0, cache.statsCalls.get())
            assertNull(cap.observe().first().cacheStats)
        }

    @Test
    fun concurrentClearAll_secondIsIgnoredDuplicate() =
        runBlocking {
            val cache = FakeCacheRepository(holdMs = 250L)
            val cap = StorageOfflineSettingsCapabilityImpl(FakeSettingsRepository(AppSettings()), cache)
            val first =
                async(Dispatchers.IO) {
                    cap.execute(StorageOfflineSettingsCommand.ClearAllCache)
                }
            while (cache.clearAllCalls.get() == 0) {
                delay(5)
            }
            val second =
                withContext(Dispatchers.IO) {
                    cap.execute(StorageOfflineSettingsCommand.ClearAllCache)
                }
            assertEquals(StorageOfflineSettingsResult.IgnoredDuplicate, second)
            assertEquals(StorageOfflineSettingsResult.Success, first.await())
            assertEquals(1, cache.clearAllCalls.get())
        }

    @Test
    fun refreshThenCleanup_areSerialized_andCleanupStatisticsWin() =
        runBlocking {
            val refreshEntered = CompletableDeferred<Unit>()
            val releaseRefresh = CompletableDeferred<Unit>()
            val staleStats =
                CacheStats(
                    databaseBytes = 100,
                    imageCacheBytes = 200,
                    attachmentCacheBytes = 300,
                    otherCacheBytes = 400,
                )
            val cleanupStats =
                CacheStats(
                    databaseBytes = 100,
                    imageCacheBytes = 0,
                    attachmentCacheBytes = 0,
                    otherCacheBytes = 4,
                )
            val cache = FakeCacheRepository()
            cache.statsBlock = { call ->
                if (call == 1) {
                    refreshEntered.complete(Unit)
                    releaseRefresh.await()
                    staleStats
                } else {
                    cleanupStats
                }
            }
            val cap = StorageOfflineSettingsCapabilityImpl(FakeSettingsRepository(AppSettings()), cache)

            val refresh =
                async(Dispatchers.IO) {
                    cap.execute(StorageOfflineSettingsCommand.RefreshStats)
                }
            refreshEntered.await()
            val cleanup =
                async(Dispatchers.IO) {
                    cap.execute(StorageOfflineSettingsCommand.ClearAllCache)
                }

            delay(50)
            assertEquals(0, cache.clearAllCalls.get())
            releaseRefresh.complete(Unit)

            assertEquals(StorageOfflineSettingsResult.Success, refresh.await())
            assertEquals(StorageOfflineSettingsResult.Success, cleanup.await())
            assertEquals(cleanupStats, cap.observe().first().cacheStats)
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

        override suspend fun setOfflineImagePrefetchEnabled(enabled: Boolean) {
            flow.value = flow.value.copy(offlineImagePrefetchEnabled = enabled)
        }

        override suspend fun setOfflineImagePrefetchMaxMemos(count: Int) {
            flow.value = flow.value.copy(offlineImagePrefetchMaxMemos = count)
        }

        override suspend fun setOfflineImagePrefetchMaxImages(count: Int) {
            flow.value = flow.value.copy(offlineImagePrefetchMaxImages = count)
        }

        override suspend fun setAttachmentCacheMaxMb(mb: Int) {
            flow.value = flow.value.copy(attachmentCacheMaxMb = mb)
        }

        override suspend fun setAttachmentUploadMaxMb(mb: Int) = Unit
        override suspend fun setTodoReminderMode(mode: TodoReminderMode) = Unit
        override suspend fun setCalendarIntegrationEnabled(enabled: Boolean) = Unit
        override suspend fun setCalendarIntegrationCalendarId(calendarId: Long?) = Unit
        override suspend fun setCalendarIntegrationSyncReminders(enabled: Boolean) = Unit
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
        override suspend fun acknowledgeFullSyncCompletion(runId: String) = Unit
        override suspend fun setFullSyncFailed(
            runId: String,
            stage: FullSyncStage,
            pagesFetched: Int,
            itemsFetched: Int,
            error: String,
        ) = Unit
    }

    private class FakeCacheRepository(
        private val holdMs: Long = 0L,
    ) : CacheRepository {
        val stats =
            CacheStats(
                databaseBytes = 10,
                imageCacheBytes = 20,
                attachmentCacheBytes = 30,
                otherCacheBytes = 40,
            )
        val statsCalls = AtomicInteger(0)
        val clearImageCalls = AtomicInteger(0)
        val clearAttachmentCalls = AtomicInteger(0)
        val clearAllCalls = AtomicInteger(0)
        var failClear: Boolean = false
        var statsBlock: suspend (Int) -> CacheStats = { stats }
        var clearAllBlock: suspend () -> Unit = {}

        override suspend fun getCacheStats(): CacheStats {
            val call = statsCalls.incrementAndGet()
            return statsBlock(call)
        }

        override suspend fun clearImageCache() {
            clearImageCalls.incrementAndGet()
            if (failClear) throw IOException("disk full")
            if (holdMs > 0L) Thread.sleep(holdMs)
        }

        override suspend fun clearAttachmentCache() {
            clearAttachmentCalls.incrementAndGet()
            if (failClear) throw IOException("disk full")
            if (holdMs > 0L) Thread.sleep(holdMs)
        }

        override suspend fun clearAllCache() {
            clearAllCalls.incrementAndGet()
            if (failClear) throw IOException("disk full")
            if (holdMs > 0L) Thread.sleep(holdMs)
            clearAllBlock()
        }

        override suspend fun ensureImageAttachmentCached(
            serverBase: String,
            memoUuid: String,
            remoteName: String,
            filename: String,
        ): String? = null
    }
}
