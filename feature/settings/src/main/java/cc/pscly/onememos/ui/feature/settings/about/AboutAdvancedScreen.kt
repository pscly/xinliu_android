package cc.pscly.onememos.ui.feature.settings.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.pscly.onememos.domain.settings.DeveloperOptions
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.domain.settings.UpdateSettingsPhase
import cc.pscly.onememos.feature.settings.R
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.component.ScrollPaperSurface
import cc.pscly.onememos.ui.feature.settings.common.SettingsConfirmation
import cc.pscly.onememos.ui.feature.settings.common.SettingsMessage
import cc.pscly.onememos.ui.feature.settings.common.SettingsUiEvent

/**
 * 关于与高级能力页：单列纸墨布局，最大内容宽 720dp。
 * 不在进入时检查更新/导出诊断/下载/安装；Quick Capture 只发平台动作。
 */
data class AboutAdvancedContentCallbacks(
    val onCheckForUpdates: () -> Unit,
    val onDownloadUpdate: () -> Unit,
    val onInstallUpdate: () -> Unit,
    val onClearIgnoredUpdate: () -> Unit,
    val onExportDiagnostics: () -> Unit,
    val onRequestQuickCaptureTile: () -> Unit,
    val onRequestScreenshotTile: () -> Unit,
    val onOpenQuickCapture: () -> Unit,
    val onOpenScreenshotCapture: () -> Unit,
    val onRequestRebuildDerivedFields: () -> Unit,
    val onConfirmRebuildDerivedFields: () -> Unit,
    val onDismissRebuildConfirm: () -> Unit,
    val onSetAttachmentUploadLimitMb: (Int) -> Unit,
    val onSetDeveloperOptions: (DeveloperOptions) -> Unit,
)

@Composable
fun AboutAdvancedScreen(
    viewModel: AboutAdvancedViewModel = hiltViewModel(),
    onPlatformEvent: (SettingsUiEvent.Platform) -> Unit = {},
    onUpdateDeliveryEvent: (SettingsUiEvent.UpdateDelivery) -> Unit = {},
    onToast: (SettingsMessage) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showRebuildConfirm by remember { mutableStateOf(false) }
    var lastAnnouncement by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsUiEvent.Platform -> onPlatformEvent(event)
                is SettingsUiEvent.UpdateDelivery -> onUpdateDeliveryEvent(event)
                is SettingsUiEvent.Toast -> {
                    onToast(event.message)
                    lastAnnouncement =
                        when (event.message) {
                            SettingsMessage.COMMAND_SUCCEEDED -> "success"
                            SettingsMessage.COMMAND_FAILED -> "failed"
                            SettingsMessage.PERMISSION_DENIED -> "failed"
                        }
                }
                is SettingsUiEvent.Confirm -> {
                    if (event.request == SettingsConfirmation.REBUILD_DERIVED_FIELDS) {
                        showRebuildConfirm = true
                    }
                }
                is SettingsUiEvent.Navigate -> Unit
            }
        }
    }

    AboutAdvancedContent(
        state = uiState,
        callbacks =
            AboutAdvancedContentCallbacks(
                onCheckForUpdates = viewModel::onCheckForUpdates,
                onDownloadUpdate = viewModel::onDownloadUpdate,
                onInstallUpdate = viewModel::onInstallUpdate,
                onClearIgnoredUpdate = viewModel::onClearIgnoredUpdate,
                onExportDiagnostics = viewModel::onExportDiagnostics,
                onRequestQuickCaptureTile = viewModel::onRequestQuickCaptureTile,
                onRequestScreenshotTile = viewModel::onRequestScreenshotTile,
                onOpenQuickCapture = viewModel::onOpenQuickCapture,
                onOpenScreenshotCapture = viewModel::onOpenScreenshotCapture,
                onRequestRebuildDerivedFields = viewModel::onRequestRebuildDerivedFields,
                onConfirmRebuildDerivedFields = {
                    showRebuildConfirm = false
                    viewModel.onConfirmRebuildDerivedFields()
                },
                onDismissRebuildConfirm = { showRebuildConfirm = false },
                onSetAttachmentUploadLimitMb = viewModel::onSetAttachmentUploadLimitMb,
                onSetDeveloperOptions = viewModel::onSetDeveloperOptions,
            ),
        showRebuildConfirm = showRebuildConfirm,
        lastAnnouncement = lastAnnouncement,
    )
}

