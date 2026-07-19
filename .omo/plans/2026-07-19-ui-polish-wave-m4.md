# M4：视觉收尾全量清债 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans，按任务逐项实现。步骤使用 checkbox（`- [ ]`）跟踪。
>
> **Momus 审阅就绪**：本计划为决策完备稿；实现代理不得发明设置 UI、不得把 Debug APK 当交付物、不得把 `BLOCKED_SIGNING_MISSING` / `SKIPPED_NO_DEVICE` 记为完成。

**Goal:** 在 **视觉零变化** 约束下完成 M4 剩余工作：Pre-B 令牌补齐 → Wave B 四泳道裸 dp 收敛 → C1 旧全量 Markdown 渲染退役（无设置 UI）→ C2 文档回写 → D 全门禁 + `1.13.0 (163)` + 时间戳 Benchmark APK + ADB 视觉 QA + AGENTS.md §8 远端发布闭环。

**Architecture:** 令牌层先补齐 `InkSpacing.X4` / `ContentMaxWidth`，再按 **模块路径零重叠** 四泳道并行替换 feature 裸 dp；结构常量可保留但必须中文注释。Markdown 退役只删 **全量** `MarkdownPaper` 渲染与 `useNewMarkdownEngine` 数据路径，**保留** 列表侧 `MarkdownPreview` 与纯文本工具函数（必要时先抽出再删全量渲染）。收口统一版本 `1.13.0 (163)`，仅固定签名 Benchmark 可进 latest Release。

**Tech Stack:** Kotlin, Jetpack Compose, Android Gradle, DataStore Preferences, mikepenz Markdown, commonmark（仅列表预览/纯文本）、`scripts/verify*.sh`、`scripts/build-benchmark-apk.sh`、`scripts/release-*.ps1`、ADB、GitHub Actions。

**目标子项目：** 仓库根（单应用 `cc.pscly.onememos`）。

---

## 0. 当前状态表（以仓库事实为准）

| 项 | 状态 | 证据 / 备注 |
| --- | --- | --- |
| 基线版本 | **完成** | `app/build.gradle.kts`：`versionCode = 162`，`versionName = "1.12.2"` |
| Wave A2 设计原语 | **完成** | `caecf83` — InkChip 焦点环 + `InkDisabledColors` + Seal*/InkChip 禁用视觉 |
| Wave A3 sharecard | **完成** | `4457877` — ShareCard 令牌 + 状态原语 |
| Wave A1 状态原语 | **完成** | `3ca78a7` — home 列表/对话框 + settings 的 RecordEditing / ReminderCalendar；**未改 AuthScreen** |
| Wave A 审计收口 | **完成** | `b00afbb` — `InkRetryBanner` 可空动作、`SyncBannerPolicy`、`InkShareCardPalette` |
| Wave A 强制验证 | **完成** | `:feature:home:testDebugUnitTest --rerun-tasks`、`InkStatePrimitivesTest`、`SettingsPrimitivesAccessibilityTest`、`:feature:sharecard:compileDebugKotlin` 均 PASS；`feature/` 零 `Color(0x…)`；Home 仅 2 处按钮级 `CircularProgressIndicator` 豁免 |
| AuthScreen 按钮转圈 | **豁免（非债务）** | `feature/auth/.../AuthScreen.kt` 按钮内 `CircularProgressIndicator` 保留；A1 **未**改 AuthScreen，后续亦不得以「状态原语」名义改动按钮加载态 |
| Pre-B 令牌 | **未开始** | `InkSpacing.kt` **无** `X4`、**无** `ContentMaxWidth`；已有 `TouchTargetMin = 48.dp`、`SheetGapL = 18.dp` |
| Wave B 裸 dp（main 约计，2026-07-19） | **未开始** | home≈69，settings≈155，auth≈15，welcome≈5，profile≈15，editor≈18，todo≈13，collections≈11，quickcapture≈16，sharecard≈17（A3 后可再扫，不阻塞 B） |
| C1 Markdown 退役 | **未开始** | `useNewMarkdownEngine` 仍在 `AppSettings` / DataStore 读 / `EditorUiState` / `EditorScreen` 分支；**无任何设置 UI 引用** |
| C2 文档 | **未开始** | `DESIGN.md` §5.6/§5.9/§8.2 仍写「开关并存 / 业务迁移部分保留」 |
| D 门禁 / 1.13.0 / Release | **未开始** | 完成 B+C 后统一 bump；签名缺失时 `BLOCKED_SIGNING_MISSING` **阻塞**远端正式发布，不得伪装完成 |

---

## 1. 铁律与映射规则（所有剩余波次强制）

### 1.1 视觉零变化

- 裸 dp / 裸圆角 **等值** 映射到已有或 Pre-B 新增令牌；禁止改布局逻辑、文案、导航、业务分支。
- 状态原语替换 **仅** 发生在 Wave A（已完成）；Wave B **禁止** 再改 loading/empty/error 行为。
- 禁止：改变任何可感知视觉结果；把 `0.dp` / `1.dp` 令牌化；顺手重构；跨泳道改文件。
- 注释与文档统一 **中文**；文件 **UTF-8 无 BOM**。

### 1.2 裸值 → 令牌映射表

