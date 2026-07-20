# M6 悬浮层/壳层/feature 令牌收敛波 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把悬浮速记层、应用壳层与全部 feature 残留裸 dp/裸色/裸形状收敛进 InkSpacing/InkShape/InkBorder，悬浮层对齐纸墨原语，并以 1.15.0 (165) 完成 AGENTS.md §8 完整发布闭环。

**Architecture:** 先令牌（TDD 锁定字面量）→ 后调用点逐模块等值映射；悬浮层输入框换 ScrollTextField 是唯一意图视觉变化；feature 零视觉漂移（值相等才映射）。规格：`docs/superpowers/specs/2026-07-20-overlay-shell-token-wave-design.md`。

**Tech Stack:** Kotlin / Jetpack Compose (BOM 2026.06.00) / JUnit4 / Gradle Wrapper / adb 192.168.12.101:5555。

**环境约定：** Linux；构建用 `./gradlew`（不要 `.\gradlew.bat`）；每次 Bash 前先 `[Console]::OutputEncoding` 不需要（非 Windows）；所有文件 UTF-8 无 BOM；注释用中文。

---

### Task 1: InkSpacing 新尺度 + 语义别名（TDD）

**Files:**
- Test: `core/designsystem/src/test/java/cc/pscly/onememos/ui/theme/InkSpacingTest.kt`
- Modify: `core/designsystem/src/main/java/cc/pscly/onememos/ui/theme/InkSpacing.kt`

- [ ] **Step 1: 扩展失败测试**

在 `InkSpacingTest` 类内追加两个测试方法（保持既有 import）：

```kotlin
    @Test
    fun m6NewScales_areExact() {
        assertEquals(18.dp, InkSpacing.X18)
        assertEquals(22.dp, InkSpacing.X22)
        assertEquals(26.dp, InkSpacing.X26)
        assertEquals(28.dp, InkSpacing.X28)
        assertEquals(30.dp, InkSpacing.X30)
        assertEquals(54.dp, InkSpacing.X54)
        assertEquals(64.dp, InkSpacing.X64)
        assertEquals(68.dp, InkSpacing.X68)
        assertEquals(76.dp, InkSpacing.X76)
        assertEquals(80.dp, InkSpacing.X80)
        assertEquals(84.dp, InkSpacing.X84)
        assertEquals(92.dp, InkSpacing.X92)
        assertEquals(108.dp, InkSpacing.X108)
        assertEquals(120.dp, InkSpacing.X120)
        assertEquals(140.dp, InkSpacing.X140)
        assertEquals(320.dp, InkSpacing.X320)
        assertEquals(324.dp, InkSpacing.X324)
        assertEquals(360.dp, InkSpacing.X360)
        assertEquals(380.dp, InkSpacing.X380)
        assertEquals(420.dp, InkSpacing.X420)
        assertEquals(520.dp, InkSpacing.X520)
        assertEquals(600.dp, InkSpacing.X600)
    }

    @Test
    fun m6Aliases_referenceScales() {
        assertEquals(InkSpacing.X18, InkSpacing.SheetGapL)
        assertEquals(InkSpacing.X84, InkSpacing.OverlayThumbSize)
        assertEquals(InkSpacing.X28, InkSpacing.OverlayThumbBadgeSize)
        assertEquals(InkSpacing.X140, InkSpacing.OverlayImeLiftMax)
        assertEquals(0.35f, InkSpacing.OverlayImeLiftFactor, 0.0001f)
        assertEquals(InkSpacing.X120, InkSpacing.OverlayInputMinHeight)
        assertEquals(InkSpacing.X324, InkSpacing.OverlayInputMaxHeight)
        assertEquals(InkSpacing.X22, InkSpacing.ShareCardMarginX)
        assertEquals(InkSpacing.X26, InkSpacing.ShareCardLineGap)
        assertEquals(InkSpacing.X54, InkSpacing.ShareCardPaddingH)
        assertEquals(InkSpacing.X56, InkSpacing.ShareCardPaddingV)
        assertEquals(InkSpacing.X92, InkSpacing.ShareCardSealSize)
        assertEquals(InkSpacing.X108, InkSpacing.ShareCardImageSize)
        assertEquals(InkSpacing.X520, InkSpacing.ShareCardQuoteBarHeight)
        assertEquals(InkSpacing.X360, InkSpacing.ShareCardPreviewHeight)
        assertEquals(InkSpacing.X80, InkSpacing.ShareCardThemesTopPadding)
        assertEquals(InkSpacing.X4, InkSpacing.ShareCardElevation)
        assertEquals(InkSpacing.X84, InkSpacing.AttachmentThumbSize)
        assertEquals(InkSpacing.X76, InkSpacing.SingleImageThumbSize)
        assertEquals(InkSpacing.X88, InkSpacing.GridImageThumbSize)
        assertEquals(InkSpacing.X320, InkSpacing.DialogListMaxHeight)
        assertEquals(InkSpacing.X520, InkSpacing.SheetListMaxHeight)
        assertEquals(InkSpacing.X420, InkSpacing.UpdateDialogNotesMaxHeight)
        assertEquals(InkSpacing.X360, InkSpacing.CollectionsDialogMaxHeight)
        assertEquals(InkSpacing.X18, InkSpacing.SkeletonTextLineHeight)
        assertEquals(InkSpacing.X600, InkSpacing.TwoColumnMinWidth)
        assertEquals(InkSpacing.X380, InkSpacing.ProfileCalendarHeight)
        assertEquals(InkSpacing.X68, InkSpacing.EditorRowEndPadding)
        assertEquals(InkSpacing.X30, InkSpacing.TodoStatusIconSize)
        assertEquals(InkSpacing.X44, InkSpacing.CalendarCellMin)
        assertEquals(InkSpacing.X56, InkSpacing.CalendarCellMax)
        assertEquals(InkSpacing.X34, InkSpacing.CalendarDaySize)
    }
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests "cc.pscly.onememos.ui.theme.InkSpacingTest" -Pkotlin.compiler.execution.strategy=in-process`
Expected: 编译失败（`Unresolved reference: X18` 等），即 RED。

