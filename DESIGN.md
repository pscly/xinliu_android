# 心流 Android 设计系统

本文档从现有 Jetpack Compose 源码中提取设计事实（M1–M3 文墨·flomo 重构之后），是后续界面工作的视觉依据。文中使用以下标记区分信息性质：

- **现状**：当前源码已经实现并可直接复用的令牌、原语或行为。
- **约束**：后续新增或修改界面时必须遵守的规则，用于保护现有识别度与可访问性。
- **债务**：当前实现尚未形成统一能力，不能被描述为已经支持。

核心证据来自：

| 区域 | 路径 |
| --- | --- |
| 主题描述符 | `core/model/.../ThemeDescriptor.kt`、`ThemePalette.kt` |
| 令牌与主题 | `core/designsystem/.../theme/InkSpacing.kt`、`InkShape.kt`、`InkBorder.kt`、`InkMotion.kt`、`ColorSchemes.kt`、`Typography.kt`、`OneMemosTheme.kt`、`ReadingConfig.kt`、`PaperInkComponents.kt` |
| 原语 | `core/designsystem/.../component/`、`markdown2/MikepenzMarkdown.kt` |
| 无障碍 | `core/designsystem/.../accessibility/ReducedMotion.kt`、`PaperInkFocusIndicator.kt` |
| 领域词汇 | `CONTEXT.md`；决策见 ADR `0008`–`0012` |

本文档不定义设置中心的具体业务布局；设置 UI 标签以 `feature/settings/.../settings_appearance_strings.xml` 为准。

---

## 1. Atmosphere & Identity（氛围与识别）

### 1.1 识别核心

心流界面是一套克制的中式纸墨系统，审美靶心为 **flomo 式内容呼吸感**（极简卡片流、标签安静退后），同时保留并强化 **纸、墨、线、印** 四要素：

1. **纸**：草白背景 + 米宣纸表面（浅色），或墨灰背景 + 玄青纸面（深色）。
2. **墨**：焦茶正文 / 银灰正文；主色仅作印记、强调与边线。
3. **线**：文墨卷轴质感下，细墨色横线随内容滚动，左侧低透明度主题色竖线；清简质感下横线关闭、留白加大。
4. **印**：方形圆角印章控件（压印缩放 + 轻触感）与盖章完成反馈。

后续界面应延续上述要素，禁止替换为渐变、玻璃、投影堆叠、通用 SaaS 卡片或未经源码支持的新字体。

### 1.2 四轴主题系统（现状）

主题不再是单一色板枚举，而是 **主题描述符**（`ThemeDescriptor`）：

```
色板 palette × 质感 texture × 密度 density × 字阶 typeScale × 字体 fontFamily
```

代码注释称「ADR 0008 四轴 + 字体档」五元组；新增外观等于新增描述符数据，不写新组件分支。

| 轴 | 枚举 | 档位（源码） |
| --- | --- | --- |
| 色板 | `ThemePalette` | `PAPER_INK`、`INDIGO`、`CYBER`、`MOON_WHITE`、`DYNAMIC` |
| 质感 | `ThemeTexture` | `SCROLL`（文墨卷轴）、`MINIMAL`（清简） |
| 密度 | `ThemeDensity` | `STANDARD`、`RELAXED`、`COMPACT` |
| 字阶 | `ThemeTypeScale` | 目前仅 `STANDARD`（扩展预留） |
| 字体 | `ThemeFontFamily` | `WENKAI`（霞鹜文楷标题）、`SYSTEM`（系统衬线标题） |

**出厂风格预设**（`ThemeDescriptor.FACTORY_PRESETS`，一键切换）：

| 预设常量 | UI 名 | 轴组合 |
| --- | --- | --- |
| `WENMO_ZHUSHA`（默认） | 文墨·朱砂 | 纸墨 × 卷轴 × 标准 × 标准 × 文楷 |
| `QINGJIAN_YUEBAI` | 清简·月白 | 月白 × 清简 × 宽松 × 标准 × 系统 |
| `YEHANG_DAILAN` | 夜航·黛蓝 | 黛蓝 × 卷轴 × 标准 × 标准 × 文楷 |
| `SAIBO_FLUOR` | 赛博·荧光青 | 赛博 × 清简 × 紧凑 × 标准 × 系统 |

`DYNAMIC`（跟随系统动态色）仅出现在高级色板调节，不绑定出厂预设。明暗由 `ThemeMode`（跟随系统 / 浅色 / 深色）独立控制。

**下发机制（现状）**：

- `OneMemosTheme` 注入 `MaterialTheme`（色板 + Typography + `PaperInkShapes`）及 CompositionLocal：`LocalThemeTexture`、`LocalThemeDensity`、`LocalReadingConfig`。
- 密度 → 页边距映射：`COMPACT` 更紧、`STANDARD` 中档、`RELAXED` 更松（见 `ThemeDensityCompositionLocal`）。
- 持久化：DataStore key `theme_descriptor`（JSON）；旧 `theme_palette` 经 `fromLegacyPalette` 迁移后清除。

- **约束**：主题相关新色值 / 字号 / 间距 / 圆角只允许进令牌与描述符，禁止散落业务界面。
- **约束**：质感轴分支只在共享原语内实现（如 `ScrollPaper`、`InkCard`、`TagChip`），业务屏不得再写第二套「有无横线」逻辑。

---

## 2. Design Tokens（设计令牌）

M1.1 起，原语禁止裸 `dp` / `sp` / 硬编码 `Color(0x…)`（色板字面量仅集中于 `ColorSchemes.kt` / `InkTone`）。

### 2.1 `InkSpacing`（间距与布局）

