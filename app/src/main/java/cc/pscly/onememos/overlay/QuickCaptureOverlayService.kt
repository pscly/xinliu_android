package cc.pscly.onememos.overlay

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.model.MemoAttachmentDraft
import cc.pscly.onememos.domain.model.QuickInsertTimeFormat
import cc.pscly.onememos.domain.model.SyncStatus
import cc.pscly.onememos.domain.repository.MemoRepository
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.ui.feature.quickcapture.draft.DraftAutoSaver
import cc.pscly.onememos.ui.feature.quickcapture.draft.QuickCaptureDraft
import cc.pscly.onememos.ui.feature.quickcapture.draft.QuickCaptureDraftAttachment
import cc.pscly.onememos.ui.feature.quickcapture.draft.QuickCaptureDraftStore
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.component.ScrollTextField
import cc.pscly.onememos.ui.component.SealButton
import cc.pscly.onememos.ui.component.SealStampOverlay
import cc.pscly.onememos.ui.theme.InkShape
import cc.pscly.onememos.ui.theme.InkSpacing
import cc.pscly.onememos.ui.theme.OneMemosTheme
import cc.pscly.onememos.ui.theme.OneMemosThemeConfig
import cc.pscly.onememos.ui.util.AutoTagLineHider
import cc.pscly.onememos.ui.util.DateTimeFormatter
import cc.pscly.onememos.ui.util.QuickInsertTimeFormatter
import cc.pscly.onememos.ui.util.rememberOneMemosHaptics
import coil.compose.AsyncImage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

internal enum class QuickCaptureOverlaySource {
    NORMAL,
    SCREENSHOT,
}

internal data class QuickCaptureOverlayAttachmentUi(
    val key: String,
    val localUri: String?,
    val cacheUri: String?,
    val remoteName: String?,
    val filename: String?,
    val mimeType: String?,
    val createdAt: Long,
)

internal data class QuickCaptureOverlayUiState(
    val content: TextFieldValue = TextFieldValue(""),
    val isSaving: Boolean = false,
    val error: String? = null,
    val editingUuid: String? = null,
    val hiddenAutoTagLines: List<String> = emptyList(),
    val history: List<QuickCaptureOverlayHistoryItem> = emptyList(),
    val attachments: List<QuickCaptureOverlayAttachmentUi> = emptyList(),
    val attachmentsEditable: Boolean = true,
    val quickInsertTimeEnabled: Boolean = false,
    val quickInsertTimeFormat: QuickInsertTimeFormat = QuickInsertTimeFormat.FULL_DATETIME,
    val source: QuickCaptureOverlaySource = QuickCaptureOverlaySource.NORMAL,
    val draftBannerVisible: Boolean = false,
    val draftOverwriteDialogVisible: Boolean = false,
)

internal data class QuickCaptureOverlayHistoryItem(
    val uuid: String,
    val preview: String,
    val updatedAt: Long,
)

internal sealed interface QuickCaptureOverlayEvent {
    data object Saved : QuickCaptureOverlayEvent
}

@AndroidEntryPoint
class QuickCaptureOverlayService : Service() {
    companion object {
        const val EXTRA_ATTACHMENTS = "cc.pscly.onememos.extra.ATTACHMENTS"
        const val EXTRA_PREFILL_TEXT = "cc.pscly.onememos.extra.PREFILL_TEXT"

        const val ACTION_ADD_ATTACHMENTS = "cc.pscly.onememos.action.QUICK_CAPTURE_OVERLAY_ADD_ATTACHMENTS"
        const val ACTION_SCREENSHOT_CAPTURE = "cc.pscly.onememos.action.QUICK_CAPTURE_OVERLAY_SCREENSHOT_CAPTURE"
    }

    @Inject
    lateinit var memoRepository: MemoRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var windowManager: WindowManager? = null
    private var rootView: View? = null
    private var viewTreeOwners: OverlayComposeOwners? = null

    private val defaultAutoTagKeywords: List<String> = AutoTagLineHider.parseKeywords(null)

    private val _uiState = MutableStateFlow(QuickCaptureOverlayUiState())
    private val uiState: StateFlow<QuickCaptureOverlayUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<QuickCaptureOverlayEvent>(extraBufferCapacity = 1)
    private val events: SharedFlow<QuickCaptureOverlayEvent> = _events.asSharedFlow()

    private val imeBottomPx = MutableStateFlow(0)

    private val draftStore: QuickCaptureDraftStore by lazy { QuickCaptureDraftStore(applicationContext) }
    private val draftAutoSaver: DraftAutoSaver by lazy {
        DraftAutoSaver(
            scope = serviceScope,
            save = { text, attachments ->
                saveDraftFromAutoSaver(text = text, attachments = attachments)
            },
        )
    }

