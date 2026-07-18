# M3.2 纸墨无障碍清债 — 验收记录

**任务**：Checkbox 26 / M3.2 无障碍清债  
**日期**：2026-07-18  
**范围**：设计系统原语 + Home 浏览主路径 + Settings 矩阵扩展  

## 1. 自动化门禁

| 套件 | 命令 | 结果 |
|------|------|------|
| 原语 a11y | `./gradlew :core:designsystem:testDebugUnitTest --tests cc.pscly.onememos.ui.component.SettingsPrimitivesAccessibilityTest --tests cc.pscly.onememos.ui.accessibility.*` | **PASS**（SettingsPrimitives 6、PaperInkFocus 3、ReducedMotion 2） |
| Settings 矩阵 | `./gradlew :feature:settings:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.settings.SettingsAccessibilityMatrixTest` | **PASS**（11/11） |
| Memo TalkBack 文案 | `./gradlew :feature:home:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.home.MemoItemTalkBackTest` | **PASS**（3/3） |

矩阵相对 M3.0 新增：

- `largeFont_allCapabilityPages_keepRootOrKeySemantics`（存储/外观/提醒/关于/记录 @ fontScale=2）
- `paperInkFocusTokens_andHubTouchTargetContract_remainGreen`（焦点环令牌 + Hub 48dp）

原语新增：

- `tagChip_clickable_hasMin48TouchTarget_andTagContentDescription`
- `sealStampOverlay_visible_hasContentDescriptionAndSurvivesLargeFont`（fontScale=2）

## 2. 实现摘要

| 项 | 改动 |
|----|------|
| 48dp 触控 | TagChip 可点击态补 `minimumInteractiveComponentSize` + `TouchTargetMin`；InkCard/Seal* 已具备，回归守护 |
| 纸墨焦点环 | 新增 `PaperInkFocusIndicator`，InkCard/SealButton/SealIconButton/TagChip 复用 |
| TalkBack | MemoItem 合并 `contentDescription`（时间+摘要+状态+标签）；列表 `isTraversalGroup`；FAB「新建随笔」 |
| 大字体 / 文楷印章 | `SealStampOverlay` 随 fontScale 扩张最小边 + `wrapContentSize(unbounded)`，避免 150dp 固定裁切 |

## 3. TalkBack 人工过验清单（设备就绪后勾选）

**环境**：Android 系统 TalkBack 开启；纸墨主题；字体默认 / 最大档各一轮。

| 流 | 期望 | 状态 |
|----|------|------|
| 首页列表 | 每条随笔单节点朗读「随笔，时间，摘要，同步状态…」；「更多操作」可单独聚焦 | ⬜ 待真机 |
| 浏览→编辑 | 点随笔进入编辑；返回列表焦点仍合理 | ⬜ 待真机 |
| 保存盖章 | 盖章浮层播报印章文案（如「已存」）；大字体不裁切 | ⬜ 待真机 |
| 归档 | 归档成功反馈可读；列表项状态变为已归档相关文案 | ⬜ 待真机 |
| 标签 | 可点击 TagChip ≥48dp；朗读含「标签 #xxx」 | ⬜ 待真机 |
| 设置 Hub | 行 1–6 顺序与 unit 一致（既有矩阵 PASS） | ✅ unit |
| 焦点环 | 外接键盘/D-pad 焦点时 primary 描边可见 | ⬜ 待真机 |

**无 ADB 设备时**：人工 TalkBack 与系统字体最大档截图统一标记 **SKIPPED_NO_DEVICE**，不以设备 PASS 宣称。

## 4. 系统字体最大档截图

建议目录：`docs/qa/screenshots/m3-2-a11y/`（本交付不提交二进制）。

| 表面 | 期望 | 状态 |
|------|------|------|
| 首页 Memo 卡片 | 时间/摘要/标签/状态无横向裁切 | ⬜ SKIPPED_NO_DEVICE |
| SealStampOverlay | 文楷/标题字「已存」等在印章内完整或省略号，不溢出不可读 | ✅ unit 语义 + 布局扩张；截图 ⬜ |
| 设置外观/Hub | 与既有 nav3 矩阵一致 | ✅ unit 大字体语义 |

## 5. 相关文件

- `core/designsystem/.../accessibility/PaperInkFocusIndicator.kt`
- `core/designsystem/.../component/{InkCard,SealButton,SealIconButton,TagChip,SealStampOverlay}.kt`
- `feature/home/.../{MemoItem,MemoItemTalkBack,HomeScreen}.kt`
- `SettingsAccessibilityMatrixTest` / `SettingsPrimitivesAccessibilityTest` / `MemoItemTalkBackTest`

## 6. 结论

自动化矩阵与原语测试全绿；纸墨 48dp / 焦点环令牌 / Memo 合并朗读 / 印章大字体语义已落地。TalkBack 真机与最大档截图因无设备保持待验，设备就绪后按 §3–§4 勾选并补截图。
