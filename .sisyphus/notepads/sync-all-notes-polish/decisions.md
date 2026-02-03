# Decisions

## [2026-02-02T03:27:05Z] Confirmed By User
- 取消后的状态：`FullSyncStatus.CANCELLED`
- 并发策略：fullSync RUNNING 时 periodic 跳过
- KEEP 丢请求：最多补跑一次
- 多账号/多服务器：存在切换，需要 fullSync 状态按 syncKey 隔离/迁移