| 尺度 | 值 | 语义别名（节选） |
| --- | --- | --- |
| `X1` | `1.dp` | 发丝线宽（与 `InkBorder.Hairline` 一致） |
| `X6` | `6.dp` | `TagPaddingV`、查看器内缩等 |
| `X8` | `8.dp` | `ChipPaddingV`、`StateGapS` |
| `X10` | `10.dp` | `TagPaddingH`、`QuoteGap`、查看器 chrome |
| `X12` | `12.dp` | `PaperPaddingV`、`ChipPaddingH`、`MarkdownBlockGap`、`StateGapM` |
| `X14` | `14.dp` | `CardPadding`、`BannerPaddingH` |
| `X16` | `16.dp` | `PaperPaddingEnd`、清简纸面上下边距 |
| `X20` | `20.dp` | `SheetMarginH`、清简纸面左右边距 |
| `X24` | `24.dp` | `MarginLineX`、`QuoteBarMinHeight` |
| `X34` | `34.dp` | `PaperPaddingStart`、`StatePaddingV` |
| `X44` | `44.dp` | `SealIconSize`、`SealCompactThreshold`、`StateIconSize` |
| `X56` | `56.dp` | `SealButtonSize` |
| `X88` | `88.dp` | `TableCellMinWidth` |
| `X150` | `150.dp` | `StampSize` / 悬浮层输入框最大高（≈4 行，悬浮层） |
| `X18` | `18.dp` | CPI 尺寸 / 骨架屏正文行高（同源 `SheetGapL`；悬浮层/骨架） |
| `X22` | `22.dp` | 分享卡画布边距 / 锦囊分隔（分享卡/锦囊） |
| `X26` | `26.dp` | 分享卡行距（分享卡） |
| `X28` | `28.dp` | 附件角标与移除钮（悬浮层） |
| `X30` | `30.dp` | 待办状态圈（待办） |
| `X54` | `54.dp` | 分享卡画布水平内边距（分享卡） |
| `X64` | `64.dp` | 骨架屏块宽（骨架屏） |
| `X68` | `68.dp` | 编辑器行尾留白（编辑器） |
| `X76` | `76.dp` | 锦囊单图缩略（锦囊） |
| `X80` | `80.dp` | 分享卡顶部留白（分享卡） |
| `X84` | `84.dp` | 附件缩略图边长（悬浮层/编辑器） |
| `X92` | `92.dp` | 分享卡印章尺寸（分享卡） |
| `X108` | `108.dp` | 分享卡图片尺寸（分享卡） |
| `X120` | `120.dp` | 悬浮层输入框最小高（悬浮层） |
| `X320` | `320.dp` | 收藏弹窗列表最大高（弹窗） |
| `X324` | `324.dp` | 历史悬浮层输入框最大高（已退役，保留尺度备查） |
| `X360` | `360.dp` | 分享卡预览高 / 锦囊弹窗高（分享卡/弹窗） |
| `X380` | `380.dp` | 个人中心日历高（个人中心） |
| `X420` | `420.dp` | 更新弹窗内容最大高（弹窗） |
| `X520` | `520.dp` | 分享卡引用竖条高 / 待办弹层列表高（分享卡/弹层） |
| `X600` | `600.dp` | 首页双列断点（首页布局） |
| `LinePitch` | `30.sp` | 纸面横线节距 = 长文正文行高 |
| `CodeLineHeight` | `20.sp` | 代码块行高 |
| `TouchTargetMin` | `48.dp` | 最小触控目标 |
| `QuoteBarWidth` | `3.dp` | 引用竖条宽（语义别名，非 X* 尺度） |
| `SheetGapL` | `18.dp` | 弹层底部大间隔 |

清简质感纸面留白：`PaperPaddingStartMinimal` / `EndMinimal` = `X20`，`PaperPaddingVMinimal` = `X16`。

### 2.2 `InkShape`（圆角）

| 尺度 | 值 | 语义形状 |
| --- | --- | --- |
| `RadiusL` | `14.dp` | `Card`、`Paper`、`Seal` |
| `RadiusM` | `12.dp` | `SealCompact`、`Chip`、`MarkdownSub` |
| `RadiusS` | `10.dp` | `Tag`、`Stamp` |
| `RadiusXs` | `2.dp` | `QuoteBar` |
| `RadiusXl` | `18.dp` | `SkeletonCard`（首页骨架卡片 / 横幅；M6） |
| `RadiusXss` | `8.dp` | `Skeleton`、`CanvasSub`（骨架屏占位、分享卡画布子面；M6） |
| `RadiusMicro` | `3.dp` | `Legend`（个人中心图例色条；M6） |
| — | `percent=50` | `Pill`、`PillStart`、`PillEnd`（胶囊形状；替代历史 `999.dp`；M6） |

`InkShape.sealFor(size)`：`size ≤ SealCompactThreshold(44.dp)` → `SealCompact`，否则 `Seal`。

`PaperInkShapes` 将上述圆角注入 Material3 `Shapes`（extraSmall…extraLarge），使 Dialog / BottomSheet 等默认读纸墨圆角。

### 2.3 `InkBorder` 与 `InkTone`

**描边宽度**

| 令牌 | 值 | 用途 |
| --- | --- | --- |
| `Hairline` | `1.dp` | 卡片 / 纸面 / Chip / 自绘线 |
| `Stamp` | `2.dp` | 盖章印记与强调焦点环 |
| `TableCell` | `0.5.dp` | 表格细分隔 |
| `CanvasStroke` | `3.dp` | 分享卡画布竖线描边（M6） |
| `CalendarRing` | `1.6.dp` | 个人中心日历今日描边（M6） |
| `SpinnerStroke` | `2.dp` | 按钮加载态 CPI 描边，复用 `Stamp` 值（M6） |

**透明度（作用于 outline / primary 等）**

