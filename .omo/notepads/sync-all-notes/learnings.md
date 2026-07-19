# Learnings

## [2026-02-01T13:57:18Z] Bootstrap
- Plan: `.sisyphus/plans/sync-all-notes.md`
- Goal: 登录后全量同步（NORMAL+ARCHIVED）+ 设置页手动重同步 + 状态/进度展示

## [2026-02-01T14:30:00Z] Task 1 Verified
- FullSync 状态已落到 DataStore，并通过 `AppSettings.fullSync` 与 `SettingsViewModel.SettingsUiState` 透出。
- runId 守卫：`setFullSyncProgress/Success/Failed` 会检查 DataStore 内的 storedRunId，避免旧 run 覆写。
- syncKey：`MemosUrls.normalizeServerBase(serverUrl) + "|" + creatorOr(unknown)`；syncKey 不匹配时映射层直接视为未完成（IDLE），不做 Flow 写回。
- Build 验证：`./gradlew.bat :app:assembleDebug` 在 `1memos/` 项目目录执行通过。

## [2026-02-01T14:33:15Z] Task 2 Note
- `SettingsViewModel` 新增 `requestFullResync()`，当前仅占位：直接调用 `syncScheduler.requestSync()`，后续再接入真正的全量重同步语义。

## [2026-02-01T14:41:59Z] Task 3 Note
- `SettingsScreen` 的“账号与同步”卡片（已登录分支）新增“重新同步所有笔记”按钮与确认弹窗，并展示全量同步状态（RUNNING/SUCCESS/FAILED/IDLE）与时间/进度。

## [2026-02-01T15:05:00Z] Worker Full Sync Implemented
- `app/src/main/java/cc/pscly/onememos/worker/MemosSyncWorker.kt` 增加 full sync 分支：`force_full_sync` 或 `settings.fullSync.status != SUCCESS` 时执行。
- FULL SYNC 参数：`pageSize=500`、`orderBy="name asc"`、按 `NORMAL -> ARCHIVED` 两段拉取直到 `nextPageToken` 为空。
- 进度与状态：通过 `SettingsRepository.setFullSyncRunning/Progress/Success/Failed` 写回 DataStore。
- 防死循环：按“请求 token”去重（HashSet），重复则 FAILED。
- 401/403：标记 FAILED("鉴权失败，请重新登录") 并返回 success（不重试）。

## [2026-02-01T16:20:00Z] Wire Full Resync Trigger
- `SyncScheduler.requestFullResync()` 作为“重新同步所有笔记”的入口；默认实现仍回退到 `requestSync()`。
- `WorkManagerSyncScheduler.requestFullResync()`：对 `MemosSyncWorker.UNIQUE_WORK_NAME` 使用 `ExistingWorkPolicy.REPLACE`，并通过 inputData 传入 `MemosSyncWorker.KEY_FORCE_FULL_SYNC=true`。
- `SettingsViewModel.requestFullResync()` 已改为调用 `syncScheduler.requestFullResync()`，从而触发真正的 REPLACE + forceFull 语义。

## [2026-02-01T17:10:00Z] Task 5 Unit Tests Approach
- 为避免 WorkManager 集成测试脆弱性，将全量同步的“分页/进度/写库保护”抽取到 `app/src/main/java/cc/pscly/onememos/worker/FullSyncHelpers.kt`，在 JVM 单测中直接调用。
- 测试策略：
  - 分页：使用 `FakeMemosApi` 预置 state+pageToken -> response，断言 NORMAL/ARCHIVED 两段都拉到 nextPageToken 为空且进度累计正确。
  - 取消/失败：通过注入 `ensureActive` 抛 `CancellationException`，验证不会触发 success 回调。
  - 本地保护：用 in-memory Room（`OneMemosDatabase`）预置 `syncStatus != SYNCED` 的本地 memo，验证远端同名 memo 不会覆盖。
- runId 守卫：为绕开 JVM 下 EncryptedSharedPreferences/Keystore 约束，引入 `TokenStorage` 抽象，让 `SettingsRepositoryImpl` 可用内存实现注入并覆盖 runId guard。

## [2026-02-02T00:06:00Z] Verification + Benchmark Output
- 已验证：`./gradlew.bat :app:testDebugUnitTest`、`./gradlew.bat :app:assembleDebug`、`./gradlew.bat :app:assembleBenchmark` 均通过。
- benchmark APK（时间戳复制）：`app/build/outputs/apk/benchmark/2026-02-02T00-05-10.apk`
