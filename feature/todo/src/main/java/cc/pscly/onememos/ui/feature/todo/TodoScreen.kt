@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package cc.pscly.onememos.ui.feature.todo

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.pscly.onememos.domain.model.TodoItem
import cc.pscly.onememos.domain.model.TodoStatuses
import cc.pscly.onememos.domain.util.LocalDateTimes
import cc.pscly.onememos.ui.component.TagChip
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Composable
fun TodoScreen(
    onOpenDrawer: () -> Unit,
) {
    val viewModel: TodoViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showCreateList by remember { mutableStateOf(false) }
    var newListName by remember { mutableStateOf("") }
    var showManageList by remember { mutableStateOf(false) }
    var editListName by remember { mutableStateOf("") }
    var editListArchived by remember { mutableStateOf(false) }

    var showCreateItem by remember { mutableStateOf(false) }
    var newItemTitle by remember { mutableStateOf("") }
    var newItemListId by remember { mutableStateOf<String?>(null) }

    var editingItem by remember { mutableStateOf<TodoItem?>(null) }
    var editItemTitle by remember { mutableStateOf("") }
    var editItemNote by remember { mutableStateOf("") }
    var editItemTagsText by remember { mutableStateOf("") }
    var editItemDueAtLocal by remember { mutableStateOf<String?>(null) }
    var editItemListId by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
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
                    IconButton(
                        onClick = {
                            newItemListId = uiState.selectedListId ?: uiState.lists.firstOrNull()?.id
                            showCreateItem = true
                        },
                    ) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = "新增任务")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (!uiState.enabled) {
                Text(
                    text = "待办功能需要使用“账号登录（BACKEND）”。请先在设置页登录。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("显示归档清单", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = uiState.includeArchivedLists,
                    onCheckedChange = viewModel::setIncludeArchivedLists,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("清单", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { showCreateList = true }) {
                    Text("新建清单")
                }
                val selectedList = uiState.selectedListId?.let { id -> uiState.lists.firstOrNull { it.id == id } }
                if (selectedList != null) {
                    IconButton(
                        onClick = {
                            editListName = selectedList.name
                            editListArchived = selectedList.archived
                            showManageList = true
                        },
                    ) {
                        Icon(imageVector = Icons.Filled.Edit, contentDescription = "管理清单")
                    }
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                item {
                    FilterChip(
                        label = "全部",
                        selected = uiState.selectedListId == null,
                        onClick = { viewModel.selectList(null) },
                    )
                }
                items(uiState.lists, key = { it.id }) { list ->
                    FilterChip(
                        label =
                            if (list.archived) {
                                "${list.name}（归档）"
                            } else {
                                list.name
                            },
                        selected = uiState.selectedListId == list.id,
                        onClick = { viewModel.selectList(list.id) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    label = "全部",
                    selected = uiState.statusFilter == null,
                    onClick = { viewModel.setStatusFilter(null) },
                )
                FilterChip(
                    label = "未完成",
                    selected = uiState.statusFilter == TodoStatuses.OPEN,
                    onClick = { viewModel.setStatusFilter(TodoStatuses.OPEN) },
                )
                FilterChip(
                    label = "已完成",
                    selected = uiState.statusFilter == TodoStatuses.DONE,
                    onClick = { viewModel.setStatusFilter(TodoStatuses.DONE) },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.tagFilter,
                onValueChange = viewModel::setTagFilter,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("按标签筛选（输入 tag）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))

            val listNameMap = remember(uiState.lists) { uiState.lists.associate { it.id to it.name } }

            if (uiState.items.isEmpty()) {
                Text(
                    text = "暂无任务。点击右上角 + 新增。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(uiState.items, key = { it.id }) { item ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val isDone = item.status == TodoStatuses.DONE || !item.completedAtLocal.isNullOrBlank()
                                Checkbox(
                                    checked = isDone,
                                    onCheckedChange = { checked -> viewModel.toggleDone(item, checked) },
                                )
                                Column(
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .clickable {
                                                editingItem = item
                                                editItemTitle = item.title
                                                editItemNote = item.note
                                                editItemTagsText = item.tags.joinToString(separator = ", ")
                                                editItemDueAtLocal = item.dueAtLocal
                                                editItemListId = item.listId
                                            },
                                ) {
                                    Text(text = item.title, style = MaterialTheme.typography.titleMedium)
                                    if (!item.dueAtLocal.isNullOrBlank()) {
                                        Text(
                                            text = "到期：${item.dueAtLocal}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else if (item.isRecurring) {
                                        Text(
                                            text = "循环任务",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (uiState.selectedListId == null) {
                                        val listName = listNameMap[item.listId]
                                        if (!listName.isNullOrBlank()) {
                                            Text(
                                                text = "清单：$listName",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    if (item.tags.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items(item.tags, key = { it }) { tag ->
                                                TagChip(
                                                    tag = tag,
                                                    onClick = { viewModel.setTagFilter(tag) },
                                                )
                                            }
                                        }
                                    }
                                }
                                IconButton(onClick = { viewModel.deleteItem(item.id) }) {
                                    Icon(imageVector = Icons.Filled.Delete, contentDescription = "删除")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showManageList) {
        val selectedList = uiState.selectedListId?.let { id -> uiState.lists.firstOrNull { it.id == id } }
        if (selectedList != null) {
            AlertDialog(
                onDismissRequest = { showManageList = false },
                title = { Text("管理清单") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = editListName,
                            onValueChange = { editListName = it },
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
                                checked = editListArchived,
                                onCheckedChange = { editListArchived = it },
                            )
                        }
                        Text(
                            text = "提示：归档清单默认会被隐藏；可在页面顶部打开“显示归档清单”。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.updateList(
                                selectedList.copy(
                                    name = editListName.trim().ifBlank { selectedList.name },
                                    archived = editListArchived,
                                ),
                            )
                            showManageList = false
                        },
                        enabled = editListName.trim().isNotBlank(),
                    ) {
                        Text("保存")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showManageList = false }) {
                        Text("取消")
                    }
                },
            )
        } else {
            showManageList = false
        }
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

    if (showCreateItem) {
        AlertDialog(
            onDismissRequest = {
                showCreateItem = false
                newItemTitle = ""
                newItemListId = null
            },
            title = { Text("新增任务") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("选择清单", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.lists, key = { it.id }) { list ->
                            FilterChip(
                                label = list.name,
                                selected = newItemListId == list.id,
                                onClick = { newItemListId = list.id },
                            )
                        }
                    }

                    OutlinedTextField(
                        value = newItemTitle,
                        onValueChange = { newItemTitle = it },
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
                        val listId = newItemListId ?: return@Button
                        viewModel.createQuickItem(newItemTitle, listId)
                        showCreateItem = false
                        newItemTitle = ""
                        newItemListId = null
                    },
                    enabled = newItemTitle.trim().isNotBlank() && !newItemListId.isNullOrBlank(),
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateItem = false
                        newItemTitle = ""
                    },
                ) {
                    Text("取消")
                }
            },
        )
    }

    if (editingItem != null) {
        val item = editingItem ?: return
        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = { Text("编辑任务") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editItemTitle,
                        onValueChange = { editItemTitle = it },
                        label = { Text("标题") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    OutlinedTextField(
                        value = editItemNote,
                        onValueChange = { editItemNote = it },
                        label = { Text("备注") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                    )

                    Text("所属清单", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.lists, key = { it.id }) { list ->
                            FilterChip(
                                label = list.name,
                                selected = editItemListId == list.id,
                                onClick = { editItemListId = list.id },
                            )
                        }
                    }

                    OutlinedTextField(
                        value = editItemTagsText,
                        onValueChange = { editItemTagsText = it },
                        label = { Text("标签（逗号/空格分隔）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )

                    OutlinedTextField(
                        value = editItemDueAtLocal.orEmpty(),
                        onValueChange = { editItemDueAtLocal = it.trim().ifBlank { null } },
                        label = { Text("到期时间（yyyy-MM-dd HH:mm:ss）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val initial =
                                        LocalDateTimes.parseOrNull(editItemDueAtLocal)
                                            ?: LocalDateTime.now().withSecond(0).withNano(0)
                                    showDateTimePicker(
                                        context = context,
                                        initial = initial,
                                        onPicked = { picked ->
                                            editItemDueAtLocal = LocalDateTimes.format(picked.withSecond(0).withNano(0))
                                        },
                                    )
                                },
                            ) {
                                Icon(imageVector = Icons.Filled.Event, contentDescription = "选择日期")
                            }
                        },
                    )
                    if (!editItemDueAtLocal.isNullOrBlank()) {
                        TextButton(onClick = { editItemDueAtLocal = null }) {
                            Text("清除到期时间")
                        }
                    }

                    if (item.isRecurring) {
                        Text(
                            text = "循环任务：当前仅支持“完成本次”。RRULE 编辑将在后续版本补齐。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { viewModel.toggleDone(item, true) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("完成本次")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val tags =
                            editItemTagsText
                                .split(',', '，', ' ', '\n', '\t')
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .distinct()
                        val listId = editItemListId.trim()
                        if (listId.isBlank()) return@Button

                        viewModel.updateItem(
                            item.copy(
                                title = editItemTitle.trim().ifBlank { item.title },
                                note = editItemNote,
                                tags = tags,
                                dueAtLocal = editItemDueAtLocal?.trim()?.takeIf { it.isNotBlank() },
                                listId = listId,
                            ),
                        )
                        editingItem = null
                    },
                    enabled = editItemTitle.trim().isNotBlank() && editItemListId.trim().isNotBlank(),
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingItem = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg =
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    val fg =
        if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = bg),
        modifier = Modifier,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = fg,
        )
    }
}

private fun showDateTimePicker(
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
