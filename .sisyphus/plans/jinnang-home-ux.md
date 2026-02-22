# 计划：锦囊文件夹同主页预览 + 首页长按直进多选 + 锦囊内标签筛选

## TL;DR

> **Quick Summary**：把“锦囊/Collections 文件夹内的随笔引用（NOTE_REF）”升级为主页同款预览卡片（标签/图片/富预览/状态/时间），并把主页/已归档的长按改为“直接进入多选”；同时在锦囊页内支持点标签进行当前文件夹内筛选。
>
> **Deliverables**：
> - Collections：NOTE_REF 卡片改为主页同款信息结构；不再以“（无标题）/随笔引用”作为主要展示
> - Collections：标签 chips 可点击做“当前文件夹内、多标签 OR 筛选”，并有筛选条展示与清除
> - Home + Archived：长按直接进入多选并默认选中；每条卡片增加“…”按钮保留单条更多操作入口（墨迹卡片等）
>
> **Estimated Effort**：Medium
> **Parallel Execution**：YES（3 waves）
> **Critical Path**：Collections NOTE_REF 预览对齐 → 锦囊标签筛选 → Home/Archived 长按与“…”入口 → verify + benchmark APK

---

## Context

### Original Request
- “锦囊 还需要优化一下，打开那个文件夹的时候可以看到像是主页那样的预览，而不是无标题和随笔引用这种 的内容”
- “然后主页也优化一下，长按还可以是多选，而非只有分享和放入锦囊”

### Interview Summary（已确认）
- 本次范围只做两项优化（不做全量遗留收尾梳理）。
- 锦囊文件夹内 NOTE_REF：尽量做到主页卡片同款（标签/图片缩略图/富预览/底部状态+时间）。
- 锦囊标签：点标签在“锦囊当前文件夹内”筛选；支持多标签 OR；切换文件夹自动清空；文件夹项始终显示；面包屑下方显示筛选条（标签 chips + 清除）。
- NOTE_REF 标题行：仅当引用被手动命名时显示标题；未命名不显示标题行。
- 主页/已归档：长按直接进入多选并默认选中；每条卡片增加“…”按钮打开单条更多操作面板，保留墨迹卡片等入口。
- 测试策略：不新增自动化测试；以 `./scripts/verify.sh` + agent 可执行 QA 场景为准。

### Research Findings（代码定位）
- Collections：`feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsScreen.kt`（`CollectionItemCard()`、已存在 selectionMode + NOTE_REF 预览雏形）
- Collections：`feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsViewModel.kt`（`memoByRefTargetId` 的集中式缓存/加载，避免 N+1 Flow）
- Home：`feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeScreen.kt`（selection mode 底栏、`moreActionsTarget` bottom sheet、AddToCollectionsBatchDialog）
- Home 卡片：`feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/MemoItem.kt`（标签/图片缩略图/富预览/状态/时间/选择态 UI）
- 门禁与交付：`scripts/verify.sh`、`scripts/build-benchmark-apk.sh`（产出时间戳 benchmark APK）

### Metis Review（已吸收）
- 避免引入 `feature/collections` → `feature/home` 的 feature-to-feature 依赖；优先复制最小渲染规则或抽取到合适的共享层。
- 注意 Compose 列表性能：富文本 + 多图缩略图 + tags 可能掉帧；需要缓存、限制行数/图片数、滚动降级策略。
- 选择态下要避免卡片内部（tag/image/…）与“切换选中”手势冲突。
- 锦囊筛选只过滤 NOTE_REF、文件夹始终显示：需要明确分组渲染/过滤策略。

---

## Work Objectives

### Core Objective
让锦囊文件夹内的 NOTE_REF 像主页一样“可读、可扫、可筛选”，并让主页/已归档长按成为符合直觉的“进入多选”的主入口，同时保留单条更多操作入口。

