# 全量同步收尾与稳健性加固（sync-all-notes polish）

## TL;DR

> **目标**：在现有“登录后自动全量同步 + 设置页强制重同步 + 进度展示”的基础上，补齐并发/取消/终态收敛等高风险边界，避免全量同步卡 RUNNING、重复 full sync、以及 KEEP 丢请求导致的延迟同步；同时补齐关键自动化测试与工程卫生（`.gitignore`）。
>
> **核心策略**：把“Worker 是无状态函数”→“fullSync/progress 是有状态”的矛盾显式化：
> 1) 定义清晰的 fullSync 状态机（含取消语义），2) 通过编排层或 Worker 内互斥保证单实例执行，3) 用自动化测试固化边界。

**Deliverables**
- fullSync 状态机与终态收敛：取消/失败不再卡 RUNNING
- 同步并发治理：避免 periodic + one-time 并发导致重复 full sync / runId 抖动
- KEEP 丢请求的补跑策略：保证新增 DIRTY 最终能被同步（即便正在同步中）
- 多账号/多服务器：fullSync 状态按 syncKey 隔离/重置，切换账号不串台
- 补测：重复 pageToken 保险丝、取消终态（CANCELLED）、setFullSyncFailed runId guard、syncKey 隔离/切换
- 工程卫生：忽略 Kotlin LSP 临时文件，避免误提交

**Estimated Effort**: Medium
**Parallel Execution**: YES（2 waves）
**Critical Path**: 状态机语义 → 互斥/编排策略 → 终态收敛实现 → 测试固化 → lint/benchmark 验收

## Task Checklist (for boulder)
- [x] 0) 多账号：fullSync 状态按 syncKey 隔离（切换不串台）
- [x] 1) fullSync 取消终态：引入 CANCELLED 并确保取消不再卡 RUNNING
- [x] 2) 补齐关键测试：重复 token、CANCELLED、failed runId guard、syncKey 隔离切换
- [x] 3) 工程卫生：忽略 `.kotlin-lsp-tmp/` 与 `kotlin-lsp.cmd`
- [x] 4) 并发治理：fullSync RUNNING 时 periodic 跳过；避免重复进入 full sync
- [x] 5) KEEP 丢请求补跑：最多补跑一次（followup 标记），保证新增 DIRTY 最终同步
- [x] 6) 门禁验收：test/lint/debug/benchmark + 时间戳 benchmark APK

---

## Context

### 当前已具备（已完成并通过构建/测试）
- `MemosSyncWorker` 支持 full sync（NORMAL+ARCHIVED）、分页拉全、进度写回 DataStore。
- 设置页提供“重新同步所有笔记”（WorkManager REPLACE + forceFull）。
- 已有 JVM 单测与 benchmark 构建链路。

### 审计发现（高风险点）
- **并发**：periodic work（`one_memos_periodic_sync`）与 one-time work（`one_memos_sync`）名字不同，可能并发运行两个 `MemosSyncWorker`，导致重复 full sync / runId thrash / DB 写放大。
- **取消语义**：`CancellationException` 直接透传，fullSync 状态可能长期停留 RUNNING（设置页“卡住”）。
- **KEEP 丢请求**：同步中多次 `requestSync()` 会被 KEEP 丢弃，新增 DIRTY 可能延迟到下次触发/periodic。

### Metis Review（已纳入本计划的 guardrails）
- 必须显式定义 fullSync 状态机（含 CANCELLED/终态收敛）；任何退出路径都落盘。
- 必须解决 periodic + one-time 并发（优先编排层；否则 worker 内互斥 + retry/skip）。
- 必须修复 KEEP 丢请求导致的延迟同步（至少保证“最终会被同步”）。
- 相关变更必须有自动化验收（Gradle 可执行），避免依赖人工点击。

---

## Work Objectives

### Core Objective
让“全量同步”在真实环境（REPLACE 取消、周期任务、频繁本地变更）下可恢复、可预期、可验证。

