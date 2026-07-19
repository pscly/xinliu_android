# 标签筛选新增“排除(非)” + 插入时间 Markdown 换行修复

## TL;DR
> 目标：在现有标签筛选（或/与）上增加“排除/不含”开关（排除任意所选标签，并且排除开启时强制按“或”语义）；同时修复“插入时间”后继续输入仍处在 `>` 引用块的问题（插入 `\n\n` 结束引用）。

**Deliverables**
- Home/Editor/Collections 的标签筛选：新增“排除/不含”开关，并实现“排除任意标签（强制或）”。
- “插入时间”在 3 个入口统一插入 `> HH:mm:ss\n\n`：编辑页、极速记录、悬浮极速记录。
- 跑通 `benchmark` 构建并导出带时间戳命名的 benchmark APK。

**Estimated Effort**: Medium
**Parallel Execution**: YES (2-3 waves)
**Critical Path**: 过滤模型扩展 → Home/Editor/Collections 接线 → 3 处插入时间统一 → benchmark 构建与导出

---

## Context

### Original Request
- “你更新一下，标签筛选再多一个非功能，目前只有与或。”
- “编辑功能我希望是可以正确完善的 md 语法：插入时间后继续输入文字，实际上还是在 > 里面；换行应是两个 \n。”

### Confirmed Decisions
- **排除语义**：排除任意所选标签（只要 memo 含任意所选 tag，就被过滤掉）。
- **UI 形态**：保留“或/与”选择，再新增“排除/不含”开关。
- **强制规则**：排除开启时强制按“或”语义工作（自动切到 OR，并禁用/隐藏“与”）。
- **作用范围**：主页列表 + 详情/编辑页（点击标签弹筛选）+ Collections（锦囊）。
- **搜索框 #tag**：排除开启时，搜索框里输入的 `#tag`（queryTags）也一起参与排除。
- **插入时间**：仅“插入时间”追加空行（`\n\n`）；普通回车行为不改。
- **自动化测试**：不新增单测；验证以可执行构建 + 设备/模拟器脚本化回归为主。

### Key References (Verified)
- 过滤模型：`core/designsystem/src/main/java/cc/pscly/onememos/ui/filter/MemoFilter.kt`
- 过滤状态：`core/designsystem/src/main/java/cc/pscly/onememos/ui/filter/MemoFilterStore.kt`
- 筛选面板 UI：`core/designsystem/src/main/java/cc/pscly/onememos/ui/component/TagFilterBottomSheet.kt`
- Home 过滤逻辑：`feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeViewModel.kt`（`applyFilterToPaging`）
- Editor 筛选 apply：`feature/editor/src/main/java/cc/pscly/onememos/ui/feature/editor/EditorViewModel.kt`（`applyFilter`）
- Collections 标签筛选：`feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsScreen.kt`
- 插入时间（3 处同构）：
  - `feature/editor/src/main/java/cc/pscly/onememos/ui/feature/editor/EditorViewModel.kt`
  - `feature/quickcapture/src/main/java/cc/pscly/onememos/ui/feature/quickcapture/QuickCaptureViewModel.kt`
  - `app/src/main/java/cc/pscly/onememos/overlay/QuickCaptureOverlayService.kt`
- Markdown 渲染：`core/designsystem/src/main/java/cc/pscly/onememos/ui/component/MarkdownPaper.kt`（`org.commonmark`，存在 blockquote lazy continuation）
- benchmark 构建类型：`app/build.gradle.kts`（`buildTypes.create("benchmark")`）

---

## Work Objectives

### Core Objective
在不破坏现有 OR/AND 行为的前提下，引入“排除标签”能力，并让插入时间的 Markdown 行为符合 CommonMark 解析规则。

### Definition of Done
- [ ] Home/Editor/Collections 三处都能使用“排除/不含”开关，且排除开启时按“排除任意标签（强制或）”生效。
- [ ] 在编辑页/极速记录/悬浮极速记录点击“插入时间”后，继续输入文本不会仍被 Markdown 解析为引用块内容。
- [ ] `./gradlew :app:assembleBenchmark` 成功。
- [ ] 导出 benchmark APK 到带时间戳文件名（ISO 风格）的路径，并在输出中给出该路径。

