# Decisions

## [2026-02-01T13:57:18Z] Initial Decisions
- 自动全量触发：仅当 full sync 未完成时触发；下次启动继续补齐
- 手动“重新同步所有笔记”：REPLACE 重启
- 范围：NORMAL + ARCHIVED；不含 deleted
- pageSize=500；orderBy="name asc"
- 不清本地 DB；不清理本地陈旧 serverId 记录

## [2026-02-01T14:11:40Z] Full Sync 状态持久化模型
- 状态模型：`AppSettings.fullSync: FullSyncState`（status/runId/lastSuccessAt/lastError/stage/pagesFetched/itemsFetched/syncKey）
- syncKey：`normalizeServerBase(AppSettings.serverUrl) + "|" + currentUserCreator.trim()`；creator 为空用 `(unknown)`
- 读取策略：DataStore 内的 syncKey 与当前 syncKey 不一致时，仅在映射层将状态视为 `IDLE`（不在 Flow map 中做任何写回）
- 写入策略：通过 `SettingsRepository.setFullSyncRunning/Progress/Success/Failed` 更新；写入时会重算并持久化 syncKey；Progress/Success/Failed 额外用 runId 做守卫，避免旧 run 覆写新 run
