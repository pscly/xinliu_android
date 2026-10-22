package cc.pscly.onememos.domain.sync

/**
 * Todo 同步调度器：触发一次尽快同步（push outbox + pull changes）。
 */
interface TodoSyncScheduler {
    fun requestSync()

    /**
     * 手动重试：忽略退避，立即发起一次同步尝试。
     * 默认回退到 [requestSync]，方便既有测试 Fake 无需改动。
     */
    fun requestImmediateSync() = requestSync()
}

