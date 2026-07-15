package cc.pscly.onememos.worker

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import cc.pscly.onememos.domain.util.LocalDateTimes
import cc.pscly.onememos.externalactions.InAppFallbackPort
import dagger.hilt.android.AndroidEntryPoint
import java.time.Duration
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * Todo 外部动作中转页：
 * - 由通知 Action 触发；
 * - 用于在系统时钟 App 中创建“闹钟/计时器”（需要用户确认）；
 * - 避免在 Worker/Receiver 中直接启动外部 Activity 时的兼容性问题。
 */

/**
 * 时钟启动决策纯函数，便于单元测试，不依赖 Hilt/Activity 生命周期。
 */
object TodoClockLaunchPlanner {
    enum class Kind {
        SET_ALARM,
        SET_TIMER,
        SHOW_ALARMS,
    }

    data class Plan(
        val kind: Kind,
        val hour: Int = 0,
        val minute: Int = 0,
        val seconds: Int = 0,
    )

    fun plan(
        dueAtLocal: String,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): Plan {
        val dueLocal = LocalDateTimes.parseOrNull(dueAtLocal) ?: return Plan(Kind.SHOW_ALARMS)
        val due = dueLocal.atZone(now.zone)
        val deltaSeconds = Duration.between(now, due).seconds
        val isToday = dueLocal.toLocalDate() == now.toLocalDate()
        if (isToday && deltaSeconds > 0) {
            return Plan(
                kind = Kind.SET_ALARM,
                hour = dueLocal.hour.coerceIn(0, 23),
                minute = dueLocal.minute.coerceIn(0, 59),
            )
        }
        if (deltaSeconds in 60..(24 * 60 * 60)) {
            return Plan(
                kind = Kind.SET_TIMER,
                seconds = deltaSeconds.toInt().coerceIn(60, 24 * 60 * 60),
            )
        }
        return Plan(
            kind = Kind.SET_ALARM,
            hour = dueLocal.hour.coerceIn(0, 23),
            minute = dueLocal.minute.coerceIn(0, 59),
        )
    }
}

@AndroidEntryPoint
class TodoExternalActionsActivity : ComponentActivity() {
    @Inject
    lateinit var inAppFallbackPort: InAppFallbackPort

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = intent?.getStringExtra(EXTRA_TODO_TITLE)?.trim().orEmpty()
        val dueAtLocal = intent?.getStringExtra(EXTRA_DUE_AT_LOCAL)?.trim().orEmpty()

        val plan = TodoClockLaunchPlanner.plan(dueAtLocal = dueAtLocal)
        val message = buildClockMessage(title = title, dueAtLocal = dueAtLocal)

        val targetIntent =
            when (plan.kind) {
                TodoClockLaunchPlanner.Kind.SET_ALARM ->
                    Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, plan.hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, plan.minute)
                        putExtra(AlarmClock.EXTRA_MESSAGE, message)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    }

                TodoClockLaunchPlanner.Kind.SET_TIMER ->
                    Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, plan.seconds)
                        putExtra(AlarmClock.EXTRA_MESSAGE, message)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    }

                TodoClockLaunchPlanner.Kind.SHOW_ALARMS ->
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
        val launchIntent = inAppFallbackPort.todoIntent(this)
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

    companion object {
        const val EXTRA_TODO_TITLE = "cc.pscly.onememos.extra.TODO_TITLE"
        const val EXTRA_DUE_AT_LOCAL = "cc.pscly.onememos.extra.TODO_DUE_AT_LOCAL"
    }
}
