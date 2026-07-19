# 计划：锦囊文件夹预览同主页 + 主页长按多选（批量放入锦囊/归档/分享文本）

## TL;DR

> **目标**：
> 1) 锦囊（Collections）进入文件夹后，NOTE_REF（随笔引用）条目展示“主页同款”的内容预览（但不显示标签 chips），不再只有“（无标题）/随笔引用”。
> 2) 主页（Home）长按底部菜单新增“多选”，进入多选模式后支持：批量放入锦囊、批量归档/恢复、把多条随笔“合并为一段文本”分享（包含每条全文）。
>
> **交付物**：
> - Collections 文件夹列表：NOTE_REF 预览卡片（内容预览 + 时间；无 tag chips；缺失引用有降级占位）
> - Home：长按菜单新增“多选”入口；多选态 UI（计数 + 操作区）
> - 批量动作：放入锦囊 / 归档或恢复 / 分享合并文本
> - 自动化测试（TDD）：选择态与分享文本拼接等核心逻辑覆盖
>
> **预计工作量**：Medium
> **并行执行**：YES（3-4 waves）
> **关键路径**：Home 多选状态与动作 → 批量放入锦囊 → Collections NOTE_REF 预览联动数据 → 集成验证 + benchmark APK

---

## Context

### 原始需求（用户描述）
- “锦囊 优化一下，打开那个文件夹的时候可以看到像是主页那样的预览，而不是无标题和随笔引用这种”
- “主页也优化一下，长按还可以是多选，而非只有分享和放入锦囊”

### 访谈结论（已确认）
- 入口：问题发生在“锦囊内文件夹”视图（不是锦囊根列表）。
- Collections 预览丰富度：接近主页卡片，但**不展示标签 chips**。
- Home 长按交互：保留当前长按底部菜单，并在菜单里新增“多选”。
- 多选分享：支持多条，但形式为“合并为一段文本”一次性分享；每条包含**完整内容**。
- 多选批量动作（至少）：放入锦囊；归档/取消归档。
- 测试策略：需要自动化测试，采用 TDD。

### 代码定位（可作为实现参考）
- Collections 列表与选择态：`feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsScreen.kt`
  - `CollectionItemCard()` 当前使用 `item.name.ifBlank { "（无标题）" }` 与 meta（"随笔引用" 等）。
  - 已有 selectionMode（selectedIds + 长按选中 + 点击切换）。
- Collections 数据模型：`core/model/src/main/java/cc/pscly/onememos/domain/model/CollectionItem.kt`
  - NOTE_REF 仅保存 refId/refLocalUuid，不含内容摘要/附件等预览字段。
- Home 长按底部菜单：`feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeScreen.kt`
  - `moreActionsTarget` → `ModalBottomSheet` 目前只有“放入锦囊”“墨迹卡片”。
- 主页卡片渲染：`feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/MemoItem.kt`
  - 内容预览（MarkdownPreview/纯文本）+ 标签 chips + 时间。
- 归档/恢复：`core/domain/src/main/java/cc/pscly/onememos/domain/repository/MemoRepository.kt`（archive/unarchive API）
  - 参考调用点：`feature/editor/src/main/java/cc/pscly/onememos/ui/feature/editor/EditorViewModel.kt`
- 锦囊“放入”单条实现：`feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/AddToCollectionsDialog.kt`
  - 依赖：`core/domain/src/main/java/cc/pscly/onememos/domain/repository/CollectionsRepository.kt` + `core/data/.../CollectionsRepositoryImpl.kt`
- Benchmark APK 构建：`scripts/build-benchmark-apk.sh`（会生成时间戳 APK）
- 门禁脚本：`scripts/verify.sh`（assembleDebug + 单测 + lintDebug + assembleBenchmark）

### Metis 复核要点（已吸收进计划）
- Paging + selection：必须用稳定 id Set；refresh/append 后要 reconcile 或直接清空 selection。
- 批量操作后列表可能立刻变化（归档后从随笔消失）：建议操作成功后清空 selection。
- Collections NOTE_REF 预览不要做“每 item collect 一个 Flow”的 N+1 订阅风暴：优先在 VM 层批量/缓存式加载。
- 分享文本可能很大（全文）：先按需求实现，但在计划里写明风险与后续兜底方向。

---

## Work Objectives

### Core Objective
把“锦囊文件夹列表的 NOTE_REF 展示”升级为可读的内容预览，并把“主页长按”从单条操作扩展为可进入多选态的批量工作流。

### Concrete Deliverables
- Collections：NOTE_REF 条目渲染为“内容预览卡片 + 时间”，不显示 tag chips；引用缺失/未同步时有明确占位。
- Home：长按底部菜单新增“多选”；多选模式支持：批量放入锦囊、批量归档/恢复、分享合并文本（全文）。
- 测试：至少覆盖选择态切换、分享文本拼接（TDD）。

