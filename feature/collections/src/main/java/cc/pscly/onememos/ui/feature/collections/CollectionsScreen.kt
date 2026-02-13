@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package cc.pscly.onememos.ui.feature.collections

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.pscly.onememos.domain.model.CollectionItem
import cc.pscly.onememos.domain.model.CollectionItemType
import cc.pscly.onememos.domain.model.CollectionRefType
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.component.InkChip
import cc.pscly.onememos.ui.component.ScrollPaperSurface
import cc.pscly.onememos.ui.component.SealButton
import cc.pscly.onememos.ui.util.rememberOneMemosHaptics
import kotlinx.coroutines.launch

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

    var busy by remember { mutableStateOf(false) }

    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val selectionMode = selectedIds.isNotEmpty()

    var reorderMode by remember { mutableStateOf(false) }
    var reorderIds by remember { mutableStateOf<List<String>>(emptyList()) }

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
    val itemsToRender =
        remember(uiState.items, reorderMode, reorderIndex) {
            if (!reorderMode) {
                uiState.items
            } else {
                uiState.items.sortedWith(compareBy({ reorderIndex[it.id] ?: Int.MAX_VALUE }, { it.sortOrder }, { it.id }))
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
                            modifier = Modifier.size(18.dp),
                            imageVector = if (reorderMode) Icons.Filled.Check else Icons.Filled.SwapVert,
                            contentDescription = if (reorderMode) "完成排序" else "排序",
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = if (reorderMode) "完成" else "排序")
                    }
                },
            )
        },
    ) { padding ->
        ScrollPaperSurface(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
            scrollOffsetPx = paperScrollOffsetPx.floatValue,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
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

                if (itemsToRender.isEmpty()) {
                    InkCard(onClick = null) {
                        Text(
                            text = "这里还空着。\n\n你可以：\n- 在这里新建文件夹\n- 回到随笔页长按，把笔记放入锦囊",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
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
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    items(itemsToRender, key = { it.id }) { item ->
                        val selected = selectedIds.contains(item.id)
                        val index = reorderIndex[item.id] ?: -1
                        CollectionItemCard(
                            item = item,
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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

    val name = item.name.trim().ifBlank { "（无标题）" }
    val color = parseColorOrNull(item.color)

    InkCard(
        onClick = onClick,
        onLongClick = if (selectionMode || reorderMode) null else onLongClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (color != null) {
                    Box(
                        modifier = Modifier.size(10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(color = color)
                        }
                    }
                }
            }

            Icon(imageVector = icon, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                )

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

                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
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
                Spacer(modifier = Modifier.width(8.dp))
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
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
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
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
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
                                androidx.compose.foundation.Canvas(modifier = Modifier.size(12.dp)) {
                                    drawCircle(color = opt.swatch)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                            } else {
                                Spacer(modifier = Modifier.width(22.dp))
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
