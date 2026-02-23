# 锦囊（Collections）复用“自动标签元数据行隐藏” + 预览一致性完善 + benchmark 交付

## TL;DR
> 把 Home 已有的“自动标签元数据行隐藏（AutoTagLineHider）”能力，按同样的开关与关键字配置，复用到锦囊（Collections）NOTE_REF 预览与放入锦囊时的显示名生成；最后按仓库流程产出新的 benchmark APK，并安装/推送到手机（`ADB_SERIAL=192.168.12.101:5555`）。

**交付物**
- 锦囊列表 NOTE_REF（memos_memo）预览：跟随 `devShowAutoTagLineInHome` 隐藏/显示元数据行（关键字来自 `devAutoTagLineKeywords`）
- “放入锦囊”生成的 NOTE_REF `displayName`：同样使用 settings 关键字跳过元数据行，避免标题/预览不一致
- 交付链路：`./scripts/verify.sh` 通过；`./scripts/deliver-benchmark.sh` 生成时间戳 benchmark APK，并安装 + push 到 `/sdcard/Download/`

**预计工作量**：Short
**并行执行**：YES（2 waves）
**关键路径**：CollectionsViewModel/UiState → CollectionsScreen 预览渲染 → AddToCollectionsViewModel displayName → verify → deliver benchmark

---

## Context

### 原始需求（用户）
- “自动标签数据行隐藏的那个功能，也可以作用在锦囊那边去……然后把锦囊功能做的更完善完好一些”

### 访谈结论（已确认）
- 锦囊侧复用 Home 既有隐藏逻辑（不做差异化改造）
- 开关选择：复用 `devShowAutoTagLineInHome`
- 关键字来源：使用 settings 的 `devAutoTagLineKeywords`
- 预览一致性：需要把 NOTE_REF 的 displayName 生成也改成使用 settings 关键字
- 交付要求：需要新的 benchmark APK，并推送到手机（`ADB_SERIAL=192.168.12.101:5555`）
- 测试策略：不新增测试；以 `./scripts/verify.sh` 为基础门禁

### 代码与规则定位（可直接参考）
- 隐藏规则权威实现：`core/designsystem/src/main/java/cc/pscly/onememos/ui/util/AutoTagLineHider.kt`
- 纯文本预览跳过行：`core/domain/src/main/java/cc/pscly/onememos/domain/derived/MemoDerivedFields.kt`（`MarkdownDeriver.plainPreviewSkippingLinesEndingWithKeywords`）
- Home 的复用范例：`feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/MemoItem.kt`
- 锦囊渲染缺口：`feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsScreen.kt`（NOTE_REF 预览直接用 `memo.content`）
- 锦囊 VM 缺口：`feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsViewModel.kt`（当前仅用 settings 做 enabled gating）
- displayName 生成缺口：`feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/AddToCollectionsViewModel.kt`（当前固定默认 `__Atags`）

### Metis Review（已吸收为 guardrails）
- 不新增 Collections 专用开关；严格复用 Home 开关语义
- 不扩大到 Collections 之外的其它列表/详情/分享卡片，除非用户后续明确要求
- 关键字解析遵循 `AutoTagLineHider.parseKeywords` 默认行为（空/空白 -> 默认 `__Atags`），确保与 Home 一致
- Compose 列表中避免 per-item 重复解析关键字：在 Screen 层 `remember(raw)` 解析一次即可

---

## Work Objectives

### 核心目标
让锦囊（Collections）NOTE_REF 的“预览内容”和“显示名”与 Home 的“自动标签元数据行隐藏”保持同一套规则、同一套开关、同一套关键字来源。

### 这次明确包含（IN）
- Collections：NOTE_REF（`CollectionRefType.MEMOS_MEMO`）的富预览（`MarkdownPreview`）与降级文本预览（`Text(plainPreview)`）
- AddToCollections：NOTE_REF 的 `displayName` 生成
- benchmark 交付：构建 + 安装 + push 到 Download

### 明确不包含（OUT / Guardrails）
- 不新增 settings 开关（不做 `devShowAutoTagLineInCollections`）
- 不重构 `AutoTagLineHider` / `MarkdownDeriver` 现有 API
- 不扩展到 Flow Note 引用（`CollectionRefType.FLOW_NOTE`）的预览能力

