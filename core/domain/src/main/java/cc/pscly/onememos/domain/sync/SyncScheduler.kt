package cc.pscly.onememos.domain.sync

/**
 * 触发一次“尽快同步”（上传本地待同步记录，并刷新服务端数据）。
 * 具体实现可能是 WorkManager，也可能是前台服务等。
 */
interface SyncScheduler {
    fun requestSync()

    /**
     * 手动重试：**忽略退避/排队**，立即发起一次同步尝试。
     *
     * 与 [requestSync]（自动触发，遵守 KEEP/退避策略）的区别：
     * 用户点“重试 / 立即同步”时，若旧任务正处于 RETRY 退避期，KEEP 策略会让点击“没反应”；
     * 实现方应替换掉待执行/重试中的旧任务再入队。
     *
     * 默认回退到 [requestSync]，方便既有测试 Fake 无需改动。
     */
    fun requestImmediateSync() = requestSync()

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