| 令牌 | 值 | 用途 |
| --- | --- | --- |
| `OutlineStrong` | `0.45` | 卡片与纸面外框 |
| `OutlineSoft` | `0.22` | 纸面横线、分隔线 |
| `OutlineSelected` | `0.80` | 选中描边 |
| `OutlineIdle` | `0.40` | InkChip 未选中 |
| `TagIdle` | `0.35` | TagChip 未选中 |
| `TableOutline` | `0.35` | 表格描边 |
| `MarginLine` | `0.35` | 左侧主题竖线 |
| `QuoteBar` | `0.30` | 引用竖条 |
| `ChipFillSelected` | `0.14` | InkChip 选中底 |
| `TableHeaderFill` | `0.55` | 表头 surfaceVariant 之上 |
| `StampFill` / `StampOutline` / `StampText` / `StampScrim` | `0.10` / `0.85` / `0.90` / `0.10` | 盖章层 |

**`InkTone`（不随色板变化的固定色）**：`TagTextOnLight`、`TagTextOnDark`、`PaperEdge`、`VermilionLine`、`ViewerBackdrop`、`InlineCodeBg`。

### 2.4 `InkMotion`（动效）

| 组 | 令牌 | 值 |
| --- | --- | --- |
| 印章按压 | `PressScale` / `PressDurationMs` | `0.92` / `120` |
| 盖章总时长 | `StampDurationDefaultMs` 及 Min/Max | `600`（`200..2000`） |
| 入退场占比 | `StampEnterRatio` / `StampExitRatio` | `0.45` / `0.35` |
| 入场关键帧 | 缩放 `1.42 → 0.94 → 1.00`；旋转 `-26° → -8° → -12°` | 见源码常量 |
| 退场趋向 | 缩放 `1.25`、旋转 `-24°` | |
| 图片查看器 | 自动隐藏 / 双击窗 / 放大倍率 / 上限 | `2200ms` / `320ms` / `2.5x` / `5x` |

- **约束**：新增共享动效必须使用 `InkMotion` + `ReducedMotion.current` 分支，业务页不得复制另一套印章物理感。

---

## 3. Color（颜色）

### 3.1 基础色事实

字面量集中于 `ColorSchemes.kt`：

| 源码颜色 | 色值 | 语义 |
| --- | --- | --- |
| `PaperBg` | `#F7F8F4` | 草白，浅色应用背景 |
| `PaperSurface` | `#FDFBF3` | 米宣纸，浅色卡片/纸面 |
| `InkText` | `#383431` | 焦茶，浅色主文字 |
| `InkSubText` | `#878885` | 雅灰，次要文字/描边来源 |
| `Vermilion` | `#FF4C39` | 朱砂 |
| `Indigo` | `#305169` | 黛蓝 |
| `NightBg` | `#18191B` | 墨灰，深色背景 |
| `NightSurface` | `#22252A` | 玄青，深色表面 |
| `NightText` | `#E0E0E0` | 银灰，深色主文字 |
| `Gold` | `#F2BE45` | 赤金 |
| `NeonCyan` | `#00E5BC` | 荧光青 |
| `MoonPrimaryDark` 等 | `#D9D4CD` 等 | 月白深色主/次色 |

浅色纸系与深色墨系还完整覆盖 **surface 容器阶**（`surfaceDim` / `surfaceContainer*` / `surfaceVariant` / `outlineVariant` 等），消除 M3 默认紫灰残留（M2.10）。

### 3.2 策展色板映射（浅色）

| Material 角色 | 纸墨 `PAPER_INK` | 黛蓝 `INDIGO` | 赛博 `CYBER` | 月白 `MOON_WHITE` |
| --- | --- | --- | --- | --- |
| `primary` | 朱砂 | 黛蓝 | 荧光青 | 焦茶 |
| `onPrimary` | 白 | 白 | `#0B1F1A` | 白 |
| `secondary` | 黛蓝 | 朱砂 | 朱砂 | 雅灰 |
| `background` / `surface` | 草白 / 米宣纸 | 同 | 同 | 同 |
| `onBackground` / `onSurface` | 焦茶 | 同 | 同 | 同 |

### 3.3 策展色板映射（深色）

| Material 角色 | 纸墨 | 黛蓝 | 赛博 | 月白 |
| --- | --- | --- | --- | --- |
| `primary` | 赤金 | 黛蓝 | 荧光青 | 米灰 `#D9D4CD` |
| `secondary` | 朱砂 | 赤金 | 赤金 | 中性灰 |
| `background` / `surface` | 墨灰 / 玄青 | 同 | 同 | 同 |
| `onSurface` | 银灰 | 同 | 同 | 同 |

### 3.4 `DYNAMIC` 与对比度

- **现状**：API 31+ 使用 `dynamicLightColorScheme` / `dynamicDarkColorScheme`；无 Context 或 API &lt; 31 回退 `PAPER_INK`。
- **现状**：`WcagContrastTest` 对 **4 套策展色板 × 明暗** 断言：`onBackground/background`、`onSurface/surface` ≥ `4.5:1`；`onPrimary/primary`、`onSecondary/secondary` ≥ `3:1`。`DYNAMIC` 不进自动矩阵（尽力而为）。
- **约束**：主色只用于强调、选择、光标、印章与边线，不作为大面积装饰。
- **约束**：新增色板必须先写 `ColorSchemes` 全角色 + WCAG 单测，再更新本文档。

### 3.5 原语取色（现状）

共享原语通过 `MaterialTheme.colorScheme` 取色：`InkCard` / 纸面用 `surface`；正文 `onSurface`；外框 `outline@OutlineStrong`；横线 `outline@OutlineSoft`；左侧竖线 `primary@MarginLine`。`TagChip` 在文墨质感下仍可按标签哈希生成低饱和 HSV 色块；清简质感下去彩色块、细描边、次要色文字，且保留 `#` 前缀。

---

## 4. Typography（字体与排版）

### 4.1 字体档

