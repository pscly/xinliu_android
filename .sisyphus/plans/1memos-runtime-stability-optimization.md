# 1memos 运行时性能与稳定性优化

## Task Checklist
- [x] Task 1: 截图记录写盘/编码切到 IO 线程 + 单测覆盖
- [x] Task 2: Receiver + Singleton 协程入口补齐兜底（最小侵入）+ 单测覆盖
- [x] Task 3: 新增“附件上传大小上限”设置（默认 50MB，dev2 可调到 1GB）+ 单测覆盖
- [x] Task 4: MemosSyncWorker 附件上传：流式 base64 JSON RequestBody + 超限跳过 + 错误分类 + 单测覆盖
- [x] Task 5: Worker 重试治理（FlowTodoSyncWorker + MemosSyncWorker）+ 单测覆盖
- [x] Task 6 (optional): 附件缓存 trim 抖动优化（降低全量扫描/排序频率）
- [ ] Task 7: 门禁/benchmark/ADB 交付 + 更新 `.ai_session.md` + git 提交

## TL;DR

> **目标**：优先解决运行时卡顿/OOM 与稳定性（异常/重试）问题，避免高频路径出现主线程 I/O、同步上传大附件 OOM、Receiver/长生命周期协程未兜底导致的崩溃，以及不合理的重试风暴。
>
> **交付物**：
> - 截图记录：落盘/编码强制在 `Dispatchers.IO`（不再阻塞 UI 线程）
> - 同步上传：附件大小上限（默认 50MB，开发者模式可调，最大 1GB）+ 超限附件跳过并提示 + 大附件上传避免一次性读入/字符串膨胀（流式 base64 JSON RequestBody）
> - 稳定性：`TodoReminderAlarmReceiver` / `AppSettingsState` 等关键协程入口补齐异常兜底 + 超时
> - 重试治理：`FlowTodoSyncWorker` / `MemosSyncWorker` 的错误分类与重试边界更清晰（避免不可恢复错误无限 retry）
> - 自动化验证：补充 Robolectric/协程单测 + 通过现有 `./scripts/verify.sh`，最终构建 benchmark APK 并尝试 ADB 安装
>
> **预计工作量**：Large
> **并行执行**：YES（可拆 2-3 波）
> **关键路径**：附件上传（流式 base64 + 超限策略 + 测试）

---

## Context

### 原始诉求
- “详细检查哪些地方可以优化”，先给完整清单；随后确认优先做 **3+4**：运行时卡顿/OOM + 稳定性（异常/重试）。

### 已确认决策
- 附件上传大小上限：默认 **50MB**；开发者模式可调整，最大 **1GB**。
- 超限处理：**跳过该附件**（同步继续），并给出明确错误提示（避免卡住整体同步）。
- 协程治理：**最小侵入**（try/catch + timeout + exception handler 等兜底），不做大规模架构重构。
- 验证：**补充单测**（Robolectric/协程测试）+ 现有脚本门禁 + benchmark 构建。

### 关键定位点（证据）
- 主线程 I/O：`app/src/main/java/cc/pscly/onememos/screenshot/ScreenshotQuickCaptureActivity.kt`（`saveBitmapToCache` 同步写盘 + PNG 编码）
- 大附件 OOM：`core/sync/src/main/java/cc/pscly/onememos/worker/MemosSyncWorker.kt`（`readBytes()` + `ByteArrayOutputStream.toByteArray()` + `Base64.encodeToString`）
- Receiver 协程兜底：`core/sync/src/main/java/cc/pscly/onememos/worker/TodoReminderAlarmReceiver.kt`（`goAsync()` + `launch`）
- 长生命周期协程兜底：`core/network/src/main/java/cc/pscly/onememos/core/network/AppSettingsState.kt`（`init` 自建 scope）
- 重试边界：`core/sync/src/main/java/cc/pscly/onememos/worker/FlowTodoSyncWorker.kt`、`core/sync/src/main/java/cc/pscly/onememos/worker/MemosSyncWorker.kt`
- 缓存 trim：`core/data/src/main/java/cc/pscly/onememos/data/cache/CacheRepositoryImpl.kt`（`walkTopDown().toList().sortedBy`）

