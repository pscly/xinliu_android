package cc.pscly.onememos.ui.feature.settings.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.pscly.onememos.domain.settings.AccountSyncSettingsCapability
import cc.pscly.onememos.domain.settings.AccountSyncSettingsCommand
import cc.pscly.onememos.domain.settings.AccountSyncSettingsResult
import cc.pscly.onememos.domain.settings.AccountSyncSettingsSnapshot
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.navigation.AccountManagementSettingsKey
import cc.pscly.onememos.navigation.AdvancedSyncSettingsKey
import cc.pscly.onememos.navigation.AuthKey
import cc.pscly.onememos.ui.feature.settings.common.SettingsConfirmation
import cc.pscly.onememos.ui.feature.settings.common.SettingsMessage
import cc.pscly.onememos.ui.feature.settings.common.SettingsUiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 账号同步 UI 状态。
 * 命令错误按命令族分区：sync / password / logout / fullResync，互不覆盖。
 */
data class AccountSyncUiState(
    val snapshot: AccountSyncSettingsSnapshot? = null,
    val syncError: SettingsCapabilityError? = null,
    val passwordError: SettingsCapabilityError? = null,
    val logoutError: SettingsCapabilityError? = null,
    val fullResyncError: SettingsCapabilityError? = null,
    /** 密码修改成功代数；界面据此清空输入，失败不递增。 */
    val passwordSuccessGeneration: Int = 0,
)

@HiltViewModel
class AccountSyncViewModel @Inject constructor(
    private val capability: AccountSyncSettingsCapability,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AccountSyncUiState())
    val uiState: StateFlow<AccountSyncUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            capability.observe().collect { snapshot ->
                _uiState.update { it.copy(snapshot = snapshot) }
            }
        }
    }

    private val _events = MutableSharedFlow<SettingsUiEvent>(replay = 0, extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun openLogin() {
        _events.tryEmit(SettingsUiEvent.Navigate(AuthKey()))
    }

    fun openAccountManagement() {
        _events.tryEmit(SettingsUiEvent.Navigate(AccountManagementSettingsKey))
    }

    fun openAdvancedSync() {
        _events.tryEmit(SettingsUiEvent.Navigate(AdvancedSyncSettingsKey))
    }

    fun requestLogout() {
        _events.tryEmit(SettingsUiEvent.Confirm(SettingsConfirmation.LOGOUT))
    }

    fun requestFullResync() {
        _events.tryEmit(SettingsUiEvent.Confirm(SettingsConfirmation.FULL_RESYNC))
    }

    fun syncNow() {
        execute(AccountSyncSettingsCommand.SyncNow)
    }

    fun confirmLogout() {
        execute(AccountSyncSettingsCommand.Logout)
    }

    fun changePassword(
        currentPassword: String,
        newPassword: String,
        repeatedPassword: String,
    ) {
        execute(
            AccountSyncSettingsCommand.ChangePassword(
                currentPassword = currentPassword,
                newPassword = newPassword,
                repeatedPassword = repeatedPassword,
            ),
        )
    }

    fun confirmFullResync() {
        execute(AccountSyncSettingsCommand.FullResync)
    }

    fun acknowledgeFullResyncCompletion(completionId: String) {
        execute(AccountSyncSettingsCommand.AcknowledgeFullResyncCompletion(completionId = completionId))
    }

    private fun execute(command: AccountSyncSettingsCommand) {
        viewModelScope.launch {
            when (val result = capability.execute(command)) {
                AccountSyncSettingsResult.Success -> {
                    onCommandSuccess(command)
                    _events.emit(SettingsUiEvent.Toast(SettingsMessage.COMMAND_SUCCEEDED))
                }
                AccountSyncSettingsResult.IgnoredDuplicate -> Unit
                is AccountSyncSettingsResult.Failure -> {
                    onCommandFailure(command, result.error)
                    _events.emit(SettingsUiEvent.Toast(SettingsMessage.COMMAND_FAILED))
                }
            }
        }
    }

    private fun onCommandSuccess(command: AccountSyncSettingsCommand) {
        _uiState.update { current ->
            when (command) {
                AccountSyncSettingsCommand.SyncNow -> current.copy(syncError = null)
                is AccountSyncSettingsCommand.ChangePassword ->
                    current.copy(
                        passwordError = null,
                        passwordSuccessGeneration = current.passwordSuccessGeneration + 1,
                    )
                AccountSyncSettingsCommand.Logout -> current.copy(logoutError = null)
                AccountSyncSettingsCommand.FullResync,
                is AccountSyncSettingsCommand.AcknowledgeFullResyncCompletion,
                -> current.copy(fullResyncError = null)
            }
        }
    }

    private fun onCommandFailure(
        command: AccountSyncSettingsCommand,
        error: SettingsCapabilityError,
    ) {
        _uiState.update { current ->
            when (command) {
                AccountSyncSettingsCommand.SyncNow -> current.copy(syncError = error)
                is AccountSyncSettingsCommand.ChangePassword -> current.copy(passwordError = error)
                AccountSyncSettingsCommand.Logout -> current.copy(logoutError = error)
                AccountSyncSettingsCommand.FullResync,
                is AccountSyncSettingsCommand.AcknowledgeFullResyncCompletion,
                -> current.copy(fullResyncError = error)
            }
        }
    }
}
