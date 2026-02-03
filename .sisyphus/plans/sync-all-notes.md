# 登录后全量同步笔记（含归档）+ 设置页重同步 + 进度展示

## TL;DR

> **目标**：解决“只拉到前 200 条导致笔记不全”的问题：登录后在“未完成全量同步”时自动把服务端所有 memo 拉全；设置页提供“重新同步所有笔记”（REPLACE 重启）；并在设置里展示全量同步状态/时间/进度。
>
> **核心策略**：保留现有“普通同步（最多 4 页）”用于日常刷新；新增“全量同步模式”取消页数上限，按 `pageToken/nextPageToken` 拉到末页，覆盖 `NORMAL + ARCHIVED`，`pageSize=500`，`orderBy=name asc`。

**Deliverables**
- 全量同步模式（同一 Worker 多模式）：自动（未完成则全量）+ 手动强制全量
- 全量同步状态持久化（DataStore / AppSettings 扩展）
- 设置页新增“重新同步所有笔记”入口 + 显示状态/时间/进度
- 单元测试：全量分页 loop、两阶段（NORMAL+ARCHIVED）、REPLACE 语义、失败不置 SUCCESS

**Estimated Effort**: Medium
**Parallel Execution**: YES (2 waves)
**Critical Path**: Sync 状态模型 → Worker full-sync 实现 → Settings UI/触发点 → 测试与验证

---

## Context

### 原始诉求
- 登录后要主动从服务器把“所有笔记”拿全。
- 设置里可以点击“重新同步所有笔记”。
- 现状：如果笔记没拿完很不方便。

### 现状定位（根因）
- `app/src/main/java/cc/pscly/onememos/worker/MemosSyncWorker.kt` 的 `refreshFromServer()` 目前硬限制：`while (pages < 4)`，且 `pageSize=50`、`state=NORMAL`。
  - 这会导致最多只回拉约 200 条，历史更早的 memo 永远不会进入本地 DB，因此 UI 本地 Paging 翻页也翻不出来。

### 现有触发点（会 enqueue 同一个同步 Worker）
- 登录成功：`app/src/main/java/cc/pscly/onememos/ui/feature/auth/AuthViewModel.kt` 调用 `syncScheduler.requestSync()`。
- 首页启动后自动同步：`app/src/main/java/cc/pscly/onememos/ui/feature/home/HomeScreen.kt` 登录后 delay 800ms 调 `viewModel.requestSync()`。
- 设置页“立即同步”：`app/src/main/java/cc/pscly/onememos/ui/feature/settings/SettingsScreen.kt` → `SettingsViewModel.requestSyncNow()` → `SyncScheduler.requestSync()`。

### 服务端 API 约束（官方）
- Memos v1 API 列表分页：`pageSize` + `pageToken` → `nextPageToken`，循环直到 `nextPageToken` 为空/省略。
- `pageSize` 最大 1000；本方案选 500。
- `state` 支持 `NORMAL/ARCHIVED`；`showDeleted` 可选（本方案不启用，排除 deleted）。

外部参考（证据/示例）
- 官方 OpenAPI/proto（分页与参数）：
  - https://github.com/usememos/memos/blob/d14cfa1c4fcbb335f22da6d0ebbfafd2a87e75f7/proto/gen/openapi.yaml
  - https://github.com/usememos/memos/blob/d14cfa1c4fcbb335f22da6d0ebbfafd2a87e75f7/proto/api/v1/memo_service.proto
- 开源 Android 客户端示例（分页 loop 参考）：
  - https://github.com/mudkipme/MoeMemosAndroid/blob/release/app/src/main/java/me/mudkip/moememos/data/repository/MemosV1Repository.kt

---

## Work Objectives

### Core Objective
在不牺牲日常同步体验的前提下，实现“首次/需要时全量同步”，确保本地 DB 拥有服务端全部 memo（NORMAL+ARCHIVED），并提供可见的进度/状态。

### Scope
IN:
- Worker 增强：普通同步 vs 全量同步模式
- 全量同步状态的持久化与 UI 展示
- 设置页手动触发“重新同步所有笔记”（REPLACE）
- 单测覆盖关键路径

