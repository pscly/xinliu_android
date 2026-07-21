@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)

package cc.pscly.onememos.ui.feature.home

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import cc.pscly.onememos.core.network.MemosUrls
import cc.pscly.onememos.domain.derived.MarkdownDeriver
import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.model.MemoServerState
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.component.InkEmpty
import cc.pscly.onememos.ui.component.InkError
import cc.pscly.onememos.ui.component.InkLoading
import cc.pscly.onememos.ui.component.InkRetryBanner
import cc.pscly.onememos.ui.component.MarkdownPreview
import cc.pscly.onememos.ui.component.SealButton
import cc.pscly.onememos.ui.component.SealIconButton
import cc.pscly.onememos.ui.component.TagChip
import cc.pscly.onememos.ui.component.TagFilterBottomSheet
import cc.pscly.onememos.domain.tag.TagExtractor
import cc.pscly.onememos.domain.model.SyncStatus
import cc.pscly.onememos.domain.model.GlobalSyncState
import cc.pscly.onememos.domain.model.ListLayout
import cc.pscly.onememos.domain.model.SwipeAction
import cc.pscly.onememos.domain.model.ThemeDensity
import coil.compose.AsyncImage
import coil.request.ImageRequest
import cc.pscly.onememos.ui.util.DateTimeFormatter
import cc.pscly.onememos.ui.util.AutoTagLineHider
import cc.pscly.onememos.ui.util.OneMemosHaptics
import cc.pscly.onememos.ui.util.rememberOneMemosHaptics
import cc.pscly.onememos.ui.theme.InkSpacing
import cc.pscly.onememos.ui.theme.InkBorder
import cc.pscly.onememos.ui.theme.InkShape
import cc.pscly.onememos.ui.theme.LocalThemeDensity
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import cc.pscly.onememos.ui.theme.PaperInkModalBottomSheet
import cc.pscly.onememos.ui.theme.PaperInkSnackbarHost
import cc.pscly.onememos.ui.theme.PaperInkTopAppBar
import cc.pscly.onememos.navigation.memoSharedBounds

