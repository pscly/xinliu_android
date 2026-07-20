# 2026-07-20 悬浮层/壳层/feature 令牌收敛波 设计规格

> 依据：v1.14.0 发布后 UI 现状盘点；M1–M5 文墨重构已发布，本波收尾剩余裸 dp/裸色/裸形状。
> 前置澄清结论（用户已确认）：
> 1. 范围 = **P1（悬浮速记 + 壳层）+ P2（feature 残留裸 dp）一起做**；
> 2. 视觉行为 = **顺带对齐纸墨**（悬浮层输入框/遮罩/横幅对齐原语，feature 等值映射）；
> 3. 结构尺寸 = **进令牌语义别名**，DESIGN.md 同步登记。

## 1. 背景与目标

- **目标**：全应用业务 UI 与壳层/悬浮层的 `dp/sp/Color(0x/RoundedCornerShape(裸值)` 收敛进 `InkSpacing/InkShape/InkBorder`；悬浮速记对齐纸墨原语；一次发布 `1.15.0` 完成闭环。
- **非目标**：不改服务逻辑/数据流；不改原语内部；不处理状态原语扩展、TalkBack 全流程、大字体契约债（独立议题）。
- **不变量**：feature 模块**视觉零变化**（仅悬浮层有意图视觉对齐）；`Color(0x` 保持 feature 零；CPI 按钮加载态豁免不动（仅其尺寸/描边数值进令牌）。

## 2. 范围与文件清单

| 层 | 文件 | 动作 |
| --- | --- | --- |
| 令牌 | `core/designsystem/.../InkSpacing.kt` / `InkShape.kt` / `InkBorder.kt` | 新增尺度/形状/描边 + 语义别名 |
| 令牌测试 | `core/designsystem/.../InkSpacingTest.kt`（扩展）、新增 `InkShapeBorderTokenTest.kt` | 断言新令牌值 |
| 悬浮层 | `app/.../overlay/QuickCaptureOverlayService.kt`（1030–1530 UI 半部） | 对齐纸墨 + 全令牌化 |
| 壳层 | `app/.../ui/OneMemosApp.kt` | 抽屉与更新弹窗等值映射 |
| feature | home(19) / sharecard(17) / profile(13) / collections(8) / editor(7) / todo(4) / auth(4) / settings(0 实改) | 等值映射 |
| 文档 | `DESIGN.md` §4.1/§5 增补；`.ai_session.md` 回写 | 同步登记 |

## 3. 令牌扩展表

### 3.1 InkSpacing 新尺度（字面量全库唯一）

| 尺度 | 值 | 数值依据（现状实测） |
| --- | --- | --- |
| X18 | 18.dp | home/auth CPI 尺寸、home 骨架行高 ×3；**SheetGapL=18.dp 去重**改引 X18 |
| X22 | 22.dp | sharecard 画布边距、collections 分隔 |
| X26 | 26.dp | sharecard 行距 ×2 |
| X28 | 28.dp | 悬浮层/编辑器附件角标与移除钮 ×5、home ×2 |
| X30 | 30.dp | todo 状态圈 |
| X54 | 54.dp | sharecard 画布水平内边距 |
| X64 | 64.dp | home 骨架块宽 |
| X68 | 68.dp | editor 行尾留白 |
| X76 | 76.dp | collections 单图缩略 |
| X80 | 80.dp | sharecard 顶部留白 |
| X84 | 84.dp | 悬浮层/编辑器附件缩略 |
| X92 | 92.dp | sharecard 印章 |
| X108 | 108.dp | sharecard 图 |
| X120 | 120.dp | 悬浮层输入框最小高（≈3 行 LinePitch + 纸面留白） |
| X140 | 140.dp | 悬浮层 IME 抬升上限 |
| X320 | 320.dp | home 收藏弹窗列表最大高 ×2 |
| X324 | 324.dp | 悬浮层输入框最大高（≈10 行 LinePitch + 纸面留白） |
| X360 | 360.dp | sharecard 预览高、collections 弹窗高 ×2 |
| X380 | 380.dp | profile 日历高 |
| X420 | 420.dp | 更新弹窗内容最大高 |
| X520 | 520.dp | sharecard 引用竖条高、todo 弹层列表高 ×2 |
| X600 | 600.dp | home 双列断点（原文件私有 `TwoColumnMinWidth` 迁入） |