- [ ] **Step 3: 写入新尺度**

在 `InkSpacing.kt` 数值尺度区按升序插入（每个字面量全文件唯一）：

```kotlin
    val X18 = 18.dp // CPI 尺寸 / 骨架屏正文行高（SheetGapL 同源）
    val X22 = 22.dp // 分享卡画布边距 / 锦囊分隔
    val X26 = 26.dp // 分享卡行距
    val X28 = 28.dp // 附件角标与移除钮
    val X30 = 30.dp // 待办状态圈
    val X54 = 54.dp // 分享卡画布水平内边距
    val X64 = 64.dp // 骨架屏块宽
    val X68 = 68.dp // 编辑器行尾留白
    val X76 = 76.dp // 锦囊单图缩略
    val X80 = 80.dp // 分享卡顶部留白
    val X84 = 84.dp // 附件缩略图边长
    val X92 = 92.dp // 分享卡印章
    val X108 = 108.dp // 分享卡图
    val X120 = 120.dp // 悬浮层输入框最小高（≈3 行 LinePitch + 纸面留白）
    val X140 = 140.dp // 悬浮层 IME 抬升上限
    val X320 = 320.dp // 收藏弹窗列表最大高
    val X324 = 324.dp // 悬浮层输入框最大高（≈10 行 LinePitch + 纸面留白）
    val X360 = 360.dp // 分享卡预览高 / 锦囊弹窗高
    val X380 = 380.dp // 个人中心日历高
    val X420 = 420.dp // 更新弹窗内容最大高
    val X520 = 520.dp // 分享卡引用竖条高 / 待办弹层列表高
    val X600 = 600.dp // 首页双列断点
```

并把 `val SheetGapL = 18.dp` 改为 `val SheetGapL = X18`（去重，注释保留并补“同源 X18”）。

- [ ] **Step 4: 写入语义别名区段**

在「状态原语」区段之后、「触控」区段之前插入：

```kotlin
    // ---------- 语义别名：悬浮速记层（M6） ----------
    val OverlayThumbSize = X84 // 附件缩略图边长
    val OverlayThumbBadgeSize = X28 // 附件角标 / 移除钮尺寸
    val OverlayImeLiftMax = X140 // 键盘抬升上限
    const val OverlayImeLiftFactor = 0.35f // 键盘高度抬升比例
    val OverlayInputMinHeight = X120 // 输入框最小高
    val OverlayInputMaxHeight = X324 // 输入框最大高

    // ---------- 语义别名：分享卡画布（M6） ----------
    val ShareCardMarginX = X22
    val ShareCardLineGap = X26
    val ShareCardPaddingH = X54
    val ShareCardPaddingV = X56
    val ShareCardSealSize = X92
    val ShareCardImageSize = X108
    val ShareCardQuoteBarHeight = X520
    val ShareCardPreviewHeight = X360
    val ShareCardThemesTopPadding = X80
    val ShareCardElevation = X4 // 画布卡片投影（全库唯一使用投影处）

    // ---------- 语义别名：附件 / 图片缩略（M6） ----------
    val AttachmentThumbSize = X84 // 编辑器附件缩略（与悬浮层同值）
    val SingleImageThumbSize = X76 // 锦囊单图缩略
    val GridImageThumbSize = X88 // 锦囊多图缩略

    // ---------- 语义别名：弹窗 / 弹层（M6） ----------
    val DialogListMaxHeight = X320 // 收藏弹窗列表
    val SheetListMaxHeight = X520 // 待办弹层列表
    val UpdateDialogNotesMaxHeight = X420 // 更新弹窗说明
    val CollectionsDialogMaxHeight = X360 // 锦囊弹窗

    // ---------- 语义别名：骨架屏 / 布局（M6） ----------
    val SkeletonTextLineHeight = X18 // 骨架屏正文行高（一次性占位几何）
    val TwoColumnMinWidth = X600 // 首页双列断点（自 HomeScreen 迁入）
    val ProfileCalendarHeight = X380 // 个人中心日历高
    val EditorRowEndPadding = X68 // 编辑器行尾留白
    val TodoStatusIconSize = X30 // 待办状态圈
    val CalendarCellMin = X44 // 日历单元格最小边长
    val CalendarCellMax = X56 // 日历单元格最大边长
    val CalendarDaySize = X34 // 日历日号内圈
```