@Composable
fun AboutAdvancedContent(
    state: AboutAdvancedUiState,
    callbacks: AboutAdvancedContentCallbacks,
    showRebuildConfirm: Boolean,
    lastAnnouncement: String?,
    modifier: Modifier = Modifier,
) {
    val snapshot = state.snapshot
    val busy = state.actionsDisabled
    val scroll = rememberScrollState()
    val announcementText =
        when (lastAnnouncement) {
            "success" -> stringResource(R.string.settings_about_result_success)
            "failed" -> stringResource(R.string.settings_about_result_failed)
            else -> ""
        }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .testTag("settings_about_root"),
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
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("settings_about_list"),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_about_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("settings_about_title"),
                    )

                    if (announcementText.isNotEmpty()) {
                        Text(
                            text = announcementText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier =
                                Modifier
                                    .testTag("settings_about_result_announcer")
                                    .semantics {
                                        liveRegion = LiveRegionMode.Polite
                                    },
                        )
                    } else {
                        // 固定存在，便于测试与动态播报挂载
                        Spacer(
                            modifier =
                                Modifier
                                    .height(0.dp)
                                    .testTag("settings_about_result_announcer")
                                    .semantics {
                                        liveRegion = LiveRegionMode.Polite
                                    },
                        )
                    }

                    if (state.loading || snapshot == null) {
                        Text(
                            text = stringResource(R.string.settings_about_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("settings_about_loading"),
                        )
                    } else {
                        VersionCard(snapshot.versionName, snapshot.versionCode, snapshot.buildType)
                        UpdateCard(
                            update = snapshot.update,
                            busy = busy,
                            persistentError = state.persistentError,
                            onCheck = callbacks.onCheckForUpdates,
                            onDownload = callbacks.onDownloadUpdate,
                            onInstall = callbacks.onInstallUpdate,
                            onClearIgnored = callbacks.onClearIgnoredUpdate,
                        )
                        TilesCard(
                            busy = busy,
                            onQuickTile = callbacks.onRequestQuickCaptureTile,
                            onQuickOpen = callbacks.onOpenQuickCapture,
                            onShotTile = callbacks.onRequestScreenshotTile,
                            onShotOpen = callbacks.onOpenScreenshotCapture,
                        )
                        DiagnosticsCard(
                            busy = busy,
                            available = snapshot.diagnosticsAvailable,
                            onExport = callbacks.onExportDiagnostics,
                        )
                        RebuildCard(
                            busy = busy,
                            onRebuild = callbacks.onRequestRebuildDerivedFields,
                        )
                        UploadCard(
                            busy = busy,
                            limitMb = snapshot.attachmentUploadLimitMb,
                            onLimit = callbacks.onSetAttachmentUploadLimitMb,
                        )
                        DeveloperCard(
                            busy = busy,
                            options = snapshot.developerOptions,
                            onOptions = callbacks.onSetDeveloperOptions,
                        )
                    }
                }
            }
        }
    }

    if (showRebuildConfirm) {
        AlertDialog(
            onDismissRequest = callbacks.onDismissRebuildConfirm,
            title = { Text(stringResource(R.string.settings_about_rebuild_confirm_title)) },
            text = { Text(stringResource(R.string.settings_about_rebuild_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = callbacks.onConfirmRebuildDerivedFields,
                    modifier =
                        Modifier
                            .heightIn(min = 48.dp)
                            .testTag("settings_about_rebuild_confirm"),
                ) {
                    Text(stringResource(R.string.settings_about_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = callbacks.onDismissRebuildConfirm,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.settings_about_cancel))
                }
            },
        )
    }
}