| 裸值 | 令牌 | 说明 |
| --- | --- | --- |
| `2.dp` | `InkSpacing.X2` | 等值 |
| `4.dp` | **新增** `InkSpacing.X4 = 4.dp`（Pre-B） | 等值 |
| `6.dp` | `InkSpacing.X6` | 等值 |
| `8.dp` | `InkSpacing.X8` | 等值 |
| `10.dp` | `InkSpacing.X10` | 等值 |
| `12.dp` | `InkSpacing.X12` | 等值 |
| `14.dp` | `InkSpacing.X14` | 等值 |
| `16.dp` | `InkSpacing.X16` | 等值 |
| `18.dp` | `InkSpacing.SheetGapL` 或语义别名 | 按语义选，禁止 silently 改成别的尺度 |
| `20.dp` | `InkSpacing.X20` | 等值 |
| `24.dp` | `InkSpacing.X24` | 等值 |
| `48.dp` | `InkSpacing.TouchTargetMin` | 触控兜底 |
| `720.dp` | **新增** `InkSpacing.ContentMaxWidth = 720.dp`（Pre-B） | 宽屏内容上限 |
| `14.dp` 圆角 | `InkShape.RadiusL` / `InkShape.Card` 等语义形状 | 等值形状 |
| `12.dp` 圆角 | `InkShape.RadiusM` / `InkShape.Chip` | 等值形状 |
| `10.dp` 圆角 | `InkShape.RadiusS` / `InkShape.Tag` | 等值形状 |
| 非表尺寸（如 360/320/56/40/28/20 按钮转圈等） | 语义令牌 **或** 保留 + **中文注释** | 一次性布局/组件特有尺寸可保留；注释写清「结构常量，非间距尺度」 |

### 1.3 残留阈值

- 每个 Wave B 模块：`rg '\b\d+\.dp\b' feature/<mod>/src/main -g '*.kt'` 残留 **`< 20`**。
- 残留必须是 **结构常量** 且行内/上行有中文注释；不得残留「本可映射却未映射」的 4/8/12/16/20/24/48/720 等。
- `feature/` 全局：裸 `Color(0x` = **0**（已满足，回归不得回退）。
- 非按钮内 `CircularProgressIndicator` 不得新增；Auth 按钮内与 Home 两处按钮内豁免保留。

### 1.4 提交边界通用规则

- **禁止** 提交：`.omo/run-continuation/**`、密钥/证书、构建产物、本地环境文件。
- 每任务独立 commit（见各任务「Commit」）；message 用中文或 `type(scope): 中文说明`，与仓库近期风格一致。
- 每个 commit 前：`git diff --check` 无输出。

### 1.5 依赖总序

```text
Pre-B（串行）
  → Wave B1|B2|B3|B4（可并行，路径零重叠）
  → C1（串行，依赖 B 全部完成或至少不与 editor 并行改同一文件；推荐 B 全完成后）
  → C2（串行，依赖 C1）
  → D1 门禁 → D2 版本+Benchmark → D3 ADB QA → D4 §8 远端闭环
```

- `BLOCKED_SIGNING_MISSING`：可完成 D1–D3 的本地证据，但 **D4 正式 Release 阻塞**，不得勾选「发布完成」。
- `SKIPPED_NO_DEVICE`：D3 视觉 QA **阻塞**（不得勾选 M4 视觉验收完成）；可保留自动化门禁 PASS 记录，但交付清单须标 **阻塞**。

---

## 文件结构（剩余改动地图）

| 路径 | 职责 |
| --- | --- |
| `core/designsystem/.../theme/InkSpacing.kt` | Pre-B：`X4`、`ContentMaxWidth` |
| `feature/home/**` | B1 裸 dp |
| `feature/settings/**` | B2 裸 dp |
| `feature/auth/**` + `feature/welcome/**` + `feature/profile/**` | B3 裸 dp |
| `feature/editor/**` + `feature/todo/**` + `feature/collections/**` + `feature/quickcapture/**` | B4 裸 dp |
| `core/model/.../AppSettings.kt` | C1：删除 `useNewMarkdownEngine` |
| `core/data/.../SettingsRepositoryImpl.kt` | C1：删除 key 与读映射 |
| `feature/editor/.../EditorViewModel.kt` + `EditorScreen.kt` | C1：UiState/分支只走 Mikepenz |
| `core/designsystem/.../component/MarkdownPaper.kt` → 拆分/删除 | C1：删全量渲染，保留 Preview/纯文本 |
| `core/designsystem/.../markdown2/MikepenzMarkdown.kt` | C1：注释去「开关并存」 |
| `app/src/test/.../MarkdownPaperTest.kt` 或迁测 | C1：纯文本测试仍绿 |
| `DESIGN.md`、`.ai_session.md` | C2 + D 收口写回 |
| `app/build.gradle.kts` | D2：`1.13.0` / `163` |

---

## Task Pre-B: 串行补齐 `InkSpacing.X4` 与 `ContentMaxWidth`

**Depends on:** Wave A 完成（已满足）  
**Blocks:** Wave B 全部  
**Files:**
- Modify: `core/designsystem/src/main/java/cc/pscly/onememos/ui/theme/InkSpacing.kt`
- 禁止改：任何 `feature/**`

- [ ] **Step 1: 基线确认（FAIL 则说明 Pre-B 已做过，跳过新增）**

```bash
rg -n 'X4\b|ContentMaxWidth\b' core/designsystem/src/main/java/cc/pscly/onememos/ui/theme/InkSpacing.kt
```

Expected: **无匹配**（当前仓库事实）。若已存在等值定义 → 记录 SKIP 并进入 Wave B。

- [ ] **Step 2: 写入令牌（视觉零变化，仅新增常量）**

在 `object InkSpacing` 的「数值尺度」区段，于 `X2` 与 `X6` 之间插入：

```kotlin
val X4 = 4.dp // 紧凑间距（列表/图标间隙等值映射）
```

在「触控」区段 `TouchTargetMin` 附近追加：

```kotlin
val ContentMaxWidth = 720.dp // 宽屏内容最大宽度（设置等页）
```

- [ ] **Step 3: 编译**

```bash
./gradlew :core:designsystem:compileDebugKotlin --stacktrace
```

Expected: **PASS** `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit 边界**

```bash
git add core/designsystem/src/main/java/cc/pscly/onememos/ui/theme/InkSpacing.kt
git diff --check
git commit -m "$(cat <<'EOF'
feat(designsystem): M4 Pre-B 补齐 InkSpacing.X4 与 ContentMaxWidth