### Definition of Done
- `./scripts/verify.sh` 通过
- `./scripts/build-benchmark-apk.sh` 成功并输出时间戳 APK 路径（形如 `app/build/outputs/apk/benchmark/YYYY-MM-DDTHH-MM-SS.apk`）
- QA 场景执行产出证据文件到 `.sisyphus/evidence/`

### Must Have
- Collections 文件夹内 NOTE_REF 卡片展示：标签 chips + 图片缩略图（如有）+ 预览正文（滚动时降级）+ 状态/时间；不再以“（无标题）/随笔引用”作为主内容。
- Collections：点击标签 chips 可在当前文件夹内筛选 NOTE_REF（多标签 OR），并可清除。
- Home/Archived：长按任一随笔卡片直接进入多选并选中该条；多选底栏动作继续可用（归档/恢复、放入锦囊、分享等）。
- Home/Archived：每条卡片提供“…”入口打开单条更多操作面板（墨迹卡片等仍可达）。

### Must NOT Have（Guardrails）
- 不扩展到“全项目遗留项收尾”（本计划只覆盖本次确认的两项优化 + 锦囊内标签筛选）。
- 不引入跨 feature 直接依赖（`feature/collections` 不直接引用 `feature/home` 的内部 composable）。
- 不在 LazyColumn item 内为每个条目单独订阅 Flow（保持 ViewModel 集中式加载/缓存策略）。
- 不做大规模 UI 体系重构（仅为满足本需求做最小可维护抽取/复用）。

---

## Verification Strategy（MANDATORY）

### Test Decision
- **Infrastructure exists**：YES（项目已有 JVM 单测与 lint/assemble 门禁，见 `scripts/verify.sh`）
- **Automated tests**：None（不新增测试）
- **Agent-Executed QA**：必须（使用命令 + adb/uiautomator（如环境可用）产出证据文件）

### Evidence Policy
- 证据统一写入：`.sisyphus/evidence/`
- 命名约定：`task-{N}-{scenario-slug}.{ext}`（例如 png / txt）

---

## Execution Strategy

### Parallel Execution Waves

Wave 1（可并行：交互契约与 UI 入口改造）
- T1 Home/Archived：长按直进多选 + “…”入口（保留更多操作面板）
- T2 Collections：筛选条 UI + 多标签 OR 状态管理（随 folder 变化自动清空）

Wave 2（可并行：Collections NOTE_REF 同主页预览卡片）
- T3 Collections：NOTE_REF 卡片渲染升级（标签/图片/预览/状态/时间 + selection/reorder 兼容）

Wave 3（集成 + 验证 + 交付）
- T4 端到端验证：verify.sh + benchmark APK + adb/uiautomator QA 证据
- T5 Git 提交与交付物汇总（含 APK 路径）

### Dependency Matrix（abbrev）
| Task | Depends On | Blocks |
|------|------------|--------|
| T1 | — | T4 |
| T2 | — | T3, T4 |
| T3 | T2 | T4 |
| T4 | T1, T3 | T5 |
| T5 | T4 | — |

---

## TODOs