OUT (Guardrails):
- 不做本地清库（不清空 memos/attachments 表）
- 不做“本地 serverId 但服务端缺失”的自动清理/删除对齐
- 不引入双向同步策略重做（上传逻辑保持不动）
- 不新增复杂通知/前台服务（默认后台静默；仅设置页展示状态/进度）

### Definition of Done
- 登录后（仅当 full sync 未完成）会执行 full sync，最终本地 memos 完整（NORMAL+ARCHIVED）。
- 设置页存在“重新同步所有笔记”，点击后会 REPLACE 重启 full sync。
- 设置页展示：状态（RUNNING/SUCCESS/FAILED）、最后成功时间、进度（阶段 + 页数/条数）。
- `.\gradlew.bat :app:testDebugUnitTest` 全部通过。

---

## Verification Strategy

### Test Decision
- **Infrastructure exists**: YES（JUnit4 + Robolectric 示例已存在）
- **User wants tests**: YES

### 测试形态（推荐）
- 单测放在：`app/src/test/java/...`
- 主要策略：JUnit4 + Robolectric（提供 Context）+ in-memory Room（真实 MemoDao）+ FakeMemosApi（可控分页响应）
- 验证以 Gradle 命令为准（Windows）：
  - `.\gradlew.bat :app:testDebugUnitTest`

---

## Execution Strategy

### Parallel Execution Waves

Wave 1 (可并行，先打基础)
- Task 1：同步状态模型/存储（DataStore + AppSettings 扩展）
- Task 2：Settings UI 入口与状态展示（先做 UI，逻辑可临时 stub）

Wave 2（依赖 Wave 1 的模型字段）
- Task 3：Worker 多模式（普通/全量/强制全量）+ 分页保险丝 + 进度写入
- Task 4：触发点接线（Auth/Home/Settings 复用 requestSync，Settings 手动走 REPLACE 强制 full）
- Task 5：测试补齐 + 验证命令 + benchmark 打包

---

## TODOs

### 1) 定义 Full Sync 状态模型与持久化（DataStore）

**What to do**
- 在 `SettingsRepository`/`SettingsRepositoryImpl` 中新增 full sync 相关字段与读写方法：
  - status: `IDLE/RUNNING/SUCCESS/FAILED`
  - runId（用于 REPLACE/取消时避免旧 worker 误写 SUCCESS）
  - lastSuccessAt
  - lastError
  - progress: stage（NORMAL/ARCHIVED）、pagesFetched、itemsFetched
  - syncKey（至少基于 `effective serverUrl` + `currentUserCreator`；creator 为空时可先用 serverUrl，后续补齐）
- 明确 syncKey 的默认计算规则（避免歧义）：
  - `syncKey = normalizeServerBase(AppSettings.serverUrl) + "|" + AppSettings.currentUserCreator.trim()`
  - 若 `currentUserCreator` 为空：临时 `syncKey = normalizeServerBase(serverUrl) + "|" + "(unknown)"`，在 `ensureCurrentUserCreator()` 成功后刷新 key。
  - 当 `syncKey` 变化（例如换账号/换 serverUrl/creator 变化）时：将 full sync 状态重置为 `IDLE`（或 `FAILED`）并要求重新 full sync。
- 在 `AppSettings` 与 `SettingsUiState` 增加这些字段，以便 SettingsScreen 展示。

**Must NOT do**
- 不引入多账号复杂映射（默认单账号/单 serverUrl）；如未来需要再做 JSON map 或 Room 表。

**Recommended Agent Profile**
- Category: `unspecified-high`
- Skills: `project-analyze`

**Parallelization**
- Can Run In Parallel: YES（与 Task 2）

**References**
- `app/src/main/java/cc/pscly/onememos/data/settings/SettingsRepositoryImpl.kt`：DataStore key 与 settings flow 的实现位置。
- `app/src/main/java/cc/pscly/onememos/domain/model/AppSettings.kt`：SettingsScreen 读取的数据模型。
- `app/src/main/java/cc/pscly/onememos/ui/feature/settings/SettingsViewModel.kt`：UI state 组合入口。

