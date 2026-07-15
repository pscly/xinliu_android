package cc.pscly.onememos.domain.settings

import cc.pscly.onememos.domain.model.CacheStats
import kotlinx.coroutines.flow.Flow

interface StorageOfflineSettingsCapability {
    fun observe(): Flow<StorageOfflineSettingsSnapshot>

    suspend fun execute(command: StorageOfflineSettingsCommand): StorageOfflineSettingsResult
}

data class StorageOfflineSettingsSnapshot(
    val imagePrefetchEnabled: Boolean,
    val prefetchMemoLimit: Int,
    val prefetchImageLimit: Int,
    val attachmentCacheLimitMb: Int,
    val cacheStats: CacheStats?,
    val commandInFlight: StorageOfflineSettingsCommand? = null,
)

sealed interface StorageOfflineSettingsCommand {
    data class SetImagePrefetchEnabled(val enabled: Boolean) : StorageOfflineSettingsCommand

    data class SetPrefetchMemoLimit(val value: Int) : StorageOfflineSettingsCommand

    data class SetPrefetchImageLimit(val value: Int) : StorageOfflineSettingsCommand

    data class SetAttachmentCacheLimitMb(val value: Int) : StorageOfflineSettingsCommand

    data object RefreshStats : StorageOfflineSettingsCommand

    data object ClearImageCache : StorageOfflineSettingsCommand

    data object ClearAttachmentCache : StorageOfflineSettingsCommand

    data object ClearAllCache : StorageOfflineSettingsCommand
}

sealed interface StorageOfflineSettingsResult {
    data object Success : StorageOfflineSettingsResult

    data object IgnoredDuplicate : StorageOfflineSettingsResult

    data class Failure(val error: SettingsCapabilityError) : StorageOfflineSettingsResult
}