- [ ] 1. Home/Archived：长按直接进入多选 + “…”保留单条更多操作

  **What to do**:
  - 调整主页与已归档列表的长按逻辑：长按任意随笔卡片 → 直接进入 selectionMode，并默认选中该条。
  - 为每条随笔卡片增加“…”按钮：点击“…”打开现有的“更多操作”面板（保留“墨迹卡片”等单条入口）。
  - 多选模式下：隐藏/禁用每条卡片的“…”按钮，避免“单条操作”与“批量操作”混淆。
  - 清理长按菜单里的“多选”入口（如仍存在）：避免入口重复（长按已是多选）。

  **Must NOT do**:
  - 不改变现有多选底栏批量动作的语义（归档/恢复、放入锦囊、分享合并文本）。
  - 不移除“墨迹卡片”能力，仅迁移入口。

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: Compose UI/交互为主，需要把手势与布局做稳定。
  - **Skills**: （可选）`frontend-ui-ux`
    - `frontend-ui-ux`: 用于把“多选/更多/底栏”交互做得更符合直觉（不做大改造）。
  - **Skills Evaluated but Omitted**:
    - `playwright`: Web 自动化不适用 Android。

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1（with Task 2）
  - **Blocks**: Task 4
  - **Blocked By**: None

  **References**:
  - `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeScreen.kt` - 当前 selectionMode、长按触发 `moreActionsTarget` bottom sheet 的入口；需要改为“长按直进多选”。
  - `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/MemoItem.kt` - 主页卡片的 InkCard 与 onLongClick 绑定点；适合加入“…”按钮并在 selectionMode 下禁用。

  **Acceptance Criteria**:
  - [ ] 长按任意随笔卡片后，立即进入多选态：TopAppBar 标题显示“已选 1”，底部批量操作栏出现。
  - [ ] 点击任意卡片右侧/底部的“…”按钮，可打开“更多操作”面板，且包含“墨迹卡片”等原有入口。
  - [ ] 进入多选态后，“…”按钮不再可用（隐藏或 disabled）。

  **QA Scenarios**:
  ```
  Scenario: Home 长按直进多选（Happy path）
    Tool: Bash (adb + uiautomator)
    Preconditions:
      - adb 可用且仅连接 1 台设备：`adb devices` 显示 exactly 1 device
      - 已安装 app（Debug 或 Benchmark 均可）
    Steps:
      1. 启动应用主界面（随笔列表）：`adb shell monkey -p cc.pscly.onememos 1`
      2. 在屏幕中部做一次“长按”（用 swipe 模拟长按）：
         - `adb shell wm size` 获取分辨率
         - `adb shell input swipe <x> <y> <x> <y> 800`
      3. Dump 当前 UI：`adb shell uiautomator dump /sdcard/uix.xml && adb pull /sdcard/uix.xml .sisyphus/evidence/task-1-home-enter-multiselect.xml`
      4. 断言：uix.xml 中包含“操作区”或“已选”（对应多选底栏/标题）
    Expected Result:
      - 进入多选态（count=1），底部批量操作栏可见
    Evidence:
      - .sisyphus/evidence/task-1-home-enter-multiselect.xml

  Scenario: “…”打开更多操作（Happy path）
    Tool: Bash (adb + uiautomator)
    Preconditions: 同上，且当前不在多选态
    Steps:
      1. 单击卡片内的“…”按钮（坐标点按，或在 uix.xml 中定位后点按）
      2. Dump UI：保存到 `.sisyphus/evidence/task-1-home-overflow.xml`
      3. 断言：包含“更多操作”与“墨迹卡片”文本
    Expected Result:
      - 更多操作面板弹出，原入口可达
    Evidence: .sisyphus/evidence/task-1-home-overflow.xml

  Scenario: 返回键退出多选（Edge case）
    Tool: Bash (adb + uiautomator)
    Preconditions: 已进入多选态
    Steps:
      1. `adb shell input keyevent 4`（BACK）
      2. Dump UI：`adb shell uiautomator dump /sdcard/uix.xml && adb pull /sdcard/uix.xml .sisyphus/evidence/task-1-home-exit-multiselect.xml`
      3. 断言：uix.xml 不再包含“操作区/已选”（多选态退出）
    Expected Result:
      - 退出多选态，回到普通列表浏览
    Evidence: .sisyphus/evidence/task-1-home-exit-multiselect.xml
  ```

