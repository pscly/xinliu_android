package cc.pscly.onememos.settings.appearance

import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.settings.AppearanceInteractionSettingsCapability
import cc.pscly.onememos.domain.settings.AppearanceInteractionSettingsCommand
import cc.pscly.onememos.domain.settings.AppearanceInteractionSettingsResult
import cc.pscly.onememos.domain.settings.AppearanceInteractionSettingsSnapshot
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.domain.settings.SettingsPlatformAction
import cc.pscly.onememos.quicktiles.OverlayPermissionGateway
import cc.pscly.onememos.settings.SettingsCapabilityErrorMapper
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex

/**
 * 外观与交互深能力：主题/明暗/悬浮记录/盖章时长。
 * 悬浮窗权限通过 OverlayPermissionGateway 窄端口读取，不持有 Activity。
 */
@Singleton
class AppearanceInteractionSettingsCapabilityImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val overlayPermissionGateway: OverlayPermissionGateway,
) : AppearanceInteractionSettingsCapability {
    private val commandInFlight = MutableStateFlow<AppearanceInteractionSettingsCommand?>(null)
    private val locks = ConcurrentHashMap<String, Mutex>()

    override fun observe(): Flow<AppearanceInteractionSettingsSnapshot> =
        combine(settingsRepository.settings, commandInFlight) { settings, inFlight ->
            AppearanceInteractionSettingsSnapshot(
                themeDescriptor = settings.themeDescriptor,
                themeMode = settings.themeMode,
                quickCaptureOverlayEnabled = settings.quickCaptureOverlayEnabled,
                sealStampDurationMs = settings.sealStampDurationMs,
                readingFontScale = settings.readingFontScale,
                lineHeight = settings.lineHeight,
                commandInFlight = inFlight,
            )
        }

    override suspend fun execute(
        command: AppearanceInteractionSettingsCommand,
    ): AppearanceInteractionSettingsResult {
        val lock = locks.getOrPut(command.lockKey()) { Mutex() }
        if (!lock.tryLock()) {
            return AppearanceInteractionSettingsResult.IgnoredDuplicate
        }
        commandInFlight.value = command
        return try {
            when (command) {
                is AppearanceInteractionSettingsCommand.SetThemeDescriptor -> {
                    settingsRepository.setThemeDescriptor(command.descriptor)
                    AppearanceInteractionSettingsResult.Success
                }
                is AppearanceInteractionSettingsCommand.SetThemePalette -> {
                    settingsRepository.setThemePalette(command.palette)
                    AppearanceInteractionSettingsResult.Success
                }
                is AppearanceInteractionSettingsCommand.SetThemeMode -> {
                    settingsRepository.setThemeMode(command.mode)
                    AppearanceInteractionSettingsResult.Success
                }
                is AppearanceInteractionSettingsCommand.SetQuickCaptureOverlayEnabled -> {
                    if (command.enabled && !overlayPermissionGateway.isGranted()) {
                        return AppearanceInteractionSettingsResult.Platform(
                            SettingsPlatformAction.OpenOverlayPermissionSettings(
                                packageName = overlayPermissionGateway.packageName,
                            ),
                        )
                    }
                    settingsRepository.setQuickCaptureOverlayEnabled(command.enabled)
                    AppearanceInteractionSettingsResult.Success
                }
                is AppearanceInteractionSettingsCommand.SetSealStampDurationMs -> {
                    if (command.value !in 200..2000) {
                        return AppearanceInteractionSettingsResult.Failure(
                            SettingsCapabilityError.InvalidInput,
                        )
                    }
                    settingsRepository.setSealStampDurationMs(command.value)
                    AppearanceInteractionSettingsResult.Success
                }
                is AppearanceInteractionSettingsCommand.SetReadingFontScale -> {
                    settingsRepository.setReadingFontScale(command.scale)
                    AppearanceInteractionSettingsResult.Success
                }
                is AppearanceInteractionSettingsCommand.SetReadingLineHeight -> {
                    settingsRepository.setReadingLineHeight(command.lineHeight)
                    AppearanceInteractionSettingsResult.Success
                }
            }
        } catch (t: Throwable) {
            val mapped = SettingsCapabilityErrorMapper.map(t)
            AppearanceInteractionSettingsResult.Failure(
                if (mapped is SettingsCapabilityError.Unknown) {
                    SettingsCapabilityError.StorageFailure
                } else {
                    mapped
                },
            )
        } finally {
            commandInFlight.value = null
            lock.unlock()
        }
    }

    private fun AppearanceInteractionSettingsCommand.lockKey(): String =
        when (this) {
            is AppearanceInteractionSettingsCommand.SetThemeDescriptor -> "SetThemeDescriptor"
            is AppearanceInteractionSettingsCommand.SetThemePalette -> "SetThemePalette"
            is AppearanceInteractionSettingsCommand.SetThemeMode -> "SetThemeMode"
            is AppearanceInteractionSettingsCommand.SetQuickCaptureOverlayEnabled ->
                "SetQuickCaptureOverlayEnabled"
            is AppearanceInteractionSettingsCommand.SetSealStampDurationMs ->
                "SetSealStampDurationMs"
            is AppearanceInteractionSettingsCommand.SetReadingFontScale -> "SetReadingFontScale"
            is AppearanceInteractionSettingsCommand.SetReadingLineHeight -> "SetReadingLineHeight"
        }
}
