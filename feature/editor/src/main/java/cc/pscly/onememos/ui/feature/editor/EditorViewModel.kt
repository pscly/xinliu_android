package cc.pscly.onememos.ui.feature.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import cc.pscly.onememos.core.network.MemosUrls
import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.QuickInsertTimeFormat
import cc.pscly.onememos.domain.model.SyncStatus
import cc.pscly.onememos.domain.repository.CacheRepository
import cc.pscly.onememos.domain.repository.MemoRepository
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.sync.SyncScheduler
import cc.pscly.onememos.domain.tag.TagStat
import cc.pscly.onememos.domain.tag.TagStats
import cc.pscly.onememos.ui.filter.MemoFilter
import cc.pscly.onememos.ui.filter.MemoFilterStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import cc.pscly.onememos.ui.util.AutoTagLineHider
import cc.pscly.onememos.ui.util.DateTimeFormatter
import cc.pscly.onememos.ui.util.QuickInsertTimeFormatter
import javax.inject.Inject

data class EditorAttachmentUi(
    val key: String,
    val localUri: String?,
    val cacheUri: String?,
    val remoteName: String?,
    val filename: String?,
    val mimeType: String?,
    val createdAt: Long,
)

data class EditorUiState(
    val uuid: String? = null,
    val serverId: String? = null,
    val syncStatus: SyncStatus? = null,
    val serverState: String? = null,
    val content: TextFieldValue = TextFieldValue(""),
    // 用于标签提取：即使“自动标签元数据行”被隐藏，也能从原始内容中解析出标签并用于筛选。
    val tagSourceText: String = "",
    val hiddenAutoTagLines: List<String> = emptyList(),
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val attachments: List<EditorAttachmentUi> = emptyList(),
    val canEdit: Boolean = true,
    val attachmentsEditable: Boolean = true,
    val isSaving: Boolean = false,
    val loadError: String? = null,
    val lastSyncError: String? = null,
    val serverBase: String? = null,
    val allTagStats: List<TagStat> = emptyList(),
    // 体验优化：输入联想路径避免每次从 allTagStats 再 map 出 List<String>
    val allTagNames: List<String> = emptyList(),
    val tagSuggestions: List<String> = emptyList(),
    val showTagCountsInFilter: Boolean = true,
    val quickInsertTimeEnabled: Boolean = false,
    val quickInsertTimeFormat: QuickInsertTimeFormat = QuickInsertTimeFormat.FULL_DATETIME,
    val sealStampDurationMs: Int = 600,
    val devAutoTagLineKeywords: String = "__Atags",
    val devShowAutoTagLineInView: Boolean = false,
    val devShowAutoTagLineInEdit: Boolean = false,
)

sealed interface EditorEvent {
    data object Saved : EditorEvent
    data object Archived : EditorEvent
    data object Unarchived : EditorEvent
    data object FilterApplied : EditorEvent
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val memoRepository: MemoRepository,
    private val settingsRepository: SettingsRepository,
    private val cacheRepository: CacheRepository,
    private val syncScheduler: SyncScheduler,
    private val filterStore: MemoFilterStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val uuidArg: String? =
        savedStateHandle.get<String>("uuid")?.let { raw ->
            // 与 Routes.editor() 的 Uri.encode 对应：这里解码回原始 uuid（可能包含 "/"）。
            runCatching { Uri.decode(raw) }.getOrDefault(raw)
        }

    private val _uiState = MutableStateFlow(EditorUiState(uuid = uuidArg))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<EditorEvent> = _events.asSharedFlow()

    private val cachingRemoteNames = mutableSetOf<String>()
    private var creatorHintPushed: Boolean = false

    // 体验优化：标签联想在输入路径会被高频触发；用“首字符分桶”缩小扫描范围。
    // 注意：bucket 内顺序保持为 TagStats.build() 的排序（按数量倒序），保证联想仍然“常用优先”。
    private var tagNamesByFirstChar: Map<Char, List<String>> = emptyMap()

    private fun buildTagNameIndex(names: List<String>): Map<Char, List<String>> {
        if (names.isEmpty()) return emptyMap()
        val out = LinkedHashMap<Char, MutableList<String>>()
        for (name in names) {
            val key = name.firstOrNull()?.lowercaseChar() ?: continue
            out.getOrPut(key) { mutableListOf() }.add(name)
        }
        return out
    }