| 档 | 标题 | 正文 |
| --- | --- | --- |
| `WENKAI` | 霞鹜文楷 `R.font.lxgw_wenkai`（全量 TTF，OFL） | `FontFamily.SansSerif` |
| `SYSTEM` | `FontFamily.Serif` | `FontFamily.SansSerif` |

关于页署名：霞鹜文楷 · SIL Open Font License 1.1（`assets/licenses/OFL.txt`）。

### 4.2 显式字阶（`oneMemosTypography`）

| 角色 | 字重 | 字号 | 行高 | 用途 |
| --- | --- | --- | --- | --- |
| `headlineLarge` | SemiBold | `30.sp` | `38.sp` | 页面级标题、盖章文案 |
| `titleLarge` | SemiBold | `20.sp` | `26.sp` | 区块标题 |
| `bodyLarge` | Normal | `16.sp` | `24.sp` | 默认正文 |

其余 Material 角色沿用 M3 默认。`ScrollTextField` / `MikepenzMarkdown` 长文行高对齐 `InkSpacing.LinePitch`（`30.sp`）。

### 4.3 阅读模式（`ReadingConfig` / `LocalReadingConfig`）

| 正文字号档 | `bodyFontSize` | 基准行高 |
| --- | --- | --- |
| `SMALL` | `13.sp` | `18.sp` |
| `STANDARD` | `14.sp` | `20.sp` |
| `LARGE` | `16.sp` | `24.sp` |
| `EXTRA_LARGE` | `18.sp` | `28.sp` |

行距档系数：`COMPACT` `0.875` / `STANDARD` `1.0` / `RELAXED` `1.25`。设置页「阅读模式」暴露正文字号与行距；`OneMemosTheme` 提供 `LocalReadingConfig` 供列表/编辑正文读取。

- **约束**：不得关闭系统字体缩放。新增层级优先复用 Material 角色，反复出现的新层级才写入 `Typography.kt`。
- **约束**：保持「衬线/文楷标题、无衬线正文」；代码可用系统等宽，不引入第四种字体语气。

---

## 5. Components（可复用组件）

本节记录 `core/designsystem` 已存在的原语。间距 / 圆角 / 描边 / 动效一律取 §2 令牌。

### 5.1 `InkCard`

- 全宽 `Surface`，`InkShape.Card`，`surface` 底，`Hairline` + `outline@OutlineStrong`，无投影。
- 内边距 `CardPadding`；可点击时 `minimumInteractiveComponentSize` + `TouchTargetMin` 高度兜底。
- 变体：静态 / 点击 / 长按；支持 `enabled` 语义与 `paperInkFocusBorder`。
- 无系统 ripple；无独立加载/错误变体外观。

### 5.2 `ScrollPaper` / `ScrollPaperSurface`

- 纸面：`InkShape.Paper`、横线节距 `LinePitch`、左侧竖线 `MarginLineX`。
- **SCROLL 质感**：绘制横线 + 主题竖线；内容区左 `PaperPaddingStart` / 右 `PaperPaddingEnd` / 上下 `PaperPaddingV`。
- **MINIMAL 质感**：无横线，更大对称留白（Minimal 系列令牌）。
- `ScrollPaper` 内滚动；`ScrollPaperSurface` 接收外部 `scrollOffsetPx` 供 `LazyColumn` 组合。

### 5.3 `SealButton` / `SealIconButton`

- 印章：主色底、`onPrimary` 内容；按压 `PressScale` + haptic tick（`ReducedMotion` 时 snap、不触发动效缩放）。
- 默认尺寸：按钮 `56.dp`、图标钮视觉 `44.dp` + **外层 ≥48.dp 触控包围盒**。
- 禁用：容器/内容取 `LocalInkDisabledColors`（`onSurface×0.12` / `onSurface×0.38`，M3 惯例）；语义 `disabled()`。
- 焦点：`paperInkFocusBorder(emphasized = true)`（仅 `enabled` 时）。

### 5.4 `InkChip` / `TagChip`

- `InkChip`：克制筛选片；选中 `primary@ChipFillSelected` + 强描边 + primary 文字。M4-A2 已核销焦点环与禁用视觉：仅 `enabled` 时叠加 `paperInkFocusBorder`（`focused && enabled`）；禁用时文字/描边取 `LocalInkDisabledColors.content`（`onSurface×0.38`），语义 `disabled()`。
- `TagChip`：固定 `#` 前缀；文墨下可有稳定哈希色块，清简下退后为细描边次要色；已接焦点环（仅可点击时 `focused && clickable`）。无独立 `enabled` 参数，不走 `LocalInkDisabledColors`。
- **约束**：标签语义不得只靠色块；`#` 与文本必须可读。

**触控与选中语义（现状 vs 目标）**：

- **现状**：可点击 `TagChip` 源码注释写明「产品决策不套用 48dp」；`SettingsPrimitivesAccessibilityTest` 当前**不**断言 Chip 触控 ≥48dp，只守 `contentDescription` / `stateDescription`。`InkChip` 尚无外层 48dp 包围盒，也无 `SemanticsProperties.Selected` 机器可读选中态（主要靠颜色 + 描边）。
- **目标约束（UI 债务收口计划，2026-07-21 已批准，实现待 Todo 2）**：可交互 `TagChip` 与全部 `InkChip` 采用**双层**：外层语义/点击容器 ≥ `InkSpacing.TouchTargetMin`（48dp），内层 `Surface` 保持紧凑视觉（填充/描边色块不得撑成 48dp 巨型胶囊；可见面约 ≥40dp 视为视觉失败）。可点击/可选 Chip 必须暴露 `Selected` + 准确 `stateDescription`；静态未选 TagChip 不伪装按钮、不朗读「未选中」。

### 5.5 `ScrollTextField`