### Concrete Deliverables
- fullSync 在取消/失败/重启后不会卡 RUNNING，UI 显示可解释的终态。
- 同一时刻最多一个“真正执行同步逻辑”的 worker（至少在 full sync 期间）。
- 新增 DIRTY 在同步进行中也不会被 KEEP 永久吞掉，最多延迟到 worker 结束后的补跑。
- 切换 serverUrl/账号时 fullSync 状态不会串台（按 syncKey 隔离/重置）。
- `.gitignore` 覆盖 Kotlin LSP 临时文件。

### Must NOT Have（Guardrails）
- 不重做同步架构、不引入复杂通知/前台服务（除非为解决 Android 后台限制明确必要且你同意）。
- 不做本地清库/删除对齐。
- 不扩大 UI 改动范围（仅围绕 fullSync 状态展示必要补强）。

---

## Decisions Made（已确认）

1) **取消后的状态**：使用 `FullSyncStatus.CANCELLED`
- 目标：取消后不再卡 RUNNING，且 UI 语义明确是“取消”而不是失败。

2) **并发策略（periodic 与 one-time）**：full sync RUNNING 时，periodic 触发跳过
- 目标：避免 periodic + one-time 并发导致重复 full sync/runId 抖动。

3) **KEEP 丢请求的处理**：采用“补跑一次”策略
- 目标：同步进行中产生的新 DIRTY 不会被 KEEP 永久吞掉，worker 结束时触发一次补跑即可。

4) **多账号/多服务器**：存在切换
- 策略：fullSync 状态必须按 syncKey 隔离（至少做到“切换不串台”；可选保留每个 syncKey 的历史状态）。

---

## Verification Strategy

### Test Decision
- **Infrastructure exists**: YES（JUnit4 + Robolectric 已存在）
- **User wants tests**: YES（以 JVM 单测固化边界；必要时补少量 WorkManager Test）

### Commands (agent-executable)
```powershell
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

./gradlew.bat :app:testDebugUnitTest --stacktrace
./gradlew.bat :app:lintDebug --stacktrace
./gradlew.bat :app:assembleDebug --stacktrace
./gradlew.bat :app:assembleBenchmark --stacktrace
pwsh -NoProfile -File scripts/copy-benchmark-apk.ps1
```

---

## Execution Strategy

Wave 1（先定语义/补测/快速收益）
- Task 0：多账号：fullSync 状态按 syncKey 隔离（切换不串台）
- Task 1：定义 fullSync 终态收敛（CANCELLED；取消不再卡 RUNNING）
- Task 2：补测：重复 token 保险丝、取消终态（CANCELLED）、runId guard（failed）、syncKey 隔离/切换
- Task 3：`.gitignore` 工程卫生

Wave 2（并发治理 + KEEP 丢请求补跑）
- Task 4：并发互斥策略落地（periodic vs one-time）
- Task 5：KEEP 丢请求补跑机制（最终一致性）
- Task 6：跑 lint/benchmark/验收 + 出时间戳 benchmark APK

---

## TODOs

### 0) 多账号：fullSync 状态按 syncKey 隔离（切换不串台）

**What to do**
- 明确“当前账号”的定义：`syncKey = normalizeServerBase(serverUrl) + "|" + currentUserCreator`（现有约定）。
- 将 fullSync 的持久化从“单一槽位”升级为“按 syncKey 隔离的槽位”，至少保证：
  - 切换 serverUrl/账号时，不会显示/误用上一个账号的 fullSync 状态。
  - 切回旧账号时，行为可预期（默认建议：保留该 syncKey 的 lastSuccessAt/progress，避免重复全量）。
- 兼容迁移：把旧的单槽位字段迁移到“当前 syncKey 的槽位”。

**References**
- `app/src/main/java/cc/pscly/onememos/data/settings/SettingsRepositoryImpl.kt`：目前 full_sync_* keys 为单槽位；syncKey mismatch 仅做映射不写回
- `app/src/main/java/cc/pscly/onememos/core/network/MemosUrls.kt`：normalizeServerBase
- `app/src/main/java/cc/pscly/onememos/domain/model/FullSyncState.kt`：状态模型