- [ ] 2. Collections：当前文件夹内标签筛选（多标签 OR）+ 筛选条 UI

  **What to do**:
  - 在 Collections 页面增加 `selectedTags` 状态（Set<String>）。
  - 规则：
    - 多标签 OR；仅作用 NOTE_REF；文件夹条目始终显示。
    - folderId（`currentParentId`）变化时自动清空筛选。
  - 在面包屑下方渲染“筛选条”：显示已选 tags chips + “清除”按钮；点 chip 可取消该 tag。

  **Must NOT do**:
  - 不把筛选做成全局状态（只在 Collections 当前 folder 生命周期内）。
  - 不在 selectionMode / reorderMode 下允许切换筛选（避免交互冲突）。

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: Compose 状态管理 + UI 条带布局。
  - **Skills**: （可选）`frontend-ui-ux`
  - **Skills Evaluated but Omitted**:
    - `playwright`: 不适用。

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1（with Task 1）
  - **Blocks**: Task 3, Task 4
  - **Blocked By**: None

  **References**:
  - `feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsScreen.kt` - 当前 breadcrumb 与列表渲染位置；新增筛选条与过滤逻辑的主战场。
  - `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeScreen.kt` - 主页筛选状态条/标签交互可借鉴（但不要跨 feature 依赖）。

  **Acceptance Criteria**:
  - [ ] 点击任意 NOTE_REF 卡片上的 tag chip，会把该 tag 加入筛选集合，并在面包屑下方出现筛选条。
  - [ ] 多个 tag 同时选中时，列表按 OR 过滤 NOTE_REF；文件夹条目始终可见。
  - [ ] 切换到其它文件夹（进入/返回）后，筛选自动清空。

  **QA Scenarios**:
  ```
  Scenario: 锦囊内点标签后出现筛选条（Happy path）
    Tool: Bash (adb + uiautomator)
    Preconditions:
      - adb 可用，且锦囊内至少存在 1 条 NOTE_REF，且该 NOTE_REF 能渲染出 tag chip
    Steps:
      1. 打开锦囊页面并进入任意含 NOTE_REF 的文件夹
      2. 点按任意 tag chip
      3. Dump UI 到 `.sisyphus/evidence/task-2-collections-filterbar.xml`
      4. 断言：uix.xml 中出现“清除”与该 tag 文本
    Evidence: .sisyphus/evidence/task-2-collections-filterbar.xml

  Scenario: 切换文件夹后筛选清空（Edge case）
    Tool: Bash (adb + uiautomator)
    Steps:
      1. 在筛选开启状态下，进入/返回到其它文件夹
      2. Dump UI 到 `.sisyphus/evidence/task-2-collections-filter-reset.xml`
      3. 断言：筛选条不再出现（不含“清除”或不含已选 tag）
    Evidence: .sisyphus/evidence/task-2-collections-filter-reset.xml
  ```