### Must NOT Have (Guardrails)
- 不修改 `TagExtractor` 的标签定义/正则（除非发现明确 bug）。
- 不把筛选逻辑下推到数据库 SQL（避免分页语义变化与性能/一致性风险）。
- 不改普通回车输入行为（仅修复“插入时间”模板）。

---

## Verification Strategy

### Automated Tests
- **新增测试**：不新增（按用户选择）。
- **仍建议执行的现有验证命令**：`./gradlew testDebugUnitTest`（不新增测试用例，只保证现有用例与依赖可跑）。

### QA Policy (Agent-Executed)
由于不新增单测，核心行为通过“脚本化设备/模拟器回归”验证：
- 使用 `adb` 启动页面、输入文本、点击控件（可用固定分辨率模拟器 + 坐标 tap）。
- 关键节点使用 `adb exec-out screencap -p` 截图作为证据，并保存到 `.sisyphus/evidence/`。

Evidence convention:
- `.sisyphus/evidence/task-{N}-{scenario-slug}.txt|png`

---

## Execution Strategy

### Parallel Execution Waves

Wave 1 (Foundation)
- T1 过滤模型扩展：为 MemoFilter 增加排除开关字段 + Store 支持
- T2 TagFilterBottomSheet 扩展：新增排除开关 UI + 强制 OR 规则

Wave 2 (Feature Wiring, parallel)
- T3 Home：接入排除字段 + 过滤谓词更新
- T4 HomeScreen：面板 UI 传参接线 + 清空行为一致
- T5 Editor：筛选面板本地状态扩展 + applyFilter 传参
- T6 Collections：引入同样的“或/与 + 排除”筛选控制与逻辑
- T7/T8/T9 插入时间统一：Editor / QuickCapture / Overlay

Wave 3 (Integration + Build)
- T10 设备/模拟器脚本化回归（产出证据）
- T11 benchmark APK 构建与导出（带时间戳命名）
- T12 更新 `.ai_session.md` + Git commit

---

## TODOs

> 每个任务都必须产出可检查的证据文件（截图或命令输出）。

- [ ] 1. 扩展筛选模型：MemoFilter 增加“排除/不含”开关

  **What to do**:
  - 在 `core/designsystem/src/main/java/cc/pscly/onememos/ui/filter/MemoFilter.kt` 为 `MemoFilter` 增加字段（建议：`excludeTags: Boolean = false`）。
  - 保持 `TagMatchMode` 仍为 `OR/AND`（不新增 NOT 枚举），因为“排除”是开关而不是第三种模式。
  - 在 `core/designsystem/src/main/java/cc/pscly/onememos/ui/filter/MemoFilterStore.kt` 增加 `setExcludeTags(enabled: Boolean)`（或等价命名），并确保 `clear()` 会重置为默认值（exclude=false）。

  **Must NOT do**:
  - 不改 `TagExtractor` 标签正则。
  - 不把筛选逻辑移到 Repository/DAO。

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: （无）

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 1
  - **Blocks**: 2, 3, 4, 5, 6
  - **Blocked By**: None

  **References**:
  - `core/designsystem/src/main/java/cc/pscly/onememos/ui/filter/MemoFilter.kt`：筛选模型与 TagMatchMode 定义位置。
  - `core/designsystem/src/main/java/cc/pscly/onememos/ui/filter/MemoFilterStore.kt`：Home/Editor 共用的全局筛选状态来源。

  **Acceptance Criteria**:
  - [ ] `./gradlew :app:compileDebugKotlin` 通过（证据保存到 `.sisyphus/evidence/task-1-compile.txt`）。

  **QA Scenarios**:
  ```
  Scenario: 编译通过（确保模型扩展不破坏调用方）
    Tool: Bash (Gradle)
    Steps:
      1. 运行 ./gradlew :app:compileDebugKotlin
      2. 保存命令输出到 .sisyphus/evidence/task-1-compile.txt
    Expected Result: BUILD SUCCESSFUL
  ```

