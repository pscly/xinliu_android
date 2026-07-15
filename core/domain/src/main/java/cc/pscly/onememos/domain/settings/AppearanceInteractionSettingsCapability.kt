package cc.pscly.onememos.domain.settings

import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import kotlinx.coroutines.flow.Flow

interface AppearanceInteractionSettingsCapability {
    fun observe(): Flow<AppearanceInteractionSettingsSnapshot>

    suspend fun execute(command: AppearanceInteractionSettingsCommand): AppearanceInteractionSettingsResult
}

data class AppearanceInteractionSettingsSnapshot(
    val themePalette: ThemePalette,
    val themeMode: ThemeMode,
    val quickCaptureOverlayEnabled: Boolean,
    val sealStampDurationMs: Int,
    val commandInFlight: AppearanceInteractionSettingsCommand? = null,
)

sealed interface AppearanceInteractionSettingsCommand {
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
