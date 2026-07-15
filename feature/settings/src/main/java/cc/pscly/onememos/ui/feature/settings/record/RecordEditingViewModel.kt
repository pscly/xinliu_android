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
    val isSubmitting: Boolean = false,
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
        if (!beginSubmission()) return

        viewModelScope.launch {
            try {
                when (val result = capability.execute(command)) {
                    RecordEditingSettingsResult.Success -> {
                        _uiState.update { it.copy(persistentError = null) }
                        _events.emit(SettingsUiEvent.Toast(SettingsMessage.COMMAND_SUCCEEDED))
                    }
                    RecordEditingSettingsResult.IgnoredDuplicate -> Unit
                    is RecordEditingSettingsResult.Failure -> {
                        _uiState.update { it.copy(persistentError = result.error) }
                        _events.emit(SettingsUiEvent.Toast(SettingsMessage.COMMAND_FAILED))
                    }
                }
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    private fun beginSubmission(): Boolean {
        while (true) {
            val state = _uiState.value
            if (state.isSubmitting) return false
            if (_uiState.compareAndSet(state, state.copy(isSubmitting = true))) return true
        }
    }
}
