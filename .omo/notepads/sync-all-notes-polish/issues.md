# Issues

## [2026-02-02T03:27:05Z] Known Risks
- periodic 与 one-time 并发可能导致重复 full sync / runId thrash
- CancellationException 透传可能导致 fullSync 状态卡 RUNNING（需引入 CANCELLED + 终态收敛）
- KEEP 丢请求可能导致新增 DIRTY 延迟同步（需补跑一次机制）
