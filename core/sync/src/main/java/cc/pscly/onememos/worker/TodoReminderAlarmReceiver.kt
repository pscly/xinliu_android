package cc.pscly.onememos.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cc.pscly.onememos.core.database.dao.TodoDao
import cc.pscly.onememos.data.auth.FlowBackendCredentialStorage
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.model.TodoStatuses
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.util.Hashing
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Todo 准点提醒（EXACT）接收器：
 * - 由 AlarmManager 触发；
 * - 触发后尽快展示系统通知；
 * - 为避免跨账号串台，会校验 intent 携带的 ownerKey 与当前账号一致。
 */
@AndroidEntryPoint
class TodoReminderAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var todoDao: TodoDao
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var flowBackendCredentialStorage: FlowBackendCredentialStorage

    override fun onReceive(context: Context, intent: Intent?) {
        val safeIntent = intent ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                TodoReminderAlarmReceiver.runWithTimeoutAndLog(timeoutMs = RECEIVER_TIMEOUT_MS) {
                    handleReceive(appContext = context.applicationContext, intent = safeIntent)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleReceive(
        appContext: Context,
        intent: Intent,
    ) {
        val hasPermission =
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        val expectedOwnerKey = intent.getStringExtra(EXTRA_OWNER_KEY)?.trim().orEmpty()
        if (expectedOwnerKey.isBlank()) return

        val settings = settingsRepository.settings.first()
        if (settings.loginMode != LoginMode.BACKEND) return
        if (settings.token.trim().isBlank()) return

        val ownerKey = currentOwnerKeyOrNull() ?: return
        if (ownerKey != expectedOwnerKey) return

        val itemId = intent.getStringExtra(TodoReminderNotifyWorker.KEY_ITEM_ID)?.trim().orEmpty()
        if (itemId.isBlank()) return

        val minutes = intent.getIntExtra(TodoReminderNotifyWorker.KEY_BEFORE_MINUTES, 0).coerceAtLeast(0)
        val dueAtLocal = intent.getStringExtra(TodoReminderNotifyWorker.KEY_DUE_AT_LOCAL)?.trim().orEmpty()

        // 读最新数据，避免“提醒到点但任务已完成/已删除”的误通知。
        val item = todoDao.getItem(ownerKey, itemId) ?: return
        if (!item.deletedAt.isNullOrBlank()) return

        if (!item.isRecurring) {
            val done = item.status == TodoStatuses.DONE || !item.completedAtLocal.isNullOrBlank()
            if (done) return
        }

        val contentText =
            when (minutes) {
                0 -> "到期：$dueAtLocal"
                else -> "提前 $minutes 分钟：$dueAtLocal"
            }

        ensureChannel(appContext)

        val launchIntent =
            appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)?.apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(TodoReminderNotifyWorker.EXTRA_START_ROUTE, "todo")
            }
                ?: return

        val requestCode = itemId.hashCode()
        val pendingIntent =
            PendingIntent.getActivity(
                appContext,
                requestCode,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val clockActionIntent =
            Intent(appContext, TodoExternalActionsActivity::class.java).apply {
                putExtra(TodoExternalActionsActivity.EXTRA_TODO_TITLE, item.title)
                putExtra(TodoExternalActionsActivity.EXTRA_DUE_AT_LOCAL, dueAtLocal)
            }
        val clockActionRequestCode = "todo_clock:$itemId:$minutes:$dueAtLocal".hashCode()
        val clockActionPendingIntent =
            PendingIntent.getActivity(
                appContext,
                clockActionRequestCode,
                clockActionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notificationId = stableNotificationId(itemId = itemId, minutes = minutes, dueAtLocal = dueAtLocal)
        val iconRes = appContext.applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.ic_dialog_info
        val notification =
            NotificationCompat.Builder(appContext, TodoReminderNotifyWorker.CHANNEL_ID)
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

        NotificationManagerCompat.from(appContext).notify(notificationId, notification)
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channel =
            NotificationChannel(
                TodoReminderNotifyWorker.CHANNEL_ID,
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
        val seed = "$itemId|$minutes|$dueAtLocal"
        return seed.hashCode()
    }

    companion object {
        private const val TAG = "todo_reminder_alarm_receiver"

        @VisibleForTesting
        internal suspend fun runWithTimeoutAndLog(
            timeoutMs: Long,
            block: suspend () -> Unit,
        ) {
            try {
                withTimeout(timeoutMs) {
                    block()
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "TodoReminderAlarmReceiver 处理超时：timeoutMs=$timeoutMs", e)
            } catch (e: CancellationException) {
                Log.w(TAG, "TodoReminderAlarmReceiver 协程被取消", e)
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "TodoReminderAlarmReceiver 处理异常", t)
            }
        }

        const val ACTION_NOTIFY = "cc.pscly.onememos.action.TODO_REMINDER_NOTIFY"
        const val EXTRA_OWNER_KEY = "cc.pscly.onememos.extra.TODO_OWNER_KEY"

        private const val RECEIVER_TIMEOUT_MS = 10_000L
    }
}