@Composable
fun HomeScreen(
    title: String,
    mode: HomeScreenMode,
    onOpenDrawer: () -> Unit,
    onOpenAuth: () -> Unit,
    onCreateMemo: () -> Unit,
    onOpenMemo: (String) -> Unit,
    onOpenShareCard: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val globalSyncState by viewModel.globalSyncState.collectAsStateWithLifecycle()
    val batchBusy by viewModel.batchBusy.collectAsStateWithLifecycle()
    var showFilterSheet by remember { mutableStateOf(false) }
    var showSearchPopup by remember { mutableStateOf(false) }
    var moreActionsTarget by remember { mutableStateOf<Memo?>(null) }
    var addToCollectionsTarget by remember { mutableStateOf<Memo?>(null) }
    var showAddToCollectionsBatchDialog by remember { mutableStateOf(false) }
    var showCollectionsDisabledDialog by remember { mutableStateOf(false) }
    var showBatchArchiveOrRestoreConfirm by remember { mutableStateOf(false) }
    var selectionState by remember { mutableStateOf(HomeSelectionState()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val (initialIndex, initialOffset) = viewModel.peekListPosition()
    val listState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = initialIndex,
            initialFirstVisibleItemScrollOffset = initialOffset,
        )
    // 双列（宽屏/COMPACT）模式使用的瀑布流状态；与 listState 互不共享。
    val gridState = rememberLazyStaggeredGridState()
    val scope = rememberCoroutineScope()
    val haptics = rememberOneMemosHaptics()
    val selectionMode = selectionState.selectionMode
    val context = LocalContext.current

    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is HomeEvent.BatchActionFinished -> {
                    val summary = event.summary
                    toast("成功 ${summary.successCount} 条，失败 ${summary.failedCount} 条")
                    selectionState = selectionState.exit()
                    showBatchArchiveOrRestoreConfirm = false
                }

                is HomeEvent.ShareTextReady -> {
                    val i =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            // 注意：全文合并后可能非常大；若遇到 TransactionTooLargeException，可改为“先导出文件再分享”。
                            putExtra(Intent.EXTRA_TEXT, event.text)
                        }
                    runCatching {
                        context.startActivity(Intent.createChooser(i, "分享随笔"))
                    }.onFailure {
                        toast("没有可用的分享方式")
                    }
                    selectionState = selectionState.exit()
                }

                is HomeEvent.SwipeArchived -> {
                    // 归档撤销：单独起协程，避免 showSnackbar 挂起阻塞后续事件。
                    launch {
                        val result =
                            snackbarHostState.showSnackbar(
                                message = "已归档",
                                actionLabel = "撤销",
                                withDismissAction = true,
                            )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.unarchiveMemo(event.uuid)
                        }
                    }
                }

                is HomeEvent.SwipeActionMessage -> {
                    toast(event.text)
                }
            }
        }
    }

    BackHandler(enabled = selectionMode) {
        selectionState = selectionState.exit()
    }

    // 1B：滚动中仅渲染纯文本预览，停稳 ~200ms 后再切回 Markdown 样式预览。
    // 单列/双列各持有一份滚动状态，任一滚动都视为“滚动中”。
    var enableRichPreview by remember { mutableStateOf(false) }
    LaunchedEffect(listState, gridState) {
        snapshotFlow { listState.isScrollInProgress || gridState.isScrollInProgress }
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
    val showScrollToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 200 ||
                gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 200
        }
    }
    val hasQuery = uiState.filter.query.trim().isNotBlank()
    val hasTagFilter = uiState.filter.selectedTags.isNotEmpty()

    LaunchedEffect(mode, uiState.filter.query, uiState.filter.selectedTags) {
        if (!selectionState.selectionMode) return@LaunchedEffect
        selectionState = selectionState.exit()
        moreActionsTarget = null
        showBatchArchiveOrRestoreConfirm = false
    }

    // 启动体验：先渲染本地列表，再后台触发同步（避免冷启动白屏/卡顿）。
    var autoSyncTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isLoggedIn, uiState.serverBase) {
        if (!autoSyncTriggered && uiState.isLoggedIn && !uiState.serverBase.isNullOrBlank()) {
            autoSyncTriggered = true
            delay(800)
            viewModel.requestSync()
        }
    }

    val activeItems = viewModel.activePaging.collectAsLazyPagingItems()
    val archivedItems = viewModel.archivedPaging.collectAsLazyPagingItems()
    val pagingItems =
        when (mode) {
            HomeScreenMode.ACTIVE -> activeItems
            HomeScreenMode.ARCHIVED -> archivedItems
        }

    // 宽屏自适应（M2.4）：按可用宽度 + listLayout 设置决定单列/双列。
    // AUTO=随宽度自适应；SINGLE=强制单列；DOUBLE=强制双列。
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useGrid =
        when (uiState.listLayout) {
            ListLayout.AUTO -> maxWidth >= InkSpacing.TwoColumnMinWidth
            ListLayout.SINGLE -> false
            ListLayout.DOUBLE -> true
        }

    // 记录当前列表位置：从详情页返回时恢复到进入前的位置。
    fun captureCurrentListPosition() {
        if (useGrid) {
            viewModel.captureListPosition(
                index = gridState.firstVisibleItemIndex,
                offset = gridState.firstVisibleItemScrollOffset,
            )
        } else {
            viewModel.captureListPosition(
                index = listState.firstVisibleItemIndex,
                offset = listState.firstVisibleItemScrollOffset,
            )
        }
    }

    // 从详情页返回时：恢复到进入前的列表位置（只在需要时触发一次）。
    LaunchedEffect(pagingItems.itemCount, useGrid) {
        val pos = viewModel.pendingRestoreListPosition() ?: return@LaunchedEffect
        val (idx, off) = pos
        // Paging 可能先加载少量 item；只有当目标 index 已可用时才真正恢复，并清除 pending。
        if (idx >= 0 && pagingItems.itemCount > idx) {
            if (useGrid) {
                gridState.scrollToItem(idx, off)
            } else {
                listState.scrollToItem(idx, off)
            }
            viewModel.markListPositionRestored()
        }
    }

    val refreshState = pagingItems.loadState.refresh
    val isRefreshing = refreshState is LoadState.Loading
    val refreshError = (refreshState as? LoadState.Error)?.error
    val hasItems = pagingItems.itemCount > 0
    val isSyncing = globalSyncState.isSyncing

    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        snackbarHost = { PaperInkSnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column {
                PaperInkTopAppBar(
                    title = {
                        Text(
                            text = if (selectionMode) "已选 ${selectionState.selectedIds.size}" else title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (selectionMode) {
                                    haptics.tick()
                                    selectionState = selectionState.exit()
                                } else {
                                    onOpenDrawer()
                                }
                            },
                        ) {
                            Icon(
                                imageVector = if (selectionMode) Icons.Filled.Close else Icons.Filled.Menu,
                                contentDescription = if (selectionMode) "退出多选" else "菜单",
                            )
                        }
                    },
                    actions = {
                        if (selectionMode) return@PaperInkTopAppBar
                        IconButton(onClick = viewModel::requestSync, enabled = !isSyncing) {
                            if (isSyncing) {
                                // 顶栏同步按钮加载态：转圈尺寸为结构常量，非间距尺度（M4 豁免保留）
                                CircularProgressIndicator(modifier = Modifier.size(InkSpacing.X20), strokeWidth = InkBorder.SpinnerStroke)
                            } else {
                                Icon(imageVector = Icons.Filled.Refresh, contentDescription = "同步")
                            }
                        }
                        IconButton(onClick = { showFilterSheet = true }) {
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
                        IconButton(onClick = { showSearchPopup = true }) {
                            BadgedBox(
                                badge = {
                                    if (hasQuery) {
                                        Badge()
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = "搜索",
                                    tint =
                                        if (hasQuery) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                )

                val showSyncBanner =
                    uiState.isLoggedIn &&
                        (
                            globalSyncState.isSyncing ||
                                globalSyncState.isEnqueued ||
                                globalSyncState.pendingCount > 0 ||
                                globalSyncState.hasError ||
                                !globalSyncState.networkOnline
                        )
                SyncStatusBanner(
                    visible = showSyncBanner,
                    state = globalSyncState,
                    onRetrySync = viewModel::requestSync,
                    onOpenAuth = onOpenAuth,
                )

                FilterStatusBanner(
                    visible = uiState.isFiltering,
                    query = uiState.filter.query,
                    tags = uiState.filter.selectedTags.toList(),
                    regexEnabled = uiState.regexSearchEnabled,
                    onClearAll = viewModel::clearFilter,
                    onClearQuery = { viewModel.setQuery("") },
                    onEditQuery = { showSearchPopup = true },
                    onToggleTag = viewModel::toggleTag,
                )
            }
        },
        bottomBar = {
            if (!selectionMode) return@Scaffold

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = InkSpacing.X2,
                shadowElevation = InkSpacing.X8,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = InkSpacing.X16, vertical = InkSpacing.X10),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(InkSpacing.X10),
                ) {
                    Text(
                        text = "操作区",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(
                        enabled = !batchBusy,
                        onClick = {
                            haptics.tick()
                            selectionState = selectionState.exit()
                        },
                    ) {
                        Text("取消")
                    }

                    TextButton(
                        enabled = !batchBusy,
                        onClick = {
                            haptics.tick()
                            showBatchArchiveOrRestoreConfirm = true
                        },
                    ) {
                        Text(if (mode == HomeScreenMode.ACTIVE) "归档" else "恢复")
                    }

                    TextButton(
                        enabled = !batchBusy,
                        onClick = {
                            haptics.tick()
                            if (uiState.collectionsEnabled) {
                                showAddToCollectionsBatchDialog = true
                            } else {
                                showCollectionsDisabledDialog = true
                            }
                        },
                    ) {
                        Text("放入锦囊")
                    }

                    TextButton(
                        enabled = !batchBusy,
                        onClick = {
                            if (selectionState.selectedIds.isEmpty()) {
                                toast("未选择任何随笔")
                                return@TextButton
                            }
                            haptics.tick()
                            viewModel.requestBatchShareMergedText(selectionState.selectedIds)
                        },
                    ) {
                        Text("分享")
                    }
                }
            }
        },
        floatingActionButton = {
            // 浏览→新建：FAB 组靠后朗读，回到顶部先于「记」
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(InkSpacing.X10),
                modifier =
                    Modifier
                        .testTag("home_fab_group")
                        .semantics {
                            isTraversalGroup = true
                            traversalIndex = 1f
                        },
            ) {
                AnimatedVisibility(visible = showScrollToTop) {
                    SealIconButton(
                        icon = Icons.Filled.KeyboardArrowUp,
                        contentDescription = "回到顶部",
                        modifier = Modifier.semantics { traversalIndex = 0f },
                        onClick = {
                            scope.launch {
                                if (useGrid) {
                                    gridState.animateScrollToItem(0)
                                } else {
                                    listState.animateScrollToItem(0)
                                }
                            }
                        },
                    )
                }
                SealButton(
                    text = "记",
                    contentDescription = "新建随笔",
                    modifier =
                        Modifier
                            .testTag("home_fab_create")
                            .semantics { traversalIndex = 1f },
                    onClick = {
                        captureCurrentListPosition()
                        onCreateMemo()
                    },
                )
            }
        },
    ) { padding ->
        moreActionsTarget?.let { target ->
            PaperInkModalBottomSheet(
                onDismissRequest = { moreActionsTarget = null },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = InkSpacing.X20),
                    verticalArrangement = Arrangement.spacedBy(InkSpacing.X10),
                ) {
                    Text(
                        text = "更多操作",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "选择一个操作。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    InkCard(
                        onClick = {
                            haptics.tick()
                            moreActionsTarget = null
                            if (uiState.collectionsEnabled) {
                                addToCollectionsTarget = target
                            } else {
                                showCollectionsDisabledDialog = true
                            }
                        },
                        onLongClick = null,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "放入锦囊",
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                            )
                        }
                    }

                    InkCard(
                        onClick = {
                            haptics.tick()
                            moreActionsTarget = null
                            onOpenShareCard(target.uuid)
                        },
                        onLongClick = null,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "墨迹卡片",
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = {
                                haptics.tick()
                                moreActionsTarget = null
                            },
                        ) {
                            Text("取消")
                        }
                    }

                    Spacer(modifier = Modifier.height(InkSpacing.X10))
                }
            }
        }

        addToCollectionsTarget?.let { target ->
            AddToCollectionsDialog(
                memo = target,
                onDismiss = { addToCollectionsTarget = null },
            )
        }

        if (showAddToCollectionsBatchDialog) {
            val selectedIds = selectionState.selectedIds
            val selectedMemosSnapshot = pagingItems.itemSnapshotList.items.filter { it.uuid in selectedIds }
            AddToCollectionsBatchDialog(
                totalSelectedCount = selectedIds.size,
                selectedMemos = selectedMemosSnapshot,
                onAllSuccess = {
                    selectionState = selectionState.exit()
                },
                onDismiss = { showAddToCollectionsBatchDialog = false },
            )
        }

        if (showBatchArchiveOrRestoreConfirm) {
            val count = selectionState.selectedIds.size
            val actionText = if (mode == HomeScreenMode.ACTIVE) "归档" else "恢复"
            val titleText = if (mode == HomeScreenMode.ACTIVE) "确认归档 $count 条？" else "恢复到随笔 $count 条？"
            val bodyText =
                if (mode == HomeScreenMode.ACTIVE) {
                    "归档后将从“随笔”隐藏，可在“已归档”中恢复。（共 $count 条）"
                } else {
                    "恢复后会重新出现在“随笔”列表，并会自动同步到服务器。（共 $count 条）"
                }

            AlertDialog(
                onDismissRequest = { if (!batchBusy) showBatchArchiveOrRestoreConfirm = false },
                title = { Text(titleText) },
                text = { Text(bodyText) },
                confirmButton = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (batchBusy) {
                            // 批量确认按钮加载态：转圈尺寸为结构常量，非间距尺度（M4 豁免保留）
                            CircularProgressIndicator(
                                strokeWidth = InkBorder.SpinnerStroke,
                                modifier = Modifier.size(InkSpacing.X18),
                            )
                            Spacer(modifier = Modifier.width(InkSpacing.X10))
                        }
                        TextButton(
                            enabled = !batchBusy,
                            onClick = {
                                haptics.confirm()
                                viewModel.requestBatchArchiveOrRestore(mode = mode, selectedIds = selectionState.selectedIds)
                            },
                        ) {
                            Text(actionText)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !batchBusy,
                        onClick = { showBatchArchiveOrRestoreConfirm = false },
                    ) {
                        Text("取消")
                    }
                },
            )
        }

        if (showCollectionsDisabledDialog) {
            AlertDialog(
                onDismissRequest = { showCollectionsDisabledDialog = false },
                title = { Text("锦囊不可用") },
                text = { Text("请先使用 Flow Backend 登录后再使用锦囊；自定义服务器模式暂不支持。") },
                confirmButton = {
                    TextButton(onClick = { showCollectionsDisabledDialog = false }) {
                        Text("知道了")
                    }
                },
            )
        }

        if (showSearchPopup) {
            SearchPopup(
                query = uiState.filter.query,
                regexEnabled = uiState.regexSearchEnabled,
                errorText = uiState.searchError,
                onQueryChange = viewModel::setQuery,
                onClear = { viewModel.setQuery("") },
                onDismiss = { showSearchPopup = false },
            )
        }

        if (showFilterSheet) {
            TagFilterBottomSheet(
                title = "标签筛选",
                allTags = uiState.tagStats,
                selectedTags = uiState.filter.selectedTags,
                showTagCounts = uiState.showTagCountsInFilter,
                tagMatchMode = uiState.filter.tagMatchMode,
                excludeTags = uiState.filter.excludeTags,
                onExcludeTagsChange = viewModel::setExcludeTags,
                onToggleTag = viewModel::toggleTag,
                onTagMatchModeChange = viewModel::setTagMatchMode,
                onClear = viewModel::clearFilter,
                onApply = null,
                onDismiss = { showFilterSheet = false },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 密度轴：按 LocalThemeDensity 调整列表留白与间距
            val density = LocalThemeDensity.current
            val (horizontalPad, verticalPad, itemGap) =
                remember(density) {
                    when (density) {
                        ThemeDensity.COMPACT -> Triple(InkSpacing.X8, InkSpacing.X8, InkSpacing.X8)
                        ThemeDensity.STANDARD -> Triple(InkSpacing.X16, InkSpacing.X12, InkSpacing.X12)
                        ThemeDensity.RELAXED -> Triple(InkSpacing.X24, InkSpacing.X20, InkSpacing.X20)
                    }
                }
            val isEmpty = !isRefreshing && refreshError == null && !hasItems

            // FAB 组（回到顶部 + 「记」）悬浮在列表右下角，底部内容会被遮挡；
            // 因此列表/网格的底部 contentPadding 需要额外预留 FAB 组的高度净空。
            val fabBottomClearance = HomeFabClearance.fabBottomClearance(showScrollToTop)

            if (useGrid) {
                // 双列瀑布流：DateHeader 跨整行（FullLine），memo 占单列。
                // 令牌：列间距 InkSpacing.X12，项间距 InkSpacing.CardPadding。
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .testTag("home_memo_list")
                            .semantics {
                                isTraversalGroup = true
                                traversalIndex = 0f
                            },
                    state = gridState,
                    // 底部追加 FAB 净空，避免最后一项被悬浮按钮遮挡
                    contentPadding = PaddingValues(
                        start = horizontalPad,
                        end = horizontalPad,
                        top = verticalPad,
                        bottom = verticalPad + fabBottomClearance,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(InkSpacing.X12),
                    verticalItemSpacing = InkSpacing.CardPadding,
                ) {
                    if (isRefreshing && !hasItems) {
                        item(key = "loading", span = StaggeredGridItemSpan.FullLine) {
                            HomeListLoadingItem()
                        }
                    } else if (refreshError != null && !hasItems) {
                        item(key = "loadError", span = StaggeredGridItemSpan.FullLine) {
                            HomeListErrorItem(
                                message = refreshError.message ?: "未知错误",
                                onRetry = { pagingItems.retry() },
                                onSync = viewModel::requestSync,
                            )
                        }
                    } else if (isEmpty) {
                        item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
                            HomeListEmptyItem(isFiltering = uiState.isFiltering)
                        }
                    } else {
                        // 将 pagingItems 按日历日期分组，插入 DateHeader
                        val groupedItems = buildGroupedItems(pagingItems)

                        items(
                            count = groupedItems.size,
                            key = { index -> groupedItems[index].stableKey() },
                            contentType = { index -> groupedItems[index].contentType() },
                            span = { index ->
                                if (groupedItems[index] is GroupedListItem.DateHeader) {
                                    StaggeredGridItemSpan.FullLine
                                } else {
                                    StaggeredGridItemSpan.SingleLane
                                }
                            },
                        ) { index ->
                            GroupedItemContent(
                                item = groupedItems[index],
                                uiState = uiState,
                                mode = mode,
                                enableRichPreview = enableRichPreview,
                                selectionMode = selectionMode,
                                selectionState = selectionState,
                                onSelectionStateChange = { selectionState = it },
                                viewModel = viewModel,
                                haptics = haptics,
                                capturePosition = ::captureCurrentListPosition,
                                onOpenMemo = onOpenMemo,
                                onMoreActions = { moreActionsTarget = it },
                            )
                        }

                        if (pagingItems.loadState.append is LoadState.Loading) {
                            item(key = "appendLoading", span = StaggeredGridItemSpan.FullLine) {
                                HomeListAppendLoadingItem()
                            }
                        }
                    }
                }
            } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .testTag("home_memo_list")
                        .semantics {
                            isTraversalGroup = true
                            traversalIndex = 0f
                        },
                state = listState,
                // 底部追加 FAB 净空，避免最后一项被悬浮按钮遮挡
                contentPadding = PaddingValues(
                    start = horizontalPad,
                    end = horizontalPad,
                    top = verticalPad,
                    bottom = verticalPad + fabBottomClearance,
                ),
                verticalArrangement = Arrangement.spacedBy(itemGap),
            ) {
                if (isRefreshing && !hasItems) {
                    item(key = "loading") {
                        HomeListLoadingItem()
                    }
                } else if (refreshError != null && !hasItems) {
                    item(key = "loadError") {
                        HomeListErrorItem(
                            message = refreshError.message ?: "未知错误",
                            onRetry = { pagingItems.retry() },
                            onSync = viewModel::requestSync,
                        )
                    }
                } else if (isEmpty) {
                    item(key = "empty") {
                        HomeListEmptyItem(isFiltering = uiState.isFiltering)
                    }
                } else {
                    // 将 pagingItems 按日历日期分组，插入 DateHeader
                    val groupedItems = buildGroupedItems(pagingItems)

                    items(
                        count = groupedItems.size,
                        key = { index -> groupedItems[index].stableKey() },
                        contentType = { index -> groupedItems[index].contentType() },
                    ) { index ->
                        GroupedItemContent(
                            item = groupedItems[index],
                            uiState = uiState,
                            mode = mode,
                            enableRichPreview = enableRichPreview,
                            selectionMode = selectionMode,
                            selectionState = selectionState,
                            onSelectionStateChange = { selectionState = it },
                            viewModel = viewModel,
                            haptics = haptics,
                            capturePosition = ::captureCurrentListPosition,
                            onOpenMemo = onOpenMemo,
                            onMoreActions = { moreActionsTarget = it },
                        )
                    }

                    if (pagingItems.loadState.append is LoadState.Loading) {
                        item(key = "appendLoading") {
                            HomeListAppendLoadingItem()
                        }
                    }
                }
            }
            }

            // Paging 在数据库变更时可能触发 refresh；不要用“全屏加载”把列表刷白，避免肉眼可见的闪烁。
            if (isRefreshing && hasItems) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                )
            }
        }
    }
    }
}

