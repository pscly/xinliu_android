package cc.pscly.onememos.ui.feature.quickcapture

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.repository.MemoRepository
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.ui.util.AutoTagLineHider
import cc.pscly.onememos.ui.util.DateTimeFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
)

sealed interface QuickCaptureEvent {
    data object Saved : QuickCaptureEvent
}

@HiltViewModel
class QuickCaptureViewModel @Inject constructor(
    private val memoRepository: MemoRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    // 只去掉行尾空格/Tab，不吞掉换行（否则用户很难插入换行/空行）。
    private fun trimTrailingSpacesOnly(text: String): String =
        text.trimEnd { it == ' ' || it == '\t' }

    private val defaultAutoTagKeywords: List<String> = AutoTagLineHider.parseKeywords(null)

    private val _uiState = MutableStateFlow(QuickCaptureUiState())
    val uiState: StateFlow<QuickCaptureUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<QuickCaptureEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<QuickCaptureEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collectLatest { settings ->
                _uiState.update { it.copy(quickInsertTimeEnabled = settings.quickInsertTimeEnabled) }
            }
        }
    }

    fun updateContent(value: TextFieldValue) {
        _uiState.update { it.copy(content = value, error = null) }
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
        val stampLine = "> ${DateTimeFormatter.formatHms(now)}"
        val next = insertLineAtSelection(value = state.content, line = stampLine)
        _uiState.update { it.copy(content = next, error = null) }
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
                append('\n')
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
