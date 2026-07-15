package cc.pscly.onememos.ui.feature.settings.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.pscly.onememos.domain.settings.AboutAdvancedSettingsCapability
import cc.pscly.onememos.domain.settings.AboutAdvancedSettingsCommand
import cc.pscly.onememos.domain.settings.AboutAdvancedSettingsResult
import cc.pscly.onememos.domain.settings.AboutAdvancedSettingsSnapshot
import cc.pscly.onememos.domain.settings.DeveloperOptions
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.ui.feature.settings.common.SettingsConfirmation
import cc.pscly.onememos.ui.feature.settings.common.SettingsMessage
import cc.pscly.onememos.ui.feature.settings.common.SettingsUiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 关于与高级能力页 ViewModel：仅注入 AboutAdvancedSettingsCapability。
 * 稳定状态来自 observe()；平台与更新交付经 SettingsUiEvent 单次发送（replay=0）。
 * APK 安装 / 未知来源设置只走 UpdateDelivery，绝不发 Platform。
 * 进入/订阅不检查网络、不导出诊断、不下载、不安装。
 */
data class AboutAdvancedUiState(
    val loading: Boolean = true,
    val snapshot: AboutAdvancedSettingsSnapshot? = null,
    val persistentError: SettingsCapabilityError? = null,
) {
    /** 命令进行中时禁用重复提交。 */
    val actionsDisabled: Boolean
        get() = snapshot?.commandInFlight != null
}

@HiltViewModel
class AboutAdvancedViewModel @Inject constructor(
    private val capability: AboutAdvancedSettingsCapability,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AboutAdvancedUiState())
    val uiState: StateFlow<AboutAdvancedUiState> = _uiState.asStateFlow()

    private val _events =
        MutableSharedFlow<SettingsUiEvent>(
            replay = 0,
            extraBufferCapacity = 16,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val events: SharedFlow<SettingsUiEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            capability.observe().collect { snap ->
                _uiState.update { current ->
                    current.copy(
                        loading = false,
                        snapshot = snap,
                        persistentError = current.persistentError,
                    )
                }
            }
        }
    }

    fun onCheckForUpdates() = execute(AboutAdvancedSettingsCommand.CheckForUpdates)

    fun onDownloadUpdate() = execute(AboutAdvancedSettingsCommand.DownloadUpdate)

    fun onInstallUpdate() = execute(AboutAdvancedSettingsCommand.InstallUpdate)

    fun onClearIgnoredUpdate() = execute(AboutAdvancedSettingsCommand.ClearIgnoredUpdate)

    fun onExportDiagnostics() = execute(AboutAdvancedSettingsCommand.ExportDiagnostics)

    fun onRequestQuickCaptureTile() = execute(AboutAdvancedSettingsCommand.RequestQuickCaptureTile)

    fun onRequestScreenshotTile() = execute(AboutAdvancedSettingsCommand.RequestScreenshotTile)

    fun onOpenQuickCapture() = execute(AboutAdvancedSettingsCommand.OpenQuickCapture)

    fun onOpenScreenshotCapture() = execute(AboutAdvancedSettingsCommand.OpenScreenshotCapture)

    /** 仅发出确认事件，确认后再执行重建。 */
    fun onRequestRebuildDerivedFields() {
        send(SettingsUiEvent.Confirm(SettingsConfirmation.REBUILD_DERIVED_FIELDS))
    }

    fun onConfirmRebuildDerivedFields() = execute(AboutAdvancedSettingsCommand.RebuildDerivedFields)

    fun onSetAttachmentUploadLimitMb(value: Int) =
        execute(AboutAdvancedSettingsCommand.SetAttachmentUploadLimitMb(value))

    fun onSetDeveloperOptions(options: DeveloperOptions) =
        execute(AboutAdvancedSettingsCommand.SetDeveloperOptions(options))

    private fun execute(command: AboutAdvancedSettingsCommand) {
        if (_uiState.value.actionsDisabled) {
            return
        }
        viewModelScope.launch {
            when (val result = capability.execute(command)) {
                AboutAdvancedSettingsResult.Success -> {
                    _uiState.update { it.copy(persistentError = null) }
                    send(SettingsUiEvent.Toast(SettingsMessage.COMMAND_SUCCEEDED))
                }
                AboutAdvancedSettingsResult.IgnoredDuplicate -> {
                    // 能力层互斥：不覆盖错误、不发失败 toast
                }
                is AboutAdvancedSettingsResult.Platform -> {
                    _uiState.update { it.copy(persistentError = null) }
                    send(SettingsUiEvent.Platform(result.action))
                }
                is AboutAdvancedSettingsResult.UpdateDelivery -> {
                    _uiState.update { it.copy(persistentError = null) }
                    send(SettingsUiEvent.UpdateDelivery(result.action))
                }
                is AboutAdvancedSettingsResult.Failure -> {
                    // 只保留领域错误，不泄漏 HTTP body / 异常 message
                    _uiState.update { it.copy(persistentError = result.error) }
                    send(SettingsUiEvent.Toast(SettingsMessage.COMMAND_FAILED))
                }
            }
        }
    }

    private fun send(event: SettingsUiEvent) {
        // tryEmit 在 extraBufferCapacity=1 时保证 STARTED 收集者单次消费；不重放
        if (!_events.tryEmit(event)) {
            viewModelScope.launch { _events.emit(event) }
        }
    }
}