EOF
)"
```

**验收:**
- `InkSpacing.X4 == 4.dp`，`ContentMaxWidth == 720.dp`
- 无 feature 文件变更
- `git diff --check` 清洁

---

## Task B1: Wave B — `feature/home` 裸 dp 收敛

**Depends on:** Pre-B  
**Parallel with:** B2, B3, B4  
**Exclusive paths:** 仅 `feature/home/**`  
**Files（main，按需触及）:**
- `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeScreen.kt`
- `.../MemoItem.kt`
- `.../DateHeader.kt`
- `.../AddToCollectionsDialog.kt`
- `.../AddToCollectionsBatchDialog.kt`
- 其他 home main 中含 `\d+\.dp` 的文件

- [ ] **Step 1: 基线计数**

```bash
rg -n '\b\d+\.dp\b' feature/home/src/main -g '*.kt' | tee /tmp/m4-b1-before.txt | wc -l
rg -n 'Color\(0x' feature/home/src/main -g '*.kt' || true
rg -n 'CircularProgressIndicator' feature/home/src/main -g '*.kt'
```

Expected: 裸 dp 约 **69**；`Color(0x` **0**；CPI 仅 **2** 处按钮内（顶栏同步 / 批量操作）+ 可能的 import。

- [ ] **Step 2: 等值替换**

按 §1.2 映射；`import cc.pscly.onememos.ui.theme.InkSpacing`（及 `InkShape` 若改圆角）。  
**禁止** 改 CPI 豁免点的逻辑；按钮内 `20.dp`/`2.dp` 转圈尺寸可保留并中文注释「按钮加载态尺寸，结构常量」。

- [ ] **Step 3: 残留注释**

对无法映射的结构常量，示例：

```kotlin
Modifier = Modifier.size(56.dp) // 结构常量：宫格/缩略图边长，非间距尺度
```

- [ ] **Step 4: 门禁**

```bash
rg -n '\b\d+\.dp\b' feature/home/src/main -g '*.kt' | tee /tmp/m4-b1-after.txt | wc -l
# 残留须 < 20
./gradlew :feature:home:compileDebugKotlin :feature:home:testDebugUnitTest --stacktrace
```

Expected: 计数 **PASS** `<20`；Gradle **PASS**。

- [ ] **Step 5: Commit**

```bash
git add feature/home/
git diff --check
git commit -m "$(cat <<'EOF'
refactor(home): M4-B1 裸 dp 等值映射 InkSpacing/InkShape

EOF
)"
```

**验收:** 残留 `<20` 且均有中文结构注释；测试绿；零 `Color(0x`；两处按钮 CPI 仍在。

---

## Task B2: Wave B — `feature/settings` 裸 dp 收敛

**Depends on:** Pre-B  
**Parallel with:** B1, B3, B4  
**Exclusive paths:** 仅 `feature/settings/**`  
**Files（main 屏幕优先）:**
- `.../hub/SettingsHubScreen.kt`
- `.../appearance/AppearanceInteractionScreen.kt`
- `.../account/AccountSyncScreen.kt`、`AccountManagementScreen.kt`、`AdvancedSyncScreen.kt`
- `.../record/RecordEditingScreen.kt`
- `.../reminder/ReminderCalendarScreen.kt`
- `.../storage/StorageOfflineScreen.kt`
- `.../about/AboutAdvancedScreen.kt`
- 同模块其他含裸 dp 的 main 文件

- [ ] **Step 1: 基线**

```bash
rg -n '\b\d+\.dp\b' feature/settings/src/main -g '*.kt' | tee /tmp/m4-b2-before.txt | wc -l
rg -n '720\.dp|48\.dp' feature/settings/src/main -g '*.kt'
```

Expected: 约 **155**；应存在可映射的 `48.dp` / `720.dp`（若有）。

- [ ] **Step 2: 替换**

- `48.dp` → `InkSpacing.TouchTargetMin`
- `720.dp` → `InkSpacing.ContentMaxWidth`
- 其余按 §1.2

- [ ] **Step 3: 编译 + 单测（含无障碍矩阵）**

```bash
rg -n '\b\d+\.dp\b' feature/settings/src/main -g '*.kt' | tee /tmp/m4-b2-after.txt | wc -l
./gradlew :feature:settings:compileDebugKotlin :feature:settings:testDebugUnitTest --stacktrace
```

Expected: 残留 **`<20`**；**PASS**（含 `SettingsAccessibilityMatrixTest` 等既有测试）。

- [ ] **Step 4: Commit**

```bash
git add feature/settings/
git diff --check
git commit -m "$(cat <<'EOF'
refactor(settings): M4-B2 裸 dp 收敛（含 TouchTargetMin/ContentMaxWidth）

EOF
)"
```

**验收:** `<20` + 测试绿；**不**新增 Markdown 设置项；**不**改 DataStore schema。

---

## Task B3: Wave B — auth + welcome + profile

**Depends on:** Pre-B  
**Parallel with:** B1, B2, B4  
**Exclusive paths:** `feature/auth/**`、`feature/welcome/**`、`feature/profile/**`  
**Files:**
- `feature/auth/.../AuthScreen.kt`（**仅**间距/圆角令牌；**禁止**改按钮内 `CircularProgressIndicator`）
- `feature/welcome/.../WelcomeScreen.kt`
- `feature/profile/.../ProfileScreen.kt`（及 main 内其他含 dp 文件）

- [ ] **Step 1: 基线（分模块）**

```bash
for m in auth welcome profile; do
  echo "== $m =="
  rg -n '\b\d+\.dp\b' "feature/$m/src/main" -g '*.kt' | tee "/tmp/m4-b3-$m-before.txt" | wc -l
done
rg -n 'CircularProgressIndicator' feature/auth/src/main -g '*.kt'
```

Expected: auth≈15、welcome≈5、profile≈15；Auth CPI 仅按钮内。

- [ ] **Step 2: 等值替换 + 结构注释**

Auth 按钮转圈旁必须保留类似注释：

```kotlin
// 按钮内加载态：豁免状态原语；尺寸为结构常量
CircularProgressIndicator(modifier = Modifier.size(...), strokeWidth = ...)
```

- [ ] **Step 3: 门禁**

```bash
for m in auth welcome profile; do
  c=$(rg -n '\b\d+\.dp\b' "feature/$m/src/main" -g '*.kt' | wc -l)
  echo "$m residual=$c"
  test "$c" -lt 20 || exit 1
done
./gradlew :feature:auth:compileDebugKotlin :feature:welcome:compileDebugKotlin :feature:profile:compileDebugKotlin \
  :feature:profile:testDebugUnitTest --stacktrace
```

Expected: 每模块 **`<20`**；Gradle **PASS**（auth/welcome 若 NO-SOURCE 测试亦算 PASS）。

- [ ] **Step 4: Commit**

```bash
git add feature/auth/ feature/welcome/ feature/profile/
git diff --check
git commit -m "$(cat <<'EOF'
refactor(feature): M4-B3 auth/welcome/profile 裸 dp 等值映射

EOF
)"
```

**验收:** 三模块均 `<20`；Auth 按钮 CPI 仍在；**未**引入状态原语替换 Auth 按钮。

---

## Task B4: Wave B — editor + todo + collections + quickcapture

**Depends on:** Pre-B  
**Parallel with:** B1, B2, B3  
**Exclusive paths:** `feature/editor/**`、`feature/todo/**`、`feature/collections/**`、`feature/quickcapture/**`  
**注意:** C1 会再改 `EditorScreen.kt` / `EditorViewModel.kt`；B4 只做 dp/shape 令牌，**不**删 Markdown 分支。

- [ ] **Step 1: 基线**

```bash
for m in editor todo collections quickcapture; do
  echo "== $m =="
  rg -n '\b\d+\.dp\b' "feature/$m/src/main" -g '*.kt' | tee "/tmp/m4-b4-$m-before.txt" | wc -l
done
```

Expected: editor≈18、todo≈13、collections≈11、quickcapture≈16（已接近阈值，仍须清可映射项并注释残留）。

- [ ] **Step 2: 替换**

按 §1.2；`EditorScreen` 中预览区 padding 等可映射则映射；**保留** `useNewMarkdownEngine` 分支至 C1。

- [ ] **Step 3: 门禁**

```bash
for m in editor todo collections quickcapture; do
  c=$(rg -n '\b\d+\.dp\b' "feature/$m/src/main" -g '*.kt' | wc -l)
  echo "$m residual=$c"
  test "$c" -lt 20 || exit 1
done
./gradlew \
  :feature:editor:compileDebugKotlin :feature:editor:testDebugUnitTest \
  :feature:todo:compileDebugKotlin :feature:todo:testDebugUnitTest \
  :feature:collections:compileDebugKotlin :feature:collections:testDebugUnitTest \
  :feature:quickcapture:compileDebugKotlin \
  --stacktrace
```

Expected: 四模块 **`<20`**；编译/测试 **PASS**。

- [ ] **Step 4: Commit**

```bash
git add feature/editor/ feature/todo/ feature/collections/ feature/quickcapture/
git diff --check
git commit -m "$(cat <<'EOF'
refactor(feature): M4-B4 editor/todo/collections/quickcapture 裸 dp 收敛

EOF
)"
```

**验收:** 四模块 `<20`；Markdown 开关代码仍在（留给 C1）。

---

## Task B-Gate: Wave B 汇总扫描（串行，B1–B4 之后）

**Depends on:** B1, B2, B3, B4

- [ ] **Step 1: 全 feature 残留与回归**

```bash
for m in home settings auth welcome profile editor todo collections quickcapture sharecard; do
  dp=$(rg -n '\b\d+\.dp\b' "feature/$m/src/main" -g '*.kt' 2>/dev/null | wc -l)
  c=$(rg -n 'Color\(0x' "feature/$m/src/main" -g '*.kt' 2>/dev/null | wc -l)
  echo "$m bare_dp=$dp Color0x=$c"
  test "$dp" -lt 20 || exit 1
  test "$c" -eq 0 || exit 1
done
rg -n 'CircularProgressIndicator' feature -g '*.kt'
```

Expected: 每模块 bare_dp **`<20`**；Color0x **0**；CPI 仅 Auth 按钮 + Home 两处按钮（可加 import 行）。

- [ ] **Step 2: 记录（可选本地笔记，不强制 commit）**  
将 `/tmp/m4-b*-after.txt` 路径写入后续 `.ai_session.md` 草稿要点（真正写文件在 C2/D）。

**验收:** 上表全部 PASS，方可进入 C1。

---

## Task C1: 退役旧全量 `MarkdownPaper` 渲染 + 删除 `useNewMarkdownEngine`（无设置 UI）

**Depends on:** B-Gate（推荐）；**不得**与 B4 并行改 `EditorScreen.kt`  
**源码事实（强制）:**
- **不存在** Markdown 引擎设置 UI；禁止发明开关界面。
- 引用点：
  - `AppSettings.useNewMarkdownEngine`（`core/model/.../AppSettings.kt`）
  - DataStore：`Keys.USE_NEW_MARKDOWN_ENGINE` + 读映射（`SettingsRepositoryImpl.kt`）；**无**独立 update API 写入该字段（仅 prefs 读默认 true）
  - `EditorUiState.useNewMarkdownEngine` + ViewModel 映射（`EditorViewModel.kt`）
  - `EditorScreen.EditorMarkdownPreview(useNewEngine=...)` 分支（`EditorScreen.kt` ≈802–825）
  - 全量渲染：`MarkdownPaper` composable（`MarkdownPaper.kt`）
  - **必须保留：** `MarkdownPreview`（home/collections/profile 列表）、`markdownToPlainText` / `markdownToPlainPreview`、`MarkdownPaperTest` 所测行为
- 列表侧 **继续** 使用 `MarkdownPreview`（commonmark 预览路径可保留在同文件或抽出文件）

### C1-a 抽出保留 API（若全量渲染与 Preview 同文件耦合）

- [ ] **Step 1: 确认符号位置**

```bash
rg -n "fun MarkdownPaper|fun MarkdownPreview|fun markdownToPlainText|fun markdownToPlainPreview" \
  core/designsystem/src/main -g '*.kt'
rg -n "MarkdownPaper|MarkdownPreview|markdownToPlain" feature app -g '*.kt'
```

- [ ] **Step 2: 抽出（推荐新文件，避免删文件时误删 Preview）**

- Create: `core/designsystem/src/main/java/cc/pscly/onememos/ui/component/MarkdownPreview.kt`  
  迁入：`MarkdownPreview` + 其依赖的 parse/cache/内部类型中 **仅预览需要** 的部分。  
- Create: `core/designsystem/src/main/java/cc/pscly/onememos/ui/component/MarkdownPlainText.kt`  
  迁入：`markdownToPlainText`、`markdownToPlainPreview` 及纯文本所需 parser 依赖。  
- 若拆分成本过高：允许 **同文件删 `MarkdownPaper` composable 与仅全量路径**，但必须证明 `MarkdownPreview` 与纯文本函数仍可编译；优先拆分降低误删风险。

- [ ] **Step 3: 迁移测试位置（若包路径变）**

- 保持 `app/src/test/java/cc/pscly/onememos/ui/component/MarkdownPaperTest.kt` 可继续调用同包函数；或迁到 `core/designsystem/src/test/...` 并改 Gradle 测试依赖。  
- 测试断言不得弱化。

```bash
./gradlew :app:testDebugUnitTest --tests 'cc.pscly.onememos.ui.component.MarkdownPaperTest' --stacktrace
# 若测试迁到 designsystem：
# ./gradlew :core:designsystem:testDebugUnitTest --tests '*Markdown*' --stacktrace
```

Expected: **PASS**（3 个纯文本用例逻辑仍在）。

### C1-b 编辑器只走 Mikepenz

- [ ] **Step 4: `EditorScreen.kt`**

- 删除 `import ...MarkdownPaper`
- `EditorMarkdownPreview` 改为 **仅**：

```kotlin
@Composable
private fun EditorMarkdownPreview(
    content: String,
    modifier: Modifier = Modifier,
) {
    MikepenzMarkdown(
        markdownText = content,
        placeholder = "写点什么…",
        modifier = modifier,
    )
}
```

- 所有调用点去掉 `useNewEngine = uiState.useNewMarkdownEngine`
- 更新 KDoc：去掉「功能开关 / 旧引擎」表述

- [ ] **Step 5: `EditorViewModel.kt`**

- 删除 `EditorUiState.useNewMarkdownEngine`
- 删除 `useNewMarkdownEngine = settings.useNewMarkdownEngine` 映射

- [ ] **Step 6: `AppSettings.kt`**

- 删除字段：

```kotlin
// 删除整段：
// /** 是否使用新 Markdown 引擎 ... */
// val useNewMarkdownEngine: Boolean = true,
```

- [ ] **Step 7: `SettingsRepositoryImpl.kt`**

- 删除 `val USE_NEW_MARKDOWN_ENGINE = booleanPreferencesKey("use_new_markdown_engine")`
- 删除 `AppSettings(...)` 构造中的 `useNewMarkdownEngine = prefs[...] ?: true`
- DataStore 中历史 key 可残留磁盘（无读即忽略）；**不要**写迁移 UI

- [ ] **Step 8: 确认无设置 UI / 无残留引用**

```bash
rg -n "useNewMarkdownEngine|USE_NEW_MARKDOWN_ENGINE|use_new_markdown_engine" --glob '!**/build/**' --glob '!**/.omo/**'
rg -n "\bMarkdownPaper\b" --glob '!**/build/**' --glob '!**/.omo/**'
```

Expected:
- `useNewMarkdown*`：**0** 命中（文档将在 C2 更新前可能仍有 DESIGN.md 旧句 → C1 代码侧须 0；DESIGN.md 可留到 C2 同一提交或 C2 提交，但 **Kotlin 侧必须 0**）
- `\bMarkdownPaper\b`：仅允许出现在 **历史注释即将删除处** 或 **C2 文档**；**Kotlin 源码 0 个 composable 调用/定义**

更严：

```bash
rg -n "useNewMarkdownEngine|USE_NEW_MARKDOWN_ENGINE|use_new_markdown_engine" -g '*.kt' -g '*.kts'
rg -n "fun MarkdownPaper|\bMarkdownPaper\(" -g '*.kt'
```

Expected: **均无匹配**。

- [ ] **Step 9: 删除全量渲染实现**

- 删除 `MarkdownPaper` composable 及其 **仅** 全量阅读使用的代码路径。  
- 更新 `MikepenzMarkdown.kt` 文件头注释：改为「编辑器完整阅读唯一引擎」，删除「与 MarkdownPaper 并存 / useNewMarkdownEngine」。  
- `core/designsystem/build.gradle.kts` 注释同步（依赖 mikepenz 保留；commonmark 若 Preview/纯文本仍需要则 **保留** 依赖）。

- [ ] **Step 10: 编译与测试矩阵**

```bash
./gradlew \
  :core:model:compileDebugKotlin \
  :core:data:compileDebugKotlin \
  :core:designsystem:compileDebugKotlin \
  :core:designsystem:testDebugUnitTest \
  :feature:editor:compileDebugKotlin \
  :feature:editor:testDebugUnitTest \
  :feature:home:compileDebugKotlin \
  :feature:home:testDebugUnitTest \
  :feature:collections:compileDebugKotlin \
  :feature:profile:compileDebugKotlin \
  :app:testDebugUnitTest --tests 'cc.pscly.onememos.ui.component.MarkdownPaperTest' \
  --stacktrace