- [ ] **Step 5: 运行测试确认通过**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests "cc.pscly.onememos.ui.theme.InkSpacingTest" -Pkotlin.compiler.execution.strategy=in-process`
Expected: BUILD SUCCESSFUL，全部断言 PASS。

- [ ] **Step 6: Commit**

```bash
git add core/designsystem/src/main/java/cc/pscly/onememos/ui/theme/InkSpacing.kt core/designsystem/src/test/java/cc/pscly/onememos/ui/theme/InkSpacingTest.kt
git commit -m "feat(designsystem): M6 InkSpacing 22 尺度与悬浮层/分享卡语义别名"
```

---

### Task 2: InkShape + InkBorder 新增（TDD）

**Files:**
- Test: `core/designsystem/src/test/java/cc/pscly/onememos/ui/theme/InkShapeBorderTokenTest.kt`（新建）
- Modify: `core/designsystem/src/main/java/cc/pscly/onememos/ui/theme/InkShape.kt`
- Modify: `core/designsystem/src/main/java/cc/pscly/onememos/ui/theme/InkBorder.kt`

- [ ] **Step 1: 新建失败测试**

```kotlin
package cc.pscly.onememos.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [InkShape] / [InkBorder] M6 新增令牌的精确值回归。
 */
class InkShapeBorderTokenTest {
    @Test
    fun newShapeRadii_areExact() {
        assertEquals(8.dp, InkShape.RadiusXss)
        assertEquals(18.dp, InkShape.RadiusXl)
        assertEquals(3.dp, InkShape.RadiusMicro)
    }

    @Test
    fun semanticShapes_areExact() {
        assertEquals(RoundedCornerShape(8.dp), InkShape.Skeleton)
        assertEquals(RoundedCornerShape(8.dp), InkShape.CanvasSub)
        assertEquals(RoundedCornerShape(18.dp), InkShape.SkeletonCard)
        assertEquals(RoundedCornerShape(3.dp), InkShape.Legend)
        assertEquals(RoundedCornerShape(percent = 50), InkShape.Pill)
        assertEquals(RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50), InkShape.PillStart)
        assertEquals(RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50), InkShape.PillEnd)
    }

    @Test
    fun newBorderStrokes_areExact() {
        assertEquals(3.dp, InkBorder.CanvasStroke)
        assertEquals(1.6.dp, InkBorder.CalendarRing)
        assertEquals(2.dp, InkBorder.SpinnerStroke)
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests "cc.pscly.onememos.ui.theme.InkShapeBorderTokenTest" -Pkotlin.compiler.execution.strategy=in-process`
Expected: 编译失败（Unresolved reference），即 RED。

- [ ] **Step 3: InkShape 追加**

`InkShape.kt` 圆角尺度区追加（紧跟 RadiusXs 之后）：

```kotlin
    val RadiusXl = 18.dp // 首页骨架卡片 / 横幅（M6）
    val RadiusXss = 8.dp // 骨架屏占位块、分享卡画布子面（M6）
    val RadiusMicro = 3.dp // 个人中心图例色条（M6）
```

语义形状区（`QuoteBar` 之后、`sealFor` 之前）追加：

```kotlin
    val Skeleton = RoundedCornerShape(RadiusXss) // 骨架屏占位块
    val CanvasSub = RoundedCornerShape(RadiusXss) // 分享卡画布子面
    val SkeletonCard = RoundedCornerShape(RadiusXl) // 首页骨架卡片 / 横幅
    val Legend = RoundedCornerShape(RadiusMicro) // 图例色条
    /** 胶囊：替代 RoundedCornerShape(999.dp)；使用点元素尺寸均 <200dp，视觉等价 */
    val Pill = RoundedCornerShape(percent = 50)
    val PillStart = RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50) // 日历连选左端
    val PillEnd = RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50) // 日历连选右端
```

- [ ] **Step 4: InkBorder 追加**

描边宽度区（`TableCell` 之后）追加：

```kotlin
    val CanvasStroke = 3.dp // 分享卡画布竖线描边（M6）
    val CalendarRing = 1.6.dp // 个人中心日历今日描边（M6）
    val SpinnerStroke = Stamp // 按钮加载态 CPI 描边（复用 Stamp 值，不重复字面量）
```

- [ ] **Step 5: 运行确认通过**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests "cc.pscly.onememos.ui.theme.*" -Pkotlin.compiler.execution.strategy=in-process`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 6: Commit**

```bash
git add core/designsystem/src/main/java/cc/pscly/onememos/ui/theme/InkShape.kt core/designsystem/src/main/java/cc/pscly/onememos/ui/theme/InkBorder.kt core/designsystem/src/test/java/cc/pscly/onememos/ui/theme/InkShapeBorderTokenTest.kt
git commit -m "feat(designsystem): M6 InkShape 胶囊/骨架圆角与 InkBorder 画布/日历/加载描边"
```

