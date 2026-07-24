package cc.pscly.onememos.ui.feature.settings.account

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.pscly.onememos.domain.settings.AccountSyncHealth
import cc.pscly.onememos.domain.settings.AccountSyncSettingsCommand
import cc.pscly.onememos.domain.settings.AccountSyncSettingsSnapshot
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.feature.settings.R
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.feature.settings.common.SettingsConfirmation
import cc.pscly.onememos.ui.theme.InkSpacing
import cc.pscly.onememos.ui.theme.PaperInkAlertDialog

@Composable
fun AdvancedSyncScreen(
    onBack: () -> Unit,
    confirmation: SettingsConfirmation?,
    onDismissConfirmation: () -> Unit,
    viewModel: AccountSyncViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snapshot = uiState.snapshot
    if (snapshot == null) {
        AccountPaperPage {
            AccountHeader(
                title = stringResource(R.string.settings_account_advanced_page_title),
                onBack = onBack,
            )
            SummaryBlock(
                title = stringResource(R.string.settings_account_full_resync_status_title),
                value = stringResource(R.string.settings_account_loading),
            )
        }
        return
    }
    AdvancedSyncContent(
        snapshot = snapshot,
        showConfirmation = confirmation == SettingsConfirmation.FULL_RESYNC,
        onBack = onBack,
        onRequestFullResync = viewModel::requestFullResync,
        onDismissConfirmation = onDismissConfirmation,
        onConfirmFullResync = {
            onDismissConfirmation()
            viewModel.confirmFullResync()
        },
        onAcknowledgeFullResyncCompletion = viewModel::acknowledgeFullResyncCompletion,
        commandError = uiState.fullResyncError,
    )
}