```

Expected: 全部 **PASS**（`MarkdownPaperTest` 名称可保留，测的是 plain 工具函数）。

- [ ] **Step 11: Commit**

```bash
git add \
  core/model/src/main/java/cc/pscly/onememos/domain/model/AppSettings.kt \
  core/data/src/main/java/cc/pscly/onememos/data/settings/SettingsRepositoryImpl.kt \
  core/designsystem/ \
  feature/editor/ \
  app/src/test/java/cc/pscly/onememos/ui/component/MarkdownPaperTest.kt
# 若有新建 MarkdownPreview.kt / MarkdownPlainText.kt 一并 add
git diff --check
git commit -m "$(cat <<'EOF'
refactor(markdown): M4-C1 退役全量 MarkdownPaper，编辑器固定 Mikepenz

EOF
)"
```

**验收:**
- 无 `useNewMarkdownEngine`（Kotlin）
- 无全量 `MarkdownPaper` composable
- `MarkdownPreview` 仍被 home/collections/profile 使用
- plain 测试 PASS
- **未**新增任何设置开关 UI

---

## Task C2: `DESIGN.md` + `.ai_session.md` 回写

**Depends on:** C1  
**Files:**
- Modify: `DESIGN.md`（§3.5 若涉及 TagChip 事实、§5.4、§5.6、§5.9、§8.2，并追加 M4 记录）
- Modify: `.ai_session.md`（文首新增 2026-07-19/20 M4 完成段）

- [ ] **Step 1: 更新 §5.6 Markdown**

改为事实：
- 编辑器完整阅读：**仅** `MikepenzMarkdown`
- 列表预览：仍 `MarkdownPreview`（commonmark 轻量）
- 纯文本：`markdownToPlainText` / `markdownToPlainPreview`
- **已删除** `useNewMarkdownEngine` 与全量 `MarkdownPaper`
- **无**用户设置开关

- [ ] **Step 2: 更新 §5.9 状态原语**

- 写明 Wave A 已接入：home 列表/对话框、settings 记录编辑与提醒日历、sharecard；  
- Auth **按钮内**转圈豁免  
- 引用提交：`3ca78a7`、`caecf83`、`4457877`、`b00afbb`

- [ ] **Step 3: 更新 §5.4 / 禁用与焦点（若 §8.2 交叉引用）**

- InkChip 焦点环 + 禁用视觉：**已核销**（A2）

- [ ] **Step 4: §8.2 债务表**

| 债务 | 新结论 |
| --- | --- |
| 状态原语业务迁移 | **已核销（M4-A 范围）**；Auth 按钮加载态明确豁免 |
| InkChip 焦点/禁用 | **已核销**（A2） |
| MarkdownPaper 双引擎 | **已核销（M4-C1）** |

- [ ] **Step 5: `.ai_session.md` 文首追加**

记录：Pre-B、B1–B4 残留计数、C1 删除清单、版本仍待 D2 或已 D2（若 C2 与 D2 分提交，C2 写「待 D2 bump」）。

- [ ] **Step 6: Commit**

```bash
git add DESIGN.md .ai_session.md
git diff --check
git commit -m "$(cat <<'EOF'
docs: M4-C2 DESIGN/会话回写（状态原语与 Markdown 退役事实）

