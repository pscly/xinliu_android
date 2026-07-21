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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
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
import cc.pscly.onememos.ui.component.ScrollPaper
import cc.pscly.onememos.ui.markdown2.MikepenzMarkdown
import cc.pscly.onememos.ui.component.SealButton
import cc.pscly.onememos.ui.component.SealStampOverlay
import cc.pscly.onememos.ui.component.TagChip
import cc.pscly.onememos.ui.component.TagFilterBottomSheet
import cc.pscly.onememos.ui.filter.MemoFilter
import cc.pscly.onememos.ui.filter.TagMatchMode
import cc.pscly.onememos.ui.theme.InkBorder
import cc.pscly.onememos.ui.theme.InkShape
import cc.pscly.onememos.ui.theme.InkSpacing
import cc.pscly.onememos.ui.theme.LocalReadingConfig
import cc.pscly.onememos.ui.util.DateTimeFormatter
import cc.pscly.onememos.ui.util.rememberOneMemosHaptics
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import cc.pscly.onememos.ui.theme.PaperInkTopAppBar
import cc.pscly.onememos.ui.component.InkRetryBanner

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
    var excludeTags by remember { mutableStateOf(false) }

    var viewerOpen by remember { mutableStateOf(false) }
    var viewerStartIndex by remember { mutableStateOf(0) }
    var reorderMode by remember { mutableStateOf(false) }

    // M2.8：编辑 / 阅览 / 双栏状态。
    // 草稿正文由 EditorViewModel 持有（ViewModel 本身扛旋转），这里用 rememberSaveable
    // 让“阅览开关 / 双栏开关”这类纯 UI 状态在旋转后也能恢复。
    var previewEnabled by rememberSaveable { mutableStateOf(false) }
    var dualPaneEnabled by rememberSaveable { mutableStateOf(false) }
    // 宽屏阈值：达到才允许双栏（左写右览）；窄屏自动塌缩回单栏。
    val isWideEditor = LocalConfiguration.current.screenWidthDp >= DualPaneMinWidthDp
    val effectiveDualPane = uiState.canEdit && dualPaneEnabled && isWideEditor

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
            excludeTags = excludeTags,
            onExcludeTagsChange = {
                excludeTags = it
                if (it) filterMode = TagMatchMode.OR
            },
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
                excludeTags = false
            },
            onApply = {
                viewModel.applyFilter(
                    MemoFilter(
                        query = "",
                        selectedTags = filterSelectedTags,
                        tagMatchMode = filterMode,
                        excludeTags = excludeTags,
                    ),
                )
            },
            onDismiss = { showFilterSheet = false },
        )
    }

    Scaffold(
        topBar = {
            PaperInkTopAppBar(
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
                    // M2.8：编辑态提供“阅览 / 双栏”切换；双栏态下预览常驻，无需再切换
                    if (uiState.canEdit) {
                        if (isWideEditor) {
                            TextButton(onClick = { dualPaneEnabled = !dualPaneEnabled }) {
                                Text(text = if (effectiveDualPane) "单栏" else "双栏")
                            }
                        }
                        if (!effectiveDualPane) {
                            TextButton(onClick = { previewEnabled = !previewEnabled }) {
                                Text(text = if (previewEnabled) "编辑" else "阅览")
                            }
                        }
                    }
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
                .padding(horizontal = InkSpacing.X16, vertical = InkSpacing.X12)
                // minSdk=33（Android 13）：直接用 Compose 的 IME insets 修正布局。
                // 相比手动读取 insets 再算 padding，这种写法更“布局层”，避免 IME 动画期间频繁触发重组带来的卡顿。
                .imePadding(),
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(InkSpacing.X10),
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
                    InkRetryBanner(
                        message = "同步失败：${uiState.lastSyncError}",
                        retryLabel = "重试同步",
                        onRetry = viewModel::retrySync,
                    )
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
                        horizontalArrangement = Arrangement.spacedBy(InkSpacing.X10),
                        verticalArrangement = Arrangement.spacedBy(InkSpacing.X10),
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
                    // 纯预览态没有输入框，标签联想只在“编辑可见”时出现
                    val editorVisible = !previewEnabled || effectiveDualPane
                    val showSuggestions = editorVisible && tagPrefix != null && uiState.tagSuggestions.isNotEmpty()

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true),
                    ) {
                        when {
                            // 宽屏双栏：左编辑右预览，实时对照
                            effectiveDualPane -> {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.spacedBy(InkSpacing.X12),
                                ) {
                                    HighlightingEditorField(
                                        value = uiState.content,
                                        onValueChange = viewModel::onContentChange,
                                        placeholder = "写点什么…",
                                        modifier = Modifier.weight(1f).fillMaxSize(),
                                    )
                                    EditorMarkdownPreview(
                                        content = uiState.content.text,
                                        modifier = Modifier.weight(1f).fillMaxSize(),
                                    )
                                }
                            }

                            // 单栏阅览：全量 Markdown 阅读渲染
                            previewEnabled -> {
                                EditorMarkdownPreview(
                                    content = uiState.content.text,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            // 单栏编辑：纸面输入 + Markdown 语法着色
                            else -> {
                                HighlightingEditorField(
                                    value = uiState.content,
                                    onValueChange = viewModel::onContentChange,
                                    placeholder = "写点什么…",
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }

                        TagSuggestionPanel(
                            visible = showSuggestions,
                            suggestions = uiState.tagSuggestions,
                            allTags = uiState.allTagStats,
                            onSelect = viewModel::completeTag,
                            modifier =
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(horizontal = InkSpacing.X2, vertical = InkSpacing.X10),
                        )
                    }
                } else {
                    // 只读查看：唯一全量 Markdown 阅读渲染
                    EditorMarkdownPreview(
                        content = uiState.content.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true),
                    )
                }

                if (uiState.attachments.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(vertical = InkSpacing.X4),
                        horizontalArrangement = Arrangement.spacedBy(InkSpacing.X10),
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
                                    // 结构常量：附件缩略图边长，组件几何，非间距尺度
                                    .size(InkSpacing.AttachmentThumbSize)
                                    .clip(InkShape.Chip)
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
                                        // 结构常量：附件覆盖按钮尺寸，对齐缩略图角标几何，非间距尺度
                                        modifier = Modifier.align(Alignment.TopEnd).size(InkSpacing.OverlayThumbBadgeSize),
                                        onClick = { viewModel.removeAttachment(attachment.key) },
                                    ) {
                                        Icon(imageVector = Icons.Filled.Close, contentDescription = "删除附件")
                                    }
                                }

                                if (showReorder) {
                                    if (index > 0) {
                                        IconButton(
                                            // 结构常量：附件覆盖按钮尺寸，对齐缩略图角标几何，非间距尺度
                                            modifier = Modifier.align(Alignment.BottomStart).size(InkSpacing.OverlayThumbBadgeSize),
                                            onClick = { viewModel.moveAttachment(attachment.key, -1) },
                                        ) {
                                            Icon(imageVector = Icons.Filled.ChevronLeft, contentDescription = "左移")
                                        }
                                    }
                                    if (index < uiState.attachments.lastIndex) {
                                        IconButton(
                                            // 结构常量：附件覆盖按钮尺寸，对齐缩略图角标几何，非间距尺度
                                            modifier = Modifier.align(Alignment.BottomEnd).size(InkSpacing.OverlayThumbBadgeSize),
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
                    // 结构常量：工具栏最小高度，组件几何，非间距尺度
                    modifier = Modifier.fillMaxWidth().heightIn(min = InkSpacing.X56),
                ) {
                    // 结构常量：编辑态为印章预留尾部空间；0dp 禁止令牌化
                    val rowEndPadding = if (uiState.canEdit) InkSpacing.EditorRowEndPadding else 0.dp
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
                            // 结构常量：SealButton 固定尺寸，与主页“记”对齐的组件几何，非间距尺度
                            size = InkSpacing.X56,
                            enabled = uiState.canEdit && !uiState.isSaving,
                            onClick = viewModel::save,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(InkSpacing.X6))
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

            Spacer(modifier = Modifier.height(InkSpacing.X10))

            val countMap = remember(allTags) { allTags.associate { it.name to it.count } }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.X10),
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

/**
 * 编辑页阅读预览承载：统一走 MikepenzMarkdown（唯一全量阅读渲染器）。
 * 供“单栏阅览 / 双栏右栏 / 只读查看”三处复用。
 */
@Composable
private fun EditorMarkdownPreview(
    content: String,
    modifier: Modifier = Modifier,
) {
    MikepenzMarkdown(
        markdownText = content,
        placeholder = "写点什么…",
        modifier = modifier,
    )
}

/**
 * M2.8 着色编辑器：ScrollPaper 纸面 + BasicTextField + Markdown 语法着色。
 * 视觉与 ScrollTextField 一致（同样的信纸横线 / 行高 / 占位符），仅多一层
 * VisualTransformation 实时高亮；因 ScrollTextField 不暴露该参数，这里就地组装。
 */
@Composable
private fun HighlightingEditorField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
) {
    val scheme = MaterialTheme.colorScheme
    val transformation =
        remember(scheme) {
            MarkdownHighlightTransformation(
                markerAccentColor = scheme.primary,
                markerDimColor = scheme.outline,
                codeColor = scheme.onSurfaceVariant,
                codeBackground = scheme.surfaceVariant,
                linkColor = scheme.primary,
                quoteColor = scheme.onSurface.copy(alpha = InkBorder.QuoteText),
            )
        }

    // 编辑区正文字号/行高跟随阅读模式；纸面横线节距与文字行高对齐。
    val readingConfig = LocalReadingConfig.current
    val textStyle =
        readingConfig.applyTo(MaterialTheme.typography.bodyLarge).copy(
            color = scheme.onSurface,
        )
    val lineHeight = readingConfig.bodyLineHeight

    ScrollPaper(
        modifier = modifier,
        lineHeight = lineHeight,
    ) { contentModifier ->
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = contentModifier,
            textStyle = textStyle,
            cursorBrush = SolidColor(scheme.primary),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Default,
                ),
            singleLine = false,
            maxLines = Int.MAX_VALUE,
            visualTransformation = transformation,
            decorationBox = { innerTextField ->
                if (value.text.isBlank()) {
                    Text(
                        text = placeholder,
                        style = textStyle,
                        color = scheme.outline,
                    )
                }
                innerTextField()
            },
        )
    }
}