- `ScrollPaper` + `BasicTextField` / 只读可选文本；行高 `LinePitch`；占位用 `outline`。
- 无统一校验错误 / 加载原语外观。
- **M6 悬浮速记层接入**（`QuickCaptureOverlayService`）：`ScrollTextField` + `InkShape.Card`（圆角 14dp）+ `colorScheme.scrim` 全屏遮罩 + `InkCard`，形成纸墨悬浮输入体验。
  - **窗口**：`MATCH_PARENT × MATCH_PARENT` 全屏、`TYPE_APPLICATION_OVERLAY`、`FLAG_LAYOUT_IN_SCREEN`、可获焦；`softInputMode = SOFT_INPUT_ADJUST_RESIZE`（主信号：窗口随键盘缩放）；`fitInsetsTypes = 平台默认 | WindowInsets.Type.ime()`（1.15.2：HyperOS 默认 fitTypes 不含 IME，导致 ADJUST_RESIZE 与 insets 双信号均失效，故显式 OR 入 IME）；`WindowInsets.ime.bottom` 为兜底信号。
  - **几何**（`QuickCaptureOverlayGeometry`，双信号）：自由带 = `min(窗口实际高度, 全屏视口 − 钳制后的 IME 高度)`，两信号任一生效都把卡片限制在键盘上方，同时到达时不重复扣减；底部避让 = 窗口高度 − 自由带（仅窗口未被缩放时补偿）；卡片高度上限 = `min(全屏视口 × OverlayCardMaxHeightFraction, 自由带 − 2×OverlayCardMarginV)`；水平边距 `OverlayCardMarginH`。
  - **高度与键盘态**：卡片**内容自适应**、不超过上限（常态上限为视口 50%，`OverlayCardMaxHeightFraction = 0.5f`），居中于自由带；键盘升起时自由带收窄，卡片随之上移并进一步压缩上限。
  - **卡片内部结构**：主体（标题 → 草稿条）置于可滚动区，滚动区高度上限 = 卡高上限 − 底栏预留（卡片上下内边距 + 底栏上间距 + SealButton 高度）；底部「续写 / 取消 / 盖」动作行固定在卡片底边，不可随正文滚走。
  - **输入框高度**：`OverlayInputMinHeight`–`OverlayInputMaxHeight`（`X120`…`X150`，≈3–4 行）；`ScrollTextField` 传入 `fillMaxSize = false`，高度随文本在区间内自适应，超出 4 行后输入框内部滚动（`ScrollPaper` 新增 `fillMaxSize` 形参，默认 `true` 保持编辑器行为不变）。
  - **令牌**：`OverlayCardMaxHeightFraction`、`OverlayCardMarginH`（`X16`）、`OverlayCardMarginV`（`X16`）、`OverlayThumbSize`、`OverlayThumbBadgeSize`、`OverlayInputMinHeight`、`OverlayInputMaxHeight`。已退役：`OverlayImeLiftMax` / `OverlayImeLiftFactor` / `X140`（旧「按 IME 比例抬升 + 140dp 上限」方案）、固定半屏卡高写法与 `X324` 输入框 10 行最大高（1.15.1 起收敛为 `X150` ≈4 行）。

### 5.6 Markdown：全量阅读 `MikepenzMarkdown` 与列表预览 `MarkdownPreview`

- **编辑器完整阅读（现状，M4-C1）**：唯一实现为 `markdown2/MikepenzMarkdown`（mikepenz multiplatform-markdown-renderer M3）。覆盖单栏阅览、双栏右栏、只读查看；纸墨令牌映射 `colorScheme` + `Ink*`；正文行高对齐 `InkSpacing.LinePitch`；最内层 `SelectionContainer` 支持选择复制。空内容显示调用方 `placeholder`（outline 色）。
- **列表/卡片预览（现状）**：继续使用 `MarkdownPreview`（commonmark 轻量预览），调用方为 home / profile / collections。
- **纯文本（现状）**：`markdownToPlainText` / `markdownToPlainPreview`（与 Preview 同源 commonmark 扩展集）。
- **双引擎开关退役（M4-C1）**：全量 `MarkdownPaper` 与 `useNewMarkdownEngine` 的 model / DataStore / ViewModel / UI 分支已删除；无用户设置开关；历史磁盘 key 不再读取且不迁移。
- **依赖职责**：commonmark 服务 Preview / plain；mikepenz 服务编辑器全量阅读，两套依赖独立。

### 5.7 `SealStampOverlay`

- 全屏盖章反馈；时长与关键帧取 `InkMotion`；`ReducedMotion` 时 alpha/scale/rotation 走 `snap()`。
- **约束**：表达一次性结果，不能代替持久状态文案或 TalkBack 播报。

### 5.8 `TagFilterBottomSheet` / `ImageViewerDialog`

- 标签筛选：排除、或/与匹配、清空、FlowRow 标签；水平边距 `SheetMarginH`。
- 图片查看器：缩放/拖拽/双击、chrome 自动隐藏；固定色见 `InkTone`。

### 5.9 状态原语（M3.4）

| 组件 | 职责 |
| --- | --- |
| `InkLoading` | 全幅居中进度；默认指示色 `surfaceVariant` |
| `InkEmpty` | 图标 + 安静文案 + 可选动作 |
| `InkError` | error 图标/文案 + 重试；`LiveRegionMode.Assertive` |
| `InkRetryBanner` | 内联重试横幅；`surfaceVariant` 底 + Hairline；`LiveRegionMode.Polite` |

M4-A 已接入范围（提交 `3ca78a7` / `caecf83` / `4457877` / `b00afbb`）：home 列表加载/错误/空态/追加加载与同步横幅、home 收藏对话框 busy、settings 记录编辑与提醒日历加载、sharecard 加载/错误/导出进度。Auth 与 Home 按钮内 `CircularProgressIndicator` 为明确按钮加载态豁免，不迁入状态原语。其余 feature 未宣称全量迁移。

