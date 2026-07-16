package cc.pscly.onememos.ui.feature.settings

import cc.pscly.onememos.domain.sync.FullResyncScheduleResult
import kotlinx.coroutines.CancellationException

sealed interface FullResyncFeedback {
    data class Accepted(val requestId: String) : FullResyncFeedback

    data object Duplicate : FullResyncFeedback

    data object Busy : FullResyncFeedback

    data object Failure : FullResyncFeedback
}

suspend fun requestFullResyncFeedback(
    request: suspend () -> FullResyncScheduleResult,
): FullResyncFeedback =
    try {
        when (val result = request()) {
            is FullResyncScheduleResult.Accepted -> FullResyncFeedback.Accepted(result.requestId)
            FullResyncScheduleResult.Duplicate -> FullResyncFeedback.Duplicate
            FullResyncScheduleResult.Busy -> FullResyncFeedback.Busy
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        FullResyncFeedback.Failure
    }

fun fullResyncToastMessage(feedback: FullResyncFeedback): String =
    when (feedback) {
        is FullResyncFeedback.Accepted -> "已开始后台重同步"
        FullResyncFeedback.Duplicate -> "已有全量重同步任务正在进行"
        FullResyncFeedback.Busy -> "当前有同步任务正在进行，请稍后重试"
        FullResyncFeedback.Failure -> "启动全量重同步失败，请稍后重试"
    }
