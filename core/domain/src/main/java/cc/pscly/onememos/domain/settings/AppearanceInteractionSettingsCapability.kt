package cc.pscly.onememos.domain.settings

import cc.pscly.onememos.domain.model.ThemeDescriptor
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import kotlinx.coroutines.flow.Flow

interface AppearanceInteractionSettingsCapability {
    fun observe(): Flow<AppearanceInteractionSettingsSnapshot>

    suspend fun execute(command: AppearanceInteractionSettingsCommand): AppearanceInteractionSettingsResult
}

/**
 * 外观与交互快照。
 * [themeDescriptor] 为完整五元组；[themePalette] 派生自描述符色板轴。
 */
data class AppearanceInteractionSettingsSnapshot(
    val themeDescriptor: ThemeDescriptor,
    val themeMode: ThemeMode,
    val quickCaptureOverlayEnabled: Boolean,
    val sealStampDurationMs: Int,
    val commandInFlight: AppearanceInteractionSettingsCommand? = null,
) {
    val themePalette: ThemePalette
        get() = themeDescriptor.palette
}

sealed interface AppearanceInteractionSettingsCommand {
    data class SetThemeDescriptor(val descriptor: ThemeDescriptor) : AppearanceInteractionSettingsCommand

    data class SetThemePalette(val palette: ThemePalette) : AppearanceInteractionSettingsCommand

    data class SetThemeMode(val mode: ThemeMode) : AppearanceInteractionSettingsCommand

    data class SetQuickCaptureOverlayEnabled(val enabled: Boolean) : AppearanceInteractionSettingsCommand

    data class SetSealStampDurationMs(val value: Int) : AppearanceInteractionSettingsCommand
}

sealed interface AppearanceInteractionSettingsResult {
    data object Success : AppearanceInteractionSettingsResult

    data object IgnoredDuplicate : AppearanceInteractionSettingsResult

    data class Platform(val action: SettingsPlatformAction) : AppearanceInteractionSettingsResult

    data class Failure(val error: SettingsCapabilityError) : AppearanceInteractionSettingsResult
}