/** 列表项稳定 key：与单列/双列容器无关，保证切换形态时 item 状态可复用。 */
private fun GroupedListItem.stableKey(): Any =
    when (this) {
        is GroupedListItem.DateHeader -> itemKey
        is GroupedListItem.MemoEntry -> memo.uuid
        is GroupedListItem.LoadingEntry -> itemKey
    }

/** 列表项 contentType：用于 Compose 复用池分桶。 */
private fun GroupedListItem.contentType(): String =
    when (this) {
        is GroupedListItem.DateHeader -> "date_header"
        is GroupedListItem.MemoEntry -> "memo"
        is GroupedListItem.LoadingEntry -> "loading"
    }

/** 首屏加载占位（整行居中）。 */
@Composable
private fun HomeListLoadingItem() {
    InkLoading()
}

/** 首屏加载失败（错误原语 + 原「同步」动作）。 */
@Composable
private fun HomeListErrorItem(
    message: String,
    onRetry: () -> Unit,
    onSync: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        InkError(message = "加载失败：$message", onRetry = onRetry)
        Spacer(modifier = Modifier.height(InkSpacing.StateGapS))
        TextButton(onClick = onSync) { Text("同步") }
    }
}

/** 空态文案（整行居中）。 */
@Composable
private fun HomeListEmptyItem(isFiltering: Boolean) {
    InkEmpty(
        message = if (isFiltering) "没有匹配的记录" else "还没有任何记录，点右下角“记”开始吧。",
    )
}

