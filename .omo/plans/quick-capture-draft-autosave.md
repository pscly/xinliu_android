# Quick Capture 草稿自动保存（悬浮窗 + 普通极速记录页）

## TL;DR

> **目标**：为“快捷开关(QS Tile) 悬浮窗极速记录”以及“普通 QuickCaptureActivity”增加**本地草稿**自动保存/手动恢复/手动清空能力，避免误触退出或进程被杀导致输入丢失。
>
> **关键约束**：草稿必须**严格不触发同步/上传**；使用 1s 防抖保存正文、附件变更即时保存；草稿是**全局单槽位**；附件需要复制到 `filesDir` 并在清空时一并删除。

**交付物**：
- 草稿存储层（JSON + 原子写 + 附件落盘目录），完全独立于同步系统
- 悬浮窗 `QuickCaptureOverlayService` 接入：自动保存、底部提示条“恢复草稿”、更多菜单“清空草稿”、覆盖确认
- 普通 `QuickCaptureActivity`/`QuickCaptureViewModel` 接入：同样的草稿体验
- Robolectric TDD 测试：防抖、附件复制、清空、覆盖确认、严格不触发同步
- 最终构建 benchmark APK（带日期时间命名）并尝试 `adb connect 192.168.12.101:5555` 安装

**预计工作量**：中等（涉及 Service + ViewModel + 存储 + 附件 IO + 测试 + 构建/安装）

---

## Context

### 原始需求
- “每次快速记录都能每秒自动保存本地草稿，避免点错退出丢笔记（不上传，只是草稿保存）”。

### 已确认的产品决策
- 入口：QS 快捷开关触发悬浮窗记录（并同时覆盖普通 `QuickCaptureActivity`）。
- 草稿槽位：全局单草稿（只恢复这一份）。
- 恢复方式：手动恢复；检测到草稿时在底部提示条提供“恢复草稿”。
- 清空入口：更多菜单（⋮）中提供“清空草稿”。
- 覆盖规则：若存在草稿但用户不点恢复直接输入新内容，第一次覆盖前弹确认。
- 保存策略：正文 1 秒防抖；附件变更即时保存；关闭/后台强制 flush。
- 保留策略：未正式保存前，草稿长期保留直到手动清空；正式保存成功后自动清空草稿。
- 附件：草稿要完整恢复附件；附件必须复制到 `filesDir`；清空草稿时连同附件副本一起删除。
- 不上传：严格不触发同步/上传（草稿保存/恢复不得触发 sync scheduler）。
- 加密：不需要。
- overlay 编辑已有 memo：不纳入草稿机制（仅新建极速记录）。
- 测试：TDD，优先 Robolectric 单测。

### 代码入口与相关模块（已定位）
- QS Tile：`app/src/main/java/cc/pscly/onememos/qs/QuickCaptureTileService.kt`
- 悬浮窗入口 Activity：`app/src/main/java/cc/pscly/onememos/overlay/QuickCaptureOverlayEntryActivity.kt`
- 悬浮窗核心 Service：`app/src/main/java/cc/pscly/onememos/overlay/QuickCaptureOverlayService.kt`
- 普通极速记录：
  - Activity：`app/src/main/java/cc/pscly/onememos/ui/feature/quickcapture/QuickCaptureActivity.kt`
  - UI：`feature/quickcapture/src/main/java/cc/pscly/onememos/ui/feature/quickcapture/QuickCaptureScreen.kt`
  - VM：`feature/quickcapture/src/main/java/cc/pscly/onememos/ui/feature/quickcapture/QuickCaptureViewModel.kt`

---

## 目标与范围

### Core Objective
在用户输入过程中**持续保存本地草稿**，并在下次打开时提供**可控的手动恢复**能力；任何情况下都不把草稿卷入同步/上传链路。

### IN（必须做）
- Overlay Service 与普通 QuickCaptureActivity 共享同一套草稿机制与 UX
- 草稿元数据 + 文本 + 附件列表（含顺序）都能恢复
- 文件 IO：附件复制到 `filesDir`，并实现清理与覆盖时的旧附件清理
- 单测覆盖：防抖、附件即时保存、flush、清理、覆盖确认、无同步触发

### OUT（明确不做）
- 云草稿/多草稿列表/历史版本
- 自动恢复（仍坚持手动恢复）
- 草稿加密
- 影响 overlay “编辑已有 memo”流程

