package cc.pscly.onememos.domain.sync

/**
 * Todo “测试提醒”调度器：
 * - 用于快速验证：通知权限/通知通道/后台调度链路是否正常
 * - 不参与正式提醒逻辑（正式提醒仍由 TodoReminderScheduler + RescheduleWorker 扫描排程）
 */
interface TodoReminderTestScheduler {
    /**
     * 请求一次测试提醒（默认 10 秒后）。
     *
     * @param itemId 用于读取最新任务信息（标题等），确保通知内容与数据一致。
     * @param dueAtLocal 仅用于通知内容展示（不会被解析为触发时间）。
     * @param delaySeconds 延迟秒数，默认 10 秒。
     */
    fun requestTest(
        itemId: String,
        dueAtLocal: String,
        delaySeconds: Long = 10,
    )
}