/** 分页追加加载占位（整行居中）。 */
@Composable
private fun HomeListAppendLoadingItem() {
    InkLoading()
}

/** 单个分组项渲染：DateHeader / 加载占位 / memo 卡片。单列与双列容器共用。 */
@Composable
private fun GroupedItemContent(
    item: GroupedListItem,
    uiState: HomeUiState,
    mode: HomeScreenMode,
    enableRichPreview: Boolean,
    selectionMode: Boolean,
    selectionState: HomeSelectionState,
    onSelectionStateChange: (HomeSelectionState) -> Unit,
    viewModel: HomeViewModel,
    haptics: OneMemosHaptics,
    capturePosition: () -> Unit,
    onOpenMemo: (String) -> Unit,
    onMoreActions: (Memo) -> Unit,
) {
    when (item) {
        is GroupedListItem.DateHeader -> {
            DateHeader(dateKey = item.dateKey, epochMillis = item.epochMillis)
        }
        is GroupedListItem.LoadingEntry -> {
            MemoItemLoadingPlaceholder()
        }
        is GroupedListItem.MemoEntry -> {
            val memo = item.memo
            val renderRichPreview = enableRichPreview || viewModel.isRichPreviewSticky(memo.uuid)
            LaunchedEffect(memo.uuid, enableRichPreview) {
                if (enableRichPreview) {
                    viewModel.markRichPreviewSticky(memo.uuid)
                }
            }
            val swipeGesturesEnabled =
                SwipeActionPolicy.gesturesEnabled(
                    swipeEnabled = uiState.swipeEnabled,
                    selectionMode = selectionMode,
                    mode = mode,
                )
            SwipeableMemoItem(
                enabled = swipeGesturesEnabled,
                rightAction = uiState.swipeRightAction,
                leftAction = uiState.swipeLeftAction,
                haptics = haptics,
                onSwipeAction = { action -> viewModel.performSwipeAction(memo, action) },
            ) {
            // 仅 ACTIVE 已有随笔参与 shared bounds；Archived/新建不配对。
            val sharedModifier =
                if (mode == HomeScreenMode.ACTIVE) {
                    Modifier.memoSharedBounds(memo.uuid)
                } else {
                    Modifier
                }
            MemoItem(
                memo = memo,
                serverBase = uiState.serverBase,
                devKeywordsRaw = uiState.devAutoTagLineKeywords,
                showAutoTagLineInHome = uiState.devShowAutoTagLineInHome,
                enableRichPreview = renderRichPreview,
                selectionMode = selectionMode,
                selected = selectionState.selectedIds.contains(memo.uuid),
                onOpenMemo = {
                    if (selectionMode) {
                        haptics.tick()
                        onSelectionStateChange(selectionState.toggle(memo.uuid))
                    } else {
                        capturePosition()
                        onOpenMemo(memo.uuid)
                    }
                },
                onLongShare = {
                    if (selectionMode) {
                        haptics.tick()
                        onSelectionStateChange(selectionState.toggle(memo.uuid))
                    } else {
                        haptics.tick()
                        onSelectionStateChange(selectionState.enter(memo.uuid))
                    }
                },
                onToggleTag = { tag -> if (!selectionMode) viewModel.toggleTag(tag) },
                onMoreActions =
                    if (selectionMode) {
                        null
                    } else {
                        {
                            haptics.tick()
                            onMoreActions(memo)
                        }
                    },
                modifier = sharedModifier,
            )
            }
        }
    }
}

