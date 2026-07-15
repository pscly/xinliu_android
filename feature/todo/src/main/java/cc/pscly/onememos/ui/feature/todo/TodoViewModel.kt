@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package cc.pscly.onememos.ui.feature.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.model.TodoItem
import cc.pscly.onememos.domain.model.TodoList
import cc.pscly.onememos.domain.model.TodoStatuses
import cc.pscly.onememos.domain.model.TodoOccurrence
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.util.OwnerKeyProvider
import cc.pscly.onememos.domain.repository.TodoRepository
import cc.pscly.onememos.navigation.TodoItemKey
import cc.pscly.onememos.domain.sync.TodoReminderScheduler
import cc.pscly.onememos.domain.sync.TodoReminderTestScheduler
import cc.pscly.onememos.domain.sync.TodoSyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

data class TodoUiState(
    val enabled: Boolean = false,
    val includeArchivedLists: Boolean = false,
    val lists: List<TodoList> = emptyList(),
    val selectedListId: String? = null,
    val statusFilter: String? = null,
    val tagFilter: String = "",
    val items: List<TodoItem> = emptyList(),
)

@HiltViewModel
class TodoViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    private val todoSyncScheduler: TodoSyncScheduler,
    private val todoReminderScheduler: TodoReminderScheduler,
    private val todoReminderTestScheduler: TodoReminderTestScheduler,
    private val settingsRepository: SettingsRepository,
    private val ownerKeyProvider: OwnerKeyProvider,
) : ViewModel() {
    private val selectedListId = MutableStateFlow<String?>(null)
    private val targetKey = MutableStateFlow<TodoItemKey?>(null)
    private var boundKey: TodoItemKey? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val todoItemTargetState: StateFlow<TodoItemTargetState> =
        combine(targetKey, settingsRepository.settings) { key, settings ->
            key to settings
        }.flatMapLatest { (key, settings) ->
            if (key == null) {
                kotlinx.coroutines.flow.flowOf<TodoItemTargetState>(TodoItemTargetState.Idle)
            } else {
                kotlinx.coroutines.flow.flow<TodoItemTargetState> {
                    emit(TodoItemTargetState.Loading(key))
                    val enabled = settings.loginMode == LoginMode.BACKEND && settings.token.isNotBlank()
                    if (!enabled) {
                        emit(
                            TodoItemTargetState.Unavailable(
                                key = key,
                                reason = TodoItemUnavailableReason.DISABLED,
                            ),
                        )
                        return@flow
                    }
                    val owner = ownerKeyProvider.currentOwnerKeyOrNull()
                    if (owner.isNullOrBlank() || owner != key.expectedOwnerKey) {
                        emit(
                            TodoItemTargetState.Unavailable(
                                key = key,
                                reason = TodoItemUnavailableReason.NOT_FOUND_OR_ACCOUNT_MISMATCH,
                            ),
                        )
                        return@flow
                    }
                    val item = todoRepository.getItem(key.itemId)
                    when {
                        item == null ->
                            emit(
                                TodoItemTargetState.Unavailable(
                                    key = key,
                                    reason = TodoItemUnavailableReason.NOT_FOUND_OR_ACCOUNT_MISMATCH,
                                ),
                            )
                        !item.deletedAt.isNullOrBlank() ->
                            emit(
                                TodoItemTargetState.Unavailable(
                                    key = key,
                                    reason = TodoItemUnavailableReason.DELETED,
                                ),
                            )
                        else -> emit(TodoItemTargetState.Ready(key = key, item = item))
                    }
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TodoItemTargetState.Idle,
        )

    fun bind(key: TodoItemKey) {
        if (boundKey == key) return
        boundKey = key
        targetKey.value = key
    }

    fun clearTarget() {
        boundKey = null
        targetKey.value = null
    }
    private val statusFilter = MutableStateFlow<String?>(null)
    private val tagFilter = MutableStateFlow("")
    private val includeArchivedLists = MutableStateFlow(false)

    private val enabledFlow =
        settingsRepository.settings
            .map { s -> s.loginMode == LoginMode.BACKEND && s.token.isNotBlank() }
            .distinctUntilChanged()

    private val deletedListsFlow =
        enabledFlow.flatMapLatest { enabled ->
            if (!enabled) {
                flowOf(emptyList())
            } else {
                todoRepository.observeLists(includeArchived = true, includeDeleted = true)
                    .map { lists -> lists.filter { it.deletedAt != null } }
            }
        }

    private val deletedItemsFlow =
        enabledFlow.flatMapLatest { enabled ->
            if (!enabled) {
                flowOf(emptyList())
            } else {
                todoRepository.observeItems(
                    listId = null,
                    status = null,
                    tag = null,
                    includeArchivedLists = true,
                    includeDeleted = true,
                )
                    .map { items ->
                        items
                            .filter { it.deletedAt != null }
                            .sortedByDescending { it.deletedAt.orEmpty() }
                    }
            }
        }

    private val listsFlow =
        includeArchivedLists.flatMapLatest { include ->
            todoRepository.observeLists(includeArchived = include)
        }

    private val itemsFlow =
        combine(
            selectedListId,
            statusFilter,
            tagFilter,
            includeArchivedLists,
        ) { listId, status, tag, includeArchived ->
            ItemQuery(
                listId = listId,
                status = status,
                tag = tag,
                includeArchivedLists = includeArchived,
            )
        }
            .flatMapLatest { q ->
                todoRepository.observeItems(
                    listId = q.listId,
                    status = q.status,
                    tag = q.tag.takeIf { it.isNotBlank() },
                    includeArchivedLists = q.includeArchivedLists,
                    includeDeleted = false,
                )
            }

    val hasAnyReminders: StateFlow<Boolean> =
        enabledFlow.flatMapLatest { enabled ->
            if (!enabled) {
                flowOf(false)
            } else {
                todoRepository.observeItems(
                    listId = null,
                    status = null,
                    tag = null,
                    includeArchivedLists = true,
                    includeDeleted = false,
                )
                    .map { items ->
                        items.any { it.remindersJson.trim().isNotBlank() && it.remindersJson.trim() != "[]" }
                    }
            }
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false,
            )

    private val filtersFlow =
        combine(
            selectedListId,
            statusFilter,
            tagFilter,
        ) { listId, status, tag ->
            Filters(
                selectedListId = listId,
                statusFilter = status,
                tagFilter = tag,
            )
        }

    val uiState: StateFlow<TodoUiState> =
        combine(
            enabledFlow,
            includeArchivedLists,
            listsFlow,
            filtersFlow,
            itemsFlow,
        ) { enabled, includeArchived, lists, filters, items ->
            val effectiveSelected =
                filters.selectedListId?.takeIf { id -> lists.any { it.id == id } }
            TodoUiState(
                enabled = enabled,
                includeArchivedLists = includeArchived,
                lists = lists.sortedWith(compareBy<TodoList> { it.sortOrder }.thenBy { it.clientUpdatedAtMs }.thenBy { it.id }),
                selectedListId = effectiveSelected,
                statusFilter = filters.statusFilter,
                tagFilter = filters.tagFilter,
                items = items,
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = TodoUiState(),
            )

    fun setIncludeArchivedLists(include: Boolean) {
        includeArchivedLists.value = include
    }

    fun selectList(id: String?) {
        selectedListId.value = id
    }

    fun setStatusFilter(status: String?) {
        statusFilter.value = status?.trim()?.takeIf { it.isNotBlank() }
    }

    fun setTagFilter(tag: String) {
        tagFilter.value = tag.trim()
    }

    fun requestSync() {
        todoSyncScheduler.requestSync()
    }

    fun requestReminderReschedule() {
        todoReminderScheduler.requestReschedule()
    }

    fun requestTestReminder(
        itemId: String,
        dueAtLocal: String,
    ) {
        todoReminderTestScheduler.requestTest(
            itemId = itemId,
            dueAtLocal = dueAtLocal,
            delaySeconds = 10,
        )
    }

    fun observeDeletedLists(): Flow<List<TodoList>> = deletedListsFlow

    fun observeDeletedItems(): Flow<List<TodoItem>> = deletedItemsFlow

    fun observeOccurrences(
        itemId: String,
        includeDeleted: Boolean = false,
    ): Flow<List<TodoOccurrence>> = todoRepository.observeOccurrences(itemId = itemId, includeDeleted = includeDeleted)

    fun createList(name: String) {
        viewModelScope.launch {
            todoRepository.createList(name = name.trim(), color = null)
            todoSyncScheduler.requestSync()
            todoReminderScheduler.requestReschedule()
        }
    }

    fun deleteList(listId: String) {
        viewModelScope.launch {
            todoRepository.deleteList(listId)
            todoSyncScheduler.requestSync()
            todoReminderScheduler.requestReschedule()
        }
    }

    fun restoreList(listId: String) {
        viewModelScope.launch {
            todoRepository.restoreList(listId)
            todoSyncScheduler.requestSync()
            todoReminderScheduler.requestReschedule()
        }
    }

    fun createQuickItem(
        title: String,
        listId: String,
    ) {
        val state = uiState.value
        if (listId.isBlank()) return
        if (state.lists.none { it.id == listId }) return
        val tzid = runCatching { ZoneId.systemDefault().id }.getOrNull().orEmpty()
        val item =
            TodoItem(
                id = UUID.randomUUID().toString(),
                listId = listId,
                title = title.trim().ifBlank { "未命名任务" },
                note = "",
                status = TodoStatuses.OPEN,
                priority = 0,
                sortOrder = 0,
                tags = emptyList(),
                remindersJson = "[]",
                dueAtLocal = null,
                completedAtLocal = null,
                isRecurring = false,
                rrule = null,
                dtstartLocal = null,
                tzid = tzid,
            )
        viewModelScope.launch {
            todoRepository.createItem(item)
            todoSyncScheduler.requestSync()
            todoReminderScheduler.requestReschedule()
        }
    }

    fun updateItem(item: TodoItem) {
        viewModelScope.launch {
            todoRepository.updateItem(item)
            todoSyncScheduler.requestSync()
            todoReminderScheduler.requestReschedule()
        }
    }

    fun updateList(list: TodoList) {
        viewModelScope.launch {
            todoRepository.updateList(list)
            todoSyncScheduler.requestSync()
            todoReminderScheduler.requestReschedule()
        }
    }

    fun toggleDone(item: TodoItem, done: Boolean) {
        viewModelScope.launch {
            if (item.isRecurring) {
                todoRepository.completeNextOccurrence(item.id)
            } else {
                todoRepository.setItemDone(item.id, done)
            }
            todoSyncScheduler.requestSync()
            todoReminderScheduler.requestReschedule()
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            todoRepository.deleteItem(itemId)
            todoSyncScheduler.requestSync()
            todoReminderScheduler.requestReschedule()
        }
    }

    fun restoreItem(itemId: String) {
        viewModelScope.launch {
            todoRepository.restoreItem(itemId)
            todoSyncScheduler.requestSync()
            todoReminderScheduler.requestReschedule()
        }
    }

    private data class ItemQuery(
        val listId: String?,
        val status: String?,
        val tag: String,
        val includeArchivedLists: Boolean,
    )

    private data class Filters(
        val selectedListId: String?,
        val statusFilter: String?,
        val tagFilter: String,
    )
}
