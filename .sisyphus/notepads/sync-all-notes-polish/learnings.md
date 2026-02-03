# Learnings

## [2026-02-02T03:27:05Z] Bootstrap
- Plan: `.sisyphus/plans/sync-all-notes-polish.md`
- Goal: fullSync 收尾与稳健性加固（CANCELLED、并发治理、补跑一次、多账号隔离）

## [2026-02-02T03:35:00Z] Task 3 Done (.gitignore)
- 在仓库根 `../.gitignore` 增加忽略规则：`.kotlin-lsp-tmp/`、`kotlin-lsp.cmd`，避免 Kotlin LSP 临时文件误提交。

## [2026-02-02T04:05:00Z] Task 0 Done (fullSync per syncKey)
- `SettingsRepositoryImpl` 将 fullSync 从“单槽位”升级为“按 syncKey 分槽位”，使用 `sha256(syncKey)` 的短 hash 作为 Preferences key 后缀。
- 兼容迁移：保留 legacy keys（只读回退）并在 init 的 IO scope 里迁移到分槽位 keys（不在 settings Flow map 中写回）。
- 单测：新增 `SettingsRepositoryImplFullSyncKeyIsolationTest` 覆盖 creator/serverUrl 切换隔离与切回恢复。

## [2026-02-02T04:20:00Z] Task 1 Done (CANCELLED)
- 新增 `FullSyncStatus.CANCELLED`，Settings 支持显示“已取消”。
- Worker 在 `CancellationException` 时用 `NonCancellable` 先写入 CANCELLED，再透传取消，避免 fullSync 卡 RUNNING。

## [2026-02-02T04:35:00Z] Task 2 Done (tests)
- `FullSyncHelpersTest` 新增：重复 nextPageToken 触发异常（防死循环）。
- `SettingsRepositoryImplFullSyncRunIdTest` 补齐：old-run 的 FAILED/CANCELLED 写入不会覆盖 new-run。

## [2026-02-02T04:50:00Z] Task 4 Done (periodic skip)
- 周期任务通过 inputData `is_periodic=true` 标记，Worker 侧保证周期不会触发 full sync。
- fullSync RUNNING 时周期任务直接 early-return，避免 periodic 与 one-time 重叠执行。

## [2026-02-02T05:05:00Z] Task 5 Done (followup)
- 在 Worker 成功结束前检查 `memoDao.listMemosNeedingSync()`，若仍有 DIRTY 则 enqueue 一次 followup（inputData `followup_sync=true`），最多补跑一次防止无限循环。
- followup 视为“轻量同步”：不会触发 full sync。

## [2026-02-02T05:15:00Z] Task 6 Done (QA + benchmark)
- 门禁通过：`:app:testDebugUnitTest`、`:app:lintDebug`、`:app:assembleDebug`、`:app:assembleBenchmark`
- 时间戳 benchmark APK：`app/build/outputs/apk/benchmark/2026-02-02T14-10-53.apk`

## [2026-02-02T04:09:12Z] Task 0 Done (fullSync 多账号隔离)
- 存储策略：使用动态 Preferences key 分槽；key 后缀为 `sha256(syncKey)` 的前 12 位 hex；槽位字段为 `full_sync_status_<hash>`/`full_sync_run_id_<hash>`/...；syncKey 仍为 `normalizeServerBase(serverUrl) + "|" + creator(or (unknown))`。
- 读取策略：`settings` 映射只读当前 syncKey 槽位；若迁移未完成且槽位为空，则仅在 `legacy full_sync_key == 当前 syncKey` 时只读回退到旧单槽位状态（不在 Flow 里写回）。
- 迁移策略：在 `SettingsRepositoryImpl` init 的 IO scope 做一次性搬运；旧槽位有 `full_sync_key` 时迁到对应槽位（槽位为空才复制），并清理旧 key；若旧 `full_sync_key` 缺失，则按“当前账号/服务器” best-effort 迁移，且仅在复制后清理旧 key。
- 边界：hash 理论上可能碰撞但概率极低；当旧 `full_sync_key` 缺失时，历史状态归属可能不完全准确（best-effort）。

## [2026-02-02T05:36:49Z] Task 2 Done (tests)
- `FullSyncHelpersTest`：新增“重复 nextPageToken”单测，验证会抛出 `IllegalStateException` 且不会陷入分页死循环。
- `SettingsRepositoryImplFullSyncRunIdTest`：补齐 runId guard 覆盖：`FAILED`/`CANCELLED` 的旧 runId 不得覆盖新 `RUNNING`。
- 验证：`./gradlew.bat :app:testDebugUnitTest --stacktrace` 通过。

## [2026-02-02T05:44:18Z] Task 4 Done (periodic 并发治理)
- 周期 WorkRequest 注入 `KEY_IS_PERIODIC=true`（并用 `ExistingPeriodicWorkPolicy.UPDATE` 确保升级后能更新到已存在的周期任务）。
- Worker 读取该标记：周期同步永不触发 full sync；若 fullSync 为 `RUNNING` 则在 doWork 早期直接跳过（`Result.success()`），避免重叠。

## [2026-02-02T06:00:40Z] Task 5 Done (KEEP 丢请求补跑)
- Worker 读取 inputData `followup_sync=true` 作为补跑标记：补跑视同周期同步，不触发 full sync。
- 普通一次性同步成功结束时，如果 `memoDao.listMemosNeedingSync()` 仍非空，则 enqueue 一次 followup；followup 结束后不再继续补跑，避免无限循环。
- 调度策略：优先使用 `ExistingWorkPolicy.APPEND` 追加到 `one_memos_sync` 链尾；若版本不支持则退化为独立 unique work（只保证不自取消）。
- 验证：`./gradlew.bat :app:testDebugUnitTest --stacktrace` 通过。