### Definition of Done
- `./scripts/verify.sh` 通过（或等价 Gradle 组合：单测 + lintDebug + assembleBenchmark）。
- `./scripts/build-benchmark-apk.sh` 产出时间戳 benchmark APK（路径形如 `app/build/outputs/apk/benchmark/2026-02-20Txx-xx-xx.apk`）。

### Must Have
- Collections 文件夹内 NOTE_REF 视觉上不再是“（无标题）/随笔引用”主导；能看到类似主页的正文预览。
- Home 长按菜单包含“多选”；多选态可批量放入锦囊 + 批量归档/恢复 + 合并文本分享（全文）。
- 批量操作执行后 selection 清空（避免误操作）。
- 代码注释与文案保持中文。

### Must NOT Have（Guardrails）
- 不做全局 UI 大重构（不把 MemoItem 全面抽离成设计系统大组件，除非改动极小且收益明确）。
- 不扩展到 Collections 根列表/其它列表页（除非实现过程中发现同一代码路径天然覆盖，且不会引入额外复杂度）。
- 不实现“多张墨迹卡片批量分享”（本次多选分享仅合并文本）。

---

## Verification Strategy（MANDATORY）

> **零人工介入**：验证必须可由执行代理通过命令完成。

### Test Decision
- **基础设施**：已存在 JVM 单测（示例：`feature/home/src/test/java/...`）。项目启用 Robolectric（见 `app/build.gradle.kts`）。
- **自动化测试**：YES，TDD。
- **策略**：
  - 纯逻辑（选择态 reducer、分享文本拼接）→ JVM 单测（最快、最稳）。
  - UI 行为（长按菜单新增入口、多选态切换）→ 以 ViewModel 状态/事件为主的单测 + 最小编译门禁；如已有 Compose UI test 基建再补。

### QA Policy（每个任务都要写）
- 每个任务都必须包含 1 个 happy path + 1 个 failure/edge case 的 QA 场景。
- 证据文件统一写入：`.sisyphus/evidence/`（例如：测试输出、关键日志截取、构建产物路径）。

---

## Execution Strategy

### 并行 Waves（建议）

Wave 1（基础能力 + 纯逻辑 TDD，可并行）：
- Home：多选状态机/选择 reducer + 单测
- Home：分享合并文本 builder + 单测
- Collections：NOTE_REF→Memo 解析策略/缓存策略（数据结构 + 单测）
- Collections：NOTE_REF 预览卡片的 UI model（不含 tags）

Wave 2（Home 交互 + 批量动作）：
- HomeScreen：底部菜单新增“多选”入口，进入/退出多选态
- Home：批量放入锦囊（复用 folder 选择 UI，一次选目标，批量 addMemoRef）
- Home：批量归档/恢复（按 ACTIVE/ARCHIVED mode 给出动作）
- Home：多选分享（ACTION_SEND 文本；包含全文；顺序/分隔符固定）

Wave 3（Collections 预览联动 + UI 完成）：
- CollectionsViewModel：批量加载/缓存引用 memo（避免 N+1 Flow）
- CollectionsScreen：NOTE_REF 渲染为“主页同款内容预览（无 tags）”，缺失引用有占位

Wave 4（总体验证 + benchmark 产物）：
- 运行 `./scripts/verify.sh`
- 构建并复制时间戳 benchmark APK：`./scripts/build-benchmark-apk.sh`
- 补齐 `.ai_session.md`（由执行代理完成）并提交 git commit

---

## TODOs

- [ ] 1. Home：多选选择状态 reducer（TDD，纯逻辑）

  **What to do**:
  - 在 `:feature:home` 新增一个纯逻辑的 selection state（例如：`selectionMode + selectedIds(Set<String>)`）。
  - 提供最小事件/操作：进入多选并选中 1 条、切换选中、清空选择、退出多选。
  - 写 JVM 单测（TDD）：覆盖 happy path（选择/取消/退出）+ edge（重复点击、空集合退出）。

  **Must NOT do**:
  - 不在这一任务里改 UI（HomeScreen/MemoItem）。

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 单模块纯逻辑 + 单测，改动集中。
  - **Skills**: （无）
  - **Skills Evaluated but Omitted**:
    - `ui-ux-pro-max`: 本任务不涉及 UI。

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: Task 4/5/6/7（Home UI/批量动作需要复用该状态）
  - **Blocked By**: None

  **References**:
  - `feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsScreen.kt`：已有 selectionMode（selectedIds + 长按进入 + 点击切换）的交互语义，可对齐。
  - `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeScreen.kt`：将来要把 reducer 接到“多选模式”UI 上。

  **Acceptance Criteria**:
  - [ ] 新增/修改的单测通过：`./gradlew :feature:home:testDebugUnitTest --stacktrace`

  **QA Scenarios**:
  ```
  Scenario: 选择态 reducer 覆盖基本流程（happy path）
    Tool: Bash (gradle)
    Steps:
      1. ./gradlew :feature:home:testDebugUnitTest --tests "*Selection*" --stacktrace
    Expected Result: 测试通过（0 failures）
    Evidence: .sisyphus/evidence/task-01-home-selection-tests.txt

  Scenario: 选择态 reducer 覆盖边界（edge case）
    Tool: Bash (gradle)
    Steps:
      1. ./gradlew :feature:home:testDebugUnitTest --tests "*Selection*" --stacktrace
    Expected Result: 边界用例（重复 toggle / 退出后清空）断言通过
    Evidence: .sisyphus/evidence/task-01-home-selection-tests-edge.txt
  ```

