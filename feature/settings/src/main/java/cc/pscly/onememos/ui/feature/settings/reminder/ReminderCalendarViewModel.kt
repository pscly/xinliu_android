package cc.pscly.onememos.ui.feature.settings.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.pscly.onememos.domain.model.TodoReminderMode
import cc.pscly.onememos.domain.settings.ReminderCalendarSettingsCapability
import cc.pscly.onememos.domain.settings.ReminderCalendarSettingsCommand
import cc.pscly.onememos.domain.settings.ReminderCalendarSettingsResult
import cc.pscly.onememos.domain.settings.ReminderCalendarSettingsSnapshot
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.domain.settings.SettingsPermission
import cc.pscly.onememos.ui.feature.settings.common.SettingsMessage
import cc.pscly.onememos.ui.feature.settings.common.SettingsPlatformResult
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

data class ReminderCalendarUiState(
    val loading: Boolean = true,
    val snapshot: ReminderCalendarSettingsSnapshot? = null,
    val persistentError: SettingsCapabilityError? = null,
    val notice: ReminderCalendarNotice? = null,
)

enum class ReminderCalendarNotice {
    PERMISSION_GRANTED,
}

sealed interface ReminderCalendarUserIntent {
    data class SetReminderMode(val mode: TodoReminderMode) : ReminderCalendarUserIntent

    data class SetCalendarEnabled(val enabled: Boolean) : ReminderCalendarUserIntent

    data class SelectCalendar(val calendarId: Long) : ReminderCalendarUserIntent

    data object ClearCalendar : ReminderCalendarUserIntent

    data class SetCalendarReminderSync(val enabled: Boolean) : ReminderCalendarUserIntent

    data object Reschedule : ReminderCalendarUserIntent

    data class ApplyPlatformResult(val result: SettingsPlatformResult) : ReminderCalendarUserIntent
}

@HiltViewModel
class ReminderCalendarViewModel @Inject constructor(
    private val capability: ReminderCalendarSettingsCapability,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ReminderCalendarUiState())
    val uiState: StateFlow<ReminderCalendarUiState> = mutableUiState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<SettingsUiEvent>(replay = 0, extraBufferCapacity = 1)
    val events: SharedFlow<SettingsUiEvent> = mutableEvents.asSharedFlow()

    private var pendingPlatformCommand: ReminderCalendarSettingsCommand? = null

    init {
        viewModelScope.launch {
            capability.observe().collect { snapshot ->
                mutableUiState.update { it.copy(loading = false, snapshot = snapshot) }
            }
        }
    }

    fun onIntent(intent: ReminderCalendarUserIntent) {
        viewModelScope.launch {
            when (intent) {
                is ReminderCalendarUserIntent.SetReminderMode ->
                    execute(ReminderCalendarSettingsCommand.SetReminderMode(intent.mode))
                is ReminderCalendarUserIntent.SetCalendarEnabled ->
                    execute(ReminderCalendarSettingsCommand.SetCalendarEnabled(intent.enabled))
                is ReminderCalendarUserIntent.SelectCalendar ->
                    execute(ReminderCalendarSettingsCommand.SetCalendar(intent.calendarId))
                ReminderCalendarUserIntent.ClearCalendar ->
                    execute(ReminderCalendarSettingsCommand.SetCalendar(null))
                is ReminderCalendarUserIntent.SetCalendarReminderSync ->
                    execute(ReminderCalendarSettingsCommand.SetCalendarReminderSync(intent.enabled))
                ReminderCalendarUserIntent.Reschedule ->
                    execute(ReminderCalendarSettingsCommand.Reschedule)
                is ReminderCalendarUserIntent.ApplyPlatformResult ->
                    applyPlatformResult(intent.result)
            }
        }
    }

    private suspend fun applyPlatformResult(result: SettingsPlatformResult) {
        when (result) {
            is SettingsPlatformResult.Permissions -> applyPermissions(result)
            is SettingsPlatformResult.Failed -> {
                pendingPlatformCommand = null
                showFailure(result.error)
            }
            SettingsPlatformResult.Completed,
            is SettingsPlatformResult.OverlayPermissionChanged,
            -> Unit
        }
    }

    private suspend fun applyPermissions(result: SettingsPlatformResult.Permissions) {
        val permissionResult =
            capability.execute(ReminderCalendarSettingsCommand.ApplyPermissionResult(result.granted))
        if (permissionResult is ReminderCalendarSettingsResult.Failure) {
            pendingPlatformCommand = null
            showFailure(permissionResult.error)
            return
        }

        val allGranted =
            result.denied.isEmpty() &&
                result.granted.containsAll(
                    setOf(SettingsPermission.READ_CALENDAR, SettingsPermission.WRITE_CALENDAR),
                )
        if (!allGranted) {
            pendingPlatformCommand = null
            mutableUiState.update {
                it.copy(
                    persistentError = SettingsCapabilityError.PermissionDenied,
                    notice = null,
                )
            }
            mutableEvents.emit(SettingsUiEvent.Toast(SettingsMessage.PERMISSION_DENIED))
            return
        }

        mutableUiState.update {
            it.copy(persistentError = null, notice = ReminderCalendarNotice.PERMISSION_GRANTED)
        }
        val command = pendingPlatformCommand
        pendingPlatformCommand = null
        if (command != null) {
            execute(command)
        } else {
            mutableEvents.emit(SettingsUiEvent.Toast(SettingsMessage.COMMAND_SUCCEEDED))
        }
    }

    private suspend fun execute(command: ReminderCalendarSettingsCommand) {
        when (val result = capability.execute(command)) {
            ReminderCalendarSettingsResult.Success -> {
                mutableUiState.update { it.copy(persistentError = null) }
                mutableEvents.emit(SettingsUiEvent.Toast(SettingsMessage.COMMAND_SUCCEEDED))
            }
            ReminderCalendarSettingsResult.IgnoredDuplicate -> Unit
            is ReminderCalendarSettingsResult.Platform -> {
                pendingPlatformCommand = command
                mutableEvents.emit(SettingsUiEvent.Platform(result.action))
            }
            is ReminderCalendarSettingsResult.Failure -> showFailure(result.error)
        }
    }

    private suspend fun showFailure(error: SettingsCapabilityError) {
        mutableUiState.update { it.copy(persistentError = error, notice = null) }
        mutableEvents.emit(SettingsUiEvent.Toast(SettingsMessage.COMMAND_FAILED))
    }
}
