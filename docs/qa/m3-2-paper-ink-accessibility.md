# M3.2 纸墨无障碍清债 — 验收记录

**任务**：Checkbox 26 / M3.2 无障碍清债
**日期**：2026-07-18（实现）；2026-07-21 更正文档与源码/测试不一致处
**范围**：设计系统原语 + Home 浏览主路径 + Settings 矩阵扩展

## 1. 自动化门禁（以当前源码/测试名为准）

| 套件 | 命令 | 结果 |
|------|------|------|
| 原语 a11y | `./gradlew :core:designsystem:testDebugUnitTest --tests cc.pscly.onememos.ui.component.SettingsPrimitivesAccessibilityTest --tests cc.pscly.onememos.ui.accessibility.*` | **PASS**（M3.2 交付时：SettingsPrimitives / PaperInkFocus / ReducedMotion） |
| Settings 矩阵 | `./gradlew :feature:settings:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.settings.SettingsAccessibilityMatrixTest` | **PASS**（11/11） |
| Memo TalkBack 文案 | `./gradlew :feature:home:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.home.MemoItemTalkBackTest` | **PASS**（3/3） |

矩阵相对 M3.0 新增（名称以测试源码为准）：

- `largeFont_allCapabilityPages_keepRootOrKeySemantics`（存储/外观/提醒/关于/记录 @ fontScale=2）
- `paperInkFocusTokens_andHubTouchTargetContract_remainGreen`（焦点环令牌 + Hub 48dp）

原语（M3.2 实际落地）：

- 印章 / 可点击 InkCard：≥48dp 触控 + `contentDescription` 守护
- `sealStampOverlay_visible_hasContentDescriptionAndSurvivesLargeFont`（fontScale=2）
- TagChip：保留 `contentDescription` / `stateDescription`；**当前不强制 48dp**（见 §2 更正）

### 1.1 文档纠错（2026-07-21）

| 旧文档表述 | 当前事实 | 目标（UI 债务收口，待实现） |
| --- | --- | --- |
| `tagChip_clickable_hasMin48TouchTarget_andTagContentDescription` 已存在且 PASS | `TagChip.kt` 注释写明「产品决策不套用 48dp」；`SettingsPrimitivesAccessibilityTest` 对 clickable TagChip **不再**断言 48dp，只守语义 | 双层 48dp 外触控 + 紧凑视觉；可点击/可选 Chip 暴露 `Selected` |
| 「TagChip 可点击态补 `minimumInteractiveComponentSize` + `TouchTargetMin`」 | 源码未强制外层 48dp | Todo 2 恢复契约；测试名将按实现同步 |
| 「纸墨 48dp … 已落地」笼统结论 | **印章 / InkCard / Hub 行** 48dp 已守护；**Chip 48dp 未完成** | §8.3 / 计划 Todo 2–3 |

## 2. 实现摘要（M3.2 实际）

| 项 | 改动 |
|----|------|
| 48dp 触控 | **印章** `SealIconButton` 外层 ≥48dp（视觉可 44dp）；**可点击 InkCard** 最小高度兜底；**Hub 行** 矩阵守护。**Chip：未强制 48dp（债务）** |
| 纸墨焦点环 | `PaperInkFocusIndicator`；InkCard/SealButton/SealIconButton/TagChip/InkChip 复用（Chip 仅可交互时） |
| TalkBack | MemoItem 合并 `contentDescription`（时间+摘要+状态+标签）；列表 `isTraversalGroup`；FAB「新建随笔」 |
| 大字体 / 文楷印章 | `SealStampOverlay` 随 fontScale 扩张最小边 + `wrapContentSize(unbounded)` |

## 3. TalkBack 人工过验清单（设备就绪后勾选）

**环境**：Android 系统 TalkBack 开启；纸墨主题；字体默认 / 最大档各一轮。

| 流 | 期望 | 状态 |
|----|------|------|
| 首页列表 | 每条随笔单节点朗读「随笔，时间，摘要，同步状态…」；「更多操作」可单独聚焦 | ⬜ 待真机 |
| 浏览→编辑 | 点随笔进入编辑；返回列表焦点仍合理 | ⬜ 待真机 |
| 保存盖章 | 盖章浮层播报印章文案（如「已存」）；大字体不裁切 | ⬜ 待真机 |
| 归档 | 归档成功反馈可读；列表项状态变为已归档相关文案 | ⬜ 待真机 |
| 标签 | 朗读含「标签 #xxx」；**触控 ≥48dp 在 Chip 收口前不可宣称 unit PASS** | ⬜ 待真机；48dp unit ⬜ 待 Todo 2 |
| 设置 Hub | 行 1–6 顺序与 unit 一致（既有矩阵 PASS） | ✅ unit |
| 焦点环 | 外接键盘/D-pad 焦点时 primary 描边可见 | ⬜ 待真机 |
| Shared bounds / Home→Editor 转场 | 仅 ACTIVE 已有 memo 参与；Reduced Motion 关闭 bounds（计划验收） | ⬜ 待真机（Todo 15） |

**无 ADB 设备时**：人工 TalkBack 与系统字体最大档截图统一标记 **SKIPPED_NO_DEVICE**，不以设备 PASS 宣称。

## 4. 系统字体最大档截图

建议目录：`docs/qa/screenshots/m3-2-a11y/`（本交付不提交二进制）。

| 表面 | 期望 | 状态 |
|------|------|------|
| 首页 Memo 卡片 | 时间/摘要/标签/状态无横向裁切 | ⬜ SKIPPED_NO_DEVICE |
| SealStampOverlay | 文楷/标题字「已存」等在印章内完整或省略号，不溢出不可读 | ✅ unit 语义 + 布局扩张；截图 ⬜ |
| 设置外观/Hub | 与既有 nav3 矩阵一致 | ✅ unit 大字体语义 |
| Chip 紧凑视觉 vs 48dp 外框 | 可见色块不得呈巨型胶囊；语义外框 ≥48dp | ⬜ 待 Todo 2/3 Roborazzi + 真机 |

## 5. 相关文件

- `core/designsystem/.../accessibility/PaperInkFocusIndicator.kt`
- `core/designsystem/.../component/{InkCard,SealButton,SealIconButton,TagChip,InkChip,SealStampOverlay}.kt`
- `feature/home/.../{MemoItem,MemoItemTalkBack,HomeScreen}.kt`
- `SettingsAccessibilityMatrixTest` / `SettingsPrimitivesAccessibilityTest` / `MemoItemTalkBackTest`
- 设计跟踪：`DESIGN.md` §5.4 / §8.3；计划：`.omo/plans/2026-07-21-ui-debt-closeout.md`

## 6. 结论

- **已落地（M3.2）**：印章/InkCard/Hub 触控守护、焦点环令牌、Memo 合并朗读、印章大字体语义、Settings 矩阵自动化。
- **文档曾高估**：TagChip/InkChip **48dp 触控未完成**；不得再写「Chip 48dp 已落地」。
- **真机**：TalkBack 与最大档截图仍 **待真机 / SKIPPED_NO_DEVICE**，待产品收口 Todo 15 设备 QA 后勾选。
- **后续实现**须同步更新本文件的测试名、§1 结果与 §3–§4 状态；禁止在无证据时写 PASS。
