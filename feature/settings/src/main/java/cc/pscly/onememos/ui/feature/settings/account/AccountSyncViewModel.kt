package cc.pscly.onememos.ui.feature.settings.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.pscly.onememos.domain.settings.AccountSyncSettingsCapability
import cc.pscly.onememos.domain.settings.AccountSyncSettingsCommand
import cc.pscly.onememos.domain.settings.AccountSyncSettingsResult
import cc.pscly.onememos.domain.settings.AccountSyncSettingsSnapshot
import cc.pscly.onememos.navigation.AccountManagementSettingsKey
import cc.pscly.onememos.navigation.AdvancedSyncSettingsKey
import cc.pscly.onememos.navigation.AuthKey
import cc.pscly.onememos.ui.feature.settings.common.SettingsConfirmation
import cc.pscly.onememos.ui.feature.settings.common.SettingsMessage
import cc.pscly.onememos.ui.feature.settings.common.SettingsUiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountSyncUiState(
    val snapshot: AccountSyncSettingsSnapshot? = null,
)

@HiltViewModel
class AccountSyncViewModel @Inject constructor(
    private val capability: AccountSyncSettingsCapability,
) : ViewModel() {
    val uiState: StateFlow<AccountSyncUiState> =
        capability
            .observe()
            .map { AccountSyncUiState(snapshot = it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = AccountSyncUiState(),
            )

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

    private fun execute(command: AccountSyncSettingsCommand) {
        viewModelScope.launch {
            when (capability.execute(command)) {
                AccountSyncSettingsResult.Success ->
                    _events.emit(SettingsUiEvent.Toast(SettingsMessage.COMMAND_SUCCEEDED))
                AccountSyncSettingsResult.IgnoredDuplicate -> Unit
                is AccountSyncSettingsResult.Failure ->
                    _events.emit(SettingsUiEvent.Toast(SettingsMessage.COMMAND_FAILED))
            }
        }
    }
}
