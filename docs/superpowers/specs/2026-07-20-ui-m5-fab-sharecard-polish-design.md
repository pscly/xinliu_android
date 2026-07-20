# M5 首页 FAB 净空与分享卡片标签换行打磨设计

## 1. 文档状态

| 项目 | 结论 |
| --- | --- |
| 文档日期 | 2026-07-20 |
| 当前状态 | **实现已落地**；本文记录问题、方案、范围与既有验证，供后续 1.14.0 周期引用 |
| 适用范围 | Home 列表/网格底部 FAB 避让；ShareCardCanvas 标签行布局 |
| 版本策略 | **本轮不 bump versionCode / versionName**；`v1.13.0` 不可变；下一稳定周期目标 `1.14.0` |
| 文档任务约束 | 仅文档与会话记录；不改 Kotlin 产品代码；不 git commit |

本文对应 M4 视觉 QA 中已标为**非阻塞观察**的两项 UI 瑕疵。目标是最小打磨（polish），不是重做设计系统或发布闭环。

## 2. 问题

### 2.1 Home：末条 memo 与 FAB「记」重叠

**现象**：首页列表滚到底时，最后一条 memo 的标签行被右下角悬浮 FAB 组遮挡，尤其是「记」创建按钮区域。

**根因**：`Scaffold.floatingActionButton` **不会**自动给内容区加底部 inset。`LazyColumn` / `LazyVerticalStaggeredGrid` 的 `contentPadding` 原先只按密度轴设置 `verticalPad`，未预留 FAB 组实际占用高度。

**证据**（M4 视觉 QA 设备 dump，密度约 3.5）：

| 对象 | 坐标（约） | 含义 |
| --- | --- | --- |
| 末条 memo 标签区 | y ≈ 2943–3111 | 内容延伸到 FAB 垂直带 |
| FAB「记」 | [1188, 2948]–[1384, 3144] | 高度约 196px ≈ 56dp（`SealButtonSize`） |

结论：列表底部内容与 FAB 在同一垂直区间重叠，属于 layout inset 缺失，不是 Seal 尺寸本身错误。

### 2.2 ShareCard：第四枚标签被画布右缘裁切

**现象**：分享卡片预览/导出图上，第四枚标签（例：`#同事`）在卡片右缘被裁成约 33px 宽的残片。

**根因**：`ShareCardCanvas` 内标签行使用 `LazyRow`。水平滚动容器在固定画布宽度下会裁切超出右缘的子项。预览与导出共用同一 `ShareCardCanvas`，故两端同时出现半截胶囊。

**排除项**：`ThemesPanel` 四主题胶囊均完整（约 205px），**不是**主题条问题，不必为本问题改 ThemesPanel。

## 3. 已实现方案

### 3.1 Home：`HomeFabClearance` + 列表/网格 bottom inset

抽出纯函数，全部使用 `InkSpacing` 令牌：

```text
HomeFabClearance.fabBottomClearance(showScrollToTop):
  true  → TouchTargetMin + X10 + SealButtonSize + X16
  false → SealButtonSize + X16
```

令牌语义：

| 令牌 | 典型值 | 用途 |
| --- | --- | --- |
| `TouchTargetMin` | 48dp | 回到顶部 `SealIconButton` 外层最小触控高度（视觉 `SealIconSize`=44，布局须用 48） |
| `X10` | 间距 | 图标按钮与「记」之间 |
| `SealButtonSize` | 56dp | 「记」按钮高度 |
| `X16` | 缓冲 | 列表底缘与 FAB 组之间额外呼吸空间 |

**接入点**（`HomeScreen`）：

1. `val fabBottomClearance = HomeFabClearance.fabBottomClearance(showScrollToTop)`
2. `LazyColumn` 与 `LazyVerticalStaggeredGrid` 均使用显式 `PaddingValues`：
   - `start/end = horizontalPad`
   - `top = verticalPad`
   - `bottom = verticalPad + fabBottomClearance`

`showScrollToTop` 与现有 FAB 组 `AnimatedVisibility` 同源（列表/网格首项偏移阈值），净空随「单 FAB / 双 FAB」动态变化。

**未改动**：`home_fab_group` / `home_fab_create` 等 testTag；全局 Seal 尺寸；密度轴 `horizontalPad` / `verticalPad` / `itemGap` 规则。

### 3.2 ShareCardCanvas：标签 `LazyRow` → `FlowRow`

