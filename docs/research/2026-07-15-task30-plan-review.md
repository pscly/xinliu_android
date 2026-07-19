# Task 30 计划验证：权威文档对照与规划影响

> **状态**: 纯研究工作，不发布、不修改任何代码
> **日期**: 2026-07-15
> **目的**: 验证 Task 30 计划的四个关键技术点是否与 AndroidX 官方文档和 Android 最佳实践一致

---

## 1. AndroidX Navigation 3 Host/Contributor 模式

### 官方文档事实

Navigation 3 官方架构（[Get started](https://developer.android.com/guide/navigation/navigation-3/get-started)，2026-07-14 更新）：

| 组件 | 官方角色 | 所在 artifact |
|------|---------|--------------|
| `NavEntry<T>` | 包装某个 key 对应的 Composable 内容 + metadata | `navigation3-runtime` |
| `NavDisplay` | 观察 back stack，渲染当前 Scene 的 NavEntry | `navigation3-ui` |
| `EntryProvider` / `entryProvider {}` DSL | `(key) -> NavEntry<T>` 的函数/DSL，将导航键映射到内容 | `navigation3-runtime` |
| `rememberDecoratedNavEntries` | 给 back stack 添加 decorator（如 ViewModelStore、SavedState）后产出 List\<NavEntry\> | `navigation3-runtime` |
| `Scene` / `SceneStrategy` | 决定多个 entry 如何在屏幕上布局（单窗格/双窗格等） | `navigation3-ui` |

官方模块化指南（[Modularize navigation code](https://developer.android.com/guide/navigation/navigation-3/modularize)，2026-07-14 更新）：

- **每个 feature 分 `api` 和 `impl` 子模块**：`api` 模块持有导航键（NavKey），`impl` 模块持有 `NavEntry` 定义和 `entryProvider` 扩展函数。
- **入口建造器（entry builder）**：在 `EntryProviderScope` 上定义扩展函数，由 `impl` 模块提供，app 模块聚合调用。
- **依赖方向**：`impl` 可以依赖其他 feature 的 `api` 模块获取导航键 → feature 间只通过键解耦。

> **证据** ([官方 modularize 文档](https://developer.android.com/guide/navigation/navigation-3/modularize?hl=th))：官方明确描述 "เครื่องมือสร้างรายการ"（entry builder）作为模块化模式，feature 的 `impl` 模块提供 `NavEntry` 定义和 `entryProvider`。

### 本仓库的实现模式（仓库假设）

本仓库的 `FeatureEntryContributor` 接口：

```kotlin
// core/navigation/.../FeatureEntryContributor.kt
interface FeatureEntryContributor {
    fun owns(key: OneMemosNavKey): Boolean
    fun entry(key: OneMemosNavKey, navigator: OneMemosNavigator, host: FeatureEntryHost): NavEntry<OneMemosNavKey>
}
```

这与官方模式在**精神上一致**但**形式上不同**：
- 官方用 `EntryProviderScope` 扩展函数，本仓库用 `FeatureEntryContributor` 接口。
- 本仓库额外引入了 `owns()` 方法用于**运行时键所有权验证**（官方无此概念，依赖编译时类型系统）。
- 本仓库的 `resolveEntryContributor()` 执行 `require(owners.size == 1)` 强制每个键恰有一个 owner。

**关键发现**：`FeatureEntryContributor` 模式 **不是 Navigation 3 官方 API**，而是本项目的架构抽象。但这种抽象**完全兼容** Navigation 3 的 `entryProvider` 模型——在 `AppNavigationHost.kt`（L148-L159）中可以看到，`contributor.entry()` 被直接用作 `rememberDecoratedNavEntries` 的 `entryProvider` 参数。

### 对 Task 30 的规划影响

- ✅ `SettingsEntryContributor` 继承 `FeatureEntryContributor` 并通过 `owns()` 声明九个 Settings 键 → 与既有模式一致。
- ✅ 从 `appEntryContributors` 列表中替换 `LegacySettingsEntryContributor` → 与官方 "app 聚合 feature entry" 模式一致。
- ⚠️ **注意**：官方模块化指南建议每个 feature 分 `api`/`impl` 子模块，当前仓库未采用此分层。这对 Task 30 **无直接影响**，但将来如果 Settings 键需要被其他 feature 引用（跨 feature 导航），建议抽取 `api` 模块。

---

## 2. Lifecycle STARTED 事件收集

### 官方文档事实

**`repeatOnLifecycle(Lifecycle.State.STARTED)`**（[官方文档](https://developer.android.com/topic/libraries/architecture/coroutines)，2026-06-16 更新）：

- 当 lifecycle 达到 `STARTED` 状态（含 `RESUMED`）时，启动一个新的协程执行 `block`。
- 当 lifecycle 降到 `STOPPED` 以下时，取消该协程。
- lifecycle 重新回到 `STARTED` 时，**重新启动一个新的协程**执行 block（即 "replay"）。
- `block` 是 `suspend` 函数，内部可安全收集多个 Flow。
- 官方明确标记 `launchWhenStarted`/`launchWhenResumed` 为 **deprecated**，推荐使用 `repeatOnLifecycle`（Lifecycle 2.4.0+）。

**`collectAsStateWithLifecycle()`**（[官方文档](https://developer.android.com/topic/libraries/architecture/coroutines)）：

- 默认 `minActiveState = Lifecycle.State.STARTED`：在 STARTED 时开始收集，STOPPED 时停止。
- 返回的是 Compose `State<T>`，State 对象本身在 lifecycle 降级时**不会重置为初始值**（不丢数据），仅暂停底层订阅。
- 内部使用 `repeatOnLifecycle` 实现。

**Compose Lifecycle Effects**（[官方文档](https://developer.android.com/topic/libraries/architecture/lifecycle)，2026-04-20 更新）：

- `LifecycleStartEffect`：在 `ON_START` 事件时执行 block，在 `ON_STOP` 或 `onDispose` 时清理。
- `LifecycleResumeEffect`：在 `ON_RESUME` 事件时执行 block，在 `ON_PAUSE` 或 `onDispose` 时清理。
- `LifecycleEventEffect`：可监听任意 lifecycle event（ON_CREATE, ON_START, ON_RESUME, ON_PAUSE, ON_STOP, ON_DESTROY, ON_ANY）。
- 所有这些 effect **每次事件触发时都会重新执行 block** → 天然具有 replay 语义。

### 对 Task 30 的规划影响

- ✅ 计划 Step 1 断言 "事件 collector 只在 STARTED 生命周期活动" → 完全符合官方 `collectAsStateWithLifecycle(STARTED)` 的默认行为。
- ✅ ViewModel 使用 `SharedFlow(replay=0)` 发射一次性事件 → `repeatOnLifecycle(STARTED)` 会在每次 lifecycle 回到 STARTED 时重新订阅 Flow，但因为 **replay=0**，之前发射的事件不会重放。这正是在 ViewModel 中处理一次性 UI 事件的标准模式。
- ⚠️ **潜在问题**：如果 lifecycle 在 STOPPED 期间发生了 Platform action 的结果回调（如权限请求结果），回调可能在 `repeatOnLifecycle` 的协程被取消期间到达。需要确保结果通过 StateFlow/SharedFlow 在 ViewModel 中缓存，或通过其他机制确保不丢失。

**最佳实践对照**：Task 30 的 "SharedFlow(replay=0) + repeatOnLifecycle(STARTED) 消费" 模式是 Android 官方推荐的标准做法（参见 [UI Events documentation](https://developer.android.com/topic/architecture/ui-layer/events)），但需注意：

1. 如果 ViewModel 在 `onResult` 回调中 `tryEmit` 事件，而此时 lifecycle 在 STOPPED，`SharedFlow(replay=0)` 会**丢弃该事件**。应改用 `Channel` 或确保 `onResult` 回调在 lifecycle STARTED 时发生。
2. 对于 platform action dispatcher 的结果（如权限授予），建议同时在 ViewModel 中更新 `StateFlow`（持久状态）+ 用 `SharedFlow`（一次性副作用），这是官方推荐的 "UI events decision tree" 结论。

---

## 3. Compose/Lifecycle 事件重放语义

### 官方文档事实

| API | 重放行为 | 生命周期绑定 | 适用场景 |
|-----|---------|-------------|---------|
| `repeatOnLifecycle(STARTED)` | ✅ 每次 re-enter STARTED 重新执行整个 block | 协程跟随 lifecycle | 在 View/Fragment/Activity 中安全收集 Flow |
| `collectAsStateWithLifecycle()` | ✅ 底层用 `repeatOnLifecycle`，但返回的 State 对象在降级/恢复期间保持值 | 订阅跟随 lifecycle，State 值跟随 composition | Compose UI 中收集 ViewModel StateFlow |
| `LaunchedEffect(key)` | ✅ key 变化时重启；dispose 时取消 | 跟随 composition | 与 composition 生命周期绑定的协程副作用 |
| `DisposableEffect(key)` | ✅ key 变化时 dispose+重新初始化；离开 composition 时 dispose | 跟随 composition | 需要清理的资源订阅 |
| `LifecycleStartEffect` | ✅ 每次 ON_START 事件触发时重新执行 | 跟随 lifecycle | 与 Android lifecycle 事件绑定的副作用 |
| `LifecycleEventEffect` | ✅ 每次指定事件触发时执行 | 跟随 lifecycle | 特定 lifecycle 事件的副作用 |

**关键区别**（[官方文档](https://developer.android.com/topic/libraries/architecture/lifecycle)，2026-04-20）：

- **Compose lifecycle**（composition → recomposition → disposal）与 **Android lifecycle**（CREATED → STARTED → RESUMED → DESTROYED）是两个正交的维度。
- `LaunchedEffect`/`DisposableEffect` 只跟随 **composition lifecycle**，不知道 Activity 是否在后台。
- `LifecycleStartEffect`/`LifecycleResumeEffect` 只跟随 **Android lifecycle**，不随 recomposition 重启。
- `collectAsStateWithLifecycle()` 桥接两者：把 Android lifecycle 的 START/STOP 与 Flow 收集绑定。

**"事件重放"语义的精确含义**：

- `repeatOnLifecycle(STARTED)` 的 "replay" 是指**重新启动协程执行 block**（如重新收集 Flow），不等于 "重放已消费的事件"。
- `SharedFlow(replay=0)` 在协程取消期间发射的事件**不会被重放**（设计如此）。
- 如果需要 "事件不丢失"，使用 `Channel`（带 buffer）或 `StateFlow`（总保留最新值）。

### 对 Task 30 的规划影响

- ✅ Task 30 使用 `SharedFlow(replay=0)` + `collectAsStateWithLifecycle` 处理 ViewModel 一次性事件 → 模式正确。
- ⚠️ **需注意**：`SettingsUiEvent.Platform(action)` 和 `SettingsUiEvent.UpdateDelivery(action)` 作为一次性事件，如果在 lifecycle STOPPED 期间从 platform dispatcher 回调中发射，会因为 `replay=0` 而丢失。建议：
  1. 确保 dispatcher 的 `onResult` 回调在 Compose context 中触发（而非后台线程），或
  2. 在 dispatcher 实现中延迟回调直到 lifecycle 回到 STARTED，或
  3. 考虑在 ViewModel 中用 `Channel` 替代 `SharedFlow`（带 `BufferOverflow.DROP_OLDEST`），配合 `receiveAsFlow()` 消费。

---

## 4. App-Owned 平台 Action Launcher 模式

### 官方文档事实

**Activity Result API**（[官方文档](https://developer.android.com/training/basics/intents/result)，2026 最新）：

- `registerForActivityResult(contract, callback)` 返回 `ActivityResultLauncher<I>`。
- callback 必须在 `Activity`/`Fragment` **创建时无条件注册**（保证进程被杀后恢复时仍可用）。
- `launch(input)` 可在任何时候调用。
- 标准 contracts：`RequestPermission`、`RequestMultiplePermissions`、`StartActivityForResult`、`CreateDocument`、`GetContent` 等。
- 可创建自定义 `ActivityResultContract<I, O>`。

**Compose 中**（[官方文档](https://developer.android.com/develop/ui/compose/libraries)）：

- `rememberLauncherForActivityResult(contract, onResult)` 必须 **在 composition 中注册**。
- 返回的 launcher 可在 `onClick` 等任何回调中调用。
- 官方建议不要在 `LaunchedEffect` 或 `DisposableEffect` 中注册 launcher。
- `ActivityResultContracts.StartActivityForResult()` 可启动任意 Intent 并获取结果。

### 对 Task 30 的规划影响

- ✅ 计划让 `AppSettingsPlatformActionDispatcher` 通过 `CompositionLocal` 提供给 Settings 页面 → 符合 Compose 依赖注入模式。
- ✅ "app 只执行平台 launcher，事件通过窄接口回传"→ 符合 Activity Result API 的 "register in UI, launch on action" 模式。
- ⚠️ **重要约束**：`rememberLauncherForActivityResult` **必须在 composition 中注册**。如果 `AppSettingsPlatformActionDispatcher` 是通过 `staticCompositionLocalOf` 在 `NavDisplay` 外层提供，则 launcher 的注册位置必须在 composition 树的 activity 层级（通常在 `OneMemosApp` 的顶层 composable 中）。这是可行的，但需确保：
  1. Launcher 在首次 composition 时注册，不是在调用 `dispatch()` 时注册。
  2. `dispatch()` 方法内部仅调用已注册好的 launcher。
- ✅ Task 30 明确说明 "平台 dispatcher 不接受未知来源设置或 APK 安装" → 职责边界清晰，避免将 `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` 混入平台 dispatcher。
- ⚠️ **需要验证**：Settings 中的 `ACTION_MANAGE_OVERLAY_PERMISSION` 需要以 `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` + `package:your.package` 的 data URI 启动。官方 `ActivityResultContracts.StartActivityForResult()` 可用于启动此 Intent，但需要在 `onResult` 中通过 `Settings.canDrawOverlays(context)` 再次检查结果（因为系统不一定返回 result）。

---

## 5. 综合规划影响总结

### ✅ 官方文档确认的合理设计

| Task 30 设计决策 | 官方依据 | 风险等级 |
|-----------------|---------|---------|
| Feature 拥有自己的 NavEntry contributor | Navigation 3 模块化官方指南 | 低 |
| 入口贡献者通过 `entryProvider` 与 `NavDisplay` 连接 | `rememberDecoratedNavEntries` API | 低 |
| ViewModel 事件通过 SharedFlow(replay=0) 发射 | 官方 UI Events 最佳实践 | 低 |
| `collectAsStateWithLifecycle(STARTED)` 消费 UI 状态 | 官方 lifecycle-aware 收集推荐 | 低 |
| app 通过 CompositionLocal 提供 platform dispatcher | Compose 依赖注入标准模式 | 低 |
| 平台 launcher 通过 Activity Result API 实现 | `rememberLauncherForActivityResult` 官方 API | 低 |

### ⚠️ 需要额外关注的仓库假设

| 仓库假设 | 官方对照 | 建议 |
|---------|---------|------|
| `FeatureEntryContributor` 接口（含 `owns()`） | 官方无此 API，是项目架构抽象 | 保持，但注意 `owns()` 不覆盖泛型键的场景（EditorsKey 等已处理） |
| `resolveEntryContributor()` 运行时键唯一性验证 | 官方依赖编译时类型 + `entryProvider` DSL 的类型安全 | 额外增加了运行时安全网，是合理增强 |
| `staticCompositionLocalOf` 提供 dispatcher | 官方推荐 `compositionLocalOf`（动态）用于可能变化的值 | `staticCompositionLocalOf` 适合这里（dispatcher 不变），正确 |
| 平台 dispatcher 不接受 "未知来源设置" | 官方 API 确实需要不同的 Intent/权限流 | 职责分离正确 |
| `LegacySettingsEntryContributor` 临时持有所有 9 个键 | 这是迁移期中转模式，非永久架构 | Task 31 删除，符合计划 |

### 🔴 需在实现中验证的关键风险点

1. **Platform action 回调丢失风险**：`SharedFlow(replay=0)` 在 lifecycle STOPPED 时如果发射 `SettingsPlatformResult`，会被丢弃。建议在 AppSettingsPlatformActionDispatcher 实现中考虑 `Channel` 或延迟发射模式。

2. **`rememberLauncherForActivityResult` 注册时机**：必须在 `OneMemosApp` 的 composition 顶层注册（通常在 `setContent {}` 内），不能在 `dispatch()` 被调用时才注册。Activity Result API 的 callback 注册和 launcher 使用是分离的——注册必须在 composition 建立时完成。

3. **Overlay permission 结果检测**：`Settings.ACTION_MANAGE_OVERLAY_PERMISSION` 不会在 Activity Result 中可靠返回结果，需在 `onResult` 回调中通过 `Settings.canDrawOverlays()` 重新检查。

4. **多个 Settings 页共享同一个 dispatcher**：六个能力页 + Hub 页（共 7 个）可能在同一个 NavDisplay/Composition 中共享同一个 `LocalSettingsPlatformActionDispatcher`。如果使用 `rememberLauncherForActivityResult`，所有页面的 platform action 会共用同一个 launcher 实例，onResult 回调需要路由到正确的发起页 ViewModel。这通过 `dispatch(action, onResult)` 的 `onResult` 回调已正确设计——每个调用方提供自己的回调闭包。

---

## 6. 数据来源

所有官方文档引用截至 2026-07-14（UTC）：

1. Navigation 3 入门：[developer.android.com/guide/navigation/navigation-3/get-started](https://developer.android.com/guide/navigation/navigation-3/get-started)
2. Navigation 3 基础：[developer.android.com/guide/navigation/navigation-3/basics](https://developer.android.com/guide/navigation/navigation-3/basics)
3. Navigation 3 迁移指南：[developer.android.com/guide/navigation/navigation-3/migration-guide](https://developer.android.com/guide/navigation/navigation-3/migration-guide)
4. Navigation 3 模块化：[developer.android.com/guide/navigation/navigation-3/modularize](https://developer.android.com/guide/navigation/navigation-3/modularize)
5. Navigation 3 API 参考：[developer.android.com/reference/kotlin/androidx/navigation3/ui/NavDisplay](https://developer.android.com/reference/kotlin/androidx/navigation3/ui/NavDisplay.composable)
6. Lifecycle 2.11.0 发布说明：[developer.android.com/jetpack/androidx/releases/lifecycle](https://developer.android.com/jetpack/androidx/releases/lifecycle)
7. Compose 中的 Lifecycle：[developer.android.com/topic/libraries/architecture/lifecycle](https://developer.android.com/topic/libraries/architecture/lifecycle)
8. 使用协程与 lifecycle-aware 组件：[developer.android.com/topic/libraries/architecture/coroutines](https://developer.android.com/topic/libraries/architecture/coroutines)
9. Compose 与其他库：[developer.android.com/develop/ui/compose/libraries](https://developer.android.com/develop/ui/compose/libraries)
10. Activity Result API：[developer.android.com/training/basics/intents/result](https://developer.android.com/training/basics/intents/result)
11. Activity 1.13.0 发布说明：[developer.android.com/jetpack/androidx/releases/activity](https://developer.android.com/jetpack/androidx/releases/activity)
12. 运行时权限请求：[developer.android.com/training/permissions/requesting](https://developer.android.com/training/permissions/requesting)
