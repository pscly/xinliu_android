# Decisions

## 2026-02-13
- Active plan: quick-capture-draft-autosave

- 草稿 JSON 原子写方案采用 `draft.json.tmp` + 同目录 rename 覆盖，避免依赖 `AtomicFile` 的 `.new/.bak` 行为（但仍保留对历史 `draft.json.bak` 的读取与清理，作为迁移兼容）。
- `loadDraft()` 只在解析成功时才迁移/清理；解析失败一律返回 null（不崩溃），并避免误删用户可能仍可恢复的残留文件。
