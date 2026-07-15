package cc.pscly.onememos.domain.settings

import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.QuickInsertTimeFormat
import kotlinx.coroutines.flow.Flow

interface RecordEditingSettingsCapability {
    fun observe(): Flow<RecordEditingSettingsSnapshot>

    suspend fun execute(command: RecordEditingSettingsCommand): RecordEditingSettingsResult
}

data class RecordEditingSettingsSnapshot(
    val defaultVisibility: MemoVisibility,
    val regexSearchEnabled: Boolean,
    val showTagCounts: Boolean,
    val quickInsertTimeEnabled: Boolean,
    val quickInsertTimeFormat: QuickInsertTimeFormat,
    val commandInFlight: RecordEditingSettingsCommand? = null,
)

sealed interface RecordEditingSettingsCommand {
    data class SetDefaultVisibility(val visibility: MemoVisibility) : RecordEditingSettingsCommand

    data class SetRegexSearchEnabled(val enabled: Boolean) : RecordEditingSettingsCommand

    data class SetShowTagCounts(val enabled: Boolean) : RecordEditingSettingsCommand

    data class SetQuickInsertTimeEnabled(val enabled: Boolean) : RecordEditingSettingsCommand

    data class SetQuickInsertTimeFormat(val format: QuickInsertTimeFormat) : RecordEditingSettingsCommand
}

sealed interface RecordEditingSettingsResult {
    data object Success : RecordEditingSettingsResult

    data object IgnoredDuplicate : RecordEditingSettingsResult

    data class Failure(val error: SettingsCapabilityError) : RecordEditingSettingsResult
}
