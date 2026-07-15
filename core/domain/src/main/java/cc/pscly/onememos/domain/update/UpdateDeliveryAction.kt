package cc.pscly.onememos.domain.update

sealed interface UpdateDeliveryAction {
    data class OpenUnknownSourcesSettings(
        val packageName: String,
    ) : UpdateDeliveryAction

    data class InstallApk(
        val uri: String,
        val mimeType: String = "application/vnd.android.package-archive",
    ) : UpdateDeliveryAction
}

sealed interface UpdateDeliveryResult {
    data class UnknownSourcesPermissionChanged(
        val granted: Boolean,
    ) : UpdateDeliveryResult

    data object InstallerReturned : UpdateDeliveryResult

    data class Failed(val reason: UpdateDeliveryFailure) : UpdateDeliveryResult
}

enum class UpdateDeliveryFailure {
    ACTIVITY_NOT_FOUND,
    PERMISSION_DENIED,
    PLATFORM_FAILURE,
}
