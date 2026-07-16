# Settings Navigation 3 视觉与可访问性走查

**计划**：`docs/superpowers/plans/2026-07-14-settings-navigation3-redesign.md` Task 34
**自动化**：

| 套件 | 命令 | 环境 | 结果 |
|------|------|------|------|
| 可访问性矩阵 | `./gradlew :feature:settings:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.settings.SettingsAccessibilityMatrixTest` | Robolectric unit | **PASS**（9/9） |
| 设备测试源码编译 | `./gradlew :app:compileDebugAndroidTestKotlin` | JVM / Android 编译 | **PASS** |
| 视觉矩阵 | `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cc.pscly.onememos.settings.SettingsVisualMatrixTest` | 真机/模拟器 instrumented | **SKIPPED_NO_DEVICE**（`adb devices` 空，未执行） |
| 模块单元门禁 | `./gradlew :core:designsystem:testDebugUnitTest :feature:settings:testDebugUnitTest :app:testDebugUnitTest` | JVM / Robolectric unit | **PASS** |
| Benchmark 构建 | `./gradlew :app:assembleBenchmark` | Android 构建 | **PASS** |

**Task 34 提交主题**：`test(settings-ui): 完成视觉与无障碍矩阵验收`
**环境记录**：

| 项 | 值 |
|----|-----|
| 设备型号 | _无连接设备_ |
| Android 版本 | n/a |
| density | n/a |
| font scale | unit 已执行 1.0 / 2.0；设备视觉骨架包含 2.0 配置但未执行 |
| 减少动态效果 | unit 覆盖 `ReducedMotion.Local = true` |
| 测试提交 | Task 34 中文提交 `test(settings-ui): 完成视觉与无障碍矩阵验收` |

---

## 1. 可访问性矩阵（自动化已覆盖）

| 检查项 | Hub | 账号 | 记录 | 提醒 | 存储 | 外观 | 关于 |
|--------|-----|------|------|------|------|------|------|
| 可点击节点 ≥48×48dp | ✅ unit | ✅ unit | ✅ unit | ✅ unit | ✅ unit（含 prefetch Switch） | ✅ unit | ✅ unit |
| 纯图标 contentDescription | — | ✅ 返回 | ✅ 返回 | — | ✅ 刷新 | — | — |
| stateDescription / 状态文案 | ✅ 六行 | ✅ 健康卡 | ✅ 错误 | ✅ 权限 | ✅ root | ✅ overlay/root | ✅ announcer |
| Hub 顺序 1–6 | ✅ | — | — | — | — | — | — |
| 账号固定顺序 | — | ✅ 标题→健康→主动作→摘要→管理→高级 | — | — | — | — | — |
| live region（错误/同步/权限/结果） | — | ✅ health | ✅ error | ✅ error+permission | — | — | ✅ result announcer |
| 大字体 200% 关键语义 | ✅ unit | ✅ unit | — | — | — | — | — |
| Reduced motion 保留文字 | — | — | — | — | — | ✅ unit | — |

### 本任务生产侧修复（由 RED 驱动）

1. **存储预取 Switch**：`settings_storage_prefetch_switch` 补 `heightIn(min = 48.dp)`。
2. **提醒权限状态**：`settings_reminder_permission_status` 补 `stateDescription`（保留 live region）。

---

## 2. 视觉矩阵清单（设备）

### 2.1 窗口 × 主题

| 表面 | 360×800 | 600×960 | 840×900 | 纸墨浅 | 纸墨深 | 黛蓝浅/深（抽查） | 赛博浅/深（抽查） | 大字体 200% |
|------|---------|---------|---------|--------|--------|-------------------|-------------------|-------------|
| Hub | ⏳ 设备 | ⏳ 设备 | ⏳ 设备 | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ 设备 / ✅ unit 语义 |
| 账号与同步 | ⏳ 设备 | ⏳ 设备 | ⏳ 设备 | ⏳ | ⏳ | — | — | ⏳ 设备 / ✅ unit 语义 |

断言（`SettingsVisualMatrixTest` 骨架）：

- 内容最大宽度 ≤720dp
- 三尺寸仍单列（Hub 序号 1–6 各存在）
- 无矩阵/双栏
- 标题/摘要/异常/主按钮在语义树可达；360dp 下可 `assertIsDisplayed`