- [ ] 2. Home：多选“合并文本分享”拼接器（TDD，纯逻辑）

  **What to do**:
  - 新增纯函数：输入 `List<Memo>` 输出 `String shareText`。
  - 规则（写死到函数/测试）：
    - 按“主页列表顺序/时间倒序”输出（选择一个稳定规则并在测试中固定）。
    - 用固定分隔符分隔（例如 `\n\n---\n\n`）。
    - 每条包含：时间（使用 `DateTimeFormatter.formatYmdHm` 或同等格式）+ `memo.content` 全文。
  - 写 JVM 单测（TDD）：覆盖 1 条/多条/空内容/包含 markdown 的内容。

  **Must NOT do**:
  - 不在这一任务里直接发起 Android Intent（保持纯逻辑可测）。

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 单文件纯函数 + 单测，收敛风险。
  - **Skills**: （无）
  - **Skills Evaluated but Omitted**:
    - `ui-ux-pro-max`: 本任务不涉及 UI。

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: Task 7（Home UI 的“分享”动作需要调用该函数）
  - **Blocked By**: None

  **References**:
  - `core/model/src/main/java/cc/pscly/onememos/domain/model/Memo.kt`：可用字段（content/createdAt）。
  - `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/MemoItem.kt`：主页显示时间与内容预览的现有格式参考。
  - `feature/sharecard/src/main/java/cc/pscly/onememos/ui/feature/sharecard/ShareCardScreen.kt`：已有 ACTION_SEND / EXTRA_STREAM 的 Intent 组装范式（本任务不直接用，但后续 UI 任务要对齐）。

  **Acceptance Criteria**:
  - [ ] 单测通过：`./gradlew :feature:home:testDebugUnitTest --stacktrace`

  **QA Scenarios**:
  ```
  Scenario: 合并分享文本生成（happy path）
    Tool: Bash (gradle)
    Steps:
      1. ./gradlew :feature:home:testDebugUnitTest --tests "*Share*" --stacktrace
    Expected Result: 多条 memo 的 shareText 含分隔符 + 含每条全文
    Evidence: .sisyphus/evidence/task-02-home-sharetext-tests.txt

  Scenario: 空/异常输入（edge case）
    Tool: Bash (gradle)
    Steps:
      1. ./gradlew :feature:home:testDebugUnitTest --tests "*Share*" --stacktrace
    Expected Result: 空列表/空内容处理符合预期（不崩溃，输出可分享文本）
    Evidence: .sisyphus/evidence/task-02-home-sharetext-tests-edge.txt
  ```

- [ ] 3. Home：放入锦囊时写入 displayName，并准备“批量放入”API

  **What to do**:
  - 更新 `AddToCollectionsViewModel.addMemoRef()`：给 `CollectionsRepository.addMemoRef(... displayName=...)` 传入一个更可读的名字（建议优先用 `memo.plainPreview`，必要时再 fallback）。
  - 为后续多选准备：在同一个 ViewModel 增加一个“批量放入”方法（一次选择 parentId，对 List<Memo> 循环调用 addMemoRef，并返回成功数量/结果）。
  - 保持现有单条对话框行为不变（仍可新建文件夹并放入）。

  **Must NOT do**:
  - 不在这一任务里改 HomeScreen 的多选 UI。

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: 需要理解现有“放入锦囊”流程与 repo API，且要兼容同步/待同步提示。
  - **Skills**: （无）
  - **Skills Evaluated but Omitted**:
    - `git-master`: 本任务不包含提交。

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: Task 5（批量放入锦囊 UI 会依赖批量 API/显示名策略）
  - **Blocked By**: None

  **References**:
  - `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/AddToCollectionsViewModel.kt`：当前 `displayName = null`，导致 Collections 侧出现“（无标题）”。
  - `core/domain/src/main/java/cc/pscly/onememos/domain/repository/CollectionsRepository.kt`：`addMemoRef(... displayName: String?)`。
  - `core/data/src/main/java/cc/pscly/onememos/data/repository/CollectionsRepositoryImpl.kt`：`CollectionItemEntity.name = displayName?.trim().orEmpty()` 的落库策略。
  - `core/model/src/main/java/cc/pscly/onememos/domain/model/Memo.kt`：可用 `plainPreview/content`。

  **Acceptance Criteria**:
  - [ ] 编译通过：`./gradlew :feature:home:assembleDebug --stacktrace`
  - [ ] 现有单测通过：`./gradlew :feature:home:testDebugUnitTest --stacktrace`

  **QA Scenarios**:
  ```
  Scenario: 单条放入锦囊仍可用（happy path）
    Tool: Bash (gradle)
    Steps:
      1. ./gradlew :feature:home:assembleDebug --stacktrace
    Expected Result: 编译通过；未引入新依赖错误
    Evidence: .sisyphus/evidence/task-03-home-addtocollections-assemble.txt

  Scenario: displayName 不再为空（edge case）
    Tool: Bash (gradle)
    Steps:
      1. ./gradlew :feature:home:testDebugUnitTest --stacktrace
    Expected Result: 若新增了相关单测/断言，则应覆盖 memo.plainPreview 为空时的 fallback
    Evidence: .sisyphus/evidence/task-03-home-addtocollections-tests.txt
  ```

