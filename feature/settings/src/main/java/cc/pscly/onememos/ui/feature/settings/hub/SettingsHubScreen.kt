package cc.pscly.onememos.ui.feature.settings.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.pscly.onememos.domain.settings.SectionSummaryState
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.domain.settings.SettingsHubSnapshot
import cc.pscly.onememos.domain.settings.SummaryIssueKind
import cc.pscly.onememos.feature.settings.R
import cc.pscly.onememos.navigation.AboutAdvancedSettingsKey
import cc.pscly.onememos.navigation.AccountSyncSettingsKey
import cc.pscly.onememos.navigation.AppearanceInteractionSettingsKey
import cc.pscly.onememos.navigation.OneMemosNavKey
import cc.pscly.onememos.navigation.RecordEditingSettingsKey
import cc.pscly.onememos.navigation.ReminderCalendarSettingsKey
import cc.pscly.onememos.navigation.StorageOfflineSettingsKey
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.component.ScrollPaperSurface
import cc.pscly.onememos.ui.theme.InkSpacing

private data class HubRow(
    val index: Int,
    val titleRes: Int,
    val state: SectionSummaryState,
    val key: OneMemosNavKey,
    val testTag: String,
)

/**
 * 线册式只读设置首页：固定六行、单列、最大内容宽 720dp。
 * 无 Switch/Checkbox/Slider/内联写操作；仅收集 StateFlow。
 */
@Composable
fun SettingsHubScreen(
    onOpen: (OneMemosNavKey) -> Unit,
    viewModel: SettingsHubViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsHubContent(
        snapshot = uiState.snapshot,
        onOpen = onOpen,
    )
}

@Composable
fun SettingsHubContent(
    snapshot: SettingsHubSnapshot?,
    onOpen: (OneMemosNavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = hubRows(snapshot)
    val scroll = rememberScrollState()
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .testTag("settings_hub_root"),
    ) {
        val contentMax = maxWidth.coerceAtMost(InkSpacing.ContentMaxWidth)
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
                            .padding(vertical = InkSpacing.X8)
                            .testTag("settings_hub_list"),
                    verticalArrangement = Arrangement.spacedBy(InkSpacing.X10),
                ) {
                    Text(
                        text = stringResource(R.string.settings_hub_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier =
                            Modifier
                                .padding(bottom = InkSpacing.X4)
                                .testTag("settings_hub_title"),
                    )
                    rows.forEach { row ->
                        HubSectionRow(
                            row = row,
                            onOpen = onOpen,
                        )
                    }
                }
            }
        }
    }
}

private fun hubRows(snapshot: SettingsHubSnapshot?): List<HubRow> {
    val loading = SectionSummaryState.Loading
    return listOf(
        HubRow(
            index = 1,
            titleRes = R.string.settings_hub_section_account,
            state = snapshot?.accountSync ?: loading,
            key = AccountSyncSettingsKey,
            testTag = "settings_hub_row_account",
        ),
        HubRow(
            index = 2,
            titleRes = R.string.settings_hub_section_record,
            state = snapshot?.recordEditing ?: loading,
            key = RecordEditingSettingsKey,
            testTag = "settings_hub_row_record",
        ),
        HubRow(
            index = 3,
            titleRes = R.string.settings_hub_section_reminder,
            state = snapshot?.reminderCalendar ?: loading,
            key = ReminderCalendarSettingsKey,
            testTag = "settings_hub_row_reminder",
        ),
        HubRow(
            index = 4,
            titleRes = R.string.settings_hub_section_storage,
            state = snapshot?.storageOffline ?: loading,
            key = StorageOfflineSettingsKey,
            testTag = "settings_hub_row_storage",
        ),
        HubRow(
            index = 5,
            titleRes = R.string.settings_hub_section_appearance,
            state = snapshot?.appearanceInteraction ?: loading,
            key = AppearanceInteractionSettingsKey,
            testTag = "settings_hub_row_appearance",
        ),
        HubRow(
            index = 6,
            titleRes = R.string.settings_hub_section_about,
            state = snapshot?.aboutAdvanced ?: loading,
            key = AboutAdvancedSettingsKey,
            testTag = "settings_hub_row_about",
        ),
    )
}