**状态**：测试骨架已完成且 `:app:compileDebugAndroidTestKotlin` 通过；真机/模拟器未连接，instrumented 执行为 **SKIPPED_NO_DEVICE**。以上 `⏳` 单元格均保持待验，不代表 PASS。

### 2.2 人工走查（设备就绪后勾选）

| 检查 | Hub | 账号 | 记录 | 提醒 | 存储 | 外观 | 关于 | 结果 |
|------|-----|------|------|------|------|------|------|------|
| 无横向滚动 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | SKIPPED_NO_DEVICE |
| 标题/摘要/异常/按钮不裁切 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | SKIPPED_NO_DEVICE |
| 焦点环可见（键盘/D-pad） | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | SKIPPED_NO_DEVICE |
| 状态不只靠颜色 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | SKIPPED_NO_DEVICE |
| TalkBack 顺序合理 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | SKIPPED_NO_DEVICE |
| Dialog 关闭后焦点回触发节点 | — | ⬜ 登出等 | — | — | ⬜ 清理确认 | — | ⬜ 重建确认 | SKIPPED_NO_DEVICE |
| Switch Access 可达 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | SKIPPED_NO_DEVICE |
| 减少动态效果 UI 文字仍在 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ✅ unit | ⬜ | SKIPPED_NO_DEVICE（外观 unit PASS） |
| 普通文字对比度 ≥4.5:1 | 参考 Appearance unit 六主题 | | | | | ✅ unit 对比度 | | SKIPPED_NO_DEVICE（unit PASS） |
| 视觉：仅 surface/1dp 描边/纸面线/印记；无阴影渐变玻璃 | 设计系统原语已约束；设备截图待补 | | | | | | | SKIPPED_NO_DEVICE |

### 2.3 TalkBack 顺序（设备）

| 页 | 期望顺序摘要 | 状态 |
|----|--------------|------|
| Hub | 标题 → 行1…行6（序号、标题、异常、摘要、进入） | SKIPPED_NO_DEVICE |
| 账号 | 返回/标题 → 健康 → 主动作 → 上次成功 → 摘要 → 账号管理 → 高级同步 | SKIPPED_NO_DEVICE（unit 语义顺序 PASS） |

---

## 3. 截图路径（设备就绪后）

建议目录：`docs/qa/screenshots/settings-nav3/`（本交付不提交截图二进制）。

命名：`{surface}-{width}x{height}-{palette}-{mode}-font{scale}.png`
例：`hub-360x800-paper_ink-light-font1.png`

**采集状态**：**SKIPPED_NO_DEVICE**。截图仍为待补证据，不代表设备渲染 PASS。

---

## 4. 证据

| 项 | 值 |
|----|-----|
| 可访问性矩阵 | 2026-07-16 unit 9/9 PASS |
| AndroidTest 源码编译 | `:app:compileDebugAndroidTestKotlin` PASS |
| 视觉矩阵 instrumented | **SKIPPED_NO_DEVICE**：`adb devices` 无设备，未运行 `connectedDebugAndroidTest` |
| 人工设备走查 | **SKIPPED_NO_DEVICE**：设备渲染、截图、TalkBack、键盘/D-pad 与 Switch Access 均未执行 |
| 相关代码 | `SettingsAccessibilityMatrixTest.kt`、`SettingsVisualMatrixTest.kt`、`StorageOfflineScreen.kt`、`ReminderCalendarScreen.kt` |
| 模块单元门禁（Task 34 Step 5） | `:core:designsystem:testDebugUnitTest`、`:feature:settings:testDebugUnitTest`、`:app:testDebugUnitTest` PASS |
| Benchmark 门禁（Task 34 Step 5） | `:app:assembleBenchmark` PASS |

**结论**：自动化可访问性矩阵 9/9 PASS，AndroidTest 源码编译、模块单元门禁与 Benchmark 构建 PASS；设备视觉、截图、TalkBack、键盘/D-pad、Switch Access 和人工走查因无 ADB 设备统一标记 **SKIPPED_NO_DEVICE**，不作任何设备成功声明。设备就绪后执行 `SettingsVisualMatrixTest` 与上表人工清单，全部 PASS 后再将本文件「⏳/⬜」改为 ✅。
