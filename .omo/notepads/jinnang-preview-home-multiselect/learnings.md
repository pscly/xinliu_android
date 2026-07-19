# learnings.md

（累计记录：实现过程中发现的规律/坑点/可复用代码位置。只追加，不覆盖。）

## 2026-02-21 - Home 多选选择状态 reducer
- 纯逻辑 reducer：`feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeSelectionState.kt`
- 典型用法：`state = state.enter(uuid)` 进入多选；多选切换用 `state.toggle(uuid)`；退出用 `state.clear()`/`state.exit()`
- UI 判断多选模式：`state.selectionMode`；当前选中集合：`state.selectedIds`

## 2026-02-21 - Home 多选“合并文本分享”拼接器
- 纯逻辑拼接器：`feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeShareTextBuilder.kt`
- 调用方式：`HomeShareTextBuilder.build(memos: List<Memo>): String`
- 输出格式：每条为 `DateTimeFormatter.formatYmdHm(createdAt) + "\n" + memo.content`，多条用 `\n\n---\n\n` 分隔

## 2026-02-21 - Home 放入锦囊 displayName 与批量 API
- displayName：`MarkdownDeriver.plainPreviewSkippingLinesEndingWithKeywords(markdown = memo.content, keywords = AutoTagLineHider.parseKeywords(null), maxChars = 80).trim()`；空白兜底为 `"随笔"`
- 批量 API：`suspend fun addMemoRefs(parentId: String?, memos: List<Memo>): Int`（空列表返回 0；repo 返回空串不计入成功；返回成功数量）

## 2026-02-21 - Home 多选批量“放入锦囊”对话框落点
- 新增批量 BottomSheet：`feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/AddToCollectionsBatchDialog.kt`
- Home 多选“操作区”入口：`feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeScreen.kt` 的 `Scaffold(bottomBar = { ... })`
- 选中条目来源：使用 `pagingItems.itemSnapshotList.items` 过滤 `selectionState.selectedIds` 得到当前可用的 `Memo` 列表（Paging 未加载到的条目会计入失败数）

## 2026-02-21 - Home 多选选择态 UI 接入点
- 状态上提：`feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeScreen.kt` 内 `var selectionState by remember { mutableStateOf(HomeSelectionState()) }`，模式判断用 `selectionState.selectionMode`，选中集合用 `selectionState.selectedIds`
- 进入多选：长按仍先打开 `ModalBottomSheet`（更多操作），BottomSheet 新增 `InkCard("多选")`，点击后 `selectionState = selectionState.enter(target.uuid)` 且关闭弹层
- 退出多选：TopAppBar 左侧 `X` + `BackHandler(enabled = selectionMode)`，统一走 `selectionState.exit()`（清空 selectedIds）
- 交互切换：列表 item 的 `onOpenMemo/onLongShare` 在 `selectionMode=true` 时改为 `selectionState.toggle(memo.uuid)`，否则维持原先的打开详情/打开 BottomSheet 行为
- 防误操作：监听筛选 key（`mode/query/selectedTags`）变化时自动 `exit()`，避免保留不可见条目的选中态

## 2026-02-21 - Home 多选批量归档/恢复（带确认弹窗 + 结果 toast）
- ViewModel 承载批量逻辑：`feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeViewModel.kt` 的 `requestBatchArchiveOrRestore(mode, selectedIds)`
- 统计规则：逐个 `memoRepository.getMemo(uuid)` 判空（不存在计 failed），存在才调用 `archiveMemo/unarchiveMemo`（异常计 failed，不中断）
- 一次性事件：`HomeEvent.BatchActionFinished(summary)`（`feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeBatchActionModels.kt`）用于 UI toast + 结束后清空 selection
- UI 入口：`feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeScreen.kt` 的 selectionMode bottomBar“操作区”新增按钮（ACTIVE=归档，ARCHIVED=恢复），点击弹 `AlertDialog`

## 2026-02-21 - Home 多选“分享合并文本”（ACTION_SEND, EXTRA_TEXT）
- UI 入口：`feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeScreen.kt` 的 `Scaffold(bottomBar = { ... })`，selectionMode 操作区新增 `TextButton("分享")`
- ViewModel 批量拉取并复用 builder：`feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeViewModel.kt` 的 `requestBatchShareMergedText(selectedIds)` -> `HomeShareTextBuilder.build(memos)`
- 一次性事件：`HomeEvent.ShareTextReady(text)`（`feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeBatchActionModels.kt`），由 UI 层 `Intent(Intent.ACTION_SEND)` + `putExtra(Intent.EXTRA_TEXT, text)` 触发系统分享，并在结束后 `selectionState.exit()`

## 2026-02-21 - Collections NOTE_REF 批量加载/缓存被引用 Memo（避免 N+1 Flow）
- 统一落点：`feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsViewModel.kt`
- UIState 扩展：`CollectionsUiState.memoByRefTargetId: Map<String, Memo>`；key 规则复用 UI：`refId ?: refLocalUuid`
- 避免 N+1 Flow 的做法：在 ViewModel 内对 `children` 做一次收敛 collector，批量计算需要的 targetId，再对缺失项逐个 `memoRepository.getMemo(targetId)`（suspend）补齐；UI 不做 per-item collect
- 简单缓存裁剪策略：每次 `children` 变化先把缓存裁剪到“当前 children 仍需要的 key”，再加载缺失项，防止 map 随历史浏览无限增长

## 2026-02-21 - Collections NOTE_REF 卡片渲染对齐 Home 预览（无标签 chips）
- 渲染落点：`feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsScreen.kt` 的 `CollectionItemCard()` 分支
- targetId 规则：`val target = item.refId ?: item.refLocalUuid`；通过 `uiState.memoByRefTargetId[target]` 查找（仅 Map 访问，不引入 per-item Flow collect）
- 预览与时间：使用 `MarkdownPreview(markdown = memo.content, maxBlocks = 3, maxLines = 4)`；时间用 `DateTimeFormatter.formatYmdHm(memo.createdAt)`
- 兜底：memo 缺失/待同步 -> `item.name` + “引用内容不可用/待同步”；`refType == FLOW_NOTE` 维持占位行为
