package cc.pscly.onememos.ui.feature.settings.common

import androidx.compose.runtime.staticCompositionLocalOf
import cc.pscly.onememos.domain.update.UpdateDeliveryAction
import cc.pscly.onememos.domain.update.UpdateDeliveryResult

/**
 * 更新交付窄接口：只接收 UpdateDeliveryAction，回传 UpdateDeliveryResult。
 * 不经过 SettingsPlatformActionDispatcher，不持有更新管理器。
 */
interface SettingsUpdateDeliveryDispatcher {
    fun dispatch(
        action: UpdateDeliveryAction,
        onResult: (UpdateDeliveryResult) -> Unit,
    )
}

val LocalSettingsUpdateDeliveryDispatcher =
    staticCompositionLocalOf<SettingsUpdateDeliveryDispatcher> {
        error("SettingsUpdateDeliveryDispatcher 未由 app 提供")
    }
