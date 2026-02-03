@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)

package cc.pscly.onememos.ui.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import cc.pscly.onememos.feature.home.BuildConfig
import cc.pscly.onememos.core.network.MemosUrls
import cc.pscly.onememos.domain.model.AppSettings
import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.repository.MemoRepository
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.sync.SyncScheduler
import cc.pscly.onememos.domain.tag.TagExtractor
import cc.pscly.onememos.domain.tag.TagStat
import cc.pscly.onememos.domain.tag.TagStats
import cc.pscly.onememos.ui.filter.MemoFilter
import cc.pscly.onememos.ui.filter.MemoFilterStore
import cc.pscly.onememos.ui.filter.TagMatchMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class HomeUiState(
    val serverBase: String? = null,
    val isLoggedIn: Boolean = false,
    val filter: MemoFilter = MemoFilter(),
    val tagStats: List<TagStat> = emptyList(),
    val regexSearchEnabled: Boolean = false,
    val showTagCountsInFilter: Boolean = true,
    val devAutoTagLineKeywords: String = "__Atags",
    val devShowAutoTagLineInHome: Boolean = false,
    val dev2ShowPublicWorkspaceMemos: Boolean = false,
    val searchError: String? = null,
) {
    val isFiltering: Boolean get() = filter.query.isNotBlank() || filter.selectedTags.isNotEmpty()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val memoRepository: MemoRepository,
    private val settingsRepository: SettingsRepository,
    private val filterStore: MemoFilterStore,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {
    private var creatorHintPushed: Boolean = false

    // 主页富预览“粘住”：仅缓存 memo uuid，用于减缓滚动中 rich/plain 切换造成的“跳动”。
    // - limit=0 时完全禁用（清空 + mark no-op）
    // - 仅 benchmark/release 生效（debug 不启用，避免影响日常开发体验）
    private val stickyRichPreviewIds = StickyRichPreviewPolicy(initialLimit = 0)
    private var stickyRichPreviewLimit: Int = 0
    private var stickyRichPreviewBrowseScope: MemoRepository.BrowseScope? = null

    // 主页列表滚动位置：用于从详情页返回时恢复到进入前的位置，避免每次回到顶部。
    // 这里不用 SavedStateHandle，是因为 HOME/ARCHIVED 各自是独立 back stack entry（hiltViewModel），且需求主要是“返回”场景。
    private val listPositionStore = HomeListPositionStore()

    private val browseScopeFlow: Flow<MemoRepository.BrowseScope> =
        settingsRepository.settings
            .map { settings ->
                val loggedIn = settings.token.isNotBlank()
                val showPublicWorkspace = settings.dev2Unlocked && settings.dev2ShowPublicWorkspaceMemos
                val creator = settings.currentUserCreator.trim()
                computeBrowseScope(
                    loggedIn = loggedIn,
                    showPublicWorkspace = showPublicWorkspace,
                    creator = creator,
                )
            }
            .distinctUntilChanged()

    private val baseActivePaging: Flow<PagingData<Memo>> =
        browseScopeFlow
            .flatMapLatest { scope -> memoRepository.pagingMemos(scope) }
            .cachedIn(viewModelScope)

    private val baseArchivedPaging: Flow<PagingData<Memo>> =
        browseScopeFlow
            .flatMapLatest { scope -> memoRepository.pagingArchivedMemos(scope) }
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
                // 未登录：离线期间的记录都属于“本机”，全部可见。
                if (!loggedIn) return true
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
                filter = filter,
                tagStats = tagStats,
                regexSearchEnabled = regexEnabled,
                showTagCountsInFilter = showTagCountsInFilter,
                devAutoTagLineKeywords = settings.devAutoTagLineKeywords,
                devShowAutoTagLineInHome = settings.devShowAutoTagLineInHome,
                dev2ShowPublicWorkspaceMemos = showPublicWorkspace,
                searchError = searchError,
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = HomeUiState(),
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
                loggedIn = settings.token.isNotBlank(),
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

    fun setQuery(query: String) {
        filterStore.setQuery(query)
    }

    fun toggleTag(tag: String) {
        filterStore.toggleTag(tag)
    }

    fun setTagMatchMode(mode: TagMatchMode) {
        filterStore.setTagMatchMode(mode)
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

private fun computeBrowseScope(
    loggedIn: Boolean,
    showPublicWorkspace: Boolean,
    creator: String,
): MemoRepository.BrowseScope =
    when {
        !loggedIn -> MemoRepository.BrowseScope.All
        showPublicWorkspace -> MemoRepository.BrowseScope.All
        creator.isNotBlank() -> MemoRepository.BrowseScope.Creator(creator)
        else -> MemoRepository.BrowseScope.LocalOnly
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
        when (tagMatchMode) {
            TagMatchMode.OR -> effectiveTags.any { memoTags.contains(it) }
            TagMatchMode.AND -> effectiveTags.all { memoTags.contains(it) }
        }
    }
}
