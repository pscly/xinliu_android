package cc.pscly.onememos.ui.feature.settings.common

import androidx.compose.runtime.staticCompositionLocalOf
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.domain.settings.SettingsPermission
import cc.pscly.onememos.domain.settings.SettingsPlatformAction

/**
 * 平台动作结果：由 app 的 Activity Result / Intent 适配器回传。
 * 不持有页面状态持有者、宿主页面或更新管理器。
 */
sealed interface SettingsPlatformResult {
    data object Completed : SettingsPlatformResult

    data class Permissions(
        val granted: Set<SettingsPermission>,
        val denied: Set<SettingsPermission>,
    ) : SettingsPlatformResult

    data class OverlayPermissionChanged(val granted: Boolean) : SettingsPlatformResult

    data class Failed(val error: SettingsCapabilityError) : SettingsPlatformResult
}

/**
 * Feature 只提交 SettingsPlatformAction；app 实现 launcher 并 callback 结果。
 */
interface SettingsPlatformActionDispatcher {
    fun dispatch(
        action: SettingsPlatformAction,
        onResult: (SettingsPlatformResult) -> Unit,
    )
}

val LocalSettingsPlatformActionDispatcher =
    staticCompositionLocalOf<SettingsPlatformActionDispatcher> {
        error("SettingsPlatformActionDispatcher 未由 app 提供")
    }
