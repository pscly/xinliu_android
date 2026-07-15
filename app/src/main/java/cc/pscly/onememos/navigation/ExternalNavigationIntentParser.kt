package cc.pscly.onememos.navigation

import android.content.Intent
import cc.pscly.onememos.MainActivity

/**
 * 把平台 Intent 解析为受控 [ExternalNavigationInput]。
 * 未知/空输入返回 null，由调用方记录诊断且不改导航栈。
 */
object ExternalNavigationIntentParser {
    fun parse(intent: Intent?): ExternalNavigationInput? {
        if (intent == null) return null

        val editorUuid = intent.getStringExtra(MainActivity.EXTRA_START_EDITOR_UUID)
        if (editorUuid != null) {
            // 分享与旧 editor extra：保留原字符串交给 mapper 做空白校验。
            return ExternalNavigationInput.LegacyEditorExtra(uuid = editorUuid)
        }

        if (intent.action == TodoNavigationIntentContract.ACTION_OPEN_TODO) {
            val hasItem = intent.hasExtra(TodoNavigationIntentContract.EXTRA_TODO_ITEM_ID)
            val hasOwner = intent.hasExtra(TodoNavigationIntentContract.EXTRA_TODO_OWNER_KEY)
            return when {
                !hasItem && !hasOwner -> ExternalNavigationInput.OpenTodoRoot
                else ->
                    ExternalNavigationInput.TodoNotification(
                        itemId = intent.getStringExtra(TodoNavigationIntentContract.EXTRA_TODO_ITEM_ID).orEmpty(),
                        expectedOwnerKey =
                            intent.getStringExtra(TodoNavigationIntentContract.EXTRA_TODO_OWNER_KEY).orEmpty(),
                    )
            }
        }

        val startRoute = intent.getStringExtra(MainActivity.EXTRA_START_ROUTE)
        if (startRoute != null) {
            // 迁移期兼容：START_ROUTE=todo；其他值交给 mapper 拒绝。
            return ExternalNavigationInput.LegacyRouteExtra(value = startRoute)
        }

        return null
    }
}
