package cc.pscly.onememos.ui.feature.settings.record

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.QuickInsertTimeFormat
import cc.pscly.onememos.domain.settings.RecordEditingSettingsCommand
import cc.pscly.onememos.domain.settings.RecordEditingSettingsSnapshot
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.feature.settings.R
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.component.ScrollPaperSurface
import cc.pscly.onememos.ui.component.SealIconButton
import cc.pscly.onememos.ui.feature.settings.common.SettingsMessage
import cc.pscly.onememos.ui.feature.settings.common.SettingsUiEvent

@Composable
fun RecordEditingScreen(
    onBack: () -> Unit,
    viewModel: RecordEditingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val successMessage = stringResource(R.string.settings_record_command_succeeded)
    val failureMessage = stringResource(R.string.settings_record_command_failed)

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                if (event is SettingsUiEvent.Toast) {
                    val message =
                        when (event.message) {
                            SettingsMessage.COMMAND_SUCCEEDED -> successMessage
                            SettingsMessage.COMMAND_FAILED -> failureMessage
                            SettingsMessage.PERMISSION_DENIED -> failureMessage
                        }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    RecordEditingContent(
        uiState = uiState,
        onBack = onBack,
        onSubmit = viewModel::submit,
    )
}

@Composable
fun RecordEditingContent(
    uiState: RecordEditingUiState,
    onBack: () -> Unit,
    onSubmit: (RecordEditingSettingsCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .testTag("settings_record_root"),
    ) {
        val contentMax = maxWidth.coerceAtMost(720.dp)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            ScrollPaperSurface(
                modifier =
                    Modifier
                        .widthIn(max = contentMax)
                        .fillMaxWidth()
                        .fillMaxSize(),
                scrollOffsetPx = scroll.value.toFloat(),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scroll)
                            .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RecordHeader(onBack = onBack)
                    uiState.persistentError?.let { error ->
                        PersistentError(error = error)
                    }
                    val snapshot = uiState.snapshot
                    if (uiState.loading || snapshot == null) {
                        LoadingContent()
                    } else {
                        RecordSettings(
                            snapshot = snapshot,
                            writesEnabled = !uiState.isSubmitting,
                            onSubmit = onSubmit,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SealIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.settings_record_back),
            modifier = Modifier.testTag("settings_record_back"),
            onClick = onBack,
        )
        Text(
            text = stringResource(R.string.settings_record_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
        )
    }
}

@Composable
private fun LoadingContent() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.settings_record_loading),
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun PersistentError(error: SettingsCapabilityError) {
    val errorText = errorText(error)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("settings_record_error")
                .semantics {
                    stateDescription = errorText
                    liveRegion = LiveRegionMode.Assertive
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = errorText,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun RecordSettings(
    snapshot: RecordEditingSettingsSnapshot,
    writesEnabled: Boolean,
    onSubmit: (RecordEditingSettingsCommand) -> Unit,
) {
    VisibilitySettings(
        snapshot = snapshot,
        enabled = writesEnabled,
        onSubmit = onSubmit,
    )
    SwitchSetting(
        title = stringResource(R.string.settings_record_regex_title),
        description = stringResource(R.string.settings_record_regex_description),
        checked = snapshot.regexSearchEnabled,
        enabled = writesEnabled,
        testTag = "settings_record_regex",
        onCheckedChange = {
            onSubmit(RecordEditingSettingsCommand.SetRegexSearchEnabled(it))
        },
    )
    SwitchSetting(
        title = stringResource(R.string.settings_record_tag_counts_title),
        description = stringResource(R.string.settings_record_tag_counts_description),
        checked = snapshot.showTagCounts,
        enabled = writesEnabled,
        testTag = "settings_record_tag_counts",
        onCheckedChange = {
            onSubmit(RecordEditingSettingsCommand.SetShowTagCounts(it))
        },
    )
    QuickInsertSettings(
        snapshot = snapshot,
        enabled = writesEnabled,
        onSubmit = onSubmit,
    )
}

@Composable
private fun VisibilitySettings(
    snapshot: RecordEditingSettingsSnapshot,
    enabled: Boolean,
    onSubmit: (RecordEditingSettingsCommand) -> Unit,
) {
    InkCard {
        SectionTitle(
            title = stringResource(R.string.settings_record_visibility_title),
            description = stringResource(R.string.settings_record_visibility_description),
        )
        Column(modifier = Modifier.selectableGroup()) {
            VisibilityOption(
                label = stringResource(R.string.settings_record_visibility_private),
                selected = snapshot.defaultVisibility == MemoVisibility.PRIVATE,
                enabled = enabled,
                testTag = "settings_record_visibility_private",
                onClick = {
                    onSubmit(RecordEditingSettingsCommand.SetDefaultVisibility(MemoVisibility.PRIVATE))
                },
            )
            VisibilityOption(
                label = stringResource(R.string.settings_record_visibility_protected),
                selected = snapshot.defaultVisibility == MemoVisibility.PROTECTED,
                enabled = enabled,
                testTag = "settings_record_visibility_protected",
                onClick = {
                    onSubmit(RecordEditingSettingsCommand.SetDefaultVisibility(MemoVisibility.PROTECTED))
                },
            )
            VisibilityOption(
                label = stringResource(R.string.settings_record_visibility_public),
                selected = snapshot.defaultVisibility == MemoVisibility.PUBLIC,
                enabled = enabled,
                testTag = "settings_record_visibility_public",
                onClick = {
                    onSubmit(RecordEditingSettingsCommand.SetDefaultVisibility(MemoVisibility.PUBLIC))
                },
            )
        }
    }
}

@Composable
private fun VisibilityOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .selectable(
                    selected = selected,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = onClick,
                )
                .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SwitchSetting(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    InkCard {
        SwitchSettingHeader(
            title = title,
            description = description,
            checked = checked,
            enabled = enabled,
            testTag = testTag,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun QuickInsertSettings(
    snapshot: RecordEditingSettingsSnapshot,
    enabled: Boolean,
    onSubmit: (RecordEditingSettingsCommand) -> Unit,
) {
    InkCard {
        SwitchSettingHeader(
            title = stringResource(R.string.settings_record_quick_insert_title),
            description = stringResource(R.string.settings_record_quick_insert_description),
            checked = snapshot.quickInsertTimeEnabled,
            enabled = enabled,
            testTag = "settings_record_quick_insert",
            onCheckedChange = {
                onSubmit(RecordEditingSettingsCommand.SetQuickInsertTimeEnabled(it))
            },
        )
        Text(
            text = stringResource(R.string.settings_record_format_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp),
        )
        Column(modifier = Modifier.selectableGroup()) {
            TimeFormatOption(
                label = stringResource(R.string.settings_record_format_full),
                selected = snapshot.quickInsertTimeFormat == QuickInsertTimeFormat.FULL_DATETIME,
                enabled = enabled,
                testTag = "settings_record_format_full",
                onClick = {
                    onSubmit(
                        RecordEditingSettingsCommand.SetQuickInsertTimeFormat(
                            QuickInsertTimeFormat.FULL_DATETIME,
                        ),
                    )
                },
            )
            TimeFormatOption(
                label = stringResource(R.string.settings_record_format_time),
                selected = snapshot.quickInsertTimeFormat == QuickInsertTimeFormat.TIME_ONLY,
                enabled = enabled,
                testTag = "settings_record_format_time",
                onClick = {
                    onSubmit(
                        RecordEditingSettingsCommand.SetQuickInsertTimeFormat(
                            QuickInsertTimeFormat.TIME_ONLY,
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun SwitchSettingHeader(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    val largeFont = LocalDensity.current.fontScale >= 1.5f
    if (largeFont) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(
                title = title,
                description = description,
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier =
                    Modifier
                        .align(Alignment.End)
                        .padding(top = 8.dp)
                        .heightIn(min = 48.dp)
                        .testTag(testTag),
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle(
                title = title,
                description = description,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier =
                    Modifier
                        .padding(start = 12.dp)
                        .heightIn(min = 48.dp)
                        .testTag(testTag),
            )
        }
    }
}

@Composable
private fun TimeFormatOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    VisibilityOption(
        label = label,
        selected = selected,
        enabled = enabled,
        testTag = testTag,
        onClick = onClick,
    )
}

@Composable
private fun SectionTitle(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
        )
    }
}

@Composable
private fun errorText(error: SettingsCapabilityError): String =
    when (error) {
        SettingsCapabilityError.AuthenticationExpired ->
            stringResource(R.string.settings_record_error_authentication)
        SettingsCapabilityError.NetworkUnavailable ->
            stringResource(R.string.settings_record_error_network)
        SettingsCapabilityError.PermissionDenied ->
            stringResource(R.string.settings_record_error_permission)
        SettingsCapabilityError.PlatformUnavailable ->
            stringResource(R.string.settings_record_error_platform)
        SettingsCapabilityError.InvalidInput ->
            stringResource(R.string.settings_record_error_input)
        SettingsCapabilityError.AlreadyRunning ->
            stringResource(R.string.settings_record_error_running)
        SettingsCapabilityError.StorageFailure ->
            stringResource(R.string.settings_record_error_storage)
        is SettingsCapabilityError.Unknown ->
            stringResource(R.string.settings_record_error_unknown)
    }