- [ ] 4. Home：长按底部菜单新增“多选”，并落地选择态 UI（进入/退出/切换选中）

  **What to do**:
  - 修改 `HomeScreen` 的长按 `ModalBottomSheet`：在“放入锦囊/墨迹卡片”之外新增一个入口“多选”。
    - 点击后：关闭 BottomSheet → 进入 selectionMode → 默认选中当前长按的 memo。
  - 在 Home 列表中加入 selectionMode：
    - selectionMode=false：点击打开 memo；长按弹 BottomSheet（保持现状）。
    - selectionMode=true：点击切换选中；提供“取消/退出多选”的明确入口。
  - 为 `MemoItem` 增加 selected/selectionMode 的视觉提示（例如描边/勾选角标）；不显示 tag chips 的需求不适用于 Home（Home 维持现状）。
  - selectionMode 的状态管理应复用 Task 1 reducer。
  - 默认策略（写进代码/注释即可）：筛选条件变化（query/tags）时清空 selection，避免选中不可见项。

  **Must NOT do**:
  - 不在这一任务里实现批量动作（放入锦囊/归档/分享）——只把“进入多选 + 选择/取消”打通。

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: Compose UI 交互改动，需要兼顾现有视觉与可用性。
  - **Skills**: （无）
  - **Skills Evaluated but Omitted**:
    - `ui-ux-pro-max`: 主要是交互落地，不做风格重设计。

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: Task 5/6/7（批量动作依赖 selectionMode）
  - **Blocked By**: Task 1

  **References**:
  - `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeScreen.kt`：`moreActionsTarget` + `ModalBottomSheet` 现有结构；列表项调用 `MemoItem(... onLongShare = { moreActionsTarget = memo })`。
  - `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/MemoItem.kt`：当前 `InkCard(onClick=..., onLongClick=...)`，需要接入 selectionMode 的点击语义与选中态视觉。
  - `feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsScreen.kt`：selectionMode 点击切换 selectedIds 的既有交互语义可借鉴。

  **Acceptance Criteria**:
  - [ ] 编译通过：`./gradlew :app:assembleDebug --stacktrace`
  - [ ] 单测通过：`./gradlew :feature:home:testDebugUnitTest --stacktrace`

  **QA Scenarios**:
  ```
  Scenario: 编译门禁（happy path）
    Tool: Bash (gradle)
    Steps:
      1. ./gradlew :app:assembleDebug --stacktrace
    Expected Result: assembleDebug 通过
    Evidence: .sisyphus/evidence/task-04-home-multiselect-assemble.txt

  Scenario: selection reducer 不回归（edge case）
    Tool: Bash (gradle)
    Steps:
      1. ./gradlew :feature:home:testDebugUnitTest --stacktrace
    Expected Result: reducer 单测仍全绿
    Evidence: .sisyphus/evidence/task-04-home-multiselect-tests.txt
  ```

- [ ] 5. Home：多选批量“放入锦囊”（一次选目标文件夹）

  **What to do**:
  - 新增一个面向多选的对话框（或复用现有对话框并最小改造）：
    - 标题清晰表达：将 N 条记录放入锦囊。
    - 目标文件夹选择逻辑复用 `AddToCollectionsViewModel.folders`。
    - 支持“在此新建文件夹并放入”（一次创建 → 批量放入）。
  - 调用 Task 3 的批量 API（或在 dialog 内循环）对所有选中 memo 执行 addMemoRef。
  - 成功策略默认：全部成功 → 清空 selection 并退出多选；部分失败 → toast 提示并保留 selection 以便重试。

  **Must NOT do**:
  - 不在这一任务里改 Collections 文件夹预览。

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: 需要同时处理 UI（选择文件夹/新建）与批量写入 repo 的错误处理。
  - **Skills**: （无）

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: Home 多选“放入锦囊”端到端完成
  - **Blocked By**: Task 3, Task 4

  **References**:
  - `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/AddToCollectionsDialog.kt`：单条放入的 UI/交互与文案；可直接复制最小必要结构。
  - `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/AddToCollectionsViewModel.kt`：folders 与 addMemoRef/createFolder。
  - `core/domain/src/main/java/cc/pscly/onememos/domain/repository/CollectionsRepository.kt`：addMemoRef API。

  **Acceptance Criteria**:
  - [ ] 编译通过：`./gradlew :app:assembleDebug --stacktrace`

  **QA Scenarios**:
  ```
  Scenario: 批量放入锦囊编译可用（happy path）
    Tool: Bash (gradle)
    Steps:
      1. ./gradlew :app:assembleDebug --stacktrace
    Expected Result: assembleDebug 通过
    Evidence: .sisyphus/evidence/task-05-home-batch-add-assemble.txt

  Scenario: 批量放入对“锦囊不可用”保持原提示（edge case）
    Tool: Bash (gradle)
    Steps:
      1. ./gradlew :app:assembleDebug --stacktrace
    Expected Result: 不引入未处理的 nullable/状态分支，保持可编译与可运行
    Evidence: .sisyphus/evidence/task-05-home-batch-add-edge.txt
  ```

