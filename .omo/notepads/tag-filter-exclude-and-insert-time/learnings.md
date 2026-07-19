# Learnings

## 2026-02-27
- 交付收尾阶段仅提交白名单文件：先 `git diff --stat` 做 sanity check，再用 `git add -- <paths...>` 精确加到暂存区，避免误带入 `.sisyphus/evidence/` 等证据产物。

## 2026-02-27 - 标签筛选数据流映射（excludeTags + 强制 OR）
- 过滤模型与全局状态：`core/designsystem/src/main/java/cc/pscly/onememos/ui/filter/MemoFilter.kt` + `core/designsystem/src/main/java/cc/pscly/onememos/ui/filter/MemoFilterStore.kt`（StateFlow 持有 query/selectedTags/tagMatchMode/excludeTags）。
- Home 数据流：`HomeScreen` 搜索框/筛选面板 -> `HomeViewModel` 写入 `MemoFilterStore`；`HomeViewModel.activePaging/archivedPaging` combine(filterStore.state) 后在 `applyFilterToPaging` 内做 PagingData.filter。
- queryTags 来源：`applyFilterToPaging` 内 `queryTags = TagExtractor.extractAll(filter.query)`，并与 `selectedTags` 合并为 `effectiveTags`；正文搜索用 `TagExtractor.stripTagTokens(filter.query)` 去掉 #tag token。
- OR/AND/排除判定：include 模式按 `TagMatchMode.OR(any)/AND(all)`；exclude 模式为 `effectiveTags.none { it in memoTags }`（排除任意标签，强制 OR）。
- memoTags 提取 fallback：过滤与统计均优先用 `memo.tags`，为空再 `TagExtractor.extractAll(memo.content)`（见 `HomeViewModel.applyFilterToPaging`、`TagStats.build`、`CollectionsScreen.itemsToRender`）。
- 强制 OR 的 UI 约束：`TagFilterBottomSheet` 在 `excludeTags && tagMatchMode==AND` 时自动回调切回 OR，并禁用“与”按钮；`EditorScreen`/`CollectionsScreen` 的 onExcludeTagsChange 也做了本地兜底把 mode 设回 OR。
- 共享过滤 store 仅 Home/Editor 使用：仓库内只有 `HomeViewModel` 与 `EditorViewModel` 注入 `MemoFilterStore`。
