package cc.pscly.onememos.ui.feature.settings.storage

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.pscly.onememos.domain.model.CacheStats
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.domain.settings.StorageOfflineSettingsCommand
import cc.pscly.onememos.domain.settings.StorageOfflineSettingsSnapshot
import cc.pscly.onememos.feature.settings.R
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.component.ScrollPaperSurface
import cc.pscly.onememos.ui.component.SealIconButton
import cc.pscly.onememos.ui.feature.settings.common.SettingsConfirmation
import cc.pscly.onememos.ui.feature.settings.common.SettingsUiEvent
import cc.pscly.onememos.ui.util.ByteSizeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest

sealed interface StorageOfflineUiAction {
    data class SetImagePrefetchEnabled(val enabled: Boolean) : StorageOfflineUiAction

    data class SetPrefetchMemoLimit(val value: Int) : StorageOfflineUiAction

    data class SetPrefetchImageLimit(val value: Int) : StorageOfflineUiAction

    data class SetAttachmentCacheLimitMb(val value: Int) : StorageOfflineUiAction

    data object RefreshStats : StorageOfflineUiAction

    data object RequestClearImageCache : StorageOfflineUiAction

    data object RequestClearAttachmentCache : StorageOfflineUiAction

    data object RequestClearAllCache : StorageOfflineUiAction

    data object ConfirmClearImageCache : StorageOfflineUiAction

    data object ConfirmClearAttachmentCache : StorageOfflineUiAction

    data object ConfirmClearAllCache : StorageOfflineUiAction
}

@Composable
fun StorageOfflineScreen(viewModel: StorageOfflineViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmation by remember { mutableStateOf<SettingsConfirmation?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            if (event is SettingsUiEvent.Confirm) {
                confirmation = event.request
            }
        }
    }

    StorageOfflineContent(
        uiState = uiState,
        confirmation = confirmation,
        onAction = { action -> viewModel.dispatch(action) },
        onDismissConfirmation = { confirmation = null },
    )
}

@Composable
fun StorageOfflineContent(
    uiState: StorageOfflineUiState,
    confirmation: SettingsConfirmation?,
    onAction: (StorageOfflineUiAction) -> Unit,
    onDismissConfirmation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stateText =
        when {
            uiState.loading -> stringResource(R.string.settings_storage_state_loading)
            uiState.persistentError != null -> stringResource(R.string.settings_storage_state_error)
            uiState.cleanupDisabled -> stringResource(R.string.settings_storage_state_cleaning)
            else -> stringResource(R.string.settings_storage_state_ready)
        }
    val scroll = rememberScrollState()

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .testTag("settings_storage_root")
                .semantics { stateDescription = stateText },
    ) {
        val contentMaxWidth = maxWidth.coerceAtMost(720.dp)
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            ScrollPaperSurface(
                modifier =
                    Modifier
                        .widthIn(max = contentMaxWidth)
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
                    Text(
                        text = stringResource(R.string.settings_storage_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    StorageOfflineBody(uiState = uiState, onAction = onAction)
                }
            }
        }
    }

    StorageCleanupConfirmation(
        confirmation = confirmation,
        onAction = onAction,
        onDismiss = onDismissConfirmation,
    )
}