---

## Verification Strategy

### 测试决策
- **测试基础设施**：JUnit4 + Robolectric（已有）；Macrobenchmark/BaselineProfile 模块存在（需要设备）
- **自动化测试**：None（本次不新增测试）
- **基础门禁**：必须跑 `./scripts/verify.sh`

### 交付验证（agent 可执行）
- `./scripts/verify.sh` 通过
- `ADB_SERIAL=192.168.12.101:5555 ./scripts/deliver-benchmark.sh` 通过
- ADB 断言：设备在线、包可查询、可启动（见 TODO 的 QA Scenarios）

---

## Execution Strategy

### Parallel Execution Waves

Wave 1（代码改动，可并行但注意文件冲突）：
- T1：CollectionsViewModel/UiState 注入 settings（开关 + 关键字 raw）
- T2：CollectionsScreen NOTE_REF 预览应用 Home 同款隐藏逻辑（富预览 + 文本降级预览）
- T3：AddToCollectionsViewModel displayName 改为使用 settings 关键字

Wave 2（集成验证 + 交付闭环）：
- T4：跑门禁 `./scripts/verify.sh` 并保存证据
- T5：benchmark 构建/安装/推送（`deliver-benchmark.sh`）并保存证据
- T6：更新 `.ai_session.md` + git commit（按 Conventional Commits）

---

## TODOs

- [x] 1. CollectionsUiState 注入“自动标签隐藏”开关与关键字（仅 settings 透传）

  **What to do**:
  - 修改 `feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsViewModel.kt`：
    - 在 `CollectionsUiState` 增加字段（命名可按现有风格）：
      - `devAutoTagLineKeywordsRaw: String`
      - `devShowAutoTagLineInHome: Boolean`
    - 从 `settingsRepository.settings` 映射出上述 2 个值，并在 `uiState` 的 `combine(...)` 构建时写入 `CollectionsUiState`。
  - **默认策略**：不在 VM 层做特殊兜底；Screen 侧统一用 `AutoTagLineHider.parseKeywords(raw)` 解析（raw 为空/空白会回退默认 `__Atags`，与 Home 一致）。

  **Must NOT do**:
  - 不新增任何 settings Key/开关（不做 Collections 专用开关）
  - 不把关键字解析放到 LazyColumn item 内（避免 per-item 重算）

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 单文件 ViewModel/UiState 透传字段改动
  - **Skills**: （无）

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: Task 2
  - **Blocked By**: None

  **References**:
  - `feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsViewModel.kt:41` - `CollectionsUiState` 定义位置（新增字段）
  - `feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsViewModel.kt:119` - `uiState` combine 构建位置（把 settings 写进 state）
  - `core/model/src/main/java/cc/pscly/onememos/domain/model/AppSettings.kt` - `devAutoTagLineKeywords/devShowAutoTagLineInHome` 来源
  - `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeViewModel.kt` - Home 侧 settings 注入范例（对齐用法）

  **Acceptance Criteria**:
  - [ ] `CollectionsUiState` 暴露关键字 raw + Home 开关两字段，且 `CollectionsScreen` 可读取

  **QA Scenarios**:
  ```
  Scenario: 编译通过（Collections 侧透传改动不破坏构建）
    Tool: Bash
    Steps:
      1. ./gradlew :feature:collections:compileDebugKotlin --stacktrace | tee .sisyphus/evidence/task-1-collections-settings-compile.txt
    Expected Result: 退出码为 0
    Evidence: .sisyphus/evidence/task-1-collections-settings-compile.txt
  ```

