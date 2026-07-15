package cc.pscly.onememos.domain.settings

/**
 * 设置能力层公共错误：纯领域类型，不泄漏异常 message / HTTP body / WorkInfo。
 */
sealed interface SettingsCapabilityError {
    data object AuthenticationExpired : SettingsCapabilityError

    data object NetworkUnavailable : SettingsCapabilityError

    data object PermissionDenied : SettingsCapabilityError

    data object PlatformUnavailable : SettingsCapabilityError

    data object InvalidInput : SettingsCapabilityError

    data object AlreadyRunning : SettingsCapabilityError

    data object StorageFailure : SettingsCapabilityError

    data class Unknown(val diagnosticCode: String) : SettingsCapabilityError
}

sealed interface SettingsPlatformAction {
    data class RequestPermissions(val permissions: Set<SettingsPermission>) : SettingsPlatformAction

    data class OpenOverlayPermissionSettings(val packageName: String) : SettingsPlatformAction

    data class ShareFile(val uri: String, val mimeType: String) : SettingsPlatformAction

    data object OpenQuickCapture : SettingsPlatformAction

    data object StartQuickCaptureOverlay : SettingsPlatformAction

    data object OpenScreenshotCapture : SettingsPlatformAction
}

enum class SettingsPermission {
    READ_CALENDAR,
    WRITE_CALENDAR,
}