画布标签区改为：

- `FlowRow`
- 水平间距 `InkSpacing.X10`
- 垂直间距 `InkSpacing.X8`
- 仍 `state.tags.take(6)`
- 文件级 `@OptIn(ExperimentalLayoutApi::class)`（与既有 Foundation opt-in 并列）

超宽时标签自动换行，每枚胶囊完整呈现。预览与导出共用画布，一次修改两端生效。

**未改动**：照片区仍为 `LazyRow`（横向相册语义合理）；ThemesPanel 布局（本问题无关）。

## 4. 范围与否决项

### 4.1 范围内（已做）

1. Home 列表 + 网格 contentPadding 底部 FAB 净空。
2. ShareCardCanvas 标签 FlowRow 换行。
3. `HomeFabClearance` 纯函数 + 单元测试。
4. 编译与单元测试门禁（见第 5 节）。

### 4.2 范围外

1. 设计系统重做、Seal 全局尺寸变更。
2. ThemesPanel 防御性 FlowRow（可选、非本轮必做）。
3. versionCode / versionName bump、Tag、Actions、正式 Release。
4. 完整设备视觉 QA 重跑（本轮文档任务不声称已重跑）。
5. 提交 `.omo/run-continuation`、`.tmp_vqa` 等脏项。

### 4.3 明确否决

| 候选 | 否决原因 |
| --- | --- |
| 仅给 `LazyRow` 加 `contentPadding` | 导出位图上仍可能出现半截芯片，不能保证「完整可见」 |
| 全局缩小 Seal / 改 FAB 尺寸 | 影响触控与品牌一致性；根因是 inset，不是按钮过大 |
| 把 ThemesPanel 一并改 FlowRow | 证据显示主题条未裁切；扩大范围无收益 |

## 5. 验证（实现阶段已完成）

| 检查 | 结果 |
| --- | --- |
| `HomeFabClearanceTest` | PASS（显示/不显示回到顶部两用例） |
| `:feature:home` `compileDebugKotlin` | SUCCESS |
| `:feature:sharecard` `compileDebugKotlin` | SUCCESS |

说明：`:feature:sharecard` 无 `compileBenchmarkKotlin` 任务；库模块可用 `compileReleaseKotlin` / `compileDebugKotlin` 验证。Benchmark APK 变体在 `app` 模块。

**未在本文档任务中重新执行**：真机/模拟器像素级视觉 QA。M4 双 Oracle 曾将两项标为非阻塞观察；本修复针对其根因。若进入 `1.14.0` 发布，应在门禁中补一轮 home 滚底 + sharecard 多标签导出目检。

## 6. 产品文件清单（磁盘已存在，文档任务不编辑）

| 路径 | 角色 |
| --- | --- |
| `feature/home/.../HomeFabClearance.kt` | FAB 底部净空纯函数 |
| `feature/home/.../HomeFabClearanceTest.kt` | 净空公式单元测试 |
| `feature/home/.../HomeScreen.kt` | 列表/网格应用 `verticalPad + fabBottomClearance` |
| `feature/sharecard/.../ShareCardCanvas.kt` | 标签 FlowRow + take(6) |

## 7. 与版本/发布的关系

- **当前稳定线**：`1.13.0 (163)` 已冻结；本 polish **不**进入 `v1.13.0` 回写。
- **下一周期**：`1.14.0` 可携带本 M5 改动 + 其它积压项，再走完整发布闭环（递增 version、门禁、签名 Benchmark、push、Tag、Actions、正式 Release）。
- **本任务**：只写规格与会话历史，**不** commit、不 bump 版本。

## 8. 成功定义（实现视角）

1. 列表/网格滚到底时，最后一条 memo（含标签行）不被 FAB 组遮挡。
2. ShareCard 预览与导出图中，最多 6 枚标签均完整可见（必要时换行），无右缘 33px 残片。
3. 净空公式可单测锁定，且随 `showScrollToTop` 切换。
4. 改动面最小化：无设计令牌全局回写、无 testTag 破坏、无版本漂移。

## 9. 相关上下文

- M4 视觉 QA（2026-07-20）非阻塞观察：home FAB 轻微重叠；sharecard 第四枚标签轻微裁切。
- 记事本：`.omo/notepads/2026-07-20-ui-m5-polish/`（decisions / learnings）。
- 会话：`.ai_session.md` 中 `[2026-07-20] M5` 节。
