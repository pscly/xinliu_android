package cc.pscly.onememos.domain.sync

/**
 * 触发一次“尽快同步”（上传本地待同步记录，并刷新服务端数据）。
 * 具体实现可能是 WorkManager，也可能是前台服务等。
 */
interface SyncScheduler {
    fun requestSync()

    /**
     * 请求一次全量重同步。
     *
     * 返回 [FullResyncScheduleResult]：
     * - [FullResyncScheduleResult.Accepted]：请求已交接给 WorkManager，且按 requestId 核验成功。
     * - [FullResyncScheduleResult.Duplicate]：已有未结束的全量同步任务。
     * - [FullResyncScheduleResult.Busy]：已有普通同步任务进行中，无法立即起全量。
     */
    suspend fun requestFullResync(): FullResyncScheduleResult
}
