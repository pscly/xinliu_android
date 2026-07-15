package cc.pscly.onememos.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cc.pscly.onememos.core.database.dao.TodoDao
import cc.pscly.onememos.data.auth.FlowBackendCredentialStorage
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.model.TodoStatuses
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.util.Hashing
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Todo 单次提醒通知 Worker：到点后展示一条系统通知。
 *
 * 注意：
 * - minSdk=33：通知权限为 POST_NOTIFICATIONS，必须在运行时授权。
 * - 此处不会做“精确闹钟”（AlarmManager）模式；依赖 WorkManager 的调度可能被省电策略延迟。
 */
@HiltWorker
class TodoReminderNotifyWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val todoDao: TodoDao,
    private val settingsRepository: SettingsRepository,
    private val flowBackendCredentialStorage: FlowBackendCredentialStorage,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val hasPermission =
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return Result.success()

        val settings = settingsRepository.settings.first()
        if (settings.loginMode != LoginMode.BACKEND) return Result.success()
        if (settings.token.trim().isBlank()) return Result.success()

        val expectedOwnerKey = inputData.getString(KEY_OWNER_KEY)?.trim().orEmpty()
        if (expectedOwnerKey.isBlank()) return Result.success()

        val ownerKey = currentOwnerKeyOrNull() ?: return Result.success()
        if (ownerKey != expectedOwnerKey) return Result.success()

        val itemId = inputData.getString(KEY_ITEM_ID)?.trim().orEmpty()
        if (itemId.isBlank()) return Result.success()

        // 读最新数据，避免“提醒到点但任务已完成/已删除”的误通知。
        val item = todoDao.getItem(ownerKey, itemId) ?: return Result.success()
        if (!item.deletedAt.isNullOrBlank()) return Result.success()

        if (!item.isRecurring) {
            val done = item.status == TodoStatuses.DONE || !item.completedAtLocal.isNullOrBlank()
            if (done) return Result.success()
        }

        val minutes = inputData.getInt(KEY_BEFORE_MINUTES, 0).coerceAtLeast(0)
        val dueAtLocal = inputData.getString(KEY_DUE_AT_LOCAL)?.trim().orEmpty()

        val contentText =
            when (minutes) {
                0 -> "到期：$dueAtLocal"
                else -> "提前 $minutes 分钟：$dueAtLocal"
            }

        ensureChannel()

        val intent =
            TodoReminderLaunchIntentFactory.createOpenTodoIntent(
                context = applicationContext,
                itemId = itemId,
                ownerKey = ownerKey,
            )

        val requestCode = "$ownerKey|$itemId".hashCode()
        val pendingIntent =
            PendingIntent.getActivity(
                applicationContext,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val clockActionIntent =
            Intent(applicationContext, TodoExternalActionsActivity::class.java).apply {
                putExtra(TodoExternalActionsActivity.EXTRA_TODO_TITLE, item.title)
                putExtra(TodoExternalActionsActivity.EXTRA_DUE_AT_LOCAL, dueAtLocal)
            }
        val clockActionRequestCode = "todo_clock:$itemId:$minutes:$dueAtLocal".hashCode()
        val clockActionPendingIntent =
            PendingIntent.getActivity(
                applicationContext,
                clockActionRequestCode,
                clockActionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notificationId = stableNotificationId(itemId = itemId, minutes = minutes, dueAtLocal = dueAtLocal)
        val iconRes = applicationContext.applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.ic_dialog_info
        val notification =
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(iconRes)
                .setContentTitle("待办提醒：${item.title}")
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .addAction(
                    android.R.drawable.ic_lock_idle_alarm,
                    "设为系统闹钟",
                    clockActionPendingIntent,
                )
                .build()

        NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
        return Result.success()
    }

    private fun ensureChannel() {
        val nm = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "待办提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "待办事项到期前的提醒通知"
            }
        nm.createNotificationChannel(channel)
    }

    private fun currentOwnerKeyOrNull(): String? {
        val cred = flowBackendCredentialStorage.get() ?: return null
        val username = cred.username.trim()
        if (username.isBlank()) return null
        return Hashing.sha256Hex(username.lowercase())
    }

    private fun stableNotificationId(
        itemId: String,
        minutes: Int,
        dueAtLocal: String,
    ): Int {
        // 避免 Android 13+ 对 notificationId 的限制：保证为 Int 且稳定（同一条提醒只覆盖自身）。
        val seed = "$itemId|$minutes|$dueAtLocal"
        return seed.hashCode()
    }

    companion object {
        const val TAG = "todo_reminder_notify"
        const val CHANNEL_ID = "todo_reminders"

        @Deprecated("迁移期兼容常量；Task 12 后生产者改用 ACTION_OPEN_TODO")
        const val EXTRA_START_ROUTE = "cc.pscly.onememos.extra.START_ROUTE"

        const val KEY_ITEM_ID = "itemId"
        const val KEY_OWNER_KEY = "ownerKey"
        const val KEY_DUE_AT_LOCAL = "dueAtLocal"
        const val KEY_BEFORE_MINUTES = "beforeMinutes"

        fun uniqueWorkName(
            ownerKey: String,
            itemId: String,
            triggerAtMs: Long,
            minutes: Int,
        ): String = "todo_reminder_notify:${ownerKey.trim()}:$itemId:$triggerAtMs:$minutes"
    }
}