- [ ] 6. Home：多选批量归档/恢复（按 ACTIVE/ARCHIVED 模式切换）

  **What to do**:
  - 在 selectionMode 下提供一个批量动作：
    - `HomeScreenMode.ACTIVE`：显示“归档”，对选中 uuid 批量调用 `MemoRepository.archiveMemo(uuid)`。
    - `HomeScreenMode.ARCHIVED`：显示“恢复”，批量调用 `MemoRepository.unarchiveMemo(uuid)`。
  - 交互建议（默认）：弹确认框（包含数量），确认后执行；执行成功后清空 selection 并退出多选。
  - 错误处理：个别 uuid 不存在时忽略并继续，最终 toast 汇总（成功/失败数量）。

  **Must NOT do**:
  - 不做“混合状态选择”的复杂策略（ACTIVE 列表本身不包含 archived；ARCHIVED 列表本身全是 archived）。

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: 涉及批量副作用 + UI 确认框 + 与 Paging 列表刷新联动。
  - **Skills**: （无）

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: Home 多选“归档/恢复”端到端完成
  - **Blocked By**: Task 4

  **References**:
  - `core/domain/src/main/java/cc/pscly/onememos/domain/repository/MemoRepository.kt`：`archiveMemo/unarchiveMemo`。
  - `feature/editor/src/main/java/cc/pscly/onememos/ui/feature/editor/EditorViewModel.kt`：单条归档/恢复的调用范式（viewModelScope + emit event）。
  - `feature/editor/src/main/java/cc/pscly/onememos/ui/feature/editor/EditorScreen.kt`：归档/恢复确认对话框文案参考。
  - `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeScreen.kt`：`HomeScreenMode`（ACTIVE/ARCHIVED）路由来源见 `app/src/main/java/.../OneMemosApp.kt`。

  **Acceptance Criteria**:
  - [ ] 编译通过：`./gradlew :app:assembleDebug --stacktrace`

  **QA Scenarios**:
  ```
  Scenario: ACTIVE 模式批量归档代码路径可编译（happy path）
    Tool: Bash (gradle)
    Steps:
      1. ./gradlew :app:assembleDebug --stacktrace
    Expected Result: 编译通过
    Evidence: .sisyphus/evidence/task-06-home-batch-archive-assemble.txt

  Scenario: ARCHIVED 模式批量恢复代码路径可编译（edge case）
    Tool: Bash (gradle)
    Steps:
      1. ./gradlew :app:assembleDebug --stacktrace
    Expected Result: 编译通过；无未处理分支
    Evidence: .sisyphus/evidence/task-06-home-batch-unarchive-edge.txt
  ```

- [ ] 7. Home：多选“分享合并文本”（ACTION_SEND, EXTRA_TEXT, 全文）

  **What to do**:
  - selectionMode 下提供“分享”动作：调用 Task 2 的 shareText builder 得到合并文本。
  - UI 层发起 `Intent(Intent.ACTION_SEND)`：
    - `type = "text/plain"`
    - `putExtra(Intent.EXTRA_TEXT, shareText)`
    - `startActivity(Intent.createChooser(intent, "分享随笔"))`
  - 空选择时禁用该按钮。
  - 风险提示（写在代码注释即可）：全文可能较大，若遇到 TransactionTooLarge，后续可改为“导出到文件再分享”。

  **Must NOT do**:
  - 不实现 ACTION_SEND_MULTIPLE 图片分享（与需求相悖）。

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: UI 动作 + Android intent 副作用处理。
  - **Skills**: （无）

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: Home 多选分享端到端完成
  - **Blocked By**: Task 2, Task 4

  **References**:
  - `feature/sharecard/src/main/java/cc/pscly/onememos/ui/feature/sharecard/ShareCardScreen.kt`：Intent ACTION_SEND / createChooser 的现有写法（图片分享），可复用错误处理模式。
  - `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/SettingsScreen.kt`：文件分享（ACTION_SEND + EXTRA_STREAM）示例。
  - `app/src/main/java/cc/pscly/onememos/share/ShareIntentParser.kt`：项目对分享 intent 的字段约定（EXTRA_TEXT/subject），可帮助对齐格式。

  **Acceptance Criteria**:
  - [ ] 编译通过：`./gradlew :app:assembleDebug --stacktrace`
  - [ ] 单测通过：`./gradlew :feature:home:testDebugUnitTest --stacktrace`

  **QA Scenarios**:
  ```
  Scenario: share intent 代码路径可编译（happy path）
    Tool: Bash (gradle)
    Steps:
      1. ./gradlew :app:assembleDebug --stacktrace
    Expected Result: assembleDebug 通过
    Evidence: .sisyphus/evidence/task-07-home-share-intent-assemble.txt

  Scenario: 分享文本 builder 单测覆盖全文与分隔符（edge case）
    Tool: Bash (gradle)
    Steps:
      1. ./gradlew :feature:home:testDebugUnitTest --tests "*Share*" --stacktrace
    Expected Result: 单测通过
    Evidence: .sisyphus/evidence/task-07-home-share-intent-tests.txt
  ```

