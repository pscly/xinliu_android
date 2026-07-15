package cc.pscly.onememos.di

import android.content.Context
import android.content.Intent
import cc.pscly.onememos.MainActivity
import cc.pscly.onememos.externalactions.InAppFallbackPort
import cc.pscly.onememos.worker.TodoReminderNotifyWorker
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 迁移期回退：等价于旧 START_ROUTE=todo 启动语义。
 * Task 12 会在 Navigation 3 白名单就绪后切换为固定 action OPEN_TODO。
 */
@Singleton
class InAppFallbackAdapter @Inject constructor() : InAppFallbackPort {
    override fun todoIntent(context: Context): Intent {
        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent(context, MainActivity::class.java)
        launchIntent.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        launchIntent.putExtra(TodoReminderNotifyWorker.EXTRA_START_ROUTE, "todo")
        return launchIntent
    }
}
