package cc.pscly.onememos.settings

import cc.pscly.onememos.domain.model.AppSettings
import cc.pscly.onememos.domain.model.FullSyncStatus
import cc.pscly.onememos.domain.model.GlobalSyncState
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.SyncWorkState
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import cc.pscly.onememos.domain.model.TodoReminderMode
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.settings.SectionSummaryState
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.domain.settings.SettingsHubCapability
import cc.pscly.onememos.domain.settings.SettingsHubSnapshot
import cc.pscly.onememos.domain.settings.SummaryFact
import cc.pscly.onememos.domain.settings.SummaryIssue
import cc.pscly.onememos.domain.settings.SummaryIssueKind
import cc.pscly.onememos.domain.sync.SyncStatusMonitor
import cc.pscly.onememos.update.AppIdentityPort
import cc.pscly.onememos.update.AppUpdateManager
import cc.pscly.onememos.update.AppUpdatePhase
import cc.pscly.onememos.update.AppUpdateUiState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * 设置首页只读摘要：仅组合本地设置、已有同步观察流与已缓存更新状态。
 * 不发起网络检查、同步、扫描、权限、导出或任何 execute/refresh。
 */
@Singleton
class SettingsHubCapabilityImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val syncStatusMonitor: SyncStatusMonitor,
    private val appUpdateManager: AppUpdateManager,
    private val appIdentity: AppIdentityPort,
) : SettingsHubCapability {
    override fun observe(): Flow<SettingsHubSnapshot> {
        val left =
            combine(
                accountSection(),
                recordSection(),
                reminderSection(),
                storageSection(),
                appearanceSection(),
            ) { account, record, reminder, storage, appearance ->
                HubLeft(
                    account = account,
                    record = record,
                    reminder = reminder,
                    storage = storage,
                    appearance = appearance,
                )
            }
        return combine(left, aboutSection()) { hubLeft, about ->
            SettingsHubSnapshot(
                accountSync = hubLeft.account,
                recordEditing = hubLeft.record,
                reminderCalendar = hubLeft.reminder,
                storageOffline = hubLeft.storage,
                appearanceInteraction = hubLeft.appearance,
                aboutAdvanced = about,
            )
        }
    }

    private data class HubLeft(
        val account: SectionSummaryState,
        val record: SectionSummaryState,
        val reminder: SectionSummaryState,
        val storage: SectionSummaryState,
        val appearance: SectionSummaryState,
    )

    private fun accountSection(): Flow<SectionSummaryState> =
        combine(settingsRepository.settings, syncStatusMonitor.globalState) { settings, global ->
            mapAccount(settings, global)
        }
            .catch { emit(SectionSummaryState.Error(SettingsCapabilityError.Unknown("ACCOUNT_SUMMARY"))) }
            .onStart { emit(SectionSummaryState.Loading) }

    private fun recordSection(): Flow<SectionSummaryState> =
        settingsRepository.settings
            .map { mapRecord(it) }
            .catch { emit(SectionSummaryState.Error(SettingsCapabilityError.Unknown("RECORD_SUMMARY"))) }
            .onStart { emit(SectionSummaryState.Loading) }

    private fun reminderSection(): Flow<SectionSummaryState> =
        settingsRepository.settings
            .map { mapReminder(it) }
            .catch { emit(SectionSummaryState.Error(SettingsCapabilityError.Unknown("REMINDER_SUMMARY"))) }
            .onStart { emit(SectionSummaryState.Loading) }

    private fun storageSection(): Flow<SectionSummaryState> =
        settingsRepository.settings
            .map { mapStorage(it) }
            .catch { emit(SectionSummaryState.Error(SettingsCapabilityError.Unknown("STORAGE_SUMMARY"))) }
            .onStart { emit(SectionSummaryState.Loading) }

    private fun appearanceSection(): Flow<SectionSummaryState> =
        settingsRepository.settings
            .map { mapAppearance(it) }
            .catch { emit(SectionSummaryState.Error(SettingsCapabilityError.Unknown("APPEARANCE_SUMMARY"))) }
            .onStart { emit(SectionSummaryState.Loading) }

    private fun aboutSection(): Flow<SectionSummaryState> =
        appUpdateManager.uiState
            .map { mapAbout(it) }
            .catch { emit(SectionSummaryState.Error(SettingsCapabilityError.Unknown("ABOUT_SUMMARY"))) }
            .onStart { emit(SectionSummaryState.Loading) }

    private fun mapAccount(
        settings: AppSettings,
        global: GlobalSyncState,
    ): SectionSummaryState {
        val hasConfig = settings.serverUrl.isNotBlank()
        val signedIn = settings.token.isNotBlank()
        val lastSuccess =
            when {
                global.lastSuccessAt > 0L -> global.lastSuccessAt
                settings.lastSync.lastSuccessAt > 0L -> settings.lastSync.lastSuccessAt
                else -> null
            }

        return when {
            !hasConfig ->
                ready("ACCOUNT_UNBOUND")
            !signedIn ->
                ready("ACCOUNT_CONFIGURED_SIGNED_OUT")
            global.authInvalid ->
                ready(
                    primary = "ACCOUNT_AUTH_EXPIRED",
                    secondary = lastSuccessFact(lastSuccess),
                    issue = SummaryIssue(SummaryIssueKind.AUTHENTICATION_EXPIRED),
                )
            settings.fullSync.status == FullSyncStatus.FAILED ->
                ready(
                    primary = "ACCOUNT_FULL_RESYNC_FAILED",
                    secondary = lastSuccessFact(lastSuccess),
                    issue = SummaryIssue(SummaryIssueKind.FULL_RESYNC_FAILED),
                )
            global.hasError ->
                ready(
                    primary = "ACCOUNT_LAST_SYNC_FAILED",
                    secondary = lastSuccessFact(lastSuccess),
                    issue = SummaryIssue(SummaryIssueKind.LAST_SYNC_FAILED),
                )
            settings.fullSync.status == FullSyncStatus.RUNNING ->
                ready(
                    primary = "ACCOUNT_FULL_RESYNC_RUNNING",
                    secondary = lastSuccessFact(lastSuccess),
                )
            global.workState == SyncWorkState.RUNNING ->
                ready(
                    primary = "ACCOUNT_SYNCING",
                    secondary = lastSuccessFact(lastSuccess),
                )
            global.workState == SyncWorkState.ENQUEUED ->
                ready(
                    primary = "ACCOUNT_QUEUED",
                    secondary = lastSuccessFact(lastSuccess),
                )
            else ->
                ready(
                    primary = "ACCOUNT_HEALTHY",
                    secondary = lastSuccessFact(lastSuccess),
                )
        }
    }

    private fun mapRecord(settings: AppSettings): SectionSummaryState {
        val visibility =
            when (settings.defaultVisibility) {
                MemoVisibility.PRIVATE -> "VISIBILITY_PRIVATE"
                MemoVisibility.PROTECTED -> "VISIBILITY_PROTECTED"
                MemoVisibility.PUBLIC -> "VISIBILITY_PUBLIC"
            }
        val secondary =
            buildList {
                if (settings.regexSearchEnabled) add("REGEX_ON")
                if (settings.quickInsertTimeEnabled) add("QUICK_INSERT_ON")
                if (settings.showTagCountsInFilter) add("TAG_COUNTS_ON")
            }.firstOrNull()
        return ready(visibility, secondary?.let { SummaryFact(it) })
    }

    private fun mapReminder(settings: AppSettings): SectionSummaryState {
        val mode =
            when (settings.todoReminderMode) {
                TodoReminderMode.SMART -> "REMINDER_SMART"
                TodoReminderMode.EXACT -> "REMINDER_EXACT"
            }
        val calendarFact =
            when {
                !settings.calendarIntegrationEnabled -> "CALENDAR_OFF"
                settings.calendarIntegrationCalendarId != null ->
                    "CALENDAR_CONNECTED"
                else -> "CALENDAR_ENABLED_NO_TARGET"
            }
        val issue =
            if (
                settings.calendarIntegrationEnabled &&
                settings.calendarIntegrationCalendarId == null
            ) {
                SummaryIssue(SummaryIssueKind.PERMISSION_REQUIRED)
            } else {
                null
            }
        return ready(mode, SummaryFact(calendarFact), issue)
    }

    private fun mapStorage(settings: AppSettings): SectionSummaryState {
        val prefetch =
            if (settings.offlineImagePrefetchEnabled) {
                "PREFETCH_ON"
            } else {
                "PREFETCH_OFF"
            }
        return ready(
            primary = prefetch,
            secondary = SummaryFact("CACHE_LIMIT_MB_${settings.attachmentCacheMaxMb}"),
        )
    }

    private fun mapAppearance(settings: AppSettings): SectionSummaryState {
        val palette =
            when (settings.themePalette) {
                ThemePalette.PAPER_INK -> "THEME_PAPER_INK"
                ThemePalette.INDIGO -> "THEME_INDIGO"
                ThemePalette.CYBER -> "THEME_CYBER"
                ThemePalette.MOON_WHITE -> "THEME_MOON_WHITE"
                ThemePalette.DYNAMIC -> "THEME_DYNAMIC"
            }
        val mode =
            when (settings.themeMode) {
                ThemeMode.FOLLOW_SYSTEM -> "MODE_FOLLOW_SYSTEM"
                ThemeMode.LIGHT -> "MODE_LIGHT"
                ThemeMode.DARK -> "MODE_DARK"
            }
        return ready(palette, SummaryFact(mode))
    }

    private fun mapAbout(update: AppUpdateUiState): SectionSummaryState {
        val version = SummaryFact("VERSION_${appIdentity.versionName}")
        val phaseFact =
            when (update.phase) {
                AppUpdatePhase.IDLE -> "UPDATE_IDLE"
                AppUpdatePhase.CHECKING -> "UPDATE_CHECKING"
                AppUpdatePhase.AVAILABLE ->
                    "UPDATE_AVAILABLE_${update.release?.versionName.orEmpty()}"
                AppUpdatePhase.DOWNLOADING -> "UPDATE_DOWNLOADING"
                AppUpdatePhase.READY_TO_INSTALL -> "UPDATE_READY_TO_INSTALL"
                AppUpdatePhase.UP_TO_DATE -> "UPDATE_UP_TO_DATE"
                AppUpdatePhase.ERROR -> "UPDATE_ERROR"
            }
        val issue =
            if (update.phase == AppUpdatePhase.ERROR) {
                SummaryIssue(SummaryIssueKind.UPDATE_FAILURE)
            } else {
                null
            }
        return ready(version.value, SummaryFact(phaseFact), issue)
    }

    private fun lastSuccessFact(epochMs: Long?): SummaryFact? =
        epochMs?.takeIf { it > 0L }?.let { SummaryFact("LAST_SUCCESS_AT_$it") }

    private fun ready(
        primary: String,
        secondary: SummaryFact? = null,
        issue: SummaryIssue? = null,
    ): SectionSummaryState.Ready =
        SectionSummaryState.Ready(
            primary = SummaryFact(primary),
            secondary = secondary,
            issue = issue,
        )
}
