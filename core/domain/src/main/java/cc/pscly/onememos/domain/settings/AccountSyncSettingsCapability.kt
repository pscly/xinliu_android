package cc.pscly.onememos.domain.settings

import cc.pscly.onememos.domain.model.FullSyncStage
import kotlinx.coroutines.flow.Flow

interface AccountSyncSettingsCapability {
    fun observe(): Flow<AccountSyncSettingsSnapshot>

    suspend fun execute(command: AccountSyncSettingsCommand): AccountSyncSettingsResult
}

sealed interface AccountSyncHealth {
    data object Unbound : AccountSyncHealth

    data object ConfiguredSignedOut : AccountSyncHealth

    data class Healthy(val lastSuccessAtEpochMs: Long?) : AccountSyncHealth

    data object Syncing : AccountSyncHealth

    data object Queued : AccountSyncHealth

    data class Failed(val error: SettingsCapabilityError) : AccountSyncHealth

    data object AuthenticationExpired : AccountSyncHealth

    data class FullResyncRunning(val progress: FullResyncProgress) : AccountSyncHealth

    data class FullResyncFailed(val error: SettingsCapabilityError) : AccountSyncHealth

    data class FullResyncCompleted(val completedAtEpochMs: Long) : AccountSyncHealth
}

data class FullResyncProgress(
    val stage: FullSyncStage,
    val pagesFetched: Int,
    val itemsFetched: Int,
)

data class AccountSyncSettingsSnapshot(
    val health: AccountSyncHealth,
    val accountLabel: String?,
    val lastSuccessAtEpochMs: Long?,
    val commandInFlight: AccountSyncSettingsCommand?,
)

sealed interface AccountSyncSettingsCommand {
    data object SyncNow : AccountSyncSettingsCommand

    data object Logout : AccountSyncSettingsCommand

    data class ChangePassword(
        val currentPassword: String,
        val newPassword: String,
        val repeatedPassword: String,
    ) : AccountSyncSettingsCommand

    data object FullResync : AccountSyncSettingsCommand
}

sealed interface AccountSyncSettingsResult {
    data object Success : AccountSyncSettingsResult

    data object IgnoredDuplicate : AccountSyncSettingsResult

    data class Failure(val error: SettingsCapabilityError) : AccountSyncSettingsResult
}