**Acceptance Criteria**
- 新增字段可在 SettingsScreen 读到（编译通过）。
- `.\gradlew.bat :app:assembleDebug` → SUCCESS。

### 2) 设置页新增“重新同步所有笔记”入口 + 状态/进度展示

**What to do**
- 在 `SettingsScreen` 的“账号与同步”卡片（bound==true 分支）新增按钮：
  - 文案：重新同步所有笔记
  - 二次确认对话框（仿照 logout confirm）
  - 点击确认后调用 `viewModel.requestFullResync()`（新方法）
- 同一区域增加状态展示：
  - RUNNING：显示 stage（NORMAL/ARCHIVED）+ pages/items
  - SUCCESS：显示 lastSuccessAt
  - FAILED：显示 lastError + 可重试（按钮同上）

**Must NOT do**
- 不引入通知栏/前台服务（保持后台静默；仅设置页展示）。

**Recommended Agent Profile**
- Category: `visual-engineering`
- Skills: `frontend-ui-ux`

**Parallelization**
- Can Run In Parallel: YES（与 Task 1）

**References**
- `app/src/main/java/cc/pscly/onememos/ui/feature/settings/SettingsScreen.kt`：现有“立即同步”“退出”“确认弹窗”的写法。
- `app/src/main/java/cc/pscly/onememos/ui/feature/settings/SettingsViewModel.kt`：现有 `requestSyncNow()` 与 bound 判断。

**Acceptance Criteria**
- 设置页可看到新入口与状态区块；编译通过。
- `.\gradlew.bat :app:assembleDebug` → SUCCESS。

### 3) Worker 增加多模式：普通 sync vs full sync（NORMAL+ARCHIVED）

**What to do**
- 在 `MemosSyncWorker` 中加入 mode 决策：
  - 默认 mode=普通同步（保留 `pages < 4` 以避免拉爆）
  - 当 `fullSyncStatus != SUCCESS` 时：进入 full sync 模式
  - 当 inputData `forceFull=true` 时：强制 full sync（忽略 SUCCESS）
- full sync 拉取策略：
  - 分两段：`state=NORMAL` 全量分页到结束，再 `state=ARCHIVED` 全量分页到结束
  - `pageSize=500`
  - `orderBy="name asc"`
  - `showDeleted` 不传（或 false）
- 分页保险丝：
  - 检测 nextPageToken 重复 → 失败退出（记录错误）
  - loop 中定期检查取消（REPLACE 会 cancel）→ 取消时标记 FAILED/或保持 RUNNING 但不写 SUCCESS
- 进度写入：每拉完一页（或每 N 页）更新 DataStore 中的 pages/items/stage，并写入 runId 归属。

**Must NOT do**
- 不清空本地 DB；不删除本地 serverId 但服务端缺失的记录。
- 不覆盖本地未同步 memo：保留现有 `if (local != null && local.syncStatus != SYNCED) continue` 规则。

**Recommended Agent Profile**
- Category: `unspecified-high`
- Skills: `project-analyze`

**Parallelization**
- Can Run In Parallel: NO（依赖 Task 1 的状态字段）

**References**
- `app/src/main/java/cc/pscly/onememos/worker/MemosSyncWorker.kt`：现有分页限制与 upsert 写库位置。
- `app/src/main/java/cc/pscly/onememos/core/network/MemosApi.kt`：listMemos 参数（pageSize/pageToken/state/orderBy/showDeleted）。
- `app/src/main/java/cc/pscly/onememos/domain/model/MemoServerState.kt`：NORMAL/ARCHIVED。
- 外部：`mudkipme/MoeMemosAndroid` 的 `MemosV1Repository.kt`（nextPageToken do-while 拉全量）作为分页 loop 参考。

**Acceptance Criteria**
- full sync 在 mock API（测试）里能跑到 nextPageToken 为空后停止，并正确拉 NORMAL+ARCHIVED。
- cancel/REPLACE 不会把旧 run 标记为 SUCCESS。
- `.\gradlew.bat :app:assembleDebug` → SUCCESS。

### 4) 触发点接线：登录后/启动后“未完成则全量”；设置页“强制全量（REPLACE）”

