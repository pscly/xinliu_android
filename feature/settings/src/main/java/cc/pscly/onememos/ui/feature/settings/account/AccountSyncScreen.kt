package cc.pscly.onememos.ui.feature.settings.account

import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.pscly.onememos.domain.model.FullSyncStage
import cc.pscly.onememos.domain.settings.AccountSyncHealth
import cc.pscly.onememos.domain.settings.AccountSyncSettingsCommand
import cc.pscly.onememos.domain.settings.AccountSyncSettingsSnapshot
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.feature.settings.R
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.component.ScrollPaperSurface

@Composable
fun AccountSyncScreen(
    onBack: () -> Unit,
    viewModel: AccountSyncViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AccountSyncContent(
        snapshot = uiState.snapshot,
        onBack = onBack,
        onOpenLogin = viewModel::openLogin,
        onSyncNow = viewModel::syncNow,
        onOpenAccountManagement = viewModel::openAccountManagement,
        onOpenAdvancedSync = viewModel::openAdvancedSync,
    )
}

@Composable
fun AccountSyncContent(
    snapshot: AccountSyncSettingsSnapshot?,
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenAccountManagement: () -> Unit,
    onOpenAdvancedSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AccountPaperPage(modifier = modifier) {
        AccountHeader(
            title = stringResource(R.string.settings_account_title),
            onBack = onBack,
            modifier = Modifier.testTag("settings_account_header"),
        )
        HealthBlock(snapshot)
        PrimaryRecoveryAction(
            snapshot = snapshot,
            onOpenLogin = onOpenLogin,
            onSyncNow = onSyncNow,
            onOpenAdvancedSync = onOpenAdvancedSync,
        )
        SummaryBlock(
            title = stringResource(R.string.settings_account_last_success_title),
            value = lastSuccessText(snapshot?.lastSuccessAtEpochMs),
            modifier = Modifier.testTag("settings_account_last_success"),
        )
        SummaryBlock(
            title = stringResource(R.string.settings_account_summary_title),
            value = accountSummary(snapshot),
            modifier = Modifier.testTag("settings_account_summary"),
        )
        NavigationRow(
            title = stringResource(R.string.settings_account_management_title),
            summary = stringResource(R.string.settings_account_management_summary),
            onClick = onOpenAccountManagement,
            modifier = Modifier.testTag("settings_account_management"),
        )
        if (snapshot?.health?.showsAdvancedSync() == true) {
            NavigationRow(
                title = stringResource(R.string.settings_account_advanced_title),
                summary = stringResource(R.string.settings_account_advanced_summary),
                onClick = onOpenAdvancedSync,
                modifier = Modifier.testTag("settings_account_advanced"),
            )
        }
    }
}

