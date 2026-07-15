package cc.pscly.onememos.domain.settings

import kotlinx.coroutines.flow.Flow

/**
 * 设置首页只读能力：只观察六行摘要，不承载写操作。
 */
interface SettingsHubCapability {
    fun observe(): Flow<SettingsHubSnapshot>
}

data class SettingsHubSnapshot(
    val accountSync: SectionSummaryState,
    val recordEditing: SectionSummaryState,
    val reminderCalendar: SectionSummaryState,
    val storageOffline: SectionSummaryState,
    val appearanceInteraction: SectionSummaryState,
    val aboutAdvanced: SectionSummaryState,
)

sealed interface SectionSummaryState {
    data object Loading : SectionSummaryState

    data class Ready(
        val primary: SummaryFact,
        val secondary: SummaryFact? = null,
        val issue: SummaryIssue? = null,
    ) : SectionSummaryState

    data class Error(val error: SettingsCapabilityError) : SectionSummaryState
}

data class SummaryFact(val value: String)

data class SummaryIssue(val kind: SummaryIssueKind)

enum class SummaryIssueKind {
    AUTHENTICATION_EXPIRED,
    LAST_SYNC_FAILED,
    FULL_RESYNC_FAILED,
    PERMISSION_REQUIRED,
    STORAGE_FAILURE,
    UPDATE_FAILURE,
    DIAGNOSTICS_FAILURE,
    PREFERENCE_READ_FAILURE,
}
