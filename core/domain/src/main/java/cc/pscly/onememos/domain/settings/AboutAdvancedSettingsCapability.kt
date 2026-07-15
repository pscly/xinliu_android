package cc.pscly.onememos.domain.settings

import cc.pscly.onememos.domain.update.UpdateDeliveryAction
import kotlinx.coroutines.flow.Flow

interface AboutAdvancedSettingsCapability {
    fun observe(): Flow<AboutAdvancedSettingsSnapshot>

    suspend fun execute(command: AboutAdvancedSettingsCommand): AboutAdvancedSettingsResult
}

enum class UpdateSettingsPhase {
    IDLE,
    CHECKING,
    AVAILABLE,
    DOWNLOADING,
    READY_TO_INSTALL,
    UP_TO_DATE,
    ERROR,
}

data class UpdateSettingsSnapshot(
    val phase: UpdateSettingsPhase,
    val availableVersion: String? = null,
    val ignoredVersionTag: String? = null,
    val downloadProgressPercent: Int? = null,
    val error: SettingsCapabilityError? = null,
)

data class DeveloperOptions(
    val unlocked: Boolean,
    val showPublicWorkspaceMemos: Boolean,
    val autoTagLineKeywords: String,
    val showAutoTagLineInHome: Boolean,
    val showAutoTagLineInView: Boolean,
    val showAutoTagLineInEdit: Boolean,
    val homeRichPreviewStickyLimit: Int,
)

data class AboutAdvancedSettingsSnapshot(
    val versionName: String,
    val versionCode: Long,
    val buildType: String,
    val update: UpdateSettingsSnapshot,
    val diagnosticsAvailable: Boolean,
    val attachmentUploadLimitMb: Int,
    val developerOptions: DeveloperOptions,
    val commandInFlight: AboutAdvancedSettingsCommand? = null,
)

sealed interface AboutAdvancedSettingsCommand {
    data object CheckForUpdates : AboutAdvancedSettingsCommand

    data object DownloadUpdate : AboutAdvancedSettingsCommand

    data object InstallUpdate : AboutAdvancedSettingsCommand

    data object ClearIgnoredUpdate : AboutAdvancedSettingsCommand

    data object ExportDiagnostics : AboutAdvancedSettingsCommand

    data object RequestQuickCaptureTile : AboutAdvancedSettingsCommand

    data object RequestScreenshotTile : AboutAdvancedSettingsCommand

    data object OpenQuickCapture : AboutAdvancedSettingsCommand

    data object OpenScreenshotCapture : AboutAdvancedSettingsCommand

    data object RebuildDerivedFields : AboutAdvancedSettingsCommand

    data class SetAttachmentUploadLimitMb(val value: Int) : AboutAdvancedSettingsCommand

    data class SetDeveloperOptions(val options: DeveloperOptions) : AboutAdvancedSettingsCommand
}

sealed interface AboutAdvancedSettingsResult {
    data object Success : AboutAdvancedSettingsResult

    data object IgnoredDuplicate : AboutAdvancedSettingsResult

    data class Platform(val action: SettingsPlatformAction) : AboutAdvancedSettingsResult

    data class UpdateDelivery(val action: UpdateDeliveryAction) : AboutAdvancedSettingsResult

    data class Failure(val error: SettingsCapabilityError) : AboutAdvancedSettingsResult
}
