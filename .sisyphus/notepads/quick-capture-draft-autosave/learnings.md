# Learnings

## 2026-02-13
- （预留）

- 草稿落盘用“同目录 tmp + rename 覆盖”实现原子写：`draft.json.tmp` 写完 `fd.sync()` 后 `move(REPLACE_EXISTING)` 到 `draft.json`，避免并发/中途崩溃导致 JSON 半截。
- `loadDraft()` 读取按 `draft.json -> draft.json.tmp -> draft.json.bak` 兜底；若从 tmp/bak 恢复，会迁移回 `draft.json` 并清理残留文件，避免出现“banner 提示有草稿但无法恢复”的体验。
- 所有 API 都在 `withContext(Dispatchers.IO)` 内再 `Mutex.withLock`，规避 runBlocking 场景下“主线程持锁 + IO 切线程”的潜在死锁。

- `insertCurrentTimeStamp()` 这类“非输入框直接回填内容”的入口也要走 `updateContent()/onContentChange()` 这类统一路径，否则会绕过草稿覆盖确认与自动保存（Overlay/Editor 已采用同样模式）。