---

## Work Objectives

### Core Objective
- 降低高频路径的卡顿/ANR 风险；避免同步上传与缓存裁剪造成 OOM/抖动；让后台协程入口“异常不崩、可观测、可控重试”。

### Scope
- IN：截图记录写盘线程治理；同步附件上传的内存/大小/超限策略；Receiver 与 Singleton 的协程异常兜底与超时；Worker 重试分类与上限；必要的开发者模式设置项与 UI；单测补齐；benchmark 构建与交付脚本串联。
- OUT：重写同步架构、变更 memos API 协议语义、引入大型新框架、全局性 UI 重新设计。

### Must Have
- 不引入“用户手动验收”步骤；所有验收用命令/测试脚本完成。
- 超限附件不会卡住整轮同步；错误可观测（至少 lastError 可见）。
- 50MB 默认值在客户端侧不会触发“一次性读入 + base64 string 膨胀”导致的 OOM（通过流式写入避免）。

### Must NOT Have (Guardrails)
- 不改变 `CreateAttachmentRequestDto` 对 memos v0.25.x 的协议语义（content 仍是 base64 字符串字段）。
- 不记录敏感信息（token、附件内容）到日志；日志只包含必要的 size/原因/稳定标识。
- 不把不可恢复错误当成 `Result.retry()`（避免重试风暴/耗电）。

---

## Verification Strategy

### 测试与验证选择（已确认）
- **自动化测试**：YES（新增 Robolectric/协程单测，属于 tests-after）
- **门禁脚本**（Linux）：`./scripts/verify.sh`

### 通用验收命令（执行侧必须跑）
```bash
bash ./scripts/verify.sh
bash ./scripts/build-benchmark-apk.sh

# 按仓库约定：每次打包后都尝试连接并安装（成功则继续推送）
adb connect 192.168.12.101:5555 || true
ADB_SERIAL=192.168.12.101:5555 bash ./scripts/deliver-benchmark.sh || true
```

### 证据输出
- 单测/门禁输出：保留终端输出（必要时重定向到 `.sisyphus/evidence/*.log`）
- benchmark APK：脚本 `scripts/copy-benchmark-apk.sh` 输出的时间戳 APK 路径

---

## Execution Strategy

Wave 1（可并行开始）：
- Task 1（截图写盘线程治理）
- Task 2（Receiver/Singleton 协程兜底）

Wave 2（依赖部分 Task 3/4）：
- Task 3（附件大小上限 + 开发者设置 UI）
- Task 4（MemosSyncWorker：流式 base64 上传 + 超限跳过 + 错误分类）

Wave 3（收尾整合）：
- Task 5（重试治理统一 & 缓存 trim 优化可选）
- Task 6（全量门禁 + benchmark 交付 + 更新 .ai_session.md + 提交）

---

## TODOs

> 说明：每个任务都必须包含自动化验收（命令/测试），不允许“人工点一点看看”。

### 1) 截图记录：落盘/编码强制 IO 线程 + 单测覆盖

**What to do**
- 改造 `ScreenshotQuickCaptureActivity.captureOneFrameToCache()`：确保 `saveBitmapToCache`（目录创建、文件写入、PNG 编码）在 `Dispatchers.IO`。
- 避免在主线程做 `mkdirs()` / `FileOutputStream` / `bitmap.compress`。
- 保持行为不变：仍产出 png，仍走 `FileProvider` 返回 uri。

**References**
- `app/src/main/java/cc/pscly/onememos/screenshot/ScreenshotQuickCaptureActivity.kt` - 当前同步写盘位置与调用链。
- `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/SettingsScreen.kt` - 说明文案里提到“首次会弹系统确认框”，验收不能依赖人工点击。

