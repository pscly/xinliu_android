# Issues

## [2026-02-01T13:57:18Z] Known Constraints
- 现有 `MemosSyncWorker.refreshFromServer()` 有 pages<4 限制（约 200 条），必须保留普通 sync 与 full sync 的区分
- WorkManager REPLACE 需要 worker 支持协作式取消，否则用户体验会差

## [2026-02-01T14:30:00Z] Tooling
- `lsp_diagnostics` 在 Windows + Bun v1.3.5 环境被安全保护拦截（提示已知崩溃 bug），本会话无法使用 LSP 工具；以 Gradle 编译/测试作为替代验证。
- 工作区有本地 LSP 临时文件（未入库）：`.kotlin-lsp-tmp/`、`kotlin-lsp.cmd`、`../kotlin-lsp.cmd`。