**UI 债务收口状态范围（设计裁决，实现待产品波次）**：

- **仅迁移** Editor 同步失败横幅（`SyncStatus.FAILED` + `lastSyncError`）为等价 `InkRetryBanner`（文案前缀「同步失败：」、动作「重试同步」、polite live region、单次 `retrySync`）。
- **明确保留**（不得为了“统一”改布局/动作类型）：Collections 空态（InkCard + busy-aware SealButton）、Profile 安静空态卡片、Todo 双动作空态、Editor 无 retry 的 `loadError` early return、紧凑弹层空文案。
- 不为 Todo 双动作或紧凑弹层扩展 `InkEmpty` 形态。

### 5.10 M3 系统组件纸墨化（`PaperInkComponents`）

- 全局：`PaperInkShapes` + 全令牌 `ColorScheme`。
- 显式包装：`PaperInkTopAppBar`、`PaperInkSnackbar` / Host、`PaperInkAlertDialog`、`PaperInkModalBottomSheet`（及 defaults 色/形）。
- 容器色示例：顶栏 `surface`；Snackbar `inverseSurface`；Dialog `surfaceContainerHigh`；Sheet `surfaceContainerLow`。

**生产接线（现状 vs 目标）**：

- **现状**：包装 API 与截图 smoke 已存在；业务屏仍大量调用 raw `TopAppBar` / `ModalBottomSheet` / `SnackbarHost`（生产调用 `PaperInk*` 为零）。`AlertDialog` 形状/颜色已走全局主题，不强制全量改包装。
- **目标接线面（已批准，实现待 Todo 4–7/9）**：8 个业务 `TopAppBar`、5 个非悬浮层 `ModalBottomSheet`、Home `SnackbarHost` 显式改用对应 `PaperInk*`；**禁止**改 `QuickCaptureOverlayService` 内 raw Dialog/BottomSheet（1.15.2 已验收悬浮速记）。
- **契约扫描根（目标）**：`app/src/main`、全部 `feature/*/src/main`、`core/designsystem/src/main`；共同豁免 `PaperInkComponents.kt`；ModalBottomSheet 仅额外豁免 overlay 服务。

### 5.11 其它 Material 组合

底部开关、`OutlinedButton`、`TextButton` 等可直接使用 M3 + 当前 `MaterialTheme`。无独立二次原语时，不得假装已有统一纸墨皮肤。

---

## 6. Motion & Interaction（动效与交互）

### 6.1 现有行为

| 行为 | 参数来源 | 说明 |
| --- | --- | --- |
| 印章按压 | `InkMotion` + `ReducedMotion` | 压印手感；减少动效时无缩放动画 |
| 印章触感 | `EFFECT_TICK` | 有效点击；减少动效路径可跳过缩放但仍可点按 |
| 盖章反馈 | `InkMotion` 时长与关键帧 | 保存/归档/完成仪式感 |
| 纸面横线 | 滚动对 `LinePitch` 取模 | 仅 SCROLL 质感 |
| 页面转场 | Navigation3 `transitionSpec` 族 + `SharedTransitionLayout` | fade + 共享轴；见 ADR 0012 |
| 减少动效门控 | `ReducedMotion.current` | 系统动画缩放归零 **或** `pageTransitionsEnabled=false` |

### 6.2 规则

- **现状**：`InkCard` / Chip / 印章主动取消 ripple；印章提供按压缩放。
- **约束**：新增动效必须表达按压、滚动关系、导航或状态完成，禁止纯装饰循环动画。
- **约束**：`ReducedMotion.current == true` 时，转场与印章动效应即时/无动画，结果文案仍立即可见。

### 6.3 Shared bounds（卡片 → 编辑器）

- **现状（宿主）**：`AppNavigationHost` 已提供 `SharedTransitionLayout`，并在 `ReducedMotion` 时将 `NavDisplay.sharedTransitionScope` 置 `null`；页面级 fade / 共享轴已接线。
- **现状（业务）**：Home `MemoItem` 与 Editor 内容根**尚未**挂 `sharedBounds`；无仓库级统一 key helper。
- **目标约束（UI 债务收口，实现待 Todo 1/8）**：
  1. 仅在 `core:navigation` 提供唯一 `LocalMemoSharedTransitionScope`（nullable）+ `Modifier.memoSharedBounds(uuid)`；key 形如 `memo/<uuid>`；null/blank UUID 与 null scope 必须 early return，禁止条件调用 `rememberSharedContentState` 破坏 slot 稳定性。
  2. 配对范围：**仅** Home 活跃分区已有随笔（`HomeScreenMode.ACTIVE` + 非空 uuid）→ `EditorKey(uuid!=null)` 内容根容器；新建、归档列表、跨顶层分区、进程恢复不参与。
  3. Reduced Motion：继续向 CompositionLocal / `NavDisplay` 注入 `null` scope，不新增第二套开关。
  4. 不引入 Navigation 2 或社区 `SharedEntryInSceneNavEntryDecorator` / 外部 local 命名。
  5. 降级条件仍见 ADR 0012（掉帧、bounds 错位、Scene 冲突 → 关共享元素，保留 fade + shared-axis）。

---

## 7. Depth & Surface（层次与表面）

1. `background` 底层 + `surface` 纸面/卡片（浅色草白/米宣纸，深色墨灰/玄青）。
2. `1.dp outline@OutlineStrong` 定界，不使用 elevation 阴影制造层级。
3. 文墨质感：横线 + 左侧主题竖线；清简：细描边与更大留白。
4. 选择态用描边/轻底/文字色；印章用实色底 + 压印。
5. 盖章层用半透明印泥色与短时遮罩，仍无阴影。

- **约束**：新增共享表面先组合纸 / 卡片 / 印章材料，禁止玻璃、模糊、发光、渐变或悬浮阴影。
- **约束**：圆角只复用 `InkShape` 语义。