- [ ] 2. 扩展筛选面板：TagFilterBottomSheet 增加“排除/不含”开关 + 强制 OR 规则

  **What to do**:
  - 扩展 `core/designsystem/src/main/java/cc/pscly/onememos/ui/component/TagFilterBottomSheet.kt` 组件签名：新增 `excludeTags: Boolean` 与 `onExcludeTagsChange: (Boolean) -> Unit`。
  - UI 规则：
    - 当 `excludeTags == true` 时：自动切换到 `TagMatchMode.OR`（通过回调触发），并禁用/隐藏 “与” 按钮。
    - 文案建议：开关文案用“排除/不含”，并在标题下方给一行提示（例如“排除：含任意所选标签的记录会被隐藏”）。
  - 同步更新所有调用点（至少 HomeScreen、EditorScreen；Collections 在任务 6 里接入）。

  **Must NOT do**:
  - 不改变既有“或/与”按钮语义（exclude=false 时行为必须与现在一致）。

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
  - **Skills**: （无）

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: 4, 5, 6
  - **Blocked By**: 1

  **References**:
  - `core/designsystem/src/main/java/cc/pscly/onememos/ui/component/TagFilterBottomSheet.kt`：现有“或/与 + 清空 + 标签 chips”实现。
  - `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeScreen.kt`：Home 侧调用点（`onApply` 为空）。
  - `feature/editor/src/main/java/cc/pscly/onememos/ui/feature/editor/EditorScreen.kt`：Editor 侧调用点（`onApply` 非空）。

  **Acceptance Criteria**:
  - [ ] `./gradlew :app:compileDebugKotlin` 通过（证据 `.sisyphus/evidence/task-2-compile.txt`）。

  **QA Scenarios**:
  ```
  Scenario: 编译通过（确保新增参数已在调用处接线）
    Tool: Bash (Gradle)
    Steps:
      1. 运行 ./gradlew :app:compileDebugKotlin
      2. 保存输出到 .sisyphus/evidence/task-2-compile.txt
    Expected Result: BUILD SUCCESSFUL
  ```

- [ ] 3. Home 过滤谓词支持排除：applyFilterToPaging 增加 excludeTags 分支

  **What to do**:
  - 在 `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeViewModel.kt` 的 `applyFilterToPaging(...)` 中读取 `filter.excludeTags`。
  - 计算 `effectiveTags` 仍沿用当前逻辑：`selectedTags + queryTags`（已确认排除也包含 queryTags）。
  - 过滤逻辑：
    - 若 `effectiveTags.isEmpty()`：保持现有逻辑（只看 textOk）。
    - 若 `excludeTags == true`：返回 `effectiveTags.none { it in memoTags }`（排除任意标签，强制 OR）。
    - 否则沿用现有 `OR/AND`。
  - 注意：`memoTags` 的 fallback 逻辑保持不变（优先 `memo.tags`，为空再 `TagExtractor.extractAll(memo.content)`）。

  **Must NOT do**:
  - 不新增新的标签解析路径（避免性能回归）。

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: （无）

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: 10
  - **Blocked By**: 1

  **References**:
  - `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeViewModel.kt`：现有 OR/AND 分支位置与 `effectiveTags` 计算。

  **Acceptance Criteria**:
  - [ ] `./gradlew :app:compileDebugKotlin` 通过（证据 `.sisyphus/evidence/task-3-compile.txt`）。

  **QA Scenarios**:
  ```
  Scenario: 编译通过（HomeViewModel 逻辑分支接入成功）
    Tool: Bash (Gradle)
    Steps:
      1. 运行 ./gradlew :app:compileDebugKotlin
      2. 保存输出到 .sisyphus/evidence/task-3-compile.txt
    Expected Result: BUILD SUCCESSFUL
  ```

- [ ] 4. HomeScreen 接线：筛选面板新增排除开关的 state/回调/清空一致性

  **What to do**:
  - 在 `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeScreen.kt` 的 `TagFilterBottomSheet(...)` 调用处传入新参数（`excludeTags`、`onExcludeTagsChange`）。
  - 为排除开关新增 ViewModel API（推荐：在 `HomeViewModel` 增加 `setExcludeTags(Boolean)`，再转调 `MemoFilterStore`）。
  - 清空按钮行为：清空应同时清掉 `selectedTags`、把 `tagMatchMode` 设回 OR、并关闭排除开关（exclude=false）。

  **Must NOT do**:
  - 不改变 Home 侧 `onApply == null` 的行为（Home 仍是“即时生效”）。

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: （无）

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: 10
  - **Blocked By**: 1, 2

  **References**:
  - `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeScreen.kt`：TagFilterBottomSheet 调用点。
  - `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeViewModel.kt`：现有 `setTagMatchMode`/`toggleTag`/`clearFilter` 等过滤 API。
  - `core/designsystem/src/main/java/cc/pscly/onememos/ui/filter/MemoFilterStore.kt`：需要增加 `setExcludeTags` 或等价入口。

  **Acceptance Criteria**:
  - [ ] `./gradlew :app:compileDebugKotlin` 通过（证据 `.sisyphus/evidence/task-4-compile.txt`）。

  **QA Scenarios**:
  ```
  Scenario: 编译通过（HomeScreen/VM/store 接线完成）
    Tool: Bash (Gradle)
    Steps:
      1. 运行 ./gradlew :app:compileDebugKotlin
      2. 保存输出到 .sisyphus/evidence/task-4-compile.txt
    Expected Result: BUILD SUCCESSFUL
  ```