/**
 * M2.8 轻量 Markdown 语法着色。
 *
 * 支持最小标记集：`# ## ###` 标题、`**粗体**`、`*斜体*`、`[文字](url)` 链接、
 * `` `行内代码` ``、`- [ ] / - [x]` 待办、`> ` 引用。
 *
 * 关键约束：
 * - 只附加 SpanStyle、不增删字符，因此 OffsetMapping.Identity，光标不错位；
 * - 所有样式不改字号，保证与纸面横线节距（InkSpacing.LinePitch）对齐；
 * - 任何解析异常都回退原文（degrade gracefully，不崩溃、不染错）。
 */
private class MarkdownHighlightTransformation(
    private val markerAccentColor: androidx.compose.ui.graphics.Color,
    private val markerDimColor: androidx.compose.ui.graphics.Color,
    private val codeColor: androidx.compose.ui.graphics.Color,
    private val codeBackground: androidx.compose.ui.graphics.Color,
    private val linkColor: androidx.compose.ui.graphics.Color,
    private val quoteColor: androidx.compose.ui.graphics.Color,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val styled = runCatching { highlight(text.text) }.getOrElse { text }
        return TransformedText(styled, OffsetMapping.Identity)
    }

    private fun highlight(source: String): AnnotatedString =
        buildAnnotatedString {
            append(source)
            var lineStart = 0
            while (lineStart <= source.length) {
                val lineEnd =
                    source.indexOf('\n', startIndex = lineStart)
                        .let { if (it < 0) source.length else it }
                if (lineEnd > lineStart) {
                    styleLine(source, lineStart, lineEnd)
                }
                lineStart = lineEnd + 1
            }
        }

    private fun AnnotatedString.Builder.styleLine(source: String, start: Int, end: Int) {
        val line = source.substring(start, end)

        // 标题：# / ## / ### —— 井号用朱砂色，标题正文加粗
        HEADING_REGEX.find(line)?.let { m ->
            val markerLen = m.groupValues[1].length
            addStyle(
                SpanStyle(color = markerAccentColor, fontWeight = FontWeight.Bold),
                start,
                start + markerLen,
            )
            addStyle(SpanStyle(fontWeight = FontWeight.Bold), start + markerLen, end)
        }

        // 引用：> 开头整行斜体 + 降饱和
        if (line.startsWith(">")) {
            addStyle(SpanStyle(color = quoteColor, fontStyle = FontStyle.Italic), start, end)
        }

        // 待办：- [ ] / - [x]；勾选框着色，已完成项正文划线降饱和
        TODO_REGEX.find(line)?.let { m ->
            addStyle(
                SpanStyle(color = markerAccentColor, fontWeight = FontWeight.Bold),
                start + m.range.first,
                start + m.range.last + 1,
            )
            val checked = m.groupValues[1].equals("x", ignoreCase = true)
            if (checked && end > start + m.range.last + 1) {
                addStyle(
                    SpanStyle(color = markerDimColor, textDecoration = TextDecoration.LineThrough),
                    start + m.range.last + 1,
                    end,
                )
            }
        }

        // 行内代码：`code` —— 反引号弱化，内容等宽 + 底色
        INLINE_CODE_REGEX.findAll(line).forEach { m ->
            val s = start + m.range.first
            val e = start + m.range.last + 1
            addStyle(SpanStyle(color = markerDimColor), s, s + 1)
            addStyle(SpanStyle(color = markerDimColor), e - 1, e)
            addStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    color = codeColor,
                    background = codeBackground,
                ),
                s + 1,
                e - 1,
            )
        }

        // 粗体：**text** —— 星号弱化，内容加粗
        BOLD_REGEX.findAll(line).forEach { m ->
            val s = start + m.range.first
            val e = start + m.range.last + 1
            addStyle(SpanStyle(color = markerDimColor), s, s + 2)
            addStyle(SpanStyle(color = markerDimColor), e - 2, e)
            addStyle(SpanStyle(fontWeight = FontWeight.Bold), s + 2, e - 2)
        }

        // 斜体：*text* —— 用环视排除粗体的双星号
        ITALIC_REGEX.findAll(line).forEach { m ->
            val s = start + m.range.first
            val e = start + m.range.last + 1
            addStyle(SpanStyle(color = markerDimColor), s, s + 1)
            addStyle(SpanStyle(color = markerDimColor), e - 1, e)
            addStyle(SpanStyle(fontStyle = FontStyle.Italic), s + 1, e - 1)
        }

        // 链接：[文字](url) —— 先整体弱化标记，再给文字上色加下划线
        LINK_REGEX.findAll(line).forEach { m ->
            val s = start + m.range.first
            val e = start + m.range.last + 1
            addStyle(SpanStyle(color = markerDimColor), s, e)
            m.groups[1]?.let { textGroup ->
                addStyle(
                    SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                    start + textGroup.range.first,
                    start + textGroup.range.last + 1,
                )
            }
        }
    }

    private companion object {
        val HEADING_REGEX = Regex("^(#{1,3})\\s+")
        val TODO_REGEX = Regex("^\\s*[-*]\\s+\\[([ xX])\\]")
        val INLINE_CODE_REGEX = Regex("`([^`]+)`")
        val BOLD_REGEX = Regex("\\*\\*([^*]+)\\*\\*")
        val ITALIC_REGEX = Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)")
        val LINK_REGEX = Regex("\\[([^]]+)]\\(([^)\\s]+)\\)")
    }
}

/** 双栏最小可用宽度（dp）：达到后顶栏才出现“双栏”开关。 */
private const val DualPaneMinWidthDp = 840

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