---

### Task 3: 悬浮层纸墨对齐（QuickCaptureOverlayService.kt UI 半部）

**Files:**
- Modify: `app/src/main/java/cc/pscly/onememos/overlay/QuickCaptureOverlayService.kt`（约 1030–1530 行 UI 区）

约束：行 1–1030 服务逻辑零改动；仅 UI composable。完成后本文件 `rg '\b[0-9]+\.dp\b'` 除 `0.dp` 外为零。

- [ ] **Step 1: IME 抬升常量**（约 1046 行）

```kotlin
// 前
val raw = (imeBottomDp.value * 0.35f).dp
raw.coerceIn(0.dp, 140.dp)
// 后
val raw = (imeBottomDp.value * InkSpacing.OverlayImeLiftFactor).dp
raw.coerceIn(0.dp, InkSpacing.OverlayImeLiftMax)
```

- [ ] **Step 2: 遮罩色**（约 1084 行）

```kotlin
// 前
.background(Color.Black.copy(alpha = 0.38f))
// 后
.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.38f))
```

（collections 先例：`colorScheme.scrim.copy(alpha=0.35f)`；alpha 保留 0.38 零漂移。若 `Color` import 此后无其他使用则删除。）

- [ ] **Step 3: 卡片与列间距**（约 1095/1099 行）

`.padding(horizontal = 16.dp)` → `.padding(horizontal = InkSpacing.X16)`；`Arrangement.spacedBy(10.dp)` → `Arrangement.spacedBy(InkSpacing.X10)`。

- [ ] **Step 4: 输入框换 ScrollTextField**（约 1134–1144 行）

```kotlin
// 前
OutlinedTextField(
    value = uiState.content,
    onValueChange = onUpdateContent,
    modifier = Modifier
        .fillMaxWidth()
        .focusRequester(focusRequester),
    placeholder = { Text(text = "写点什么…") },
    minLines = 3,
    maxLines = 10,
    singleLine = false,
)
// 后
ScrollTextField(
    value = uiState.content,
    onValueChange = onUpdateContent,
    modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = InkSpacing.OverlayInputMinHeight, max = InkSpacing.OverlayInputMaxHeight)
        .focusRequester(focusRequester),
    placeholder = "写点什么…",
)
```

import：加 `cc.pscly.onememos.ui.component.ScrollTextField` 与 `androidx.compose.foundation.layout.heightIn`（若缺）；`OutlinedTextField` 无其他使用则删 import。

- [ ] **Step 5: 附件行**（约 1140 行）

`PaddingValues(vertical = 4.dp)` → `PaddingValues(vertical = InkSpacing.X4)`；`Arrangement.spacedBy(10.dp)` → `Arrangement.spacedBy(InkSpacing.X10)`。

- [ ] **Step 6: 草稿横幅**（约 1212–1236 行）

`clip(RoundedCornerShape(12.dp))` → `clip(InkShape.Card)`；`padding(horizontal = 12.dp, vertical = 10.dp)` → `padding(horizontal = InkSpacing.X12, vertical = InkSpacing.X10)`；两处动作 `padding(horizontal = 8.dp, vertical = 4.dp)` → `padding(horizontal = InkSpacing.X8, vertical = InkSpacing.X4)`；`Spacer(modifier = Modifier.size(4.dp))` → `Modifier.size(InkSpacing.X4)`。import 加 `cc.pscly.onememos.ui.theme.InkShape`。

- [ ] **Step 7: 底部按钮行**（约 1265 行）

`.padding(start = 10.dp)` → `.padding(start = InkSpacing.X10)`。

- [ ] **Step 8: 附件缩略**（OverlayAttachmentThumb，约 1327/1347 行）

`Modifier.size(84.dp).clip(RoundedCornerShape(12.dp))` → `Modifier.size(InkSpacing.OverlayThumbSize).clip(InkShape.Card)`；`Modifier.align(Alignment.TopEnd).size(28.dp)` → `.size(InkSpacing.OverlayThumbBadgeSize)`。

- [ ] **Step 9: 文本工具行**（约 1402 行）

`Arrangement.spacedBy(8.dp, Alignment.End)` → `Arrangement.spacedBy(InkSpacing.X8, Alignment.End)`。

- [ ] **Step 10: 历史弹层**（QuickCaptureHistoryBottomSheet，约 1459–1500 行）

标题 `padding(horizontal = 20.dp)` → `padding(horizontal = InkSpacing.SheetMarginH)`；`Spacer(height(12.dp))` → `height(InkSpacing.X12)`；空态 `padding(horizontal = 20.dp, vertical = 8.dp)` → `padding(horizontal = InkSpacing.SheetMarginH, vertical = InkSpacing.X8)`；LazyColumn `padding(horizontal = 20.dp)` → `padding(horizontal = InkSpacing.SheetMarginH)`；`spacedBy(10.dp)` → `spacedBy(InkSpacing.X10)`；`padding(top = 6.dp)` → `padding(top = InkSpacing.X6)`；末尾 `Spacer(height(16.dp))` → `height(InkSpacing.X16)`。