/**
 * M2.5 滑动手势容器：右滑/左滑触发设置页自选的动作（动作池见 ADR 0011）。
 * - enabled=false（总开关关闭/多选中/已归档页）时直接渲染内容，回退纯长按。
 * - confirmValueChange 恒返回 false：卡片始终回弹；归档项由数据库变更驱动分页移除，
 *   避免“动作失败但卡片已消失”的假删除。
 */
@Composable
private fun SwipeableMemoItem(
    enabled: Boolean,
    rightAction: SwipeAction,
    leftAction: SwipeAction,
    haptics: OneMemosHaptics,
    onSwipeAction: (SwipeAction) -> Unit,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }

    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        haptics.confirm()
                        onSwipeAction(rightAction)
                    }
                    SwipeToDismissBoxValue.EndToStart -> {
                        haptics.confirm()
                        onSwipeAction(leftAction)
                    }
                    SwipeToDismissBoxValue.Settled -> Unit
                }
                false
            },
            positionalThreshold = { distance -> distance * SwipeActionPolicy.THRESHOLD_FRACTION },
        )

    // 过阈值触感：拖动中 targetValue 越过位置阈值变为非 Settled 时 tick 一次。
    LaunchedEffect(dismissState) {
        snapshotFlow { dismissState.targetValue }
            .distinctUntilChanged()
            .collectLatest { value ->
                if (value != SwipeToDismissBoxValue.Settled) haptics.tick()
            }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            SwipeActionBackground(
                dismissState = dismissState,
                rightAction = rightAction,
                leftAction = leftAction,
            )
        },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
    ) {
        content()
    }
}

