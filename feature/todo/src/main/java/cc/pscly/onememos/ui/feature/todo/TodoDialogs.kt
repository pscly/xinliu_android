@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package cc.pscly.onememos.ui.feature.todo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import cc.pscly.onememos.domain.model.TodoItem
import cc.pscly.onememos.domain.model.TodoList
import cc.pscly.onememos.domain.model.TodoOccurrence
import cc.pscly.onememos.domain.model.TodoStatuses
import cc.pscly.onememos.domain.todo.TodoRecurrenceCalculator
import cc.pscly.onememos.domain.util.LocalDateTimes
import cc.pscly.onememos.ui.component.InkChip
import cc.pscly.onememos.ui.theme.InkSpacing
import cc.pscly.onememos.ui.util.rememberOneMemosHaptics
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.absoluteValue

@Composable
internal fun TodoManageListDialog(
    list: TodoList,
    onSave: (TodoList) -> Unit,
    onDelete: (TodoList) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(list.id) { mutableStateOf(list.name) }
    var archived by remember(list.id) { mutableStateOf(list.archived) }
    var showDeleteConfirm by remember(list.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理清单") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.X12)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("清单名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("归档", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = archived,
                        onCheckedChange = { archived = it },
                    )
                }
                Text(
                    text = "提示：归档清单默认会被隐藏；可在页面顶部打开“显示归档清单”。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(2.dp))
                TextButton(onClick = { showDeleteConfirm = true }) {
                    Text("删除清单", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        list.copy(
                            name = name.trim().ifBlank { list.name },
                            archived = archived,
                        ),
                    )
                    onDismiss()
                },
                enabled = name.trim().isNotBlank(),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除清单？") },
            text = { Text("删除后清单会进入“已删除清单”，可随时恢复。") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(list)
                        showDeleteConfirm = false
                        onDismiss()
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
internal fun TodoDeletedListsDialog(
    deletedLists: List<TodoList>,
    onRestore: (TodoList) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("已删除清单") },
        text = {
            if (deletedLists.isEmpty()) {
                Text(
                    text = "暂无已删除清单。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(InkSpacing.X10),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(
                        items = deletedLists.sortedByDescending { it.deletedAt.orEmpty() },
                        key = { it.id },
                    ) { list ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = list.name, style = MaterialTheme.typography.titleMedium)
                                    if (!list.deletedAt.isNullOrBlank()) {
                                        Text(
                                            text = "删除于：${list.deletedAt}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                IconButton(onClick = { onRestore(list) }) {
                                    Icon(imageVector = Icons.Filled.RestoreFromTrash, contentDescription = "恢复")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

@Composable
internal fun TodoDeletedItemsDialog(
    deletedItems: List<TodoItem>,
    listNameMap: Map<String, String>,
    onRestore: (TodoItem) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("已删除任务") },
        text = {
            if (deletedItems.isEmpty()) {
                Text(
                    text = "暂无已删除任务。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(InkSpacing.X10),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 520.dp),
                ) {
                    items(
                        items = deletedItems.sortedByDescending { it.deletedAt.orEmpty() },
                        key = { it.id },
                    ) { item ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = item.title, style = MaterialTheme.typography.titleMedium)
                                    val listLabel =
                                        listNameMap[item.listId]
                                            ?: "清单ID：${item.listId.take(8)}"
                                    Text(
                                        text = "清单：$listLabel",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (!item.deletedAt.isNullOrBlank()) {
                                        Text(
                                            text = "删除于：${item.deletedAt}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                IconButton(onClick = { onRestore(item) }) {
                                    Icon(imageVector = Icons.Filled.RestoreFromTrash, contentDescription = "恢复")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

@Composable
internal fun TodoCreateItemDialog(
    lists: List<TodoList>,
    initialListId: String?,
    onCreate: (listId: String, title: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var selectedListId by remember(initialListId, lists) { mutableStateOf(initialListId ?: lists.firstOrNull()?.id) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增任务") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.X12)) {
                Text("选择清单", style = MaterialTheme.typography.labelLarge)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(InkSpacing.X8),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(lists, key = { it.id }) { list ->
                        TodoFilterChip(
                            label = list.name,
                            selected = selectedListId == list.id,
                            onClick = { selectedListId = list.id },
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val listId = selectedListId?.trim().orEmpty()
                    if (listId.isBlank()) return@Button
                    onCreate(listId, title.trim())
                    onDismiss()
                },
                enabled = title.trim().isNotBlank() && !selectedListId.isNullOrBlank(),
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
internal fun TodoEditItemDialog(
    item: TodoItem,
    lists: List<TodoList>,
    occurrences: List<TodoOccurrence>,
    onSave: (TodoItem) -> Unit,
    onCompleteNextOccurrence: () -> Unit,
    onDelete: (TodoItem) -> Unit,
    onRequestTestReminder: (itemId: String, dueAtLocal: String) -> Unit,
    onRequestReminderReschedule: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    var title by remember(item.id) { mutableStateOf(item.title) }
    var note by remember(item.id) { mutableStateOf(item.note) }
    var listId by remember(item.id) { mutableStateOf(item.listId) }
    var tagsText by remember(item.id) { mutableStateOf(item.tags.joinToString(separator = ", ")) }

    var dueAtLocal by remember(item.id) { mutableStateOf(item.dueAtLocal) }
    var tzid by remember(item.id) {
        mutableStateOf(
            item.tzid.trim().ifBlank {
                runCatching { ZoneId.systemDefault().id }.getOrNull().orEmpty()
            },
        )
    }

    // 循环任务
    var recurring by remember(item.id) { mutableStateOf(item.isRecurring) }
    var dtstartLocal by remember(item.id) {
        mutableStateOf(
            item.dtstartLocal?.trim()?.takeIf { it.isNotBlank() }
                ?: item.dueAtLocal?.trim()?.takeIf { it.isNotBlank() }
                ?: LocalDateTimes.nowString(tzid),
        )
    }
    var rruleText by remember(item.id) { mutableStateOf(item.rrule?.trim().orEmpty()) }

    val parsed = remember(item.id) { parseRrule(item.rrule) }
    var freq by remember(item.id) { mutableStateOf(parsed?.freq ?: RruleFreq.DAILY) }
    var intervalText by remember(item.id) { mutableStateOf((parsed?.interval ?: 1).coerceAtLeast(1).toString()) }
    var byDay by remember(item.id) { mutableStateOf(parsed?.byDay.orEmpty()) }
    var monthDayText by remember(item.id) { mutableStateOf((parsed?.byMonthDay ?: 1).coerceIn(1, 31).toString()) }
    var monthText by remember(item.id) { mutableStateOf((parsed?.byMonth ?: 1).coerceIn(1, 12).toString()) }

    // 初次进入：如果 RRULE 无法识别，就进入“自定义”模式，避免误改
    val initialMode =
        remember(item.id) {
            if (item.rrule.isNullOrBlank()) {
                RruleEditorMode.VISUAL
            } else if (parsed == null) {
                RruleEditorMode.CUSTOM
            } else {
                RruleEditorMode.VISUAL
            }
        }
    var rruleMode by remember(item.id) { mutableStateOf(initialMode) }

    val interval = intervalText.trim().toIntOrNull()?.coerceAtLeast(1) ?: 1
    val monthDay = monthDayText.trim().toIntOrNull()?.coerceIn(1, 31) ?: 1
    val month = monthText.trim().toIntOrNull()?.coerceIn(1, 12) ?: 1
    val visualRruleText =
        buildRrule(
            freq = freq,
            interval = interval,
            byDay = byDay,
            byMonthDay = monthDay,
            byMonth = month,
        )
    val effectiveRruleText = if (rruleMode == RruleEditorMode.VISUAL) visualRruleText else rruleText.trim()

    // 提醒（reminders）编辑：客户端自定义结构，统一保存为 JSON 数组字符串
    var reminderEntries by remember(item.id) { mutableStateOf(parseReminders(item.remindersJson)) }

    val haptics = rememberOneMemosHaptics()

    var notificationPermissionGranted by remember(item.id) {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { granted ->
                notificationPermissionGranted = granted
                if (granted) {
                    // 可能存在“先设置提醒，后授权通知”的路径：授权后立即重排一次，减少用户困惑。
                    onRequestReminderReschedule()
                }
            },
        )

    var reminderInlineHint by remember(item.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(reminderInlineHint) {
        if (reminderInlineHint.isNullOrBlank()) return@LaunchedEffect
        delay(2_200)
        reminderInlineHint = null
    }

    fun ensureDueAtLocalForRemindersIfNeeded() {
        if (recurring) return
        if (!dueAtLocal.isNullOrBlank()) return

        val base =
            LocalDateTimes.parseOrNull(LocalDateTimes.nowString(tzid))
                ?: LocalDateTime.now()
        val picked =
            base
                .plusHours(1)
                .withSecond(0)
                .withNano(0)
        val formatted = LocalDateTimes.format(picked)
        dueAtLocal = formatted
        reminderInlineHint = "已自动设置到期时间：$formatted（可修改）"
    }

    var showDeleteConfirm by remember(item.id) { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑任务") },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(InkSpacing.X12),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )

                Text("所属清单", style = MaterialTheme.typography.labelLarge)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(InkSpacing.X8),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(lists, key = { it.id }) { list ->
                        TodoFilterChip(
                            label = if (list.archived) "${list.name}（归档）" else list.name,
                            selected = listId == list.id,
                            onClick = { listId = list.id },
                        )
                    }
                }

                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text("标签（逗号/空格分隔）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )

                OutlinedTextField(
                    value = dueAtLocal.orEmpty(),
                    onValueChange = { dueAtLocal = it.trim().ifBlank { null } },
                    label = { Text("到期时间（yyyy-MM-dd HH:mm:ss）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val initial =
                                    LocalDateTimes.parseOrNull(dueAtLocal)
                                        ?: LocalDateTime.now().withSecond(0).withNano(0)
                                showTodoDateTimePicker(
                                    context = context,
                                    initial = initial,
                                    onPicked = { picked ->
                                        dueAtLocal = LocalDateTimes.format(picked.withSecond(0).withNano(0))
                                    },
                                )
                            },
                        ) {
                            Icon(imageVector = Icons.Filled.Event, contentDescription = "选择日期")
                        }
                    },
                )
                if (!dueAtLocal.isNullOrBlank()) {
                    TextButton(onClick = { dueAtLocal = null }) {
                        Text("清除到期时间")
                    }
                }

                // reminders
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("提醒", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text =
                            when (val n = reminderEntries.size) {
                                0 -> "未设置"
                                else -> "已添加 $n 个（保存后生效）"
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (!notificationPermissionGranted) {
                    Text(
                        text = "未授予通知权限（POST_NOTIFICATIONS），提醒不会弹出。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(InkSpacing.X10),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InkChip(
                            label = "授权通知",
                            selected = true,
                            onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        )
                        TextButton(
                            onClick = {
                                val intent =
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                context.startActivity(intent)
                            },
                        ) {
                            Text("打开系统设置")
                        }
                    }
                }

                if (!recurring && dueAtLocal.isNullOrBlank()) {
                    Text(
                        text = "提示：未设置到期时间。首次添加提醒时会自动设置到期时间为 1 小时后（可修改）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                fun addBeforeDue(minutes: Int) {
                    ensureDueAtLocalForRemindersIfNeeded()
                    reminderEntries = (reminderEntries + ReminderEntry.beforeDue(minutes)).dedupeReminderEntries()
                    reminderInlineHint = "已添加提醒：${ReminderEntry.beforeDue(minutes).label()}（保存后生效）"
                    haptics.tick()
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(InkSpacing.X8),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    item {
                        InkChip(label = "准时", selected = false, onClick = { addBeforeDue(0) })
                    }
                    item {
                        InkChip(label = "5分钟", selected = false, onClick = { addBeforeDue(5) })
                    }
                    item {
                        InkChip(label = "30分钟", selected = false, onClick = { addBeforeDue(30) })
                    }
                    item {
                        InkChip(label = "1小时", selected = false, onClick = { addBeforeDue(60) })
                    }
                    item {
                        InkChip(label = "1天", selected = false, onClick = { addBeforeDue(24 * 60) })
                    }
                }

                if (reminderEntries.isNotEmpty()) {
                    Text(
                        text = "已添加（点击可删除）：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(InkSpacing.X8),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(reminderEntries, key = { it.stableKey }) { entry ->
                            InkChip(
                                label = entry.label(),
                                selected = true,
                                onClick = {
                                    reminderEntries = reminderEntries.filterNot { it.stableKey == entry.stableKey }
                                    reminderInlineHint = "已删除提醒：${entry.label()}（保存后生效）"
                                    haptics.tick()
                                },
                            )
                        }
                    }
                } else {
                    Text(
                        text = "暂无提醒。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val isItemDone = item.status == TodoStatuses.DONE || !item.completedAtLocal.isNullOrBlank()
                InkChip(
                    label = "测试提醒（10秒后）",
                    selected = false,
                    enabled = !isItemDone,
                    onClick = {
                        if (!notificationPermissionGranted) {
                            reminderInlineHint = "请先授权通知权限，再测试提醒。"
                            return@InkChip
                        }
                        val base = LocalDateTimes.parseOrNull(LocalDateTimes.nowString(tzid)) ?: LocalDateTime.now()
                        val testDue = LocalDateTimes.format(base.plusSeconds(10).withNano(0))
                        onRequestTestReminder(item.id, testDue)
                        reminderInlineHint = "已安排测试提醒：$testDue（10 秒后）"
                        haptics.tick()
                    },
                )

                if (!reminderInlineHint.isNullOrBlank()) {
                    Text(
                        text = reminderInlineHint.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                // recurrence
                Text("循环任务", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("启用循环", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = recurring,
                        onCheckedChange = { recurring = it },
                    )
                }

                if (recurring) {
                    OutlinedTextField(
                        value = dtstartLocal.orEmpty(),
                        onValueChange = { dtstartLocal = it.trim().ifBlank { "" } },
                        label = { Text("开始时间（DTSTART，yyyy-MM-dd HH:mm:ss）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val initial =
                                        LocalDateTimes.parseOrNull(dtstartLocal)
                                            ?: LocalDateTime.now().withSecond(0).withNano(0)
                                    showTodoDateTimePicker(
                                        context = context,
                                        initial = initial,
                                        onPicked = { picked ->
                                            dtstartLocal = LocalDateTimes.format(picked.withSecond(0).withNano(0))
                                        },
                                    )
                                },
                            ) {
                                Icon(imageVector = Icons.Filled.Event, contentDescription = "选择日期")
                            }
                        },
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(InkSpacing.X8),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TodoFilterChip(
                            label = "可视化",
                            selected = rruleMode == RruleEditorMode.VISUAL,
                            onClick = { rruleMode = RruleEditorMode.VISUAL },
                        )
                        TodoFilterChip(
                            label = "RRULE",
                            selected = rruleMode == RruleEditorMode.CUSTOM,
                            onClick = {
                                // 切换到自定义时，用当前可视化结果作为初始值，避免空字符串导致不可保存。
                                if (rruleMode != RruleEditorMode.CUSTOM) {
                                    rruleText = visualRruleText
                                }
                                rruleMode = RruleEditorMode.CUSTOM
                            },
                        )
                    }

                    if (rruleMode == RruleEditorMode.VISUAL) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(InkSpacing.X8),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TodoFilterChip(label = "每天", selected = freq == RruleFreq.DAILY, onClick = { freq = RruleFreq.DAILY })
                            TodoFilterChip(label = "每周", selected = freq == RruleFreq.WEEKLY, onClick = { freq = RruleFreq.WEEKLY })
                            TodoFilterChip(label = "每月", selected = freq == RruleFreq.MONTHLY, onClick = { freq = RruleFreq.MONTHLY })
                            TodoFilterChip(label = "每年", selected = freq == RruleFreq.YEARLY, onClick = { freq = RruleFreq.YEARLY })
                        }

                        OutlinedTextField(
                            value = intervalText,
                            onValueChange = { intervalText = it.filter { ch -> ch.isDigit() }.take(3) },
                            label = { Text("间隔（INTERVAL）") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        )

                        if (freq == RruleFreq.WEEKLY) {
                            Text(
                                text = "每周几：",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(InkSpacing.X6),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RruleDay.values().forEach { day ->
                                    val selected = byDay.contains(day.token)
                                    TodoFilterChip(
                                        label = day.zh,
                                        selected = selected,
                                        onClick = {
                                            byDay =
                                                if (selected) {
                                                    byDay - day.token
                                                } else {
                                                    byDay + day.token
                                                }
                                        },
                                    )
                                }
                            }
                        }

                        if (freq == RruleFreq.MONTHLY) {
                            OutlinedTextField(
                                value = monthDayText,
                                onValueChange = { monthDayText = it.filter { ch -> ch.isDigit() }.take(2) },
                                label = { Text("每月第几天（BYMONTHDAY）") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            )
                        }

                        if (freq == RruleFreq.YEARLY) {
                            OutlinedTextField(
                                value = monthText,
                                onValueChange = { monthText = it.filter { ch -> ch.isDigit() }.take(2) },
                                label = { Text("月份（BYMONTH）") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            )
                            OutlinedTextField(
                                value = monthDayText,
                                onValueChange = { monthDayText = it.filter { ch -> ch.isDigit() }.take(2) },
                                label = { Text("日期（BYMONTHDAY）") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            )
                        }

                        val nowLocal = remember(tzid) { LocalDateTimes.nowString(tzid) }
                        val next = remember(effectiveRruleText, dtstartLocal, nowLocal) {
                            TodoRecurrenceCalculator.nextRecurrenceIdLocal(
                                rrule = effectiveRruleText,
                                dtstartLocal = dtstartLocal,
                                nowLocal = nowLocal,
                            )
                        }
                        Text(
                            text = "当前 RRULE：$effectiveRruleText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!next.isNullOrBlank()) {
                            Text(
                                text = "下一次：$next",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = rruleText,
                            onValueChange = { rruleText = it },
                            label = { Text("RRULE（例如：FREQ=WEEKLY;BYDAY=MO,WE,FR）") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                        )
                    }

                    OutlinedTextField(
                        value = tzid,
                        onValueChange = { tzid = it.trim() },
                        label = { Text("时区（TZID，例如 Asia/Shanghai）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )

                    Button(
                        onClick = onCompleteNextOccurrence,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("完成本次")
                    }

                    Text(
                        text = "完成记录",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (occurrences.isEmpty()) {
                        Text(
                            text = "暂无完成记录。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(InkSpacing.X8),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            occurrences.take(20).forEach { oc ->
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(text = oc.recurrenceIdLocal, style = MaterialTheme.typography.bodyMedium)
                                    if (!oc.completedAtLocal.isNullOrBlank()) {
                                        Text(
                                            text = "完成于：${oc.completedAtLocal}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                TextButton(onClick = { showDeleteConfirm = true }) {
                    Text("删除任务", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            val canSave =
                title.trim().isNotBlank() &&
                    listId.trim().isNotBlank() &&
                    (!recurring || (dtstartLocal.trim().length == 19 && effectiveRruleText.isNotBlank()))
            Button(
                onClick = {
                    val tags =
                        tagsText
                            .split(',', '，', ' ', '\n', '\t')
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .distinct()

                    val remindersJson = buildRemindersJson(reminderEntries)

                    onSave(
                        item.copy(
                            title = title.trim().ifBlank { item.title },
                            note = note,
                            tags = tags,
                            dueAtLocal = dueAtLocal?.trim()?.takeIf { it.isNotBlank() },
                            listId = listId.trim(),
                            remindersJson = remindersJson,
                            isRecurring = recurring,
                            rrule = effectiveRruleText.takeIf { recurring && it.isNotBlank() },
                            dtstartLocal = dtstartLocal.trim().takeIf { recurring && it.isNotBlank() },
                            tzid = tzid.trim(),
                        ),
                    )
                    onDismiss()
                },
                enabled = canSave,
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除任务？") },
            text = { Text("删除后可在“已删除任务”中恢复。") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(item)
                        showDeleteConfirm = false
                        onDismiss()
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }
}

internal fun showTodoDateTimePicker(
    context: Context,
    initial: LocalDateTime,
    onPicked: (LocalDateTime) -> Unit,
) {
    showDateTimePicker(context = context, initial = initial, onPicked = onPicked)
}

private enum class RruleEditorMode {
    VISUAL,
    CUSTOM,
}

private enum class RruleFreq(val token: String) {
    DAILY("DAILY"),
    WEEKLY("WEEKLY"),
    MONTHLY("MONTHLY"),
    YEARLY("YEARLY"),
}

private enum class RruleDay(val token: String, val zh: String) {
    MO("MO", "一"),
    TU("TU", "二"),
    WE("WE", "三"),
    TH("TH", "四"),
    FR("FR", "五"),
    SA("SA", "六"),
    SU("SU", "日"),
}

private data class ParsedRrule(
    val freq: RruleFreq,
    val interval: Int,
    val byDay: Set<String>,
    val byMonthDay: Int?,
    val byMonth: Int?,
)

private fun parseRrule(raw: String?): ParsedRrule? {
    val text = raw?.trim().orEmpty()
    if (text.isBlank()) return null

    val parts = text.split(";").map { it.trim() }.filter { it.isNotBlank() }
    val kv = parts.mapNotNull { p ->
        val idx = p.indexOf('=')
        if (idx <= 0 || idx == p.lastIndex) return@mapNotNull null
        p.substring(0, idx).uppercase() to p.substring(idx + 1).trim()
    }.toMap()

    val freqToken = kv["FREQ"]?.uppercase() ?: return null
    val freq =
        when (freqToken) {
            "DAILY" -> RruleFreq.DAILY
            "WEEKLY" -> RruleFreq.WEEKLY
            "MONTHLY" -> RruleFreq.MONTHLY
            "YEARLY" -> RruleFreq.YEARLY
            else -> return null
        }

    val interval = kv["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val byDay =
        kv["BYDAY"]
            ?.split(',')
            ?.map { it.trim().uppercase() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()
    val byMonthDay = kv["BYMONTHDAY"]?.split(',')?.firstOrNull()?.trim()?.toIntOrNull()
    val byMonth = kv["BYMONTH"]?.split(',')?.firstOrNull()?.trim()?.toIntOrNull()

    return ParsedRrule(
        freq = freq,
        interval = interval,
        byDay = byDay,
        byMonthDay = byMonthDay,
        byMonth = byMonth,
    )
}

private fun buildRrule(
    freq: RruleFreq,
    interval: Int,
    byDay: Set<String>,
    byMonthDay: Int,
    byMonth: Int,
): String {
    val parts = mutableListOf<String>()
    parts += "FREQ=${freq.token}"
    if (interval > 1) parts += "INTERVAL=$interval"

    when (freq) {
        RruleFreq.DAILY -> Unit
        RruleFreq.WEEKLY -> {
            val days = byDay.map { it.uppercase() }.filter { it.isNotBlank() }.distinct().sorted()
            if (days.isNotEmpty()) {
                parts += "BYDAY=${days.joinToString(",")}"
            }
        }

        RruleFreq.MONTHLY -> {
            parts += "BYMONTHDAY=${byMonthDay.coerceIn(1, 31)}"
        }

        RruleFreq.YEARLY -> {
            parts += "BYMONTH=${byMonth.coerceIn(1, 12)}"
            parts += "BYMONTHDAY=${byMonthDay.coerceIn(1, 31)}"
        }
    }

    return parts.joinToString(";")
}

private sealed class ReminderEntry(
    val stableKey: String,
) {
    abstract fun label(): String
    abstract fun toJsonObjectOrNull(): JSONObject?

    data class BeforeDue(
        val minutes: Int,
    ) : ReminderEntry(stableKey = "before_due:$minutes") {
        override fun label(): String =
            when (minutes) {
                0 -> "准时"
                else -> "提前 $minutes 分钟"
            }

        override fun toJsonObjectOrNull(): JSONObject =
            JSONObject()
                .put("type", "before_due")
                .put("minutes", minutes)
    }

    data class Raw(
        val rawJson: String,
    ) : ReminderEntry(stableKey = "raw:${rawJson.hashCode().absoluteValue}") {
        override fun label(): String = "自定义提醒"

        override fun toJsonObjectOrNull(): JSONObject? =
            runCatching { JSONObject(rawJson) }.getOrNull()
    }

    companion object {
        fun beforeDue(minutes: Int): ReminderEntry = BeforeDue(minutes.coerceAtLeast(0))
    }
}

private fun List<ReminderEntry>.dedupeReminderEntries(): List<ReminderEntry> =
    this.distinctBy { it.stableKey }
        .sortedWith(
            compareBy<ReminderEntry> {
                when (it) {
                    is ReminderEntry.BeforeDue -> 0
                    is ReminderEntry.Raw -> 1
                }
            }.thenBy {
                when (it) {
                    is ReminderEntry.BeforeDue -> it.minutes
                    is ReminderEntry.Raw -> Int.MAX_VALUE
                }
            },
        )

private fun parseReminders(rawJson: String): List<ReminderEntry> {
    val t = rawJson.trim()
    if (t.isBlank()) return emptyList()

    val arr = runCatching { JSONArray(t) }.getOrNull() ?: return emptyList()
    val result = mutableListOf<ReminderEntry>()
    for (i in 0 until arr.length()) {
        val v = arr.opt(i) ?: continue
        val obj = v as? JSONObject ?: continue
        val type = obj.optString("type").trim()
        if (type.equals("before_due", ignoreCase = true)) {
            val minutes = obj.optInt("minutes", -1)
            if (minutes >= 0) {
                result += ReminderEntry.beforeDue(minutes)
            } else {
                result += ReminderEntry.Raw(obj.toString())
            }
        } else {
            result += ReminderEntry.Raw(obj.toString())
        }
    }
    return result.dedupeReminderEntries()
}

private fun buildRemindersJson(entries: List<ReminderEntry>): String {
    val arr = JSONArray()
    entries.forEach { entry ->
        val obj = entry.toJsonObjectOrNull() ?: return@forEach
        arr.put(obj)
    }
    return arr.toString()
}