- [ ] **Step 11: 文本动作**（QuickCaptureTextAction，约 1523 行）

`.padding(horizontal = 10.dp, vertical = 8.dp)` → `.padding(horizontal = InkSpacing.X10, vertical = InkSpacing.X8)`。

- [ ] **Step 12: 残留扫描 + 编译**

Run: `rg -n '\b[1-9][0-9]*\.dp\b' app/src/main/java/cc/pscly/onememos/overlay/QuickCaptureOverlayService.kt`
Expected: 零输出（`0.dp` 允许保留）。
Run: `./gradlew :app:compileDebugKotlin -Pkotlin.compiler.execution.strategy=in-process`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 13: Commit**

```bash
git add app/src/main/java/cc/pscly/onememos/overlay/QuickCaptureOverlayService.kt
git commit -m "refactor(overlay): M6 悬浮速记对齐纸墨原语并全量令牌化"
```

---

### Task 4: 壳层 OneMemosApp 映射

**Files:**
- Modify: `app/src/main/java/cc/pscly/onememos/ui/OneMemosApp.kt`

- [ ] **Step 1: 抽屉间距**（约 186/200 行）

`Modifier.padding(horizontal = 16.dp, vertical = 18.dp)` → `Modifier.padding(horizontal = InkSpacing.X16, vertical = InkSpacing.X18)`；`Spacer(modifier = Modifier.height(14.dp))` → `Modifier.height(InkSpacing.X14)`。import 加 `cc.pscly.onememos.ui.theme.InkSpacing`。

- [ ] **Step 2: 更新弹窗**（AppUpdateDialog，约 314/318/341 行）

`.heightIn(max = 420.dp)` → `.heightIn(max = InkSpacing.UpdateDialogNotesMaxHeight)`；两处 `Spacer(modifier = Modifier.height(10.dp))` → `Modifier.height(InkSpacing.X10)`。

- [ ] **Step 3: 残留扫描 + 编译**

