package cc.pscly.onememos.ui.feature.settings.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.pscly.onememos.domain.settings.RecordEditingSettingsCapability
import cc.pscly.onememos.domain.settings.RecordEditingSettingsCommand
import cc.pscly.onememos.domain.settings.RecordEditingSettingsResult
import cc.pscly.onememos.domain.settings.RecordEditingSettingsSnapshot
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.ui.feature.settings.common.SettingsMessage
import cc.pscly.onememos.ui.feature.settings.common.SettingsUiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecordEditingUiState(
    val loading: Boolean = true,
    val snapshot: RecordEditingSettingsSnapshot? = null,
    val persistentError: SettingsCapabilityError? = null,
)

@HiltViewModel
class RecordEditingViewModel @Inject constructor(
    private val capability: RecordEditingSettingsCapability,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecordEditingUiState())
    val uiState: StateFlow<RecordEditingUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsUiEvent>(replay = 0, extraBufferCapacity = 1)
    val events: SharedFlow<SettingsUiEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            capability.observe().collect { snapshot ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        snapshot = snapshot,
                    )
                }
            }
        }
    }

    fun submit(command: RecordEditingSettingsCommand) {
        val commandInFlight = _uiState.value.snapshot?.commandInFlight
        if (commandInFlight != null && commandInFlight.matches(command)) return

        viewModelScope.launch {
            when (val result = capability.execute(command)) {
                RecordEditingSettingsResult.Success -> {
                    _uiState.update { it.copy(persistentError = null) }
                    _events.tryEmit(SettingsUiEvent.Toast(SettingsMessage.COMMAND_SUCCEEDED))
                }
                RecordEditingSettingsResult.IgnoredDuplicate -> Unit
                is RecordEditingSettingsResult.Failure -> {
                    _uiState.update { it.copy(persistentError = result.error) }
                    _events.tryEmit(SettingsUiEvent.Toast(SettingsMessage.COMMAND_FAILED))
                }
            }
        }
    }
}

private fun RecordEditingSettingsCommand.matches(other: RecordEditingSettingsCommand): Boolean =
    when (this) {
        is RecordEditingSettingsCommand.SetDefaultVisibility ->
            other is RecordEditingSettingsCommand.SetDefaultVisibility
        is RecordEditingSettingsCommand.SetRegexSearchEnabled ->
            other is RecordEditingSettingsCommand.SetRegexSearchEnabled
        is RecordEditingSettingsCommand.SetShowTagCounts ->
            other is RecordEditingSettingsCommand.SetShowTagCounts
        is RecordEditingSettingsCommand.SetQuickInsertTimeEnabled ->
            other is RecordEditingSettingsCommand.SetQuickInsertTimeEnabled
        is RecordEditingSettingsCommand.SetQuickInsertTimeFormat ->
            other is RecordEditingSettingsCommand.SetQuickInsertTimeFormat
    }