- [ ] 5. Editor/Detail 接线：筛选面板本地状态扩展 + applyFilter 传递排除字段

  **What to do**:
  - 在 `feature/editor/src/main/java/cc/pscly/onememos/ui/feature/editor/EditorScreen.kt`：
    - 增加本地 state：`excludeTags`（默认 false）。
    - `TagFilterBottomSheet(...)` 传入 `excludeTags` 与 `onExcludeTagsChange`。
    - 排除开启时强制 `filterMode = TagMatchMode.OR`，并禁用“与”。（由 BottomSheet 控件本身做更佳，但 Editor 侧也要保证最终参数一致）
    - `onClear` 同时清空 selectedTags、设回 OR、关闭排除。
    - `onApply` 构造 `MemoFilter(selectedTags = ..., tagMatchMode = ..., excludeTags = ...)`。
  - 确保 `feature/editor/src/main/java/cc/pscly/onememos/ui/feature/editor/EditorViewModel.kt` 的 `applyFilter(filter: MemoFilter)` 不需要额外改动（它只是透传到 store），但要保证新字段不会被忽略（Kotlin data class copy 默认即可）。

  **Must NOT do**:
  - 不改变 Editor 的保存/归档/附件等无关逻辑。

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: （无）

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: 10
  - **Blocked By**: 1, 2

  **References**:
  - `feature/editor/src/main/java/cc/pscly/onememos/ui/feature/editor/EditorScreen.kt`：已有 `filterMode`、`filterSelectedTags` 与 `MemoFilter(...)` 构造点。
  - `feature/editor/src/main/java/cc/pscly/onememos/ui/feature/editor/EditorViewModel.kt`：`applyFilter(filter: MemoFilter)` 透传到 `MemoFilterStore`。

  **Acceptance Criteria**:
  - [ ] `./gradlew :app:compileDebugKotlin` 通过（证据 `.sisyphus/evidence/task-5-compile.txt`）。

  **QA Scenarios**:
  ```
  Scenario: 编译通过（EditorScreen 传参与 MemoFilter 扩展一致）
    Tool: Bash (Gradle)
    Steps:
      1. 运行 ./gradlew :app:compileDebugKotlin
      2. 保存输出到 .sisyphus/evidence/task-5-compile.txt
    Expected Result: BUILD SUCCESSFUL
  ```