@Composable
private fun StorageOfflineBody(
    uiState: StorageOfflineUiState,
    onAction: (StorageOfflineUiAction) -> Unit,
) {
    if (uiState.loading && uiState.snapshot == null) {
        InkCard {
            Text(
                text = stringResource(R.string.settings_storage_loading),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }

    uiState.persistentError?.let { error -> StorageErrorCard(error) }
    uiState.snapshot?.let { snapshot ->
        StorageUsageCard(snapshot = snapshot, onRefresh = { onAction(StorageOfflineUiAction.RefreshStats) })
        OfflinePrefetchCard(snapshot = snapshot, onAction = onAction)
        AttachmentLimitCard(snapshot = snapshot, onAction = onAction)
        CleanupSection(disabled = uiState.cleanupDisabled, onAction = onAction)
    }
}

@Composable
private fun StorageErrorCard(error: SettingsCapabilityError) {
    InkCard {
        Text(
            text =
                when (error) {
                    SettingsCapabilityError.StorageFailure ->
                        stringResource(R.string.settings_storage_error_storage)
                    else -> stringResource(R.string.settings_storage_error_generic)
                },
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun StorageUsageCard(
    snapshot: StorageOfflineSettingsSnapshot,
    onRefresh: () -> Unit,
) {
    val refreshing = snapshot.commandInFlight == StorageOfflineSettingsCommand.RefreshStats
    InkCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_storage_usage_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            SealIconButton(
                icon = Icons.Filled.Refresh,
                contentDescription = stringResource(R.string.settings_storage_refresh),
                enabled = !refreshing,
                onClick = onRefresh,
                modifier = Modifier.testTag("settings_storage_refresh"),
            )
        }
        if (refreshing) {
            Text(
                text = stringResource(R.string.settings_storage_refreshing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StorageStats(snapshot.cacheStats)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_storage_cache_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StorageStats(stats: CacheStats?) {
    val unavailable = stringResource(R.string.settings_storage_stats_unavailable)
    val values =
        listOf(
            R.string.settings_storage_usage_database to stats?.databaseBytes,
            R.string.settings_storage_usage_images to stats?.imageCacheBytes,
            R.string.settings_storage_usage_attachments to stats?.attachmentCacheBytes,
            R.string.settings_storage_usage_other to stats?.otherCacheBytes,
        )
    values.forEach { (label, bytes) ->
        Text(
            text =
                stringResource(
                    label,
                    bytes?.let(ByteSizeFormatter::format) ?: unavailable,
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OfflinePrefetchCard(
    snapshot: StorageOfflineSettingsSnapshot,
    onAction: (StorageOfflineUiAction) -> Unit,
) {
    InkCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_storage_prefetch_title),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.settings_storage_prefetch_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = snapshot.imagePrefetchEnabled,
                onCheckedChange = {
                    onAction(StorageOfflineUiAction.SetImagePrefetchEnabled(it))
                },
                modifier = Modifier.testTag("settings_storage_prefetch_switch"),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text =
                if (snapshot.prefetchMemoLimit <= 0) {
                    stringResource(R.string.settings_storage_range_unlimited)
                } else {
                    stringResource(R.string.settings_storage_range, snapshot.prefetchMemoLimit)
                },
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = snapshot.prefetchMemoLimit.coerceIn(0, 100).toFloat(),
            onValueChange = {
                onAction(StorageOfflineUiAction.SetPrefetchMemoLimit(it.roundToInt()))
            },
            valueRange = 0f..100f,
            steps = 19,
            enabled = snapshot.imagePrefetchEnabled,
            modifier = Modifier.testTag("settings_storage_memo_limit"),
        )
        Text(
            text =
                if (snapshot.prefetchImageLimit <= 0) {
                    stringResource(R.string.settings_storage_image_limit_unlimited)
                } else {
                    stringResource(R.string.settings_storage_image_limit, snapshot.prefetchImageLimit)
                },
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = snapshot.prefetchImageLimit.coerceIn(0, 200).toFloat(),
            onValueChange = {
                onAction(StorageOfflineUiAction.SetPrefetchImageLimit(it.roundToInt()))
            },
            valueRange = 0f..200f,
            steps = 39,
            enabled = snapshot.imagePrefetchEnabled,
            modifier = Modifier.testTag("settings_storage_image_limit"),
        )
        Text(
            text = stringResource(R.string.settings_storage_prefetch_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AttachmentLimitCard(
    snapshot: StorageOfflineSettingsSnapshot,
    onAction: (StorageOfflineUiAction) -> Unit,
) {
    InkCard {
        Text(
            text =
                if (snapshot.attachmentCacheLimitMb <= 0) {
                    stringResource(R.string.settings_storage_attachment_limit_unlimited)
                } else {
                    stringResource(
                        R.string.settings_storage_attachment_limit,
                        snapshot.attachmentCacheLimitMb,
                    )
                },
            style = MaterialTheme.typography.bodyLarge,
        )
        Slider(
            value = snapshot.attachmentCacheLimitMb.coerceIn(0, 2_048).toFloat(),
            onValueChange = {
                onAction(StorageOfflineUiAction.SetAttachmentCacheLimitMb(it.roundToInt()))
            },
            valueRange = 0f..2_048f,
            steps = 31,
            modifier = Modifier.testTag("settings_storage_attachment_limit"),
        )
        Text(
            text = stringResource(R.string.settings_storage_attachment_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CleanupSection(
    disabled: Boolean,
    onAction: (StorageOfflineUiAction) -> Unit,
) {
    Text(
        text = stringResource(R.string.settings_storage_cleanup_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    if (disabled) {
        Text(
            text = stringResource(R.string.settings_storage_cleanup_in_progress),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    CleanupActionCard(
        title = stringResource(R.string.settings_storage_clear_images),
        detail = stringResource(R.string.settings_storage_clear_images_summary),
        tag = "settings_storage_clear_images",
        enabled = !disabled,
        onClick = { onAction(StorageOfflineUiAction.RequestClearImageCache) },
    )
    CleanupActionCard(
        title = stringResource(R.string.settings_storage_clear_attachments),
        detail = stringResource(R.string.settings_storage_clear_attachments_summary),
        tag = "settings_storage_clear_attachments",
        enabled = !disabled,
        onClick = { onAction(StorageOfflineUiAction.RequestClearAttachmentCache) },
    )
    CleanupActionCard(
        title = stringResource(R.string.settings_storage_clear_all),
        detail = stringResource(R.string.settings_storage_clear_all_summary),
        tag = "settings_storage_clear_all",
        enabled = !disabled,
        onClick = { onAction(StorageOfflineUiAction.RequestClearAllCache) },
    )
}

@Composable
private fun CleanupActionCard(
    title: String,
    detail: String,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    InkCard(
        onClick = onClick,
        enabled = enabled,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag(tag),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StorageCleanupConfirmation(
    confirmation: SettingsConfirmation?,
    onAction: (StorageOfflineUiAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val copy = confirmation?.storageConfirmationCopy() ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(copy.titleRes)) },
        text = { Text(stringResource(copy.detailRes)) },
        confirmButton = {
            TextButton(
                onClick = {
                    onAction(copy.confirmAction)
                    onDismiss()
                },
                modifier = Modifier.testTag("settings_storage_confirm_cleanup"),
            ) {
                Text(stringResource(R.string.settings_storage_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_storage_cancel))
            }
        },
    )
}

private data class StorageConfirmationCopy(
    val titleRes: Int,
    val detailRes: Int,
    val confirmAction: StorageOfflineUiAction,
)

private fun SettingsConfirmation.storageConfirmationCopy(): StorageConfirmationCopy? =
    when (this) {
        SettingsConfirmation.CLEAR_IMAGE_CACHE ->
            StorageConfirmationCopy(
                titleRes = R.string.settings_storage_confirm_images_title,
                detailRes = R.string.settings_storage_confirm_images_detail,
                confirmAction = StorageOfflineUiAction.ConfirmClearImageCache,
            )
        SettingsConfirmation.CLEAR_ATTACHMENT_CACHE ->
            StorageConfirmationCopy(
                titleRes = R.string.settings_storage_confirm_attachments_title,
                detailRes = R.string.settings_storage_confirm_attachments_detail,
                confirmAction = StorageOfflineUiAction.ConfirmClearAttachmentCache,
            )
        SettingsConfirmation.CLEAR_ALL_CACHE ->
            StorageConfirmationCopy(
                titleRes = R.string.settings_storage_confirm_all_title,
                detailRes = R.string.settings_storage_confirm_all_detail,
                confirmAction = StorageOfflineUiAction.ConfirmClearAllCache,
            )
        SettingsConfirmation.LOGOUT,
        SettingsConfirmation.FULL_RESYNC,
        SettingsConfirmation.REBUILD_DERIVED_FIELDS,
        -> null
    }

private fun StorageOfflineViewModel.dispatch(action: StorageOfflineUiAction) {
    when (action) {
        is StorageOfflineUiAction.SetImagePrefetchEnabled ->
            setImagePrefetchEnabled(action.enabled)
        is StorageOfflineUiAction.SetPrefetchMemoLimit -> setPrefetchMemoLimit(action.value)
        is StorageOfflineUiAction.SetPrefetchImageLimit -> setPrefetchImageLimit(action.value)
        is StorageOfflineUiAction.SetAttachmentCacheLimitMb ->
            setAttachmentCacheLimitMb(action.value)
        StorageOfflineUiAction.RefreshStats -> refreshStats()
        StorageOfflineUiAction.RequestClearImageCache -> requestClearImageCache()
        StorageOfflineUiAction.RequestClearAttachmentCache -> requestClearAttachmentCache()
        StorageOfflineUiAction.RequestClearAllCache -> requestClearAllCache()
        StorageOfflineUiAction.ConfirmClearImageCache -> confirmClearImageCache()
        StorageOfflineUiAction.ConfirmClearAttachmentCache -> confirmClearAttachmentCache()
        StorageOfflineUiAction.ConfirmClearAllCache -> confirmClearAllCache()
    }
}