    // 只去掉行尾空格/Tab，不吞掉换行（否则用户很难插入换行/空行）。
    private fun trimTrailingSpacesOnly(text: String): String =
        text.trimEnd { it == ' ' || it == '\t' }

    init {
        viewModelScope.launch {
            settingsRepository.settings.collectLatest { settings ->
                val base = MemosUrls.normalizeServerBase(settings.serverUrl)
                val keywordsRaw = settings.devAutoTagLineKeywords
                val showInView = settings.devShowAutoTagLineInView
                val showInEdit = settings.devShowAutoTagLineInEdit
                _uiState.update {
                    it.copy(
                        serverBase = base,
                        showTagCountsInFilter = settings.showTagCountsInFilter,
                        quickInsertTimeEnabled = settings.quickInsertTimeEnabled,
                        quickInsertTimeFormat = settings.quickInsertTimeFormat,
                        sealStampDurationMs = settings.sealStampDurationMs,
                        devAutoTagLineKeywords = keywordsRaw,
                        devShowAutoTagLineInView = showInView,
                        devShowAutoTagLineInEdit = showInEdit,
                    )
                }
                // 设置变更时尽量“就地切换”显示策略：
                // - 查看态：直接从 tagSourceText 重新生成展示文本
                // - 编辑态：在不丢用户当前输入的前提下，来回切换显示/隐藏
                applyAutoTagLineVisibility()
                // serverBase 变化时，尝试对当前 memo 的远端图片做持久缓存预取（不阻塞 UI）。
                schedulePrefetchAttachmentCache()
            }
        }

        viewModelScope.launch {
            combine(
                memoRepository.observeAllMemos(),
                settingsRepository.settings,
            ) { all, settings ->
                val loggedIn = settings.token.isNotBlank()
                val showPublicWorkspace = settings.dev2Unlocked && settings.dev2ShowPublicWorkspaceMemos
                val myCreator = settings.currentUserCreator.trim()

                val inferredCreator =
                    if (loggedIn && !showPublicWorkspace && myCreator.isBlank()) {
                        val candidates =
                            all.asSequence()
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
                    if (showPublicWorkspace) return true
                    if (m.serverId.isNullOrBlank()) return true
                    if (m.visibility == MemoVisibility.PUBLIC) return false
                    if (inferredCreator.isBlank()) return false
                    return m.creator == inferredCreator
                }

                all.filter(::visible)
            }.collectLatest { visibleAll ->
                // 体验优化：标签统计在数据量大时会明显吃 CPU；放到 Default 线程，避免影响主线程输入/动画。
                val stats = withContext(Dispatchers.Default) { TagStats.build(visibleAll) }
                val names = stats.map { s -> s.name }
                tagNamesByFirstChar = buildTagNameIndex(names)
                _uiState.update {
                    it.copy(
                        allTagStats = stats,
                        allTagNames = names,
                    )
                }
            }
        }

        if (!uuidArg.isNullOrBlank()) {
            viewModelScope.launch {
                val memo = memoRepository.getMemo(uuidArg)
                if (memo == null) {
                    _uiState.update { it.copy(loadError = "记录不存在或已被删除") }
                    return@launch
                }

                val attachmentsUi =
                    memo.attachments.map { a ->
                        EditorAttachmentUi(
                            key = a.remoteName ?: a.localUri ?: "attachment_${a.id}",
                            localUri = a.localUri,
                            cacheUri = a.cacheUri,
                            remoteName = a.remoteName,
                            filename = a.filename,
                            mimeType = a.mimeType,
                            createdAt = a.createdAt,
                        )
                    }

                val text = memo.content
                val keywords = AutoTagLineHider.parseKeywords(_uiState.value.devAutoTagLineKeywords)
                val split = AutoTagLineHider.split(text, keywords)
                _uiState.update {
                    // 只要 serverId 为空，就说明仍未上传到服务端：必须允许离线反复编辑（避免 syncStatus 误标导致只读）。
                    val localOnly = memo.serverId.isNullOrBlank()
                    val canEdit = localOnly || (memo.syncStatus != SyncStatus.SYNCED && memo.syncStatus != SyncStatus.SYNCING)
                    val showLine =
                        if (canEdit) {
                            it.devShowAutoTagLineInEdit
                        } else {
                            it.devShowAutoTagLineInView
                        }
                    val displayText = if (showLine) text else split.visibleText
                    it.copy(
                        uuid = memo.uuid,
                        serverId = memo.serverId,
                        syncStatus = memo.syncStatus,
                        serverState = memo.serverState.name,
                        createdAt = memo.createdAt,
                        updatedAt = memo.updatedAt,
                        content = TextFieldValue(text = displayText, selection = TextRange(displayText.length)),
                        tagSourceText = text,
                        hiddenAutoTagLines = split.hiddenLines,
                        attachments = attachmentsUi,
                        // 同步中禁止编辑，避免“后台刚写回 SYNCED 覆盖用户新改动”等竞态。
                        canEdit = canEdit,
                        attachmentsEditable = canEdit,
                        lastSyncError = memo.lastSyncError,
                        loadError = null,
                    )
                }

                // 打开详情页即开始预取附件持久缓存：用户“看图”的概率最高，且附件数量通常有限。
                schedulePrefetchAttachmentCache()
            }
        }
    }