/** 滑动时卡片背后露出的动作区：令牌色底 + 动作图标与中文标签，对齐滑动起侧。 */
@Composable
private fun SwipeActionBackground(
    dismissState: SwipeToDismissBoxState,
    rightAction: SwipeAction,
    leftAction: SwipeAction,
) {
    val direction = dismissState.dismissDirection
    val action =
        when (direction) {
            SwipeToDismissBoxValue.StartToEnd -> rightAction
            SwipeToDismissBoxValue.EndToStart -> leftAction
            SwipeToDismissBoxValue.Settled -> null
        }
    val (containerColor, contentColor) = swipeActionColors(action)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(InkShape.Card)
                .background(containerColor)
                .padding(horizontal = InkSpacing.X16),
        contentAlignment =
            if (direction == SwipeToDismissBoxValue.EndToStart) {
                Alignment.CenterEnd
            } else {
                Alignment.CenterStart
            },
    ) {
        if (action != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.X8),
            ) {
                Icon(
                    imageVector = swipeActionIcon(action),
                    contentDescription = null,
                    tint = contentColor,
                )
                Text(
                    text = SwipeActionPolicy.label(action),
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 动作 → 令牌底色/前景色（归档偏红、待办偏蓝、收藏偏黄、置顶偏次色，全部走 colorScheme）。 */
@Composable
private fun swipeActionColors(action: SwipeAction?): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (action) {
        SwipeAction.ADD_TO_TODO -> scheme.primaryContainer to scheme.onPrimaryContainer
        SwipeAction.FAVORITE -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        SwipeAction.ARCHIVE -> scheme.errorContainer to scheme.onErrorContainer
        SwipeAction.PIN -> scheme.secondaryContainer to scheme.onSecondaryContainer
        null -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
}

private fun swipeActionIcon(action: SwipeAction): ImageVector =
    when (action) {
        SwipeAction.ADD_TO_TODO -> Icons.Filled.AddTask
        SwipeAction.FAVORITE -> Icons.Filled.Star
        SwipeAction.ARCHIVE -> Icons.Filled.Archive
        SwipeAction.PIN -> Icons.Filled.PushPin
    }

@Composable
private fun FilterStatusBanner(
    visible: Boolean,
    query: String,
    tags: List<String>,
    regexEnabled: Boolean,
    onClearAll: () -> Unit,
    onClearQuery: () -> Unit,
    onEditQuery: () -> Unit,
    onToggleTag: (String) -> Unit,
) {
    AnimatedVisibility(visible = visible) {
        Surface(
            modifier = Modifier.padding(horizontal = InkSpacing.X16, vertical = InkSpacing.X8),
            shape = InkShape.SkeletonCard,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = InkSpacing.X2,
            shadowElevation = InkSpacing.X10,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = InkSpacing.X12, vertical = InkSpacing.X10),
                verticalArrangement = Arrangement.spacedBy(InkSpacing.X8),
            ) {
                val q = query.trim()
                val hasQuery = q.isNotBlank()
                val hasTags = tags.isNotEmpty()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val count = (if (hasQuery) 1 else 0) + tags.size
                    val titleText =
                        when {
                            hasQuery && !hasTags -> "搜索中"
                            !hasQuery && hasTags -> "筛选中"
                            else -> "筛选+搜索中"
                        }
                    Text(
                        text = if (count > 0) "$titleText（$count）" else titleText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    TextButton(onClick = onClearAll) {
                        Text(text = "清除")
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(InkSpacing.X8),
                    verticalArrangement = Arrangement.spacedBy(InkSpacing.X8),
                ) {
                    if (q.isNotBlank()) {
                        Surface(
                            shape = InkShape.Tag,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier =
                                Modifier.clickable(
                                    indication = null,
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                ) {
                                    onEditQuery()
                                },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = InkSpacing.X10, vertical = InkSpacing.X6),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(InkSpacing.X8),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(InkSpacing.X16),
                                )
                                Text(
                                    text = q.take(24),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (regexEnabled) {
                                    Text(
                                        text = "正则",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                IconButton(
                                    onClick = onClearQuery,
                                    // 结构常量：清除按钮尺寸，组件特有约束，非间距尺度
                                    modifier = Modifier.size(InkSpacing.OverlayThumbBadgeSize),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "清除关键词",
                                        // 结构常量：清除图标尺寸，组件特有约束，非间距尺度
                                        modifier = Modifier.size(InkSpacing.X18),
                            )
                            }
                        }
                    }
                        }
                    }

                    tags.forEach { tag ->
                        TagChip(
                            tag = tag,
                            selected = true,
                            onClick = { onToggleTag(tag) },
                        )
                    }
                }
            }
        }
    }