@Composable
private fun VersionCard(
    versionName: String,
    versionCode: Long,
    buildType: String,
) {
    InkCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("settings_about_version"),
    ) {
        Text(
            text = stringResource(R.string.settings_about_version_label),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Box(modifier = Modifier.height(6.dp))
        Text(
            text =
                stringResource(
                    R.string.settings_about_version_format,
                    versionName,
                    versionCode.toInt(),
                ),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag("settings_about_version_value"),
        )
        Text(
            text = stringResource(R.string.settings_about_build_type, buildType),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UpdateCard(
    update: cc.pscly.onememos.domain.settings.UpdateSettingsSnapshot,
    busy: Boolean,
    persistentError: SettingsCapabilityError?,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onClearIgnored: () -> Unit,
) {
    val statusText = updateStatusText(update)
    val errorText =
        when {
            update.error != null -> mapError(update.error!!)
            persistentError != null -> mapError(persistentError)
            update.phase == UpdateSettingsPhase.ERROR ->
                stringResource(R.string.settings_about_update_error)
            else -> null
        }
    val stateDesc =
        when {
            errorText != null -> stringResource(R.string.settings_about_state_error)
            update.phase == UpdateSettingsPhase.CHECKING ||
                update.phase == UpdateSettingsPhase.DOWNLOADING ->
                stringResource(R.string.settings_about_state_busy)
            else -> stringResource(R.string.settings_about_state_ready)
        }

    InkCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_about_update_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Box(modifier = Modifier.height(6.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            modifier =
                Modifier
                    .testTag("settings_about_update_status")
                    .semantics { stateDescription = stateDesc },
        )
        update.downloadProgressPercent?.let { progress ->
            Box(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0, 100) / 100f },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("settings_about_update_progress"),
            )
        }
        if (errorText != null) {
            Box(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .testTag("settings_about_update_error")
                        .semantics { stateDescription = errorText },
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 4.dp),
                )
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionButton(
                text = stringResource(R.string.settings_about_check_update),
                enabled = !busy && update.phase != UpdateSettingsPhase.CHECKING,
                onClick = onCheck,
                testTag = "settings_about_check_update",
                modifier = Modifier.weight(1f),
            )
            when (update.phase) {
                UpdateSettingsPhase.AVAILABLE ->
                    ActionButton(
                        text = stringResource(R.string.settings_about_download_update),
                        enabled = !busy,
                        onClick = onDownload,
                        testTag = "settings_about_download_update",
                        modifier = Modifier.weight(1f),
                    )
                UpdateSettingsPhase.READY_TO_INSTALL ->
                    ActionButton(
                        text = stringResource(R.string.settings_about_install_update),
                        enabled = !busy,
                        onClick = onInstall,
                        testTag = "settings_about_install_update",
                        modifier = Modifier.weight(1f),
                    )
                else -> Unit
            }
        }
        update.ignoredVersionTag?.takeIf { it.isNotBlank() }?.let { tag ->
            Box(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_about_update_ignored, tag),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ActionButton(
                text = stringResource(R.string.settings_about_clear_ignored),
                enabled = !busy,
                onClick = onClearIgnored,
                testTag = "settings_about_clear_ignored",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TilesCard(
    busy: Boolean,
    onQuickTile: () -> Unit,
    onQuickOpen: () -> Unit,
    onShotTile: () -> Unit,
    onShotOpen: () -> Unit,
) {
    InkCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_about_tiles_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Box(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_about_quick_capture_title),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.settings_about_quick_capture_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(
                text = stringResource(R.string.settings_about_add_tile),
                enabled = !busy,
                onClick = onQuickTile,
                testTag = "settings_about_quick_tile",
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                text = stringResource(R.string.settings_about_open_now),
                enabled = !busy,
                onClick = onQuickOpen,
                testTag = "settings_about_quick_open",
                modifier = Modifier.weight(1f),
            )
        }
        Box(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.settings_about_screenshot_title),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.settings_about_screenshot_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(
                text = stringResource(R.string.settings_about_add_tile),
                enabled = !busy,
                onClick = onShotTile,
                testTag = "settings_about_screenshot_tile",
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                text = stringResource(R.string.settings_about_open_now),
                enabled = !busy,
                onClick = onShotOpen,
                testTag = "settings_about_screenshot_open",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DiagnosticsCard(
    busy: Boolean,
    available: Boolean,
    onExport: () -> Unit,
) {
    InkCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_about_diagnostics_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Box(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.settings_about_diagnostics_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (available) {
            Text(
                text = stringResource(R.string.settings_about_diagnostics_ready),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Box(modifier = Modifier.height(8.dp))
        ActionButton(
            text = stringResource(R.string.settings_about_export_diagnostics),
            enabled = !busy,
            onClick = onExport,
            testTag = "settings_about_export",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RebuildCard(
    busy: Boolean,
    onRebuild: () -> Unit,
) {
    InkCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_about_rebuild_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Box(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.settings_about_rebuild_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(modifier = Modifier.height(8.dp))
        ActionButton(
            text = stringResource(R.string.settings_about_rebuild_action),
            enabled = !busy,
            onClick = onRebuild,
            testTag = "settings_about_rebuild",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun UploadCard(
    busy: Boolean,
    limitMb: Int,
    onLimit: (Int) -> Unit,
) {
    var text by remember(limitMb) { mutableStateOf(limitMb.toString()) }
    InkCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("settings_about_upload_section"),
    ) {
        Text(
            text = stringResource(R.string.settings_about_upload_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Box(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.settings_about_upload_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }
                text = digits
                val parsed = digits.toIntOrNull() ?: return@OutlinedTextField
                val clamped = parsed.coerceIn(1, 1024)
                if (!busy) onLimit(clamped)
            },
            enabled = !busy,
            label = { Text(stringResource(R.string.settings_about_upload_limit_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("settings_about_upload_limit"),
        )
    }
}

@Composable
private fun DeveloperCard(
    busy: Boolean,
    options: DeveloperOptions,
    onOptions: (DeveloperOptions) -> Unit,
) {
    InkCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("settings_about_developer_section"),
    ) {
        Text(
            text = stringResource(R.string.settings_about_developer_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Box(modifier = Modifier.height(6.dp))
        Text(
            text =
                if (options.unlocked) {
                    stringResource(R.string.settings_about_developer_unlocked)
                } else {
                    stringResource(R.string.settings_about_developer_locked)
                },
            style = MaterialTheme.typography.bodyMedium,
        )
        if (options.unlocked) {
            Box(modifier = Modifier.height(8.dp))
            DevSwitchRow(
                label = stringResource(R.string.settings_about_developer_public),
                checked = options.showPublicWorkspaceMemos,
                enabled = !busy,
                onChecked = { onOptions(options.copy(showPublicWorkspaceMemos = it)) },
            )
            DevSwitchRow(
                label = stringResource(R.string.settings_about_developer_home),
                checked = options.showAutoTagLineInHome,
                enabled = !busy,
                onChecked = { onOptions(options.copy(showAutoTagLineInHome = it)) },
            )
            DevSwitchRow(
                label = stringResource(R.string.settings_about_developer_view),
                checked = options.showAutoTagLineInView,
                enabled = !busy,
                onChecked = { onOptions(options.copy(showAutoTagLineInView = it)) },
            )
            DevSwitchRow(
                label = stringResource(R.string.settings_about_developer_edit),
                checked = options.showAutoTagLineInEdit,
                enabled = !busy,
                onChecked = { onOptions(options.copy(showAutoTagLineInEdit = it)) },
            )
            Box(modifier = Modifier.height(8.dp))
            ActionButton(
                text = stringResource(R.string.settings_about_developer_exit),
                enabled = !busy,
                onClick = {
                    onOptions(
                        options.copy(
                            unlocked = false,
                            showPublicWorkspaceMemos = false,
                        ),
                    )
                },
                testTag = "settings_about_developer_exit",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DevSwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            enabled = enabled,
        )
    }
}

@Composable
private fun ActionButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    InkCard(
        onClick = onClick,
        enabled = enabled,
        contentDescription = text,
        modifier =
            modifier
                .heightIn(min = 48.dp)
                .testTag(testTag),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun updateStatusText(update: cc.pscly.onememos.domain.settings.UpdateSettingsSnapshot): String {
    return when (update.phase) {
        UpdateSettingsPhase.IDLE -> stringResource(R.string.settings_about_update_idle)
        UpdateSettingsPhase.CHECKING -> stringResource(R.string.settings_about_update_checking)
        UpdateSettingsPhase.AVAILABLE ->
            stringResource(
                R.string.settings_about_update_available,
                update.availableVersion.orEmpty(),
            )
        UpdateSettingsPhase.DOWNLOADING ->
            stringResource(
                R.string.settings_about_update_downloading,
                update.downloadProgressPercent ?: 0,
            )
        UpdateSettingsPhase.READY_TO_INSTALL -> stringResource(R.string.settings_about_update_ready)
        UpdateSettingsPhase.UP_TO_DATE -> stringResource(R.string.settings_about_update_up_to_date)
        UpdateSettingsPhase.ERROR -> stringResource(R.string.settings_about_update_error)
    }
}

@Composable
private fun mapError(error: SettingsCapabilityError): String {
    return when (error) {
        SettingsCapabilityError.AuthenticationExpired ->
            stringResource(R.string.settings_about_error_auth)
        SettingsCapabilityError.NetworkUnavailable ->
            stringResource(R.string.settings_about_error_network)
        SettingsCapabilityError.PermissionDenied ->
            stringResource(R.string.settings_about_error_permission)
        SettingsCapabilityError.PlatformUnavailable ->
            stringResource(R.string.settings_about_error_platform)
        SettingsCapabilityError.InvalidInput ->
            stringResource(R.string.settings_about_error_invalid)
        SettingsCapabilityError.AlreadyRunning ->
            stringResource(R.string.settings_about_error_running)
        SettingsCapabilityError.StorageFailure ->
            stringResource(R.string.settings_about_error_storage)
        is SettingsCapabilityError.Unknown ->
            stringResource(R.string.settings_about_error_generic)
    }
}