EOF
)"
```

**验收:** 文档与代码一致；中文 UTF-8；无「请在设置中关闭旧引擎」类虚构描述。

---

## Task D1: 全量门禁

**Depends on:** C2（文档可与 D1 并行，但建议 C2 已提交）  
**不得** 在本任务 bump 版本。

- [ ] **Step 1: 架构**

```bash
./scripts/verify-architecture.sh
```

Expected: **PASS**（exit 0）

- [ ] **Step 2: 推荐总门禁**

```bash
./scripts/verify.sh
```

Expected: **PASS**  
若脚本过长，最小等价集：

```bash
./scripts/verify-architecture.sh
./gradlew \
  :core:designsystem:testDebugUnitTest \
  :feature:home:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  :feature:sharecard:compileDebugKotlin \
  :feature:editor:compileDebugKotlin \
  :feature:todo:testDebugUnitTest \
  :feature:collections:compileDebugKotlin \
  :feature:profile:testDebugUnitTest \
  :app:compileBenchmarkKotlin \
  lint \
  --stacktrace
```

Expected: **PASS**（lint 若仅 warning，按仓库既有门禁定义；**error 必须 0**）

- [ ] **Step 3: 包名不变量**

```bash
rg -n 'applicationId|namespace' app/build.gradle.kts
./scripts/verify-architecture.sh  # 内含 §10.1 包名字面量检查
```

Expected: 正式/benchmark **`cc.pscly.onememos`**；debug 后缀 `.dev` 仅 debug。

- [ ] **Step 4: 令牌回归扫描**

```bash
for m in home settings auth welcome profile editor todo collections quickcapture sharecard; do
  dp=$(rg -n '\b\d+\.dp\b' "feature/$m/src/main" -g '*.kt' | wc -l)
  test "$dp" -lt 20 || { echo "FAIL $m $dp"; exit 1; }