- [ ] 6. Collections（锦囊）升级：加入“或/与 + 排除”并复用 TagFilterBottomSheet

  **What to do**:
  - 在 `feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsScreen.kt`：
    - 新增本地 state：`tagMatchMode`（默认 OR）、`excludeTags`（默认 false）、`showFilterSheet`（控制 BottomSheet）。
    - 提供入口打开 `TagFilterBottomSheet`（建议：TopAppBar actions 增加一个筛选按钮，或在 `selectedTags` bar 右侧增加“筛选”chip）。
    - `allTags`：从 `uiState.memoByRefTargetId` 与当前可见 NOTE_REF memo 聚合构建 `TagStat` 列表（可复用 `TagStats.build(...)` 模式；注意不要在每次 recomposition 重算，需 `remember(...)` + 适度 debounce/withContext）。
    - 排除开启时强制 OR（同 Home/Editor 规则）。
  - 更新 `itemsToRender` 的过滤逻辑：
    - `excludeTags==true`：NOTE_REF 若拿不到 memo 或 tags，则保留；否则 `effectiveTags.none { it in memoTags }`。
    - 否则：按 OR/AND 判断（OR=any，AND=all）。

  **Must NOT do**:
  - 不破坏 `selectionMode/reorderMode` 下对筛选控件的禁用策略（已有 `filterControlsEnabled` 逻辑）。

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: （无）

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: 10
  - **Blocked By**: 1, 2

  **References**:
  - `feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsScreen.kt`：`selectedTags`、`itemsToRender` 过滤点、selection/reorder 的禁用条件。
  - `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeViewModel.kt`：OR/AND 谓词与 tag fallback 方式可复用。
  - `core/designsystem/src/main/java/cc/pscly/onememos/ui/component/TagFilterBottomSheet.kt`：新增排除开关后的统一面板。

  **Acceptance Criteria**:
  - [ ] `./gradlew :app:compileDebugKotlin` 通过（证据 `.sisyphus/evidence/task-6-compile.txt`）。

  **QA Scenarios**:
  ```
  Scenario: 编译通过（Collections 新面板与过滤逻辑接入成功）
    Tool: Bash (Gradle)
    Steps:
      1. 运行 ./gradlew :app:compileDebugKotlin
      2. 保存输出到 .sisyphus/evidence/task-6-compile.txt
    Expected Result: BUILD SUCCESSFUL
  ```

- [ ] 7. 插入时间（编辑页）：追加空行 `\n\n`，确保退出引用块

  **What to do**:
  - 修改 `feature/editor/src/main/java/cc/pscly/onememos/ui/feature/editor/EditorViewModel.kt` 的 `insertLineAtSelection(...)`：
    - 当前插入片段尾部为 `\n`，改为 `\n\n`。
    - 建议做“去重空行”的小保护：若插入点后面已经是换行/空行，则不要无限累加（避免连续点“时”产生多空行）。

  **Must NOT do**:
  - 不改变普通回车行为；只改“插入时间”拼接的模板。

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: （无）

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with 8, 9)
  - **Blocks**: 10
  - **Blocked By**: None

  **References**:
  - `feature/editor/src/main/java/cc/pscly/onememos/ui/feature/editor/EditorViewModel.kt`：`insertCurrentTimeStamp()` 与 `insertLineAtSelection()`。
  - `core/designsystem/src/main/java/cc/pscly/onememos/ui/util/DateTimeFormatter.kt`：时间格式 `HH:mm:ss`。

  **Acceptance Criteria**:
  - [ ] `./gradlew :app:compileDebugKotlin` 通过（证据 `.sisyphus/evidence/task-7-compile.txt`）。

  **QA Scenarios**:
  ```
  Scenario: 编译通过（插入时间逻辑更新已生效）
    Tool: Bash (Gradle)
    Steps:
      1. 运行 ./gradlew :app:compileDebugKotlin
      2. 保存输出到 .sisyphus/evidence/task-7-compile.txt
    Expected Result: BUILD SUCCESSFUL
  ```

- [ ] 8. 插入时间（极速记录）：追加空行 `\n\n`

  **What to do**:
  - 修改 `feature/quickcapture/src/main/java/cc/pscly/onememos/ui/feature/quickcapture/QuickCaptureViewModel.kt` 的 `insertLineAtSelection(...)`，尾部 `\n` → `\n\n`。
  - 规则同任务 7（只改插入时间模板，不动回车）。

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: （无）

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with 7, 9)
  - **Blocks**: 10
  - **Blocked By**: None

  **References**:
  - `feature/quickcapture/src/main/java/cc/pscly/onememos/ui/feature/quickcapture/QuickCaptureViewModel.kt`：与 Editor 同构的插入逻辑。

  **Acceptance Criteria**:
  - [ ] `./gradlew :app:compileDebugKotlin` 通过（证据 `.sisyphus/evidence/task-8-compile.txt`）。

  **QA Scenarios**:
  ```
  Scenario: 编译通过（QuickCapture 插入时间模板更新）
    Tool: Bash (Gradle)
    Steps:
      1. 运行 ./gradlew :app:compileDebugKotlin
      2. 保存输出到 .sisyphus/evidence/task-8-compile.txt
    Expected Result: BUILD SUCCESSFUL
  ```

