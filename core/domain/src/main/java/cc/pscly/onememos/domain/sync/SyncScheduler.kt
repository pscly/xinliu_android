package cc.pscly.onememos.domain.sync

/**
 * 触发一次“尽快同步”（上传本地待同步记录，并刷新服务端数据）。
 * 具体实现可能是 WorkManager，也可能是前台服务等。
 */
interface SyncScheduler {
    fun requestSync()

    /**
     * 用于用户手动“重新同步所有笔记”。
     * 通常会以 REPLACE 方式覆盖已有任务，并携带 forceFull 标记以触发 full sync。
     *
     * 注意：默认实现会回退到 [requestSync]，具体 full sync 行为由实现类覆盖。
     */
    fun requestFullResync() {
        requestSync()
    }
}