@Composable
private fun SyncStatusBanner(
    visible: Boolean,
    state: GlobalSyncState,
    onRetrySync: () -> Unit,
    onOpenAuth: () -> Unit,
) {
    AnimatedVisibility(visible = visible) {
        Column {
            if (state.isSyncing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            val pendingText =
                if (state.pendingCount > 0) {
                    "待同步 ${state.pendingCount} 条"
                } else {
                    "无待同步"
                }

            val message =
                when {
                    state.authInvalid -> "鉴权失败，请重新登录。"
                    !state.networkOnline -> "当前离线，联网后会自动同步。"
                    state.isSyncing -> "同步中…"
                    state.hasError -> "同步失败：${state.lastError.ifBlank { "未知错误" }}"
                    state.pendingCount > 0 -> "有离线记录待同步。"
                    state.isEnqueued -> "已排队，等待同步执行。"
                    else -> "同步状态"
                }

            val action = SyncBannerPolicy.actionFor(state)
            val onAction =
                when (action) {
                    SyncBannerAction.OPEN_AUTH -> onOpenAuth
                    SyncBannerAction.RETRY_SYNC -> onRetrySync
                    SyncBannerAction.SYNC_PENDING -> onRetrySync
                    SyncBannerAction.NONE -> null
                }

            InkRetryBanner(
                message = "$message\n$pendingText",
                onRetry = onAction,
                retryLabel = SyncBannerPolicy.label(action),
            )
        }
    }
}

@Composable
private fun SearchPopup(
    query: String,
    regexEnabled: Boolean,
    errorText: String?,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var queryValue by remember { mutableStateOf(initialSearchFieldValue(query)) }

    LaunchedEffect(query) {
        queryValue = syncSearchFieldValueWithExternalQuery(current = queryValue, query = query)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 背景点击关闭
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    // 结构常量：56dp 为顶栏占位高度，组件特有约束，非间距尺度
                    .padding(top = topInset + InkSpacing.X56 + InkSpacing.X10)
                    .padding(horizontal = InkSpacing.X16)
                    .fillMaxWidth(),
                shape = InkShape.SkeletonCard,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = InkSpacing.X2,
                shadowElevation = InkSpacing.X8,
            ) {
                Column(modifier = Modifier.padding(InkSpacing.X12), verticalArrangement = Arrangement.spacedBy(InkSpacing.X8)) {
                    TextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            // 避免大字号/无障碍字体下文字被裁切：不要固定 44dp，高度至少按 Material 默认高度走。
                            .heightIn(min = InkSpacing.X56)
                            .focusRequester(focusRequester),
                        value = queryValue,
                        onValueChange = { value: TextFieldValue ->
                            queryValue = value
                            if (value.text != query) {
                                onQueryChange(value.text)
                            }
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (regexEnabled) {
                                    Text(
                                        text = "正则",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.padding(end = InkSpacing.X8),
                                    )
                                }
                                if (queryValue.text.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            queryValue = initialSearchFieldValue("")
                                            onClear()
                                        },
                                    ) {
                                        Icon(imageVector = Icons.Filled.Close, contentDescription = "清空")
                                    }
                                } else {
                                    IconButton(onClick = onDismiss) {
                                        Icon(imageVector = Icons.Filled.Close, contentDescription = "关闭")
                                    }
                                }
                            }
                        },
                        keyboardOptions =
                            KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrect = false,
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Search,
                            ),
                        keyboardActions =
                            KeyboardActions(
                                onSearch = {
                                    keyboard?.hide()
                                    onDismiss()
                                },
                            ),
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                        shape = RoundedCornerShape(InkShape.RadiusL),
                    )

                    if (!errorText.isNullOrBlank()) {
                        Text(
                            text = errorText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Text(
                        text = "支持：#标签；多词空格分隔。${if (regexEnabled) "已开启正则模式（设置里可关闭）" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
}

@Composable
private fun MemoItemLoadingPlaceholder() {
    InkCard {
        val blockColor = MaterialTheme.colorScheme.surfaceVariant

        Row(horizontalArrangement = Arrangement.spacedBy(InkSpacing.X10)) {
            repeat(3) {
                Surface(
                    // 结构常量：骨架屏标签块尺寸（28×64），一次性占位几何，非间距尺度
                    modifier = Modifier
                        .height(InkSpacing.X28)
                        .width(InkSpacing.X64),
                    shape = RoundedCornerShape(InkShape.RadiusL),
                    color = blockColor,
                ) {}
            }
        }

        Spacer(modifier = Modifier.height(InkSpacing.X10))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(InkSpacing.SkeletonTextLineHeight),
            shape = InkShape.Skeleton,
            color = blockColor,
        ) {}
        Spacer(modifier = Modifier.height(InkSpacing.X8))
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .height(InkSpacing.SkeletonTextLineHeight),
            shape = InkShape.Skeleton,
            color = blockColor,
        ) {}

        Spacer(modifier = Modifier.height(InkSpacing.X10))

        Surface(
            modifier = Modifier
                .fillMaxWidth(0.40f)
                .height(InkSpacing.X14),
            shape = InkShape.Skeleton,
            color = blockColor,
        ) {}
    }
}

