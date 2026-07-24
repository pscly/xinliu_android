@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)

package cc.pscly.onememos.ui.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import cc.pscly.onememos.feature.home.BuildConfig
import cc.pscly.onememos.core.network.MemosUrls
import cc.pscly.onememos.domain.derived.MarkdownDeriver
import cc.pscly.onememos.domain.model.AppSettings
import cc.pscly.onememos.domain.model.CollectionItemType
import cc.pscly.onememos.domain.model.GlobalSyncState
import cc.pscly.onememos.domain.model.ListLayout
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.SwipeAction
import cc.pscly.onememos.domain.model.TodoItem
import cc.pscly.onememos.domain.repository.CollectionsRepository
import cc.pscly.onememos.domain.repository.MemoBrowseScope
import cc.pscly.onememos.domain.repository.MemoRepository
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.repository.TodoRepository
import cc.pscly.onememos.domain.sync.SyncScheduler
import cc.pscly.onememos.domain.sync.SyncStatusMonitor
import cc.pscly.onememos.domain.sync.TodoReminderScheduler
import cc.pscly.onememos.domain.sync.TodoSyncScheduler
import cc.pscly.onememos.domain.tag.TagExtractor
import cc.pscly.onememos.domain.tag.TagStat
import cc.pscly.onememos.domain.tag.TagStats
import cc.pscly.onememos.ui.filter.MemoFilter
import cc.pscly.onememos.ui.filter.MemoFilterStore
import cc.pscly.onememos.ui.filter.TagMatchMode
import cc.pscly.onememos.ui.util.AutoTagLineHider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