Run: `rg -n '\b[1-9][0-9]*\.dp\b' app/src/main/java/cc/pscly/onememos/ui/OneMemosApp.kt` → 零输出；`rg -n 'import androidx.compose.ui.unit.dp' app/src/main/java/cc/pscly/onememos/ui/OneMemosApp.kt` 若无其他 dp 使用则删除该 import。
Run: `./gradlew :app:compileDebugKotlin -Pkotlin.compiler.execution.strategy=in-process` → BUILD SUCCESSFUL。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/cc/pscly/onememos/ui/OneMemosApp.kt
git commit -m "refactor(app): M6 壳层抽屉与更新弹窗令牌化"
```

---

### Task 5: feature/editor + feature/todo 映射

**Files:**
- Modify: `feature/editor/src/main/java/cc/pscly/onememos/ui/feature/editor/EditorScreen.kt`
- Modify: `feature/todo/src/main/java/cc/pscly/onememos/ui/feature/todo/TodoDialogs.kt`
- Modify: `feature/todo/src/main/java/cc/pscly/onememos/ui/feature/todo/TodoItemRow.kt`

- [ ] **Step 1: EditorScreen 六处**

`.size(84.dp)` → `.size(InkSpacing.AttachmentThumbSize)`（约 585）；三处 `.size(28.dp)` → `.size(InkSpacing.OverlayThumbBadgeSize)`（约 617/628/637）；`.heightIn(min = 56.dp)` → `.heightIn(min = InkSpacing.X56)`（约 652）；`val rowEndPadding = if (uiState.canEdit) 68.dp else 0.dp` → `if (uiState.canEdit) InkSpacing.EditorRowEndPadding else 0.dp`（约 655，`0.dp` 保留）；`size = 56.dp` → `size = InkSpacing.X56`（约 732）。

- [ ] **Step 2: Todo 四处**

`TodoDialogs.kt` 两处 `.heightIn(max = 520.dp)` → `.heightIn(max = InkSpacing.SheetListMaxHeight)`（约 243/479）；`TodoItemRow.kt` `.size(30.dp)` → `.size(InkSpacing.TodoStatusIconSize)`（约 216）；`BorderStroke(1.dp, borderColor)` → `BorderStroke(InkBorder.Hairline, borderColor)`（约 225，import 加 `cc.pscly.onememos.ui.theme.InkBorder`）。

- [ ] **Step 3: 残留扫描 + 编译**

Run: `rg -n '\b[1-9][0-9]*\.dp\b' feature/editor/src/main feature/todo/src/main --glob '*.kt'` → 零输出（EditorScreen 内 `TODO_REGEX` 等非 dp 命中不算）。
Run: `./gradlew :feature:editor:compileDebugKotlin :feature:todo:compileDebugKotlin -Pkotlin.compiler.execution.strategy=in-process` → BUILD SUCCESSFUL。

- [ ] **Step 4: Commit**

```bash
git add feature/editor/src/main/java/cc/pscly/onememos/ui/feature/editor/EditorScreen.kt feature/todo/src/main/java/cc/pscly/onememos/ui/feature/todo/TodoDialogs.kt feature/todo/src/main/java/cc/pscly/onememos/ui/feature/todo/TodoItemRow.kt
git commit -m "refactor(feature): M6 editor/todo 残留 dp 等值入令牌"
```

---

### Task 6: feature/home 映射

**Files:**
- Modify: `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeScreen.kt`
- Modify: `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/AddToCollectionsDialog.kt`
- Modify: `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/AddToCollectionsBatchDialog.kt`

- [ ] **Step 1: 弹窗列表高（两处）**

两文件 `.heightIn(max = 320.dp)` → `.heightIn(max = InkSpacing.DialogListMaxHeight)`。

- [ ] **Step 2: CPI 数值（按钮加载态豁免保留组件）**

约 367 行：`CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)` → `Modifier.size(InkSpacing.X20)`、`strokeWidth = InkBorder.SpinnerStroke`；约 678–680 行：`strokeWidth = 2.dp` → `InkBorder.SpinnerStroke`、`Modifier.size(18.dp)` → `Modifier.size(InkSpacing.X18)`。import 加 `cc.pscly.onememos.ui.theme.InkBorder`。

- [ ] **Step 3: 双列断点迁入**

删除 `private val TwoColumnMinWidth = 600.dp`（约 931 行），引用处改 `InkSpacing.TwoColumnMinWidth`。

- [ ] **Step 4: 骨架屏与横幅形状**

约 1220 行 `RoundedCornerShape(18.dp)` → `InkShape.SkeletonCard`；约 1411 行同改。约 1533/1541 行 `.height(18.dp)` → `.height(InkSpacing.SkeletonTextLineHeight)`；约 1534/1542/1552 行 `RoundedCornerShape(8.dp)` → `InkShape.Skeleton`；**删除这些行尾的「结构常量」注释**（已入令牌）。import 加 `cc.pscly.onememos.ui.theme.InkShape`。

- [ ] **Step 5: 其余尺寸**

约 1298 行 `.size(28.dp)` → `.size(InkSpacing.OverlayThumbBadgeSize)`；约 1304 行 `.size(18.dp)` → `.size(InkSpacing.X18)`；约 1407 行 `topInset + 56.dp + InkSpacing.X10` → `topInset + InkSpacing.X56 + InkSpacing.X10`；约 1421 行 `.heightIn(min = 56.dp)` → `.heightIn(min = InkSpacing.X56)`；约 1520–1521 行 `.height(28.dp)` → `.height(InkSpacing.X28)`、`.width(64.dp)` → `.width(InkSpacing.X64)`。

- [ ] **Step 6: 残留扫描 + 编译 + 单测**

Run: `rg -n '\b[1-9][0-9]*\.dp\b' feature/home/src/main --glob '*.kt'` → 零输出。
Run: `./gradlew :feature:home:compileDebugKotlin :feature:home:testDebugUnitTest -Pkotlin.compiler.execution.strategy=in-process` → BUILD SUCCESSFUL。

- [ ] **Step 7: Commit**

```bash
git add feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeScreen.kt feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/AddToCollectionsDialog.kt feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/AddToCollectionsBatchDialog.kt
git commit -m "refactor(home): M6 双列断点迁入令牌并清空残留 dp"
```

---

### Task 7: feature/sharecard 映射

**Files:**
- Modify: `feature/sharecard/src/main/java/cc/pscly/onememos/ui/feature/sharecard/ShareCardScreen.kt`
- Modify: `feature/sharecard/src/main/java/cc/pscly/onememos/ui/feature/sharecard/ShareCardCanvas.kt`

- [ ] **Step 1: 预览高**

`ShareCardScreen.kt` 约 196 行 `.height(360.dp)` → `.height(InkSpacing.ShareCardPreviewHeight)`。

- [ ] **Step 2: 画布边距与描边**

约 84 行 `val marginX = 22.dp.toPx()` → `InkSpacing.ShareCardMarginX.toPx()`；约 85 行 `val lineGap = 26.dp.toPx()` → `InkSpacing.ShareCardLineGap.toPx()`；约 122 行 `strokeWidth = 3.dp.toPx()` → `InkBorder.CanvasStroke.toPx()`（import 加 InkBorder）。

- [ ] **Step 3: 内容内边距与列距**

约 143 行 `PaddingValues(horizontal = 54.dp, vertical = 56.dp)` → `PaddingValues(horizontal = InkSpacing.ShareCardPaddingH, vertical = InkSpacing.ShareCardPaddingV)`；约 350 行 `Arrangement.spacedBy(26.dp)` → `Arrangement.spacedBy(InkSpacing.ShareCardLineGap)`；约 369 行 `.padding(start = 22.dp)` → `.padding(start = InkSpacing.ShareCardMarginX)`。

- [ ] **Step 4: 形状**

约 326 行 `RoundedCornerShape(8.dp)` → `InkShape.CanvasSub`；约 378/483 行两处 `RoundedCornerShape(999.dp)` → `InkShape.Pill`（约 348 行 `horizontal = 0.dp` 保留）。

- [ ] **Step 5: 竖条 / 图 / 印章 / 投影**

约 377 行 `.size(width = 3.dp, height = 520.dp)` → `.size(width = InkBorder.CanvasStroke, height = InkSpacing.ShareCardQuoteBarHeight)`；约 462/506 行 `shadowElevation = 4.dp` → `shadowElevation = InkSpacing.ShareCardElevation`；约 474 行 `.size(108.dp)` → `.size(InkSpacing.ShareCardImageSize)`；约 512 行 `.size(92.dp)` → `.size(InkSpacing.ShareCardSealSize)`；约 530 行 `.padding(top = 80.dp, start = InkSpacing.X12)` → `.padding(top = InkSpacing.ShareCardThemesTopPadding, start = InkSpacing.X12)`。

- [ ] **Step 6: 残留扫描 + 编译**

Run: `rg -n '\b[1-9][0-9]*\.dp\b' feature/sharecard/src/main --glob '*.kt'` → 零输出。
Run: `./gradlew :feature:sharecard:compileDebugKotlin :feature:sharecard:testDebugUnitTest -Pkotlin.compiler.execution.strategy=in-process` → BUILD SUCCESSFUL。

- [ ] **Step 7: Commit**

```bash
git add feature/sharecard/src/main/java/cc/pscly/onememos/ui/feature/sharecard/ShareCardScreen.kt feature/sharecard/src/main/java/cc/pscly/onememos/ui/feature/sharecard/ShareCardCanvas.kt
git commit -m "refactor(sharecard): M6 画布边距/形状/投影等值入令牌"
```

---

### Task 8: feature/profile + feature/collections + feature/auth 映射

**Files:**
- Modify: `feature/profile/src/main/java/cc/pscly/onememos/ui/feature/profile/ProfileScreen.kt`
- Modify: `feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsScreen.kt`
- Modify: `feature/auth/src/main/java/cc/pscly/onememos/ui/feature/auth/AuthScreen.kt`

- [ ] **Step 1: Profile**

约 263 行 `.width(44.dp)` → `.width(InkSpacing.CalendarCellMin)`；约 297/298 行 `val maxCell = 56.dp` / `val minCell = 44.dp` → `InkSpacing.CalendarCellMax` / `InkSpacing.CalendarCellMin`；约 484 行 `RoundedCornerShape(topEnd = 999.dp, bottomEnd = 999.dp)` → `InkShape.PillEnd`；约 485 行 → `InkShape.PillStart`；约 486/525 行 `RoundedCornerShape(999.dp)` → `InkShape.Pill`；约 521 行 `.size(34.dp)` → `.size(InkSpacing.CalendarDaySize)`；约 523 行 `width = 1.6.dp` → `width = InkBorder.CalendarRing`；约 579 行 `RoundedCornerShape(3.dp)` → `InkShape.Legend`；约 604 行 `.height(380.dp)` → `.height(InkSpacing.ProfileCalendarHeight)`。`spacedBy(0.dp)` 两处保留。

- [ ] **Step 2: Collections**

约 471 行 `.size(18.dp)` → `.size(InkSpacing.X18)`；约 1022 行 `76.dp.roundToPx()` / `88.dp.roundToPx()` → `InkSpacing.SingleImageThumbSize.roundToPx()` / `InkSpacing.GridImageThumbSize.roundToPx()`；约 1069 行 `.size(76.dp)` → `.size(InkSpacing.SingleImageThumbSize)`；约 1151/1168 行 `.size(88.dp)` → `.size(InkSpacing.GridImageThumbSize)`；约 1361/1444 行 `.heightIn(max = 360.dp)` → `.heightIn(max = InkSpacing.CollectionsDialogMaxHeight)`；约 1464 行 `Modifier.width(22.dp)` → `Modifier.width(InkSpacing.X22)`。

- [ ] **Step 3: Auth**

约 194–195 与 295–296 两处 CPI：`Modifier.size(18.dp)` → `Modifier.size(InkSpacing.X18)`、`strokeWidth = 2.dp` → `strokeWidth = InkBorder.SpinnerStroke`（按钮加载态组件豁免保留；import 加 InkBorder）。

- [ ] **Step 4: 残留扫描 + 编译 + 单测**

Run: `rg -n '\b[1-9][0-9]*\.dp\b' feature/profile/src/main feature/collections/src/main feature/auth/src/main --glob '*.kt'` → 零输出。
Run: `./gradlew :feature:profile:compileDebugKotlin :feature:collections:compileDebugKotlin :feature:auth:compileDebugKotlin :feature:profile:testDebugUnitTest -Pkotlin.compiler.execution.strategy=in-process` → BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add feature/profile/src/main/java/cc/pscly/onememos/ui/feature/profile/ProfileScreen.kt feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsScreen.kt feature/auth/src/main/java/cc/pscly/onememos/ui/feature/auth/AuthScreen.kt
git commit -m "refactor(feature): M6 profile/collections/auth 残留 dp 等值入令牌"
```

