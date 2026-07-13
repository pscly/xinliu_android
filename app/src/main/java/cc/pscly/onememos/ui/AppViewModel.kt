package cc.pscly.onememos.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.ui.theme.OneMemosThemeConfig
import cc.pscly.onememos.update.AppUpdateManager
import cc.pscly.onememos.update.AppUpdateUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val appUpdateManager: AppUpdateManager,
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

    val updateUiState: StateFlow<AppUpdateUiState> = appUpdateManager.uiState

    fun checkForUpdatesAutomatically() = appUpdateManager.checkForUpdates(manual = false)

    fun checkForUpdatesManually() = appUpdateManager.checkForUpdates(manual = true)

    fun startUpdateDownload() = appUpdateManager.startDownload()

    fun installDownloadedUpdate() = appUpdateManager.requestInstall()

    fun remindUpdateLater() = appUpdateManager.remindLater()

    fun ignoreCurrentUpdate() = appUpdateManager.ignoreCurrentVersion()

    fun clearIgnoredUpdate() = appUpdateManager.clearIgnoredVersion()

    fun dismissUpdatePrompt() = appUpdateManager.dismissPrompt()

    fun onHostResumed() = appUpdateManager.onHostResumed()
}
