package cc.pscly.onememos.ui.feature.settings.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.pscly.onememos.domain.settings.SettingsHubCapability
import cc.pscly.onememos.domain.settings.SettingsHubSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SettingsHubUiState(
    val snapshot: SettingsHubSnapshot? = null,
)

/**
 * 设置首页只读 ViewModel：仅订阅 SettingsHubCapability.observe()。
 * 无写命令、刷新方法或平台事件。
 */
@HiltViewModel
class SettingsHubViewModel @Inject constructor(
    capability: SettingsHubCapability,
) : ViewModel() {
    val uiState: StateFlow<SettingsHubUiState> =
        capability
            .observe()
            .map { SettingsHubUiState(snapshot = it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsHubUiState(),
            )
}
