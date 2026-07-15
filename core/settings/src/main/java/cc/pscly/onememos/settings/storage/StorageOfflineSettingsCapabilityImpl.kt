package cc.pscly.onememos.settings.storage

import cc.pscly.onememos.domain.model.CacheStats
import cc.pscly.onememos.domain.repository.CacheRepository
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.domain.settings.StorageOfflineSettingsCapability
import cc.pscly.onememos.domain.settings.StorageOfflineSettingsCommand
import cc.pscly.onememos.domain.settings.StorageOfflineSettingsResult
import cc.pscly.onememos.domain.settings.StorageOfflineSettingsSnapshot
import cc.pscly.onememos.settings.SettingsCapabilityErrorMapper
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex

/**
 * 存储与离线深能力：组合设置与缓存统计。
 * observe 不自行全盘扫描，只有已有统计或显式命令才访问 CacheRepository。
 */
@Singleton
class StorageOfflineSettingsCapabilityImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val cacheRepository: CacheRepository,
) : StorageOfflineSettingsCapability {
    private val commandInFlight = MutableStateFlow<StorageOfflineSettingsCommand?>(null)
    private val cacheStats = MutableStateFlow<CacheStats?>(null)
    private val locks = ConcurrentHashMap<String, Mutex>()

    override fun observe(): Flow<StorageOfflineSettingsSnapshot> =
        combine(settingsRepository.settings, commandInFlight, cacheStats) { settings, inFlight, stats ->
            StorageOfflineSettingsSnapshot(
                imagePrefetchEnabled = settings.offlineImagePrefetchEnabled,
                prefetchMemoLimit = settings.offlineImagePrefetchMaxMemos,
                prefetchImageLimit = settings.offlineImagePrefetchMaxImages,
                attachmentCacheLimitMb = settings.attachmentCacheMaxMb,
                cacheStats = stats,
                commandInFlight = inFlight,
            )
        }

    override suspend fun execute(command: StorageOfflineSettingsCommand): StorageOfflineSettingsResult {
        val lock = locks.getOrPut(command.lockKey()) { Mutex() }
        if (!lock.tryLock()) {
            return StorageOfflineSettingsResult.IgnoredDuplicate
        }
        commandInFlight.value = command
        return try {
            when (command) {
                is StorageOfflineSettingsCommand.SetImagePrefetchEnabled -> {
                    settingsRepository.setOfflineImagePrefetchEnabled(command.enabled)
                    StorageOfflineSettingsResult.Success
                }
                is StorageOfflineSettingsCommand.SetPrefetchMemoLimit -> {
                    if (command.value < 0) {
                        return StorageOfflineSettingsResult.Failure(SettingsCapabilityError.InvalidInput)
                    }
                    settingsRepository.setOfflineImagePrefetchMaxMemos(command.value)
                    StorageOfflineSettingsResult.Success
                }
                is StorageOfflineSettingsCommand.SetPrefetchImageLimit -> {
                    if (command.value < 0) {
                        return StorageOfflineSettingsResult.Failure(SettingsCapabilityError.InvalidInput)
                    }
                    settingsRepository.setOfflineImagePrefetchMaxImages(command.value)
                    StorageOfflineSettingsResult.Success
                }
                is StorageOfflineSettingsCommand.SetAttachmentCacheLimitMb -> {
                    if (command.value < 0) {
                        return StorageOfflineSettingsResult.Failure(SettingsCapabilityError.InvalidInput)
                    }
                    settingsRepository.setAttachmentCacheMaxMb(command.value)
                    StorageOfflineSettingsResult.Success
                }
                StorageOfflineSettingsCommand.RefreshStats -> {
                    refreshStatsOrThrow()
                    StorageOfflineSettingsResult.Success
                }
                StorageOfflineSettingsCommand.ClearImageCache -> {
                    cacheRepository.clearImageCache()
                    refreshStatsOrThrow()
                    StorageOfflineSettingsResult.Success
                }
                StorageOfflineSettingsCommand.ClearAttachmentCache -> {
                    cacheRepository.clearAttachmentCache()
                    refreshStatsOrThrow()
                    StorageOfflineSettingsResult.Success
                }
                StorageOfflineSettingsCommand.ClearAllCache -> {
                    cacheRepository.clearAllCache()
                    refreshStatsOrThrow()
                    StorageOfflineSettingsResult.Success
                }
            }
        } catch (t: Throwable) {
            val mapped = SettingsCapabilityErrorMapper.map(t)
            StorageOfflineSettingsResult.Failure(
                when (mapped) {
                    is SettingsCapabilityError.Unknown,
                    SettingsCapabilityError.NetworkUnavailable,
                    -> SettingsCapabilityError.StorageFailure
                    else -> mapped
                },
            )
        } finally {
            commandInFlight.value = null
            lock.unlock()
        }
    }

    private suspend fun refreshStatsOrThrow() {
        cacheStats.value = cacheRepository.getCacheStats()
    }

    private fun StorageOfflineSettingsCommand.lockKey(): String =
        when (this) {
            is StorageOfflineSettingsCommand.SetImagePrefetchEnabled -> "SetImagePrefetchEnabled"
            is StorageOfflineSettingsCommand.SetPrefetchMemoLimit -> "SetPrefetchMemoLimit"
            is StorageOfflineSettingsCommand.SetPrefetchImageLimit -> "SetPrefetchImageLimit"
            is StorageOfflineSettingsCommand.SetAttachmentCacheLimitMb -> "SetAttachmentCacheLimitMb"
            StorageOfflineSettingsCommand.RefreshStats -> "RefreshStats"
            StorageOfflineSettingsCommand.ClearImageCache -> "ClearImageCache"
            StorageOfflineSettingsCommand.ClearAttachmentCache -> "ClearAttachmentCache"
            StorageOfflineSettingsCommand.ClearAllCache -> "ClearAllCache"
        }
}
