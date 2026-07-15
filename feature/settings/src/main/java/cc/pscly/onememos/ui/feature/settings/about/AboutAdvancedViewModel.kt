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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 操作分区：稳定错误挂在发起操作的分区，不跨区误显示到更新卡片。
 */
enum class AboutErrorSection {
    UPDATE,
    DIAGNOSTICS,
    TILES,
    CAPTURE,
    REBUILD,
    UPLOAD,
    DEVELOPER,
}

/** 动态结果播报种类；配合 announcementToken 保证连续相同结果仍可观察。 */
enum class AboutAnnouncementKind {
    SUCCESS,
    FAILED,
}

/**
 * 关于与高级能力页 ViewModel：仅注入 AboutAdvancedSettingsCapability。
 * 稳定状态来自 observe()；平台与更新交付经 SettingsUiEvent 单次发送（replay=0, buffer=1）。
 * APK 安装 / 未知来源设置只走 UpdateDelivery，绝不发 Platform。
 * 进入/订阅不检查网络、不导出诊断、不下载、不安装。
 */
data class AboutAdvancedUiState(
    val loading: Boolean = true,
    val snapshot: AboutAdvancedSettingsSnapshot? = null,
    /** 分区稳定错误（领域类型，不泄漏 HTTP/异常文本）。 */
    val sectionError: SettingsCapabilityError? = null,
    val errorSection: AboutErrorSection? = null,
    /** 单调递增，保证连续相同结果仍更新语义。 */
    val announcementToken: Long = 0L,
    val announcementKind: AboutAnnouncementKind? = null,
) {
    /** 命令进行中时禁用重复提交。 */
    val actionsDisabled: Boolean
        get() = snapshot?.commandInFlight != null

    /** 兼容既有测试：当前分区错误。 */
    val persistentError: SettingsCapabilityError?
        get() = sectionError
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
            extraBufferCapacity = 1,
        )
    val events: SharedFlow<SettingsUiEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            capability.observe().collect { snap ->
                _uiState.update { current ->
                    current.copy(
                        loading = false,
                        snapshot = snap,
                        // 快照刷新保留分区错误，直至本区成功清除或本区失败覆盖
                        sectionError = current.sectionError,
                        errorSection = current.errorSection,
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
        viewModelScope.launch {
            _events.emit(SettingsUiEvent.Confirm(SettingsConfirmation.REBUILD_DERIVED_FIELDS))
        }
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
        val section = sectionOf(command)
        viewModelScope.launch {
            when (val result = capability.execute(command)) {
                AboutAdvancedSettingsResult.Success -> {
                    _uiState.update { current ->
                        val clearOwn =
                            current.errorSection == null || current.errorSection == section
                        current.copy(
                            sectionError = if (clearOwn) null else current.sectionError,
                            errorSection = if (clearOwn) null else current.errorSection,
                            announcementToken = current.announcementToken + 1,
                            announcementKind = AboutAnnouncementKind.SUCCESS,
                        )
                    }
                    _events.emit(SettingsUiEvent.Toast(SettingsMessage.COMMAND_SUCCEEDED))
                }
                AboutAdvancedSettingsResult.IgnoredDuplicate -> {
                    // 能力层互斥：不覆盖错误、不发失败 toast
                }
                is AboutAdvancedSettingsResult.Platform -> {
                    _uiState.update { current ->
                        val clearOwn =
                            current.errorSection == null || current.errorSection == section
                        current.copy(
                            sectionError = if (clearOwn) null else current.sectionError,
                            errorSection = if (clearOwn) null else current.errorSection,
                            announcementToken = current.announcementToken + 1,
                            announcementKind = AboutAnnouncementKind.SUCCESS,
                        )
                    }
                    _events.emit(SettingsUiEvent.Platform(result.action))
                }
                is AboutAdvancedSettingsResult.UpdateDelivery -> {
                    _uiState.update { current ->
                        val clearOwn =
                            current.errorSection == null || current.errorSection == section
                        current.copy(
                            sectionError = if (clearOwn) null else current.sectionError,
                            errorSection = if (clearOwn) null else current.errorSection,
                            announcementToken = current.announcementToken + 1,
                            announcementKind = AboutAnnouncementKind.SUCCESS,
                        )
                    }
                    _events.emit(SettingsUiEvent.UpdateDelivery(result.action))
                }
                is AboutAdvancedSettingsResult.Failure -> {
                    // 只保留领域错误，挂到发起分区
                    _uiState.update { current ->
                        current.copy(
                            sectionError = result.error,
                            errorSection = section,
                            announcementToken = current.announcementToken + 1,
                            announcementKind = AboutAnnouncementKind.FAILED,
                        )
                    }
                    _events.emit(SettingsUiEvent.Toast(SettingsMessage.COMMAND_FAILED))
                }
            }
        }
    }

    private fun sectionOf(command: AboutAdvancedSettingsCommand): AboutErrorSection =
        when (command) {
            AboutAdvancedSettingsCommand.CheckForUpdates,
            AboutAdvancedSettingsCommand.DownloadUpdate,
            AboutAdvancedSettingsCommand.InstallUpdate,
            AboutAdvancedSettingsCommand.ClearIgnoredUpdate,
            -> AboutErrorSection.UPDATE
            AboutAdvancedSettingsCommand.ExportDiagnostics -> AboutErrorSection.DIAGNOSTICS
            AboutAdvancedSettingsCommand.RequestQuickCaptureTile,
            AboutAdvancedSettingsCommand.RequestScreenshotTile,
            -> AboutErrorSection.TILES
            AboutAdvancedSettingsCommand.OpenQuickCapture,
            AboutAdvancedSettingsCommand.OpenScreenshotCapture,
            -> AboutErrorSection.CAPTURE
            AboutAdvancedSettingsCommand.RebuildDerivedFields -> AboutErrorSection.REBUILD
            is AboutAdvancedSettingsCommand.SetAttachmentUploadLimitMb -> AboutErrorSection.UPLOAD
            is AboutAdvancedSettingsCommand.SetDeveloperOptions -> AboutErrorSection.DEVELOPER
        }
}