- [x] 2. 锦囊列表 NOTE_REF 预览应用 Home 同款“元数据行隐藏”（富预览 + 文本降级预览）

  **What to do**:
  - 修改 `feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsScreen.kt`：
    - 在 `CollectionsScreen` 顶层（拿到 `uiState` 后）解析关键字一次：
      - `val autoTagKeywords = remember(uiState.devAutoTagLineKeywordsRaw) { AutoTagLineHider.parseKeywords(uiState.devAutoTagLineKeywordsRaw) }`
      - `val showAutoTagLineInHome = uiState.devShowAutoTagLineInHome`
    - 在 NOTE_REF（`CollectionRefType.MEMOS_MEMO`）卡片渲染处：
      - 富预览 `MarkdownPreview(markdown = ...)` 改为传入 `displayMarkdown`：
        - 逻辑与 Home 一致：`if (showAutoTagLineInHome) memo.content else AutoTagLineHider.hideFast(memo.content, autoTagKeywords)`
      - 文本降级预览 `plainPreview` 的计算逻辑对齐 Home：
        - 当开关关闭且 `basePlainPreview` 包含关键字时，用 `MarkdownDeriver.plainPreviewSkippingLinesEndingWithKeywords(markdown = memo.content, keywords = autoTagKeywords, maxChars = 320)` 重新生成
    - 仅影响 NOTE_REF 的预览内容；其余 UI（筛选、多选、排序、缩略图、时间/同步状态）不改。

  **Must NOT do**:
  - 不在列表 item 内重复 `parseKeywords`（关键字必须在 Screen 层一次性解析）
  - 不改动 Flow Note 引用（`CollectionRefType.FLOW_NOTE`）当前占位策略

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 单文件 Compose 逻辑对齐 Home 的既有模式
  - **Skills**: （无）

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: Task 4
  - **Blocked By**: Task 1

  **References**:
  - `feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsScreen.kt:957` - NOTE_REF 富预览当前直接 `markdown = memo.content`（需要替换）
  - `feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsScreen.kt:915` - NOTE_REF 文本降级预览 `plainPreview` 计算（需要对齐 Home）
  - `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/MemoItem.kt:164` - Home 的 `plainPreview` 生成逻辑（照抄对齐）
  - `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/MemoItem.kt:228` - Home 的 `displayMarkdown`（hideFast）逻辑（照抄对齐）
  - `core/designsystem/src/main/java/cc/pscly/onememos/ui/util/AutoTagLineHider.kt` - hideFast/parseKeywords 权威实现
  - `core/domain/src/main/java/cc/pscly/onememos/domain/derived/MemoDerivedFields.kt` - `plainPreviewSkippingLinesEndingWithKeywords`（与 Home 对齐使用）

  **Acceptance Criteria**:
  - [ ] 当 `devShowAutoTagLineInHome=false` 时，NOTE_REF 富预览与降级文本预览都不会再显示匹配关键字的元数据行
  - [ ] 当 `devShowAutoTagLineInHome=true` 时，NOTE_REF 预览保持原文（与 Home 一致）

  **QA Scenarios**:
  ```
  Scenario: 静态检查 - NOTE_REF 富预览不再直接使用 memo.content
    Tool: Bash
    Steps:
      1. ! rg "MarkdownPreview\(\s*markdown = memo\.content" feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsScreen.kt > .sisyphus/evidence/task-2-rg-markdownpreview-memo-content.txt
    Expected Result: 无匹配（返回 1 或输出为空）
    Evidence: .sisyphus/evidence/task-2-rg-markdownpreview-memo-content.txt

  Scenario: 编译通过（CollectionsScreen 改动不破坏构建）
    Tool: Bash
    Steps:
      1. ./gradlew :feature:collections:compileDebugKotlin --stacktrace | tee .sisyphus/evidence/task-2-collections-screen-compile.txt
    Expected Result: 退出码为 0
    Evidence: .sisyphus/evidence/task-2-collections-screen-compile.txt
  ```

