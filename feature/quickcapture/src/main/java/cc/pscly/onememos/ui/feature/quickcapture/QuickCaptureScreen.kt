package cc.pscly.onememos.ui.feature.quickcapture

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.component.SealButton
import cc.pscly.onememos.ui.component.SealStampOverlay
import cc.pscly.onememos.ui.theme.InkShape
import cc.pscly.onememos.ui.theme.InkSpacing
import cc.pscly.onememos.ui.util.DateTimeFormatter
import cc.pscly.onememos.ui.util.rememberOneMemosHaptics
import kotlinx.coroutines.delay
import cc.pscly.onememos.ui.theme.PaperInkModalBottomSheet
import cc.pscly.onememos.ui.theme.PaperInkAlertDialog

@Composable
fun QuickCaptureRoute(
    onClose: () -> Unit,
    viewModel: QuickCaptureViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var showStamp by remember { mutableStateOf(false) }
    val haptics = rememberOneMemosHaptics()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                QuickCaptureEvent.Saved -> {
                    // 成功节奏：tick -> confirm（更像“盖章落下”的两段式反馈）
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

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    viewModel.flushDraftNow()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    QuickCaptureScreen(
        uiState = uiState,
        focusRequester = focusRequester,
        onClose = onClose,
        onContentChange = viewModel::updateContent,
        onInsertTime = viewModel::insertCurrentTimeStamp,
        onSave = viewModel::save,
        onEditPrevious = viewModel::editPrevious,
        onRefreshHistory = viewModel::refreshHistory,
        onLoadForEdit = viewModel::loadForEdit,
        onRestoreDraft = viewModel::restoreDraft,
        onClearDraft = viewModel::clearDraft,
        onConfirmOverwrite = viewModel::confirmOverwriteAndApplyPending,
        onDismissOverwrite = viewModel::dismissOverwriteDialog,
        showStamp = showStamp,
    )
}

@Composable
internal fun QuickCaptureScreen(
    uiState: QuickCaptureUiState,
    focusRequester: FocusRequester,
    onClose: () -> Unit,
    onContentChange: (TextFieldValue) -> Unit,
    onInsertTime: () -> Unit,
    onSave: () -> Unit,
    onEditPrevious: () -> Unit,
    onRefreshHistory: () -> Unit,
    onLoadForEdit: (String) -> Unit,
    onRestoreDraft: () -> Unit,
    onClearDraft: () -> Unit,
    onConfirmOverwrite: () -> Unit,
    onDismissOverwrite: () -> Unit,
    showStamp: Boolean,
) {
    val scrimInteraction = remember { MutableInteractionSource() }
    var showHistory by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val haptics = rememberOneMemosHaptics()

    LaunchedEffect(showHistory) {
        if (showHistory) {
            onRefreshHistory()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景遮罩：点击空白区域即退出
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
                .padding(horizontal = InkSpacing.X16)
                // 消费卡片区域的点击，避免点到卡片背景也触发关闭
                .pointerInput(Unit) {
                    detectTapGestures { /* no-op */ }
                },
            onClick = null,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(InkSpacing.X12),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "极速记录",
                        style = MaterialTheme.typography.titleLarge,
                    )

                    Box {
                        IconButton(
                            modifier = Modifier.semantics { contentDescription = "更多" },
                            onClick = { showMenu = true },
                        ) {
                            Icon(imageVector = Icons.Filled.MoreVert, contentDescription = null)
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(text = "清空草稿") },
                                onClick = {
                                    showMenu = false
                                    onClearDraft()
                                },
                            )
                        }
                    }
                }

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

                OutlinedTextField(
                    value = uiState.content,
                    onValueChange = onContentChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text(text = "写点什么…") },
                    minLines = 3,
                    maxLines = 10,
                    singleLine = false,
                )

                if (!uiState.error.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .semantics {
                                error(uiState.error.orEmpty())
                                liveRegion = LiveRegionMode.Assertive
                            }
                            .testTag("qc_error"),
                    ) {
                        Text(
                            text = uiState.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                if (uiState.draftBannerVisible) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { liveRegion = LiveRegionMode.Polite }
                            .clip(InkShape.Chip)
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
                        if (uiState.quickInsertTimeEnabled) {
                            TextButton(
                                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "插入时间" },
                                enabled = !uiState.isSaving,
                                onClick = {
                                    haptics.tick()
                                    onInsertTime()
                                },
                            ) {
                                Text(text = "时")
                            }
                        }
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
            PaperInkAlertDialog(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickCaptureHistoryBottomSheet(
    items: List<QuickCaptureHistoryItem>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    PaperInkModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = InkSpacing.X20),
            text = "续写",
            style = MaterialTheme.typography.titleLarge,
        )

        Spacer(modifier = Modifier.height(InkSpacing.X12))

        if (items.isEmpty()) {
            Text(
                modifier = Modifier.padding(horizontal = InkSpacing.X20, vertical = InkSpacing.X8),
                text = "还没有可续写的记录。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = InkSpacing.X20),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun QuickCaptureTextAction(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .defaultMinSize(
                minHeight = InkSpacing.TouchTargetMin,
                minWidth = InkSpacing.TouchTargetMin,
            )
            .combinedClickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick ?: {},
            )
            .semantics { contentDescription = text },
    ) {
        Text(
            text = text,
            modifier = Modifier
                .padding(horizontal = InkSpacing.X10, vertical = InkSpacing.X8)
                .clearAndSetSemantics { },
            style = MaterialTheme.typography.labelLarge,
            color =
                MaterialTheme.colorScheme.primary.copy(
                    alpha = if (enabled) 1f else 0.45f,
                ),
        )
    }
}