**Recommended Agent Profile**
- Category: `unspecified-high`
- Skills: `project-analyze`

**Acceptance Criteria**
- 新增单测：模拟两组不同 syncKey 的 fullSync 状态，切换后读取到的 fullSync 必须对应当前 syncKey
- `./gradlew.bat :app:testDebugUnitTest --stacktrace` → SUCCESS

### 1) 明确定义 fullSync 的取消/终态收敛（不再卡 RUNNING）

**What to do**
- 在 `MemosSyncWorker` 的取消路径上写入终态（CANCELLED），保证 UI 不会长期显示 RUNNING。
- 关键点：写回必须避免被取消中断（需要 `NonCancellable` 或等价手段）。

**References**
- `app/src/main/java/cc/pscly/onememos/worker/MemosSyncWorker.kt`：catch CancellationException 目前直接 throw
- `app/src/main/java/cc/pscly/onememos/domain/model/FullSyncState.kt`：状态枚举定义
- `app/src/main/java/cc/pscly/onememos/ui/feature/settings/SettingsScreen.kt`：RUNNING/SUCCESS/FAILED/CANCELLED 渲染

**Recommended Agent Profile**
- Category: `unspecified-high`
- Skills: `project-analyze`

**Acceptance Criteria**
- `./gradlew.bat :app:testDebugUnitTest` → SUCCESS
- 新增/更新测试：模拟取消后 `settings.fullSync.status` 不为 RUNNING（见 Task 2）

---

### 2) 补齐关键测试（重复 token / 取消终态(CANCELLED) / failed runId guard / syncKey 隔离切换）

**What to do**
- 增补 JVM 单测（Robolectric + in-memory Room + fake api）覆盖：
  - `nextPageToken` 重复 → 触发失败并写入 FAILED（错误可读）
  - REPLACE/取消 → 最终状态为 CANCELLED（且不再卡 RUNNING）
  - `setFullSyncFailed` runId guard：旧 run 的 FAILED 不覆盖新 run
  - 多账号：不同 syncKey 的 fullSync 状态隔离；切换 syncKey 后读取到的状态必须对应当前 syncKey

**References**
- `app/src/main/java/cc/pscly/onememos/worker/FullSyncHelpers.kt`：重复 token 保险丝
- `app/src/main/java/cc/pscly/onememos/data/settings/SettingsRepositoryImpl.kt`：runId guard + syncKey 隔离/迁移实现
- `app/src/test/java/cc/pscly/onememos/worker/FullSyncHelpersTest.kt`：现有分页/取消/本地保护测试
- `app/src/test/java/cc/pscly/onememos/data/settings/SettingsRepositoryImplFullSyncRunIdTest.kt`：现有 runId guard（progress/success）

**Recommended Agent Profile**
- Category: `unspecified-high`
- Skills: `project-analyze`

**Acceptance Criteria**
- `./gradlew.bat :app:testDebugUnitTest --stacktrace` → SUCCESS（新增测试全部通过）

---

### 3) 工程卫生：忽略 Kotlin LSP 临时文件，避免误提交

**What to do**
- 在仓库根 `.gitignore` 追加最小忽略规则：
  - `.kotlin-lsp-tmp/`
  - `kotlin-lsp.cmd`

**References**
- `../.gitignore`（仓库根：toAndroid/.gitignore；注意该文件不在 1memos 目录内）
- `kotlin-lsp.cmd`、`1memos/kotlin-lsp.cmd` 文件内明确声明不应提交

**Recommended Agent Profile**
- Category: `quick`
- Skills: `git-master`

**Acceptance Criteria**
- `git status --porcelain` 不再出现 `.kotlin-lsp-tmp/` 与 `kotlin-lsp.cmd`

---

### 4) 并发治理：避免 periodic + one-time 同时执行 full sync

