package cc.pscly.onememos.worker

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.widget.Toast
import cc.pscly.onememos.domain.util.LocalDateTimes
import java.time.Duration
import java.time.ZonedDateTime

/**
 * Todo 外部动作中转页：
 * - 由通知 Action 触发；
 * - 用于在系统时钟 App 中创建“闹钟/计时器”（需要用户确认）；
 * - 避免在 Worker/Receiver 中直接启动外部 Activity 时的兼容性问题。
 */
class TodoExternalActionsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = intent?.getStringExtra(EXTRA_TODO_TITLE)?.trim().orEmpty()
        val dueAtLocal = intent?.getStringExtra(EXTRA_DUE_AT_LOCAL)?.trim().orEmpty()

        val plan = planClockLaunch(dueAtLocal = dueAtLocal)
        val message = buildClockMessage(title = title, dueAtLocal = dueAtLocal)

        val targetIntent =
            when (plan.kind) {
                ClockLaunchKind.SET_ALARM ->
                    Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, plan.hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, plan.minute)
                        putExtra(AlarmClock.EXTRA_MESSAGE, message)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    }

                ClockLaunchKind.SET_TIMER ->
                    Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, plan.seconds)
                        putExtra(AlarmClock.EXTRA_MESSAGE, message)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    }

                ClockLaunchKind.SHOW_ALARMS ->
                    Intent(AlarmClock.ACTION_SHOW_ALARMS)
            }

        runCatching {
            startActivity(targetIntent)
        }.onFailure { err ->
            // 极端机型可能没有匹配的时钟应用；此时回退到应用内待办页，至少不让用户点击无响应。
            if (err is ActivityNotFoundException) {
                Toast.makeText(this, "未找到系统时钟应用", Toast.LENGTH_SHORT).show()
                openTodoPageFallback()
            } else {
                Toast.makeText(this, "打开系统时钟失败", Toast.LENGTH_SHORT).show()
                openTodoPageFallback()
            }
        }

        finish()
    }

    private fun openTodoPageFallback() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launchIntent.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        launchIntent.putExtra(TodoReminderNotifyWorker.EXTRA_START_ROUTE, "todo")
        runCatching { startActivity(launchIntent) }
    }

    private fun buildClockMessage(
        title: String,
        dueAtLocal: String,
    ): String {
        val t = title.trim()
        val d = dueAtLocal.trim()
        return when {
            t.isBlank() && d.isBlank() -> "待办提醒"
            t.isBlank() -> "待办提醒（$d）"
            d.isBlank() -> "待办：$t"
            else -> "待办：$t（$d）"
        }
            // 避免某些时钟实现对 message 过长的崩溃/截断问题。
            .take(80)
    }

    internal enum class ClockLaunchKind {
        SET_ALARM,
        SET_TIMER,
        SHOW_ALARMS,
    }

    internal data class ClockLaunchPlan(
        val kind: ClockLaunchKind,
        val hour: Int = 0,
        val minute: Int = 0,
        val seconds: Int = 0,
    )

    /**
     * 将 dueAtLocal 转换为“更符合用户预期”的系统时钟动作：
     * - 今天且在未来：创建闹钟（小时+分钟）
     * - 未来但非今天：优先创建计时器（按剩余秒数），过长则退化为闹钟
     * - 解析失败：打开时钟列表
     */
    internal fun planClockLaunch(
        dueAtLocal: String,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): ClockLaunchPlan {
        val dueLocal = LocalDateTimes.parseOrNull(dueAtLocal) ?: return ClockLaunchPlan(ClockLaunchKind.SHOW_ALARMS)
        val due = dueLocal.atZone(now.zone)

        val deltaSeconds = Duration.between(now, due).seconds
        val isToday = dueLocal.toLocalDate() == now.toLocalDate()

        if (isToday && deltaSeconds > 0) {
            return ClockLaunchPlan(
                kind = ClockLaunchKind.SET_ALARM,
                hour = dueLocal.hour.coerceIn(0, 23),
                minute = dueLocal.minute.coerceIn(0, 59),
            )
        }

        if (deltaSeconds in 60..(24 * 60 * 60)) {
            return ClockLaunchPlan(
                kind = ClockLaunchKind.SET_TIMER,
                seconds = deltaSeconds.toInt().coerceIn(60, 24 * 60 * 60),
            )
        }

        // 过长/已过期：仍给一个“闹钟”入口（至少能预填时间，用户可自行调整日期）。
        return ClockLaunchPlan(
            kind = ClockLaunchKind.SET_ALARM,
            hour = dueLocal.hour.coerceIn(0, 23),
            minute = dueLocal.minute.coerceIn(0, 59),
        )
    }

    companion object {
        const val EXTRA_TODO_TITLE = "cc.pscly.onememos.extra.TODO_TITLE"
        const val EXTRA_DUE_AT_LOCAL = "cc.pscly.onememos.extra.TODO_DUE_AT_LOCAL"
    }
}

