package cc.pscly.onememos.worker

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import cc.pscly.onememos.core.database.dao.TodoDao
import cc.pscly.onememos.core.database.entity.TodoItemEntity
import cc.pscly.onememos.data.auth.FlowBackendCredentialStorage
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.model.TodoReminderMode
import cc.pscly.onememos.domain.model.TodoStatuses
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.todo.TodoRecurrenceCalculator
import cc.pscly.onememos.domain.util.Hashing
import cc.pscly.onememos.domain.util.LocalDateTimes
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId
import java.util.concurrent.TimeUnit

private val Context.todoReminderAlarmDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "todo_reminder_alarm_state",
)

private object TodoReminderAlarmStateKeys {
    val SCHEDULED_ALARM_KEYS = stringSetPreferencesKey("todo_reminder_scheduled_alarm_keys")
}

/**
 * Todo 提醒重排 Worker：
 * - 扫描本地 TodoItem（含 remindersJson）并计算未来一段时间内的提醒触发点；
 * - 先取消所有旧的通知 work，再按最新数据排程新的 notify work。
 *
 * 设计取舍：
 * - 使用 WorkManager 延时任务实现“到点提醒”，不需要精确闹钟权限；
 * - 但可能在省电/待机下被系统延迟，这是 WorkManager 的正常表现。
 */