@Composable
fun AdvancedSyncContent(
    snapshot: AccountSyncSettingsSnapshot,
    onBack: () -> Unit,
    onConfirmFullResync: () -> Unit,
    modifier: Modifier = Modifier,
    showConfirmation: Boolean = false,
    onRequestFullResync: () -> Unit = {},
    onDismissConfirmation: () -> Unit = {},
    onAcknowledgeFullResyncCompletion: (String) -> Unit = {},
    commandError: SettingsCapabilityError? = null,
) {
    val completed =
        snapshot.health as? AccountSyncHealth.FullResyncCompleted
    val action = fullResyncAction(snapshot)
    val actionLabel = stringResource(action.labelRes)
    val actionState = stringResource(action.stateRes)
    val focusRequester = remember { FocusRequester() }
    var restoreFocus by remember { mutableStateOf(false) }
    LaunchedEffect(showConfirmation, restoreFocus) {
        if (!showConfirmation && restoreFocus) {
            focusRequester.requestFocus()
            restoreFocus = false
        }
    }

    AccountPaperPage(modifier = modifier) {
        AccountHeader(
            title = stringResource(R.string.settings_account_advanced_page_title),
            onBack = onBack,
        )
        SummaryBlock(
            title = stringResource(R.string.settings_account_full_resync_impact_title),
            value = stringResource(R.string.settings_account_full_resync_impact),
        )
        InkCard {
            Text(
                text = stringResource(R.string.settings_account_full_resync_status_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = fullResyncStatus(snapshot.health),
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (snapshot.health is AccountSyncHealth.FullResyncFailed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.padding(top = InkSpacing.X8),
            )
            if (commandError != null) {
                Text(
                    text = errorText(commandError),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier =
                        Modifier
                            .padding(top = InkSpacing.X8)
                            .testTag("settings_account_full_resync_error"),
                )
            }
            if (completed != null) {
                val acknowledgeLabel =
                    stringResource(R.string.settings_account_full_resync_acknowledge)
                OutlinedButton(
                    onClick = { onAcknowledgeFullResyncCompletion(completed.completionId) },
                    enabled = snapshot.commandInFlight == null,
                    modifier =
                        Modifier
                            .padding(top = InkSpacing.X10)
                            .fillMaxWidth()
                            .heightIn(min = InkSpacing.TouchTargetMin)
                            .testTag("settings_account_full_resync_acknowledge")
                            .semantics { stateDescription = acknowledgeLabel },
                ) {
                    Text(text = acknowledgeLabel)
                }
            } else {
                OutlinedButton(
                    onClick = {
                        onRequestFullResync()
                    },
                    enabled = action.enabled,
                    modifier =
                        Modifier
                            .padding(top = InkSpacing.X10)
                            .fillMaxWidth()
                            .heightIn(min = InkSpacing.TouchTargetMin)
                            .focusRequester(focusRequester)
                            .testTag("settings_account_full_resync_action")
                            .semantics { stateDescription = actionState },
                ) {
                    Text(text = actionLabel)
                }
            }
        }
    }

    if (showConfirmation) {
        val isRetry = snapshot.health is AccountSyncHealth.FullResyncFailed
        PaperInkAlertDialog(
            onDismissRequest = {
                restoreFocus = true
                onDismissConfirmation()
            },
            title = {
                Text(
                    stringResource(
                        if (isRetry) {
                            R.string.settings_account_full_resync_retry_dialog_title
                        } else {
                            R.string.settings_account_full_resync_dialog_title
                        },
                    ),
                )
            },
            text = { Text(stringResource(R.string.settings_account_full_resync_dialog_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        restoreFocus = true
                        onConfirmFullResync()
                    },
                ) {
                    Text(
                        stringResource(
                            if (isRetry) {
                                R.string.settings_account_full_resync_confirm_retry
                            } else {
                                R.string.settings_account_full_resync_confirm_start
                            },
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        restoreFocus = true
                        onDismissConfirmation()
                    },
                ) {
                    Text(stringResource(R.string.settings_account_cancel))
                }
            },
            modifier = Modifier.testTag("settings_account_full_resync_dialog"),
        )
    }
}

@Composable
private fun fullResyncStatus(health: AccountSyncHealth): String =
    when (health) {
        AccountSyncHealth.Unbound,
        AccountSyncHealth.ConfiguredSignedOut,
        AccountSyncHealth.AuthenticationExpired,
        -> stringResource(R.string.settings_account_full_resync_unavailable)
        AccountSyncHealth.Syncing -> stringResource(R.string.settings_account_health_syncing_detail)
        AccountSyncHealth.Queued -> stringResource(R.string.settings_account_health_queued_detail)
        is AccountSyncHealth.FullResyncRunning ->
            stringResource(
                R.string.settings_account_health_full_progress,
                stageText(health.progress.stage),
                health.progress.pagesFetched,
                health.progress.itemsFetched,
            )
        is AccountSyncHealth.FullResyncFailed ->
            "${stringResource(R.string.settings_account_full_resync_failed)} ${errorText(health.error)}"
        is AccountSyncHealth.FullResyncCompleted ->
            stringResource(R.string.settings_account_full_resync_completed)
        else -> stringResource(R.string.settings_account_full_resync_ready)
    }

private fun fullResyncAction(snapshot: AccountSyncSettingsSnapshot): FullResyncAction {
    if (snapshot.commandInFlight == AccountSyncSettingsCommand.FullResync) {
        return FullResyncAction(
            labelRes = R.string.settings_account_full_resync_action_running,
            stateRes = R.string.settings_account_full_resync_running,
            enabled = false,
        )
    }
    return when (snapshot.health) {
        AccountSyncHealth.Unbound,
        AccountSyncHealth.ConfiguredSignedOut,
        AccountSyncHealth.AuthenticationExpired,
        ->
            FullResyncAction(
                labelRes = R.string.settings_account_full_resync_action_unavailable,
                stateRes = R.string.settings_account_full_resync_unavailable,
                enabled = false,
            )
        AccountSyncHealth.Syncing ->
            FullResyncAction(
                labelRes = R.string.settings_account_full_resync_action_syncing,
                stateRes = R.string.settings_account_full_resync_running,
                enabled = false,
            )
        AccountSyncHealth.Queued ->
            FullResyncAction(
                labelRes = R.string.settings_account_full_resync_action_queued,
                stateRes = R.string.settings_account_full_resync_running,
                enabled = false,
            )
        is AccountSyncHealth.FullResyncRunning ->
            FullResyncAction(
                labelRes = R.string.settings_account_full_resync_action_running,
                stateRes = R.string.settings_account_full_resync_running,
                enabled = false,
            )
        is AccountSyncHealth.FullResyncCompleted ->
            FullResyncAction(
                labelRes = R.string.settings_account_full_resync_action_completed,
                stateRes = R.string.settings_account_full_resync_completed,
                enabled = false,
            )
        is AccountSyncHealth.FullResyncFailed ->
            FullResyncAction(
                labelRes = R.string.settings_account_full_resync_action_retry,
                stateRes = R.string.settings_account_full_resync_failed,
                enabled = snapshot.commandInFlight == null,
            )
        else ->
            FullResyncAction(
                labelRes = R.string.settings_account_full_resync_action_start,
                stateRes = R.string.settings_account_full_resync_ready,
                enabled = snapshot.commandInFlight == null,
            )
    }
}

private data class FullResyncAction(
    @StringRes val labelRes: Int,
    @StringRes val stateRes: Int,
    val enabled: Boolean,
)