### 3.2 InkSpacing 新语义别名（按域分区）

- **悬浮层**：`OverlayThumbSize=X84`、`OverlayThumbBadgeSize=X28`、`OverlayImeLiftMax=X140`、`OverlayImeLiftFactor=0.35f（const Float）`、`OverlayInputMinHeight=X120`、`OverlayInputMaxHeight=X324`
- **分享卡**：`ShareCardMarginX=X22`、`ShareCardLineGap=X26`、`ShareCardPaddingH=X54`、`ShareCardPaddingV=X56`、`ShareCardSealSize=X92`、`ShareCardImageSize=X108`、`ShareCardQuoteBarHeight=X520`、`ShareCardPreviewHeight=X360`、`ShareCardThemesTopPadding=X80`、`ShareCardElevation=X4`（唯一使用投影的组件，注释说明）
- **附件缩略（跨域）**：`AttachmentThumbSize=X84`（与 OverlayThumbSize 同值双别名，调用方按域取用）
- **图片网格**：`SingleImageThumbSize=X76`、`GridImageThumbSize=X88`
- **弹窗**：`DialogListMaxHeight=X320`、`SheetListMaxHeight=X520`、`UpdateDialogNotesMaxHeight=X420`、`CollectionsDialogMaxHeight=X360`
- **骨架屏**：`SkeletonTextLineHeight=X18`
- **布局**：`TwoColumnMinWidth=X600`、`ProfileCalendarHeight=X380`、`EditorRowEndPadding=X68`、`TodoStatusIconSize=X30`、`CalendarCellMin=X44`、`CalendarCellMax=X56`、`CalendarDaySize=X34`

### 3.3 InkShape 新增

| 项 | 值 | 用途 |
| --- | --- | --- |
| RadiusXss | 8.dp | 骨架屏占位块、分享卡画布子面圆角 |
| RadiusXl | 18.dp | home 骨架卡片/横幅圆角 ×2 |
| RadiusMicro | 3.dp | profile 图例色条 |
| Skeleton / CanvasSub | RoundedCornerShape(RadiusXss) | 语义形状 |
| SkeletonCard | RoundedCornerShape(RadiusXl) | 语义形状 |
| Legend | RoundedCornerShape(RadiusMicro) | 语义形状 |
| Pill | RoundedCornerShape(percent=50) | 替代全部 `RoundedCornerShape(999.dp)`（profile ×4、sharecard ×2；视觉等价） |

### 3.4 InkBorder 新增

| 项 | 值 | 用途 |
| --- | --- | --- |
| CanvasStroke | 3.dp | sharecard 画布竖线描边 |
| CalendarRing | 1.6.dp | profile 日历今日描边 |
| SpinnerStroke | = Stamp（2.dp 别名复用，不重复字面量） | home/auth CPI 描边 |

## 4. 悬浮层纸墨对齐细则（唯一意图视觉变化区）

| 现状 | 改为 | 说明 |
| --- | --- | --- |
| 遮罩 `Color.Black.copy(alpha=0.38f)` | `MaterialTheme.colorScheme.scrim` | 沿用 collections 既有先例 |
| M3 `OutlinedTextField`（3–10 行） | `ScrollTextField` + `heightIn(min=OverlayInputMinHeight, max=OverlayInputMaxHeight)` | 与编辑器同纸面质感；滚动由 ScrollPaper 承载 |
| 草稿横幅 `RoundedCornerShape(12.dp)` | `InkShape.Card` + InkSpacing 令牌，`surfaceVariant` 保留 | scheme 色合规 |
| 附件 thumb `RoundedCornerShape(12.dp)` | `InkShape.Card` | 同值 |
| 全部间距/尺寸裸 dp | 上表语义别名 | 含 IME lift 的 `0.35f`/`140.dp` 命名化 |
| 历史弹层 20/12/8/10/6/16 dp | `SheetMarginH` 等既有别名 + 新尺度 | — |
| `QuickCaptureTextAction` 禁用 `primary.copy(alpha=0.45f)` | 保持（scheme 派生色，合规） | 不进 `Color(0x` |