done
rg -n 'Color\(0x' feature -g '*.kt' && exit 1 || true
rg -n 'useNewMarkdownEngine|fun MarkdownPaper' -g '*.kt' && exit 1 || true
```

Expected: 全部 **PASS**

- [ ] **Step 5: Commit**  
无代码则 **不** 空提交。门禁失败 → **FAIL**，禁止进入 D2。

**验收:** D1 全部命令 exit 0。

---

## Task D2: 版本 `1.13.0 (163)` + 时间戳 Benchmark APK + 本地核验

**Depends on:** D1 PASS  
**Files:**
- Modify: `app/build.gradle.kts`（`versionCode = 163`，`versionName = "1.13.0"`）
- Modify: `.ai_session.md`（记录版本与 APK 路径）

- [ ] **Step 1: Bump**

```kotlin
versionCode = 163
versionName = "1.13.0"
```

- [ ] **Step 2: 签名环境探测**

```bash
if [ -n "${ANDROID_RELEASE_KEYSTORE_PATH:-}" ] && \
   [ -n "${ANDROID_RELEASE_STORE_PASSWORD:-}" ] && \
   [ -n "${ANDROID_RELEASE_KEY_ALIAS:-}" ] && \
   [ -n "${ANDROID_RELEASE_KEY_PASSWORD:-}" ]; then
  echo "SIGNING_OK"
