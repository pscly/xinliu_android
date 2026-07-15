package cc.pscly.onememos.worker

import android.content.Context
import android.content.Intent
import android.net.Uri
import cc.pscly.onememos.navigation.TodoNavigationIntentContract

/**
 * Todo 提醒与应用内回退共用的启动 Intent 工厂。
 * - 带 item/owner：完整通知深链
 * - 无 item：仅 ACTION_OPEN_TODO，映射为 OpenTodoRoot
 */
object TodoReminderLaunchIntentFactory {
    fun createOpenTodoIntent(
        context: Context,
        itemId: String? = null,
        ownerKey: String? = null,
    ): Intent {
        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent().setPackage(context.packageName)

        launchIntent.action = TodoNavigationIntentContract.ACTION_OPEN_TODO
        launchIntent.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP

        val id = itemId?.trim().orEmpty()
        val owner = ownerKey?.trim().orEmpty()
        if (id.isNotEmpty() && owner.isNotEmpty()) {
            launchIntent.putExtra(TodoNavigationIntentContract.EXTRA_TODO_ITEM_ID, id)
            launchIntent.putExtra(TodoNavigationIntentContract.EXTRA_TODO_OWNER_KEY, owner)
            launchIntent.data =
                Uri.Builder()
                    .scheme("onememos")
                    .authority("todo")
                    .appendPath(owner)
                    .appendPath(id)
                    .build()
        } else {
            // 无可信 item/owner：只写 action，不写两个 extra。
            launchIntent.data = null
        }
        return launchIntent
    }
}
