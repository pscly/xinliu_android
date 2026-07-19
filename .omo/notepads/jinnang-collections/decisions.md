# Decisions

## 2026-02-13
- Active plan: jinnang-collections

- 数据权威来源锁定：`apidocs/collections.zh-CN.md`。
- 同步通道锁定：资源名 `collection_item` 必须并入同一条 Flow sync push/pull 管道（全局 cursor 语义）。
- 冲突策略锁定：客户端优先；sync push `rejected(reason=conflict, server={...})` 时保存 `server` 快照为 `localOnly=true` 冲突副本（新 id，name 后缀 `_冲突_yyyy-MM-ddTHH-mm-ss`），并重推更大 `client_updated_at_ms` 的 upsert 覆盖服务端。