- [ ] 3. Collections：NOTE_REF 同主页预览卡片（标签/图片/富预览/状态/时间）+ 手动命名标题行

  **What to do**:
  - 仅针对 NOTE_REF（`refType == MEMOS_MEMO`）且 `noteRefMemo` 可用时：把卡片渲染升级为“主页同款信息结构”。
    - tags chips：取 `memo.tags`，为空则用 `TagExtractor.extractAll(memo.content)`（与主页一致）。
    - 图片缩略图：复用主页的缩略图策略（最多 1-2 张，固定 size 解码，避免 scroll 路径高开销）。
    - 预览正文：滚动中显示纯文本 preview，停稳后切回 MarkdownPreview（同主页策略）。
    - 底部行：同步状态（仅本地/待同步/失败等）+ 时间。
  - 标题行规则：仅当 `item.name` 非空（手动命名）时展示一行标题；否则不展示“（无标题）”。
  - NOTE_REF 不可用/未同步时：给明确降级占位（不崩溃、不空白）。
  - 保持 Collections 原有 selectionMode 与 reorderMode 的 UI 控件可用（避免与卡片内部交互冲突）。

  **Must NOT do**:
  - 不引入 `feature/collections` → `feature/home` 的直接依赖（不直接复用 `MemoItem`）。
  - 不把 NOTE_REF 的 memo 加载改成 per-item Flow collect（继续用 ViewModel 的集中缓存）。

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: Compose 列表性能 + 复杂卡片布局（tags + 图片 + 富预览）。
  - **Skills**: （可选）`frontend-ui-ux`
  - **Skills Evaluated but Omitted**:
    - `playwright`: 不适用。

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 2（Sequential after Task 2）
  - **Blocks**: Task 4
  - **Blocked By**: Task 2

  **References**:
  - `feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsScreen.kt` - `CollectionItemCard()` 的 NOTE_REF 分支是主要改造点。
  - `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/MemoItem.kt` - 主页卡片渲染规则（tags/缩略图/预览/状态）需要对齐；可复制最小实现片段。
  - `feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsViewModel.kt` - `memoByRefTargetId` 加载策略；如需图片远程 URL，可从 settings 补充 serverBase（避免写死）。

  **Acceptance Criteria**:
  - [ ] NOTE_REF 卡片在“可预览”时展示：tag chips + 正文预览 +（如有）图片缩略图 + 状态/时间。
  - [ ] 未手动命名的 NOTE_REF 不显示“（无标题）”标题行。
  - [ ] 点击 tag chip 会触发当前文件夹筛选（与 Task 2 逻辑一致）。
  - [ ] selectionMode/reorderMode 下不会出现“点标签却触发筛选/点图片却打开预览”等误交互（优先按当前模式执行）。

  **QA Scenarios**:
  ```
  Scenario: NOTE_REF 卡片展示主页同款预览结构（Happy path）
    Tool: Bash (adb + uiautomator + screencap)
    Preconditions:
      - 锦囊文件夹内存在至少 1 条可预览的 NOTE_REF（本地可取到 memo）
    Steps:
      1. 打开对应锦囊文件夹
      2. `adb exec-out screencap -p > .sisyphus/evidence/task-3-collections-note-ref-preview.png`
      3. Dump UI：`.sisyphus/evidence/task-3-collections-note-ref-preview.xml`
      4. 断言：uix.xml 中包含至少 1 个 tag 文本、以及随笔正文片段（或占位“(无文字内容)”）
    Evidence:
      - .sisyphus/evidence/task-3-collections-note-ref-preview.png
      - .sisyphus/evidence/task-3-collections-note-ref-preview.xml

  Scenario: NOTE_REF 未加载/不可用时降级占位（Edge case）
    Tool: Bash (adb + uiautomator)
    Steps:
      1. 打开含“待同步/不可用引用”的锦囊文件夹
      2. Dump UI 到 `.sisyphus/evidence/task-3-collections-note-ref-fallback.xml`
      3. 断言：出现明确占位文案（如“引用内容不可用/待同步”），且不会崩溃
    Evidence: .sisyphus/evidence/task-3-collections-note-ref-fallback.xml
  ```

- [ ] 4. 集成验证：verify 门禁 + benchmark APK + 证据归档

  **What to do**:
  - 运行门禁：`./scripts/verify.sh`。
  - 构建并复制时间戳 benchmark APK：`./scripts/build-benchmark-apk.sh`（脚本会输出 apk 路径）。
  - 归档证据：保存 verify 输出与 APK 路径到 `.sisyphus/evidence/`。

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: （可选）无

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 3
  - **Blocks**: Task 5
  - **Blocked By**: Task 1, Task 3

  **References**:
  - `scripts/verify.sh` - 项目 Linux 门禁脚本（assembleDebug + unit tests + lintDebug + assembleBenchmark）。
  - `scripts/build-benchmark-apk.sh` - 构建 benchmark 并生成时间戳 APK 副本。

  **Acceptance Criteria**:
  - [ ] `./scripts/verify.sh` 输出 `verify.sh: OK`
  - [ ] `./scripts/build-benchmark-apk.sh` 输出时间戳 APK 路径，且文件存在

  **QA Scenarios**:
  ```
  Scenario: verify.sh 通过（Happy path）
    Tool: Bash
    Steps:
      1. `./scripts/verify.sh | tee .sisyphus/evidence/task-4-verify.txt`
      2. 断言：task-4-verify.txt 包含 `verify.sh: OK`
    Evidence: .sisyphus/evidence/task-4-verify.txt

  Scenario: benchmark APK 生成时间戳副本（Happy path）
    Tool: Bash
    Steps:
      1. `./scripts/build-benchmark-apk.sh | tee .sisyphus/evidence/task-4-benchmark-apk.txt`
      2. 从输出中解析最后一行 apk 路径并校验存在
    Evidence: .sisyphus/evidence/task-4-benchmark-apk.txt

  Scenario: adb 不可用时的降级验证（Edge case）
    Tool: Bash
    Steps:
      1. `adb devices | tee .sisyphus/evidence/task-4-adb-devices.txt || true`
      2. 若未检测到可用设备：在交付记录中标注“UI 自动化场景跳过（无 adb 设备）”，但仍必须完成 verify.sh + benchmark 构建
    Evidence: .sisyphus/evidence/task-4-adb-devices.txt
  ```

