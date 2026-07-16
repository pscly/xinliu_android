package cc.pscly.onememos.ui

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.update.UpdateDeliveryAction
import cc.pscly.onememos.ui.settings.AppSettingsUpdateDeliveryDispatcher
import cc.pscly.onememos.ui.theme.OneMemosThemeConfig
import cc.pscly.onememos.update.AppUpdateDeliveryLauncher
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
    private val updateDeliveryLauncher: AppUpdateDeliveryLauncher,
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

    fun installDownloadedUpdate(activity: Activity?) = updateDeliveryLauncher.requestInstall(activity)

    fun remindUpdateLater() = appUpdateManager.remindLater()

    fun ignoreCurrentUpdate() = appUpdateManager.ignoreCurrentVersion()

    fun clearIgnoredUpdate() = appUpdateManager.clearIgnoredVersion()

    fun dismissUpdatePrompt() = appUpdateManager.dismissPrompt()

    fun onHostResumed(activity: Activity?) = updateDeliveryLauncher.onHostResumed(activity)

    fun settingsUpdateDeliveryDispatcher(
        activityProvider: () -> Activity?,
    ): AppSettingsUpdateDeliveryDispatcher =
        AppSettingsUpdateDeliveryDispatcher(
            launcher = updateDeliveryLauncher,
            activityProvider = activityProvider,
        )

    fun executeSettingsUpdateDelivery(
        action: UpdateDeliveryAction,
        activity: Activity?,
    ) {
        updateDeliveryLauncher.dispatch(action = action, activity = activity)
    }
}
