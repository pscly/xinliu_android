package cc.pscly.onememos.ui.feature.settings.reminder

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import cc.pscly.onememos.domain.model.TodoReminderMode
import cc.pscly.onememos.domain.settings.CalendarPermissionState
import cc.pscly.onememos.domain.settings.CalendarSummary
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.feature.settings.R
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.component.ScrollPaperSurface
import cc.pscly.onememos.ui.feature.settings.common.LocalSettingsPlatformActionDispatcher
import cc.pscly.onememos.ui.feature.settings.common.SettingsMessage
import cc.pscly.onememos.ui.feature.settings.common.SettingsUiEvent

@Composable
fun ReminderCalendarScreen(viewModel: ReminderCalendarViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val dispatcher = LocalSettingsPlatformActionDispatcher.current
    val context = LocalContext.current
    val success = stringResource(R.string.settings_reminder_toast_success)
    val failed = stringResource(R.string.settings_reminder_toast_failed)
    val denied = stringResource(R.string.settings_reminder_toast_permission_denied)

    LaunchedEffect(viewModel, lifecycleOwner, dispatcher) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is SettingsUiEvent.Platform ->
                        dispatcher.dispatch(event.action) { result ->
                            viewModel.onIntent(ReminderCalendarUserIntent.ApplyPlatformResult(result))
                        }
                    is SettingsUiEvent.Toast -> {
                        val message =
                            when (event.message) {
                                SettingsMessage.COMMAND_SUCCEEDED -> success
                                SettingsMessage.COMMAND_FAILED -> failed
                                SettingsMessage.PERMISSION_DENIED -> denied
                            }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                    is SettingsUiEvent.Navigate,
                    is SettingsUiEvent.Confirm,
                    is SettingsUiEvent.UpdateDelivery,
                    -> Unit
                }
            }
        }
    }
    ReminderCalendarContent(uiState = uiState, onIntent = viewModel::onIntent)
}

