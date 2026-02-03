package cc.pscly.onememos.ui.feature.start

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.pscly.onememos.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AppStartUiState(
    val showWelcome: Boolean = false,
)

@HiltViewModel
class AppStartViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<AppStartUiState> =
        settingsRepository.settings
            .map { s -> AppStartUiState(showWelcome = !s.welcomeCompleted) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = AppStartUiState(),
            )

    // token 刷新改为由进程级前台监听统一触发：见 OneMemosApplication。
}