---

### Task 9: 全量残留扫描 + 文档回写

**Files:**
- Modify: `DESIGN.md`（§4.1 增补 M6 尺度、§5 增补形状/描边事实）
- Modify: `.ai_session.md`（文首新增 M6 段）

- [ ] **Step 1: 全 feature + app 残留总扫**

Run: `rg -n '\b[1-9][0-9]*\.dp\b' feature/ app/src/main --glob '*.kt' | grep -v androidTest`
Expected: 零输出（`0.dp` 与 `androidTest` 内 `DpSize(360.dp, 800.dp)` 等测试矩阵尺寸豁免）。
Run: `rg -n 'RoundedCornerShape\([0-9]' feature/ app/src/main --glob '*.kt'` → 零输出；`rg -n 'Color\(0x' feature/ --glob '*.kt'` → 零输出（保持）。

- [ ] **Step 2: DESIGN.md 增补**

§4.1 尺寸表追加 M6 尺度行（X18/X22/X26/X28/X30/X54/X64/X68/X76/X80/X84/X92/X108/X120/X140/X320/X324/X360/X380/X420/X520/X600，注明来源界面）；§5 相应组件条目补：悬浮速记层用纸墨原语（ScrollTextField + InkCard + scrim）、InkShape 新增 Pill/PillStart/PillEnd/Skeleton/SkeletonCard/CanvasSub/Legend、InkBorder 新增 CanvasStroke/CalendarRing/SpinnerStroke。

