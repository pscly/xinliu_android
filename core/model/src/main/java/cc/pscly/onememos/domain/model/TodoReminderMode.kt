package cc.pscly.onememos.domain.model

/**
 * Todo 提醒模式（双模式）：
 *
 * - SMART：默认模式，基于 WorkManager 延时任务。实现简单、无需精确闹钟权限，但可能被省电策略延迟。
 * - EXACT：准点模式，基于 AlarmManager 精确闹钟。更接近“准点”，但需要系统允许精确闹钟。
 */
enum class TodoReminderMode {
    SMART,
    EXACT,
}