- [x] 3. “放入锦囊”生成 displayName 改为使用 settings 关键字（不再固定默认 `__Atags`）

  **What to do**:
  - 修改 `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/AddToCollectionsViewModel.kt`：
    - 新增一个 `StateFlow<List<String>>`（或等价缓存）用于保存 `AutoTagLineHider.parseKeywords(settings.devAutoTagLineKeywords)` 的结果
    - `buildDisplayName(memo)` 使用上述关键字生成：
      - `MarkdownDeriver.plainPreviewSkippingLinesEndingWithKeywords(markdown = memo.content, keywords = keys, maxChars = 80)`
    - 保持现有 fallback：候选为空则返回 `"随笔"`
  - 说明（写入 `.ai_session.md`）：该改动只影响“新添加到锦囊”的 NOTE_REF；历史条目若要更新需要手动重命名或另做迁移（本次 OUT）。

  **Must NOT do**:
  - 不改动 collectionsRepository.addMemoRef(...) 的其它字段语义
  - 不尝试批量迁移/回填历史 displayName（避免范围扩大）

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 单文件 ViewModel 逻辑改动
  - **Skills**: （无）

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: Task 4
  - **Blocked By**: None

  **References**:
  - `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/AddToCollectionsViewModel.kt:32` - 当前固定默认关键字 `parseKeywords(null)`（需要替换为 settings）
  - `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/AddToCollectionsViewModel.kt:94` - `buildDisplayName()` 位置
  - `core/model/src/main/java/cc/pscly/onememos/domain/model/AppSettings.kt` - `devAutoTagLineKeywords` 字段
  - `core/designsystem/src/main/java/cc/pscly/onememos/ui/util/AutoTagLineHider.kt` - `parseKeywords` 默认行为（空 -> `__Atags`）

  **Acceptance Criteria**:
  - [ ] `buildDisplayName()` 使用 settings 关键字（而非固定默认关键字）

  **QA Scenarios**:
  ```
  Scenario: 编译通过（Home 侧 ViewModel 改动不破坏构建）
    Tool: Bash
    Steps:
      1. ./gradlew :feature:home:compileDebugKotlin --stacktrace | tee .sisyphus/evidence/task-3-add-to-collections-compile.txt
    Expected Result: 退出码为 0
    Evidence: .sisyphus/evidence/task-3-add-to-collections-compile.txt
  ```

- [x] 4. 门禁验证：运行 `./scripts/verify.sh`（保存证据）

  **What to do**:
  - 在仓库根目录执行：`./scripts/verify.sh`
  - 使用 `tee` 保存完整输出到证据文件

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Gradle 构建/单测/lint/benchmark 组合门禁，耗时且可能需要排障
  - **Skills**: （无）

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 2
  - **Blocks**: Task 5, Task 6
  - **Blocked By**: Task 1-3

  **Acceptance Criteria**:
  - [ ] `./scripts/verify.sh` 输出包含 `verify.sh: OK`

  **QA Scenarios**:
  ```
  Scenario: 运行门禁脚本
    Tool: Bash
    Steps:
      1. ./scripts/verify.sh | tee .sisyphus/evidence/task-4-verify.txt
    Expected Result: 输出末尾为 verify.sh: OK 且退出码为 0
    Evidence: .sisyphus/evidence/task-4-verify.txt
  ```

- [x] 5. 交付 benchmark APK：构建 + 安装 + 推送到手机 Download（保存证据）

  **What to do**:
  - 使用仓库脚本一键交付：
    - `export ADB_SERIAL=192.168.12.101:5555`
    - `adb connect "$ADB_SERIAL"`（若已连接可忽略错误）
    - `./scripts/deliver-benchmark.sh | tee .sisyphus/evidence/task-5-deliver-benchmark.txt`
  - 追加 ADB 断言（把输出追加到同一证据文件）：
    - `adb -s "$ADB_SERIAL" get-state`
    - `adb -s "$ADB_SERIAL" shell pm path cc.pscly.onememos`
    - `adb -s "$ADB_SERIAL" shell monkey -p cc.pscly.onememos -c android.intent.category.LAUNCHER 1`
    - `adb -s "$ADB_SERIAL" shell pidof cc.pscly.onememos`
    - `adb -s "$ADB_SERIAL" shell ls -lt /sdcard/Download/1memos-benchmark-*.apk | head -n 1`

  **Must NOT do**:
  - 不构建/交付 debug APK（只交付 benchmark）

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: 需要 ADB 环境与设备在线，且要保证交付闭环（构建+安装+push）
  - **Skills**: （无）

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 2
  - **Blocks**: Task 6
  - **Blocked By**: Task 4

  **References**:
  - `scripts/deliver-benchmark.sh` - 一键交付入口（build+install+push）
  - `scripts/build-benchmark-apk.sh` - 构建 benchmark 并生成时间戳副本（`copy-benchmark-apk.sh` 会打印最终路径）
  - `scripts/install-benchmark.sh` - 安装到设备（包名：`cc.pscly.onememos`）
  - `scripts/push-benchmark-to-download.sh` - push 到 `/sdcard/Download/` 并打印远端路径

  **Acceptance Criteria**:
  - [ ] `deliver-benchmark.sh: OK`
  - [ ] `adb -s 192.168.12.101:5555 get-state` 返回 `device`
  - [ ] `/sdcard/Download/` 下存在 `1memos-benchmark-*.apk`

  **QA Scenarios**:
  ```
  Scenario: 一键交付并做 ADB 断言
    Tool: Bash
    Preconditions: 本机可用 adb，设备可通过 192.168.12.101:5555 访问
    Steps:
      1. export ADB_SERIAL=192.168.12.101:5555
      2. adb connect "$ADB_SERIAL" || true
      3. ./scripts/deliver-benchmark.sh | tee .sisyphus/evidence/task-5-deliver-benchmark.txt
      4. {
           echo "\n--- ADB asserts ---";
           adb -s "$ADB_SERIAL" get-state;
           adb -s "$ADB_SERIAL" shell pm path cc.pscly.onememos;
           adb -s "$ADB_SERIAL" shell monkey -p cc.pscly.onememos -c android.intent.category.LAUNCHER 1;
           adb -s "$ADB_SERIAL" shell pidof cc.pscly.onememos;
           adb -s "$ADB_SERIAL" shell ls -lt /sdcard/Download/1memos-benchmark-*.apk | head -n 1;
         } | tee -a .sisyphus/evidence/task-5-deliver-benchmark.txt
    Expected Result: deliver OK；pm path 有输出；pidof 非空；Download 下存在 push 的 APK
    Evidence: .sisyphus/evidence/task-5-deliver-benchmark.txt
  ```

