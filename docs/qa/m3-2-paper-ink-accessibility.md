# M3.2 纸墨无障碍清债 — 验收记录

**任务**：Checkbox 26 / M3.2 无障碍清债 + 2026-07-21 UI 债务收口（Chip / Roborazzi）
**日期**：2026-07-18（M3.2 实现）；2026-07-21 更正文档；2026-07-21 产品收口回写
**范围**：设计系统原语 + Home 浏览主路径 + Settings 矩阵扩展 + Chip 48dp / 截图矩阵

## 1. 自动化门禁（以当前源码/测试名为准）

| 套件 | 命令 | 结果 |
|------|------|------|
| 原语 a11y | `./gradlew :core:designsystem:testDebugUnitTest --tests cc.pscly.onememos.ui.component.SettingsPrimitivesAccessibilityTest --tests cc.pscly.onememos.ui.accessibility.*` | **PASS**（含可点击 TagChip/InkChip ≥48dp + Selected） |
| Settings 矩阵 | `./gradlew :feature:settings:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.settings.SettingsAccessibilityMatrixTest` | **PASS**（11/11） |
| Memo TalkBack 文案 | `./gradlew :feature:home:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.home.MemoItemTalkBackTest` | **PASS**（3/3） |
| PaperInk/Chip 截图 | `./gradlew :core:designsystem:verifyRoborazziDebug --tests cc.pscly.onememos.ui.theme.PaperInkComponentsScreenshotTest` | **PASS**（light/dark/fontScale=2 + PaperInkDialog + 默认 Dialog） |
| MemoItem 截图 | `./gradlew :feature:home:verifyRoborazziDebug --tests cc.pscly.onememos.ui.feature.home.MemoItemScreenshotTest` | **PASS**（light/dark/fontScale=2） |

矩阵相对 M3.0 新增（名称以测试源码为准）：

- `largeFont_allCapabilityPages_keepRootOrKeySemantics`（存储/外观/提醒/关于/记录 @ fontScale=2）
- `paperInkFocusTokens_andHubTouchTargetContract_remainGreen`（焦点环令牌 + Hub 48dp）
- `clickableTagChip_hasAtLeast48TouchTarget_selectedSemanticsAndDescription` 等 Chip 48dp/Selected 断言
- `PaperInkComponentsScreenshotTest` / `MemoItemScreenshotTest` 三态矩阵

原语：

- 印章 / 可点击 InkCard：≥48dp 触控 + `contentDescription` 守护
- `sealStampOverlay_visible_hasContentDescriptionAndSurvivesLargeFont`（fontScale=2）
- TagChip / InkChip：外层 ≥48dp（可交互路径）+ `Selected` / `stateDescription`；静态未选 TagChip 不伪装按钮

### 1.1 文档纠错时间线

| 日期 | 说明 |
| --- | --- |
| 2026-07-21 上午 | 撤销「TagChip 48dp 已落地」过时表述；当时源码尚未强制 48dp |
| 2026-07-21 傍晚 | 产品 Todo 2/3/10 落地后，Chip 48dp unit + Roborazzi 矩阵均为 **PASS**；真机项仍待 Todo 15 |

## 2. 实现摘要

| 项 | 改动 |
|----|------|
| 48dp 触控 | **印章** 外层 ≥48dp（视觉可 44dp）；**可点击 InkCard** 最小高度兜底；**Hub 行** 矩阵守护；**Chip** 外层 ≥48dp + 内层紧凑视觉（双层） |
| 纸墨焦点环 | `PaperInkFocusIndicator`；InkCard/SealButton/SealIconButton/TagChip/InkChip 复用（Chip 仅可交互时） |
| TalkBack | MemoItem 合并 `contentDescription`（时间+摘要+状态+标签）；列表 `isTraversalGroup`；FAB「新建随笔」 |
| 大字体 / 文楷印章 | `SealStampOverlay` 随 fontScale 扩张最小边 + `wrapContentSize(unbounded)` |
| 截图 | designsystem PaperInk+Chip 矩阵；home MemoItem 稳定样本（固定时间戳、无网络图） |

