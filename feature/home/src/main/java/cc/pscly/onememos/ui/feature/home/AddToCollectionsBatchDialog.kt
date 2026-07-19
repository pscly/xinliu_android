@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package cc.pscly.onememos.ui.feature.home

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.component.InkLoading
import cc.pscly.onememos.ui.util.rememberOneMemosHaptics
import kotlinx.coroutines.launch

@Composable
internal fun AddToCollectionsBatchDialog(
    totalSelectedCount: Int,
    selectedMemos: List<Memo>,
    onAllSuccess: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: AddToCollectionsViewModel = hiltViewModel(),
) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()

    var selectedParentId by rememberSaveable { mutableStateOf<String?>(null) }
    var newFolderName by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberOneMemosHaptics()

    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "将 $totalSelectedCount 条记录放入锦囊",
                style = MaterialTheme.typography.titleLarge,
            )

            if (!enabled) {
                Text(
                    text = "请先使用 Flow Backend 登录后再使用锦囊；自定义服务器模式暂不支持。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                Spacer(modifier = Modifier.height(10.dp))
                return@Column
            }

            Text(
                text = "选择目标文件夹",
                style = MaterialTheme.typography.labelLarge,
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "root") {
                    BatchFolderPickCard(
                        selected = selectedParentId == null,
                        depth = 0,
                        title = "根目录（顶层）",
                        enabled = !busy,
                        onClick = {
                            haptics.tick()
                            selectedParentId = null
                        },
                    )
                }
                items(folders, key = { it.id }) { f ->
                    BatchFolderPickCard(
                        selected = selectedParentId == f.id,
                        depth = f.depth,
                        title = f.name,
                        enabled = !busy,
                        onClick = {
                            haptics.tick()
                            selectedParentId = f.id
                        },
                    )
                }
            }

            Text(
                text = "快速新建文件夹",
                style = MaterialTheme.typography.labelLarge,
            )

            OutlinedTextField(
                value = newFolderName,
                onValueChange = { newFolderName = it },
                label = { Text("文件夹名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !busy,
            )

            if (busy) {
                InkLoading()
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    enabled = !busy,
                    onClick = onDismiss,
                ) {
                    Text("取消")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    enabled = !busy && newFolderName.trim().isNotBlank(),
                    onClick = {
                        val name = newFolderName.trim()
                        busy = true
                        haptics.confirm()
                        scope.launch {
                            try {
                                if (totalSelectedCount <= 0) {
                                    toast("未选中任何记录")
                                    onDismiss()
                                    return@launch
                                }
                                val folderId = viewModel.createFolder(parentId = selectedParentId, name = name)
                                if (folderId.isBlank()) {
                                    toast("新建失败：请确认已登录 Flow Backend")
                                    return@launch
                                }
                                selectedParentId = folderId
                                newFolderName = ""
                                val success = viewModel.addMemoRefs(parentId = folderId, memos = selectedMemos)
                                val failed = (totalSelectedCount - success).coerceAtLeast(0)
                                if (success >= totalSelectedCount && totalSelectedCount > 0) {
                                    val hasLocalOnly = selectedMemos.any { it.serverId.isNullOrBlank() }
                                    toast(if (hasLocalOnly) "已放入锦囊（待同步）" else "已放入锦囊")
                                    onAllSuccess()
                                    onDismiss()
                                } else {
                                    toast("成功 $success 条，失败 $failed 条")
                                }
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) {
                    Text("在此新建并放入")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        haptics.confirm()
                        scope.launch {
                            try {
                                if (totalSelectedCount <= 0) {
                                    toast("未选中任何记录")
                                    onDismiss()
                                    return@launch
                                }
                                val success = viewModel.addMemoRefs(parentId = selectedParentId, memos = selectedMemos)
                                val failed = (totalSelectedCount - success).coerceAtLeast(0)
                                if (success >= totalSelectedCount && totalSelectedCount > 0) {
                                    val hasLocalOnly = selectedMemos.any { it.serverId.isNullOrBlank() }
                                    toast(if (hasLocalOnly) "已放入锦囊（待同步）" else "已放入锦囊")
                                    onAllSuccess()
                                    onDismiss()
                                } else {
                                    toast("成功 $success 条，失败 $failed 条")
                                }
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) {
                    Text("放入")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun BatchFolderPickCard(
    selected: Boolean,
    enabled: Boolean,
    depth: Int,
    title: String,
    onClick: () -> Unit,
) {
    val prefix = remember(depth) { "  ".repeat(depth.coerceAtMost(8)) }
    InkCard(
        onClick = if (enabled) onClick else null,
        onLongClick = null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (selected) "已选" else "未选",
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = prefix + title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
        }
    }
}