---

## 8. Accessibility Constraints & Debt（可访问性约束与债务）

### 8.1 后续界面的强制约束

- **触控目标**：可点区域至少 `48.dp × 48.dp`（`InkSpacing.TouchTargetMin` / `minimumInteractiveComponentSize`）。视觉可更紧凑，但交互包围盒不得更小。
- **语义名称**：纯图标必须提供面向动作的 `contentDescription`；装饰图形空语义。
- **对比度**：普通文本 ≥ `4.5:1`，大号/粗体与非文本关键控件 ≥ `3:1`；策展色板由 `WcagContrastTest` 守门。
- **字体缩放**：不得关闭系统字体缩放；固定印章与单行 Chip 需单独验证。
- **减少动态效果**：统一读 `ReducedMotion.current`；启用时移除非必要缩放/旋转/转场。
- **TalkBack**：组合控件稳定遍历顺序；动态弹层/错误/结果须可聚焦或 live region；关闭后焦点归还触发源。
- **状态不可只靠颜色或触感**：选择/成功/错误/同步须有文字、图标或语义状态。
- **焦点可见性**：取消 ripple 后须有 `paperInkFocusBorder` 或等价焦点环。
- **内容状态**：加载/空/错误/重试优先使用 §5.9 状态原语。

### 8.2 债务表核销（M3.5）

| 债务 | 原位置 | 核销结论 | 依据 / 保留理由 |
| --- | --- | --- | --- |
| 紧凑控件不足 `48.dp` | `SealIconButton` 默认 `44.dp`；Chip 无最小高度 | **已核销（印章）**；Chip **部分保留** | `SealIconButton` 外层 `minimumInteractiveComponentSize` + `defaultMinSize(TouchTargetMin)`。`InkCard` 可点击路径同样兜底。`InkChip`/`TagChip` 视觉仍可 &lt;48.dp，依赖 Material 最小触控与调用方 `modifier`；未在 Chip 内强制 `defaultMinSize`。 |
| 自定义焦点态缺失 | 取消 ripple 的共享原语 | **已核销** | `PaperInkFocusIndicator.paperInkFocusBorder` 已接入 `InkCard`、`SealButton`、`SealIconButton`、`TagChip`、`InkChip`（shape=`InkShape.Chip`）。M4-A2（`caecf83`）：`InkChip` 仅 enabled 时显示焦点环；`TagChip` 仅可点击时显示。 |
| reduced-motion 未统一 | 印章按压 / 盖章 / 转场 | **已核销** | `ReducedMotion` 读系统动画缩放 + `providesFromPageTransitions`；`SealButton`/`SealIconButton`/`SealStampOverlay`/`AppNavigationHost` 已门控。 |
| 禁用视觉不完整 | 印章与 `InkChip` | **已核销** | `LocalInkDisabledColors`（`inkDisabledColorsOf`：`onSurface×0.12` 容器 / `×0.38` 内容）由 `OneMemosTheme` 下发；`SealButton`/`SealIconButton`/`InkChip` 已接入（M4-A2 / `caecf83`）；语义 `disabled()` 保留。`TagChip` 无独立禁用参数，不在此项。禁用态对比度享 WCAG inactive 豁免。 |
| 通用状态原语缺失 | 设计系统 / 业务屏 | **已核销（M4-A 范围）** | 原语 `InkLoading`/`InkEmpty`/`InkError`/`InkRetryBanner` 已落地并测（`3ca78a7`）。M4-A 业务接入：home 列表与同步横幅、home 收藏对话框、settings 记录编辑与提醒日历、sharecard（`4457877`/`b00afbb` 等）。Auth/Home 按钮内 CPI 为加载态豁免。未宣称全应用所有 loading 已迁移。 |
| Markdown 全量双引擎并存 | 编辑器 / 设计系统 | **已核销（M4-C1）** | 提交 `4273387`：编辑器完整阅读唯一实现 `MikepenzMarkdown`；`MarkdownPreview` / plain 保留；`MarkdownPaper` 与 `useNewMarkdownEngine` 全链路删除；无设置 UI；历史磁盘 key 不读不迁。 |
| 字体放大适配未形成契约 | 固定印章、单行 Chip、表格 | **保留** | 无组件级字体缩放验收契约与金图矩阵；文楷印章在大字体下仍需人工验证。 |
| TalkBack 遍历与动态播报未系统化 | 卡片 / Chip / 盖章 / 弹层 | **部分核销** | 状态原语 live region；首页 `MemoItemTalkBack` 合并朗读；设置矩阵测有部分覆盖。关键路径（浏览→编辑→保存→归档）全流程焦点恢复规范仍未系统化。 |
| 选择与结果偏重颜色或触感 | Chip / 盖章 | **部分保留** | 选中仍主要靠 primary 色与描边；标签保留 `#` 文本。盖章为短暂视觉仪式，成功路径依赖业务文案/Snackbar（如归档撤销）。关闭颜色后 Chip 可读性仍弱。 |
| 色板对比度无自动证据 | 六组主题 | **已核销（策展色板）** | `WcagContrastTest` 覆盖 4 策展色板 × 明暗 × 关键对。`DYNAMIC` 明确不进矩阵。 |
| 裸 dp / 令牌收敛 | 悬浮层 / 壳层 / feature 模块 | **已核销（M6）** | M6 令牌收敛波：悬浮层（`QuickCaptureOverlayService` 接入 `ScrollTextField` 等纸墨原语）、壳层（`OneMemosApp` 抽屉与更新弹窗）、feature 模块（home/editor/todo/collections/quickcapture/sharecard/profile/auth/welcome/settings）全部收敛至 `InkSpacing`/`InkShape`/`InkBorder`；唯一残留为 `0.dp`（零值不令牌化）。初始裸 dp 数量：home 69→19、editor 18→7、settings 116→1 等，最终生产源码（`src/main/`）裸 `[1-9][0-9]*.dp` 零匹配。 |