@Composable
private fun HubSectionRow(
    row: HubRow,
    onOpen: (OneMemosNavKey) -> Unit,
) {
    val title = stringResource(row.titleRes)
    val enterLabel = stringResource(R.string.settings_hub_enter)
    val summaryLines = summaryLines(row.state)
    val issueText = issueText(row.state)
    val contentDesc =
        buildString {
            append(row.index)
            append(". ")
            append(title)
            if (issueText != null) {
                append("，")
                append(issueText)
            }
            summaryLines.forEach {
                append("，")
                append(it)
            }
            append("，")
            append(enterLabel)
        }
    val stateDesc =
        when {
            issueText != null -> issueText
            row.state is SectionSummaryState.Loading -> stringResource(R.string.settings_hub_loading)
            row.state is SectionSummaryState.Error -> stringResource(R.string.settings_hub_error_generic)
            else -> stringResource(R.string.settings_hub_ready)
        }

    // testTag 挂在 InkCard 上，保证点击与最小高度断言命中可交互节点；
    // 子节点标签通过 useUnmergedTree 读取。
    InkCard(
        onClick = { onOpen(row.key) },
        contentDescription = contentDesc,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = InkSpacing.TouchTargetMin)
                .testTag(row.testTag)
                .semantics {
                    stateDescription = stateDesc
                },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.index.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .padding(end = InkSpacing.X12)
                        .testTag("${row.testTag}_index"),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (issueText != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = InkSpacing.X2),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(end = InkSpacing.X4),
                        )
                        Text(
                            text = issueText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("${row.testTag}_issue"),
                        )
                    }
                }
                summaryLines.forEachIndexed { i, line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .padding(top = if (i == 0 && issueText == null) InkSpacing.X2 else 0.dp)
                                .testTag("${row.testTag}_summary_$i"),
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = InkSpacing.X8),
            ) {
                Text(
                    text = enterLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("${row.testTag}_enter"),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun summaryLines(state: SectionSummaryState): List<String> {
    return when (state) {
        is SectionSummaryState.Loading -> listOf(stringResource(R.string.settings_hub_loading))
        is SectionSummaryState.Error -> listOf(mapError(state.error))
        is SectionSummaryState.Ready -> {
            val lines = mutableListOf<String>()
            lines.add(mapFact(state.primary.value))
            state.secondary?.let { lines.add(mapFact(it.value)) }
            lines.take(2)
        }
    }
}

@Composable
private fun issueText(state: SectionSummaryState): String? {
    val ready = state as? SectionSummaryState.Ready ?: return null
    val issue = ready.issue ?: return null
    return when (issue.kind) {
        SummaryIssueKind.AUTHENTICATION_EXPIRED ->
            stringResource(R.string.settings_hub_issue_auth_expired)
        SummaryIssueKind.LAST_SYNC_FAILED ->
            stringResource(R.string.settings_hub_issue_sync_failed)
        SummaryIssueKind.FULL_RESYNC_FAILED ->
            stringResource(R.string.settings_hub_issue_full_resync_failed)
        SummaryIssueKind.PERMISSION_REQUIRED ->
            stringResource(R.string.settings_hub_issue_permission)
        SummaryIssueKind.STORAGE_FAILURE ->
            stringResource(R.string.settings_hub_issue_storage)
        SummaryIssueKind.UPDATE_FAILURE ->
            stringResource(R.string.settings_hub_issue_update)
        SummaryIssueKind.DIAGNOSTICS_FAILURE ->
            stringResource(R.string.settings_hub_issue_diagnostics)
        SummaryIssueKind.PREFERENCE_READ_FAILURE ->
            stringResource(R.string.settings_hub_issue_preference)
    }
}

@Composable
private fun mapError(error: SettingsCapabilityError): String {
    return when (error) {
        SettingsCapabilityError.AuthenticationExpired ->
            stringResource(R.string.settings_hub_issue_auth_expired)
        SettingsCapabilityError.NetworkUnavailable ->
            stringResource(R.string.settings_hub_error_network)
        SettingsCapabilityError.PermissionDenied ->
            stringResource(R.string.settings_hub_issue_permission)
        SettingsCapabilityError.PlatformUnavailable ->
            stringResource(R.string.settings_hub_error_platform)
        SettingsCapabilityError.InvalidInput ->
            stringResource(R.string.settings_hub_error_generic)
        SettingsCapabilityError.AlreadyRunning ->
            stringResource(R.string.settings_hub_error_generic)
        SettingsCapabilityError.StorageFailure ->
            stringResource(R.string.settings_hub_issue_storage)
        is SettingsCapabilityError.Unknown ->
            stringResource(R.string.settings_hub_error_generic)
    }
}

@Composable
private fun mapFact(token: String): String {
    return when {
        token == "ACCOUNT_UNBOUND" -> stringResource(R.string.settings_hub_fact_account_unbound)
        token == "ACCOUNT_CONFIGURED_SIGNED_OUT" ->
            stringResource(R.string.settings_hub_fact_account_signed_out)
        token == "ACCOUNT_AUTH_EXPIRED" -> stringResource(R.string.settings_hub_fact_account_auth_expired)
        token == "ACCOUNT_FULL_RESYNC_FAILED" ->
            stringResource(R.string.settings_hub_fact_account_full_resync_failed)
        token == "ACCOUNT_LAST_SYNC_FAILED" ->
            stringResource(R.string.settings_hub_fact_account_sync_failed)
        token == "ACCOUNT_FULL_RESYNC_RUNNING" ->
            stringResource(R.string.settings_hub_fact_account_full_resync_running)
        token == "ACCOUNT_SYNCING" -> stringResource(R.string.settings_hub_fact_account_syncing)
        token == "ACCOUNT_QUEUED" -> stringResource(R.string.settings_hub_fact_account_queued)
        token == "ACCOUNT_HEALTHY" -> stringResource(R.string.settings_hub_fact_account_healthy)
        token.startsWith("LAST_SUCCESS_AT_") -> stringResource(R.string.settings_hub_fact_last_success)
        token == "VISIBILITY_PRIVATE" -> stringResource(R.string.settings_hub_fact_visibility_private)
        token == "VISIBILITY_PROTECTED" -> stringResource(R.string.settings_hub_fact_visibility_protected)
        token == "VISIBILITY_PUBLIC" -> stringResource(R.string.settings_hub_fact_visibility_public)
        token == "REGEX_ON" -> stringResource(R.string.settings_hub_fact_regex_on)
        token == "QUICK_INSERT_ON" -> stringResource(R.string.settings_hub_fact_quick_insert_on)
        token == "TAG_COUNTS_ON" -> stringResource(R.string.settings_hub_fact_tag_counts_on)
        token == "REMINDER_SMART" -> stringResource(R.string.settings_hub_fact_reminder_smart)
        token == "REMINDER_EXACT" -> stringResource(R.string.settings_hub_fact_reminder_exact)
        token == "CALENDAR_OFF" -> stringResource(R.string.settings_hub_fact_calendar_off)
        token == "CALENDAR_CONNECTED" -> stringResource(R.string.settings_hub_fact_calendar_connected)
        token == "CALENDAR_ENABLED_NO_TARGET" ->
            stringResource(R.string.settings_hub_fact_calendar_no_target)
        token == "PREFETCH_ON" -> stringResource(R.string.settings_hub_fact_prefetch_on)
        token == "PREFETCH_OFF" -> stringResource(R.string.settings_hub_fact_prefetch_off)
        token.startsWith("CACHE_LIMIT_MB_") -> {
            val mb = token.removePrefix("CACHE_LIMIT_MB_")
            stringResource(R.string.settings_hub_fact_cache_limit, mb)
        }
        token == "THEME_PAPER_INK" -> stringResource(R.string.settings_hub_fact_theme_paper_ink)
        token == "THEME_INDIGO" -> stringResource(R.string.settings_hub_fact_theme_indigo)
        token == "THEME_CYBER" -> stringResource(R.string.settings_hub_fact_theme_cyber)
        token == "MODE_FOLLOW_SYSTEM" -> stringResource(R.string.settings_hub_fact_mode_follow)
        token == "MODE_LIGHT" -> stringResource(R.string.settings_hub_fact_mode_light)
        token == "MODE_DARK" -> stringResource(R.string.settings_hub_fact_mode_dark)
        token.startsWith("VERSION_") -> {
            val v = token.removePrefix("VERSION_")
            stringResource(R.string.settings_hub_fact_version, v)
        }
        token == "UPDATE_IDLE" -> stringResource(R.string.settings_hub_fact_update_idle)
        token == "UPDATE_CHECKING" -> stringResource(R.string.settings_hub_fact_update_checking)
        token.startsWith("UPDATE_AVAILABLE_") -> {
            val v = token.removePrefix("UPDATE_AVAILABLE_")
            stringResource(R.string.settings_hub_fact_update_available, v)
        }
        token == "UPDATE_DOWNLOADING" -> stringResource(R.string.settings_hub_fact_update_downloading)
        token == "UPDATE_READY_TO_INSTALL" ->
            stringResource(R.string.settings_hub_fact_update_ready)
        token == "UPDATE_UP_TO_DATE" -> stringResource(R.string.settings_hub_fact_update_up_to_date)
        token == "UPDATE_ERROR" -> stringResource(R.string.settings_hub_fact_update_error)
        else -> token
    }
}
