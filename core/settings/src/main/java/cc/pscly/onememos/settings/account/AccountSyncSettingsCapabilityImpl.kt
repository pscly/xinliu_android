package cc.pscly.onememos.settings.account

import cc.pscly.onememos.core.network.ChangePasswordRequest
import cc.pscly.onememos.core.network.FlowBackendApi
import cc.pscly.onememos.data.auth.FlowBackendCredentialStorage
import cc.pscly.onememos.domain.model.FullSyncStatus
import cc.pscly.onememos.domain.model.GlobalSyncState
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.settings.AccountSyncHealth
import cc.pscly.onememos.domain.settings.AccountSyncSettingsCapability
import cc.pscly.onememos.domain.settings.AccountSyncSettingsCommand
import cc.pscly.onememos.domain.settings.AccountSyncSettingsResult
import cc.pscly.onememos.domain.settings.AccountSyncSettingsSnapshot
import cc.pscly.onememos.domain.settings.FullResyncProgress
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.domain.sync.SyncScheduler
import cc.pscly.onememos.domain.sync.SyncStatusMonitor
import cc.pscly.onememos.domain.sync.TodoReminderScheduler
import cc.pscly.onememos.settings.SettingsCapabilityErrorMapper
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex

/**
 * 账号与同步深能力：组合设置、同步状态、凭据与全量同步状态。
 * 每种命令用 Mutex.tryLock 做原子抑制，不引入取消/冲突修复。
 */