    private var draftWriteEnabled: Boolean = true
    private var pendingOverwriteContent: TextFieldValue? = null
    private var pendingOverwriteAttachmentUris: List<String> = emptyList()
    private var pendingOverwriteReplaceAttachments: Boolean = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)

        serviceScope.launch {
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

    override fun onDestroy() {
        runCatching {
            kotlinx.coroutines.runBlocking {
                withContext(NonCancellable) {
                    flushDraftNowSuspend()
                }
            }
        }
        removeOverlay()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        serviceScope.launch { flushDraftNowSuspend() }
        super.onTaskRemoved(rootIntent)
    }

    override fun onTrimMemory(level: Int) {
        serviceScope.launch { flushDraftNowSuspend() }
        super.onTrimMemory(level)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "未授予悬浮窗权限，无法开启悬浮记录", Toast.LENGTH_SHORT).show()
            stopSelf()
            return START_NOT_STICKY
        }

        val action = intent?.action
        if (action == ACTION_ADD_ATTACHMENTS) {
            val attachments = intent.getStringArrayListExtra(EXTRA_ATTACHMENTS).orEmpty()
            addAttachmentsFromExternal(uris = attachments, replace = false)
            showOverlayIfNeeded()
            return START_NOT_STICKY
        }

        val attachments = intent?.getStringArrayListExtra(EXTRA_ATTACHMENTS).orEmpty()
        val prefillText = intent?.getStringExtra(EXTRA_PREFILL_TEXT).orEmpty()
        val hasExplicitPayload = attachments.isNotEmpty() || prefillText.isNotBlank() || action == ACTION_SCREENSHOT_CAPTURE

        // 已在悬浮窗编辑时重复触发：如果没有明确 payload，就不要打断用户（避免清空附件/输入）。
        if (!hasExplicitPayload && rootView != null) {
            showOverlayIfNeeded()
            return START_NOT_STICKY
        }

        val source =
            if (action == ACTION_SCREENSHOT_CAPTURE || attachments.isNotEmpty()) {
                QuickCaptureOverlaySource.SCREENSHOT
            } else {
                QuickCaptureOverlaySource.NORMAL
            }

        if (attachments.isNotEmpty()) {
            addAttachmentsFromExternal(uris = attachments, replace = true)
        } else {
            _uiState.update { it.copy(attachments = emptyList(), attachmentsEditable = true, source = source, error = null) }
        }

        // 截图/附件场景：强制进入“新建记录”模式，避免误覆盖上一条。
        if (attachments.isNotEmpty()) {
            _uiState.update { it.copy(editingUuid = null, hiddenAutoTagLines = emptyList()) }
        }
        _uiState.update { it.copy(source = source, error = null) }

        if (prefillText.isNotBlank()) {
            val state = _uiState.value
            if (state.content.text.isBlank()) {
                updateContent(TextFieldValue(text = prefillText, selection = TextRange(prefillText.length)))
            }
        }

        showOverlayIfNeeded()
        return START_NOT_STICKY
    }

    private fun newLocalAttachmentUi(
        uri: String,
        createdAt: Long = System.currentTimeMillis(),
    ): QuickCaptureOverlayAttachmentUi? {
        val trimmed = uri.trim()
        if (trimmed.isBlank()) return null
        return QuickCaptureOverlayAttachmentUi(
            key = trimmed,
            localUri = trimmed,
            cacheUri = null,
            remoteName = null,
            filename = null,
            mimeType = null,
            createdAt = createdAt,
        )
    }

    private fun addLocalAttachments(uris: List<String>) {
        if (uris.isEmpty()) return

        val createdAt = System.currentTimeMillis()
        _uiState.update { state ->
            if (!state.attachmentsEditable) return@update state

            val seen = state.attachments.mapNotNull { it.localUri }.toMutableSet()
            val extra = mutableListOf<QuickCaptureOverlayAttachmentUi>()
            for (uri in uris) {
                val item = newLocalAttachmentUi(uri = uri, createdAt = createdAt) ?: continue
                val localUri = item.localUri
                if (localUri != null && !seen.add(localUri)) continue
                extra.add(item)
            }
            if (extra.isEmpty()) state.copy(error = null) else state.copy(attachments = state.attachments + extra, error = null)
        }
    }

    private fun removeAttachment(key: String) {
        _uiState.update { state ->
            if (!state.attachmentsEditable) return@update state
            state.copy(attachments = state.attachments.filterNot { it.key == key }, error = null)
        }

        val state = _uiState.value
        if (isDraftEnabled(state) && draftWriteEnabled) {
            draftAutoSaver.onAttachmentsChanged(state.attachments.toDraftAttachments())
        }
    }

    private fun startPickImagesActivity() {
        runCatching {
            startActivity(
                Intent(this, QuickCaptureOverlayPickImagesActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { e ->
            Toast.makeText(this, e.message?.take(200) ?: "无法打开系统选图器", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showOverlayIfNeeded() {
        if (rootView != null) return

        val wm = windowManager ?: run {
            Toast.makeText(this, "系统窗口服务不可用", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        val owners = OverlayComposeOwners().apply { onCreate() }
        val view =
            ComposeView(this).apply {
                // Service 场景不会自动提供 ViewTree owners；缺失会导致 ComposeView attach 时崩溃，表现为“点了没反应”。
                setTag(androidx.lifecycle.runtime.R.id.view_tree_lifecycle_owner, owners)
                setTag(androidx.lifecycle.viewmodel.R.id.view_tree_view_model_store_owner, owners)
                setTag(androidx.savedstate.R.id.view_tree_saved_state_registry_owner, owners)

                ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
                    val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
                    val bottom =
                        if (imeVisible) {
                            insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                        } else {
                            0
                        }
                    imeBottomPx.value = bottom
                    insets
                }

                setContent {
                    val themeConfig by rememberThemeConfig()
                    OneMemosTheme(config = themeConfig) {
                        QuickCaptureOverlayContent(
                            uiStateFlow = uiState,
                            eventsFlow = events,
                            imeBottomPxFlow = imeBottomPx,
                            onClose = {
                                runCatching {
                                    kotlinx.coroutines.runBlocking {
                                        withContext(NonCancellable) {
                                            flushDraftNowSuspend()
                                        }
                                    }
                                }
                                removeOverlay()
                                stopSelf()
                            },
                            onUpdateContent = ::updateContent,
                            onSave = ::save,
                            onEditPrevious = ::editPrevious,
                            onRefreshHistory = ::refreshHistory,
                            onLoadForEdit = ::loadForEdit,
                            onInsertTime = ::insertCurrentTimeStamp,
                            onPickImages = ::startPickImagesActivity,
                            onRemoveAttachment = ::removeAttachment,
                            onRestoreDraft = ::restoreDraft,
                            onClearDraft = ::clearDraft,
                            onConfirmOverwrite = ::confirmOverwriteAndApplyPending,
                            onDismissOverwrite = ::dismissOverwriteDialog,
                        )
                    }
                }
            }

        val params =
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
                LayoutParams.TYPE_APPLICATION_OVERLAY,
                LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                // 让输入法可用：不要加 NOT_FOCUSABLE
                @Suppress("DEPRECATION")
                softInputMode = LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }

        runCatching {
            wm.addView(view, params)
        }.onFailure { e ->
            Toast.makeText(this, e.message?.take(200) ?: "无法显示悬浮窗", Toast.LENGTH_SHORT).show()
            owners.onDestroy()
            stopSelf()
            return
        }

        rootView = view
        viewTreeOwners = owners
        ViewCompat.requestApplyInsets(view)
    }

    private fun removeOverlay() {
        val wm = windowManager ?: return
        val view = rootView ?: return
        runCatching { wm.removeView(view) }
        rootView = null
        viewTreeOwners?.onDestroy()
        viewTreeOwners = null
    }

    private class OverlayComposeOwners : SavedStateRegistryOwner, ViewModelStoreOwner, LifecycleOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()

        override val lifecycle: Lifecycle = lifecycleRegistry
        override val savedStateRegistry = savedStateRegistryController.savedStateRegistry
        override val viewModelStore: ViewModelStore = store

        fun onCreate(savedInstanceState: Bundle? = null) {
            savedStateRegistryController.performRestore(savedInstanceState)
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        fun onDestroy() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            store.clear()
        }
    }

    private fun trimTrailingSpacesOnly(text: String): String =
        text.trimEnd { it == ' ' || it == '\t' }

    private fun updateContent(value: TextFieldValue) {
        val state = _uiState.value
        if (isDraftEnabled(state) && shouldAskOverwriteConfirm(state, incomingText = value.text)) {
            pendingOverwriteContent = value
            pendingOverwriteAttachmentUris = emptyList()
            pendingOverwriteReplaceAttachments = false
            _uiState.update { it.copy(draftOverwriteDialogVisible = true, error = null) }
            return
        }

        _uiState.update { it.copy(content = value, error = null) }
        if (isDraftEnabled(state) && draftWriteEnabled) {
            draftAutoSaver.onTextChanged(value.text)
        }
    }

    private fun insertCurrentTimeStamp() {
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

    private fun refreshHistory(limit: Int = 20) {
        serviceScope.launch {
            runCatching { withContext(Dispatchers.IO) { memoRepository.listRecentEditedActiveMemos(limit = limit) } }
                .onSuccess { memos ->
                    _uiState.update { it.copy(history = memos.map { m -> m.toHistoryItem() }) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message?.take(200) ?: "加载历史失败") }
                }
        }
    }

    private fun editPrevious() {
        val state = _uiState.value
        if (state.isSaving) return

        if (state.editingUuid.isNullOrBlank() && state.content.text.isNotBlank()) {
            _uiState.update { it.copy(error = "当前已有内容，先点“盖”保存或“取消”退出。") }
            return
        }

        serviceScope.launch {
            runCatching { withContext(Dispatchers.IO) { memoRepository.listRecentEditedActiveMemos(limit = 1).firstOrNull() } }
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

    private fun loadForEdit(uuid: String) {
        val state = _uiState.value
        if (state.isSaving) return

        serviceScope.launch {
            runCatching { withContext(Dispatchers.IO) { memoRepository.getMemo(uuid) } }
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

    private fun save() {
        val state = _uiState.value
        if (state.isSaving) return

        val visible = trimTrailingSpacesOnly(state.content.text)
        val uuid = state.editingUuid
        val attachments = state.attachments
        val localUris = attachments.mapNotNull { it.localUri }

        if (uuid.isNullOrBlank()) {
            if (visible.isBlank() && localUris.isEmpty()) {
                _events.tryEmit(QuickCaptureOverlayEvent.Saved)
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

        serviceScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                withContext(Dispatchers.IO) {
                    if (uuid.isNullOrBlank()) {
                        memoRepository.createLocalMemo(content = content, resourceUris = localUris)
                    } else {
                        if (state.attachmentsEditable) {
                            memoRepository.updateMemoDraft(
                                uuid = uuid,
                                content = content,
                                attachments =
                                    attachments.map { a ->
                                        MemoAttachmentDraft(
                                            localUri = a.localUri,
                                            remoteName = a.remoteName,
                                            filename = a.filename,
                                            mimeType = a.mimeType,
                                            createdAt = a.createdAt,
                                        )
                                    },
                            )
                        } else {
                            memoRepository.updateMemoContent(uuid = uuid, content = content)
                        }
                    }
                }
                if (uuid.isNullOrBlank() && isDraftEnabled(state)) {
                    clearDraftAfterLocalMemoSaved()
                }
                _events.tryEmit(QuickCaptureOverlayEvent.Saved)
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
        val localOnly = memo.serverId.isNullOrBlank()
        val editable = localOnly || (memo.syncStatus != SyncStatus.SYNCED && memo.syncStatus != SyncStatus.SYNCING)
        val attachmentsUi =
            memo.attachments.map { a ->
                QuickCaptureOverlayAttachmentUi(
                    key = a.remoteName ?: a.localUri ?: "attachment_${a.id}",
                    localUri = a.localUri,
                    cacheUri = a.cacheUri,
                    remoteName = a.remoteName,
                    filename = a.filename,
                    mimeType = a.mimeType,
                    createdAt = a.createdAt,
                )
            }
        _uiState.update {
            it.copy(
                content = TextFieldValue(text = split.visibleText, selection = TextRange(split.visibleText.length)),
                editingUuid = memo.uuid,
                hiddenAutoTagLines = split.hiddenLines,
                attachments = attachmentsUi,
                attachmentsEditable = editable,
                source = QuickCaptureOverlaySource.NORMAL,
                error = null,
            )
        }
    }

    private fun Memo.toHistoryItem(): QuickCaptureOverlayHistoryItem {
        val visible = AutoTagLineHider.hideFast(text = content, keywords = defaultAutoTagKeywords)
        val preview = previewText(visible)
        return QuickCaptureOverlayHistoryItem(uuid = uuid, preview = preview, updatedAt = updatedAt)
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

    @Composable
    private fun rememberThemeConfig(): androidx.compose.runtime.State<OneMemosThemeConfig> =
        androidx.compose.runtime.produceState(initialValue = OneMemosThemeConfig()) {
            val s = withContext(Dispatchers.IO) { settingsRepository.settings.first() }
            value =
                OneMemosThemeConfig(
                    themeDescriptor = s.themeDescriptor,
                    themeMode = s.themeMode,
                    readingFontScale = s.readingFontScale,
                    readingLineHeight = s.lineHeight,
                    tagChipColorful = s.tagChipColorful,
                )
        }

    private fun refreshDraftBannerOnStart() {
        val state = _uiState.value
        if (!isDraftEnabled(state)) {
            draftWriteEnabled = true
            _uiState.update { it.copy(draftBannerVisible = false, draftOverwriteDialogVisible = false) }
            return
        }

        val dir = File(noBackupFilesDir, "quick_capture_draft")
        val exists = File(dir, "draft.json").isFile || File(dir, "draft.json.bak").isFile
        draftWriteEnabled = !exists
        _uiState.update { it.copy(draftBannerVisible = exists, draftOverwriteDialogVisible = false) }
    }

    private fun disableDraft() {
        pendingOverwriteContent = null
        pendingOverwriteAttachmentUris = emptyList()
        pendingOverwriteReplaceAttachments = false
        draftWriteEnabled = true
        draftAutoSaver.cancel()
        _uiState.update { it.copy(draftBannerVisible = false, draftOverwriteDialogVisible = false) }
    }

    private fun isDraftEnabled(state: QuickCaptureOverlayUiState): Boolean = state.editingUuid.isNullOrBlank()

    private fun shouldAskOverwriteConfirm(
        state: QuickCaptureOverlayUiState,
        incomingText: String? = null,
        incomingAttachments: List<String>? = null,
    ): Boolean {
        if (!isDraftEnabled(state)) return false
        if (draftWriteEnabled) return false
        if (!state.draftBannerVisible) return false
        if (state.draftOverwriteDialogVisible) return true
        if (incomingText != null && incomingText != state.content.text) return true
        if (incomingAttachments != null && incomingAttachments.isNotEmpty()) return true
        return false
    }

    private fun confirmOverwriteAndApplyPending() {
        val pendingText = pendingOverwriteContent
        val pendingUris = pendingOverwriteAttachmentUris
        val replace = pendingOverwriteReplaceAttachments

        pendingOverwriteContent = null
        pendingOverwriteAttachmentUris = emptyList()
        pendingOverwriteReplaceAttachments = false

        draftWriteEnabled = true
        _uiState.update { it.copy(draftBannerVisible = false, draftOverwriteDialogVisible = false, error = null) }

        if (pendingText != null) {
            _uiState.update { it.copy(content = pendingText, error = null) }
            draftAutoSaver.onTextChanged(pendingText.text)
        }
        if (pendingUris.isNotEmpty()) {
            addAttachmentsFromExternal(uris = pendingUris, replace = replace)
        }
    }

    private fun dismissOverwriteDialog() {
        pendingOverwriteContent = null
        pendingOverwriteAttachmentUris = emptyList()
        pendingOverwriteReplaceAttachments = false
        _uiState.update { it.copy(draftOverwriteDialogVisible = false) }
    }

    private fun restoreDraft() {
        val state = _uiState.value
        if (!isDraftEnabled(state)) return

        serviceScope.launch {
            val draft = runCatching { draftStore.loadDraft() }.getOrNull()
            if (draft == null) {
                refreshDraftBannerOnStart()
                return@launch
            }

            val attachments =
                draft.attachments.mapNotNull { a ->
                    val f = File(filesDir, "quick_capture_draft_attachments/${a.fileName}")
                    if (!f.isFile) return@mapNotNull null
                    QuickCaptureOverlayAttachmentUi(
                        key = a.fileName,
                        localUri = android.net.Uri.fromFile(f).toString(),
                        cacheUri = null,
                        remoteName = null,
                        filename = a.originalName,
                        mimeType = null,
                        createdAt = draft.updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    )
                }

            val text = draft.text
            draftWriteEnabled = true
            _uiState.update {
                it.copy(
                    content = TextFieldValue(text = text, selection = TextRange(text.length)),
                    attachments = attachments,
                    attachmentsEditable = true,
                    draftBannerVisible = false,
                    draftOverwriteDialogVisible = false,
                    error = null,
                )
            }

            draftAutoSaver.onTextChanged(text)
            draftAutoSaver.onAttachmentsChanged(attachments.toDraftAttachments())
        }
    }

    private fun clearDraft() {
        val state = _uiState.value
        if (!isDraftEnabled(state)) return

        serviceScope.launch {
            runCatching { draftStore.clearDraft() }
            draftWriteEnabled = true
            _uiState.update { it.copy(draftBannerVisible = false, draftOverwriteDialogVisible = false) }

            draftAutoSaver.onTextChanged("")
            draftAutoSaver.onAttachmentsChanged(emptyList())
        }
    }

    private fun addAttachmentsFromExternal(
        uris: List<String>,
        replace: Boolean,
    ) {
        if (uris.isEmpty()) return

        val state = _uiState.value
        if (!state.attachmentsEditable) return
        if (!isDraftEnabled(state)) {
            addLocalAttachments(uris)
            return
        }

        if (shouldAskOverwriteConfirm(state, incomingAttachments = uris)) {
            pendingOverwriteContent = null
            pendingOverwriteAttachmentUris = uris
            pendingOverwriteReplaceAttachments = replace
            _uiState.update { it.copy(draftOverwriteDialogVisible = true, error = null) }
            return
        }

        val createdAt = System.currentTimeMillis()
        serviceScope.launch {
            val copied =
                uris.mapNotNull { raw ->
                    val trimmed = raw.trim()
                    if (trimmed.isBlank()) return@mapNotNull null
                    val attachment = runCatching { draftStore.copyInAttachment(android.net.Uri.parse(trimmed)) }.getOrNull() ?: return@mapNotNull null
                    val f = File(filesDir, "quick_capture_draft_attachments/${attachment.fileName}")
                    if (!f.isFile) return@mapNotNull null
                    QuickCaptureOverlayAttachmentUi(
                        key = attachment.fileName,
                        localUri = android.net.Uri.fromFile(f).toString(),
                        cacheUri = null,
                        remoteName = null,
                        filename = attachment.originalName,
                        mimeType = null,
                        createdAt = createdAt,
                    )
                }

            if (copied.isEmpty()) return@launch

            _uiState.update { s ->
                val next =
                    if (replace) {
                        copied
                    } else {
                        val seen = s.attachments.map { it.key }.toHashSet()
                        s.attachments + copied.filter { seen.add(it.key) }
                    }
                s.copy(attachments = next, error = null)
            }
            draftAutoSaver.onAttachmentsChanged(_uiState.value.attachments.toDraftAttachments())
        }
    }

    private suspend fun flushDraftNowSuspend() {
        val state = _uiState.value
        if (!isDraftEnabled(state)) return
        if (!draftWriteEnabled) return
        draftAutoSaver.flushNow()
    }

    private suspend fun saveDraftFromAutoSaver(
        text: String,
        attachments: List<QuickCaptureDraftAttachment>,
    ) {
        val state = _uiState.value
        if (!isDraftEnabled(state)) return
        if (!draftWriteEnabled) return

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
        draftWriteEnabled = true
        _uiState.update { it.copy(draftBannerVisible = false) }

        draftAutoSaver.onTextChanged("")
        draftAutoSaver.onAttachmentsChanged(emptyList())
    }

    internal fun debugUiState(): QuickCaptureOverlayUiState = _uiState.value

    internal fun debugUpdateContent(value: TextFieldValue) {
        updateContent(value)
    }

    internal fun debugRestoreDraft() {
        restoreDraft()
    }

    internal fun debugClearDraft() {
        clearDraft()
    }

    internal fun debugConfirmOverwrite() {
        confirmOverwriteAndApplyPending()
    }

    internal fun debugDismissOverwrite() {
        dismissOverwriteDialog()
    }

    internal fun debugSetEditingUuid(uuid: String?) {
        if (uuid.isNullOrBlank()) {
            _uiState.update { it.copy(editingUuid = null) }
            refreshDraftBannerOnStart()
            return
        }

        _uiState.update { it.copy(editingUuid = uuid) }
        disableDraft()
    }

    internal fun debugRefreshDraftBannerOnStart() {
        refreshDraftBannerOnStart()
    }

    internal fun debugAddAttachments(uris: List<String>, replace: Boolean) {
        addAttachmentsFromExternal(uris = uris, replace = replace)
    }

    internal fun debugFlushDraftNow() {
        runCatching {
            kotlinx.coroutines.runBlocking {
                withContext(NonCancellable) {
                    flushDraftNowSuspend()
                }
            }
        }
    }

    internal fun debugSimulateSaveSuccess() {
        runCatching {
            kotlinx.coroutines.runBlocking {
                withContext(NonCancellable) {
                    clearDraftAfterLocalMemoSaved()
                }
            }
        }
    }
}

@Composable
private fun QuickCaptureOverlayContent(
    uiStateFlow: StateFlow<QuickCaptureOverlayUiState>,
    eventsFlow: SharedFlow<QuickCaptureOverlayEvent>,
    imeBottomPxFlow: StateFlow<Int>,
    onClose: () -> Unit,
    onUpdateContent: (TextFieldValue) -> Unit,
    onSave: () -> Unit,
    onEditPrevious: () -> Unit,
    onRefreshHistory: (Int) -> Unit,
    onLoadForEdit: (String) -> Unit,
    onInsertTime: () -> Unit,
    onPickImages: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onRestoreDraft: () -> Unit,
    onClearDraft: () -> Unit,
    onConfirmOverwrite: () -> Unit,
    onDismissOverwrite: () -> Unit,
) {
    val uiState by uiStateFlow.collectAsState()
    val imeBottomPx by imeBottomPxFlow.collectAsState()

    val scrimInteraction = remember { MutableInteractionSource() }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val toolbarState = remember { mutableStateOf(OverlayTextToolbarState()) }
    var showStamp by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    val haptics = rememberOneMemosHaptics()
    val density = LocalDensity.current
    val imeBottomDp = with(density) { imeBottomPx.toDp() }
    val liftTarget =
        remember(imeBottomDp) {
            val raw = (imeBottomDp.value * InkSpacing.OverlayImeLiftFactor).dp
            raw.coerceIn(0.dp, InkSpacing.OverlayImeLiftMax)
        }
    val lift by animateDpAsState(targetValue = liftTarget, label = "overlayImeLift")

    LaunchedEffect(Unit) {
        eventsFlow.collect { event ->
            when (event) {
                QuickCaptureOverlayEvent.Saved -> {
                    haptics.tick()
                    delay(35)
                    haptics.confirm()
                    showStamp = true
                    delay(220)
                    showStamp = false
                    delay(200)
                    onClose()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    LaunchedEffect(showHistory) {
        if (showHistory) {
            onRefreshHistory(20)
        }
    }

    CompositionLocalProvider(
        LocalTextToolbar provides OverlayTextToolbar(toolbarState = toolbarState),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.38f))
                    .clickable(
                        interactionSource = scrimInteraction,
                        indication = null,
                    ) { onClose() },
            )

            InkCard(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = InkSpacing.X16)
                    .offset(y = -lift),
                onClick = null,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.X10)) {
                    val title =
                        when (uiState.source) {
                            QuickCaptureOverlaySource.SCREENSHOT -> "截图记录"
                            QuickCaptureOverlaySource.NORMAL -> "极速记录"
                        }
                    Text(text = title, style = MaterialTheme.typography.titleLarge)

                    Text(
                        text = "点“续写”可编辑上一条，长按“续写”可选择历史。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )

                    if (!uiState.editingUuid.isNullOrBlank()) {
                        Text(
                            text = "续写中：当前保存会覆盖原记录。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }

                    OverlayTextToolbarRow(
                        state = toolbarState.value,
                        onAfterAction = { toolbarState.value = OverlayTextToolbarState() },
                    )

                    ScrollTextField(
                        value = uiState.content,
                        onValueChange = onUpdateContent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = InkSpacing.OverlayInputMinHeight, max = InkSpacing.OverlayInputMaxHeight),
                        placeholder = "写点什么…",
                        focusRequester = focusRequester,
                    )

                    if (uiState.attachments.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(vertical = InkSpacing.X4),
                            horizontalArrangement = Arrangement.spacedBy(InkSpacing.X10),
                        ) {
                            itemsIndexed(uiState.attachments, key = { _, it -> it.key }) { _, attachment ->
                                OverlayAttachmentThumb(
                                    attachment = attachment,
                                    editable = uiState.attachmentsEditable && !uiState.isSaving,
                                    onRemove = onRemoveAttachment,
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (uiState.quickInsertTimeEnabled) {
                            IconButton(
                                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "插入时间" },
                                enabled = !uiState.isSaving,
                                onClick = {
                                    haptics.tick()
                                    onInsertTime()
                                },
                            ) {
                                Text(
                                    text = "时",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }

                        IconButton(
                            enabled = uiState.attachmentsEditable && !uiState.isSaving,
                            onClick = onPickImages,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddPhotoAlternate,
                                contentDescription = "添加图片",
                            )
                        }

                        Text(
                            text = if (uiState.attachments.isEmpty()) "未添加图片" else "附件：${uiState.attachments.size} 个",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        if (!uiState.editingUuid.isNullOrBlank() && !uiState.attachmentsEditable) {
                            Text(
                                text = "暂不支持编辑附件",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }

                    if (!uiState.error.isNullOrBlank()) {
                        Text(
                            text = uiState.error.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    if (uiState.draftBannerVisible) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(InkShape.Card)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = InkSpacing.X12, vertical = InkSpacing.X10),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "有草稿，可恢复",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "恢复草稿",
                                    modifier = Modifier
                                        .clickable { onRestoreDraft() }
                                        .padding(horizontal = InkSpacing.X8, vertical = InkSpacing.X4),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.size(InkSpacing.X4))
                                Text(
                                    text = "清空",
                                    modifier = Modifier
                                        .clickable { onClearDraft() }
                                        .padding(horizontal = InkSpacing.X8, vertical = InkSpacing.X4),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        QuickCaptureTextAction(
                            text = "续写",
                            enabled = !uiState.isSaving,
                            onClick = onEditPrevious,
                            onLongClick = { showHistory = true },
                        )

                        Row(
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = onClose) {
                                Text(text = "取消")
                            }

                            SealButton(
                                modifier = Modifier.padding(start = InkSpacing.X10),
                                text = "盖",
                                enabled = !uiState.isSaving,
                                onClick = onSave,
                            )
                        }
                    }
                }
            }

            SealStampOverlay(
                visible = showStamp,
                text = "已记",
            )

            if (showHistory) {
                QuickCaptureHistoryBottomSheet(
                    items = uiState.history,
                    onSelect = { uuid ->
                        onLoadForEdit(uuid)
                        showHistory = false
                    },
                    onDismiss = { showHistory = false },
                )
            }

            if (uiState.draftOverwriteDialogVisible) {
                AlertDialog(
                    onDismissRequest = onDismissOverwrite,
                    confirmButton = {
                        TextButton(onClick = onConfirmOverwrite) { Text(text = "覆盖") }
                    },
                    dismissButton = {
                        TextButton(onClick = onDismissOverwrite) { Text(text = "取消") }
                    },
                    title = { Text(text = "检测到草稿") },
                    text = { Text(text = "当前存在未恢复的草稿。继续操作会覆盖它，是否继续？") },
                )
            }
        }
    }

}

private fun List<QuickCaptureOverlayAttachmentUi>.toDraftAttachments(): List<QuickCaptureDraftAttachment> =
    mapNotNull { item ->
        val name = item.key.trim()
        if (name.isBlank()) return@mapNotNull null
        QuickCaptureDraftAttachment(fileName = name, originalName = item.filename)
    }

@Composable
private fun OverlayAttachmentThumb(
    attachment: QuickCaptureOverlayAttachmentUi,
    editable: Boolean,
    onRemove: (String) -> Unit,
) {
    val model =
        attachment.cacheUri?.takeIf { it.isNotBlank() }
            ?: attachment.localUri?.takeIf { it.isNotBlank() }

    Box(
        modifier = Modifier.size(InkSpacing.OverlayThumbSize).clip(InkShape.Card),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = "图片预览",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = attachment.filename ?: "附件",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        if (editable) {
            IconButton(
                modifier = Modifier.align(Alignment.TopEnd).size(InkSpacing.OverlayThumbBadgeSize),
                onClick = { onRemove(attachment.key) },
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "删除附件",
                )
            }
        }
    }
}

private data class OverlayTextToolbarState(
    val onCopy: (() -> Unit)? = null,
    val onPaste: (() -> Unit)? = null,
    val onCut: (() -> Unit)? = null,
    val onSelectAll: (() -> Unit)? = null,
)

private class OverlayTextToolbar(
    private val toolbarState: androidx.compose.runtime.MutableState<OverlayTextToolbarState>,
) : TextToolbar {
    override val status: TextToolbarStatus
        get() = if (toolbarState.value == OverlayTextToolbarState()) TextToolbarStatus.Hidden else TextToolbarStatus.Shown

    override fun hide() {
        toolbarState.value = OverlayTextToolbarState()
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        toolbarState.value =
            OverlayTextToolbarState(
                onCopy = onCopyRequested,
                onPaste = onPasteRequested,
                onCut = onCutRequested,
                onSelectAll = onSelectAllRequested,
            )
    }
}

@Composable
private fun OverlayTextToolbarRow(
    state: OverlayTextToolbarState,
    onAfterAction: () -> Unit,
) {
    if (state == OverlayTextToolbarState()) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(InkSpacing.X8, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.onCut?.let { onCut ->
            TextButton(
                onClick = {
                    onCut()
                    onAfterAction()
                },
            ) {
                Text(text = "剪切")
            }
        }
        state.onCopy?.let { onCopy ->
            TextButton(
                onClick = {
                    onCopy()
                    onAfterAction()
                },
            ) {
                Text(text = "复制")
            }
        }
        state.onPaste?.let { onPaste ->
            TextButton(
                onClick = {
                    onPaste()
                    onAfterAction()
                },
            ) {
                Text(text = "粘贴")
            }
        }
        state.onSelectAll?.let { onSelectAll ->
            TextButton(
                onClick = {
                    onSelectAll()
                    onAfterAction()
                },
            ) {
                Text(text = "全选")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickCaptureHistoryBottomSheet(
    items: List<QuickCaptureOverlayHistoryItem>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = InkSpacing.SheetMarginH),
            text = "续写",
            style = MaterialTheme.typography.titleLarge,
        )

        Spacer(modifier = Modifier.height(InkSpacing.X12))

        if (items.isEmpty()) {
            Text(
                modifier = Modifier.padding(horizontal = InkSpacing.SheetMarginH, vertical = InkSpacing.X8),
                text = "还没有可续写的记录。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = InkSpacing.SheetMarginH),
                verticalArrangement = Arrangement.spacedBy(InkSpacing.X10),
            ) {
                items(items, key = { it.uuid }) { item ->
                    InkCard(
                        onClick = { onSelect(item.uuid) },
                        onLongClick = null,
                    ) {
                        Text(
                            text = item.preview,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            modifier = Modifier.padding(top = InkSpacing.X6),
                            text = DateTimeFormatter.formatYmdHm(item.updatedAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(InkSpacing.X16))
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun QuickCaptureTextAction(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Text(
        text = text,
        modifier = Modifier
            .combinedClickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = InkSpacing.X10, vertical = InkSpacing.X8),
        style = MaterialTheme.typography.labelLarge,
        color =
            MaterialTheme.colorScheme.primary.copy(
                alpha = if (enabled) 1f else 0.45f,
            ),
    )
}