- [ ] 9. 插入时间（悬浮极速记录 Overlay）：追加空行 `\n\n`

  **What to do**:
  - 修改 `app/src/main/java/cc/pscly/onememos/overlay/QuickCaptureOverlayService.kt` 的 `insertLineAtSelection(...)`，尾部 `\n` → `\n\n`。

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: （无）

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with 7, 8)
  - **Blocks**: 10
  - **Blocked By**: None

  **References**:
  - `app/src/main/java/cc/pscly/onememos/overlay/QuickCaptureOverlayService.kt`：Overlay 内部私有的插入逻辑。

  **Acceptance Criteria**:
  - [ ] `./gradlew :app:compileDebugKotlin` 通过（证据 `.sisyphus/evidence/task-9-compile.txt`）。

  **QA Scenarios**:
  ```
  Scenario: 编译通过（Overlay 插入时间模板更新）
    Tool: Bash (Gradle)
    Steps:
      1. 运行 ./gradlew :app:compileDebugKotlin
      2. 保存输出到 .sisyphus/evidence/task-9-compile.txt
    Expected Result: BUILD SUCCESSFUL
  ```

- [ ] 10. 设备/模拟器脚本化回归（验证排除筛选 + 插入时间退出引用）

  **What to do**:
  - 目标：在不新增测试代码的情况下，用 `adb + uiautomator dump + screenshot` 做可重复的验证，并保存证据。
  - 建议在固定分辨率模拟器执行（例如 Pixel 6 API 34），避免坐标偏差。

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: （无）

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 3
  - **Blocks**: 11, 12
  - **Blocked By**: 3, 4, 5, 6, 7, 8, 9

  **References**:
  - `app/src/main/AndroidManifest.xml`：启动入口：`cc.pscly.onememos/.MainActivity` 与 `cc.pscly.onememos/.ui.feature.quickcapture.QuickCaptureActivity`。
  - `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeScreen.kt`：筛选按钮 `contentDescription = "筛选"`（用于 uiautomator 定位）。
  - `feature/editor/src/main/java/cc/pscly/onememos/ui/feature/editor/EditorScreen.kt`：插入时间按钮 `contentDescription = "插入时间"`。
  - `feature/quickcapture/src/main/java/cc/pscly/onememos/ui/feature/quickcapture/QuickCaptureScreen.kt`：插入时间按钮 `contentDescription = "插入时间"`，保存按钮文字为“盖”。
  - `app/src/main/java/cc/pscly/onememos/overlay/QuickCaptureOverlayService.kt`：插入时间按钮 `contentDescription = "插入时间"`（Overlay 验证可能受系统悬浮窗权限限制）。

  **Acceptance Criteria**:
  - [ ] 证据文件存在：
    - `.sisyphus/evidence/task-10-insert-time-quickcapture.png`
    - `.sisyphus/evidence/task-10-tag-filter-exclude.png`
    - `.sisyphus/evidence/task-10-tag-filter-include-and.png`
    - `.sisyphus/evidence/task-10-uiautomator-dumps/`（包含关键 xml dump）

  **QA Scenarios**:
  ```
  Scenario: 快速记录页“插入时间”后自动退出引用块
    Tool: Bash (adb + uiautomator)
    Preconditions:
      - adb 可用，已连接 emulator/device：adb devices
      - 已安装 debug 或 benchmark 包：cc.pscly.onememos
    Steps:
      1. adb shell am start -n cc.pscly.onememos/.ui.feature.quickcapture.QuickCaptureActivity
      2. 等待页面稳定：adb shell uiautomator dump /sdcard/ui.xml
      3. pull dump：adb pull /sdcard/ui.xml .sisyphus/evidence/task-10-uiautomator-dumps/quickcapture-1.xml
      4. 从 xml 中找到 content-desc="插入时间" 的节点 bounds（形如 [l,t][r,b]），计算中心点坐标 (x,y)
      5. adb shell input tap x y
      6. adb shell input text "after"
      7. 截图：adb exec-out screencap -p > .sisyphus/evidence/task-10-insert-time-quickcapture.png
    Expected Result:
      - 截图中可见：一行以 "> HH:mm:ss" 开头的时间行，下方有一行空行，然后才是 "after"（不紧贴时间行）。

  Scenario: Home 标签筛选 - 排除任意标签（强制或）
    Tool: Bash (adb + uiautomator)
    Preconditions:
      - 通过 QuickCapture 连续创建 3 条本地 memo（每条保存后重启 QuickCaptureActivity）：
        1) "#a memo_a"  2) "#b memo_b"  3) "#a #b memo_ab"
    Steps:
      0. （创建数据）对每条 memo 重复一次：
         a) adb shell am start -n cc.pscly.onememos/.ui.feature.quickcapture.QuickCaptureActivity
         b) adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml .sisyphus/evidence/task-10-uiautomator-dumps/quickcapture-create.xml
         c) 若输入框未聚焦，先 tap 屏幕中部聚焦：adb shell input tap 540 1200（按实际分辨率调整）
         d) adb shell input text "#a\ memo_a"（注意空格需要转义；第二、三条分别替换为 #b memo_b / #a #b memo_ab）
         e) 在 xml 中找到 text="盖" 的节点 bounds，计算中心点 tap 以保存
         f) 截图保存一张：.sisyphus/evidence/task-10-created-memos.png（可覆盖写）
      1. adb shell am start -n cc.pscly.onememos/.MainActivity
      2. dump 并 pull xml：.sisyphus/evidence/task-10-uiautomator-dumps/home-1.xml
      3. 找到 content-desc="筛选" 的按钮 bounds，tap 打开 TagFilterBottomSheet
      4. dump BottomSheet 并 pull xml，分别定位 tag 节点并 tap：
         - 找到 text 以 "a" 开头的 chip（可能是 "a" 或 "a (2)"）bounds 并 tap
         - 找到 text 以 "b" 开头的 chip（可能是 "b" 或 "b (2)"）bounds 并 tap
      5. 打开“排除/不含”开关：dump xml 找到 content-desc="排除"（实现时必须提供）并 tap
      6. 截图：adb exec-out screencap -p > .sisyphus/evidence/task-10-tag-filter-exclude.png
    Expected Result:
      - 列表中不再出现包含 #a 或 #b 的 memo（memo_a / memo_b / memo_ab 都应隐藏）；若列表为空，应显示空态。

  Scenario: Home 标签筛选 - 包含模式 AND
    Tool: Bash (adb + uiautomator)
    Steps:
      1. 关闭排除开关（exclude=false）
      2. 选择“与”模式
      3. 选择 tag "a" 与 "b"
      4. 截图：adb exec-out screencap -p > .sisyphus/evidence/task-10-tag-filter-include-and.png
    Expected Result:
      - 列表中只剩 memo_ab（同时含 #a 与 #b）。

  Scenario: Overlay 插入时间（可选）
    Tool: Bash (adb + uiautomator)
    Preconditions:
      - 设备已授予悬浮窗权限（SYSTEM_ALERT_WINDOW）；否则该场景记录为“跳过”。
    Steps:
      1. 启动入口 activity：adb shell am start -n cc.pscly.onememos/.overlay.QuickCaptureOverlayEntryActivity
      2. dump 并 pull xml，确认出现 content-desc="插入时间" 后按与 QuickCapture 相同方式 tap
      3. input text "after"
      4. 截图保存：.sisyphus/evidence/task-10-insert-time-overlay.png
    Expected Result:
      - 若权限已开：截图中同样应出现 `> HH:mm:ss` + 空行 + after
      - 若权限未开：保存一张当前系统弹窗/引导截图到 .sisyphus/evidence/task-10-insert-time-overlay-skip.png
  ```

