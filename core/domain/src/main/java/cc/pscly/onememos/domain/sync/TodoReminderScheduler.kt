package cc.pscly.onememos.domain.sync

/**
 * Todo 提醒调度器：
 * - 负责把本地 TodoItem.remindersJson + dueAtLocal/rrule 映射为系统通知提醒。
 * - 具体实现通常基于 WorkManager（更省心、无需精确闹钟权限，但可能被省电策略延迟）。
 */
interface TodoReminderScheduler {
    /**
     * 触发一次“尽快重算并重排提醒”。
     *
     * 典型调用场景：
     * - 用户新增/编辑/删除任务或提醒
     * - 同步完成后本地数据被服务端更新
     * - 用户刚授予通知权限（POST_NOTIFICATIONS）
     */
    fun requestReschedule()
}

