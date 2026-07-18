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

    /** 滑动归档完成：UI 据此弹 Snackbar（带“撤销”）。 */
    data class SwipeArchived(val uuid: String) : HomeEvent

    /** 滑动动作（待办/收藏/置顶等）的轻量反馈：UI 以 Toast 展示。 */
    data class SwipeActionMessage(val text: String) : HomeEvent
}
