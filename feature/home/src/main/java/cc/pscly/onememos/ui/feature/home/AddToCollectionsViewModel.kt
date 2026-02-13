package cc.pscly.onememos.ui.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.pscly.onememos.domain.model.CollectionItem
import cc.pscly.onememos.domain.model.CollectionItemType
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.repository.CollectionsRepository
import cc.pscly.onememos.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class FolderOption(
    val id: String,
    val name: String,
    val depth: Int,
)

@HiltViewModel
class AddToCollectionsViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val collectionsRepository: CollectionsRepository,
) : ViewModel() {
    val enabled: StateFlow<Boolean> =
        settingsRepository.settings
            .map { s -> s.loginMode == LoginMode.BACKEND && s.token.isNotBlank() }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false,
            )

    val folders: StateFlow<List<FolderOption>> =
        collectionsRepository.observeAll()
            .map(::buildFolderOptions)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    suspend fun createFolder(
        parentId: String?,
        name: String,
    ): String =
        collectionsRepository.createFolder(
            parentId = parentId,
            name = name,
            color = null,
        )

    suspend fun addMemoRef(
        parentId: String?,
        memo: Memo,
    ): String =
        collectionsRepository.addMemoRef(
            parentId = parentId,
            memo = memo,
            color = null,
            displayName = null,
        )

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

        val result = ArrayList<FolderOption>(folders.size)
        val visited = HashSet<String>(folders.size * 2)

        fun walk(parentId: String?, depth: Int) {
            val list = children[parentId].orEmpty()
            for (child in list) {
                if (!visited.add(child.id)) continue
                result.add(
                    FolderOption(
                        id = child.id,
                        name = child.name.ifBlank { "未命名文件夹" },
                        depth = depth,
                    ),
                )
                walk(parentId = child.id, depth = depth + 1)
            }
        }

        walk(parentId = null, depth = 0)
        return result
    }
}
