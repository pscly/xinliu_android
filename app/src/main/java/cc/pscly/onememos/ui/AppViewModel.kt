package cc.pscly.onememos.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.ui.theme.OneMemosThemeConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val themeConfig: StateFlow<OneMemosThemeConfig> =
        settingsRepository.settings
            .map { settings ->
                OneMemosThemeConfig(
                    palette = settings.themePalette,
                    themeMode = settings.themeMode,
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = OneMemosThemeConfig(),
            )
}

