package cc.pscly.onememos.settings.account

import cc.pscly.onememos.domain.settings.AccountSyncHealth
import cc.pscly.onememos.domain.settings.FullResyncProgress
import cc.pscly.onememos.domain.settings.SettingsCapabilityError

/**
 * 账号同步健康状态输入。一次映射只产出一个互斥状态。
 */
data class AccountSyncHealthInput(
    val hasConfiguration: Boolean,
    val signedIn: Boolean,
    val authenticationExpired: Boolean,
    val fullResyncRunning: Boolean,
    val fullResyncProgress: FullResyncProgress,
    val fullResyncError: SettingsCapabilityError?,
    val fullResyncCompletedAt: Long?,
    val fullResyncCompletionId: String? = null,
    val syncing: Boolean,
    val queued: Boolean,
    val syncError: SettingsCapabilityError?,
    val lastSuccessAtEpochMs: Long?,
)

/**
 * 固定优先级：
 * 未绑定 > 已配置未登录 > 鉴权失效 > 全量进行中 > 全量失败 > 全量完成 >
 * 常规同步中 > 已排队 > 普通失败 > 健康
 */
object AccountSyncHealthMapper {
    fun map(input: AccountSyncHealthInput): AccountSyncHealth =
        when {
            !input.hasConfiguration -> AccountSyncHealth.Unbound
            !input.signedIn -> AccountSyncHealth.ConfiguredSignedOut
            input.authenticationExpired -> AccountSyncHealth.AuthenticationExpired
            input.fullResyncRunning -> AccountSyncHealth.FullResyncRunning(input.fullResyncProgress)
            input.fullResyncError != null -> AccountSyncHealth.FullResyncFailed(input.fullResyncError)
            input.fullResyncCompletedAt != null -> AccountSyncHealth.FullResyncCompleted(
                completionId = input.fullResyncCompletionId.orEmpty(),
                completedAtEpochMs = input.fullResyncCompletedAt,
            )
            input.syncing -> AccountSyncHealth.Syncing
            input.queued -> AccountSyncHealth.Queued
            input.syncError != null -> AccountSyncHealth.Failed(input.syncError)
            else -> AccountSyncHealth.Healthy(input.lastSuccessAtEpochMs)
        }
}