- [ ] 11. 构建并导出 benchmark APK（带时间戳命名）

  **What to do**:
  - 在仓库根目录运行：`./gradlew :app:assembleBenchmark`。
  - 定位输出 APK（通常在 `app/build/outputs/apk/benchmark/`）。
  - 将 APK 复制/重命名为带时间戳的文件名（示例：`onememos-benchmark-2026-02-26T12-34-56.apk`），并记录最终路径。
  - 不要导出 debug APK。

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: （无）

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 3
  - **Blocks**: 12
  - **Blocked By**: 10

  **References**:
  - `app/build.gradle.kts`：benchmark buildType 已定义（debug 签名复用）。

  **Acceptance Criteria**:
  - [ ] `./gradlew :app:assembleBenchmark` 成功（证据 `.sisyphus/evidence/task-11-assembleBenchmark.txt`）。
  - [ ] 导出的 APK 路径被记录在 `.sisyphus/evidence/task-11-apk-path.txt`，且文件名包含时间戳（`YYYY-MM-DDTHH-MM-SS`）。

  **QA Scenarios**:
  ```
  Scenario: benchmark APK 产物可用
    Tool: Bash (Gradle)
    Steps:
      1. 运行 ./gradlew :app:assembleBenchmark | tee .sisyphus/evidence/task-11-assembleBenchmark.txt
      2. 从 app/build/outputs/apk/benchmark/ 复制 APK 并重命名为 onememos-benchmark-YYYY-MM-DDTHH-MM-SS.apk
      3. 把最终绝对路径写入 .sisyphus/evidence/task-11-apk-path.txt
    Expected Result:
      - assembleBenchmark 成功
      - 导出文件存在且为 benchmark 变体
  ```