@Composable
fun ReminderCalendarContent(
    uiState: ReminderCalendarUiState,
    onIntent: (ReminderCalendarUserIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val contentMax = maxWidth.coerceAtMost(720.dp)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            ScrollPaperSurface(
                modifier =
                    Modifier
                        .widthIn(max = contentMax)
                        .fillMaxWidth()
                        .fillMaxSize()
                        .testTag("settings_reminder_content"),
                scrollOffsetPx = scroll.value.toFloat(),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_reminder_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    val snapshot = uiState.snapshot
                    if (snapshot == null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.settings_reminder_loading))
                        }
                    } else {
                        val controlsEnabled = snapshot.commandInFlight == null
                        val calendarActive =
                            snapshot.permission == CalendarPermissionState.GRANTED && snapshot.calendarEnabled
                        SectionTitle(R.string.settings_reminder_mode_section)
                        ChoiceRow(
                            title = stringResource(R.string.settings_reminder_mode_smart),
                            summary = stringResource(R.string.settings_reminder_mode_smart_summary),
                            selected = snapshot.reminderMode == TodoReminderMode.SMART,
                            enabled = controlsEnabled,
                            tag = "settings_reminder_mode_smart",
                        ) { onIntent(ReminderCalendarUserIntent.SetReminderMode(TodoReminderMode.SMART)) }
                        ChoiceRow(
                            title = stringResource(R.string.settings_reminder_mode_exact),
                            summary = stringResource(R.string.settings_reminder_mode_exact_summary),
                            selected = snapshot.reminderMode == TodoReminderMode.EXACT,
                            enabled = controlsEnabled,
                            tag = "settings_reminder_mode_exact",
                        ) { onIntent(ReminderCalendarUserIntent.SetReminderMode(TodoReminderMode.EXACT)) }

                        SectionTitle(R.string.settings_reminder_calendar_section)
                        SettingSwitch(
                            title = stringResource(R.string.settings_reminder_calendar_enabled),
                            summary = stringResource(R.string.settings_reminder_calendar_enabled_summary),
                            checked = calendarActive,
                            enabled = controlsEnabled,
                            tag = "settings_reminder_calendar_enabled",
                        ) { onIntent(ReminderCalendarUserIntent.SetCalendarEnabled(it)) }
                        PermissionStatus(snapshot.permission)
                        if (snapshot.permission == CalendarPermissionState.GRANTED) {
                            SectionTitle(R.string.settings_reminder_calendar_list)
                            if (snapshot.writableCalendars.isEmpty()) {
                                Text(stringResource(R.string.settings_reminder_calendar_empty))
                            }
                            snapshot.writableCalendars.forEach { calendar ->
                                CalendarRow(
                                    calendar = calendar,
                                    selected = snapshot.selectedCalendar?.id == calendar.id,
                                    enabled = calendarActive && controlsEnabled,
                                ) { onIntent(ReminderCalendarUserIntent.SelectCalendar(calendar.id)) }
                            }
                            OutlinedButton(
                                onClick = { onIntent(ReminderCalendarUserIntent.ClearCalendar) },
                                enabled = calendarActive && controlsEnabled && snapshot.selectedCalendar != null,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("settings_reminder_clear_calendar"),
                            ) { Text(stringResource(R.string.settings_reminder_calendar_clear)) }
                        }
                        SettingSwitch(
                            title = stringResource(R.string.settings_reminder_sync_reminders),
                            summary = stringResource(R.string.settings_reminder_sync_reminders_summary),
                            checked = calendarActive && snapshot.syncCalendarReminders,
                            enabled = calendarActive && controlsEnabled,
                            tag = "settings_reminder_sync_reminders",
                        ) { onIntent(ReminderCalendarUserIntent.SetCalendarReminderSync(it)) }
                        uiState.persistentError?.let { ErrorStatus(it) }
                        uiState.notice?.let { NoticeStatus(it) }
                        InkCard {
                            Text(
                                text = stringResource(R.string.settings_reminder_reschedule_summary),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            OutlinedButton(
                                onClick = { onIntent(ReminderCalendarUserIntent.Reschedule) },
                                enabled = controlsEnabled,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("settings_reminder_reschedule"),
                            ) { Text(stringResource(R.string.settings_reminder_reschedule)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ChoiceRow(
    title: String,
    summary: String,
    selected: Boolean,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    val state = stringResource(if (selected) R.string.settings_reminder_selected else R.string.settings_reminder_select)
    InkCard(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 48.dp).testTag(tag).semantics {
            this.selected = selected
            stateDescription = state
        },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(summary, style = MaterialTheme.typography.bodySmall)
            }
            Text(state, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    InkCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(summary, style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier =
                    Modifier
                        .padding(start = 12.dp)
                        .heightIn(min = 48.dp)
                        .testTag(tag)
                        .toggleable(
                            value = checked,
                            enabled = enabled,
                            role = Role.Switch,
                            onValueChange = onCheckedChange,
                        ),
            )
        }
    }
}

@Composable
private fun PermissionStatus(permission: CalendarPermissionState) {
    val text =
        when (permission) {
            CalendarPermissionState.GRANTED -> R.string.settings_reminder_permission_granted_status
            CalendarPermissionState.DENIED -> R.string.settings_reminder_permission_denied_status
            CalendarPermissionState.UNKNOWN -> R.string.settings_reminder_permission_unknown_status
        }
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag("settings_reminder_permission_status").semantics {
            liveRegion = LiveRegionMode.Polite
        },
    )
}

@Composable
private fun CalendarRow(
    calendar: CalendarSummary,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val state = stringResource(if (selected) R.string.settings_reminder_calendar_current else R.string.settings_reminder_calendar_choose)
    ChoiceRow(calendar.label, state, selected, enabled, "settings_reminder_calendar_${calendar.id}", onClick)
}

@Composable
private fun ErrorStatus(error: SettingsCapabilityError) {
    val text =
        when (error) {
            SettingsCapabilityError.AuthenticationExpired -> R.string.settings_reminder_error_authentication
            SettingsCapabilityError.NetworkUnavailable -> R.string.settings_reminder_error_network
            SettingsCapabilityError.PermissionDenied -> R.string.settings_reminder_error_permission
            SettingsCapabilityError.PlatformUnavailable -> R.string.settings_reminder_error_platform
            SettingsCapabilityError.InvalidInput -> R.string.settings_reminder_error_invalid
            SettingsCapabilityError.AlreadyRunning -> R.string.settings_reminder_error_running
            SettingsCapabilityError.StorageFailure -> R.string.settings_reminder_error_storage
            is SettingsCapabilityError.Unknown -> R.string.settings_reminder_error_unknown
        }
    Text(
        text = stringResource(text),
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag("settings_reminder_error").semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun NoticeStatus(notice: ReminderCalendarNotice) {
    val text =
        when (notice) {
            ReminderCalendarNotice.PERMISSION_GRANTED -> R.string.settings_reminder_permission_granted
        }
    Text(
        text = stringResource(text),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag("settings_reminder_result").semantics { liveRegion = LiveRegionMode.Polite },
    )
}