---

## 关键设计（计划内默认选型）

### 草稿存储介质：JSON 文件 + 附件目录（推荐）
理由：
- “严格不触发同步”要求很强，复用 `MemoRepository.updateMemoDraft(...)` 会触发 `syncScheduler.requestSync()`（已确认不允许）。
- Room 新表虽可行，但需要 schema/迁移与更多集成成本；本需求更像“临时状态”，用 JSON 更轻量。

**建议落盘位置**：
- 草稿 JSON：`context.noBackupFilesDir/quick_capture_draft/draft.json`（避免系统备份/迁移，降低“草稿外泄”风险）
- 草稿附件目录：`context.filesDir/quick_capture_draft_attachments/`（长期保留，符合产品决策）

**写入原则（强制）**：
- 原子写：写到临时文件 `draft.json.tmp`，`fsync`（如实现方便），再 rename 覆盖，避免进程被杀导致 JSON 半写损坏。
- 单线程/互斥：使用 `Mutex` 或单线程队列，避免 Overlay 与 Activity 并发写造成交错。
- 版本字段：`version` + `updatedAt`，未来 schema 变更可做容错解析。
- 附件去孤儿：覆盖草稿或清空草稿时，必须删除不再被当前草稿引用的旧附件副本文件，避免磁盘垃圾。

### “正式保存成功”的定义
- 以 `memoRepository.createLocalMemo(...)` / `updateMemoContent(...)` 等返回成功（无异常）作为“正式保存成功”。
- 若正式保存抛异常/返回失败：不得清空草稿，草稿继续保留。

### flush 覆盖的生命周期点（至少）
- Overlay：
  - 关闭按钮路径（当前 `onClose -> removeOverlay(); stopSelf()`）
  - `onDestroy()`
  - `onTaskRemoved(...)`（若存在/可加）
  - `onTrimMemory(...)`（收到后台/内存压力时尽力 flush）
- Activity：`onStop`/`onPause`（至少一个），以及 ViewModel `onCleared()` 作为兜底（如果适配合理）

---

## 验证策略（强制，零人工）

### Test Decision
- **基础设施**：已有 Robolectric/JUnit 单测样例
- **策略**：TDD

### 允许的验证方式
- 运行单测：`./gradlew :app:testDebugUnitTest`（以及相关 module 的 `testDebugUnitTest`）
- 构建 benchmark：用 Gradle 任务构建 benchmark APK（需先通过 `./gradlew :app:tasks --all | rg -n "assemble.*Benchmark"` 找到准确 task）
- ADB 自动验证：尝试连接并安装（不要求人工点击 QS tile）

---

## 执行策略（并行波次）

Wave 1（存储层与核心逻辑，可并行）：Task 1、Task 2

Wave 2（接入 UI/Service/VM）：Task 3、Task 4

Wave 3（收尾：构建、安装、基准验证）：Task 5

---

## TODOs

> 每个任务都要求：实现 + 测试（TDD）+ 可执行验收命令。

- [x] 1. 新增 QuickCapture 草稿存储层（JSON + 原子写 + 附件文件管理）

  **要做什么**：
  - 新增一个独立的草稿存储组件（例如 `QuickCaptureDraftStore` / `QuickCaptureDraftRepository`），提供：
    - `loadDraft(): Draft?`
    - `saveDraft(draft)`（内部做互斥 + 原子写）
    - `clearDraft()`（删除 JSON + 删除附件副本目录）
    - `copyInAttachment(uri): DraftAttachment`（把 `content://` 复制到 `filesDir/quick_capture_draft_attachments/` 并返回引用）
  - 草稿数据结构包含：正文文本、附件列表（含顺序）、更新时间戳、schema 版本。
  - 严禁触发任何 sync：不得依赖 `MemoRepository.updateMemoDraft(...)` 或 `SyncScheduler`。
  - 所有文件 IO 必须 off main thread（Service/VM 侧用 IO dispatcher），避免悬浮窗输入时卡顿/ANR。
  - 每次保存草稿时做一次“孤儿附件清理”（删除不再被草稿引用的旧附件副本）。

  **推荐 Agent Profile**：
  - Category：`unspecified-high`
  - Skills：无

  **并行**：可与 Task 2 并行（但需先确定接口）

  **参考**：
  - `core/data/src/main/java/cc/pscly/onememos/data/cache/CacheRepositoryImpl.kt`：文件落盘 + 回写 DB 的模式（这里可复用“复制到私有目录”的 IO 写法）
  - `app/src/main/java/cc/pscly/onememos/share/ShareToOneMemosActivity.kt`：把外部 Uri 复制到私有目录的范式
  - `feature/sharecard/src/main/java/cc/pscly/onememos/ui/feature/sharecard/ShareCardFileStore.kt`：cacheDir 写文件与清理旧文件范式

  **TDD 验收（必须）**：
  - 新增 Robolectric/JUnit 测试，覆盖：
    - 原子写：中途异常不应留下损坏 JSON（至少保证下次 loadDraft 不崩）
    - 互斥：并发 save 不应产生部分写或丢字段
    - clearDraft：JSON 与附件目录都被删除
    - 覆盖草稿：保存新草稿后，旧草稿附件副本不再残留（孤儿附件被清理）
    - copyInAttachment：复制后的文件存在且可读；clearDraft 会删除
  - 命令：`./gradlew :app:testDebugUnitTest` → PASS


