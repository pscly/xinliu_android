@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package cc.pscly.onememos.ui.feature.home

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.Color
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
import cc.pscly.onememos.ui.component.MarkdownPreview
import cc.pscly.onememos.ui.component.SealButton
import cc.pscly.onememos.ui.component.SealIconButton
import cc.pscly.onememos.ui.component.TagChip
import cc.pscly.onememos.ui.component.TagFilterBottomSheet
import cc.pscly.onememos.domain.tag.TagExtractor
import cc.pscly.onememos.domain.model.SyncStatus
import cc.pscly.onememos.domain.model.GlobalSyncState
import coil.compose.AsyncImage
import coil.request.ImageRequest
import cc.pscly.onememos.ui.util.DateTimeFormatter
import cc.pscly.onememos.ui.util.AutoTagLineHider
import cc.pscly.onememos.ui.util.rememberOneMemosHaptics
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

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
    val (initialIndex, initialOffset) = viewModel.peekListPosition()
    val listState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = initialIndex,
            initialFirstVisibleItemScrollOffset = initialOffset,
        )
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
            }
        }
    }

    BackHandler(enabled = selectionMode) {
        selectionState = selectionState.exit()
    }

    // 1B：滚动中仅渲染纯文本预览，停稳 ~200ms 后再切回 Markdown 样式预览。
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
    val showScrollToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 200
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

    // 从详情页返回时：恢复到进入前的列表位置（只在需要时触发一次）。
    LaunchedEffect(pagingItems.itemCount) {
        val pos = viewModel.pendingRestoreListPosition() ?: return@LaunchedEffect
        val (idx, off) = pos
        // Paging 可能先加载少量 item；只有当目标 index 已可用时才真正恢复，并清除 pending。
        if (idx >= 0 && pagingItems.itemCount > idx) {
            listState.scrollToItem(idx, off)
            viewModel.markListPositionRestored()
        }
    }

    val refreshState = pagingItems.loadState.refresh
    val isRefreshing = refreshState is LoadState.Loading
    val refreshError = (refreshState as? LoadState.Error)?.error
    val hasItems = pagingItems.itemCount > 0
    val isSyncing = globalSyncState.isSyncing

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
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
                        if (selectionMode) return@TopAppBar
                        IconButton(onClick = viewModel::requestSync, enabled = !isSyncing) {
                            if (isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
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
                tonalElevation = 2.dp,
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
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
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AnimatedVisibility(visible = showScrollToTop) {
                    SealIconButton(
                        icon = Icons.Filled.KeyboardArrowUp,
                        contentDescription = "回到顶部",
                        onClick = {
                            scope.launch {
                                listState.animateScrollToItem(0)
                            }
                        },
                    )
                }
                SealButton(
                    text = "记",
                    onClick = {
                        viewModel.captureListPosition(
                            index = listState.firstVisibleItemIndex,
                            offset = listState.firstVisibleItemScrollOffset,
                        )
                        onCreateMemo()
                    },
                )
            }
        },
    ) { padding ->
        if (moreActionsTarget != null) {
            val target = moreActionsTarget!!
            ModalBottomSheet(
                onDismissRequest = { moreActionsTarget = null },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
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

                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }

        if (addToCollectionsTarget != null) {
            AddToCollectionsDialog(
                memo = addToCollectionsTarget!!,
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
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val isEmpty = !isRefreshing && refreshError == null && !hasItems

                if (isRefreshing && !hasItems) {
                    item(key = "loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (refreshError != null && !hasItems) {
                    item(key = "loadError") {
                        InkCard {
                            Text(
                                text = "加载失败：${refreshError.message ?: "未知错误"}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TextButton(onClick = { pagingItems.retry() }) { Text("重试") }
                                TextButton(onClick = viewModel::requestSync) { Text("同步") }
                            }
                        }
                    }
                } else if (isEmpty) {
                    item(key = "empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (uiState.isFiltering) "没有匹配的记录" else "还没有任何记录，点右下角“记”开始吧。",
                                color = MaterialTheme.colorScheme.outline,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                } else {
                    items(
                        count = pagingItems.itemCount,
                        // pagingItems[index] 可能短暂为 null（加载中/刷新中）；避免直接 return 导致列表出现“空白卡顿”。
                        key = { index -> pagingItems.peek(index)?.uuid ?: "loading_$index" },
                        contentType = { index -> if (pagingItems.peek(index) == null) "loading" else "memo" },
                    ) { index ->
                        val memo = pagingItems[index]
                        if (memo == null) {
                            MemoItemLoadingPlaceholder()
                        } else {
                            val renderRichPreview = enableRichPreview || viewModel.isRichPreviewSticky(memo.uuid)
                            // 避免在滚动/重组热路径里每次都写入状态：仅在“停稳后”切回富预览时标记一次。
                            LaunchedEffect(memo.uuid, enableRichPreview) {
                                if (enableRichPreview) {
                                    viewModel.markRichPreviewSticky(memo.uuid)
                                }
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
                                        selectionState = selectionState.toggle(memo.uuid)
                                    } else {
                                        viewModel.captureListPosition(
                                            index = listState.firstVisibleItemIndex,
                                            offset = listState.firstVisibleItemScrollOffset,
                                        )
                                        onOpenMemo(memo.uuid)
                                    }
                                },
                                onLongShare = {
                                    if (selectionMode) {
                                        haptics.tick()
                                        selectionState = selectionState.toggle(memo.uuid)
                                    } else {
                                        haptics.tick()
                                        selectionState = selectionState.enter(memo.uuid)
                                    }
                                },
                                onToggleTag = { tag -> if (!selectionMode) viewModel.toggleTag(tag) },
                                onMoreActions =
                                    if (selectionMode) {
                                        null
                                    } else {
                                        {
                                            haptics.tick()
                                            moreActionsTarget = memo
                                        }
                                    },
                            )
                        }
                    }

                    if (pagingItems.loadState.append is LoadState.Loading) {
                        item(key = "appendLoading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(strokeWidth = 2.dp)
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 10.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (q.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
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
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
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
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "清除关键词",
                                        modifier = Modifier.size(18.dp),
                                    )
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
}

@Composable
private fun SyncStatusBanner(
    visible: Boolean,
    state: GlobalSyncState,
    onRetrySync: () -> Unit,
    onOpenAuth: () -> Unit,
) {
    AnimatedVisibility(visible = visible) {
        InkCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.authInvalid || state.hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = pendingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when {
                        state.authInvalid -> {
                            TextButton(onClick = onOpenAuth) { Text("去登录") }
                        }

                        state.hasError -> {
                            TextButton(onClick = onRetrySync) { Text("重试") }
                        }

                        state.pendingCount > 0 && state.networkOnline && !state.isSyncing -> {
                            TextButton(onClick = onRetrySync) { Text("同步") }
                        }
                    }
                }
            }
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
                    .padding(top = topInset + 56.dp + 10.dp)
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 8.dp,
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            // 避免大字号/无障碍字体下文字被裁切：不要固定 44dp，高度至少按 Material 默认高度走。
                            .heightIn(min = 56.dp)
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
                                        modifier = Modifier.padding(end = 8.dp),
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
                        shape = RoundedCornerShape(14.dp),
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

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) {
                Surface(
                    modifier = Modifier
                        .height(28.dp)
                        .width(64.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = blockColor,
                ) {}
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp),
            shape = RoundedCornerShape(8.dp),
            color = blockColor,
        ) {}
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .height(18.dp),
            shape = RoundedCornerShape(8.dp),
            color = blockColor,
        ) {}

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth(0.40f)
                .height(14.dp),
            shape = RoundedCornerShape(8.dp),
            color = blockColor,
        ) {}
    }
}
