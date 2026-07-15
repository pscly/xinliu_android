package cc.pscly.onememos.di

import android.content.Context
import android.content.Intent
import cc.pscly.onememos.externalactions.InAppFallbackPort
import cc.pscly.onememos.worker.TodoReminderLaunchIntentFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用内回退：无可信 item/owner 时只写 ACTION_OPEN_TODO，映射为 OpenTodoRoot。
 */
@Singleton
class InAppFallbackAdapter @Inject constructor() : InAppFallbackPort {
    override fun todoIntent(context: Context): Intent =
        TodoReminderLaunchIntentFactory.createOpenTodoIntent(context)
}