data class HomeUiState(
    val serverBase: String? = null,
    val isLoggedIn: Boolean = false,
    val collectionsEnabled: Boolean = false,
    val filter: MemoFilter = MemoFilter(),
    val tagStats: List<TagStat> = emptyList(),
    val regexSearchEnabled: Boolean = false,
    val showTagCountsInFilter: Boolean = true,
    val devAutoTagLineKeywords: String = "__Atags",
    val devShowAutoTagLineInHome: Boolean = false,
    val dev2ShowPublicWorkspaceMemos: Boolean = false,
    val searchError: String? = null,
    /** 列表形态设置（M2.4）：AUTO=宽屏自适应双列；SINGLE=强制单列；DOUBLE=强制双列。 */
    val listLayout: ListLayout = ListLayout.AUTO,
    /** 滑动手势总开关（M2.5）：关闭后列表回退为纯长按多选。 */
    val swipeEnabled: Boolean = true,
    /** 右滑动作（默认加入待办）。 */
    val swipeRightAction: SwipeAction = SwipeAction.ADD_TO_TODO,
    /** 左滑动作（默认收藏）。 */
    val swipeLeftAction: SwipeAction = SwipeAction.FAVORITE,
    /** 列表 Markdown 是否立即渲染（true=始终富预览，避免滚停跳动）。 */
    val listMarkdownImmediateLoad: Boolean = false,
) {
    val isFiltering: Boolean get() = filter.query.isNotBlank() || filter.selectedTags.isNotEmpty()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val memoRepository: MemoRepository,
    private val homeMemoPagingSource: HomeMemoPagingSource,
    private val settingsRepository: SettingsRepository,
    private val filterStore: MemoFilterStore,
    private val syncScheduler: SyncScheduler,
    private val todoRepository: TodoRepository,
    private val todoSyncScheduler: TodoSyncScheduler,
    private val todoReminderScheduler: TodoReminderScheduler,
    private val collectionsRepository: CollectionsRepository,
    syncStatusMonitor: SyncStatusMonitor,
) : ViewModel() {
    private var creatorHintPushed: Boolean = false

    // 主页富预览“粘住”：仅缓存 memo uuid，用于减缓滚动中 rich/plain 切换造成的“跳动”。
    // - limit=0 时完全禁用（清空 + mark no-op）
    // - 仅 benchmark/release 生效（debug 不启用，避免影响日常开发体验）
    private val stickyRichPreviewIds = StickyRichPreviewPolicy(initialLimit = 0)
    private var stickyRichPreviewLimit: Int = 0
    private var stickyRichPreviewBrowseScope: MemoBrowseScope? = null

    // 主页列表滚动位置：用于从详情页返回时恢复到进入前的位置，避免每次回到顶部。
    // 这里不用 SavedStateHandle，是因为 HOME/ARCHIVED 各自是独立 back stack entry（hiltViewModel），且需求主要是“返回”场景。
    private val listPositionStore = HomeListPositionStore()

    private val batchInFlight = AtomicBoolean(false)
    private val _batchBusy = MutableStateFlow(false)
    val batchBusy: StateFlow<Boolean> = _batchBusy.asStateFlow()

    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    private val browseScopeFlow: Flow<MemoBrowseScope> =
        settingsRepository.settings
            .map { settings ->
                val showPublicWorkspace = settings.dev2Unlocked && settings.dev2ShowPublicWorkspaceMemos
                val creator = settings.currentUserCreator.trim()
                computeBrowseScope(
                    showPublicWorkspace = showPublicWorkspace,
                    creator = creator,
                )
            }
            .distinctUntilChanged()

    private val baseActivePaging: Flow<PagingData<Memo>> =
        browseScopeFlow
            .flatMapLatest { scope -> homeMemoPagingSource.active(scope) }
            .cachedIn(viewModelScope)

    private val baseArchivedPaging: Flow<PagingData<Memo>> =
        browseScopeFlow
            .flatMapLatest { scope -> homeMemoPagingSource.archived(scope) }
            .cachedIn(viewModelScope)

    val activePaging: Flow<PagingData<Memo>> =
        combine(
            baseActivePaging,
            filterStore.state,
            settingsRepository.settings.map { it.regexSearchEnabled }.distinctUntilChanged(),
        ) { paging, filter, regexEnabled ->
            applyFilterToPaging(paging, filter, regexEnabled)
        }

    val archivedPaging: Flow<PagingData<Memo>> =
        combine(
            baseArchivedPaging,
            filterStore.state,
            settingsRepository.settings.map { it.regexSearchEnabled }.distinctUntilChanged(),
        ) { paging, filter, regexEnabled ->
            applyFilterToPaging(paging, filter, regexEnabled)
        }

    val uiState: StateFlow<HomeUiState> =
        combine(
            // 回填/同步可能短时间内频繁更新 memo 表：适度 debounce，避免反复重算 TagStats 拉高 CPU。
            memoRepository.observeRecentMemos(limit = 1000).debounce(200),
            settingsRepository.settings,
            filterStore.state,
        ) { recent, settings, filter ->
            Triple(recent, settings, filter)
        }
            .mapLatest { (recent, settings, filter) ->
            updateStickyRichPreviewPolicy(settings)
            val serverBase = MemosUrls.normalizeServerBase(settings.serverUrl)
            val regexEnabled = settings.regexSearchEnabled
            val showTagCountsInFilter = settings.showTagCountsInFilter
            val showPublicWorkspace = settings.dev2Unlocked && settings.dev2ShowPublicWorkspaceMemos
            val loggedIn = settings.token.isNotBlank()
            val collectionsEnabled = settings.loginMode == LoginMode.BACKEND && loggedIn
            val myCreator = settings.currentUserCreator.trim()

            // 启动加速：当“只看自己”模式下还没拿到 currentUserCreator 时，尽量用本地缓存推断出唯一 creator。
            val inferredCreator =
                if (loggedIn && !showPublicWorkspace && myCreator.isBlank()) {
                    val candidates =
                        recent.asSequence()
                            .filter { !it.serverId.isNullOrBlank() }
                            .filter { it.visibility != MemoVisibility.PUBLIC }
                            .mapNotNull { it.creator?.trim()?.takeIf { s -> s.isNotBlank() } }
                            .distinct()
                            .take(2)
                            .toList()
                    if (candidates.size == 1) candidates.first() else ""
                } else {
                    myCreator
                }

            if (loggedIn && !showPublicWorkspace && myCreator.isBlank() && inferredCreator.isNotBlank() && !creatorHintPushed) {
                creatorHintPushed = true
                viewModelScope.launch {
                    settingsRepository.setCurrentUserCreator(inferredCreator)
                }
            }

            fun visible(m: Memo): Boolean {
                // 开发者模式2：可浏览公开/工作区内容（含他人）。
                if (showPublicWorkspace) return true
                // 本地未同步（serverId 为空）一定可见（包括刚创建的工作区笔记）。
                if (m.serverId.isNullOrBlank()) return true
                // 默认只看自己的历史：只展示自己创建的内容，并且不展示公开（PUBLIC）。
                if (m.visibility == MemoVisibility.PUBLIC) return false
                if (inferredCreator.isBlank()) return false
                return m.creator == inferredCreator
            }

            val visibleRecent = recent.filter(::visible)
            val tagStats = withContext(Dispatchers.Default) { TagStats.build(visibleRecent) }
            val searchError = computeSearchError(filter = filter, regexEnabled = regexEnabled)

            HomeUiState(
                serverBase = serverBase,
                isLoggedIn = loggedIn,
                collectionsEnabled = collectionsEnabled,
                filter = filter,
                tagStats = tagStats,
                regexSearchEnabled = regexEnabled,
                showTagCountsInFilter = showTagCountsInFilter,
                devAutoTagLineKeywords = settings.devAutoTagLineKeywords,
                devShowAutoTagLineInHome = settings.devShowAutoTagLineInHome,
                dev2ShowPublicWorkspaceMemos = showPublicWorkspace,
                searchError = searchError,
                listLayout = settings.listLayout,
                swipeEnabled = settings.swipeEnabled,
                swipeRightAction = settings.swipeRightAction,
                swipeLeftAction = settings.swipeLeftAction,
                listMarkdownImmediateLoad = settings.listMarkdownImmediateLoad,
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = HomeUiState(),
            )

    val globalSyncState: StateFlow<GlobalSyncState> =
        syncStatusMonitor.globalState
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = GlobalSyncState(),
            )

    fun isRichPreviewSticky(uuid: String): Boolean = stickyRichPreviewIds.isSticky(uuid)

    fun markRichPreviewSticky(uuid: String) {
        stickyRichPreviewIds.markSticky(uuid)
    }

    private fun updateStickyRichPreviewPolicy(settings: AppSettings) {
        val rawLimit = settings.devHomeRichPreviewStickyLimit
        val enabled = isStickyRichPreviewEnabled(buildType = BuildConfig.BUILD_TYPE, limit = rawLimit)
        val effectiveLimit = if (enabled) rawLimit.coerceAtLeast(0) else 0

        val nextBrowseScope =
            computeBrowseScope(
                showPublicWorkspace = settings.dev2Unlocked && settings.dev2ShowPublicWorkspaceMemos,
                creator = settings.currentUserCreator.trim(),
            )
        if (nextBrowseScope != stickyRichPreviewBrowseScope) {
            stickyRichPreviewBrowseScope = nextBrowseScope
            stickyRichPreviewIds.limit = 0
            stickyRichPreviewLimit = 0
        }

        if (effectiveLimit != stickyRichPreviewLimit) {
            stickyRichPreviewLimit = effectiveLimit
            stickyRichPreviewIds.limit = effectiveLimit
        }
    }

    fun requestSync() {
        syncScheduler.requestSync()
    }

    fun requestBatchArchiveOrRestore(
        mode: HomeScreenMode,
        selectedIds: Set<String>,
    ) {
        val stableIds = selectedIds.asSequence().map { it.trim() }.filter { it.isNotBlank() }.distinct().toList()
        if (stableIds.isEmpty()) return

        if (!batchInFlight.compareAndSet(false, true)) return

        viewModelScope.launch {
            _batchBusy.value = true
            try {
                val action = if (mode == HomeScreenMode.ACTIVE) HomeBatchAction.Archive else HomeBatchAction.Unarchive
                val (success, failed) =
                    withContext(Dispatchers.IO) {
                        var ok = 0
                        var bad = 0
                        stableIds.forEach { uuid ->
                            val exists = runCatching { memoRepository.getMemo(uuid) }.getOrNull()
                            if (exists == null) {
                                bad += 1
                                return@forEach
                            }

                            val succeeded =
                                runCatching {
                                    when (action) {
                                        HomeBatchAction.Archive -> memoRepository.archiveMemo(uuid)
                                        HomeBatchAction.Unarchive -> memoRepository.unarchiveMemo(uuid)
                                    }
                                }.isSuccess

                            if (succeeded) ok += 1 else bad += 1
                        }
                        ok to bad
                    }

                _events.tryEmit(
                    HomeEvent.BatchActionFinished(
                        HomeBatchActionSummary(
                            action = action,
                            successCount = success,
                            failedCount = failed,
                        ),
                    ),
                )
            } finally {
                _batchBusy.value = false
                batchInFlight.set(false)
            }
        }
    }

    fun requestBatchShareMergedText(selectedIds: Set<String>) {
        val stableIds = selectedIds.asSequence().map { it.trim() }.filter { it.isNotBlank() }.distinct().toList()
        if (stableIds.isEmpty()) return

        if (!batchInFlight.compareAndSet(false, true)) return

        viewModelScope.launch {
            _batchBusy.value = true
            try {
                val memos =
                    withContext(Dispatchers.IO) {
                        stableIds.mapNotNull { uuid -> runCatching { memoRepository.getMemo(uuid) }.getOrNull() }
                    }

                val ordered =
                    memos.sortedWith(compareByDescending<Memo> { it.createdAt }.thenBy { it.uuid })

                val shareText = HomeShareTextBuilder.build(ordered)
                _events.tryEmit(HomeEvent.ShareTextReady(text = shareText))
            } finally {
                _batchBusy.value = false
                batchInFlight.set(false)
            }
        }
    }

    fun setQuery(query: String) {
        filterStore.setQuery(query)
    }

    /**
     * 滑动归档撤销：把 memo 从已归档恢复回随笔列表。
     */
    fun unarchiveMemo(uuid: String) {
        val id = uuid.trim()
        if (id.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { memoRepository.unarchiveMemo(id) }
                .onFailure { _events.tryEmit(HomeEvent.SwipeActionMessage("撤销失败，请稍后重试")) }
        }
    }

    /**
     * 执行一次滑动手势动作（M2.5）：
     * - 归档：本地落库后发 [HomeEvent.SwipeArchived]，由 UI 弹“撤销” Snackbar。
     * - 待办/收藏/置顶：执行后发 [HomeEvent.SwipeActionMessage] 轻量反馈。
     * 动作池与术语边界见 ADR 0011（收藏=加入锦囊「收藏」文件夹；永不真删除）。
     */
    fun performSwipeAction(memo: Memo, action: SwipeAction) {
        viewModelScope.launch {
            runCatching {
                when (action) {
                    SwipeAction.ARCHIVE -> {
                        withContext(Dispatchers.IO) { memoRepository.archiveMemo(memo.uuid) }
                        _events.tryEmit(HomeEvent.SwipeArchived(memo.uuid))
                    }
                    SwipeAction.ADD_TO_TODO -> {
                        addMemoToTodo(memo)
                        _events.tryEmit(HomeEvent.SwipeActionMessage("已加入待办"))
                    }
                    SwipeAction.FAVORITE -> {
                        withContext(Dispatchers.IO) { collectionsRepository.addMemoToFavorites(memo.uuid) }
                        _events.tryEmit(HomeEvent.SwipeActionMessage("已收藏"))
                    }
                    SwipeAction.PIN -> {
                        val target = !memo.pinned
                        withContext(Dispatchers.IO) { memoRepository.setPinned(memo.uuid, target) }
                        _events.tryEmit(
                            HomeEvent.SwipeActionMessage(if (target) "已置顶" else "已取消置顶"),
                        )
                    }
                }
            }.onFailure { error ->
                _events.tryEmit(
                    HomeEvent.SwipeActionMessage(
                        when (error) {
                            is CollectionsUnavailableException -> "锦囊不可用，请先使用 Flow Backend 登录"
                            else -> "操作失败，请稍后重试"
                        },
                    ),
                )
            }
        }
    }

    /** 把 memo 的首行预览写入首个待办清单；没有清单时自动建一个默认清单。 */
    private suspend fun addMemoToTodo(memo: Memo) {
        val listId =
            withContext(Dispatchers.IO) {
                val existing =
                    todoRepository.observeLists(includeArchived = false).first()
                        .firstOrNull { it.deletedAt == null }
                        ?.id
                existing ?: todoRepository.createList(name = "待办")
            }

        val title =
            memo.plainPreview.trim().ifBlank { memo.content.trim() }
                .lineSequence().firstOrNull().orEmpty()
                .take(40)
                .ifBlank { "未命名任务" }

        val tzid = runCatching { ZoneId.systemDefault().id }.getOrNull().orEmpty()
        withContext(Dispatchers.IO) {
            todoRepository.createItem(
                TodoItem(
                    id = UUID.randomUUID().toString(),
                    listId = listId,
                    title = title,
                    tzid = tzid,
                ),
            )
        }
        todoSyncScheduler.requestSync()
        todoReminderScheduler.requestReschedule()
    }

    /** 收藏=把 memo 收进锦囊内置「收藏」文件夹（找不到则创建）。 */
    private suspend fun favoriteMemo(memo: Memo) {
        val settings = settingsRepository.settings.first()
        val collectionsAvailable = settings.loginMode == LoginMode.BACKEND && settings.token.isNotBlank()
        if (!collectionsAvailable) throw CollectionsUnavailableException()

        val folderId =
            withContext(Dispatchers.IO) {
                val existing =
                    collectionsRepository.observeAll().first()
                        .firstOrNull {
                            it.itemType == CollectionItemType.FOLDER &&
                                it.deletedAt == null &&
                                !it.localOnly &&
                                it.name.trim() == FAVORITE_FOLDER_NAME
                        }
                        ?.id
                existing
                    ?: collectionsRepository.createFolder(
                        parentId = null,
                        name = FAVORITE_FOLDER_NAME,
                        color = null,
                    )
            }

        val keywords = AutoTagLineHider.parseKeywords(settings.devAutoTagLineKeywords)
        val displayName =
            MarkdownDeriver.plainPreviewSkippingLinesEndingWithKeywords(
                markdown = memo.content,
                keywords = keywords,
                maxChars = 80,
            ).trim().ifBlank { "随笔" }

        withContext(Dispatchers.IO) {
            collectionsRepository.addMemoRef(
                parentId = folderId,
                memo = memo,
                color = null,
                displayName = displayName,
            )
        }
    }

    private class CollectionsUnavailableException : IllegalStateException("collections unavailable")

    fun toggleTag(tag: String) {
        filterStore.toggleTag(tag)
    }

    fun setTagMatchMode(mode: TagMatchMode) {
        filterStore.setTagMatchMode(mode)
    }

    fun setExcludeTags(enabled: Boolean) {
        filterStore.setExcludeTags(enabled)
    }

    fun clearFilter() {
        filterStore.clear()
    }

    fun captureListPosition(index: Int, offset: Int) {
        listPositionStore.capture(index, offset)
    }

    fun peekListPosition(): Pair<Int, Int> = listPositionStore.peek()

    fun pendingRestoreListPosition(): Pair<Int, Int>? = listPositionStore.pending()

    fun markListPositionRestored() {
        listPositionStore.markRestored()
    }
}

