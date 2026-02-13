package cc.pscly.onememos.ui.feature.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.pscly.onememos.domain.model.CollectionItemType
import cc.pscly.onememos.domain.model.CollectionItem
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.repository.CollectionsRepository
import cc.pscly.onememos.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class BreadcrumbSegment(
    val id: String?,
    val label: String,
)

data class FolderOption(
    val id: String,
    val name: String,
    val depth: Int,
)

data class CollectionsUiState(
    val enabled: Boolean = false,
    val currentParentId: String? = null,
    val breadcrumb: List<BreadcrumbSegment> = listOf(BreadcrumbSegment(id = null, label = "根目录")),
    val items: List<CollectionItem> = emptyList(),
    val folderOptions: List<FolderOption> = emptyList(),
    val folderParentById: Map<String, String?> = emptyMap(),
)

@HiltViewModel
class CollectionsViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val collectionsRepository: CollectionsRepository,
) : ViewModel() {
    private val enabledFlow =
        settingsRepository.settings
            .map { s -> s.loginMode == LoginMode.BACKEND && s.token.isNotBlank() }
            .distinctUntilChanged()

    private val _currentParentId = MutableStateFlow<String?>(null)
    val currentParentId: StateFlow<String?> = _currentParentId.asStateFlow()

    private val allItems: StateFlow<List<CollectionItem>> =
        collectionsRepository.observeAll()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    private val children: StateFlow<List<CollectionItem>> =
        _currentParentId
            .flatMapLatest { parentId -> collectionsRepository.observeChildren(parentId = parentId) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    val uiState: StateFlow<CollectionsUiState> =
        combine(enabledFlow, _currentParentId, children, allItems) { ok, parentId, items, all ->
            CollectionsUiState(
                enabled = ok,
                currentParentId = parentId,
                breadcrumb = buildBreadcrumb(currentParentId = parentId, all = all),
                items = items,
                folderOptions = buildFolderOptions(items = all),
                folderParentById = buildFolderParentById(items = all),
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = CollectionsUiState(),
            )

    fun enterFolder(folderId: String) {
        _currentParentId.value = folderId
    }

    fun goToParent(parentId: String?) {
        _currentParentId.value = parentId
    }

    fun navigateUp() {
        val current = _currentParentId.value ?: return
        val byId = allItems.value.associateBy { it.id }
        _currentParentId.value = byId[current]?.parentId
    }

    suspend fun createFolder(
        name: String,
        color: String?,
    ): String =
        collectionsRepository.createFolder(
            parentId = _currentParentId.value,
            name = name,
            color = color,
        )

    suspend fun rename(
        id: String,
        name: String,
    ) {
        collectionsRepository.rename(id = id, name = name)
    }

    suspend fun recolor(
        ids: List<String>,
        color: String?,
    ) {
        collectionsRepository.recolor(ids = ids, color = color)
    }

    suspend fun move(
        ids: List<String>,
        targetParentId: String?,
    ) {
        collectionsRepository.move(ids = ids, targetParentId = targetParentId)
    }

    suspend fun reorder(
        orderedIds: List<String>,
    ) {
        collectionsRepository.reorder(parentId = _currentParentId.value, orderedIds = orderedIds)
    }

    suspend fun batchDelete(ids: List<String>) {
        collectionsRepository.batchDelete(ids)
    }

    private fun buildBreadcrumb(
        currentParentId: String?,
        all: List<CollectionItem>,
    ): List<BreadcrumbSegment> {
        val root = BreadcrumbSegment(id = null, label = "根目录")
        if (currentParentId.isNullOrBlank()) return listOf(root)

        val byId = all.associateBy { it.id }
        val chain = ArrayList<CollectionItem>(6)
        val visited = HashSet<String>(8)

        var id: String? = currentParentId
        while (!id.isNullOrBlank() && visited.add(id)) {
            val item = byId[id] ?: break
            chain.add(item)
            id = item.parentId
        }

        chain.reverse()
        val result = ArrayList<BreadcrumbSegment>(1 + chain.size)
        result.add(root)
        for (node in chain) {
            result.add(
                BreadcrumbSegment(
                    id = node.id,
                    label = node.name.trim().ifBlank { "（无标题）" },
                ),
            )
        }
        return result
    }

    private fun buildFolderOptions(items: List<CollectionItem>): List<FolderOption> {
        val folders =
            items
                .asSequence()
                .filter { it.itemType == CollectionItemType.FOLDER }
                .filter { it.deletedAt == null }
                .filter { !it.localOnly }
                .toList()

        if (folders.isEmpty()) return emptyList()

        val byId = folders.associateBy { it.id }
        val children = HashMap<String?, MutableList<CollectionItem>>()
        for (f in folders) {
            val p = f.parentId?.takeIf { byId.containsKey(it) }
            children.getOrPut(p) { mutableListOf() }.add(f)
        }

        val comparator =
            compareBy<CollectionItem> { it.sortOrder }
                .thenByDescending { it.clientUpdatedAtMs }
                .thenBy { it.id }
        children.values.forEach { list -> list.sortWith(comparator) }

        val out = ArrayList<FolderOption>(folders.size)
        fun walk(parentId: String?, depth: Int) {
            val list = children[parentId] ?: return
            for (f in list) {
                out.add(
                    FolderOption(
                        id = f.id,
                        name = f.name.trim().ifBlank { "（无标题）" },
                        depth = depth,
                    ),
                )
                walk(parentId = f.id, depth = depth + 1)
            }
        }
        walk(parentId = null, depth = 0)
        return out
    }

    private fun buildFolderParentById(items: List<CollectionItem>): Map<String, String?> {
        val folders =
            items
                .asSequence()
                .filter { it.itemType == CollectionItemType.FOLDER }
                .filter { it.deletedAt == null }
                .filter { !it.localOnly }
                .toList()
        if (folders.isEmpty()) return emptyMap()

        val byId = folders.associateBy { it.id }
        return folders.associate { f ->
            val p = f.parentId?.takeIf { byId.containsKey(it) }
            f.id to p
        }
    }
}
