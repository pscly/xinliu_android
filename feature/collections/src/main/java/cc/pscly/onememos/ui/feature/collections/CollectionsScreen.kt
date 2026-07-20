@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package cc.pscly.onememos.ui.feature.collections

import android.widget.Toast
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.pscly.onememos.domain.model.CollectionItem
import cc.pscly.onememos.domain.model.CollectionItemType
import cc.pscly.onememos.domain.model.CollectionRefType
import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.model.SyncStatus
import cc.pscly.onememos.domain.derived.MarkdownDeriver
import cc.pscly.onememos.domain.tag.TagStat
import cc.pscly.onememos.domain.tag.TagExtractor
import cc.pscly.onememos.domain.tag.TagStats
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.component.InkChip
import cc.pscly.onememos.ui.component.MarkdownPreview
import cc.pscly.onememos.ui.component.ScrollPaperSurface
import cc.pscly.onememos.ui.theme.InkShape
import cc.pscly.onememos.ui.theme.InkSpacing
import cc.pscly.onememos.ui.component.SealButton
import cc.pscly.onememos.ui.component.TagChip
import cc.pscly.onememos.ui.component.TagFilterBottomSheet
import cc.pscly.onememos.ui.filter.TagMatchMode
import cc.pscly.onememos.ui.util.AutoTagLineHider
import cc.pscly.onememos.ui.util.DateTimeFormatter
import cc.pscly.onememos.ui.util.rememberOneMemosHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ColorOption(
    val label: String,
    val value: String?,
    val swatch: Color?,
)

private val COLLECTION_COLORS: List<ColorOption> =
    listOf(
        ColorOption(label = "清除颜色", value = null, swatch = null),
        ColorOption(label = "朱砂", value = "#FF4C39", swatch = parseColorOrNull("#FF4C39")),
        ColorOption(label = "黛蓝", value = "#305169", swatch = parseColorOrNull("#305169")),
        ColorOption(label = "赤金", value = "#F2BE45", swatch = parseColorOrNull("#F2BE45")),
        ColorOption(label = "竹青", value = "#3FA45B", swatch = parseColorOrNull("#3FA45B")),
        ColorOption(label = "墨灰", value = "#3B3A37", swatch = parseColorOrNull("#3B3A37")),
    )

