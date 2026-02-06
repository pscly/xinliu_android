package cc.pscly.onememos.domain.sync

/**
 * Todo 同步调度器：触发一次尽快同步（push outbox + pull changes）。
 */
interface TodoSyncScheduler {
    fun requestSync()
}