**Acceptance Criteria**
- `./gradlew :app:testDebugUnitTest` 通过（或 `bash ./scripts/verify.sh` 全绿）。
- 新增/更新单测：验证 `saveBitmapToCache` 输出文件存在且大小 > 0（Robolectric 可用）。
- 代码层面可检索到 `withContext(Dispatchers.IO)` 覆盖写盘/编码路径（执行侧用 `rg` 作为辅助证据）。

**Agent-Executed QA Scenarios**
```
Scenario: JVM/Robolectric 回归（截图写盘不回归）
  Tool: Bash
  Steps:
    1. bash ./scripts/verify.sh
  Expected Result: verify.sh: OK
  Evidence: .sisyphus/evidence/task-1-verify.log（可选）
```

---

### 2) 稳定性：Receiver + Singleton 协程入口补齐兜底（最小侵入）

**What to do**
- `TodoReminderAlarmReceiver`：在 `goAsync()` 协程体内增加 try/catch + 日志；增加 `withTimeout`（避免 receiver 长时间占用）；任何情况下都 `pendingResult.finish()`。
- `AppSettingsState`：为内部 scope 增加 `CoroutineExceptionHandler` 或 `.catch {}`，确保异常不导致进程崩溃/停止更新；尽量不做大重构（按“最小侵入”）。

**References**
- `core/sync/src/main/java/cc/pscly/onememos/worker/TodoReminderAlarmReceiver.kt` - 当前 goAsync + launch 结构。
- `core/network/src/main/java/cc/pscly/onememos/core/network/AppSettingsState.kt` - init 自建 scope。

**Acceptance Criteria**
- `./gradlew :app:testDebugUnitTest` 通过（或 `bash ./scripts/verify.sh` 全绿）。
- 新增单测：模拟异常路径不崩（至少验证方法调用不抛到测试线程）。

**Agent-Executed QA Scenarios**
```
Scenario: JVM 单测覆盖协程兜底
  Tool: Bash
  Steps:
    1. ./gradlew :app:testDebugUnitTest --stacktrace
  Expected Result: exit code 0
  Evidence: .sisyphus/evidence/task-2-tests.log（可选）
```

---

### 3) 新增“附件上传大小上限”设置（默认 50MB，dev2 可调到 1GB）

**What to do**
- 增加 `AppSettings` 字段：例如 `devMaxAttachmentUploadMb`（命名由执行者按现有风格确定）。
- SettingsRepository/Impl 增加读写；DataStore key 落在 `core/data/src/main/java/cc/pscly/onememos/data/settings/SettingsRepositoryImpl.kt`。
- Settings UI：放入 `开发者模式2（已解锁）` 区域（`SettingsScreen.kt`），提供输入框/步进器：
  - 默认显示 50
  - 校验范围：1..1024（1GB）
  - 文案提醒：大于几十 MB 会显著增加耗时/流量；base64 会膨胀（仅提示，不阻止）。
- 诊断导出（可选但推荐）：把该字段写入诊断 JSON（`SettingsScreen.kt` 的 `exportDiagnosticsFile` 逻辑）。

**References**
- `core/model/src/main/java/cc/pscly/onememos/domain/model/AppSettings.kt` - 设置模型。
- `core/domain/src/main/java/cc/pscly/onememos/domain/repository/SettingsRepository.kt` - 设置读写接口。
- `core/data/src/main/java/cc/pscly/onememos/data/settings/SettingsRepositoryImpl.kt` - DataStore keys + 映射。
- `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/SettingsViewModel.kt` - UI state 透传。
- `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/SettingsScreen.kt` - dev2 UI 区域。

**Acceptance Criteria**
- `./gradlew :app:testDebugUnitTest` 通过。
- 新增单测：SettingsRepositoryImpl 读写该字段可用（参照现有 `SettingsRepositoryImplFullSync*Test` 风格）。