/** 滑动「收藏」动作对应的锦囊内置文件夹名（ADR 0011）。 */
private const val FAVORITE_FOLDER_NAME = "收藏"

private fun computeBrowseScope(
    showPublicWorkspace: Boolean,
    creator: String,
): MemoBrowseScope =
    when {
        showPublicWorkspace -> MemoBrowseScope.All
        creator.isNotBlank() -> MemoBrowseScope.Creator(creator)
        else -> MemoBrowseScope.LocalOnly
    }

private fun computeSearchError(
    filter: MemoFilter,
    regexEnabled: Boolean,
): String? {
    if (!regexEnabled) return null
    val queryText = TagExtractor.stripTagTokens(filter.query).trim()
    if (queryText.isBlank()) return null
    val regex = runCatching { Regex(queryText) }.getOrNull()
    return if (regex == null) "正则表达式无效" else null
}

private fun applyFilterToPaging(
    paging: PagingData<Memo>,
    filter: MemoFilter,
    regexEnabled: Boolean,
): PagingData<Memo> {
    if (filter.query.trim().isBlank() && filter.selectedTags.isEmpty()) return paging

    val queryRaw = filter.query
    val queryTags = TagExtractor.extractAll(queryRaw).toSet()
    val effectiveTags = (filter.selectedTags + queryTags).toSet()

    val queryText = TagExtractor.stripTagTokens(queryRaw).trim()
    val regex =
        if (regexEnabled && queryText.isNotBlank()) {
            runCatching { Regex(queryText) }.getOrNull()
        } else {
            null
        }

    val tokens =
        if (!regexEnabled && queryText.isNotBlank()) {
            queryText
                .split(Regex("\\s+"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .take(8)
        } else {
            emptyList()
        }

    val tagMatchMode = filter.tagMatchMode
    val excludeTags = filter.excludeTags

    return paging.filter { memo ->
        val textOk =
            if (queryText.isBlank()) {
                true
            } else if (regexEnabled) {
                // regex 无效时 regex 为空：行为与旧逻辑一致，直接无结果，并在 UI 侧提示错误文案。
                regex?.containsMatchIn(memo.content) == true
            } else {
                // 避免把整段 content lowercase() 产生额外分配，直接做 ignoreCase 匹配即可。
                tokens.all { t -> memo.content.contains(t, ignoreCase = true) }
            }
        if (!textOk) return@filter false

        if (effectiveTags.isEmpty()) return@filter true
        val memoTags =
            (
                if (memo.tags.isNotEmpty()) memo.tags
                else TagExtractor.extractAll(memo.content)
            ).toSet()

        val tagsOk =
            if (excludeTags) {
                effectiveTags.none { it in memoTags }
            } else {
                when (tagMatchMode) {
                    TagMatchMode.OR -> effectiveTags.any { memoTags.contains(it) }
                    TagMatchMode.AND -> effectiveTags.all { memoTags.contains(it) }
                }
            }
        tagsOk
    }
}
