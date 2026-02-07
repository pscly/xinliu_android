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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.component.SealButton
import cc.pscly.onememos.ui.component.SealStampOverlay
import cc.pscly.onememos.ui.util.DateTimeFormatter
import cc.pscly.onememos.ui.util.rememberOneMemosHaptics
import kotlinx.coroutines.delay

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
        showStamp = showStamp,
    )
}

@Composable
private fun QuickCaptureScreen(
    uiState: QuickCaptureUiState,
    focusRequester: FocusRequester,
    onClose: () -> Unit,
    onContentChange: (TextFieldValue) -> Unit,
    onInsertTime: () -> Unit,
    onSave: () -> Unit,
    onEditPrevious: () -> Unit,
    onRefreshHistory: () -> Unit,
    onLoadForEdit: (String) -> Unit,
    showStamp: Boolean,
) {
    val scrimInteraction = remember { MutableInteractionSource() }
    var showHistory by remember { mutableStateOf(false) }
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
                .padding(horizontal = 16.dp)
                // 消费卡片区域的点击，避免点到卡片背景也触发关闭
                .pointerInput(Unit) {
                    detectTapGestures { /* no-op */ }
                },
            onClick = null,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "极速记录",
                    style = MaterialTheme.typography.titleLarge,
                )

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
                    Text(
                        text = uiState.error,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickCaptureHistoryBottomSheet(
    items: List<QuickCaptureHistoryItem>,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
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
