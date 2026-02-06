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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.pscly.onememos.domain.model.TodoItem
import cc.pscly.onememos.domain.model.TodoList
import cc.pscly.onememos.domain.model.TodoStatuses
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

    var showCreateList by remember { mutableStateOf(false) }
    var newListName by remember { mutableStateOf("") }
    var managingList by remember { mutableStateOf<TodoList?>(null) }
    var showDeletedLists by remember { mutableStateOf(false) }

    var showCreateItem by remember { mutableStateOf(false) }
    var createItemInitialListId by remember { mutableStateOf<String?>(null) }

    var editingItem by remember { mutableStateOf<TodoItem?>(null) }

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
                            createItemInitialListId = uiState.selectedListId ?: uiState.lists.firstOrNull()?.id
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
                TextButton(onClick = { showDeletedLists = true }) {
                    Text("已删除")
                }
                val selectedList = uiState.selectedListId?.let { id -> uiState.lists.firstOrNull { it.id == id } }
                if (selectedList != null) {
                    IconButton(
                        onClick = {
                            managingList = selectedList
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
                    TodoFilterChip(
                        label = "全部",
                        selected = uiState.selectedListId == null,
                        onClick = { viewModel.selectList(null) },
                    )
                }
                items(uiState.lists, key = { it.id }) { list ->
                    TodoFilterChip(
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
                TodoFilterChip(
                    label = "全部",
                    selected = uiState.statusFilter == null,
                    onClick = { viewModel.setStatusFilter(null) },
                )
                TodoFilterChip(
                    label = "未完成",
                    selected = uiState.statusFilter == TodoStatuses.OPEN,
                    onClick = { viewModel.setStatusFilter(TodoStatuses.OPEN) },
                )
                TodoFilterChip(
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
            onDelete = { toDelete -> viewModel.deleteItem(toDelete.id) },
            onDismiss = { editingItem = null },
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
}

@Composable
internal fun TodoFilterChip(
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