else
  echo "BLOCKED_SIGNING_MISSING"
fi
```

- `SIGNING_OK`：继续正式签名 Benchmark，可进 D4。  
- `BLOCKED_SIGNING_MISSING`：本地仍可 `assembleBenchmark`（脚本可能回落 debug keystore），**产出仅作本地验证**，**禁止** 当作 §8 latest Release 资产；D4 标 **阻塞**。

- [ ] **Step 3: 构建时间戳 APK**

```bash
./scripts/build-benchmark-apk.sh
```

Expected: 最后一行打印类似：

```text
/root/1codes/xinliu_android/app/build/outputs/apk/benchmark/2026-07-19T12-34-56.apk
```

记为 `$APK`。**禁止** 交付 `app-debug.apk` 或 debug 包名产物。

- [ ] **Step 4: 包名 / 版本 / SHA-256 / 签名摘要**

```bash
# 需要 build-tools aapt/apksigner；版本以仓库 scripts 为准（release-verify 钉 36.0.0）
AAPT="${ANDROID_HOME:-$ANDROID_SDK_ROOT}/build-tools/36.0.0/aapt"
APKSIGNER="${ANDROID_HOME:-$ANDROID_SDK_ROOT}/build-tools/36.0.0/apksigner"
"$AAPT" dump badging "$APK" | rg "package: name=|versionCode=|versionName="
sha256sum "$APK"
"$APKSIGNER" verify --print-certs "$APK" | rg -i "SHA-256|Signer"
```

Expected **PASS** 条件：
- `name='cc.pscly.onememos'`（**无** `.dev`）
- `versionCode='163'`
- `versionName='1.13.0'`
- 固定发布证书 SHA-256（小写 hex）：  
  `58749c794f0c54af6b69bb6d80248a9fda0b75c687fde55b98d9575fc091633e`  
  - 匹配 → 签名 **PASS**，可进 D4  
  - 不匹配且环境为 `BLOCKED_SIGNING_MISSING` → 记录 **FAIL_FOR_RELEASE** / **BLOCKED_SIGNING_MISSING**（本地 APK 路径仍告知用户，但 **非** 发布完成）

PowerShell 等价（有 pwsh 时优先与仓库一致）：

```powershell
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$out = Join-Path $env:TMP "m4-apk-verify.json"
pwsh -NoProfile -NonInteractive -File .\scripts\release-verify-apk.ps1 -ApkPath $env:APK -OutputPath $out
# 检查 JSON ok=true；证书摘要与包名版本
```

- [ ] **Step 5: Commit（含版本，不含 APK 二进制）**

```bash
git add app/build.gradle.kts .ai_session.md
git diff --check
git commit -m "$(cat <<'EOF'
chore(release): bump 1.13.0 (163) M4 视觉收尾