**What to do**
- 扩展 `SyncScheduler`：新增 `requestFullResync()`（或类似），用于 Settings 手动触发。
  - 普通 `requestSync()` 保持原语义（所有既有调用点不改/少改）。
  - `requestFullResync()` enqueue same unique work name，但 policy=REPLACE，并带 inputData `forceFull=true`。
- 调度策略：保持 HomeScreen 现有启动后 requestSync（800ms）不变；Worker 自己决定是否要 full sync，从而满足“下次启动继续”。

**Must NOT do**
- 不在 UI 里直接 new WorkRequest（除非已有范例必须这样做）；优先走 Scheduler 统一管理。

**Recommended Agent Profile**
- Category: `unspecified-high`
- Skills: `project-analyze`

**Parallelization**
- Can Run In Parallel: NO（依赖 Task 3 的 inputData 与状态模型）

**References**
- `app/src/main/java/cc/pscly/onememos/domain/sync/SyncScheduler.kt`
- `app/src/main/java/cc/pscly/onememos/worker/WorkManagerSyncScheduler.kt`（现有 enqueueUniqueWork KEEP）
- `app/src/main/java/cc/pscly/onememos/ui/feature/auth/AuthViewModel.kt`（登录后 requestSync）
- `app/src/main/java/cc/pscly/onememos/ui/feature/home/HomeScreen.kt`（启动后 requestSync）
- `app/src/main/java/cc/pscly/onememos/ui/feature/settings/SettingsViewModel.kt`（新增 requestFullResync 暴露给 UI）

**Acceptance Criteria**
- Settings 点击“重新同步所有笔记”会 REPLACE 当前同步并重新开始。
- 未完成 full sync 的情况下，启动后现有 requestSync 会触发 full 模式。

### 5) 测试补齐 + 构建验证 + benchmark 打包

**What to do**
- 新增单测（建议放 `app/src/test/java/...`）：
  - `fullSync_paginatesUntilTokenBlank_normalAndArchived`
  - `fullSync_doesNotMarkSuccessOnCancelOrFailure`
  - `fullSync_replaceRunIdGuardsOldWorker`
  - `fullSync_skipsDirtyLocalMemo`
- 关键验证命令（Windows/pwsh）：
  - `.\gradlew.bat :app:testDebugUnitTest`
  - `.\gradlew.bat :app:assembleDebug`
  - `.\gradlew.bat :app:assembleBenchmark`（如果该构建变体存在；最终交付按仓库约定给出带时间戳的 benchmark APK 路径）

**Recommended Agent Profile**
- Category: `unspecified-high`
- Skills: `project-analyze`

**Parallelization**
- Can Run In Parallel: NO（依赖代码完成）

**References**
- `app/src/test/java/cc/pscly/onememos/share/ShareIntentParserTest.kt`（Robolectric 测试范例）
- `app/src/test/java/cc/pscly/onememos/core/network/FlowDeviceHeadersInterceptorTest.kt`（手写 fake/stub 范例）
- `app/src/main/java/cc/pscly/onememos/core/database/OneMemosDatabase.kt`（in-memory Room 入口）

**Acceptance Criteria**
- `.\gradlew.bat :app:testDebugUnitTest` → SUCCESS（新增测试全部通过）
- `.\gradlew.bat :app:assembleBenchmark` → SUCCESS（如存在 benchmark 变体），产物可定位并按时间戳重命名输出。

---

## Commit Strategy

- Commit 1：`feat(sync): add full sync state model and settings UI`
- Commit 2：`feat(sync): add full sync mode to worker and scheduler`
- Commit 3：`test(sync): cover full sync pagination and replace semantics`

每个 commit 前置验证：`.\gradlew.bat :app:testDebugUnitTest`。

---

## Success Criteria

### 最终验证命令
```powershell
# 在 1memos 子项目根目录执行
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleBenchmark
```

### Final Checklist
- [x] 登录后（full 未完成）能够在后台完成 NORMAL+ARCHIVED 全量拉取
- [x] 设置页可查看全量同步状态/时间/进度
- [x] 设置页“重新同步所有笔记”会 REPLACE 重启
- [x] 失败/取消不会把 fullSync 标记成 SUCCESS
- [x] 单测通过