@Composable
fun CollectionsScreen(
    onOpenDrawer: () -> Unit,
    onOpenMemo: (String) -> Unit,
    viewModel: CollectionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberOneMemosHaptics()

    val autoTagKeywords =
        remember(uiState.devAutoTagLineKeywordsRaw) {
            AutoTagLineHider.parseKeywords(uiState.devAutoTagLineKeywordsRaw)
        }
    val showAutoTagLineInHome = uiState.devShowAutoTagLineInHome

    var busy by remember { mutableStateOf(false) }

    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val selectionMode = selectedIds.isNotEmpty()

    var reorderMode by remember { mutableStateOf(false) }
    var reorderIds by remember { mutableStateOf<List<String>>(emptyList()) }

    var selectedTags by remember { mutableStateOf<Set<String>>(emptySet()) }
    var tagMatchMode by remember { mutableStateOf(TagMatchMode.OR) }
    var excludeTags by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    var enableRichPreview by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collectLatest { scrolling ->
                if (scrolling) {
                    enableRichPreview = false
                } else {
                    delay(200)
                    enableRichPreview = true
                }
            }
    }

    var showCreateFolder by remember { mutableStateOf(false) }
    var createFolderName by remember { mutableStateOf("") }

    var renamingItem by remember { mutableStateOf<CollectionItem?>(null) }
    var renameDraft by remember { mutableStateOf("") }

    var showMoveDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }

    val currentParentId = uiState.currentParentId
    val atRoot = currentParentId == null

    BackHandler(enabled = !atRoot) {
        if (busy) return@BackHandler
        selectedIds = emptySet()
        reorderMode = false
        viewModel.navigateUp()
    }

    LaunchedEffect(currentParentId) {
        selectedIds = emptySet()
        selectedTags = emptySet()
        tagMatchMode = TagMatchMode.OR
        excludeTags = false
        showFilterSheet = false
        reorderMode = false
        reorderIds = uiState.items.map { it.id }
    }

    LaunchedEffect(uiState.items, reorderMode) {
        if (!reorderMode) return@LaunchedEffect
        val currentIds = uiState.items.map { it.id }
        val kept = reorderIds.filter { it in currentIds }
        val appended = currentIds.filterNot { it in kept }
        reorderIds = kept + appended
    }

    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    val paperScrollOffsetPx = remember { mutableFloatStateOf(0f) }
    val paperScrollConnection =
        remember {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    paperScrollOffsetPx.floatValue -= consumed.y
                    return Offset.Zero
                }
            }
        }

    val itemById = remember(uiState.items) { uiState.items.associateBy { it.id } }
    val selectedItems = remember(selectedIds, itemById) { selectedIds.mapNotNull { itemById[it] } }
    val selectedFolderIds = remember(selectedItems) {
        selectedItems.asSequence()
            .filter { it.itemType == CollectionItemType.FOLDER }
            .map { it.id }
            .toSet()
    }

    val disabledMoveTargets = remember(selectedFolderIds, uiState.folderParentById, currentParentId) {
        val disabled = HashSet<String>(16)
        disabled.addAll(selectedFolderIds)
        if (!currentParentId.isNullOrBlank()) disabled.add(currentParentId)

        if (selectedFolderIds.isNotEmpty() && uiState.folderParentById.isNotEmpty()) {
            val children = HashMap<String, MutableList<String>>()
            for ((id, parent) in uiState.folderParentById) {
                if (parent != null) {
                    children.getOrPut(parent) { mutableListOf() }.add(id)
                }
            }
            val queue = ArrayDeque<String>()
            selectedFolderIds.forEach { queue.addLast(it) }
            while (queue.isNotEmpty()) {
                val cur = queue.removeFirst()
                val cs = children[cur] ?: continue
                for (c in cs) {
                    if (disabled.add(c)) queue.addLast(c)
                }
            }
        }
        disabled
    }

    val reorderIndex = remember(reorderIds) { reorderIds.withIndex().associate { it.value to it.index } }

    val noteRefMemosInFolder =
        remember(uiState.items, uiState.memoByRefTargetId) {
            uiState.items
                .asSequence()
                .filter { it.itemType == CollectionItemType.NOTE_REF && it.refType == CollectionRefType.MEMOS_MEMO }
                .mapNotNull { item ->
                    val targetIdForOpen = item.refId ?: item.refLocalUuid
                    targetIdForOpen?.takeIf { it.isNotBlank() }?.let { uiState.memoByRefTargetId[it] }
                }
                .toList()
        }
    val allTags by
        produceState<List<TagStat>>(initialValue = emptyList(), noteRefMemosInFolder) {
            value = withContext(Dispatchers.Default) { TagStats.build(noteRefMemosInFolder) }
        }

    val itemsToRender =
        remember(uiState.items, uiState.memoByRefTargetId, reorderMode, reorderIndex, selectedTags, tagMatchMode, excludeTags) {
            val base =
                if (!reorderMode) {
                    uiState.items
                } else {
                    uiState.items.sortedWith(compareBy({ reorderIndex[it.id] ?: Int.MAX_VALUE }, { it.sortOrder }, { it.id }))
                }

            if (selectedTags.isEmpty()) return@remember base

            base.filter { item ->
                when (item.itemType) {
                    CollectionItemType.FOLDER -> true
                    CollectionItemType.NOTE_REF -> {
                        val targetId =
                            if (item.refType == CollectionRefType.MEMOS_MEMO) {
                                item.refId ?: item.refLocalUuid
                            } else {
                                null
                            }
                        val memo = targetId?.takeIf { it.isNotBlank() }?.let { uiState.memoByRefTargetId[it] }

                        if (excludeTags) {
                            if (memo == null) return@filter true
                            val memoTags =
                                if (memo.tags.isNotEmpty()) memo.tags else TagExtractor.extractAll(memo.content)
                            if (memoTags.isEmpty()) return@filter true
                            val memoTagSet = memoTags.toSet()
                            selectedTags.none { memoTagSet.contains(it) }
                        } else {
                            if (memo == null) return@filter false
                            val memoTags =
                                if (memo.tags.isNotEmpty()) memo.tags else TagExtractor.extractAll(memo.content)
                            if (memoTags.isEmpty()) return@filter false
                            val memoTagSet = memoTags.toSet()
                            when (tagMatchMode) {
                                TagMatchMode.OR -> selectedTags.any { memoTagSet.contains(it) }
                                TagMatchMode.AND -> selectedTags.all { memoTagSet.contains(it) }
                            }
                        }
                    }
                }
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = "锦囊")
                        val last = uiState.breadcrumb.lastOrNull()
                        if (last != null && last.id != null) {
                            Text(
                                text = last.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        enabled = !busy,
                        onClick = {
                            if (busy) return@IconButton
                            if (atRoot) {
                                onOpenDrawer()
                            } else {
                                haptics.tick()
                                selectedIds = emptySet()
                                reorderMode = false
                                viewModel.navigateUp()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (atRoot) Icons.Filled.Menu else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (atRoot) "菜单" else "返回",
                        )
                    }
                },
                actions = {
                    if (!uiState.enabled) return@TopAppBar

                    val filterEntryEnabled = !(selectionMode || reorderMode || busy)
                    val hasTagFilter = selectedTags.isNotEmpty()
                    IconButton(
                        enabled = filterEntryEnabled,
                        onClick = { showFilterSheet = true },
                    ) {
                        BadgedBox(
                            badge = {
                                if (hasTagFilter) {
                                    Badge()
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FilterList,
                                contentDescription = "筛选",
                                tint =
                                    if (hasTagFilter) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (selectionMode) {
                        if (selectedIds.size == 1) {
                            IconButton(
                                enabled = !busy,
                                onClick = {
                                    val only = selectedItems.firstOrNull() ?: return@IconButton
                                    renamingItem = only
                                    renameDraft = only.name
                                    selectedIds = emptySet()
                                },
                            ) {
                                Icon(imageVector = Icons.Filled.Edit, contentDescription = "重命名")
                            }
                        }
                        IconButton(
                            enabled = !busy,
                            onClick = { showMoveDialog = true },
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "移动")
                        }
                        IconButton(
                            enabled = !busy,
                            onClick = { showColorDialog = true },
                        ) {
                            Icon(imageVector = Icons.Filled.ColorLens, contentDescription = "改色")
                        }
                        IconButton(
                            enabled = !busy,
                            onClick = { showDeleteConfirm = true },
                        ) {
                            Icon(imageVector = Icons.Filled.Delete, contentDescription = "删除")
                        }
                        IconButton(
                            enabled = !busy,
                            onClick = { selectedIds = emptySet() },
                        ) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = "取消选择")
                        }
                        return@TopAppBar
                    }

                    IconButton(
                        enabled = !busy && !reorderMode,
                        onClick = {
                            showCreateFolder = true
                            createFolderName = ""
                        },
                    ) {
                        Icon(imageVector = Icons.Filled.CreateNewFolder, contentDescription = "新建文件夹")
                    }

                    TextButton(
                        enabled = !busy,
                        onClick = {
                            if (!reorderMode) {
                                haptics.tick()
                                reorderMode = true
                                reorderIds = uiState.items.map { it.id }
                                return@TextButton
                            }

                            val ids = reorderIds
                            busy = true
                            scope.launch {
                                try {
                                    viewModel.reorder(orderedIds = ids)
                                    toast("已更新排序")
                                    reorderMode = false
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    ) {
                        Icon(
                            // 结构常量：操作图标尺寸，组件几何，非间距尺度（勿映射 SheetGapL）
                            modifier = Modifier.size(InkSpacing.X18),
                            imageVector = if (reorderMode) Icons.Filled.Check else Icons.Filled.SwapVert,
                            contentDescription = if (reorderMode) "完成排序" else "排序",
                        )
                        Spacer(modifier = Modifier.width(InkSpacing.X6))
                        Text(text = if (reorderMode) "完成" else "排序")
                    }
                },
            )
        },
    ) { padding ->
        ScrollPaperSurface(
            modifier = Modifier.fillMaxSize().padding(padding).padding(InkSpacing.X12),
            scrollOffsetPx = paperScrollOffsetPx.floatValue,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(InkSpacing.X10),
            ) {
                if (!uiState.enabled) {
                    Text(
                        text = "请先使用 Flow Backend 登录后再使用锦囊；自定义服务器模式暂不支持。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    return@Column
                }

                BreadcrumbBar(
                    breadcrumb = uiState.breadcrumb,
                    enabled = !busy && !selectionMode && !reorderMode,
                    onClick = { id ->
                        haptics.tick()
                        viewModel.goToParent(id)
                    },
                )

                if (selectedTags.isNotEmpty()) {
                    val filterControlsEnabled = !(selectionMode || reorderMode)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(InkSpacing.X10),
                        ) {
                            items(selectedTags.toList(), key = { it }) { t ->
                                TagChip(
                                    tag = t,
                                    selected = true,
                                    onClick =
                                        if (!filterControlsEnabled) {
                                            null
                                        } else {
                                            ({ selectedTags = selectedTags - t })
                                        },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(InkSpacing.X10))
                        InkChip(
                            label = "清除",
                            selected = false,
                            enabled = filterControlsEnabled,
                            onClick = { selectedTags = emptySet() },
                        )
                    }
                }

                if (itemsToRender.isEmpty()) {
                    InkCard(onClick = null) {
                        Text(
                            text = "这里还空着。\n\n你可以：\n- 在这里新建文件夹\n- 回到随笔页长按，把笔记放入锦囊",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(InkSpacing.X12))
                        SealButton(
                            text = "新建文件夹",
                            enabled = !busy,
                            onClick = {
                                showCreateFolder = true
                                createFolderName = ""
                            },
                        )
                    }
                    return@Column
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().nestedScroll(paperScrollConnection),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(InkSpacing.X10),
                    contentPadding = PaddingValues(bottom = InkSpacing.X12),
                ) {
                    items(itemsToRender, key = { it.id }) { item ->
                        val selected = selectedIds.contains(item.id)
                        val index = reorderIndex[item.id] ?: -1

                        // NOTE_REF 的 targetId 必须与点击打开逻辑保持一致：refId ?: refLocalUuid。
                        // 这里仅做 Map 查找，不引入 per-item Flow collect（避免 N+1 Flow）。
                        val noteRefTargetId =
                            if (item.itemType == CollectionItemType.NOTE_REF && item.refType == CollectionRefType.MEMOS_MEMO) {
                                item.refId ?: item.refLocalUuid
                            } else {
                                null
                            }
                        val noteRefMemo =
                            noteRefTargetId
                                ?.takeIf { it.isNotBlank() }
                                ?.let { uiState.memoByRefTargetId[it] }
                        CollectionItemCard(
                            item = item,
                            noteRefTargetId = noteRefTargetId,
                            noteRefMemo = noteRefMemo,
                            enableRichPreview = enableRichPreview,
                            showAutoTagLineInHome = showAutoTagLineInHome,
                            autoTagKeywords = autoTagKeywords,
                            selectedTags = selectedTags,
                            onToggleTag =
                                if (selectionMode || reorderMode) {
                                    null
                                } else {
                                    ({ tag ->
                                        selectedTags =
                                            if (selectedTags.contains(tag)) {
                                                selectedTags - tag
                                            } else {
                                                selectedTags + tag
                                            }
                                    })
                                },
                            selected = selected,
                            selectionMode = selectionMode,
                            reorderMode = reorderMode,
                            canMoveUp = reorderMode && index > 0,
                            canMoveDown = reorderMode && index >= 0 && index < reorderIds.lastIndex,
                            onMoveUp = {
                                if (!reorderMode || busy) return@CollectionItemCard
                                val next = moveId(reorderIds, item.id, delta = -1)
                                if (next != null) {
                                    haptics.tick()
                                    reorderIds = next
                                }
                            },
                            onMoveDown = {
                                if (!reorderMode || busy) return@CollectionItemCard
                                val next = moveId(reorderIds, item.id, delta = 1)
                                if (next != null) {
                                    haptics.tick()
                                    reorderIds = next
                                }
                            },
                            onClick = {
                                if (busy) return@CollectionItemCard
                                if (selectionMode) {
                                    selectedIds = if (selected) selectedIds - item.id else selectedIds + item.id
                                    return@CollectionItemCard
                                }
                                if (reorderMode) return@CollectionItemCard

                                when (item.itemType) {
                                    CollectionItemType.FOLDER -> {
                                        haptics.tick()
                                        viewModel.enterFolder(item.id)
                                    }
                                    CollectionItemType.NOTE_REF -> {
                                        when (item.refType) {
                                            CollectionRefType.MEMOS_MEMO -> {
                                                val target = item.refId ?: item.refLocalUuid
                                                if (target.isNullOrBlank()) {
                                                    toast("该引用尚未同步")
                                                } else {
                                                    onOpenMemo(target)
                                                }
                                            }
                                            CollectionRefType.FLOW_NOTE -> {
                                                toast("Flow 笔记引用暂不支持预览")
                                            }
                                            null -> {
                                                toast("无效引用")
                                            }
                                        }
                                    }
                                }
                            },
                            onLongClick = {
                                if (busy || reorderMode) return@CollectionItemCard
                                haptics.heavyClick()
                                selectedIds = selectedIds + item.id
                            },
                        )
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        val enabled = !(selectionMode || reorderMode || busy)
        TagFilterBottomSheet(
            title = "标签筛选",
            allTags = allTags,
            selectedTags = selectedTags,
            showTagCounts = true,
            tagMatchMode = tagMatchMode,
            excludeTags = excludeTags,
            onExcludeTagsChange = {
                if (enabled) {
                    excludeTags = it
                    if (it) tagMatchMode = TagMatchMode.OR
                }
            },
            onToggleTag = { t ->
                if (enabled) {
                    selectedTags =
                        if (selectedTags.contains(t)) {
                            selectedTags - t
                        } else {
                            selectedTags + t
                        }
                }
            },
            onTagMatchModeChange = {
                if (enabled) {
                    tagMatchMode = it
                }
            },
            onClear = {
                if (enabled) {
                    selectedTags = emptySet()
                    tagMatchMode = TagMatchMode.OR
                    excludeTags = false
                }
            },
            onApply = { showFilterSheet = false },
            onDismiss = { showFilterSheet = false },
        )
    }

    if (showCreateFolder) {
        SimpleTextInputDialog(
            title = "新建文件夹",
            hint = "文件夹名称",
            initial = createFolderName,
            confirmText = "创建",
            busy = busy,
            onDismiss = { if (!busy) showCreateFolder = false },
            onConfirm = { text ->
                val name = text.trim()
                if (name.isBlank()) {
                    toast("请输入文件夹名称")
                    return@SimpleTextInputDialog
                }
                busy = true
                scope.launch {
                    try {
                        val id = viewModel.createFolder(name = name, color = null)
                        if (id.isBlank()) {
                            toast("新建失败：请确认已登录 Flow Backend")
                        } else {
                            toast("已创建")
                        }
                    } finally {
                        busy = false
                        showCreateFolder = false
                    }
                }
            },
        )
    }

    val renaming = renamingItem
    if (renaming != null) {
        SimpleTextInputDialog(
            title = if (renaming.itemType == CollectionItemType.FOLDER) "重命名文件夹" else "设置显示名",
            hint = "名称",
            initial = renameDraft,
            confirmText = "保存",
            busy = busy,
            onDismiss = { if (!busy) renamingItem = null },
            onConfirm = { text ->
                val name = text.trim()
                busy = true
                scope.launch {
                    try {
                        viewModel.rename(id = renaming.id, name = name)
                        toast("已保存")
                    } finally {
                        busy = false
                        renamingItem = null
                    }
                }
            },
        )
    }

    if (showMoveDialog) {
        MoveToFolderDialog(
            folders = uiState.folderOptions,
            disabledFolderIds = disabledMoveTargets,
            busy = busy,
            onDismiss = { if (!busy) showMoveDialog = false },
            onConfirm = { targetParentId ->
                val ids = selectedIds.toList()
                if (ids.isEmpty()) {
                    showMoveDialog = false
                    return@MoveToFolderDialog
                }
                busy = true
                scope.launch {
                    try {
                        viewModel.move(ids = ids, targetParentId = targetParentId)
                        toast("已移动")
                        selectedIds = emptySet()
                    } finally {
                        busy = false
                        showMoveDialog = false
                    }
                }
            },
        )
    }

    if (showColorDialog) {
        ColorPickerDialog(
            options = COLLECTION_COLORS,
            busy = busy,
            onDismiss = { if (!busy) showColorDialog = false },
            onPick = { color ->
                val ids = selectedIds.toList()
                if (ids.isEmpty()) {
                    showColorDialog = false
                    return@ColorPickerDialog
                }
                busy = true
                scope.launch {
                    try {
                        viewModel.recolor(ids = ids, color = color)
                        toast("已改色")
                        selectedIds = emptySet()
                    } finally {
                        busy = false
                        showColorDialog = false
                    }
                }
            },
        )
    }

    if (showDeleteConfirm) {
        val count = selectedIds.size
        ConfirmDialog(
            title = "删除",
            text = if (count <= 1) "确定删除该条目？文件夹会连同子项一起删除。" else "确定删除选中的 $count 项？文件夹会连同子项一起删除。",
            confirmText = "删除",
            busy = busy,
            onDismiss = { if (!busy) showDeleteConfirm = false },
            onConfirm = {
                val ids = selectedIds.toList()
                if (ids.isEmpty()) {
                    showDeleteConfirm = false
                    return@ConfirmDialog
                }
                busy = true
                scope.launch {
                    try {
                        viewModel.batchDelete(ids)
                        toast("已删除")
                        selectedIds = emptySet()
                    } finally {
                        busy = false
                        showDeleteConfirm = false
                    }
                }
            },
        )
    }
}

@Composable
private fun BreadcrumbBar(
    breadcrumb: List<BreadcrumbSegment>,
    enabled: Boolean,
    onClick: (String?) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(InkSpacing.X8),
    ) {
        items(breadcrumb, key = { it.id ?: "root" }) { seg ->
            InkChip(
                label = seg.label,
                selected = false,
                enabled = enabled,
                onClick = { onClick(seg.id) },
            )
        }
    }
}

@Composable
private fun CollectionItemCard(
    item: CollectionItem,
    noteRefTargetId: String?,
    noteRefMemo: Memo?,
    enableRichPreview: Boolean,
    showAutoTagLineInHome: Boolean,
    autoTagKeywords: List<String>,
    selectedTags: Set<String>,
    onToggleTag: ((String) -> Unit)?,
    selected: Boolean,
    selectionMode: Boolean,
    reorderMode: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val icon =
        when (item.itemType) {
            CollectionItemType.FOLDER -> Icons.Filled.Folder
            CollectionItemType.NOTE_REF -> Icons.Filled.Description
        }

    val trimmedName = item.name.trim()
    val displayName = trimmedName.ifBlank { "（无标题）" }
    val color = parseColorOrNull(item.color)

    val meta =
        when (item.itemType) {
            CollectionItemType.FOLDER -> {
                if (item.localOnly) "冲突副本" else "文件夹"
            }

            CollectionItemType.NOTE_REF -> {
                val sync =
                    if (item.refType == CollectionRefType.MEMOS_MEMO && item.refId == null && item.refLocalUuid != null) {
                        "待同步"
                    } else {
                        null
                    }
                val base =
                    when (item.refType) {
                        CollectionRefType.MEMOS_MEMO -> "随笔引用"
                        CollectionRefType.FLOW_NOTE -> "Flow 引用"
                        null -> "引用"
                    }
                listOfNotNull(base, sync, if (item.localOnly) "冲突副本" else null).joinToString(" · ")
            }
        }

    InkCard(
        onClick = onClick,
        onLongClick = if (selectionMode || reorderMode) null else onLongClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(InkSpacing.X24),
                contentAlignment = Alignment.Center,
            ) {
                if (color != null) {
                    Box(
                        modifier = Modifier.size(InkSpacing.X10),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(color = color)
                        }
                    }
                }
            }

            Icon(imageVector = icon, contentDescription = null)
            Spacer(modifier = Modifier.width(InkSpacing.X10))

            Column(modifier = Modifier.weight(1f)) {
                if (item.itemType == CollectionItemType.NOTE_REF && item.refType == CollectionRefType.MEMOS_MEMO) {
                    val memo = noteRefMemo
                    if (trimmedName.isNotBlank()) {
                        Text(
                            text = trimmedName,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(InkSpacing.X8))
                    }

                    if (memo != null && !noteRefTargetId.isNullOrBlank()) {
                        val innerEnabled = !(selectionMode || reorderMode)
                        val showRichPreview = enableRichPreview && innerEnabled

                        val tags =
                            remember(memo.tags, memo.uuid, memo.updatedAt) {
                                if (memo.tags.isNotEmpty()) memo.tags else TagExtractor.extractAll(memo.content)
                            }
                        val visibleTags = remember(tags) { tags.take(5) }
                        val moreTags = (tags.size - visibleTags.size).coerceAtLeast(0)

                        if (visibleTags.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(InkSpacing.X10),
                            ) {
                                items(visibleTags, key = { it }) { t ->
                                    TagChip(
                                        tag = t,
                                        selected = selectedTags.contains(t),
                                        onClick = onToggleTag?.takeIf { innerEnabled }?.let { cb -> ({ cb(t) }) },
                                    )
                                }
                                if (moreTags > 0) {
                                    item(key = "+$moreTags") {
                                        TagChip(tag = "+$moreTags", label = "+$moreTags")
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(InkSpacing.X10))
                        }

                        val allImageThumbModels =
                            remember(memo.attachments, memo.uuid, memo.updatedAt) {
                                memo.attachments
                                    .asSequence()
                                    .filter { it.mimeType?.startsWith("image/") == true }
                                    .mapNotNull { a ->
                                        val cacheModel = usableFileUri(a.cacheUri)
                                        when {
                                            cacheModel != null -> cacheModel
                                            !a.localUri.isNullOrBlank() -> a.localUri
                                            else -> null
                                        }
                                    }
                                    .toList()
                            }
                        val maxThumbs = if (showRichPreview) 2 else 1
                        val imageThumbModels =
                            remember(allImageThumbModels, maxThumbs) {
                                allImageThumbModels.take(maxThumbs.coerceAtLeast(0))
                            }
                        val moreImages = (allImageThumbModels.size - imageThumbModels.size).coerceAtLeast(0)
                        // 结构常量：缩略图解码像素边长（单图 76 / 多图 88），显示与解码对齐的几何，非间距尺度
                        val thumbSizePx =
                            with(LocalDensity.current) {
                                if (imageThumbModels.size == 1) InkSpacing.SingleImageThumbSize.roundToPx() else InkSpacing.GridImageThumbSize.roundToPx()
                            }
                        val hasOneImage = imageThumbModels.size == 1

                        val contentPlaceholder =
                            remember(allImageThumbModels, memo.attachments, memo.uuid, memo.updatedAt) {
                                val hasImages = allImageThumbModels.isNotEmpty()
                                val hasAttachments = memo.attachments.isNotEmpty()
                                when {
                                    hasImages -> "(无文字，含图片)"
                                    hasAttachments -> "(无文字，含附件)"
                                    else -> "(无文字内容)"
                                }
                            }
                        val basePlainPreview =
                            remember(memo.plainPreview, memo.uuid, memo.updatedAt) {
                                memo.plainPreview.ifBlank { MarkdownDeriver.plainPreview(memo.content, maxChars = 320) }
                            }
                        val plainPreview =
                            remember(basePlainPreview, memo.uuid, memo.updatedAt, showAutoTagLineInHome, autoTagKeywords, contentPlaceholder) {
                                val p =
                                    if (showAutoTagLineInHome) {
                                        basePlainPreview
                                    } else {
                                        val keys = autoTagKeywords
                                        when {
                                            keys.isEmpty() || basePlainPreview.isBlank() -> basePlainPreview
                                            keys.any { basePlainPreview.contains(it) } ->
                                                MarkdownDeriver.plainPreviewSkippingLinesEndingWithKeywords(
                                                    markdown = memo.content,
                                                    keywords = keys,
                                                    maxChars = 320,
                                                )

                                            else -> basePlainPreview
                                        }
                                    }
                                p.ifBlank { contentPlaceholder }
                            }

                        if (hasOneImage) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(InkSpacing.X12),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Box(
                                        // 结构常量：单图缩略图显示边长，组件几何，非间距尺度
                                        modifier = Modifier.size(InkSpacing.SingleImageThumbSize).clip(InkShape.Card),
                                ) {
                                    Surface(
                                        modifier = Modifier.matchParentSize(),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                    ) {}
                                    FixedSizeThumbImage(
                                        model = imageThumbModels.first(),
                                        targetSizePx = thumbSizePx,
                                        contentDescription = "图片预览",
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    if (moreImages > 0) {
                                        Surface(
                                            modifier = Modifier.matchParentSize(),
                                            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f),
                                            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(text = "+$moreImages", style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    }
                                }

                                Box(modifier = Modifier.weight(1f)) {
                                    if (showRichPreview) {
                                        val displayMarkdown =
                                            remember(memo.uuid, memo.updatedAt, showAutoTagLineInHome, autoTagKeywords) {
                                                if (showAutoTagLineInHome) memo.content else AutoTagLineHider.hideFast(memo.content, autoTagKeywords)
                                            }
                                        MarkdownPreview(
                                            markdown = displayMarkdown,
                                            placeholder = contentPlaceholder,
                                            maxBlocks = 3,
                                            maxLines = 4,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    } else {
                                        Text(
                                            text = plainPreview,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 4,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                        } else {
                            if (showRichPreview) {
                                val displayMarkdown =
                                    remember(memo.uuid, memo.updatedAt, showAutoTagLineInHome, autoTagKeywords) {
                                        if (showAutoTagLineInHome) memo.content else AutoTagLineHider.hideFast(memo.content, autoTagKeywords)
                                    }
                                MarkdownPreview(
                                    markdown = displayMarkdown,
                                    placeholder = contentPlaceholder,
                                    maxBlocks = 4,
                                    maxLines = 6,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                Text(
                                    text = plainPreview,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 6,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            if (imageThumbModels.size >= 2) {
                                Spacer(modifier = Modifier.height(InkSpacing.X10))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(InkSpacing.X10),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    imageThumbModels.forEach { model ->
                                        Box(
                                                // 结构常量：多图缩略图显示边长，组件几何，非间距尺度
                                                modifier = Modifier.size(InkSpacing.GridImageThumbSize).clip(InkShape.Chip),
                                        ) {
                                            Surface(
                                                modifier = Modifier.matchParentSize(),
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                            ) {}
                                            FixedSizeThumbImage(
                                                model = model,
                                                targetSizePx = thumbSizePx,
                                                contentDescription = "图片预览",
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }
                                    }
                                    if (moreImages > 0) {
                                        Surface(
                                            // 结构常量：多图“+N”占位边长，与缩略图同组几何，非间距尺度
                                            modifier = Modifier.size(InkSpacing.GridImageThumbSize),
                                            shape = InkShape.Chip,
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(text = "+$moreImages", style = MaterialTheme.typography.titleMedium)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(InkSpacing.X10))
                        val statusText =
                            when (memo.syncStatus) {
                                SyncStatus.LOCAL_ONLY -> "仅本地"
                                SyncStatus.DIRTY -> "待同步"
                                SyncStatus.SYNCING -> "同步中"
                                SyncStatus.SYNCED -> "已同步"
                                SyncStatus.FAILED -> "失败"
                            }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color =
                                    when (memo.syncStatus) {
                                        SyncStatus.LOCAL_ONLY -> MaterialTheme.colorScheme.outline
                                        SyncStatus.DIRTY -> MaterialTheme.colorScheme.secondary
                                        SyncStatus.SYNCING -> MaterialTheme.colorScheme.secondary
                                        SyncStatus.SYNCED -> MaterialTheme.colorScheme.outline
                                        SyncStatus.FAILED -> MaterialTheme.colorScheme.error
                                    },
                                maxLines = 1,
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = DateTimeFormatter.formatYmdHm(memo.createdAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                            )
                        }
                    } else {
                        Text(
                            text = "引用的随笔不可用/待同步",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(InkSpacing.X8))
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (reorderMode) {
                Column(horizontalAlignment = Alignment.End) {
                    IconButton(enabled = canMoveUp, onClick = onMoveUp) {
                        Icon(imageVector = Icons.Filled.KeyboardArrowUp, contentDescription = "上移")
                    }
                    IconButton(enabled = canMoveDown, onClick = onMoveDown) {
                        Icon(imageVector = Icons.Filled.KeyboardArrowDown, contentDescription = "下移")
                    }
                }
            } else if (selectionMode) {
                Spacer(modifier = Modifier.width(InkSpacing.X8))
                Icon(
                    imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = if (selected) "已选" else "未选",
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun SimpleTextInputDialog(
    title: String,
    hint: String,
    initial: String,
    confirmText: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                enabled = !busy,
                label = { Text(hint) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = { onConfirm(draft) },
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = onDismiss,
            ) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = onConfirm,
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = onDismiss,
            ) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun MoveToFolderDialog(
    folders: List<FolderOption>,
    disabledFolderIds: Set<String>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var selectedParentId by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移动到") },
        text = {
            LazyColumn(
                // 结构常量：移动弹层列表最大高度，弹层结构几何，非间距尺度
                modifier = Modifier.fillMaxWidth().heightIn(max = InkSpacing.CollectionsDialogMaxHeight),
                verticalArrangement = Arrangement.spacedBy(InkSpacing.X2),
            ) {
                item(key = "root") {
                    FolderPickRow(
                        selected = selectedParentId == null,
                        enabled = !busy,
                        depth = 0,
                        title = "根目录（顶层）",
                        onClick = { selectedParentId = null },
                    )
                }
                items(folders, key = { it.id }) { f ->
                    FolderPickRow(
                        selected = selectedParentId == f.id,
                        enabled = !busy && !disabledFolderIds.contains(f.id),
                        depth = f.depth,
                        title = f.name,
                        onClick = { selectedParentId = f.id },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = { onConfirm(selectedParentId) },
            ) {
                Text("移动")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = onDismiss,
            ) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun FolderPickRow(
    selected: Boolean,
    enabled: Boolean,
    depth: Int,
    title: String,
    onClick: () -> Unit,
) {
    val prefix = remember(depth) { "  ".repeat(depth.coerceAtMost(8)) }
    TextButton(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = prefix + title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ColorPickerDialog(
    options: List<ColorOption>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择颜色") },
        text = {
            LazyColumn(
                // 结构常量：颜色弹层列表最大高度，弹层结构几何，非间距尺度
                modifier = Modifier.fillMaxWidth().heightIn(max = InkSpacing.CollectionsDialogMaxHeight),
                verticalArrangement = Arrangement.spacedBy(InkSpacing.X2),
            ) {
                items(options, key = { it.label }) { opt ->
                    TextButton(
                        enabled = !busy,
                        onClick = { onPick(opt.value) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (opt.swatch != null) {
                                androidx.compose.foundation.Canvas(modifier = Modifier.size(InkSpacing.X12)) {
                                    drawCircle(color = opt.swatch)
                                }
                                Spacer(modifier = Modifier.width(InkSpacing.X10))
                            } else {
                                // 结构常量：无色块时对齐占位宽，与色点+间距同组几何，非间距尺度
                                Spacer(modifier = Modifier.width(InkSpacing.X22))
                            }
                            Text(text = opt.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = onDismiss,
            ) {
                Text("关闭")
            }
        },
    )
}

private fun moveId(
    ids: List<String>,
    targetId: String,
    delta: Int,
): List<String>? {
    val from = ids.indexOf(targetId)
    if (from < 0) return null
    val to = (from + delta).coerceIn(0, ids.lastIndex)
    if (to == from) return null
    val list = ids.toMutableList()
    val v = list.removeAt(from)
    list.add(to, v)
    return list.toList()
}

private fun parseColorOrNull(raw: String?): Color? {
    val s = raw?.trim().orEmpty()
    if (!s.startsWith("#")) return null
    return runCatching { Color(android.graphics.Color.parseColor(s)) }.getOrNull()
}

private fun usableFileUri(uri: String?): String? {
    if (uri.isNullOrBlank()) return null
    return runCatching {
        val parsed = android.net.Uri.parse(uri)
        if (!parsed.scheme.equals("file", ignoreCase = true)) return@runCatching null
        val path = parsed.path ?: return@runCatching null
        if (path.isBlank()) null else uri
    }.getOrNull()
}

@Composable
private fun FixedSizeThumbImage(
    model: String,
    targetSizePx: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, model, targetSizePx) {
        value =
            withContext(Dispatchers.IO) {
                decodeThumbImageBitmap(context, model, targetSizePx)
            }
    }
    val b = bitmap ?: return
    Image(
        bitmap = b,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}

private fun decodeThumbImageBitmap(
    context: android.content.Context,
    model: String,
    targetSizePx: Int,
): ImageBitmap? {
    if (model.isBlank()) return null
    if (targetSizePx <= 0) return null

    val uri = runCatching { Uri.parse(model) }.getOrNull() ?: return null
    return runCatching {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }
        val outW = bounds.outWidth
        val outH = bounds.outHeight
        if (outW <= 0 || outH <= 0) return@runCatching null

        val sample = computeInSampleSize(outW, outH, targetSizePx, targetSizePx)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }
        val bmp = resolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, opts)
        } ?: return@runCatching null
        bmp.asImageBitmap()
    }.getOrNull()
}

private fun computeInSampleSize(
    width: Int,
    height: Int,
    reqWidth: Int,
    reqHeight: Int,
): Int {
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        var halfH = height / 2
        var halfW = width / 2
        while ((halfH / inSampleSize) >= reqHeight && (halfW / inSampleSize) >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize.coerceAtLeast(1)
}
