package cc.pscly.onememos.ui.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.pscly.onememos.domain.derived.MarkdownDeriver
import cc.pscly.onememos.domain.model.CollectionItem
import cc.pscly.onememos.domain.model.CollectionItemType
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.repository.CollectionsRepository
import cc.pscly.onememos.domain.repository.MemoRepository
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.repository.TodoRepository
import cc.pscly.onememos.domain.util.MemoRelationOutcome
import cc.pscly.onememos.domain.util.MemoRelationToggle
import cc.pscly.onememos.domain.util.XinliuRelationMarker
import cc.pscly.onememos.domain.util.favoriteToast
import cc.pscly.onememos.ui.util.AutoTagLineHider
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

/** 加入锦囊结果：普通文件夹 vs 内置收藏开关（ADR 0013）。 */
data class AddToCollectionsResult(
    val ok: Boolean,
    /** 给用户的 Toast 文案 */
    val message: String,
    val successCount: Int = 0,
    val failedCount: Int = 0,
)

@HiltViewModel
class AddToCollectionsViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val collectionsRepository: CollectionsRepository,
    memoRepository: MemoRepository,
    todoRepository: TodoRepository,
) : ViewModel() {
    private val relationToggle =
        MemoRelationToggle(
            memoRepository = memoRepository,
            todoRepository = todoRepository,
            collectionsRepository = collectionsRepository,
        )

    /**
     * 关键字来源必须来自 settings（而不是固定默认值），同时做等价缓存：
     * - 避免每次 buildDisplayName() 都重复 parse
     * - 使用 SharingStarted.Eagerly，避免无人 collect 时 value 永远停留在 initialValue
     */
    private val autoTagLineKeywords: StateFlow<List<String>> =
        settingsRepository.settings
            .map { s -> s.devAutoTagLineKeywords }
            .distinctUntilChanged()
            .map { raw -> AutoTagLineHider.parseKeywords(raw) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = AutoTagLineHider.parseKeywords(null),
            )

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

    /**
     * 放入文件夹。若目标为内置「收藏」，走收藏切换（可强取消）；否则普通加引用。
     */
    suspend fun addMemoRef(
        parentId: String?,
        memo: Memo,
    ): AddToCollectionsResult {
        val folderId = parentId?.trim().orEmpty()
        if (folderId.isNotBlank() && collectionsRepository.isFavoritesFolder(folderId)) {
            return runCatching {
                val outcome = relationToggle.toggleFavorite(memo.uuid)
                AddToCollectionsResult(ok = true, message = outcome.favoriteToast(), successCount = 1)
            }.getOrElse {
                AddToCollectionsResult(ok = false, message = "操作失败，请稍后重试", failedCount = 1)
            }
        }
        val id =
            collectionsRepository.addMemoRef(
                parentId = parentId,
                memo = memo,
                color = null,
                displayName = buildDisplayName(memo),
            )
        return if (id.isBlank()) {
            AddToCollectionsResult(ok = false, message = "放入失败：请确认已登录 Flow Backend")
        } else {
            val pending = memo.serverId.isNullOrBlank()
            AddToCollectionsResult(
                ok = true,
                message = if (pending) "已放入锦囊（待同步）" else "已放入锦囊",
                successCount = 1,
            )
        }
    }

    suspend fun addMemoRefs(
        parentId: String?,
        memos: List<Memo>,
    ): AddToCollectionsResult {
        if (memos.isEmpty()) {
            return AddToCollectionsResult(ok = false, message = "未选择随笔")
        }
        val folderId = parentId?.trim().orEmpty()
        if (folderId.isNotBlank() && collectionsRepository.isFavoritesFolder(folderId)) {
            var success = 0
            var failed = 0
            var last: MemoRelationOutcome? = null
            for (memo in memos) {
                runCatching {
                    last = relationToggle.toggleFavorite(memo.uuid)
                    success++
                }.onFailure { failed++ }
            }
            return if (success == 0) {
                AddToCollectionsResult(
                    ok = false,
                    message = "操作失败，请稍后重试",
                    successCount = 0,
                    failedCount = failed,
                )
            } else {
                val msg =
                    when {
                        memos.size == 1 && last != null -> last.favoriteToast()
                        failed == 0 -> "已处理 ${memos.size} 条收藏"
                        else -> "成功 $success 条，失败 $failed 条"
                    }
                AddToCollectionsResult(
                    ok = true,
                    message = msg,
                    successCount = success,
                    failedCount = failed,
                )
            }
        }
        var success = 0
        for (memo in memos) {
            val id =
                collectionsRepository.addMemoRef(
                    parentId = parentId,
                    memo = memo,
                    color = null,
                    displayName = buildDisplayName(memo),
                )
            if (id.isNotBlank()) success++
        }
        val failed = memos.size - success
        return if (success == 0) {
            AddToCollectionsResult(
                ok = false,
                message = "放入失败：请确认已登录 Flow Backend",
                successCount = 0,
                failedCount = failed,
            )
        } else if (failed == 0) {
            val hasLocalOnly = memos.any { it.serverId.isNullOrBlank() }
            AddToCollectionsResult(
                ok = true,
                message = if (hasLocalOnly) "已放入锦囊（待同步）" else "已放入锦囊",
                successCount = success,
                failedCount = 0,
            )
        } else {
            AddToCollectionsResult(
                ok = true,
                message = "成功 $success 条，失败 $failed 条",
                successCount = success,
                failedCount = failed,
            )
        }
    }

    private fun buildDisplayName(memo: Memo): String {
        val stripped = XinliuRelationMarker.stripAllForRead(memo.content)
        val candidate =
            MarkdownDeriver.plainPreviewSkippingLinesEndingWithKeywords(
                markdown = stripped,
                keywords = autoTagLineKeywords.value,
                maxChars = 80,
            )
                .trim()

        return if (candidate.isBlank()) {
            "随笔"
        } else {
            candidate
        }
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