- [x] 6. 更新 `.ai_session.md` 并提交 git commit（包含本次 APK 路径）

  **What to do**:
  - 在仓库根目录 `.ai_session.md` 追加一段记录（按既有格式）：
    - 原始需求（锦囊复用隐藏 + 完善预览一致性）
    - 核心变更文件（至少列出 3 个 Kotlin 文件）
    - 验证：`./scripts/verify.sh` 证据路径
    - benchmark APK：从 Task 5 输出中复制时间戳 APK 路径（`app/build/outputs/apk/benchmark/YYYY-MM-DDTHH-MM-SS.apk`）
    - ADB：`ADB_SERIAL=192.168.12.101:5555`，以及 Download 远端路径
  - git 提交：
    - `git status` 确认仅包含预期文件
    - `git diff` 复核
    - `git commit -m "feat(collections): 锦囊复用自动标签元数据行隐藏"`

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 主要是文档记录 + git 提交流程
  - **Skills**: `git-master`
    - `git-master`: 保证提交原子性与信息规范（Conventional Commits）

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 2
  - **Blocks**: None
  - **Blocked By**: Task 5

  **Acceptance Criteria**:
  - [ ] `.ai_session.md` 已包含本次变更与最新 benchmark APK 路径
  - [ ] `git status` 显示工作区干净（已提交）

  **QA Scenarios**:
  ```
  Scenario: 提交前检查
    Tool: Bash
    Steps:
      1. { git status; echo; git diff; } | tee .sisyphus/evidence/task-6-git-status-and-diff.txt
    Expected Result: 变更仅限本计划相关文件
    Evidence: .sisyphus/evidence/task-6-git-status-and-diff.txt
  ```

---

## Final Verification Wave

- 跑完 Wave 2 后，再次确认：
  - Collections NOTE_REF 的 `MarkdownPreview(markdown = ...)` 不再直接使用 `memo.content`（当开关关闭时）
  - displayName 不再固定默认 `__Atags`，而是来自 settings 的关键字
  - `./scripts/verify.sh` 与 `./scripts/deliver-benchmark.sh` 都成功

---

## Commit Strategy

- 1 个提交即可（建议在 verify 通过后提交）：
  - `feat(collections): 锦囊复用自动标签元数据行隐藏`
  - 若修改点更偏修复，也可用 `fix(collections): ...`

---

## Success Criteria

- Collections NOTE_REF 预览与 displayName 逻辑均复用 Home 规则与 settings 配置（开关+关键字）
- `./scripts/verify.sh` 输出 `verify.sh: OK`
- 生成新的时间戳 benchmark APK（形如 `app/build/outputs/apk/benchmark/YYYY-MM-DDTHH-MM-SS.apk`）
- `ADB_SERIAL=192.168.12.101:5555 ./scripts/deliver-benchmark.sh` 成功，且手机 `/sdcard/Download/` 下存在 `1memos-benchmark-*.apk`
