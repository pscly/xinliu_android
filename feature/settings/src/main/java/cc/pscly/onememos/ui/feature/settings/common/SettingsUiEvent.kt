package cc.pscly.onememos.ui.feature.settings.common

import cc.pscly.onememos.domain.settings.SettingsPlatformAction
import cc.pscly.onememos.domain.update.UpdateDeliveryAction
import cc.pscly.onememos.navigation.OneMemosNavKey

/**
 * Settings 页一次性事件：Navigate / Toast / Confirm / Platform / UpdateDelivery。
 * 使用 SharedFlow(replay=0)，不重放已消费事件。
 */
sealed interface SettingsUiEvent {
    data class Navigate(val key: OneMemosNavKey) : SettingsUiEvent

    data class Toast(val message: SettingsMessage) : SettingsUiEvent

    data class Confirm(val request: SettingsConfirmation) : SettingsUiEvent

    data class Platform(val action: SettingsPlatformAction) : SettingsUiEvent

    data class UpdateDelivery(val action: UpdateDeliveryAction) : SettingsUiEvent
}

enum class SettingsMessage {
    COMMAND_SUCCEEDED,
    COMMAND_FAILED,
    PERMISSION_DENIED,
}

enum class SettingsConfirmation {
    LOGOUT,
    FULL_RESYNC,
    CLEAR_IMAGE_CACHE,
    CLEAR_ATTACHMENT_CACHE,
    CLEAR_ALL_CACHE,
    REBUILD_DERIVED_FIELDS,
}