- [x] 2. 新增“1 秒防抖 + flush”保存调度器（纯逻辑层，可复用在 Service/VM）

  **要做什么**：
  - 抽一个小型的 `DraftAutoSaver`（或类似组件），对外暴露：
    - `onTextChanged(text)`：1s debounce 后调用 `saveDraft`
    - `onAttachmentsChanged(list)`：立即 `saveDraft`
    - `flushNow()`：立即保存最后状态（用于 onStop/onDestroy/onClose）
  - 使用 `kotlinx-coroutines-test` 写时间可控的测试（虚拟时间推进）。
  - 处理 IME composing：如果现有状态使用 `TextFieldValue`，保存时建议只落盘 `text`（不保存 selection 已确认不需要）。

  **推荐 Agent Profile**：
  - Category：`unspecified-high`
  - Skills：无

  **并行**：可与 Task 1 并行（但需要 store 的接口）

  **参考**：
  - 既有 debounce 范式：`feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeViewModel.kt`（Flow.debounce）

  **TDD 验收（必须）**：
  - 连续多次 `onTextChanged`（间隔 <1s）只触发 1 次保存
  - 推进虚拟时间 >=1s 后落盘
  - `flushNow()` 会立即保存且不等待
  - 命令：`./gradlew :app:testDebugUnitTest` → PASS


- [x] 3. 接入悬浮窗：QuickCaptureOverlayService 增加草稿提示条、手动恢复、清空与覆盖确认

  **要做什么**：
  - 在 `app/src/main/java/cc/pscly/onememos/overlay/QuickCaptureOverlayService.kt`：
    - 启动时检查草稿是否存在，更新 UI state 以驱动“底部提示条”显示
    - `updateContent(value)` 调用 `DraftAutoSaver.onTextChanged(value.text)`
    - 附件 add/remove 路径调用 `onAttachmentsChanged`，并在加入草稿前完成“复制到 filesDir”
    - 提供 action：
      - “恢复草稿”：将草稿内容与附件回填到 `_uiState`
      - “清空草稿”：清空草稿 + 删除附件副本（入口在更多菜单）
      - “覆盖确认”：当草稿存在且用户未恢复就开始输入时，第一次覆盖前弹确认（确认后才允许写入覆盖旧草稿）
    - 退出/后台 flush：在 `onClose`、`onDestroy`（必要时 `onTaskRemoved/onTrimMemory`）调用 `flushNow()`
  - 明确 guardrail：若 overlay 处于编辑已有 memo（`editingUuid != null`），不启用草稿逻辑。

  **推荐 Agent Profile**：
  - Category：`unspecified-high`
  - Skills：无

  **参考**：
  - Overlay 状态与输入入口：`app/src/main/java/cc/pscly/onememos/overlay/QuickCaptureOverlayService.kt`（`_uiState` / `updateContent` / 附件添加 action / `save()`）
  - Overlay 入口链：`app/src/main/java/cc/pscly/onememos/qs/QuickCaptureTileService.kt`、`app/src/main/java/cc/pscly/onememos/overlay/QuickCaptureOverlayEntryActivity.kt`

  **TDD 验收（必须）**：
  - Robolectric 测试覆盖：
    - Service 启动后检测到草稿 → state 标记“有草稿”
    - 调用“恢复草稿”回填文本与附件
    - “清空草稿”后草稿不存在且附件副本被删
    - 覆盖草稿后旧附件副本不会残留
    - 编辑已有 memo 模式不产生草稿写入
  - 命令：`./gradlew :app:testDebugUnitTest` → PASS