@Singleton
class AccountSyncSettingsCapabilityImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val syncStatusMonitor: SyncStatusMonitor,
    private val syncScheduler: SyncScheduler,
    private val todoReminderScheduler: TodoReminderScheduler,
    private val credentialStorage: FlowBackendCredentialStorage,
    private val flowBackendApi: FlowBackendApi,
) : AccountSyncSettingsCapability {
    private val commandInFlight = MutableStateFlow<AccountSyncSettingsCommand?>(null)
    private val locks = ConcurrentHashMap<String, Mutex>()

    override fun observe(): Flow<AccountSyncSettingsSnapshot> =
        combine(
            settingsRepository.settings,
            syncStatusMonitor.globalState,
            commandInFlight,
        ) { settings, global, inFlight ->
            val health = mapHealth(settings = settings, global = global)
            AccountSyncSettingsSnapshot(
                health = health,
                accountLabel = resolveAccountLabel(settings.loginMode),
                lastSuccessAtEpochMs = global.lastSuccessAt.takeIf { it > 0L },
                commandInFlight = inFlight,
            )
        }

    override suspend fun execute(command: AccountSyncSettingsCommand): AccountSyncSettingsResult {
        val lock = locks.getOrPut(command.lockKey()) { Mutex() }
        if (!lock.tryLock()) {
            return AccountSyncSettingsResult.IgnoredDuplicate
        }
        commandInFlight.value = command
        return try {
            when (command) {
                AccountSyncSettingsCommand.SyncNow -> {
                    syncScheduler.requestSync()
                    AccountSyncSettingsResult.Success
                }
                AccountSyncSettingsCommand.FullResync -> {
                    syncScheduler.requestFullResync()
                    AccountSyncSettingsResult.Success
                }
                AccountSyncSettingsCommand.Logout -> {
                    settingsRepository.setToken("")
                    settingsRepository.setLoginMode(LoginMode.UNKNOWN)
                    settingsRepository.setCurrentUserCreator("")
                    credentialStorage.clear()
                    todoReminderScheduler.requestReschedule()
                    AccountSyncSettingsResult.Success
                }
                is AccountSyncSettingsCommand.ChangePassword -> changePassword(command)
            }
        } catch (t: Throwable) {
            AccountSyncSettingsResult.Failure(SettingsCapabilityErrorMapper.map(t))
        } finally {
            commandInFlight.value = null
            lock.unlock()
        }
    }

    private suspend fun changePassword(
        command: AccountSyncSettingsCommand.ChangePassword,
    ): AccountSyncSettingsResult {
        val current = command.currentPassword
        val new1 = command.newPassword
        val new2 = command.repeatedPassword
        if (current.isBlank()) {
            return AccountSyncSettingsResult.Failure(SettingsCapabilityError.InvalidInput)
        }
        if (new1.length < 6) {
            return AccountSyncSettingsResult.Failure(SettingsCapabilityError.InvalidInput)
        }
        if (new1.toByteArray(Charsets.UTF_8).size > 71) {
            return AccountSyncSettingsResult.Failure(SettingsCapabilityError.InvalidInput)
        }
        if (new1 != new2) {
            return AccountSyncSettingsResult.Failure(SettingsCapabilityError.InvalidInput)
        }

        val settings = settingsRepository.settings.first()
        val token = settings.token.trim()
        if (token.isBlank()) {
            return AccountSyncSettingsResult.Failure(SettingsCapabilityError.AuthenticationExpired)
        }

        val resp =
            try {
                flowBackendApi.changePassword(
                    token = "Bearer $token",
                    body =
                        ChangePasswordRequest(
                            currentPassword = current,
                            newPassword = new1,
                            newPassword2 = new2,
                        ),
                )
            } catch (t: Throwable) {
                return AccountSyncSettingsResult.Failure(SettingsCapabilityErrorMapper.map(t))
            }

        if (!resp.isSuccessful) {
            return AccountSyncSettingsResult.Failure(
                SettingsCapabilityErrorMapper.mapHttp(resp.code()),
            )
        }
        val payload = resp.body()
        val ok =
            when {
                payload == null -> false
                payload.code != null && payload.code != 200 -> false
                payload.data?.ok == true -> true
                payload.ok == true -> true
                else -> false
            }
        if (!ok) {
            return AccountSyncSettingsResult.Failure(SettingsCapabilityError.Unknown("CHANGE_PASSWORD_REJECTED"))
        }
        if (settings.loginMode == LoginMode.BACKEND) {
            val cred = credentialStorage.get()
            if (cred != null) {
                credentialStorage.set(username = cred.username, password = new1)
            }
        }
        return AccountSyncSettingsResult.Success
    }

    private fun mapHealth(
        settings: cc.pscly.onememos.domain.model.AppSettings,
        global: GlobalSyncState,
    ): AccountSyncHealth {
        val hasConfiguration =
            settings.serverUrl.isNotBlank() ||
                settings.loginMode == LoginMode.BACKEND ||
                credentialStorage.get() != null
        val signedIn = settings.token.isNotBlank()
        val full = settings.fullSync
        val fullAuthExpired =
            full.status == FullSyncStatus.FAILED && isAuthErrorText(full.lastError)
        val authenticationExpired = global.authInvalid || fullAuthExpired
        val fullResyncRunning = full.status == FullSyncStatus.RUNNING
        val fullResyncError =
            if (full.status == FullSyncStatus.FAILED && !fullAuthExpired) {
                SettingsCapabilityError.Unknown("FULL_RESYNC_FAILED")
            } else {
                null
            }
        val fullResyncCompletedAt =
            if (full.status == FullSyncStatus.SUCCESS && full.lastSuccessAt > 0L) {
                full.lastSuccessAt
            } else {
                null
            }
        val syncError =
            if (global.hasError && !global.authInvalid) {
                if (!global.networkOnline) {
                    SettingsCapabilityError.NetworkUnavailable
                } else {
                    SettingsCapabilityError.Unknown("SYNC_FAILED")
                }
            } else {
                null
            }

        return AccountSyncHealthMapper.map(
            AccountSyncHealthInput(
                hasConfiguration = hasConfiguration,
                signedIn = signedIn,
                authenticationExpired = authenticationExpired,
                fullResyncRunning = fullResyncRunning,
                fullResyncProgress =
                    FullResyncProgress(
                        stage = full.stage,
                        pagesFetched = full.pagesFetched,
                        itemsFetched = full.itemsFetched,
                    ),
                fullResyncError = fullResyncError,
                fullResyncCompletedAt = fullResyncCompletedAt,
                syncing = global.isSyncing,
                queued = global.isEnqueued,
                syncError = syncError,
                lastSuccessAtEpochMs = global.lastSuccessAt.takeIf { it > 0L },
            ),
        )
    }

    private fun resolveAccountLabel(loginMode: LoginMode): String? {
        val cred = credentialStorage.get()
        return when {
            cred != null -> cred.username
            loginMode == LoginMode.CUSTOM -> "custom"
            loginMode == LoginMode.BACKEND -> null
            else -> null
        }
    }

    private fun isAuthErrorText(raw: String): Boolean {
        val t = raw.lowercase()
        return t.contains("401") || t.contains("403") || t.contains("unauthorized") || t.contains("auth")
    }

    private fun AccountSyncSettingsCommand.lockKey(): String =
        when (this) {
            AccountSyncSettingsCommand.SyncNow -> "SyncNow"
            AccountSyncSettingsCommand.FullResync -> "FullResync"
            AccountSyncSettingsCommand.Logout -> "Logout"
            is AccountSyncSettingsCommand.ChangePassword -> "ChangePassword"
        }
}