EOF
)"
```

**禁止** `git add` APK、`app/build/**`、`.omo/run-continuation/**`。

**验收:** 版本文件已提交；`$APK` 路径有效；发布签名状态显式记录为 `SIGNING_OK` 或 `BLOCKED_SIGNING_MISSING`。

---

## Task D3: Android/ADB 视觉 QA 矩阵

**Depends on:** D2 本地 APK  
**语义:** `SKIPPED_NO_DEVICE` = **阻塞**，不是完成。

- [ ] **Step 1: 设备**

```bash
adb devices -l
```

Expected: 至少 1 台 `device`。  
若无：输出 **`SKIPPED_NO_DEVICE`**，停止勾选 D3/M4 视觉完成，写入 `.ai_session.md` 阻塞项。

- [ ] **Step 2: 安装 Benchmark**

```bash
# 单设备
./scripts/install-benchmark.sh
# 或多设备：
# ADB_SERIAL=<serial> ./scripts/install-benchmark.sh
# 签名冲突时（会清数据，需确认）：
# ADB_SERIAL=<serial> ./scripts/install-benchmark.sh --force-uninstall
```

Expected: 安装 **PASS**；包名 `cc.pscly.onememos`。

- [ ] **Step 3: 截图矩阵（每屏至少 1 张）**

```bash
mkdir -p /tmp/m4-visual-qa
SERIAL=$(adb devices | awk 'NR==2{print $1}')
shot() {
  local name="$1"
  adb -s "$SERIAL" shell screencap -p /sdcard/m4-"$name".png
  adb -s "$SERIAL" pull /sdcard/m4-"$name".png "/tmp/m4-visual-qa/$name.png"
}
# 人工导航到下列目的地后执行 shot：
# home / editor_read / settings_appearance / sharecard / collections / auth_optional
```

| 场景 | 操作要点 | Expected |
| --- | --- | --- |
| 主页 | 列表 + 同步横幅（若有） | 与 1.12.2 无布局漂移；状态原语风格统一 |
| 编辑器只读 | 打开含标题/列表/代码/链接的 memo | 仅 Mikepenz；无旧引擎差异开关 |
| 设置外观 | 主题/阅读相关页 | 间距令牌化后像素等值；宽屏 maxWidth 行为不变 |
| 分享卡 | 进入分享卡预览 | 与 A3 一致；无裸色回归 |
| 锦囊 | 列表 + MarkdownPreview | 预览仍在；无崩溃 |
| 登录页（可选） | 触发按钮 loading | 按钮转圈仍在（豁免） |

- [ ] **Step 4: 判定**

- 任一眼可见回归 → **FAIL**，修回 Wave B/C，禁止 D4。  
- 无设备 → **`SKIPPED_NO_DEVICE`（阻塞）**。  
- 全场景通过 → **PASS**，路径列表写入 `.ai_session.md`。

**验收:** PASS 才允许宣称「视觉 QA 完成」；阻塞态必须原样上报。

---

## Task D4: AGENTS.md §8 远端发布闭环

**Depends on:** D1 PASS + D2 `SIGNING_OK` + D3 PASS（或书面接受设备阻塞时 **仍不得** 发 latest？→ **本计划要求：无固定签名不得 Release；无设备不得宣称视觉完成，但签名与 Actions 通过时 Release 仍须 APK 核验**）  
**硬性:** 仅固定签名 Benchmark APK；非草稿、非预发布、latest；Tag `v1.13.0`。

- [ ] **Step 0: 阻塞检查**

```text
若 BLOCKED_SIGNING_MISSING → 停止 D4，状态=阻塞，不得创建 latest Release，不得用 debug 签名冒充。
若证书 SHA-256 ≠ 58749c794f0c54af6b69bb6d80248a9fda0b75c687fde55b98d9575fc091633e → 停止。
```

- [ ] **Step 1: 提交已在 main 所需提交链上；push main**

```bash
git status
git push origin main
```

Expected: **PASS** fast-forward；**禁止** force-push main。

- [ ] **Step 2:  annotated Tag `v1.13.0`**

```bash
git tag -a v1.13.0 -m "v1.13.0 M4 视觉收尾"
git push origin v1.13.0
```

Expected: **PASS**；Tag 指向含 `163` 的提交。

- [ ] **Step 3: 等待 GitHub Actions（该 Tag）成功**

```bash
gh run list --branch v1.13.0 --limit 5
# 或
gh run watch
```

Expected: 与 `v1.13.0` 关联的 Android workflow **success**。失败 → **FAIL**，修后按仓库恢复策略，不得跳过。

- [ ] **Step 4: 核验 Actions Artifact**

- 下载 Tag run 的 Benchmark Artifact  
- 再次跑包名/版本/证书/APK SHA-256（同 D2 Step 4）  
- 必须与固定签名一致

- [ ] **Step 5: 发布非草稿非预发布 latest Release**

```bash
gh release create v1.13.0 \
  --title "v1.13.0" \
  --notes "M4 视觉收尾：令牌收敛、状态原语收口、Markdown 全量旧引擎退役。" \
  --latest \
  "$APK_FIXED_SIGNATURE"
```

或使用：

```powershell
pwsh -NoProfile -NonInteractive -File .\scripts\release-publish.ps1 -Stage PublishRelease -OutputPath $out
# 再 VerifyRelease / Cleanup；每步检查 ok=true
```

Expected:
- `prerelease=false`、`draft=false`、latest  
- **仅** 一个已核验 Benchmark APK  
- **禁止** 只传 Actions Artifact 链接而不建 Release

- [ ] **Step 6: 下载 Release 资产复核**

```bash
gh release download v1.13.0 -D /tmp/m4-release-dl
# 对下载文件重复 aapt/apksigner/sha256sum
```

Expected: 与 Step 4 一致 → **PASS**。

- [ ] **Step 7: 会话写回**

更新 `.ai_session.md`：Release URL、APK SHA-256、证书摘要、Actions run URL。  
Commit + push（若仅文档）：

```bash
git add .ai_session.md
git commit -m "$(cat <<'EOF'
docs(session): 记录 v1.13.0 M4 发布闭环

EOF
)"
git push origin main
```

**验收（全部满足才算 D4 完成）:**
1. main 已推送  
2. `v1.13.0` annotated tag 已推送  
3. Tag Actions **success**  
4. latest Release 非草稿非预发布  
5. 资产为固定签名 Benchmark，包名/163/1.13.0/证书摘要通过  
6. 自 Release 下载复核 PASS  

任一缺失 → **未完成**，不得在状态表标「发布完成」。

---

## 总验收清单（M4 Done）

| # | 条件 | 判定 |
| --- | --- | --- |
| 1 | Wave A 四提交在历史上：`caecf83` `4457877` `3ca78a7` `b00afbb` | 已完成 |
| 2 | Pre-B：`X4` + `ContentMaxWidth` | PASS/FAIL |
| 3 | 各 feature 模块 main 裸 dp `<20` 且结构常量中文注释 | PASS/FAIL |
| 4 | `feature/` 零 `Color(0x` | PASS/FAIL |
| 5 | CPI：仅 Auth 按钮 + Home 两处按钮豁免 | PASS/FAIL |
| 6 | 无 `useNewMarkdownEngine`；无全量 `MarkdownPaper`；Preview+plain 保留且测试绿 | PASS/FAIL |
| 7 | 无虚构 Markdown 设置 UI | PASS/FAIL |
| 8 | `DESIGN.md` / `.ai_session.md` 与代码一致 | PASS/FAIL |
| 9 | `verify-architecture` + 模块测试 / `verify.sh` | PASS/FAIL |
| 10 | 版本 `1.13.0 (163)`；时间戳 Benchmark 路径已告知 | PASS/FAIL |
| 11 | 包名 `cc.pscly.onememos` + 签名策略正确标注 | PASS / BLOCKED_SIGNING_MISSING |
| 12 | ADB 截图矩阵 | PASS / SKIPPED_NO_DEVICE（阻塞） |
| 13 | §8 闭环 | PASS / 阻塞原因原文 |

---

## 自检（写计划时）

1. **Spec 覆盖:** Pre-B、四泳道 B、纠正后的 C1、C2、D1–D4、零变化、Auth 豁免、无设置 UI、Benchmark 非 Debug、阻塞语义 → 均有任务。  
2. **无空话:** 命令带 Expected；无「随便看看」「跑一下测试」级模糊句。  
3. **类型/路径一致:** `AppSettings` / `SettingsRepositoryImpl` / `EditorUiState` / `EditorScreen` / `MarkdownPreview` 与仓库一致。  
4. **版本一致:** 仅 `1.13.0` / `163`。  

---

## 执行交接

计划已就绪，路径：`.omo/plans/2026-07-19-ui-polish-wave-m4.md`。

**执行选项：**
1. **Subagent-Driven（推荐）** — 每任务新代理 + 任务间审查  
2. **Inline Execution** — 本会话按 executing-plans 分批检查点执行  

实现阶段再选；**本文仅重写计划，不改业务代码、不跑构建、不做 Git 写入（除计划文件本身）**。
