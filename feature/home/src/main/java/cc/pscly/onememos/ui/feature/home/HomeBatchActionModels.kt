package cc.pscly.onememos.ui.feature.home

enum class HomeBatchAction {
    Archive,
    Unarchive,
}

data class HomeBatchActionSummary(
    val action: HomeBatchAction,
    val successCount: Int,
    val failedCount: Int,
)

sealed interface HomeEvent {
    data class BatchActionFinished(val summary: HomeBatchActionSummary) : HomeEvent
    data class ShareTextReady(val text: String) : HomeEvent
}