- [ ] 5. Git 提交 + 交付信息汇总

  **What to do**:
  - 确认仅包含本次相关改动文件；避免提交敏感信息/本地配置。
  - 生成 1-2 个原子 commit（建议按 Home 与 Collections 分开）。
  - 在交付说明中给出 benchmark APK 时间戳路径（来自 Task 4 输出）。

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `git-master`
    - `git-master`: 保障原子提交、避免把无关改动带入。

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 3
  - **Blocks**: Final Verification Wave
  - **Blocked By**: Task 4

  **References**:
  - `scripts/copy-benchmark-apk.sh` - 时间戳 APK 命名规则（`YYYY-MM-DDTHH-MM-SS.apk`）。

  **Acceptance Criteria**:
  - [ ] 工作区干净（除非有用户指定保留的未提交变更）
  - [ ] commit message 符合 Conventional Commits
  - [ ] 交付信息包含 benchmark APK 路径

  **QA Scenarios**:
  ```
  Scenario: 提交前后状态检查（Happy path）
    Tool: Bash
    Steps:
      1. `git status --porcelain=v1 | tee .sisyphus/evidence/task-5-git-status-before.txt`
      2. 完成 add/commit 后：`git status --porcelain=v1 | tee .sisyphus/evidence/task-5-git-status-after.txt`
      3. `git log -1 --oneline | tee .sisyphus/evidence/task-5-git-log.txt`
    Expected Result:
      - status-after.txt 为空（工作区干净）
    Evidence:
      - .sisyphus/evidence/task-5-git-status-before.txt
      - .sisyphus/evidence/task-5-git-status-after.txt
      - .sisyphus/evidence/task-5-git-log.txt

  Scenario: 提交物不包含敏感/禁入文件（Edge case）
    Tool: Bash
    Steps:
      1. `git diff --name-only HEAD~1..HEAD | tee .sisyphus/evidence/task-5-git-files.txt`
      2. 断言：不包含 `*.jks/*.keystore/*.pem/*.p12/local.properties`
    Evidence: .sisyphus/evidence/task-5-git-files.txt
  ```

---

## Final Verification Wave

- [ ] F1. 计划符合性审计（oracle）
- [ ] F2. 代码质量审查（unspecified-high）
- [ ] F3. QA 场景复跑与证据核对（unspecified-high）
- [ ] F4. 范围/污染检查（deep）

---

## Commit Strategy

- 建议 1-2 个原子提交：
  - `feat(ui): home long-press enter multiselect and memo overflow menu`
  - `feat(collections): home-like memo preview and tag filtering within folder`

---

## Success Criteria

### Verification Commands
```bash
./scripts/verify.sh
./scripts/build-benchmark-apk.sh
```

### Final Checklist
- [ ] Home/Archived：长按进入多选，且“…”可打开更多操作
- [ ] Collections：NOTE_REF 具备主页同款预览信息结构 + 标签筛选条可用
- [ ] verify.sh 通过
- [ ] benchmark APK 已生成并记录路径