## 3. TalkBack 人工过验清单（设备就绪后勾选）

**环境**：Android 系统 TalkBack 开启；纸墨主题；字体默认 / 最大档各一轮。

| 流 | 期望 | 状态 |
|----|------|------|
| 首页列表 | 每条随笔单节点朗读「随笔，时间，摘要，同步状态…」；「更多操作」可单独聚焦 | ⬜ 待真机 |
| 浏览→编辑 | 点随笔进入编辑；返回列表焦点仍合理 | ⬜ 待真机 |
| 保存盖章 | 盖章浮层播报印章文案（如「已存」）；大字体不裁切 | ⬜ 待真机 |
| 归档 | 归档成功反馈可读；列表项状态变为已归档相关文案 | ⬜ 待真机 |
| 标签 | 朗读含「标签 #xxx」；可点击 Chip 触控 ≥48dp（unit **PASS**） | ⬜ 待真机；unit ✅ |
| 设置 Hub | 行 1–6 顺序与 unit 一致（既有矩阵 PASS） | ✅ unit |
| 焦点环 | 外接键盘/D-pad 焦点时 primary 描边可见 | ⬜ 待真机 |
| Shared bounds / Home→Editor 转场 | 仅 ACTIVE 已有 memo 参与；Reduced Motion 关闭 bounds | ⬜ 待真机（Todo 15）；代码接线 ✅ |

**无 ADB 设备时**：人工 TalkBack 与系统字体最大档截图统一标记 **SKIPPED_NO_DEVICE**，不以设备 PASS 宣称。

## 4. 系统字体最大档截图

建议目录：`docs/qa/screenshots/m3-2-a11y/`（真机截图本交付可不提交二进制）。

| 表面 | 期望 | 状态 |
|------|------|------|
| 首页 Memo 卡片 | 时间/摘要/标签/状态无横向裁切 | unit Roborazzi ✅ fontScale=2；真机 ⬜ SKIPPED_NO_DEVICE |
| SealStampOverlay | 文楷/标题字「已存」等在印章内完整或省略号，不溢出不可读 | ✅ unit 语义 + 布局扩张；真机截图 ⬜ |
| 设置外观/Hub | 与既有 nav3 矩阵一致 | ✅ unit 大字体语义 |
| Chip 紧凑视觉 vs 48dp 外框 | 可见色块不得呈巨型胶囊；语义外框 ≥48dp | ✅ unit + Roborazzi；真机 ⬜ |

## 5. 相关文件

- `core/designsystem/.../accessibility/PaperInkFocusIndicator.kt`
- `core/designsystem/.../component/{InkCard,SealButton,SealIconButton,TagChip,InkChip,SealStampOverlay}.kt`
- `feature/home/.../{MemoItem,MemoItemTalkBack,HomeScreen}.kt`
- `SettingsAccessibilityMatrixTest` / `SettingsPrimitivesAccessibilityTest` / `MemoItemTalkBackTest`
- `PaperInkComponentsScreenshotTest` / `MemoItemScreenshotTest`
- 设计跟踪：`DESIGN.md` §5.4 / §8.3；计划：`.omo/plans/2026-07-21-ui-debt-closeout.md`

## 6. 结论

- **已落地（M3.2 + 收口 Todo 2/3/10）**：印章/InkCard/Hub/Chip 触控守护、焦点环令牌、Memo 合并朗读、印章大字体语义、Settings 矩阵、Chip 48dp unit、PaperInk/MemoItem Roborazzi 矩阵。
- **真机**：TalkBack 与最大档人工截图仍 **待真机 / SKIPPED_NO_DEVICE**，待产品收口 Todo 15 设备 QA 后勾选。
- 禁止在无设备证据时把真机项写为 PASS。