**Agent-Executed QA Scenarios**
```
Scenario: SettingsRepository 单测回归
  Tool: Bash
  Steps:
    1. ./gradlew :app:testDebugUnitTest --stacktrace
  Expected Result: exit code 0
```

---

### 4) 同步上传：流式 base64 JSON RequestBody + 超限跳过 + 错误分类

**What to do**
- 解决目标：在 memos v0.25.x 仍要求 JSON base64 的前提下，避免“大附件 = readBytes + base64 String”带来的 OOM。
- Retrofit 接口扩展（保持协议不变）：在 `MemosApi` 增加一个 `@Body RequestBody` 版本的 `createAttachment`（返回仍为 `AttachmentDto`）。
- 自定义 `RequestBody` 的关键点：
  - base64 必须无换行（NO_WRAP / basic encoder）
  - 不能 `close()` 掉底层 Okio sink（必要时用“忽略 close 的 OutputStream”包装）
  - 若能拿到附件 size：实现 `contentLength()`，避免部分服务端/代理不接受 chunked 上传
- 在 `MemosSyncWorker`：
  - 读取 `devMaxAttachmentUploadMb`（默认 50MB）。
  - 获取附件 size（优先 `OpenableColumns.SIZE`；未知则走“边读边计数，超过阈值立刻中止并判定超限”）。
  - 若超限：跳过该附件，继续同步；聚合错误写入 `settingsRepository.setLastSyncError(...)`（不记录敏感信息）。
  - 若未超限且非图片：走流式 `RequestBody`；图片仍走现有压缩路径（已降采样，体积通常小）。
- 引入“错误分类表”：明确哪些错误 retry，哪些直接 success（不 retry）。
  - `SecurityException`/`FileNotFoundException`/超限：不可恢复 -> success + lastError
  - `HttpException`：401/403 不 retry；其它 4xx 默认不 retry（可保留 408/429 例外）；5xx/超时/IO 可 retry
  - `CancellationException` 必须透传

**References**
- `core/network/src/main/java/cc/pscly/onememos/core/network/MemosApi.kt` - Retrofit API。
- `core/network/src/main/java/cc/pscly/onememos/core/network/dto/CreateAttachmentRequestDto.kt` - 协议约束（content 为 base64 字符串）。
- `core/sync/src/main/java/cc/pscly/onememos/worker/MemosSyncWorker.kt` - 上传逻辑与现有 base64 处理。

**Acceptance Criteria**
- 新增单测覆盖流式 RequestBody：
  - 输出 JSON 可被解析（Gson/JSONObject）
  - `content` 字段 base64 与小样本非流式结果一致
  - base64 无换行（NO_WRAP）
- 新增单测覆盖“超限附件跳过”：
  - 构造一个超过阈值的 InputStream（或 fake size）
  - 断言：不会抛出导致 worker 直接失败；会记录 lastError（可用 fake SettingsRepository 验证调用）
- `bash ./scripts/verify.sh` 全绿。

**Agent-Executed QA Scenarios**
```
Scenario: 门禁 + 附件上传相关单测
  Tool: Bash
  Steps:
    1. bash ./scripts/verify.sh
  Expected Result: verify.sh: OK
  Evidence: .sisyphus/evidence/task-4-verify.log（可选）
```

---

### 5) Worker 重试治理（FlowTodoSyncWorker + MemosSyncWorker）

**What to do**
- 为 `FlowTodoSyncWorker` 与 `MemosSyncWorker` 补齐一致的“可重试/不可重试”边界：
  - 对不可恢复错误不再 `Result.retry()`（避免电量/流量消耗）
  - 对可恢复错误保留 retry，并建议结合 `runAttemptCount` 做上限
- 重点确保：
  - 401/403 -> success（等待用户重新登录）
  - 其它 4xx -> 默认不 retry（除非明确例外）
  - 5xx/网络 IO -> retry