- [ ] 8. Collections：为 NOTE_REF 批量加载/缓存被引用 Memo（避免 N+1 Flow）

  **What to do**:
  - 扩展 `CollectionsUiState`：新增 `memoByRefTargetId: Map<String, Memo>`（key 使用 NOTE_REF 打开详情时同一套 target：`refId ?: refLocalUuid`）。
  - 修改 `CollectionsViewModel`：注入 `MemoRepository`，在当前文件夹 children 变化时批量加载缺失 memo：
    - 对每个 NOTE_REF 计算 targetId；`targetId` 为空则跳过。
    - 对未缓存的 targetId 调用 `memoRepository.getMemo(targetId)` 并写入 map。
    - 允许缓存保留一段时间或按当前 folder 精简（选择一个简单策略，写进注释）。
  - 缺失/未同步：`getMemo` 返回 null 时不崩溃，UI 侧用占位文案处理。

  **Must NOT do**:
  - 不要在 `LazyColumn item` 内对每条 NOTE_REF 单独 collect Flow（会导致订阅风暴与滚动卡顿）。

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: ViewModel 状态编排 + 异步加载 + 性能风险需要把控。
  - **Skills**: （无）

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3
  - **Blocks**: Task 9（NOTE_REF 预览 UI 需要 memo map）
  - **Blocked By**: None

  **References**:
  - `feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsViewModel.kt`：当前 uiState 仅包含 items；需要扩展。
  - `feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsScreen.kt`：NOTE_REF 点击打开使用 `target = refId ?: refLocalUuid` 的既有逻辑（预览应复用同一 target）。
  - `core/domain/src/main/java/cc/pscly/onememos/domain/repository/MemoRepository.kt`：`suspend fun getMemo(uuid: String): Memo?`。
  - `core/database/src/main/java/cc/pscly/onememos/core/database/dao/MemoDao.kt`：`getMemo(uuid)` 支持用 uuid 查询（在本工程里 uuid 可能就是 `memos/{id}`）。

  **Acceptance Criteria**:
  - [ ] 编译通过：`./gradlew :feature:collections:assembleDebug --stacktrace`
  - [ ] 单测通过：`./gradlew :feature:collections:testDebugUnitTest --stacktrace`

  **QA Scenarios**:
  ```
  Scenario: Collections 模块可编译（happy path）
    Tool: Bash (gradle)
    Steps:
      1. ./gradlew :feature:collections:assembleDebug --stacktrace
    Expected Result: assembleDebug 通过
    Evidence: .sisyphus/evidence/task-08-collections-memo-cache-assemble.txt

  Scenario: NOTE_REF 缺失引用不崩溃（edge case）
    Tool: Bash (gradle)
    Steps:
      1. ./gradlew :feature:collections:testDebugUnitTest --stacktrace
    Expected Result: 若新增相关单测/假数据，则应覆盖 getMemo=null 时 map 不包含该 key
    Evidence: .sisyphus/evidence/task-08-collections-memo-cache-tests.txt
  ```