服务逻辑（draft/attachments/save/history 数据流）**零改动**。

## 5. 壳层映射（OneMemosApp.kt）

- 抽屉：`padding(horizontal=16.dp, vertical=18.dp)` → `X16`/`X18`；`Spacer(14.dp)` → `X14`
- 更新弹窗：`heightIn(max=420.dp)` → `UpdateDialogNotesMaxHeight`；`Spacer(10.dp)` ×2 → `X10`
- 其余 AlertDialog 结构不动

## 6. feature 残留映射规则

1. **等值**：值相等才映射；无对应尺度先在 §3 表内新增，禁止近似取整。
2. **`0.dp` 保留**（零不是设计令牌，settings 现状一致）。
3. **CPI 豁免不动组件**，仅 `size/strokeWidth` 数值 → `X18/X20 + InkBorder.SpinnerStroke`。
4. **已注释“结构常量”的骨架屏**（home 1533–1552）迁入令牌，注释删除。
5. `RoundedCornerShape(999.dp)` → `InkShape.Pill`；`RoundedCornerShape(8.dp)` → `InkShape.Skeleton`/`CanvasSub` 按域。
6. `strokeWidth=3.dp.toPx()`（画布）→ `InkBorder.CanvasStroke`；`BorderStroke(1.dp)` → `InkBorder.Hairline`。
7. 每模块收尾跑 `rg '\b[0-9]+\.dp\b' <module>/src/main`，目标：**除 `0.dp` 外清零**。

## 7. 验证

| 关卡 | 命令/方式 | 期望 |
| --- | --- | --- |
| 令牌单测 | `:core:designsystem:testDebugUnitTest` | 新尺度/形状/描边断言全绿 |
| 编译 | 全模块 `compileDebugKotlin` | BUILD SUCCESSFUL |
| 残留扫描 | 逐模块 `rg` | 除 `0.dp` 外零裸 dp；feature 维持 `Color(0x` = 0 |
| 架构门禁 | `scripts/verify-architecture.sh` | OK |
| 总门禁 | `scripts/verify.sh` | OK（exit 0） |
| 真机目检 | 192.168.12.101:5555 截图：悬浮速记（输入/草稿横幅/附件/历史弹层）、主屏抽屉、更新弹窗 | 悬浮层纸墨质感一致；feature 无可见差异 |
| 发布 | AGENTS.md §8 全流程 | `1.15.0 (165)` → push → tag `v1.15.0` → Actions → latest Release → 独立下载核验 |

## 8. 风险与回退

- **风险 1**：`ScrollTextField` 在小弹窗内的 IME/焦点行为与 `OutlinedTextField` 不同 → 真机目检重点验证输入、选择工具栏（OverlayTextToolbar 自定义行保留）、键盘抬升。
- **风险 2**：`InkShape.Pill`（percent=50）与 `999.dp` 在极端大尺寸元素上的差异 → 当前使用点元素尺寸均 <200dp，视觉等价；注释标注。
- **风险 3**：22 个新尺度一次性进表 → 单测锁定字面量，后续再增须先查表。
- **回退**：本波为独立提交链，任一关卡失败按 `git revert` 逐任务回退；不跨任务混合提交。

## 9. 不做的事（YAGNI）

- 不动 QuickCaptureOverlayService 行 1–1030 服务逻辑
- 不改 InkCard/InkChip/ScrollPaper 等原语内部
- 不做状态原语全 feature 迁移、TalkBack 全流程、大字体契约（DESIGN.md §8.2 保留债）
- 不迁移 `ImageViewerDialog` 等已合规组件
