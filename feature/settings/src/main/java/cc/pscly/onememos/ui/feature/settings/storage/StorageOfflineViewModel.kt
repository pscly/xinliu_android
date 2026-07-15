package cc.pscly.onememos.ui.feature.settings.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.domain.settings.StorageOfflineSettingsCapability
import cc.pscly.onememos.domain.settings.StorageOfflineSettingsCommand
import cc.pscly.onememos.domain.settings.StorageOfflineSettingsResult
import cc.pscly.onememos.domain.settings.StorageOfflineSettingsSnapshot
import cc.pscly.onememos.ui.feature.settings.common.SettingsConfirmation
import cc.pscly.onememos.ui.feature.settings.common.SettingsUiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StorageOfflineUiState(
    val loading: Boolean = true,
    val snapshot: StorageOfflineSettingsSnapshot? = null,
    val persistentError: SettingsCapabilityError? = null,
    val cleanupSubmitting: Boolean = false,
) {
    val cleanupDisabled: Boolean
        get() = cleanupSubmitting || snapshot?.commandInFlight.isCleanupCommand()
}

@HiltViewModel
class StorageOfflineViewModel @Inject constructor(
    private val capability: StorageOfflineSettingsCapability,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StorageOfflineUiState())
    val uiState: StateFlow<StorageOfflineUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsUiEvent>(replay = 0, extraBufferCapacity = 1)
    val events: SharedFlow<SettingsUiEvent> = _events.asSharedFlow()

    private val cleanupSubmitting = AtomicBoolean(false)

    init {
        viewModelScope.launch {
            capability
                .observe()
                .catch {
                    _uiState.update {
                        it.copy(
                            loading = false,
                            persistentError = SettingsCapabilityError.StorageFailure,
                        )
                    }
                }
                .collect { snapshot ->
                    _uiState.update { it.copy(loading = false, snapshot = snapshot) }
                }
        }
    }

    fun setImagePrefetchEnabled(enabled: Boolean) {
        execute(StorageOfflineSettingsCommand.SetImagePrefetchEnabled(enabled))
    }

    fun setPrefetchMemoLimit(value: Int) {
        execute(StorageOfflineSettingsCommand.SetPrefetchMemoLimit(value))
    }

    fun setPrefetchImageLimit(value: Int) {
        execute(StorageOfflineSettingsCommand.SetPrefetchImageLimit(value))
    }

    fun setAttachmentCacheLimitMb(value: Int) {
        execute(StorageOfflineSettingsCommand.SetAttachmentCacheLimitMb(value))
    }

    fun refreshStats() {
        execute(StorageOfflineSettingsCommand.RefreshStats)
    }

    fun requestClearImageCache() {
        _events.tryEmit(SettingsUiEvent.Confirm(SettingsConfirmation.CLEAR_IMAGE_CACHE))
    }

    fun requestClearAttachmentCache() {
        _events.tryEmit(SettingsUiEvent.Confirm(SettingsConfirmation.CLEAR_ATTACHMENT_CACHE))
    }

    fun requestClearAllCache() {
        _events.tryEmit(SettingsUiEvent.Confirm(SettingsConfirmation.CLEAR_ALL_CACHE))
    }

    fun confirmClearImageCache() {
        executeCleanup(StorageOfflineSettingsCommand.ClearImageCache)
    }

    fun confirmClearAttachmentCache() {
        executeCleanup(StorageOfflineSettingsCommand.ClearAttachmentCache)
    }

    fun confirmClearAllCache() {
        executeCleanup(StorageOfflineSettingsCommand.ClearAllCache)
    }

    private fun execute(command: StorageOfflineSettingsCommand) {
        viewModelScope.launch {
            handleResult(capability.execute(command))
        }
    }

    private fun executeCleanup(command: StorageOfflineSettingsCommand) {
        if (!cleanupSubmitting.compareAndSet(false, true)) return
        _uiState.update { it.copy(cleanupSubmitting = true) }
        viewModelScope.launch {
            try {
                handleResult(capability.execute(command))
            } finally {
                cleanupSubmitting.set(false)
                _uiState.update { it.copy(cleanupSubmitting = false) }
            }
        }
    }

    private fun handleResult(result: StorageOfflineSettingsResult) {
        when (result) {
            StorageOfflineSettingsResult.Success ->
                _uiState.update { it.copy(persistentError = null) }
            StorageOfflineSettingsResult.IgnoredDuplicate -> Unit
            is StorageOfflineSettingsResult.Failure ->
                _uiState.update { it.copy(persistentError = result.error) }
        }
    }
}

private fun StorageOfflineSettingsCommand?.isCleanupCommand(): Boolean =
    this == StorageOfflineSettingsCommand.ClearImageCache ||
        this == StorageOfflineSettingsCommand.ClearAttachmentCache ||
        this == StorageOfflineSettingsCommand.ClearAllCache
