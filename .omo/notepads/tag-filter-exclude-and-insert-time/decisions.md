# decisions.md

（累计记录：关键决策与理由。只追加，不覆盖。）

## [2026-02-26] - MemoFilter 排除标签开关命名

- 字段命名采用 `MemoFilter.excludeTags: Boolean`（默认 `false`），语义为“标签筛选是否取反/排除”。
- Store API 采用 `MemoFilterStore.setExcludeTags(enabled: Boolean)`，保持与 `setQuery`/`setTagMatchMode` 命名风格一致。
- `TagMatchMode` 保持仅 `OR/AND`，不引入 `NOT`，避免扩展枚举导致更大范围改动。

## [2026-02-27] - 忽略 Eclipse/Buildship 工程文件与本计划产物

- Eclipse/Buildship 会在多个模块目录生成 `.project`/`.classpath`/`.settings/`，这些属于本地 IDE 工程元数据，不具备跨环境可复现价值，且容易被误提交。
- 本计划的本地执行产物（`.sisyphus/notepads/tag-filter-exclude-and-insert-time/` 与 `.sisyphus/plans/tag-filter-exclude-and-insert-time.md`）仅用于 AI 协作记录，不应进入版本库；因此采用精确路径忽略，避免误伤其它 `.sisyphus` 内容（例如已跟踪的 `.sisyphus/boulder.json`）。