/**
 * 列表项类型：日期头部 / memo 条目 / 加载占位。
 * 用于 LazyColumn 的预分组渲染。
 */
internal sealed class GroupedListItem {
    /** 日期分组头部。 */
    data class DateHeader(
        val dateKey: String, // yyyy-MM-dd
        val epochMillis: Long,
    ) : GroupedListItem() {
        val itemKey: String get() = "date_header_$dateKey"
    }

    /** 单条 memo 条目。 */
    data class MemoEntry(
        val index: Int,
        val memo: Memo,
    ) : GroupedListItem()

    /** 未加载的占位项。 */
    data class LoadingEntry(
        val index: Int,
    ) : GroupedListItem() {
        val itemKey: String get() = "loading_$index"
    }
}

/**
 * 将分页列表按日历日期（yyyy-MM-dd）分组，插入 [GroupedListItem.DateHeader]。
 * 未加载的占位项不参与日期分组（直接以 [GroupedListItem.LoadingEntry] 穿插）。
 */
private fun buildGroupedItems(
    pagingItems: androidx.paging.compose.LazyPagingItems<Memo>,
): List<GroupedListItem> {
    val memos = List(pagingItems.itemCount) { pagingItems.peek(it) }
    return buildGroupedItemsFromList(memos)
}

/**
 * 纯数据分组逻辑（可从 JVM 单元测试调用）。
 * 输入可为 null 的列表，按日期分组输出。
 */
internal fun buildGroupedItemsFromList(
    memos: List<Memo?>,
): List<GroupedListItem> {
    val items = mutableListOf<GroupedListItem>()
    var currentDate = ""
    for (i in memos.indices) {
        val memo = memos[i]
        if (memo != null) {
            val dateKey = DateTimeFormatter.formatYmd(memo.createdAt)
            if (dateKey != currentDate) {
                currentDate = dateKey
                items.add(GroupedListItem.DateHeader(dateKey, memo.createdAt))
            }
            items.add(GroupedListItem.MemoEntry(i, memo))
        } else {
            items.add(GroupedListItem.LoadingEntry(i))
        }
    }
    return items
}
