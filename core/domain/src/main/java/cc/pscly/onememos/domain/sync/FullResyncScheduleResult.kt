package cc.pscly.onememos.domain.sync

sealed interface FullResyncScheduleResult {
    data class Accepted(val requestId: String) : FullResyncScheduleResult

    data object Duplicate : FullResyncScheduleResult

    data object Busy : FullResyncScheduleResult
}