- [ ] 9. Collections：NOTE_REF 渲染为“主页同款内容预览（无 tag chips）”

  **What to do**:
  - 在 `CollectionsScreen` 的列表渲染里区分 itemType：
    - `FOLDER`：保持现有 `CollectionItemCard`（或 folder 专用卡片）不变。
    - `NOTE_REF`：改为新的预览卡片（建议新建 composable：`CollectionNoteRefCard`），展示：
      - 正文预览：尽量复用主页的预览逻辑（MarkdownPreview/纯文本），但**不展示 tag chips**。
      - 时间：显示 memo 的时间（与主页一致，建议用 `DateTimeFormatter.formatYmdHm(memo.createdAt)`）。
      - 选中态：在 selectionMode 下给出清晰选中提示（对齐现有 selected 样式）。
      - reorderMode：保持上移/下移控件可用（不能破坏“同层排序”能力）。
  - 降级策略：
    - memo 缺失：显示 `item.name`（若 Task 3 已写入 displayName，会更可读）+ 一行占位“引用内容不可用/待同步”。
    - Flow 引用（若存在）：维持当前占位（现有逻辑已提示不支持预览）。
  - 最终视觉目标：进入锦囊文件夹时，不再主要看到“（无标题）/随笔引用”，而是可读正文预览。

  **Must NOT do**:
  - 不在这一任务里改变锦囊的批量移动/改色/删除/排序业务逻辑（仅保证 UI 不回归）。

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: Compose 卡片布局 + 交互状态（selected/reorder）需要细致调整。
  - **Skills**: （无）

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3
  - **Blocks**: Collections NOTE_REF 预览端到端完成
  - **Blocked By**: Task 8

  **References**:
  - `feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsScreen.kt`：当前 `CollectionItemCard`（含 selected/reorder 逻辑）与 NOTE_REF 点击打开逻辑。
  - `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/MemoItem.kt`：主页正文预览（MarkdownPreview/纯文本）参考实现。
  - `core/designsystem/src/main/java/cc/pscly/onememos/ui/component/MarkdownPaper.kt`：包含 `MarkdownPreview()`，用于列表 markdown 预览。
  - `core/designsystem/src/main/java/cc/pscly/onememos/ui/util/DateTimeFormatter.kt`（由 MemoItem 引用）：时间格式化。

  **Acceptance Criteria**:
  - [ ] 编译通过：`./gradlew :app:assembleDebug --stacktrace`

  **QA Scenarios**:
  ```
  Scenario: Collections NOTE_REF 预览代码路径可编译（happy path）
    Tool: Bash (gradle)
    Steps:
      1. ./gradlew :app:assembleDebug --stacktrace
    Expected Result: assembleDebug 通过
    Evidence: .sisyphus/evidence/task-09-collections-noteref-ui-assemble.txt

  Scenario: reorder/selection 逻辑不回归（edge case）
    Tool: Bash (gradle)
    Steps:
      1. ./gradlew :feature:collections:assembleDebug --stacktrace
    Expected Result: 编译通过（行为回归需在最终集成验证补充）
    Evidence: .sisyphus/evidence/task-09-collections-noteref-ui-edge.txt
  ```

- [ ] 10. 门禁补强：把 feature 模块单测纳入 `scripts/verify.sh`

  **What to do**:
  - 更新 `scripts/verify.sh`：在现有 `:app:testDebugUnitTest` 之外，增加：
    - `:feature:home:testDebugUnitTest`
    - `:feature:collections:testDebugUnitTest`
  - 保持现有输出与 `--all` 语义不变。

  **Must NOT do**:
  - 不要把 verify.sh 变成“全量 build 全模块 assemble”（仅补齐本次新增/依赖的测试即可）。

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 脚本小改动，目标明确。
  - **Skills**: （无）

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 4
  - **Blocks**: 最终门禁能覆盖本次 TDD 新增测试
  - **Blocked By**: Task 1/2/8（新增测试存在后再纳入门禁才有意义）

  **References**:
  - `scripts/verify.sh`：当前只跑 `:app:testDebugUnitTest`。
  - `settings.gradle.kts`：模块名 `:feature:home`、`:feature:collections`。

  **Acceptance Criteria**:
  - [ ] `./scripts/verify.sh` 可执行且包含新增 test task（以脚本内容审查 + 实际运行验证为准）。

  **QA Scenarios**:
  ```
  Scenario: verify.sh 门禁覆盖本次新增测试（happy path）
    Tool: Bash
    Steps:
      1. ./scripts/verify.sh
    Expected Result: 脚本依次执行 :feature:home:testDebugUnitTest 与 :feature:collections:testDebugUnitTest，最终输出 "verify.sh: OK"
    Evidence: .sisyphus/evidence/task-10-verify-sh-output.txt

  Scenario: --all 参数仍可用（edge case）
    Tool: Bash
    Steps:
      1. ./scripts/verify.sh --all
    Expected Result: 在原有基础上额外构建 baselineprofile/macrobenchmark
    Evidence: .sisyphus/evidence/task-10-verify-sh-all-output.txt
  ```