    private fun schedulePrefetchAttachmentCache() {
        val state = _uiState.value
        val memoUuid = state.uuid ?: return
        val base = state.serverBase ?: return

        val targets =
            state.attachments.filter { a ->
                a.mimeType?.startsWith("image/") == true &&
                    !a.remoteName.isNullOrBlank() &&
                    !a.filename.isNullOrBlank()
            }

        for (a in targets) {
            val remoteName = a.remoteName ?: continue
            val filename = a.filename ?: continue
            val cacheUri = a.cacheUri

            if (!cachingRemoteNames.add(remoteName)) continue

            viewModelScope.launch {
                try {
                    // 体验优化：File.exists() 属于磁盘 IO；放到 IO 线程，避免影响编辑页的主线程响应。
                    val alreadyCached = withContext(Dispatchers.IO) { isUsableFileUri(cacheUri) }
                    if (alreadyCached) return@launch

                    val cached =
                        runCatching {
                            cacheRepository.ensureImageAttachmentCached(
                                serverBase = base,
                                memoUuid = memoUuid,
                                remoteName = remoteName,
                                filename = filename,
                            )
                        }.getOrNull()
                    if (!cached.isNullOrBlank()) {
                        _uiState.update { s ->
                            s.copy(
                                attachments =
                                    s.attachments.map { item ->
                                        if (item.remoteName == remoteName) item.copy(cacheUri = cached) else item
                                    },
                            )
                        }
                    }
                } finally {
                    cachingRemoteNames.remove(remoteName)
                }
            }
        }
    }

    private fun isUsableFileUri(uri: String?): Boolean {
        if (uri.isNullOrBlank()) return false
        return runCatching {
            val parsed = android.net.Uri.parse(uri)
            if (!parsed.scheme.equals("file", ignoreCase = true)) return@runCatching false
            val path = parsed.path ?: return@runCatching false
            java.io.File(path).exists()
        }.getOrDefault(false)
    }

    fun insertCurrentTimeStamp() {
        val state = _uiState.value
        if (!state.canEdit || state.isSaving) return

        val now = System.currentTimeMillis()
        val stampLine = QuickInsertTimeFormatter.buildQuotedLine(now, state.quickInsertTimeFormat)
        val next = insertLineAtSelection(value = state.content, line = stampLine)
        onContentChange(next)
    }

    private fun insertLineAtSelection(
        value: TextFieldValue,
        line: String,
    ): TextFieldValue {
        val text = value.text
        val rawStart = value.selection.start
        val rawEnd = value.selection.end
        val start = minOf(rawStart, rawEnd).coerceIn(0, text.length)
        val end = maxOf(rawStart, rawEnd).coerceIn(0, text.length)

        val needsLeadingNewLine = start > 0 && text.getOrNull(start - 1) != '\n'
        val insertText =
            buildString {
                if (needsLeadingNewLine) append('\n')
                append(line)
                append("\n\n")
            }

        val nextText = text.replaceRange(start, end, insertText)
        val nextCursor = start + insertText.length
        return TextFieldValue(text = nextText, selection = TextRange(nextCursor))
    }

