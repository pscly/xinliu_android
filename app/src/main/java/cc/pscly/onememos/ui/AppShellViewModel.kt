package cc.pscly.onememos.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AppShellUiState(
    val showTodo: Boolean = false,
    val showCollections: Boolean = false,
)

@HiltViewModel
class AppShellViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<AppShellUiState> =
        settingsRepository.settings
            .map { s ->
                val ok = s.loginMode == LoginMode.BACKEND && s.token.isNotBlank()
                AppShellUiState(
                    showTodo = ok,
                    showCollections = ok,
                )
            }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = AppShellUiState(),
            )
}