- [ ] 11. 过程留痕：更新 `.ai_session.md`（由执行代理在实现结束后补齐）

  **What to do**:
  - 在根项目 `.ai_session.md` 追加一段记录（按仓库既有格式）：
    - 原始需求（锦囊预览 + 主页多选）
    - 关键决策（多选入口=底部菜单、多选分享=合并文本全文、Collections 预览=同主页但无 tags）
    - 核心变更文件清单（HomeScreen/MemoItem/CollectionsScreen/CollectionsViewModel/AddToCollections* 等）
    - 验证命令（verify.sh + build-benchmark-apk.sh）与 benchmark APK 路径

  **Must NOT do**:
  - 不要记录敏感信息（token/serverUrl 等）。

  **Recommended Agent Profile**:
  - **Category**: `writing`
    - Reason: 文档留痕与可追溯性。
  - **Skills**: （无）

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 4
  - **Blocks**: 无
  - **Blocked By**: Task 4-9（需要先知道实际改动文件）

  **References**:
  - `.ai_session.md`：仓库级记录格式与历史条目。

  **Acceptance Criteria**:
  - [ ] `.ai_session.md` 增量记录清晰、可复现（包含命令与产物路径）。

  **QA Scenarios**:
  ```
  Scenario: .ai_session.md 条目可复现（happy path）
    Tool: Bash
    Steps:
      1. rg -n "HomeScreen\\.kt|MemoItem\\.kt|CollectionsScreen\\.kt|CollectionsViewModel\\.kt|AddToCollections" .ai_session.md
      2. rg -n "\\./scripts/verify\\.sh|\\./scripts/build-benchmark-apk\\.sh" .ai_session.md
      3. rg -n "app/build/outputs/apk/benchmark/.*T.*\\.apk" .ai_session.md
    Expected Result: 上述三条 rg 均能匹配到内容（说明记录包含关键文件、验证命令与产物路径）
    Evidence: .sisyphus/evidence/task-11-ai-session-check.txt

  Scenario: 无敏感信息泄露（edge case）
    Tool: Bash
    Steps:
      1. ! rg -n "(token|serverUrl)\\s*[:=]\\s*\\S{8,}" .ai_session.md
      2. ! rg -n "Bearer\\s+[A-Za-z0-9._-]{10,}" .ai_session.md
      3. ! rg -n "BEGIN( RSA)? PRIVATE KEY" .ai_session.md
    Expected Result: 三条命令均成功（退出码=0），表示未命中敏感值模式
    Evidence: .sisyphus/evidence/task-11-ai-session-secrets-scan.txt
  ```

- [ ] 12. 集成验证 + benchmark 交付 + git 提交

  **What to do**:
  - 运行门禁：`./scripts/verify.sh`（或按 Task 10 更新后运行）。
  - 构建 benchmark APK：`./scripts/build-benchmark-apk.sh`，记录输出的时间戳 APK 路径。
  - git 提交（至少 2 次，或按仓库习惯）：
    - Home 多选 + 批量动作
    - Collections NOTE_REF 预览
  - 在提交信息中体现“为何”：提升可读预览、提升批量效率。

  **Must NOT do**:
  - 不要提交任何 keystore/签名文件/本地配置（仓库已有 `.gradle-keystore`，按既有规则即可）。

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: 需要跑构建与产物交付。
  - **Skills**: `git-master`
    - `git-master`: 规范化提交粒度与信息。

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 4（收尾）
  - **Blocks**: 完整交付
  - **Blocked By**: Task 4-11

  **References**:
  - `scripts/verify.sh`：Linux 门禁脚本。
  - `scripts/build-benchmark-apk.sh`：benchmark 构建与时间戳复制。
  - `ARCHITECTURE.md`：构建/验证建议命令（含 benchmark）。

  **Acceptance Criteria**:
  - [ ] `./scripts/verify.sh` → OK
  - [ ] `./scripts/build-benchmark-apk.sh` 输出时间戳 APK 路径，且文件存在于 `app/build/outputs/apk/benchmark/`。
  - [ ] git 工作区干净（除 `.sisyphus/` 等忽略文件），提交完成。

  **QA Scenarios**:
  ```
  Scenario: 门禁通过（happy path）
    Tool: Bash
    Steps:
      1. ./scripts/verify.sh
    Expected Result: 输出 "verify.sh: OK"
    Evidence: .sisyphus/evidence/task-12-verify-ok.txt

  Scenario: benchmark APK 产出（edge case）
    Tool: Bash
    Steps:
      1. ./scripts/build-benchmark-apk.sh
      2. ls app/build/outputs/apk/benchmark/*.apk
    Expected Result: 存在形如 2026-02-20Txx-xx-xx.apk 的时间戳文件
    Evidence: .sisyphus/evidence/task-12-benchmark-apk-path.txt
  ```

---

## Final Verification Wave

- F1. 运行门禁脚本：`./scripts/verify.sh`
- F2. 构建 benchmark APK：`./scripts/build-benchmark-apk.sh`（记录输出路径作为证据）
- F3. 静态检查：确认未引入超范围功能；确认 Collections NOTE_REF 预览与 Home 观感一致（无 tag chips）

---

## Commit Strategy

- 建议按“Home 多选 + 批量动作”“Collections NOTE_REF 预览”两次提交；最后一次提交做集成与构建产物记录。
- Conventional Commits（中文优先），示例：
  - `feat(home): 长按菜单新增多选并支持批量操作`
  - `feat(collections): 文件夹内随笔引用展示主页式预览`

---

## Success Criteria

- 用户体验：
  - Collections：进入锦囊文件夹后，随笔引用条目可直接看到正文预览，不再主要显示“（无标题）/随笔引用”。
  - Home：长按底部菜单出现“多选”；多选后可批量放入锦囊/归档/分享（合并文本，全文）。
- 工程质量：
  - 单测（TDD）覆盖关键纯逻辑；`./scripts/verify.sh` 通过。
  - 产物：生成时间戳 benchmark APK，并输出路径。
