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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import cc.pscly.onememos.domain.repository.MemoRepository
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.component.SealButton
import cc.pscly.onememos.ui.component.SealStampOverlay
import cc.pscly.onememos.ui.theme.OneMemosTheme
import cc.pscly.onememos.ui.theme.OneMemosThemeConfig
import cc.pscly.onememos.ui.util.AutoTagLineHider
import cc.pscly.onememos.ui.util.DateTimeFormatter
import cc.pscly.onememos.ui.util.rememberOneMemosHaptics
import dagger.hilt.android.AndroidEntryPoint
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal data class QuickCaptureOverlayUiState(
    val content: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val editingUuid: String? = null,
    val hiddenAutoTagLines: List<String> = emptyList(),
    val history: List<QuickCaptureOverlayHistoryItem> = emptyList(),
    val attachments: List<String> = emptyList(),
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

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
    }

    override fun onDestroy() {
        removeOverlay()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "未授予悬浮窗权限，无法开启悬浮记录", Toast.LENGTH_SHORT).show()
            stopSelf()
            return START_NOT_STICKY
        }

        val attachments = intent?.getStringArrayListExtra(EXTRA_ATTACHMENTS).orEmpty()
        val prefillText = intent?.getStringExtra(EXTRA_PREFILL_TEXT).orEmpty()

        // 截图/附件场景：强制进入“新建记录”模式，避免误覆盖上一条。
        _uiState.update { state ->
            val next =
                state.copy(
                    attachments = attachments,
                    error = null,
                )
            if (attachments.isNotEmpty()) {
                next.copy(editingUuid = null, hiddenAutoTagLines = emptyList())
            } else {
                next
            }
        }

        if (prefillText.isNotBlank()) {
            _uiState.update { state ->
                if (state.content.isBlank()) state.copy(content = prefillText) else state
            }
        }

        showOverlayIfNeeded()
        return START_NOT_STICKY
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
                                removeOverlay()
                                stopSelf()
                            },
                            onUpdateContent = ::updateContent,
                            onSave = ::save,
                            onEditPrevious = ::editPrevious,
                            onRefreshHistory = ::refreshHistory,
                            onLoadForEdit = ::loadForEdit,
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

    private fun updateContent(value: String) {
        _uiState.update { it.copy(content = value, error = null) }
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

        if (state.editingUuid.isNullOrBlank() && state.content.isNotBlank()) {
            _uiState.update { it.copy(error = "当前已有内容，先点“盖”保存或“取消”退出。") }
            return
        }

        serviceScope.launch {
            runCatching { withContext(Dispatchers.IO) { memoRepository.listRecentEditedActiveMemos(limit = 1).firstOrNull() } }
                .onSuccess { memo ->
                    if (memo == null) {
                        _uiState.update { it.copy(error = "暂无可续写记录") }
                    } else {
                        applyMemoForEdit(memo.uuid, memo.content)
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
                        applyMemoForEdit(memo.uuid, memo.content)
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

        val visible = trimTrailingSpacesOnly(state.content)
        val uuid = state.editingUuid
        val attachments = state.attachments

        if (uuid.isNullOrBlank()) {
            if (visible.isBlank() && attachments.isEmpty()) {
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
                        memoRepository.createLocalMemo(content = content, resourceUris = attachments)
                    } else {
                        memoRepository.updateMemoContent(uuid = uuid, content = content)
                    }
                }
                _events.tryEmit(QuickCaptureOverlayEvent.Saved)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message?.take(200) ?: "保存失败") }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun applyMemoForEdit(uuid: String, content: String) {
        val split = AutoTagLineHider.split(text = content, keywords = defaultAutoTagKeywords)
        _uiState.update {
            it.copy(
                content = split.visibleText,
                editingUuid = uuid,
                hiddenAutoTagLines = split.hiddenLines,
                error = null,
            )
        }
    }

    private fun cc.pscly.onememos.domain.model.Memo.toHistoryItem(): QuickCaptureOverlayHistoryItem {
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
            value = OneMemosThemeConfig(palette = s.themePalette, themeMode = s.themeMode)
        }
}

@Composable
private fun QuickCaptureOverlayContent(
    uiStateFlow: StateFlow<QuickCaptureOverlayUiState>,
    eventsFlow: SharedFlow<QuickCaptureOverlayEvent>,
    imeBottomPxFlow: StateFlow<Int>,
    onClose: () -> Unit,
    onUpdateContent: (String) -> Unit,
    onSave: () -> Unit,
    onEditPrevious: () -> Unit,
    onRefreshHistory: (Int) -> Unit,
    onLoadForEdit: (String) -> Unit,
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
            val raw = (imeBottomDp.value * 0.35f).dp
            raw.coerceIn(0.dp, 140.dp)
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
                    .background(Color.Black.copy(alpha = 0.38f))
                    .clickable(
                        interactionSource = scrimInteraction,
                        indication = null,
                    ) { onClose() },
            )

            InkCard(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp)
                    .offset(y = -lift),
                onClick = null,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val title = if (uiState.attachments.isNotEmpty()) "截图记录" else "极速记录"
                    Text(text = title, style = MaterialTheme.typography.titleLarge)

                    Text(
                        text = "点“续写”可编辑上一条，长按“续写”可选择历史。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )

                    if (uiState.attachments.isNotEmpty()) {
                        Text(
                            text = "附件*${uiState.attachments.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }

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

                    OutlinedTextField(
                        value = uiState.content,
                        onValueChange = onUpdateContent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        placeholder = { Text(text = "写点什么…") },
                        minLines = 3,
                        maxLines = 10,
                        singleLine = false,
                    )

                    if (!uiState.error.isNullOrBlank()) {
                        Text(
                            text = uiState.error.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
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
                                modifier = Modifier.padding(start = 10.dp),
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
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
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
            modifier = Modifier.padding(horizontal = 20.dp),
            text = "续写",
            style = MaterialTheme.typography.titleLarge,
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (items.isEmpty()) {
            Text(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                text = "还没有可续写的记录。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
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
                            modifier = Modifier.padding(top = 6.dp),
                            text = DateTimeFormatter.formatYmdHm(item.updatedAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
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
            .padding(horizontal = 10.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color =
            MaterialTheme.colorScheme.primary.copy(
                alpha = if (enabled) 1f else 0.45f,
            ),
    )
}