以上未核销项不构成永久豁免。触及对应原语的改动应优先消除债务，并同步本文档。

### 8.3 UI 债务收口跟踪表（2026-07-21 设计冻结）

下列条目来自已批准计划 `.omo/plans/2026-07-21-ui-debt-closeout.md`（Momus/Oracle 双审通过）。**均为目标约束**，在产品 Todo 落地前不得写为「已接线 / 已通过真机 / 已发布」。

| 债务 | 现状 | 目标 | 计划 Todo | 状态 |
| --- | --- | --- | --- | --- |
| Chip 48dp 外触控 + 选中语义 | TagChip 明确拒绝 48dp；InkChip 缺 Selected | 双层 48dp + Selected/stateDescription；视觉紧凑 | 2, 3 | 待实现 |
| PaperInk 生产接线 | 包装有、业务 raw 调用多 | 8 TopAppBar + 5 非 overlay Sheet + 1 Home Snackbar | 4–7, 9 | 待实现 |
| Editor 同步失败横幅 | 自定义 Row | 等价 `InkRetryBanner` | 5 | 待实现 |
| Collections/Profile/Todo 空态 | 各自布局 | **保持**，不迁 `InkEmpty` | 5, 6 | 设计冻结（不改） |
| Shared bounds 配对 | 仅宿主 scope | Home ACTIVE memo ↔ Editor 内容根 | 1, 8 | 待实现 |
| Roborazzi 矩阵 | designsystem 仅 smoke | 明暗 + fontScale=2.0；含 Chip/PaperInk/MemoItem | 3, 10 | 待实现 |
| 宏基准 fail-fast | best-effort 可空数据假绿 | resource-id + 硬断言 Home→Editor→Back | 11 | 待实现 |
| 稳定版 1.16.0 | 当前 1.15.2 (167) | 固定签名 Benchmark + Tag + latest Release | 14–18 | 待发布 |
| 悬浮速记 | 1.15.2 已验收 | **字节不变**；不迁 overlay Dialog/Sheet | 全波次 | 冻结排除 |

---

## 9. Accessibility Implementation Notes（无障碍实现备忘）

本节记录 **已落地的共享能力**，与 §8.1 约束对照。

### 9.1 `ReducedMotion`

- 位置：`ui/accessibility/ReducedMotion.kt`。
- `current = Local || isSystemReducedMotionEnabled()`（animator / transition / window scale 任一为 `0`）。
- 应用层：`ReducedMotion.providesFromPageTransitions(pageTransitionsEnabled)`；关闭页面转场等效减少动效。
- 覆盖：页面 `ContentTransform`、印章按压缩放、盖章 alpha/scale/rotation。

### 9.2 焦点环

- `PaperInkFocusIndicator`：`primary` 描边；常规宽 `Hairline`，强调宽 `Stamp`。
- 聚焦才叠加 border，未聚焦零额外分配。
- 已接入：`InkCard`、`SealButton`、`SealIconButton`、`TagChip`、`InkChip`。

### 9.7 禁用视觉令牌

- `InkDisabledColors` + `LocalInkDisabledColors`：容器 `onSurface×0.12`、内容 `onSurface×0.38`（M3）。
- 由 `OneMemosTheme` 按当前 `colorScheme` 推导下发；`SealButton`/`SealIconButton`/`InkChip` 共用。

### 9.3 触控目标

- 令牌 `TouchTargetMin = 48.dp`。
- 印章图标钮：外层 ≥48.dp，内层默认 44.dp 视觉。
- 可点击 `InkCard`：最小高度兜底。
- **Chip（现状）**：可点击 TagChip **未**强制 48dp；见 §5.4 / §8.3。目标双层契约落地前，不得引用 QA 文档中的过时「TagChip 已补 48dp」表述作为实现事实。

### 9.4 TalkBack 与 live region

- 纯图标印章强制 `contentDescription` + `Role.Button`；禁用时 `semantics { disabled() }`。
- `InkError` assertive、`InkRetryBanner` polite。
- 首页卡片：`MemoItemTalkBack.contentDescription` 合并时间、摘要、同步/归档状态、标签与多选状态。

### 9.5 对比度

- 纯 JVM `WcagContrast` + 参数化测试；策展色板变更必须保持绿。

### 9.6 阅读与系统缩放

- 阅读字号/行距：`ReadingConfig` + 设置 UI。
- 系统字体缩放：不关闭；大字体下固定容器裁切仍属 §8.2 保留债务。

---

## 10. Related ADRs & Domain Vocabulary

| ADR | 主题 |
| --- | --- |
| 0008 | 色板 × 质感 × 密度 × 字阶（+ 字体档）描述符 |
| 0009 | 霞鹜文楷全量 + arm64-only |
| 0010 | Markdown 换 mikepenz，纸墨皮肤 |
| 0011 | 无真删除；收藏 = 锦囊文件夹 |
| 0012 | Navigation3 共享元素 / 转场 + ReducedMotion |

领域用词见仓库根目录 `CONTEXT.md`（外观轴、风格预设、随笔生命周期）。

---

## 11. 相关执行计划与会话

| 产物 | 路径 | 说明 |
| --- | --- | --- |
| UI 债务收口计划 | `.omo/plans/2026-07-21-ui-debt-closeout.md` | 18 Todo + F1–F4；目标版本 1.16.0 (168) |
| 决策草稿 | `.omo/drafts/2026-07-21-ui-debt-closeout.md` | 范围、裁决、双审收据 |
| 会话记录 | `.ai_session.md` | 发布与规划时间线 |

实现须以计划 acceptance 与本文「现状 / 约束 / 债务」三分法为准；纯文档更新不得推进 versionCode。