- [x] 4. 接入普通极速记录页：QuickCaptureViewModel/Screen 同步草稿体验

  **要做什么**：
  - 在 `feature/quickcapture/.../QuickCaptureViewModel.kt`：
    - 初始化时检查草稿存在性，并暴露给 UI（用于底部提示条）
    - 文本变更接入 `DraftAutoSaver.onTextChanged(text)`
    - 若有附件功能（或未来扩展），同样走附件即时保存
    - 提供 `restoreDraft()` / `clearDraft()` / `confirmOverwriteDraft()` 等 UI action
  - 在 `feature/quickcapture/.../QuickCaptureScreen.kt`：
    - 底部提示条：草稿存在时展示“有草稿，点此恢复”，点击触发恢复
    - 更多菜单：提供“清空草稿”
    - 覆盖确认：草稿存在且未恢复时，第一次输入触发确认
  - 正式保存成功后：清空草稿（失败则保留）。

  **推荐 Agent Profile**：
  - Category：`unspecified-high`
  - Skills：无

  **参考**：
  - 现有输入状态：`feature/quickcapture/src/main/java/cc/pscly/onememos/ui/feature/quickcapture/QuickCaptureViewModel.kt`（`updateContent`/`save`）
  - UI 入口：`feature/quickcapture/src/main/java/cc/pscly/onememos/ui/feature/quickcapture/QuickCaptureScreen.kt`

  **TDD 验收（必须）**：
  - ViewModel 单测：
    - 草稿存在时 banner 状态为 true
    - restoreDraft 后文本变更
    - clearDraft 后 banner 关闭
    - 覆盖确认逻辑正确（未确认前不覆盖草稿）
    - 覆盖草稿后旧附件副本不会残留（如普通页支持附件则必须测；否则至少测 store 层清理）
  - 命令：`./gradlew :app:testDebugUnitTest`（或对应 module 的 `testDebugUnitTest`）→ PASS


- [x] 5. 收尾：构建 benchmark APK、ADB 安装、提交与记录

  **要做什么**：
  - 更新 `.ai_session.md` 记录本次架构与关键决策（由执行者在实现阶段完成，确保可追溯）。
  - Git 提交（按仓库约定，每次开发完毕都需要 commit）：
    - 建议拆 2-3 个原子提交：存储层/调度器；overlay 接入；activity 接入 + 测试
  - 构建 benchmark APK（不要 debug）：
    - 先确定任务：`./gradlew :app:tasks --all | rg -n "assemble.*Benchmark"`
    - 执行对应 assemble 任务生成 APK
    - 将 APK 复制/重命名为带时间戳：`YYYY-MM-DDTHH-MM-SS.apk`（放到约定目录并在输出里告知路径）
  - ADB：
    - `adb connect 192.168.12.101:5555`
    - 连接成功则 `adb install -r <benchmark-apk-path>`

  **推荐 Agent Profile**：
  - Category：`quick`
  - Skills：`git-master`（仅用于提交时）

  **验收（必须）**：
  - `./gradlew :app:testDebugUnitTest` → PASS
  - benchmark APK 产物存在且命名符合时间戳格式
  - ADB 连接与安装命令有明确输出（成功/失败都要记录原因与日志）

---

## Commit Strategy（建议）

1) `feat(quickcapture): add local draft store and autosave scheduler`
2) `feat(overlay): autosave draft for quick capture floating window`
3) `feat(quickcapture): add draft restore/clear UX + tests`

---

## Success Criteria

### 必须满足
- 草稿保存/恢复逻辑不依赖 memo 同步链路（不触发 `syncScheduler.requestSync()`）
- 1s 防抖与附件即时保存都被单测覆盖并通过
- Overlay 与 Activity 都出现一致的“手动恢复 + 清空草稿 + 覆盖确认”体验
- 清空草稿会删除附件副本，不产生磁盘垃圾

### 可执行验证命令（由执行者运行）
```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:tasks --all | rg -n "assemble.*Benchmark"
```
