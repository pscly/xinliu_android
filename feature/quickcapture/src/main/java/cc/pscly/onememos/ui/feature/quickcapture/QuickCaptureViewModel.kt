package cc.pscly.onememos.ui.feature.quickcapture

import android.content.Context
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.model.QuickInsertTimeFormat
import cc.pscly.onememos.domain.repository.MemoRepository
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.ui.feature.quickcapture.draft.DraftAutoSaver
import cc.pscly.onememos.ui.feature.quickcapture.draft.QuickCaptureDraft
import cc.pscly.onememos.ui.feature.quickcapture.draft.QuickCaptureDraftAttachment
import cc.pscly.onememos.ui.feature.quickcapture.draft.QuickCaptureDraftStore
import cc.pscly.onememos.ui.util.AutoTagLineHider
import cc.pscly.onememos.ui.util.QuickInsertTimeFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

data class QuickCaptureHistoryItem(
    val uuid: String,
    val preview: String,
    val updatedAt: Long,
)

data class QuickCaptureUiState(
    val content: TextFieldValue = TextFieldValue(""),
    val isSaving: Boolean = false,
    val error: String? = null,
    val editingUuid: String? = null,
    val hiddenAutoTagLines: List<String> = emptyList(),
    val history: List<QuickCaptureHistoryItem> = emptyList(),
    val quickInsertTimeEnabled: Boolean = false,
    val quickInsertTimeFormat: QuickInsertTimeFormat = QuickInsertTimeFormat.FULL_DATETIME,
    val draftBannerVisible: Boolean = false,
    val draftOverwriteDialogVisible: Boolean = false,
)

sealed interface QuickCaptureEvent {
    data object Saved : QuickCaptureEvent
}

