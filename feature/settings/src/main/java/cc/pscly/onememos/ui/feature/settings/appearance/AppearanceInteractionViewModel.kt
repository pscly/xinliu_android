package cc.pscly.onememos.ui.feature.settings.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import cc.pscly.onememos.domain.settings.AppearanceInteractionSettingsCapability
import cc.pscly.onememos.domain.settings.AppearanceInteractionSettingsCommand
import cc.pscly.onememos.domain.settings.AppearanceInteractionSettingsResult
import cc.pscly.onememos.domain.settings.AppearanceInteractionSettingsSnapshot
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.domain.settings.SettingsPlatformAction
import cc.pscly.onememos.ui.feature.settings.common.SettingsMessage
import cc.pscly.onememos.ui.feature.settings.common.SettingsPlatformResult
import cc.pscly.onememos.ui.feature.settings.common.SettingsUiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppearanceInteractionUiState(
    val loading: Boolean = true,
    val snapshot: AppearanceInteractionSettingsSnapshot? = null,
    val persistentError: SettingsCapabilityError? = null,
) {
    fun isCommandInFlight(command: AppearanceInteractionSettingsCommand): Boolean =
        snapshot?.commandInFlight == command
}

sealed interface AppearanceInteractionUserIntent {
    data class SetThemePalette(val palette: ThemePalette) : AppearanceInteractionUserIntent

    data class SetThemeMode(val mode: ThemeMode) : AppearanceInteractionUserIntent

    data class SetQuickCaptureOverlayEnabled(val enabled: Boolean) : AppearanceInteractionUserIntent

    data class SetSealStampDurationMs(val value: Int) : AppearanceInteractionUserIntent

    data class ApplyPlatformResult(val result: SettingsPlatformResult) : AppearanceInteractionUserIntent
}

@HiltViewModel
class AppearanceInteractionViewModel @Inject constructor(
    private val capability: AppearanceInteractionSettingsCapability,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppearanceInteractionUiState())
    val uiState: StateFlow<AppearanceInteractionUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsUiEvent>(replay = 0, extraBufferCapacity = 1)
    val events: SharedFlow<SettingsUiEvent> = _events.asSharedFlow()

    private var overlayCommandGeneration = 0L
    private var overlayPermissionGeneration: Long? = null

    init {
        capability
            .observe()
            .onEach { snapshot ->
                _uiState.update { it.copy(loading = false, snapshot = snapshot) }
            }
            .launchIn(viewModelScope)
    }

    fun onIntent(intent: AppearanceInteractionUserIntent) {
        when (intent) {
            is AppearanceInteractionUserIntent.SetThemePalette ->
                execute(AppearanceInteractionSettingsCommand.SetThemePalette(intent.palette))
            is AppearanceInteractionUserIntent.SetThemeMode ->
                execute(AppearanceInteractionSettingsCommand.SetThemeMode(intent.mode))
            is AppearanceInteractionUserIntent.SetQuickCaptureOverlayEnabled -> {
                val generation = ++overlayCommandGeneration
                overlayPermissionGeneration = null
                execute(
                    AppearanceInteractionSettingsCommand.SetQuickCaptureOverlayEnabled(
                        intent.enabled,
                    ),
                    overlayGeneration = generation,
                )
            }
            is AppearanceInteractionUserIntent.SetSealStampDurationMs ->
                execute(AppearanceInteractionSettingsCommand.SetSealStampDurationMs(intent.value))
            is AppearanceInteractionUserIntent.ApplyPlatformResult ->
                applyPlatformResult(intent.result)
        }
    }

    private fun execute(
        command: AppearanceInteractionSettingsCommand,
        overlayGeneration: Long? = null,
    ) {
        if (_uiState.value.isCommandInFlight(command)) return
        viewModelScope.launch {
            val result = capability.execute(command)
            if (overlayGeneration != null && overlayGeneration != overlayCommandGeneration) return@launch
            when (result) {
                AppearanceInteractionSettingsResult.Success -> {
                    _uiState.update { it.copy(persistentError = null) }
                    _events.emit(SettingsUiEvent.Toast(SettingsMessage.COMMAND_SUCCEEDED))
                }
                AppearanceInteractionSettingsResult.IgnoredDuplicate -> Unit
                is AppearanceInteractionSettingsResult.Platform -> {
                    if (
                        command == AppearanceInteractionSettingsCommand
                            .SetQuickCaptureOverlayEnabled(true) &&
                        result.action is SettingsPlatformAction.OpenOverlayPermissionSettings
                    ) {
                        overlayPermissionGeneration = overlayGeneration
                    }
                    _events.emit(SettingsUiEvent.Platform(result.action))
                }
                is AppearanceInteractionSettingsResult.Failure -> {
                    publishError(result.error, SettingsMessage.COMMAND_FAILED)
                }
            }
        }
    }

    private fun applyPlatformResult(result: SettingsPlatformResult) {
        when (result) {
            is SettingsPlatformResult.OverlayPermissionChanged -> {
                val generation = overlayPermissionGeneration ?: return
                overlayPermissionGeneration = null
                if (result.granted) {
                    execute(
                        AppearanceInteractionSettingsCommand
                            .SetQuickCaptureOverlayEnabled(true),
                        overlayGeneration = generation,
                    )
                } else {
                    viewModelScope.launch {
                        if (generation == overlayCommandGeneration) {
                            publishError(
                                SettingsCapabilityError.PermissionDenied,
                                SettingsMessage.PERMISSION_DENIED,
                            )
                        }
                    }
                }
            }
            is SettingsPlatformResult.Failed -> {
                val generation = overlayPermissionGeneration ?: return
                overlayPermissionGeneration = null
                viewModelScope.launch {
                    if (generation == overlayCommandGeneration) {
                        publishError(result.error, SettingsMessage.COMMAND_FAILED)
                    }
                }
            }
            SettingsPlatformResult.Completed,
            is SettingsPlatformResult.Permissions,
            -> Unit
        }
    }

    private suspend fun publishError(
        error: SettingsCapabilityError,
        message: SettingsMessage,
    ) {
        _uiState.update { it.copy(persistentError = error) }
        _events.emit(SettingsUiEvent.Toast(message))
    }
}