- [ ] **Step 3: 会话回写**

`.ai_session.md` 文首新增「## [2026-07-20] M6——悬浮层/壳层/feature 令牌收敛波」段：范围、映射规则、各任务提交哈希、残留扫描证据；标注**尚未** bump 版本与发布。

- [ ] **Step 4: Commit**

```bash
git add DESIGN.md .ai_session.md
git commit -m "docs: M6 DESIGN 尺寸表增补与会话回写"
```

---

### Task 10: 门禁 + 真机目检

- [ ] **Step 1: 架构门禁**

Run: `./scripts/verify-architecture.sh` → 输出 `verify-architecture.sh: OK`。

- [ ] **Step 2: 完整门禁**

Run: `./scripts/verify.sh` → 末尾输出 `verify.sh: OK`（exit 0）。

- [ ] **Step 3: 装机**

Run: `./scripts/build-benchmark-apk.sh`（生成时间戳 APK）→ `ADB_SERIAL=192.168.12.101:5555 ./scripts/install-benchmark.sh` → `Success`；确认 `versionName=1.14.0`（本任务不 bump）。

- [ ] **Step 4: 真机目检截图**

场景：①悬浮速记（输入纸面、草稿横幅、附件缩略、历史弹层）②主屏抽屉 ③更新弹窗（如可触发）④首页骨架/列表、分享卡、个人中心日历、锦囊弹窗各一张。`adb shell screencap` 拉取后逐张目检：悬浮层呈纸墨质感；feature 与 1.14.0 无可见差异。

---

### Task 11: 发布 1.15.0 (165) 完整闭环

- [ ] **Step 1: Bump**

`app/build.gradle.kts`：`versionCode = 164` → `165`；`versionName = "1.14.0"` → `"1.15.0"`。

- [ ] **Step 2: 提交 + push main**

```bash
git add app/build.gradle.kts
git commit -m "chore(release): bump 1.15.0 (165) M6 令牌收敛波"
git push origin main
```

- [ ] **Step 3: annotated Tag + push**

```bash
git tag -a v1.15.0 -m "1memos 1.15.0" <release-commit-sha>
git push origin v1.15.0
```

- [ ] **Step 4: 等 Tag Actions 成功**

Run: `gh run list -R pscly/xinliu_android --branch v1.15.0 --limit 1` 找到 run id → `gh run watch <id> -R pscly/xinliu_android --exit-status` → completed/success。

- [ ] **Step 5: 下载 Artifact 并核验**

`gh run download <id> -R pscly/xinliu_android -D /tmp/opencode/v1.15.0-tag-artifact`；对 APK 执行 `aapt2 dump badging`（包名 `cc.pscly.onememos`、`1.15.0 (165)`）、`sha256sum`、`apksigner verify --print-certs`（证书 SHA-256 = `58749c794f0c54af6b69bb6d80248a9fda0b75c687fde55b98d9575fc091633e`）。

- [ ] **Step 6: 创建 latest Release**

`gh release create v1.15.0 --repo pscly/xinliu_android --title "1memos 1.15.0" --notes-file <notes> --latest --target <release-commit-sha> <artifact-apk>` → 非草稿、非预发布、唯一 APK 资产。

- [ ] **Step 7: 独立下载复核**

`gh release download v1.15.0 -R pscly/xinliu_android -D /tmp/opencode/v1.15.0-independent-release-download --clobber`；与 Artifact **逐字节一致**（`cmp`），摘要/包名/版本/证书复核通过。

- [ ] **Step 8: 会话终态回写 + push**

`.ai_session.md` M6 段补最终裁决（Release URL/id、run、Artifact、SHA-256、证书、设备）；docs-only commit + push（不移动 Tag）。
