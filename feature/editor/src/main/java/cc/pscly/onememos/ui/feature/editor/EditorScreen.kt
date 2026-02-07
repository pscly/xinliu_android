@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package cc.pscly.onememos.ui.feature.editor

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.pscly.onememos.core.network.MemosUrls
import cc.pscly.onememos.domain.model.MemoServerState
import cc.pscly.onememos.domain.model.SyncStatus
import cc.pscly.onememos.domain.tag.TagExtractor
import cc.pscly.onememos.domain.tag.TagStat
import cc.pscly.onememos.ui.component.ImageViewerDialog
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.component.MarkdownPaper
import cc.pscly.onememos.ui.component.ScrollTextField
import cc.pscly.onememos.ui.component.SealButton
import cc.pscly.onememos.ui.component.SealStampOverlay
import cc.pscly.onememos.ui.component.TagChip
import cc.pscly.onememos.ui.component.TagFilterBottomSheet
import cc.pscly.onememos.ui.filter.MemoFilter
import cc.pscly.onememos.ui.filter.TagMatchMode
import cc.pscly.onememos.ui.util.DateTimeFormatter
import cc.pscly.onememos.ui.util.rememberOneMemosHaptics
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
fun EditorScreen(
    onBack: () -> Unit,
    onOpenShareCard: (String) -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = rememberOneMemosHaptics()

    var showStamp by remember { mutableStateOf(false) }
    var stampText by remember { mutableStateOf("已记") }
    var showArchiveConfirm by remember { mutableStateOf(false) }
    var showUnarchiveConfirm by remember { mutableStateOf(false) }

    var showFilterSheet by remember { mutableStateOf(false) }
    var filterSelectedTags by remember { mutableStateOf(emptySet<String>()) }
    var filterMode by remember { mutableStateOf(TagMatchMode.OR) }

    var viewerOpen by remember { mutableStateOf(false) }
    var viewerStartIndex by remember { mutableStateOf(0) }
    var reorderMode by remember { mutableStateOf(false) }

    val memoTags = remember(uiState.tagSourceText) { TagExtractor.extractAll(uiState.tagSourceText) }

    LaunchedEffect(uiState.attachments.size) {
        if (uiState.attachments.size < 2) {
            reorderMode = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            suspend fun playStampAndBack(text: String) {
                val holdMs = uiState.sealStampDurationMs.coerceIn(200, 2000)
                val fadeWaitMs = (holdMs / 2).coerceIn(180, 450)

                stampText = text
                showStamp = true
                delay(holdMs.toLong())
                showStamp = false
                delay(fadeWaitMs.toLong())
                onBack()
            }

            when (event) {
                EditorEvent.Saved -> {
                    haptics.tick()
                    delay(35)
                    haptics.confirm()
                    playStampAndBack("已记")
                }

                EditorEvent.Archived -> {
                    haptics.tick()
                    delay(35)
                    haptics.heavyClick()
                    playStampAndBack("已封")
                }

                EditorEvent.Unarchived -> {
                    haptics.tick()
                    delay(35)
                    haptics.confirm()
                    playStampAndBack("已启")
                }

                EditorEvent.FilterApplied -> onBack()
            }
        }
    }

    val pickImagesLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris ->
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                viewModel.addResource(uri.toString())
            }
        }

    val isReadonlyViewing =
        uiState.uuid != null &&
            uiState.serverId != null &&
            uiState.syncStatus == SyncStatus.SYNCED &&
            !uiState.canEdit
    val isArchived = uiState.serverState == MemoServerState.ARCHIVED.name

    val serverBase = uiState.serverBase
    val fullImageModels =
        remember(uiState.attachments, serverBase) {
            uiState.attachments.mapNotNull { a ->
                val cacheModel = usableFileUri(a.cacheUri)
                when {
                    cacheModel != null -> cacheModel
                    !a.localUri.isNullOrBlank() -> a.localUri
                    !serverBase.isNullOrBlank() &&
                        !a.remoteName.isNullOrBlank() &&
                        !a.filename.isNullOrBlank() &&
                        a.mimeType?.startsWith("image/") == true -> {
                        MemosUrls.attachmentFileUrl(
                            base = serverBase,
                            attachmentName = a.remoteName,
                            filename = a.filename,
                            thumbnail = false,
                        )
                    }

                    else -> null
                }
            }
        }

    if (viewerOpen) {
        ImageViewerDialog(
            models = fullImageModels,
            startIndex = viewerStartIndex,
            onDismiss = { viewerOpen = false },
        )
    }

    if (showArchiveConfirm) {
        AlertDialog(
            onDismissRequest = { showArchiveConfirm = false },
            title = { Text("确认归档？") },
            text = { Text("归档后将从“随笔”隐藏，可在“已归档”中恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showArchiveConfirm = false
                        viewModel.archive()
                    },
                ) { Text("归档") }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveConfirm = false }) { Text("取消") }
            },
        )
    }

    if (showUnarchiveConfirm) {
        AlertDialog(
            onDismissRequest = { showUnarchiveConfirm = false },
            title = { Text("恢复到随笔？") },
            text = { Text("恢复后会重新出现在“随笔”列表，并会自动同步到服务器。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnarchiveConfirm = false
                        viewModel.unarchive()
                    },
                ) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { showUnarchiveConfirm = false }) { Text("取消") }
            },
        )
    }

    if (showFilterSheet) {
        TagFilterBottomSheet(
            title = "筛选带标签的记录",
            allTags = uiState.allTagStats,
            selectedTags = filterSelectedTags,
            showTagCounts = uiState.showTagCountsInFilter,
            tagMatchMode = filterMode,
            onToggleTag = { t ->
                filterSelectedTags =
                    if (filterSelectedTags.contains(t)) {
                        filterSelectedTags - t
                    } else {
                        filterSelectedTags + t
                    }
            },
            onTagMatchModeChange = { filterMode = it },
            onClear = {
                filterSelectedTags = emptySet()
                filterMode = TagMatchMode.OR
            },
            onApply = {
                viewModel.applyFilter(
                    MemoFilter(
                        query = "",
                        selectedTags = filterSelectedTags,
                        tagMatchMode = filterMode,
                    ),
                )
            },
            onDismiss = { showFilterSheet = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val titleText =
                        when {
                            uiState.uuid.isNullOrBlank() -> "新记"
                            uiState.syncStatus == SyncStatus.SYNCED && uiState.canEdit -> "编辑"
                            else -> "查看"
                        }
                    Text(text = titleText)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    val canShare = !uiState.uuid.isNullOrBlank()
                    if (canShare) {
                        IconButton(onClick = { onOpenShareCard(uiState.uuid.orEmpty()) }) {
                            Icon(imageVector = Icons.Filled.Share, contentDescription = "墨迹卡片")
                        }
                    }
                    if (isReadonlyViewing) {
                        if (isArchived) {
                            IconButton(onClick = { showUnarchiveConfirm = true }) {
                                Icon(imageVector = Icons.Filled.Unarchive, contentDescription = "恢复")
                            }
                        } else {
                            IconButton(onClick = viewModel::enableEdit) {
                                Icon(imageVector = Icons.Filled.Edit, contentDescription = "编辑")
                            }
                            IconButton(onClick = { showArchiveConfirm = true }) {
                                Icon(imageVector = Icons.Filled.Archive, contentDescription = "归档")
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                // minSdk=33（Android 13）：直接用 Compose 的 IME insets 修正布局。
                // 相比手动读取 insets 再算 padding，这种写法更“布局层”，避免 IME 动画期间频繁触发重组带来的卡顿。
                .imePadding(),
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!uiState.loadError.isNullOrBlank()) {
                    Text(
                        text = uiState.loadError.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    return@Column
                }

                if (uiState.syncStatus == SyncStatus.FAILED && !uiState.lastSyncError.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "同步失败：${uiState.lastSyncError}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(onClick = viewModel::retrySync) {
                            Text(text = "重试同步")
                        }
                    }
                }

                if (isReadonlyViewing) {
                    val hint =
                        if (isArchived) {
                            "已归档记录默认只读；点右上角“恢复”可回到随笔。"
                        } else {
                            "已同步记录默认只读；点右上角“编辑”可修改，点“归档”可归档。"
                        }
                    Text(
                        text = hint,
                        color = MaterialTheme.colorScheme.outline,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else if (uiState.canEdit && !uiState.attachmentsEditable) {
                    Text(
                        text = "编辑已同步记录：目前仅支持修改文字，附件暂不支持编辑；保存后会自动同步。",
                        color = MaterialTheme.colorScheme.outline,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (!uiState.canEdit && memoTags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        memoTags.forEach { tag ->
                            TagChip(
                                tag = tag,
                                onClick = {
                                    filterSelectedTags = setOf(tag)
                                    filterMode = TagMatchMode.OR
                                    showFilterSheet = true
                                },
                            )
                        }
                    }
                }

                if (uiState.canEdit) {
                    val tagPrefix = remember(uiState.content) { TagCompletion.findTagPrefix(uiState.content) }
                    val showSuggestions = tagPrefix != null && uiState.tagSuggestions.isNotEmpty()

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true),
                    ) {
                        ScrollTextField(
                            value = uiState.content,
                            onValueChange = viewModel::onContentChange,
                            enabled = true,
                            readOnly = false,
                            placeholder = "写点什么…",
                            modifier = Modifier.fillMaxSize(),
                        )

                        TagSuggestionPanel(
                            visible = showSuggestions,
                            suggestions = uiState.tagSuggestions,
                            allTags = uiState.allTagStats,
                            onSelect = viewModel::completeTag,
                            modifier =
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(horizontal = 2.dp, vertical = 10.dp),
                        )
                    }
                } else {
                    MarkdownPaper(
                        markdown = uiState.content.text,
                        placeholder = "写点什么…",
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true),
                    )
                }

                if (uiState.attachments.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(uiState.attachments, key = { _, it -> it.key }) { index, attachment ->
                            val remoteName = attachment.remoteName
                            val filename = attachment.filename
                            val cacheModel = usableFileUri(attachment.cacheUri)
                            val thumbnailModel =
                                when {
                                    cacheModel != null -> cacheModel
                                    !attachment.localUri.isNullOrBlank() -> attachment.localUri
                                    !serverBase.isNullOrBlank() &&
                                        !remoteName.isNullOrBlank() &&
                                        !filename.isNullOrBlank() &&
                                        attachment.mimeType?.startsWith("image/") == true -> {
                                        MemosUrls.attachmentFileUrl(
                                            base = serverBase,
                                            attachmentName = remoteName,
                                            filename = filename,
                                            thumbnail = true,
                                        )
                                    }

                                    else -> null
                                }

                            val fullModel =
                                when {
                                    cacheModel != null -> cacheModel
                                    !attachment.localUri.isNullOrBlank() -> attachment.localUri
                                    !serverBase.isNullOrBlank() &&
                                        !remoteName.isNullOrBlank() &&
                                        !filename.isNullOrBlank() &&
                                        attachment.mimeType?.startsWith("image/") == true -> {
                                        MemosUrls.attachmentFileUrl(
                                            base = serverBase,
                                            attachmentName = remoteName,
                                            filename = filename,
                                            thumbnail = false,
                                        )
                                    }

                                    else -> null
                                }

                            val canEditAttachments = uiState.canEdit && uiState.attachmentsEditable
                            val showReorder = reorderMode && canEditAttachments && uiState.attachments.size >= 2

                            Box(
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .then(
                                        if (!showReorder && thumbnailModel != null && fullModel != null) {
                                            Modifier.clickable {
                                                viewerStartIndex = fullImageModels.indexOf(fullModel).coerceAtLeast(0)
                                                viewerOpen = true
                                            }
                                        } else {
                                            Modifier
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (thumbnailModel != null) {
                                    AsyncImage(
                                        model = thumbnailModel,
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

                                if (canEditAttachments) {
                                    IconButton(
                                        modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
                                        onClick = { viewModel.removeAttachment(attachment.key) },
                                    ) {
                                        Icon(imageVector = Icons.Filled.Close, contentDescription = "删除附件")
                                    }
                                }

                                if (showReorder) {
                                    if (index > 0) {
                                        IconButton(
                                            modifier = Modifier.align(Alignment.BottomStart).size(28.dp),
                                            onClick = { viewModel.moveAttachment(attachment.key, -1) },
                                        ) {
                                            Icon(imageVector = Icons.Filled.ChevronLeft, contentDescription = "左移")
                                        }
                                    }
                                    if (index < uiState.attachments.lastIndex) {
                                        IconButton(
                                            modifier = Modifier.align(Alignment.BottomEnd).size(28.dp),
                                            onClick = { viewModel.moveAttachment(attachment.key, 1) },
                                        ) {
                                            Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = "右移")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 底部工具栏：左侧“添加图片/附件信息”，右侧“录”（与主页右下角“记”呼应）。
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    val rowEndPadding = if (uiState.canEdit) 68.dp else 0.dp
                    Row(
                        modifier =
                            Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxWidth()
                                .padding(end = rowEndPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (uiState.canEdit && uiState.quickInsertTimeEnabled) {
                            IconButton(
                                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "插入时间" },
                                enabled = uiState.canEdit && !uiState.isSaving,
                                onClick = {
                                    haptics.tick()
                                    viewModel.insertCurrentTimeStamp()
                                },
                            ) {
                                Text(
                                    text = "时",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }

                        IconButton(
                            enabled = uiState.canEdit && uiState.attachmentsEditable,
                            onClick = { pickImagesLauncher.launch(arrayOf("image/*")) },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddPhotoAlternate,
                                contentDescription = "添加图片",
                            )
                        }

                        Text(
                            text = if (uiState.attachments.isEmpty()) "未添加图片" else "附件：${uiState.attachments.size} 个",
                            color = MaterialTheme.colorScheme.outline,
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        if (uiState.canEdit && uiState.attachmentsEditable && uiState.attachments.size >= 2) {
                            TextButton(onClick = { reorderMode = !reorderMode }) {
                                Text(text = if (reorderMode) "完成排序" else "排序")
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // 编辑态底部工具栏尽量克制，避免与“录”抢空间；只读态仍显示时间信息。
                        if (!uiState.canEdit) {
                            val createdAt = uiState.createdAt
                            val updatedAt = uiState.updatedAt
                            if (createdAt != null && updatedAt != null) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "记录：${DateTimeFormatter.formatYmdHm(createdAt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = "修改：${DateTimeFormatter.formatYmdHm(updatedAt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.canEdit) {
                        SealButton(
                            text = "录",
                            modifier = Modifier.align(Alignment.CenterEnd),
                            // 与主页右下角“记”同尺寸，形成呼应感。
                            size = 56.dp,
                            enabled = uiState.canEdit && !uiState.isSaving,
                            onClick = viewModel::save,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
            }

            SealStampOverlay(
                visible = showStamp,
                text = stampText,
                durationMs = uiState.sealStampDurationMs,
            )
        }
    }
}

@Composable
private fun TagSuggestionPanel(
    visible: Boolean,
    suggestions: List<String>,
    allTags: List<TagStat>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 用“浮层 + 动画”承载联想，避免把正文整体挤下去，视觉更自然，也不打断输入节奏。
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxWidth(),
        enter = fadeIn() + slideInVertically { fullHeight -> fullHeight / 2 },
        exit = fadeOut() + slideOutVertically { fullHeight -> fullHeight / 2 },
    ) {
        InkCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "标签联想",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "点选插入",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val countMap = remember(allTags) { allTags.associate { it.name to it.count } }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(suggestions, key = { it }) { tag ->
                    val count = countMap[tag] ?: 0
                    TagChip(
                        tag = tag,
                        label = if (count > 0) "$tag ($count)" else tag,
                        selected = false,
                        onClick = { onSelect(tag) },
                    )
                }
            }
        }
    }
}

private fun usableFileUri(uri: String?): String? {
    if (uri.isNullOrBlank()) return null
    return runCatching {
        val parsed = android.net.Uri.parse(uri)
        if (!parsed.scheme.equals("file", ignoreCase = true)) return@runCatching null
        // 体验优化：避免在 Compose 组合/滚动路径做磁盘 IO（File.exists）。
        // cacheUri 由缓存写入方保证存在，失效清理由后台逻辑处理。
        val path = parsed.path ?: return@runCatching null
        if (path.isBlank()) null else uri
    }.getOrNull()
}