    fun onContentChange(value: TextFieldValue) {
        val state = _uiState.value
        if (!state.canEdit) return

        val keywords = AutoTagLineHider.parseKeywords(state.devAutoTagLineKeywords)
        val showLine = state.devShowAutoTagLineInEdit
        val (nextValue, nextHidden, nextSource) =
            if (showLine) {
                val split = AutoTagLineHider.split(value.text, keywords)
                Triple(
                    value,
                    split.hiddenLines,
                    value.text,
                )
            } else {
                // 隐藏模式：把“元数据行”从输入中剔除，同时把剔除出来的行追加到 hidden 列表中，保存时再拼回去。
                val split = AutoTagLineHider.split(value.text, keywords)
                val visible = split.visibleText
                val hidden = (state.hiddenAutoTagLines + split.hiddenLines).distinct()

                // 关键：如果无需改写文本，就直接使用 IME 给到的 TextFieldValue。
                // 否则会在每次输入（尤其是语音/组合文本）时丢失 composition，触发 IME 反复提交导致“重复输入”。
                val next =
                    if (visible == value.text) {
                        value
                    } else {
                        val sel = value.selection.start.coerceIn(0, visible.length)
                        TextFieldValue(text = visible, selection = TextRange(sel))
                    }

                Triple(next, hidden, AutoTagLineHider.merge(visible, hidden))
            }

        val prefix = TagCompletion.findTagPrefix(nextValue)
        val suggestions =
            if (prefix == null) {
                emptyList()
            } else {
                val allTags = state.allTagNames
                val p = prefix.prefix
                if (p.isBlank()) {
                    allTags.take(10)
                } else {
                    val bucket = tagNamesByFirstChar[p.first().lowercaseChar()] ?: allTags
                    bucket
                        .asSequence()
                        .filter { it.startsWith(p, ignoreCase = true) }
                        .take(10)
                        .toList()
                }
            }

        _uiState.update {
            it.copy(
                content = nextValue,
                hiddenAutoTagLines = nextHidden,
                tagSourceText = nextSource,
                tagSuggestions = suggestions,
            )
        }
    }

    fun completeTag(tag: String) {
        val value = _uiState.value.content
        val prefix = TagCompletion.findTagPrefix(value) ?: return
        val next = TagCompletion.applySuggestion(value = value, tag = tag, prefix = prefix)
        _uiState.update { it.copy(content = next, tagSuggestions = emptyList()) }
    }

    fun addResource(uri: String) {
        _uiState.update { state ->
            if (!state.attachmentsEditable) return@update state
            val exists = state.attachments.any { it.localUri == uri }
            if (exists) {
                state
            } else {
                val now = System.currentTimeMillis()
                state.copy(
                    attachments =
                        state.attachments +
                            EditorAttachmentUi(
                                key = uri,
                                localUri = uri,
                                cacheUri = null,
                                remoteName = null,
                                filename = null,
                                mimeType = null,
                                createdAt = now,
                            ),
                )
            }
        }
    }

    fun enableEdit() {
        _uiState.update { it.copy(canEdit = true, attachmentsEditable = true) }
        applyAutoTagLineVisibility()
    }

    fun removeAttachment(key: String) {
        _uiState.update { state ->
            if (!state.attachmentsEditable) return@update state
            state.copy(attachments = state.attachments.filterNot { it.key == key })
        }
    }

    fun moveAttachment(key: String, delta: Int) {
        _uiState.update { state ->
            if (!state.attachmentsEditable) return@update state
            val from = state.attachments.indexOfFirst { it.key == key }
            if (from < 0) return@update state
            val to = (from + delta).coerceIn(0, state.attachments.lastIndex)
            if (to == from) return@update state
            val list = state.attachments.toMutableList()
            val item = list.removeAt(from)
            list.add(to, item)
            state.copy(attachments = list)
        }
    }

    fun retrySync() {
        syncScheduler.requestSync()
    }

