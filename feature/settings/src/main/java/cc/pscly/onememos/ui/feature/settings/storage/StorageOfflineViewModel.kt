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
    val operationSubmitting: Boolean = false,
    val refreshSubmitting: Boolean = false,
    val cleanupSubmitting: Boolean = false,
) {
    val operationDisabled: Boolean
        get() =
            operationSubmitting ||
                refreshSubmitting ||
                cleanupSubmitting ||
                snapshot?.commandInFlight != null

    val cleanupActive: Boolean
        get() = cleanupSubmitting || snapshot?.commandInFlight.isCleanupCommand()

    val cleanupDisabled: Boolean
        get() = operationDisabled
}

@HiltViewModel
class StorageOfflineViewModel @Inject constructor(
    private val capability: StorageOfflineSettingsCapability,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StorageOfflineUiState())
    val uiState: StateFlow<StorageOfflineUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsUiEvent>(replay = 0, extraBufferCapacity = 1)
    val events: SharedFlow<SettingsUiEvent> = _events.asSharedFlow()

    private val submissionLock = Any()
    private val pendingCommands = ArrayDeque<StorageOfflineSettingsCommand>()
    private var workerRunning = false
    private var refreshPending = false
    private var cleanupPending = false
    private val persistentErrors = linkedMapOf<StorageCommandFamily, SettingsCapabilityError>()

    init {
        viewModelScope.launch {
            capability
                .observe()
                .catch {
                    recordPersistentError(
                        family = StorageCommandFamily.OBSERVATION,
                        error = SettingsCapabilityError.StorageFailure,
                    )
                    _uiState.update { it.copy(loading = false) }
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
        submit(command)
    }

    private fun executeCleanup(command: StorageOfflineSettingsCommand) {
        submit(command)
    }

    private fun submit(command: StorageOfflineSettingsCommand) {
        var startWorker = false
        synchronized(submissionLock) {
            when {
                command == StorageOfflineSettingsCommand.RefreshStats -> {
                    if (refreshPending || cleanupPending) return
                    refreshPending = true
                }
                command.isCleanupCommand() -> {
                    if (cleanupPending) return
                    cleanupPending = true
                }
            }
            pendingCommands.addLast(command)
            if (!workerRunning) {
                workerRunning = true
                startWorker = true
            }
            updateSubmissionState()
        }
        if (startWorker) viewModelScope.launch { drainCommands() }
    }

    private suspend fun drainCommands() {
        while (true) {
            val command =
                synchronized(submissionLock) {
                    pendingCommands.firstOrNull()
                        ?: run {
                            workerRunning = false
                            return
                        }
                }
            handleResult(command, capability.execute(command))
            synchronized(submissionLock) {
                check(pendingCommands.removeFirst() == command)
                when {
                    command == StorageOfflineSettingsCommand.RefreshStats -> refreshPending = false
                    command.isCleanupCommand() -> cleanupPending = false
                }
                updateSubmissionState()
            }
        }
    }

    private fun updateSubmissionState() {
        _uiState.update {
            it.copy(
                operationSubmitting = pendingCommands.isNotEmpty(),
                refreshSubmitting = pendingCommands.any { command -> command == StorageOfflineSettingsCommand.RefreshStats },
                cleanupSubmitting = pendingCommands.any(StorageOfflineSettingsCommand::isCleanupCommand),
            )
        }
    }

    private fun handleResult(
        command: StorageOfflineSettingsCommand,
        result: StorageOfflineSettingsResult,
    ) {
        when (result) {
            StorageOfflineSettingsResult.Success -> {
                clearPersistentErrors(command)
                publishPersistentError()
            }
            StorageOfflineSettingsResult.IgnoredDuplicate -> Unit
            is StorageOfflineSettingsResult.Failure ->
                recordPersistentError(command.family(), result.error)
        }
    }

    private fun recordPersistentError(
        family: StorageCommandFamily,
        error: SettingsCapabilityError,
    ) {
        persistentErrors.remove(family)
        persistentErrors[family] = error
        publishPersistentError()
    }

    private fun publishPersistentError() {
        _uiState.update { it.copy(persistentError = persistentErrors.values.lastOrNull()) }
    }

    private fun clearPersistentErrors(command: StorageOfflineSettingsCommand) {
        when (command) {
            StorageOfflineSettingsCommand.RefreshStats ->
                persistentErrors.keys.removeAll(StorageCommandFamily::isStorageFamily)
            StorageOfflineSettingsCommand.ClearImageCache -> {
                persistentErrors.remove(StorageCommandFamily.IMAGE_CACHE)
                persistentErrors.remove(StorageCommandFamily.STORAGE_REFRESH)
            }
            StorageOfflineSettingsCommand.ClearAttachmentCache -> {
                persistentErrors.remove(StorageCommandFamily.ATTACHMENT_CACHE)
                persistentErrors.remove(StorageCommandFamily.STORAGE_REFRESH)
            }
            StorageOfflineSettingsCommand.ClearAllCache ->
                persistentErrors.keys.removeAll(StorageCommandFamily::isStorageFamily)
            else -> persistentErrors.remove(command.family())
        }
    }
}

private enum class StorageCommandFamily {
    IMAGE_PREFETCH,
    MEMO_LIMIT,
    IMAGE_LIMIT,
    ATTACHMENT_LIMIT,
    STORAGE_REFRESH,
    IMAGE_CACHE,
    ATTACHMENT_CACHE,
    ALL_CACHE,
    OBSERVATION,
}

private fun StorageCommandFamily.isStorageFamily(): Boolean =
    this == StorageCommandFamily.STORAGE_REFRESH ||
        this == StorageCommandFamily.IMAGE_CACHE ||
        this == StorageCommandFamily.ATTACHMENT_CACHE ||
        this == StorageCommandFamily.ALL_CACHE

private fun StorageOfflineSettingsCommand.family(): StorageCommandFamily =
    when (this) {
        is StorageOfflineSettingsCommand.SetImagePrefetchEnabled -> StorageCommandFamily.IMAGE_PREFETCH
        is StorageOfflineSettingsCommand.SetPrefetchMemoLimit -> StorageCommandFamily.MEMO_LIMIT
        is StorageOfflineSettingsCommand.SetPrefetchImageLimit -> StorageCommandFamily.IMAGE_LIMIT
        is StorageOfflineSettingsCommand.SetAttachmentCacheLimitMb -> StorageCommandFamily.ATTACHMENT_LIMIT
        StorageOfflineSettingsCommand.RefreshStats -> StorageCommandFamily.STORAGE_REFRESH
        StorageOfflineSettingsCommand.ClearImageCache -> StorageCommandFamily.IMAGE_CACHE
        StorageOfflineSettingsCommand.ClearAttachmentCache -> StorageCommandFamily.ATTACHMENT_CACHE
        StorageOfflineSettingsCommand.ClearAllCache -> StorageCommandFamily.ALL_CACHE
    }

private fun StorageOfflineSettingsCommand?.isCleanupCommand(): Boolean =
    this == StorageOfflineSettingsCommand.ClearImageCache ||
        this == StorageOfflineSettingsCommand.ClearAttachmentCache ||
        this == StorageOfflineSettingsCommand.ClearAllCache
