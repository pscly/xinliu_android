# 导航转场策略：Navigation3 共享元素优先

首页卡片 → 编辑器采用 **Compose Shared Element（`SharedTransitionLayout` + `sharedBounds`/`sharedElement`）**，由 Navigation 3 `NavDisplay` 的 `sharedTransitionScope` 与 `transitionSpec` 族 API 承载；页面级默认转场用 `ContentTransform`（共享轴 / 淡入淡出）。仅在真机验收失败时降级为「淡入淡出 + 共享轴」且关闭共享元素。

## 背景

- 主机已是 Navigation 3：`AppNavigationHost` → `rememberDecoratedNavEntries` + `NavDisplay`（无 Navigation 2 `NavHost`）。
- 锁定版本：`navigation3 = 1.1.4`，Compose BOM `2026.06.00`（`gradle/libs.versions.toml`）。
- 计划 M2.0 要求二选一：共享元素可行，或降级淡入淡出 + 共享轴。

## 决策依据（版本钉死）

| 证据 | 内容 |
|------|------|
| 本机 `navigation3-ui` 1.1.4 字节码 | `NavDisplay(..., SharedTransitionScope?, ..., transitionSpec, popTransitionSpec, predictivePopTransitionSpec, onBack)`；`SharedEntryInSceneNavEntryDecorator(SharedTransitionScope)`；`LocalNavAnimatedContentScope` |
| AndroidX navigation3 1.1.0 发布说明 | *Shared Elements between Scenes*：向 `NavDisplay` / `rememberSceneState` 传入 `SharedTransitionScope`；1.1.4 为后续 bugfix 稳定线 |
| 官方 *Animate between destinations* | `SharedTransitionLayout { NavDisplay(sharedTransitionScope = this) }`；可覆写 `transitionSpec` / `popTransitionSpec` / `predictivePopTransitionSpec`；entry metadata 键 `TransitionKey` / `PopTransitionKey` / `PredictivePopTransitionKey` |
| Compose Shared Elements 指南 | `sharedElement` / `sharedBounds` 需要 `SharedTransitionScope` + `AnimatedVisibilityScope` |

结论：**FEASIBLE**（M2.0）。不把 Navigation 2 方案作为主路径。

## M2.9 实施边界

1. **接线面**：只改 `AppNavigationHost` 包装层 + Home/Editor 两侧 bounds 标记；不重写多栈状态机。
2. **多栈**：`NavDisplay` 只吃 active 分区栈；共享元素仅限同栈 push/pop（Home→`EditorKey`）。顶层分区切换不做共享元素。
3. **ReducedMotion / 设置开关**：`ReducedMotion.current` 与 `pageTransitionsEnabled` 为真时禁用动画（即时或纯淡入），覆盖印章与页面转场。
4. **进程死亡**：动画不持久化；恢复后直接目标 UI。
5. **降级触发**：列表滚动中 bounds 错位、严重掉帧、或 Overlay/Dialog Scene 冲突 → 关共享元素，保留 fade + shared-axis 页面转场。

## 后果

- M2.9 以共享元素为默认交付路径；QA 须覆盖开关关、系统减少动态效果、Home↔Editor 往返。
- 不引入 Navigation 2 依赖作为转场实现。
- 本 ADR 不触发 versionCode / APK 变更（纯文档）。

## 实现状态（2026-07-21 设计冻结）

### 已完成（宿主）

- `AppNavigationHost` 已用 `SharedTransitionLayout` 包裹 `NavDisplay`。
- `NavDisplay.sharedTransitionScope = if (reducedMotion) null else this`；页面级 `transitionSpec` / `popTransitionSpec` / `predictivePopTransitionSpec`（fade + 共享轴）已接线。
- Reduced Motion 与「关闭页面转场」共用门控，不另起共享元素专用开关。

### 未完成（业务 bounds，属 UI 债务）

- Home `MemoItem` / 分组内容与 Editor 内容根**尚未**挂 `sharedBounds`。
- 尚无仓库级统一 key / Modifier helper。

### 收口设计裁决（计划 `.omo/plans/2026-07-21-ui-debt-closeout.md`，双审通过，实现待办）

1. 仅在 `core:navigation` 提供 nullable `LocalMemoSharedTransitionScope` 与 `modifier.memoSharedBounds(uuid)`；key = `memo/<uuid>`（null/blank 不产生 key）。
2. 宿主注入：`CompositionLocalProvider(LocalMemoSharedTransitionScope provides (if (reducedMotion) null else this))`，与 `NavDisplay.sharedTransitionScope` 使用同一 nullable 值。
3. 配对：仅 Home **活跃**分区已有 memo → `EditorKey(uuid!=null)`；新建、归档、跨顶层分区、进程恢复不参与。
4. API 形态要求：null scope 时 **不得** 条件调用 `rememberSharedContentState`（Compose slot 稳定性）；使用 `with(scope)` receiver + `LocalNavAnimatedContentScope.current`。
5. 不引入 Navigation 2，不引入社区 `SharedEntryInSceneNavEntryDecorator` / `localNavSharedTransitionScope` 命名，不发明第二套 Reduced Motion 状态。
6. 降级条件仍适用本节「M2.9 实施边界」第 5 条。

**说明**：本段描述的是**已批准的目标契约**。在产品 Todo 1/8 落地前，不得在 QA 或 Release 说明中宣称「shared bounds 已接线」。本 ADR 更新仍为纯文档，不触发 versionCode。