**What to do**
- 实现并发策略：fullSync RUNNING 时，periodic 触发的 worker short-circuit（跳过）。
- 目标：同一时刻最多一个“执行 full sync 的 run”。

**References**
- `app/src/main/java/cc/pscly/onememos/worker/WorkManagerSyncScheduler.kt`：periodic 独立 unique name
- `app/src/main/java/cc/pscly/onememos/worker/MemosSyncWorker.kt`：needFull 判定（RUNNING != SUCCESS 可能导致重复 full）
- `app/src/main/java/cc/pscly/onememos/domain/model/FullSyncState.kt`（FullSyncStatus/Stage 定义）

**Recommended Agent Profile**
- Category: `unspecified-high`
- Skills: `project-analyze`

**Acceptance Criteria**
- 新增自动化测试证明：当 fullSync 为 RUNNING 且非 forceFull 时，不会再次进入 full sync（纯逻辑/worker 分支测试即可，不依赖 WorkManager test 库）
- `./gradlew.bat :app:testDebugUnitTest` → SUCCESS

---

### 5) KEEP 丢请求补跑：保证新增 DIRTY 最终同步

**What to do**
- 为 “同步进行中新增 DIRTY” 提供补跑机制：
  - worker 结束前（或结束后）检测是否仍有 `syncStatus != SYNCED` 的 memo，必要时再次 enqueue one-time work。
  - 补跑策略已定：**最多补跑一次**；enqueue 使用 `REPLACE`（同一 unique name），但不携带 `forceFull`。
  - 防无限循环：为“补跑”引入一个 inputData 标记（例如 `followup=true`），当 followup run 结束后仍有 DIRTY 时不再继续补跑，只保留错误信息/等待下次触发。

**References**
- `app/src/main/java/cc/pscly/onememos/data/repository/MemoRepositoryImpl.kt`：频繁 requestSync 的来源
- `app/src/main/java/cc/pscly/onememos/core/database/dao/MemoDao.kt`：查询 pending/dirty 的入口
- `app/src/main/java/cc/pscly/onememos/worker/MemosSyncWorker.kt`：syncPending 处理队列

**Recommended Agent Profile**
- Category: `unspecified-high`
- Skills: `project-analyze`

**Acceptance Criteria**
- 新增测试：模拟 worker 运行中产生新的 DIRTY，最终会触发二次同步（以可观测标记/计数断言）
- `./gradlew.bat :app:testDebugUnitTest` → SUCCESS

---

### 6) 验收门禁 + benchmark 产物

**What to do**
- 跑完整门禁：unit tests + lint + debug/benchmark 构建。
- 输出时间戳 benchmark APK（脚本已存在）。

**References**
- `scripts/copy-benchmark-apk.ps1`

**Recommended Agent Profile**
- Category: `unspecified-low`
- Skills: `git-master`

**Acceptance Criteria**
- `./gradlew.bat :app:testDebugUnitTest --stacktrace` → SUCCESS
- `./gradlew.bat :app:lintDebug --stacktrace` → SUCCESS
- `./gradlew.bat :app:assembleBenchmark --stacktrace` → SUCCESS
- `pwsh -NoProfile -File scripts/copy-benchmark-apk.ps1` 输出类似 `app/build/outputs/apk/benchmark/yyyy-MM-ddTHH-mm-ss.apk`

---

## Commit Strategy

- Commit A：`fix(sync): fullSync 取消与并发终态收敛`
- Commit B：`test(sync): 补齐 fullSync 边界单测`
- Commit C：`chore: ignore kotlin-lsp temp files`

每个 commit 前置验证：`./gradlew.bat :app:testDebugUnitTest`

---

## Success Criteria

- fullSync 不再出现“无限 RUNNING 卡住”
- full sync 与 periodic 不会并发导致重复 full sync
- 新增 DIRTY 不会被 KEEP 永久吞掉（最终一致）
- 所有验收命令通过，并产出时间戳 benchmark APK