@HiltWorker
class TodoReminderRescheduleWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val todoDao: TodoDao,
    private val settingsRepository: SettingsRepository,
    private val flowBackendCredentialStorage: FlowBackendCredentialStorage,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val workManager = WorkManager.getInstance(applicationContext)
        // 先清理旧的提醒通知任务，避免 due/rrule/reminders 更新后“旧任务仍会弹”。
        workManager.cancelAllWorkByTag(TodoReminderNotifyWorker.TAG)

        val alarmManager = applicationContext.getSystemService(AlarmManager::class.java)
        val scheduledAlarmKeys = loadScheduledAlarmKeys()

        val settings = settingsRepository.settings.first()
        val ownerKey = currentOwnerKeyOrNull()

        val hasPermission =
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        val enabled =
            hasPermission &&
                settings.loginMode == LoginMode.BACKEND &&
                settings.token.trim().isNotBlank() &&
                !ownerKey.isNullOrBlank()

        // 若当前不可用（未登录/无权限/无 owner），仍应尽力清理历史排程，避免“退出登录后仍持续唤醒设备”。
        if (!enabled) {
            cancelExactAlarms(alarmManager = alarmManager, keys = scheduledAlarmKeys)
            saveScheduledAlarmKeys(emptySet())
            return Result.success()
        }

        val ownerKeyValue = ownerKey.orEmpty()

        val nowMs = System.currentTimeMillis()
        val lookAheadMs = TimeUnit.DAYS.toMillis(MAX_LOOKAHEAD_DAYS.toLong())
        val upperBoundMs = nowMs + lookAheadMs

        val items =
            todoDao.observeItems(
                ownerKey = ownerKeyValue,
                listId = null,
                status = null,
                includeArchivedLists = true,
                includeDeleted = false,
                tagNeedle = null,
            )
                .first()

        val triggers =
            items.asSequence()
                .filter { it.remindersJson.trim().isNotBlank() && it.remindersJson.trim() != "[]" }
                .flatMap { item ->
                    computeTriggersForItem(
                        item = item,
                        nowMs = nowMs,
                        upperBoundMs = upperBoundMs,
                    )
                }
                .sortedBy { it.triggerAtMs }
                .take(MAX_TRIGGERS)
                .toList()

        val exactEnabled =
            settings.todoReminderMode == TodoReminderMode.EXACT && alarmManager?.canScheduleExactAlarms() == true

        if (exactEnabled) {
            val newKeys =
                triggers
                    .map { trigger -> alarmKey(ownerKey = ownerKeyValue, itemId = trigger.itemId, minutes = trigger.beforeMinutes) }
                    .toSet()

            val removed = scheduledAlarmKeys - newKeys
            cancelExactAlarms(alarmManager = alarmManager, keys = removed)

            triggers.forEach { trigger ->
                scheduleExactAlarm(
                    alarmManager = alarmManager,
                    ownerKey = ownerKeyValue,
                    trigger = trigger,
                )
            }

            saveScheduledAlarmKeys(newKeys)
        } else {
            // 智能模式：使用 WorkManager 延时任务；若曾启用过准点模式，先清理旧闹钟排程。
            cancelExactAlarms(alarmManager = alarmManager, keys = scheduledAlarmKeys)
            saveScheduledAlarmKeys(emptySet())

            triggers.forEach { trigger ->
                val delayMs = (trigger.triggerAtMs - nowMs).coerceAtLeast(0L)
                val request =
                    OneTimeWorkRequestBuilder<TodoReminderNotifyWorker>()
                        .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                        .addTag(TodoReminderNotifyWorker.TAG)
                        .setInputData(
                            workDataOf(
                                TodoReminderNotifyWorker.KEY_ITEM_ID to trigger.itemId,
                                TodoReminderNotifyWorker.KEY_DUE_AT_LOCAL to trigger.dueAtLocal,
                                TodoReminderNotifyWorker.KEY_BEFORE_MINUTES to trigger.beforeMinutes,
                            ),
                        )
                        .build()

                workManager.enqueueUniqueWork(
                    TodoReminderNotifyWorker.uniqueWorkName(
                        itemId = trigger.itemId,
                        triggerAtMs = trigger.triggerAtMs,
                        minutes = trigger.beforeMinutes,
                    ),
                    ExistingWorkPolicy.KEEP,
                    request,
                )
            }
        }

        return Result.success()
    }

    private suspend fun loadScheduledAlarmKeys(): Set<String> {
        return runCatching {
            applicationContext.todoReminderAlarmDataStore.data.first()[TodoReminderAlarmStateKeys.SCHEDULED_ALARM_KEYS]
        }
            .getOrNull()
            ?: emptySet()
    }

    private suspend fun saveScheduledAlarmKeys(keys: Set<String>) {
        applicationContext.todoReminderAlarmDataStore.edit { prefs ->
            if (keys.isEmpty()) {
                prefs.remove(TodoReminderAlarmStateKeys.SCHEDULED_ALARM_KEYS)
            } else {
                prefs[TodoReminderAlarmStateKeys.SCHEDULED_ALARM_KEYS] = keys
            }
        }
    }

    private fun cancelExactAlarms(
        alarmManager: AlarmManager?,
        keys: Set<String>,
    ) {
        if (alarmManager == null) return
        keys.forEach { key ->
            val parsed = parseAlarmKey(key) ?: return@forEach
            val pendingIntent =
                PendingIntent.getBroadcast(
                    applicationContext,
                    alarmRequestCode(
                        ownerKey = parsed.ownerKey,
                        itemId = parsed.itemId,
                        minutes = parsed.minutes,
                    ),
                    Intent(applicationContext, TodoReminderAlarmReceiver::class.java).apply {
                        action = TodoReminderAlarmReceiver.ACTION_NOTIFY
                        data = alarmUri(ownerKey = parsed.ownerKey, itemId = parsed.itemId, minutes = parsed.minutes)
                    },
                    PendingIntent.FLAG_IMMUTABLE,
                )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun scheduleExactAlarm(
        alarmManager: AlarmManager?,
        ownerKey: String,
        trigger: ReminderTrigger,
    ) {
        if (alarmManager == null) return
        val pendingIntent =
            PendingIntent.getBroadcast(
                applicationContext,
                alarmRequestCode(ownerKey = ownerKey, itemId = trigger.itemId, minutes = trigger.beforeMinutes),
                Intent(applicationContext, TodoReminderAlarmReceiver::class.java).apply {
                    action = TodoReminderAlarmReceiver.ACTION_NOTIFY
                    data = alarmUri(ownerKey = ownerKey, itemId = trigger.itemId, minutes = trigger.beforeMinutes)
                    putExtra(TodoReminderAlarmReceiver.EXTRA_OWNER_KEY, ownerKey)
                    putExtra(TodoReminderNotifyWorker.KEY_ITEM_ID, trigger.itemId)
                    putExtra(TodoReminderNotifyWorker.KEY_DUE_AT_LOCAL, trigger.dueAtLocal)
                    putExtra(TodoReminderNotifyWorker.KEY_BEFORE_MINUTES, trigger.beforeMinutes)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            trigger.triggerAtMs,
            pendingIntent,
        )
    }

    private data class AlarmKey(
        val ownerKey: String,
        val itemId: String,
        val minutes: Int,
    )

    private fun alarmKey(
        ownerKey: String,
        itemId: String,
        minutes: Int,
    ): String = "${ownerKey.trim()}|${itemId.trim()}|${minutes.coerceAtLeast(0)}"

    private fun parseAlarmKey(key: String): AlarmKey? {
        val t = key.trim()
        if (t.isBlank()) return null
        val parts = t.split("|")
        if (parts.size != 3) return null
        val ownerKey = parts[0].trim()
        val itemId = parts[1].trim()
        val minutes = parts[2].trim().toIntOrNull() ?: return null
        if (ownerKey.isBlank() || itemId.isBlank()) return null
        return AlarmKey(ownerKey = ownerKey, itemId = itemId, minutes = minutes.coerceAtLeast(0))
    }

    private fun alarmRequestCode(
        ownerKey: String,
        itemId: String,
        minutes: Int,
    ): Int = alarmKey(ownerKey = ownerKey, itemId = itemId, minutes = minutes).hashCode()

    private fun alarmUri(
        ownerKey: String,
        itemId: String,
        minutes: Int,
    ): Uri = Uri.parse("onememos://todo-reminder/$ownerKey/$itemId/$minutes")

    private fun computeTriggersForItem(
        item: TodoItemEntity,
        nowMs: Long,
        upperBoundMs: Long,
    ): Sequence<ReminderTrigger> {
        val reminders = parseBeforeDueMinutes(item.remindersJson)
        if (reminders.isEmpty()) return emptySequence()

        val tzid = item.tzid.trim().ifBlank { ZoneId.systemDefault().id }
        val nowLocal = LocalDateTimes.nowString(tzid)

        val dueAtLocal =
            if (item.isRecurring) {
                val rrule = item.rrule?.trim().orEmpty()
                val dtstartLocal = item.dtstartLocal?.trim().orEmpty()
                if (rrule.isBlank() || dtstartLocal.isBlank()) return emptySequence()
                TodoRecurrenceCalculator.nextRecurrenceIdLocal(
                    rrule = rrule,
                    dtstartLocal = dtstartLocal,
                    nowLocal = nowLocal,
                )
            } else {
                item.dueAtLocal?.trim()?.takeIf { it.isNotBlank() }
            }
                ?: return emptySequence()

        val dueLocal = LocalDateTimes.parseOrNull(dueAtLocal) ?: return emptySequence()
        val zone = runCatching { ZoneId.of(tzid) }.getOrNull() ?: ZoneId.systemDefault()
        val dueAtMs = dueLocal.atZone(zone).toInstant().toEpochMilli()

        // 非循环任务：已完成就不提醒；循环任务由 occurrence 完成，item 本身通常保持 OPEN。
        if (!item.isRecurring) {
            val done = item.status == TodoStatuses.DONE || !item.completedAtLocal.isNullOrBlank()
            if (done) return emptySequence()
        }

        return reminders.asSequence().mapNotNull { minutes ->
            val triggerAtMs = dueAtMs - TimeUnit.MINUTES.toMillis(minutes.toLong())
            if (triggerAtMs <= nowMs + MIN_DELAY_MS) return@mapNotNull null
            if (triggerAtMs > upperBoundMs) return@mapNotNull null
            ReminderTrigger(
                itemId = item.id,
                dueAtLocal = dueAtLocal,
                beforeMinutes = minutes,
                triggerAtMs = triggerAtMs,
            )
        }
    }

    private fun parseBeforeDueMinutes(remindersJson: String): List<Int> {
        val t = remindersJson.trim()
        if (t.isBlank()) return emptyList()
        val arr = runCatching { JSONArray(t) }.getOrNull() ?: return emptyList()

        val minutesSet = linkedSetOf<Int>()
        for (i in 0 until arr.length()) {
            val obj = arr.opt(i) as? JSONObject ?: continue
            val type = obj.optString("type").trim()
            if (!type.equals("before_due", ignoreCase = true)) continue
            val minutes = obj.optInt("minutes", -1)
            if (minutes < 0) continue
            minutesSet += minutes.coerceAtLeast(0)
        }

        return minutesSet.toList().sorted()
    }

    private fun currentOwnerKeyOrNull(): String? {
        val cred = flowBackendCredentialStorage.get() ?: return null
        val username = cred.username.trim()
        if (username.isBlank()) return null
        return Hashing.sha256Hex(username.lowercase())
    }

    private data class ReminderTrigger(
        val itemId: String,
        val dueAtLocal: String,
        val beforeMinutes: Int,
        val triggerAtMs: Long,
    )

    companion object {
        const val TAG = "todo_reminder_reschedule"

        private const val MAX_LOOKAHEAD_DAYS = 30
        private const val MAX_TRIGGERS = 80
        private const val MIN_DELAY_MS = 5_000L
    }
}
