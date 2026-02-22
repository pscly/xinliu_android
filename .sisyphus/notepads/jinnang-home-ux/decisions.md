# decisions.md

（累计记录：关键决策与理由。只追加，不覆盖。）

## [2026-02-22] - 长按改为多选入口，更多操作改为“...”按钮
- 交互决策：长按直接 `selectionState.enter(memo.uuid)` 进入多选并默认选中该条，减少“长按 -> BottomSheet -> 多选”的二次操作。
- 入口迁移：保留原 BottomSheet 的 `moreActionsTarget` 机制，但触发方式从“长按卡片”迁移到“卡片内的 ... 按钮”，确保“墨迹卡片/放入锦囊”等单条能力不丢失。
- 避免混淆：多选模式下隐藏“...”按钮，避免在批量语境下弹出单条 BottomSheet。
- 去重：既然长按已承担多选入口，BottomSheet 中移除“多选”这一项，避免重复入口。

## [2026-02-22] - Collections：标签筛选（多标签 OR）与筛选条位置
- 规则：多标签筛选使用 OR（命中任意一个即可）；仅对 NOTE_REF 生效；FOLDER 始终可见，避免“导航入口被筛掉”。
- 状态生命周期：`uiState.currentParentId` 变化（进入/返回文件夹）时自动清空 `selectedTags`，避免跨目录残留筛选导致误判“空”。
- UI：筛选条固定放在面包屑下方（列表上方），只在 `selectedTags` 非空时渲染；多选/排序模式下禁用筛选条交互，减少模式冲突。

## [2026-02-22] - Collections：NOTE_REF 预览遵循 Home 的“滚动降级”策略
- 性能策略：列表滚动中强制只渲染纯文本预览；停稳约 200ms 后再切回 `MarkdownPreview`，避免 Compose/Markdown 复杂布局在滚动路径卡顿。
- 交互策略：在 `selectionMode` 或 `reorderMode` 下，条目内部交互（TagChip 过滤）一律禁用，并且富预览不启用，确保“卡片点击=选择/排序”的意图不被内部点击目标打断。
- 标题规则：NOTE_REF 的标题行只展示用户手动命名（`item.name.trim()` 非空）的内容，避免空标题时出现“（无标题）”占位干扰预览。