- [ ] 12. 更新会话记录 + 提交代码（Conventional Commits）

  **What to do**:
  - 更新根目录 `.ai_session.md`，追加一条记录：
    - 新增“排除/不含”开关与强制 OR 规则
    - 修复插入时间追加空行以退出 blockquote
    - 涉及的关键文件列表
    - 验证命令（assembleBenchmark 等）
  - Git 提交（一个 commit 即可）：
    - 推荐 message：`feat: 标签筛选支持排除并修复插入时间换行`

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `git-master`（仅在执行阶段）

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 3
  - **Blocked By**: 11

  **References**:
  - `.ai_session.md`：项目要求每次重要改动后更新。

  **Acceptance Criteria**:
  - [ ] `git status` 干净（除导出 APK 外的产物不入库）。
  - [ ] `git log -1` 显示本次提交信息。

---

## Final Verification Wave

> 4 个检查可并行执行；任一失败都要回到对应任务修复并补齐证据。

- [ ] F1. 行为一致性审计（排除强制 OR + queryTags 参与排除）
  - 检查点：
    - Home：排除开关打开时，“与”不可选/不可生效；排除逻辑为 `effectiveTags.none { it in memoTags }`。
    - Editor：排除开关打开时强制 OR；Apply 透传到 `MemoFilterStore`。
    - Collections：排除开关打开时强制 OR；NOTE_REF 无 memo 时不应被误排除。
  - 证据：复用任务 10 的截图与 dump。

- [ ] F2. 构建与静态检查
  - 运行：
    - `./gradlew :app:assembleDebug`
    - `./gradlew :app:assembleBenchmark`
    - `./gradlew :app:lint`
    - `./gradlew testDebugUnitTest`
  - 证据：保存到 `.sisyphus/evidence/final-build.txt`（将四个命令输出串行追加即可）。

- [ ] F3. 设备/模拟器回归复跑
  - 完整复跑任务 10 的 3 个 QA scenario，确认截图与预期一致。
  - 证据：`.sisyphus/evidence/final-qa/` 下保留最终截图（可直接复制 task-10 的证据）。

- [ ] F4. 范围与污染检查
  - 仅允许改动与以下主题相关的文件：MemoFilter/MemoFilterStore、TagFilterBottomSheet、Home/Editor/Collections 的筛选接线、3 处插入时间。
  - 禁止出现：无关 UI 重构、TagExtractor 语义改动、数据库查询逻辑变更。
  - 证据：`git diff` 摘要保存到 `.sisyphus/evidence/final-diff.txt`。

---

## Commit Strategy

- Commit 频率：建议 1 个 commit（任务 1-9 完成并通过 task-10 回归后提交）。
- Commit message（建议）：`feat: 标签筛选支持排除并修复插入时间换行`
- 不提交产物：benchmark APK 仅作为交付物导出，不纳入 git。

---

## Success Criteria

### Verification Commands
```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
./gradlew :app:assembleBenchmark
./gradlew :app:lint
./gradlew testDebugUnitTest
```

### Final Checklist
- [ ] Home/Editor/Collections 均具备“排除/不含”开关；排除开启时强制 OR 并按“排除任意标签”生效。
- [ ] 搜索框 `#tag` 在排除开启时也会参与排除。
- [ ] 三入口“插入时间”统一插入 `> HH:mm:ss\n\n`。
- [ ] `.sisyphus/evidence/` 下存在任务 10、11 的证据与最终回归证据。
- [ ] 已导出 benchmark APK，文件名包含时间戳，路径记录在 `.sisyphus/evidence/task-11-apk-path.txt`。
