package cc.pscly.onememos.settings.record

import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.settings.RecordEditingSettingsCapability
import cc.pscly.onememos.domain.settings.RecordEditingSettingsCommand
import cc.pscly.onememos.domain.settings.RecordEditingSettingsResult
import cc.pscly.onememos.domain.settings.RecordEditingSettingsSnapshot
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.settings.SettingsCapabilityErrorMapper
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex

/**
 * 记录与编辑深能力：只映射本页五项设置，不触发同步/网络/日历/诊断。
 */
@Singleton
class RecordEditingSettingsCapabilityImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : RecordEditingSettingsCapability {
    private val commandInFlight = MutableStateFlow<RecordEditingSettingsCommand?>(null)
    private val locks = ConcurrentHashMap<String, Mutex>()

    override fun observe(): Flow<RecordEditingSettingsSnapshot> =
        combine(settingsRepository.settings, commandInFlight) { settings, inFlight ->
            RecordEditingSettingsSnapshot(
                defaultVisibility = settings.defaultVisibility,
                regexSearchEnabled = settings.regexSearchEnabled,
                showTagCounts = settings.showTagCountsInFilter,
                quickInsertTimeEnabled = settings.quickInsertTimeEnabled,
                quickInsertTimeFormat = settings.quickInsertTimeFormat,
                commandInFlight = inFlight,
            )
        }

    override suspend fun execute(command: RecordEditingSettingsCommand): RecordEditingSettingsResult {
        val lock = locks.getOrPut(command.lockKey()) { Mutex() }
        if (!lock.tryLock()) {
            return RecordEditingSettingsResult.IgnoredDuplicate
        }
        commandInFlight.value = command
        return try {
            when (command) {
                is RecordEditingSettingsCommand.SetDefaultVisibility ->
                    settingsRepository.setDefaultVisibility(command.visibility)
                is RecordEditingSettingsCommand.SetRegexSearchEnabled ->
                    settingsRepository.setRegexSearchEnabled(command.enabled)
                is RecordEditingSettingsCommand.SetShowTagCounts ->
                    settingsRepository.setShowTagCountsInFilter(command.enabled)
                is RecordEditingSettingsCommand.SetQuickInsertTimeEnabled ->
                    settingsRepository.setQuickInsertTimeEnabled(command.enabled)
                is RecordEditingSettingsCommand.SetQuickInsertTimeFormat ->
                    settingsRepository.setQuickInsertTimeFormat(command.format)
            }
            RecordEditingSettingsResult.Success
        } catch (t: Throwable) {
            val mapped = SettingsCapabilityErrorMapper.map(t)
            RecordEditingSettingsResult.Failure(
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

    private fun RecordEditingSettingsCommand.lockKey(): String =
        when (this) {
            is RecordEditingSettingsCommand.SetDefaultVisibility -> "SetDefaultVisibility"
            is RecordEditingSettingsCommand.SetRegexSearchEnabled -> "SetRegexSearchEnabled"
            is RecordEditingSettingsCommand.SetShowTagCounts -> "SetShowTagCounts"
            is RecordEditingSettingsCommand.SetQuickInsertTimeEnabled -> "SetQuickInsertTimeEnabled"
            is RecordEditingSettingsCommand.SetQuickInsertTimeFormat -> "SetQuickInsertTimeFormat"
        }
}
