@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package cc.pscly.onememos.ui.feature.todo

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.pscly.onememos.domain.model.TodoItem
import cc.pscly.onememos.domain.model.TodoList
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.component.ScrollPaperSurface
import cc.pscly.onememos.ui.component.SealButton
import cc.pscly.onememos.ui.component.SealStampOverlay
import cc.pscly.onememos.ui.theme.InkSpacing
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import cc.pscly.onememos.ui.theme.PaperInkTopAppBar

@Composable
fun TodoScreen(
    onOpenDrawer: () -> Unit,
    targetKey: cc.pscly.onememos.navigation.TodoItemKey? = null,
    onTargetDismiss: (() -> Unit)? = null,
    viewModel: TodoViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val targetState by viewModel.todoItemTargetState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val hasAnyReminders by viewModel.hasAnyReminders.collectAsStateWithLifecycle()
    var notificationPermissionGranted by remember {
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
                    viewModel.requestReminderReschedule()
                }
            },
        )

    var showCreateList by remember { mutableStateOf(false) }
    var newListName by remember { mutableStateOf("") }
    var managingList by remember { mutableStateOf<TodoList?>(null) }
    var showDeletedLists by remember { mutableStateOf(false) }
    var showDeletedItems by remember { mutableStateOf(false) }

    var showCreateItem by remember { mutableStateOf(false) }
    var createItemInitialListId by remember { mutableStateOf<String?>(null) }
    var editingItem by remember { mutableStateOf<TodoItem?>(null) }
    var targetUnavailableShown by remember(targetKey) { mutableStateOf(false) }

    LaunchedEffect(targetKey) {
        if (targetKey != null) {
            viewModel.bind(targetKey)
        }
    }

    // Navigation 3 typed target: Ready 复用编辑对话框；Unavailable 一次提示后弹栈。
    LaunchedEffect(targetState) {
        when (val s = targetState) {
            is TodoItemTargetState.Ready -> {
                if (editingItem?.id != s.item.id) {
                    editingItem = s.item
                }
            }
            is TodoItemTargetState.Unavailable -> {
                if (!targetUnavailableShown) {
                    targetUnavailableShown = true
                }
            }
            else -> Unit
        }
    }

    var moreMenuExpanded by remember { mutableStateOf(false) }
    var showTagInput by remember { mutableStateOf(false) }
    var tagInputDraft by remember { mutableStateOf("") }

    var stampVisible by remember { mutableStateOf(false) }
    var lastDeletedItemId by remember { mutableStateOf<String?>(null) }

    val listNameMap = remember(uiState.lists) { uiState.lists.associate { it.id to it.name } }
    val sections = remember(uiState.items) { buildTodoSections(uiState.items) }

    // 记录滚动 delta，用于驱动纸张横线偏移（LazyColumn 没法直接拿到连续 scroll）。
    val paperScrollOffsetPx = remember { mutableFloatStateOf(0f) }
    val paperScrollConnection =
        remember {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    // consumed.y：用户向上滑动时为负；我们希望 scrollOffset 正向递增（更直觉）。
                    paperScrollOffsetPx.floatValue -= consumed.y
                    return Offset.Zero
                }
            }
        }

    // 盖章：短暂显示即可（避免持续遮挡）。
    LaunchedEffect(stampVisible) {
        if (!stampVisible) return@LaunchedEffect
        delay(260)
        stampVisible = false
    }

    Scaffold(
        topBar = {
            PaperInkTopAppBar(
                title = { Text("待办") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(imageVector = Icons.Filled.Menu, contentDescription = "菜单")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::requestSync) {
                        Icon(imageVector = Icons.Filled.Refresh, contentDescription = "同步")
                    }
                    IconButton(onClick = { moreMenuExpanded = true }) {
                        Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(
                        expanded = moreMenuExpanded,
                        onDismissRequest = { moreMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("新增任务") },
                            onClick = {
                                moreMenuExpanded = false
                                createItemInitialListId = uiState.selectedListId ?: uiState.lists.firstOrNull()?.id
                                showCreateItem = true
                            },
                            leadingIcon = { Icon(imageVector = Icons.Filled.Add, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = { Text("新建清单") },
                            onClick = {
                                moreMenuExpanded = false
                                showCreateList = true
                            },
                        )
                        val selectedList = uiState.selectedListId?.let { id -> uiState.lists.firstOrNull { it.id == id } }
                        if (selectedList != null) {
                            DropdownMenuItem(
                                text = { Text("管理当前清单") },
                                onClick = {
                                    moreMenuExpanded = false
                                    managingList = selectedList
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("已删除清单") },
                            onClick = {
                                moreMenuExpanded = false
                                showDeletedLists = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("已删除任务") },
                            onClick = {
                                moreMenuExpanded = false
                                showDeletedItems = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("筛选标签…") },
                            onClick = {
                                moreMenuExpanded = false
                                tagInputDraft = uiState.tagFilter
                                showTagInput = true
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            SealButton(
                text = "办",
                onClick = {
                    createItemInitialListId = uiState.selectedListId ?: uiState.lists.firstOrNull()?.id
                    showCreateItem = true
                },
            )
        },
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = InkSpacing.X12, vertical = InkSpacing.X10),
        ) {
            ScrollPaperSurface(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(paperScrollConnection),
                scrollOffsetPx = paperScrollOffsetPx.floatValue,
            ) {
                if (!uiState.enabled) {
                    Text(
                        text = "待办功能需要使用“账号登录（BACKEND）”。请先在设置页登录。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    return@ScrollPaperSurface
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(InkSpacing.X10),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (hasAnyReminders && !notificationPermissionGranted) {
                        item(key = "permission_banner") {
                            InkCard {
                                Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.X8)) {
                                    Text(text = "待办提醒需要通知权限", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "当前系统未授予通知权限（POST_NOTIFICATIONS），提醒将无法弹出。授权后会自动重排提醒。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(InkSpacing.X8)) {
                                        Button(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                                            Text("授权通知")
                                        }
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
                            }
                        }
                    }

                    item(key = "filters") {
                        TodoFilterBar(
                            lists = uiState.lists,
                            includeArchivedLists = uiState.includeArchivedLists,
                            selectedListId = uiState.selectedListId,
                            statusFilter = uiState.statusFilter,
                            tagFilter = uiState.tagFilter,
                            onSelectList = viewModel::selectList,
                            onSetStatusFilter = viewModel::setStatusFilter,
                            onSetTagFilter = viewModel::setTagFilter,
                            onToggleIncludeArchived = viewModel::setIncludeArchivedLists,
                            onOpenTagInput = {
                                tagInputDraft = uiState.tagFilter
                                showTagInput = true
                            },
                        )
                    }

                    if (uiState.items.isEmpty()) {
                        item(key = "empty") {
                            InkCard {
                                Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.X8)) {
                                    Text(text = "今日无事，可安心挥毫。", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "点击右下角「办」新增任务；也可以先新建一个清单，让待办更有条理。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(InkSpacing.X10)) {
                                        TextButton(onClick = { showCreateList = true }) {
                                            Text("新建清单")
                                        }
                                        TextButton(
                                            onClick = {
                                                createItemInitialListId = uiState.selectedListId ?: uiState.lists.firstOrNull()?.id
                                                showCreateItem = true
                                            },
                                        ) {
                                            Text("新增任务")
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        sections.forEach { section ->
                            item(key = "section_${section.key}") {
                                Text(
                                    text = section.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            items(
                                items = section.items,
                                key = { it.item.id },
                            ) { p ->
                                val listName =
                                    if (uiState.selectedListId == null) {
                                        listNameMap[p.item.listId]
                                    } else {
                                        null
                                    }
                                TodoItemRow(
                                    p = p,
                                    listName = listName,
                                    onOpen = { item -> editingItem = item },
                                    onToggleDone = viewModel::toggleDone,
                                    onDelete = { item ->
                                        lastDeletedItemId = item.id
                                        viewModel.deleteItem(item.id)
                                    },
                                    onTagClick = viewModel::setTagFilter,
                                    onStamp = { stampVisible = true },
                                )
                            }
                        }
                    }
                }
            }

            TodoUndoHost(
                deletedItemId = lastDeletedItemId,
                onUndo = { id ->
                    viewModel.restoreItem(id)
                    lastDeletedItemId = null
                },
                onExpired = { id ->
                    if (lastDeletedItemId == id) lastDeletedItemId = null
                },
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = InkSpacing.X8),
            )

            SealStampOverlay(
                visible = stampVisible,
                text = "已办",
            )
        }
    }

    if (managingList != null) {
        val list = managingList ?: return
        TodoManageListDialog(
            list = list,
            onSave = viewModel::updateList,
            onDelete = { toDelete -> viewModel.deleteList(toDelete.id) },
            onDismiss = { managingList = null },
        )
    }

    if (showCreateList) {
        AlertDialog(
            onDismissRequest = {
                showCreateList = false
                newListName = ""
            },
            title = { Text("新建清单") },
            text = {
                OutlinedTextField(
                    value = newListName,
                    onValueChange = { newListName = it },
                    label = { Text("清单名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createList(newListName)
                        showCreateList = false
                        newListName = ""
                    },
                    enabled = newListName.trim().isNotBlank(),
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateList = false
                        newListName = ""
                    },
                ) {
                    Text("取消")
                }
            },
        )
    }

    if (showTagInput) {
        AlertDialog(
            onDismissRequest = { showTagInput = false },
            title = { Text("筛选标签") },
            text = {
                OutlinedTextField(
                    value = tagInputDraft,
                    onValueChange = { tagInputDraft = it },
                    label = { Text("输入 tag（不含 #）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            imeAction = ImeAction.Done,
                            keyboardType = KeyboardType.Text,
                        ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setTagFilter(tagInputDraft.trim())
                        showTagInput = false
                    },
                ) {
                    Text("应用")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTagInput = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showCreateItem) {
        TodoCreateItemDialog(
            lists = uiState.lists,
            initialListId = createItemInitialListId,
            onCreate = { listId, title ->
                viewModel.createQuickItem(title = title, listId = listId)
            },
            onDismiss = { showCreateItem = false },
        )
    }


    if (targetUnavailableShown && targetState is TodoItemTargetState.Unavailable) {
        val reason = (targetState as TodoItemTargetState.Unavailable).reason
        val msg =
            when (reason) {
                TodoItemUnavailableReason.DISABLED -> "待办未启用或未登录"
                TodoItemUnavailableReason.DELETED -> "该待办已删除"
                TodoItemUnavailableReason.NOT_FOUND_OR_ACCOUNT_MISMATCH -> "待办不存在或账号不匹配"
            }
        AlertDialog(
            onDismissRequest = {
                targetUnavailableShown = false
                onTargetDismiss?.invoke()
            },
            title = { Text("无法打开待办") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(
                    onClick = {
                        targetUnavailableShown = false
                        onTargetDismiss?.invoke()
                    },
                ) { Text("知道了") }
            },
        )
    }

    if (editingItem != null) {
        val item = editingItem ?: return
        val occurrencesFlow = remember(item.id) { viewModel.observeOccurrences(itemId = item.id, includeDeleted = false) }
        val occurrences by occurrencesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

        TodoEditItemDialog(
            item = item,
            lists = uiState.lists,
            occurrences = if (item.isRecurring) occurrences else emptyList(),
            onSave = viewModel::updateItem,
            onCompleteNextOccurrence = { viewModel.toggleDone(item, true) },
            onDelete = { toDelete ->
                lastDeletedItemId = toDelete.id
                viewModel.deleteItem(toDelete.id)
            },
            onRequestTestReminder = viewModel::requestTestReminder,
            onRequestReminderReschedule = viewModel::requestReminderReschedule,
            onDismiss = {
                editingItem = null
                if (targetKey != null) {
                    onTargetDismiss?.invoke()
                }
            },
        )
    }

    if (showDeletedLists) {
        val deletedListsFlow = remember { viewModel.observeDeletedLists() }
        val deletedLists by deletedListsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

        TodoDeletedListsDialog(
            deletedLists = deletedLists,
            onRestore = { list -> viewModel.restoreList(list.id) },
            onDismiss = { showDeletedLists = false },
        )
    }

    if (showDeletedItems) {
        val deletedItemsFlow = remember { viewModel.observeDeletedItems() }
        val deletedItems by deletedItemsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

        TodoDeletedItemsDialog(
            deletedItems = deletedItems,
            listNameMap = listNameMap,
            onRestore = { item -> viewModel.restoreItem(item.id) },
            onDismiss = { showDeletedItems = false },
        )
    }
}

internal fun showDateTimePicker(
    context: android.content.Context,
    initial: LocalDateTime,
    onPicked: (LocalDateTime) -> Unit,
) {
    DatePickerDialog(
        context,
        datePicker@{ _, year, month0, dayOfMonth ->
            val pickedDate =
                runCatching { LocalDate.of(year, month0 + 1, dayOfMonth) }.getOrNull()
                    ?: return@datePicker
            TimePickerDialog(
                context,
                timePicker@{ _, hourOfDay, minute ->
                    val pickedTime =
                        runCatching { LocalTime.of(hourOfDay, minute, 0) }.getOrNull()
                            ?: return@timePicker
                    onPicked(LocalDateTime.of(pickedDate, pickedTime))
                },
                initial.hour,
                initial.minute,
                true,
            ).show()
        },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).show()
}