@HiltViewModel
class QuickCaptureViewModel @Inject constructor(
    private val memoRepository: MemoRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    // 只去掉行尾空格/Tab，不吞掉换行（否则用户很难插入换行/空行）。
    private fun trimTrailingSpacesOnly(text: String): String =
        text.trimEnd { it == ' ' || it == '\t' }

    private val defaultAutoTagKeywords: List<String> = AutoTagLineHider.parseKeywords(null)

    private val _uiState = MutableStateFlow(QuickCaptureUiState())
    val uiState: StateFlow<QuickCaptureUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<QuickCaptureEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<QuickCaptureEvent> = _events.asSharedFlow()

    private val draftStore: QuickCaptureDraftStore by lazy { QuickCaptureDraftStore(appContext) }
    private val draftAutoSaver: DraftAutoSaver by lazy {
        DraftAutoSaver(
            scope = viewModelScope,
            save = { text, attachments ->
                saveDraftFromAutoSaver(text = text, attachments = attachments)
            },
        )
    }

    private val draftWriteEnabled = AtomicBoolean(true)
    private var pendingOverwriteContent: TextFieldValue? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings.collectLatest { settings ->
                _uiState.update {
                    it.copy(
                        quickInsertTimeEnabled = settings.quickInsertTimeEnabled,
                        quickInsertTimeFormat = settings.quickInsertTimeFormat,
                    )
                }
            }
        }

        refreshDraftBannerOnStart()
    }

    fun updateContent(value: TextFieldValue) {
        val state = _uiState.value
        if (isDraftEnabled(state) && shouldAskOverwriteConfirm(state, incomingText = value.text)) {
            pendingOverwriteContent = value
            _uiState.update { it.copy(draftOverwriteDialogVisible = true, error = null) }
            return
        }

        _uiState.update { it.copy(content = value, error = null) }
        if (isDraftEnabled(state) && draftWriteEnabled.get()) {
            draftAutoSaver.onTextChanged(value.text)
        }
    }

    fun refreshHistory(limit: Int = 20) {
        viewModelScope.launch {
            runCatching { memoRepository.listRecentEditedActiveMemos(limit = limit) }
                .onSuccess { memos ->
                    _uiState.update { it.copy(history = memos.map { memo -> memo.toHistoryItem() }, error = null) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message?.take(200) ?: "加载历史失败") }
                }
        }
    }

    fun insertCurrentTimeStamp() {
        val state = _uiState.value
        if (state.isSaving) return

        val now = System.currentTimeMillis()
        val stampLine = QuickInsertTimeFormatter.buildQuotedLine(now, state.quickInsertTimeFormat)
        val next = insertLineAtSelection(value = state.content, line = stampLine)
        updateContent(next)
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

    fun editPrevious() {
        val state = _uiState.value
        if (state.isSaving) return

        // 避免误触“续写”覆盖了当前正在写的内容。
        if (state.editingUuid.isNullOrBlank() && state.content.text.isNotBlank()) {
            _uiState.update { it.copy(error = "当前已有内容，先点“盖”保存或“取消”退出。") }
            return
        }

        viewModelScope.launch {
            runCatching { memoRepository.listRecentEditedActiveMemos(limit = 1).firstOrNull() }
                .onSuccess { memo ->
                    if (memo == null) {
                        _uiState.update { it.copy(error = "暂无可续写记录") }
                    } else {
                        applyMemoForEdit(memo)
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message?.take(200) ?: "续写失败") }
                }
        }
    }

    fun loadForEdit(uuid: String) {
        val state = _uiState.value
        if (state.isSaving) return

        viewModelScope.launch {
            runCatching { memoRepository.getMemo(uuid) }
                .onSuccess { memo ->
                    if (memo == null) {
                        _uiState.update { it.copy(error = "记录不存在或已被删除") }
                    } else {
                        applyMemoForEdit(memo)
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message?.take(200) ?: "加载失败") }
                }
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return

        val visible = trimTrailingSpacesOnly(state.content.text)
        val uuid = state.editingUuid

        if (uuid.isNullOrBlank()) {
            if (visible.isBlank()) {
                // 空内容直接关闭，避免误触后还要手动返回
                _events.tryEmit(QuickCaptureEvent.Saved)
                return
            }
        } else {
            if (visible.isBlank()) {
                _uiState.update { it.copy(error = "内容不能为空") }
                return
            }
        }

        val content =
            if (!uuid.isNullOrBlank() && state.hiddenAutoTagLines.isNotEmpty()) {
                AutoTagLineHider.merge(visibleText = visible, hiddenLines = state.hiddenAutoTagLines)
            } else {
                visible
            }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                if (uuid.isNullOrBlank()) {
                    memoRepository.createLocalMemo(
                        content = content,
                        resourceUris = emptyList(),
                    )
                    if (isDraftEnabled(state)) {
                        clearDraftAfterLocalMemoSaved()
                    }
                } else {
                    memoRepository.updateMemoContent(uuid = uuid, content = content)
                }
                _events.tryEmit(QuickCaptureEvent.Saved)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message?.take(200) ?: "保存失败") }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun applyMemoForEdit(memo: Memo) {
        disableDraft()
        val split = AutoTagLineHider.split(text = memo.content, keywords = defaultAutoTagKeywords)
        val visible = split.visibleText
        _uiState.update {
            it.copy(
                content = TextFieldValue(text = visible, selection = TextRange(visible.length)),
                editingUuid = memo.uuid,
                hiddenAutoTagLines = split.hiddenLines,
                error = null,
            )
        }
    }

    fun restoreDraft() {
        val state = _uiState.value
        if (!isDraftEnabled(state)) return

        viewModelScope.launch {
            val draft = runCatching { draftStore.loadDraft() }.getOrNull()
            if (draft == null) {
                refreshDraftBannerOnStart()
                return@launch
            }

            val text = draft.text
            draftWriteEnabled.set(true)
            _uiState.update {
                it.copy(
                    content = TextFieldValue(text = text, selection = TextRange(text.length)),
                    draftBannerVisible = false,
                    draftOverwriteDialogVisible = false,
                    error = null,
                )
            }
            draftAutoSaver.onTextChanged(text)
        }
    }

    fun clearDraft() {
        val state = _uiState.value
        if (!isDraftEnabled(state)) return

        viewModelScope.launch {
            runCatching { draftStore.clearDraft() }
            draftWriteEnabled.set(true)
            _uiState.update { it.copy(draftBannerVisible = false, draftOverwriteDialogVisible = false) }
            draftAutoSaver.onTextChanged("")
        }
    }

    fun confirmOverwriteAndApplyPending() {
        val pendingText = pendingOverwriteContent
        pendingOverwriteContent = null

        draftWriteEnabled.set(true)
        _uiState.update { it.copy(draftBannerVisible = false, draftOverwriteDialogVisible = false, error = null) }

        if (pendingText != null) {
            _uiState.update { it.copy(content = pendingText, error = null) }
            draftAutoSaver.onTextChanged(pendingText.text)
            viewModelScope.launch { flushDraftNowSuspend() }
        }
    }

    fun dismissOverwriteDialog() {
        pendingOverwriteContent = null
        _uiState.update { it.copy(draftOverwriteDialogVisible = false) }
    }

    fun flushDraftNow() {
        viewModelScope.launch { flushDraftNowSuspend() }
    }

    override fun onCleared() {
        runCatching {
            runBlocking {
                kotlinx.coroutines.withContext(NonCancellable) {
                    flushDraftNowSuspend()
                }
            }
        }
        super.onCleared()
    }

    private fun refreshDraftBannerOnStart() {
        val state = _uiState.value
        if (!isDraftEnabled(state)) {
            draftWriteEnabled.set(true)
            _uiState.update { it.copy(draftBannerVisible = false, draftOverwriteDialogVisible = false) }
            return
        }

        val dir = File(appContext.noBackupFilesDir, "quick_capture_draft")
        val exists = File(dir, "draft.json").isFile || File(dir, "draft.json.bak").isFile
        draftWriteEnabled.set(!exists)
        _uiState.update { it.copy(draftBannerVisible = exists, draftOverwriteDialogVisible = false) }
    }

    private fun disableDraft() {
        pendingOverwriteContent = null
        draftWriteEnabled.set(true)
        draftAutoSaver.cancel()
        _uiState.update { it.copy(draftBannerVisible = false, draftOverwriteDialogVisible = false) }
    }

    private fun isDraftEnabled(state: QuickCaptureUiState): Boolean = state.editingUuid.isNullOrBlank()

    private fun shouldAskOverwriteConfirm(
        state: QuickCaptureUiState,
        incomingText: String? = null,
    ): Boolean {
        if (!isDraftEnabled(state)) return false
        if (draftWriteEnabled.get()) return false
        if (!state.draftBannerVisible) return false
        if (state.draftOverwriteDialogVisible) return true
        if (incomingText != null && incomingText != state.content.text) return true
        return false
    }

    private suspend fun flushDraftNowSuspend() {
        val state = _uiState.value
        if (!isDraftEnabled(state)) return
        if (!draftWriteEnabled.get()) return
        draftAutoSaver.flushNow()
    }

    private suspend fun saveDraftFromAutoSaver(
        text: String,
        attachments: List<QuickCaptureDraftAttachment>,
    ) {
        val state = _uiState.value
        if (!isDraftEnabled(state)) return
        if (!draftWriteEnabled.get()) return

        if (text.isBlank() && attachments.isEmpty()) {
            runCatching { draftStore.clearDraft() }
            _uiState.update { it.copy(draftBannerVisible = false) }
            return
        }

        draftStore.saveDraft(
            QuickCaptureDraft(
                schemaVersion = 1,
                updatedAt = 0L,
                text = text,
                attachments = attachments,
            ),
        )
    }

    private suspend fun clearDraftAfterLocalMemoSaved() {
        runCatching { draftStore.clearDraft() }
        draftWriteEnabled.set(true)
        _uiState.update { it.copy(draftBannerVisible = false) }
        draftAutoSaver.onTextChanged("")
    }

    private fun Memo.toHistoryItem(): QuickCaptureHistoryItem {
        val visible = AutoTagLineHider.hideFast(text = content, keywords = defaultAutoTagKeywords)
        val preview = previewText(visible)
        return QuickCaptureHistoryItem(uuid = uuid, preview = preview, updatedAt = updatedAt)
    }

    private fun previewText(text: String): String {
        val normalized = text.replace("\r\n", "\n")
        val compact =
            normalized
                .replace("\n", " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        if (compact.isBlank()) return "（空白）"
        return compact.take(80)
    }
}