**References**
- `core/sync/src/main/java/cc/pscly/onememos/worker/FlowTodoSyncWorker.kt`
- `core/sync/src/main/java/cc/pscly/onememos/worker/MemosSyncWorker.kt`

**Acceptance Criteria**
- 新增单测：对关键 HTTP code/异常类型的分类断言（建议把分类逻辑抽成纯函数便于测试）。
- `bash ./scripts/verify.sh` 全绿。

---

### 6) 缓存 trim 抖动优化（可选，但建议做）

**What to do**
- 降低 `trimAttachmentCacheIfNeeded` 的“全量扫描 + toList + sort”触发频率/内存占用。
- 最小侵入建议：
  - 仅当预计超限时才进入排序删除（例如基于新增文件长度的粗略估计）；
  - 或将 trim 节流（比如每 N 次下载才触发一次实际扫描）；
  - 保持行为语义：仍按最旧文件优先删除。

**References**
- `core/data/src/main/java/cc/pscly/onememos/data/cache/CacheRepositoryImpl.kt`
- `core/sync/src/main/java/cc/pscly/onememos/worker/AttachmentPrefetchWorker.kt`（已有“每 8 张图检查一次大小”的节流思路，可对齐）

**Acceptance Criteria**
- `bash ./scripts/verify.sh` 全绿。
-（若增加测试）可增加一个纯 JVM 测试：给定伪文件元数据列表，验证删除顺序与超限收敛。

---

### 7) 交付：门禁、benchmark APK、ADB 尝试安装、提交与记录

**What to do**
- 跑门禁：`bash ./scripts/verify.sh`（必要时 `--all`）。
- 构建 benchmark APK 并生成时间戳副本：`bash ./scripts/build-benchmark-apk.sh`（脚本最后会打印 `app/build/outputs/apk/benchmark/YYYY-MM-DDTHH-MM-SS.apk`）。
- 按仓库约定：打包后尝试连接并安装：
  - `adb connect 192.168.12.101:5555 || true`
  - `ADB_SERIAL=192.168.12.101:5555 bash ./scripts/deliver-benchmark.sh || true`
- 更新项目根 `.ai_session.md`：记录本次优化目标、关键决策（50MB/1GB、超限跳过、最小侵入兜底）、关键文件变更点、验证命令、APK 路径。
- Git：按仓库要求提交（建议按功能拆 2-3 个原子提交）。

**References**
- `scripts/verify.sh`
- `scripts/build-benchmark-apk.sh`
- `scripts/deliver-benchmark.sh`
- `scripts/install-benchmark.sh`
- `.ai_session.md`

**Acceptance Criteria**
- `verify.sh: OK`
- `build-benchmark-apk.sh` 输出时间戳 APK 路径存在
- 已执行 ADB connect/install 尝试（成功则安装并推送到 Download；失败需在输出中可诊断原因）
- `.ai_session.md` 已更新
- Git 工作区干净且提交完成

---

## Commit Strategy（建议）

1) `fix: screenshot cache write off main thread`
- 包含：截图写盘线程治理 + 对应单测

2) `feat: add dev max attachment upload size and skip oversize attachments`
- 包含：新设置字段 + Settings UI + MemosSyncWorker 超限跳过

3) `perf: stream base64 attachment uploads and harden worker retries`
- 包含：流式 RequestBody + 错误分类/重试治理 + 单测

---

## Success Criteria

- `bash ./scripts/verify.sh` 通过
- 单测新增覆盖关键风险点（截图写盘、流式 base64、超限跳过、重试分类、Receiver/Singleton 兜底至少不崩）
- benchmark APK 构建成功并生成时间戳副本（路径由脚本输出）
- 尝试 `adb connect 192.168.12.101:5555`；若连接成功则完成安装与推送（脚本 `deliver-benchmark.sh`）