@Composable
internal fun AccountPaperPage(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scrollState = rememberScrollState()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val contentMaxWidth = maxWidth.coerceAtMost(720.dp)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            ScrollPaperSurface(
                modifier =
                    Modifier
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth()
                        .fillMaxSize(),
                scrollOffsetPx = scrollState.value.toFloat(),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
internal fun AccountHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier =
                Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("settings_account_back"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.settings_account_back),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun HealthBlock(snapshot: AccountSyncSettingsSnapshot?) {
    val presentation = healthPresentation(snapshot?.health)
    InkCard(
        modifier =
            Modifier
                .testTag("settings_account_health")
                .semantics {
                    stateDescription = presentation.title
                    liveRegion = LiveRegionMode.Polite
                },
    ) {
        Text(
            text = presentation.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (presentation.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = presentation.detail,
            style = MaterialTheme.typography.bodyLarge,
            color = if (presentation.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun PrimaryRecoveryAction(
    snapshot: AccountSyncSettingsSnapshot?,
    onOpenLogin: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenAdvancedSync: () -> Unit,
) {
    val health = snapshot?.health ?: return
    val commandInFlight = snapshot.commandInFlight
    val action =
        when (health) {
            AccountSyncHealth.Unbound,
            AccountSyncHealth.ConfiguredSignedOut,
            -> PrimaryAction(R.string.settings_account_action_login, commandInFlight == null, onOpenLogin)
            is AccountSyncHealth.Healthy,
            is AccountSyncHealth.Failed,
            ->
                PrimaryAction(
                    if (commandInFlight == AccountSyncSettingsCommand.SyncNow) {
                        R.string.settings_account_action_syncing
                    } else {
                        R.string.settings_account_action_sync_now
                    },
                    commandInFlight == null,
                    onSyncNow,
                )
            AccountSyncHealth.Syncing ->
                PrimaryAction(R.string.settings_account_action_syncing, false, onSyncNow)
            AccountSyncHealth.Queued ->
                PrimaryAction(R.string.settings_account_action_queued, false, onSyncNow)
            AccountSyncHealth.AuthenticationExpired ->
                PrimaryAction(R.string.settings_account_action_relogin, commandInFlight == null, onOpenLogin)
            is AccountSyncHealth.FullResyncRunning ->
                PrimaryAction(R.string.settings_account_action_full_running, false, onOpenAdvancedSync)
            is AccountSyncHealth.FullResyncFailed ->
                PrimaryAction(
                    R.string.settings_account_action_troubleshoot,
                    commandInFlight == null,
                    onOpenAdvancedSync,
                )
            is AccountSyncHealth.FullResyncCompleted -> null
        } ?: return
    val label = stringResource(action.labelRes)
    Button(
        onClick = action.onClick,
        enabled = action.enabled,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("settings_account_primary")
                .semantics { stateDescription = label },
    ) {
        Text(text = label)
    }
}

@Composable
internal fun SummaryBlock(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    InkCard(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun NavigationRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enter = stringResource(R.string.settings_account_enter)
    InkCard(
        onClick = onClick,
        contentDescription = "$title，$summary，$enter",
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            NavigationAffordance(enter)
        }
    }
}

@Composable
private fun RowScope.NavigationAffordance(enter: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 8.dp),
    ) {
        Text(
            text = enter,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun healthPresentation(health: AccountSyncHealth?): HealthPresentation =
    when (health) {
        null ->
            HealthPresentation(
                title = stringResource(R.string.settings_account_loading),
                detail = stringResource(R.string.settings_account_loading),
            )
        AccountSyncHealth.Unbound ->
            HealthPresentation(
                title = stringResource(R.string.settings_account_health_unbound),
                detail = stringResource(R.string.settings_account_health_unbound_detail),
            )
        AccountSyncHealth.ConfiguredSignedOut ->
            HealthPresentation(
                title = stringResource(R.string.settings_account_health_signed_out),
                detail = stringResource(R.string.settings_account_health_signed_out_detail),
            )
        is AccountSyncHealth.Healthy ->
            HealthPresentation(
                title = stringResource(R.string.settings_account_health_healthy),
                detail = stringResource(R.string.settings_account_health_healthy_detail),
            )
        AccountSyncHealth.Syncing ->
            HealthPresentation(
                title = stringResource(R.string.settings_account_health_syncing),
                detail = stringResource(R.string.settings_account_health_syncing_detail),
            )
        AccountSyncHealth.Queued ->
            HealthPresentation(
                title = stringResource(R.string.settings_account_health_queued),
                detail = stringResource(R.string.settings_account_health_queued_detail),
            )
        is AccountSyncHealth.Failed ->
            HealthPresentation(
                title = stringResource(R.string.settings_account_health_failed),
                detail = errorText(health.error),
                isError = true,
            )
        AccountSyncHealth.AuthenticationExpired ->
            HealthPresentation(
                title = stringResource(R.string.settings_account_health_auth_expired),
                detail = stringResource(R.string.settings_account_health_auth_expired_detail),
                isError = true,
            )
        is AccountSyncHealth.FullResyncRunning ->
            HealthPresentation(
                title = stringResource(R.string.settings_account_health_full_running),
                detail =
                    stringResource(
                        R.string.settings_account_health_full_progress,
                        stageText(health.progress.stage),
                        health.progress.pagesFetched,
                        health.progress.itemsFetched,
                    ),
            )
        is AccountSyncHealth.FullResyncFailed ->
            HealthPresentation(
                title = stringResource(R.string.settings_account_health_full_failed),
                detail = errorText(health.error),
                isError = true,
            )
        is AccountSyncHealth.FullResyncCompleted ->
            HealthPresentation(
                title = stringResource(R.string.settings_account_health_full_completed),
                detail = stringResource(R.string.settings_account_health_full_completed_detail),
            )
    }

@Composable
internal fun errorText(error: SettingsCapabilityError): String =
    when (error) {
        SettingsCapabilityError.AuthenticationExpired -> stringResource(R.string.settings_account_error_auth)
        SettingsCapabilityError.NetworkUnavailable -> stringResource(R.string.settings_account_error_network)
        SettingsCapabilityError.PermissionDenied -> stringResource(R.string.settings_account_error_permission)
        SettingsCapabilityError.PlatformUnavailable -> stringResource(R.string.settings_account_error_platform)
        SettingsCapabilityError.InvalidInput -> stringResource(R.string.settings_account_error_input)
        SettingsCapabilityError.AlreadyRunning -> stringResource(R.string.settings_account_error_running)
        SettingsCapabilityError.StorageFailure -> stringResource(R.string.settings_account_error_storage)
        is SettingsCapabilityError.Unknown -> stringResource(R.string.settings_account_error_unknown)
    }

@Composable
internal fun accountSummary(snapshot: AccountSyncSettingsSnapshot?): String {
    val accountLabel = snapshot?.accountLabel?.takeIf(String::isNotBlank)
    return when {
        snapshot == null -> stringResource(R.string.settings_account_loading)
        snapshot.health is AccountSyncHealth.Unbound -> stringResource(R.string.settings_account_summary_unbound)
        accountLabel != null -> stringResource(R.string.settings_account_summary_named, accountLabel)
        snapshot.health is AccountSyncHealth.ConfiguredSignedOut ->
            stringResource(R.string.settings_account_summary_configured)
        else -> stringResource(R.string.settings_account_summary_connected)
    }
}

@Composable
private fun lastSuccessText(epochMs: Long?): String {
    if (epochMs == null) return stringResource(R.string.settings_account_last_success_none)
    val context = LocalContext.current
    val formatted =
        DateUtils.formatDateTime(
            context,
            epochMs,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME,
        )
    return stringResource(R.string.settings_account_last_success_value, formatted)
}

@Composable
internal fun stageText(stage: FullSyncStage): String =
    when (stage) {
        FullSyncStage.NORMAL -> stringResource(R.string.settings_account_stage_normal)
        FullSyncStage.ARCHIVED -> stringResource(R.string.settings_account_stage_archived)
    }

internal fun AccountSyncHealth.showsAdvancedSync(): Boolean =
    this !is AccountSyncHealth.Unbound &&
        this !is AccountSyncHealth.ConfiguredSignedOut &&
        this !is AccountSyncHealth.AuthenticationExpired

private data class PrimaryAction(
    @StringRes val labelRes: Int,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

private data class HealthPresentation(
    val title: String,
    val detail: String,
    val isError: Boolean = false,
)