    fun archive() {
        val uuid = _uiState.value.uuid ?: return
        viewModelScope.launch {
            memoRepository.archiveMemo(uuid)
            _events.tryEmit(EditorEvent.Archived)
        }
    }

    fun unarchive() {
        val uuid = _uiState.value.uuid ?: return
        viewModelScope.launch {
            memoRepository.unarchiveMemo(uuid)
            _events.tryEmit(EditorEvent.Unarchived)
        }
    }

    fun applyFilter(filter: MemoFilter) {
        filterStore.setFilter(filter)
        _events.tryEmit(EditorEvent.FilterApplied)
    }

    fun save() {
        val state = _uiState.value
        if (!state.canEdit) return
        if (state.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val keywords = AutoTagLineHider.parseKeywords(state.devAutoTagLineKeywords)
                val raw =
                    if (state.devShowAutoTagLineInEdit) {
                        // 展示全部时，保存用户所见即所得；仅去掉行尾空格，保留换行/空行。
                        trimTrailingSpacesOnly(state.content.text)
                    } else {
                        // 隐藏模式：保存时把隐藏行拼回去，保证内容不丢。
                        // 同时再跑一遍 split，捕获用户可能在正文里输入的“元数据行”。
                        val split = AutoTagLineHider.split(state.content.text, keywords)
                        val hidden = state.hiddenAutoTagLines + split.hiddenLines
                        trimTrailingSpacesOnly(AutoTagLineHider.merge(split.visibleText, hidden))
                    }
                val localUris = state.attachments.mapNotNull { it.localUri }
                if (raw.isBlank() && localUris.isEmpty()) {
                    _uiState.update { it.copy(isSaving = false) }
                    return@launch
                }

                val uuid = state.uuid
                if (uuid.isNullOrBlank()) {
                    memoRepository.createLocalMemo(
                        content = raw,
                        resourceUris = localUris,
                    )
                } else {
                    memoRepository.updateMemoDraft(
                        uuid = uuid,
                        content = raw,
                        attachments =
                            state.attachments.map { a ->
                                cc.pscly.onememos.domain.model.MemoAttachmentDraft(
                                    localUri = a.localUri,
                                    remoteName = a.remoteName,
                                    filename = a.filename,
                                    mimeType = a.mimeType,
                                    createdAt = a.createdAt,
                                )
                            },
                    )
                }
                _events.tryEmit(EditorEvent.Saved)
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun applyAutoTagLineVisibility() {
        val state = _uiState.value
        val keywords = AutoTagLineHider.parseKeywords(state.devAutoTagLineKeywords)

        if (state.canEdit) {
            val show = state.devShowAutoTagLineInEdit
            val visibleText = state.content.text
            val hidden = state.hiddenAutoTagLines
            val nextText =
                if (show) {
                    val merged = AutoTagLineHider.merge(visibleText, hidden)
                    val split = AutoTagLineHider.split(merged, keywords)
                    _uiState.update { it.copy(hiddenAutoTagLines = split.hiddenLines, tagSourceText = merged) }
                    merged
                } else {
                    val split = AutoTagLineHider.split(visibleText, keywords)
                    // 处于“显示 -> 隐藏”的切换时，需要把当前文本里匹配到的行收进 hidden，避免丢变更。
                    val nextHidden = hidden + split.hiddenLines
                    _uiState.update { it.copy(hiddenAutoTagLines = nextHidden, tagSourceText = AutoTagLineHider.merge(split.visibleText, nextHidden)) }
                    split.visibleText
                }

            _uiState.update { s ->
                val sel = s.content.selection.start.coerceIn(0, nextText.length)
                s.copy(content = TextFieldValue(text = nextText, selection = TextRange(sel)))
            }
        } else {
            val show = state.devShowAutoTagLineInView
            val split = AutoTagLineHider.split(state.tagSourceText, keywords)
            val display = if (show) state.tagSourceText else split.visibleText
            _uiState.update { s ->
                val sel = s.content.selection.start.coerceIn(0, display.length)
                s.copy(
                    content = TextFieldValue(text = display, selection = TextRange(sel)),
                    hiddenAutoTagLines = split.hiddenLines,
                )
            }
        }
    }
}
