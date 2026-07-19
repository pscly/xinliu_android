# Navigation 3 1.1.4 API 与最佳实践验证（研究锁定日 2026-07-15）

## 1. 研究说明

- **研究日期**：2026-07-15
- **下游目标**：约束 Task 30 实现（feature-owned entries + lifecycle-aware one-shot event collection + app-owned Activity Result dispatchers）
- **研究方法**：仅使用第一方来源（Android Developers 官方文档、AndroidX 发布页、AOSP 源码、Kotlin Multiplatform 官方文档、Context7 索引的 androidx 仓库 API），逐项验证每个 API 在 Navigation 3 1.1.4 及配套库中的真实签名、约束和使用模式
- **版本锁定**：Navigation 3 1.1.4（stable, 2026-07-01）、Hilt 2.60.1、Compose 1.11.4、lifecycle-runtime-compose 2.11.0

---

## 2. EntryProvider / Contributor 模式

### 2.1 entryProvider DSL 签名

`entryProvider` 是一个内联函数，返回 `(T) -> NavEntry<T>` 类型的 lambda（即 entryProvider 函数）。

**Defined in**: `androidx.navigation3.runtime.entryProvider`
**Source**: [EntryProvider.kt, androidx-main](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:navigation3/navigation3-runtime/src/commonMain/kotlin/androidx/navigation3/runtime/EntryProvider.kt)

```kotlin
// 签名（简化）
public inline fun <T : Any> entryProvider(
    noinline fallback: (unknownScreen: T) -> NavEntry<T> = {
        throw IllegalStateException("Unknown screen $it")
    },
    builder: EntryProviderScope<T>.() -> Unit,
): (T) -> NavEntry<T>
```

### 2.2 两种注册模式

`EntryProviderScope<T>` 提供两种注册模式：

**A. 按 reified 类型注册（推荐用于 Feature 模块）**：
```kotlin
entryProvider<AppDestination> {
    entry<Home> {
        HomeScreen(viewModel = viewModel<HomeViewModel>())
    }
    entry<Detail> { key ->
        DetailScreen(key.id, viewModel = viewModel<DetailViewModel>())
    }
}
```

**B. 按 key 实例注册**：
```kotlin
addEntryProvider(key = Home, content = { HomeScreen(...) })
```

### 2.3 contentKey 机制

每个 entry 有 `contentKey`（默认 = `key.toString()`），用于：
- `SavedStateNavEntryDecorator` 区分同一类型不同参数的 NavEntry
- `rememberViewModelStoreOwner` 的 scoping key

### 2.4 metadata 动态注入（1.1.0 新增）

```kotlin
entry<Dashboard>(
    metadata = NavDisplay.predictivePopTransitionSpec { swipeEdge -> ... }
) { dashboardArgs ->
    Dashboard(dashboardArgs.userId, ...)
}
```

**约束**：metadata 在 entry DSL 中声明时即固化，不支持运行期动态修改。这对 Task 30 的影响是：**每个 entry 的转场动画、Scene 归属等元数据必须在声明时确定**。

---

## 3. Serializable NavKey 处理

### 3.1 硬要求

所有 NavKey 子类必须同时满足三个条件：
1. 实现 `NavKey` 接口
2. 标注 `@Serializable`
3. 在 `SavedStateConfiguration` 的 `SerializersModule` 中注册（如果使用 `rememberNavBackStack`）

**Source**: [Navigation 3 release notes 1.0.0-alpha09](https://developer.android.com/jetpack/androidx/releases/navigation3#1.0.0-alpha09)："`NavKey` 现在要求 `@kotlinx.serialization.Serializable`"

### 3.2 两种 backStack 创建方式

**A. 简单模式（仅 Android，反射序列化）**：
```kotlin
val backStack = rememberNavBackStack(Home)
```

**B. 显式 SerializersModule（多平台兼容，推荐）**：
```kotlin
private val config = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Home::class, Home.serializer())
            subclass(Detail::class, Detail.serializer())
        }
    }
}
val backStack = rememberNavBackStack(config, Home)
```

### 3.3 多模块 NavKey 注册策略（来自 Kotlin Multiplatform 官方指南）

**推荐模式（与 ADR-0006 兼容）：sealed interface 聚合**

```kotlin
// :core:navigation
@Serializable
sealed interface AppDestination : NavKey

// :feature:home
@Serializable data object Home : AppDestination

// :feature:detail
@Serializable data class Detail(val id: String) : AppDestination

// :app (聚合)
val config = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclassesOfSealed<AppDestination>()
        }
    }
}
```

**Source**: [Kotlin Multiplatform Navigation 3 guide](https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html#polymorphic-serialization-for-destination-keys)

### 3.4 NavKey 作为 type-safe 参数传递

区别于 Navigation 2 的 `SavedStateHandle` + String route 参数，Navigation 3 中参数直接是 NavKey data class 的构造器属性：

```kotlin
@Serializable data class Detail(val id: String) : NavKey

// 消费方直接解构
entry<Detail> { key ->
    DetailScreen(id = key.id)
}
```

**这替代了 SafeArgs**，不再需要 `NavBackStackEntry.arguments?.getString("id")`。

---

## 4. 生命周期感知的 Flow 收集

### 4.1 官方 API

`collectAsStateWithLifecycle()` 来自 `lifecycle-runtime-compose`（≥2.9.0），是 Navigation 3 Compose entry 中收集 ViewModel StateFlow 的**唯一推荐方式**。

```kotlin
// Flow<T> 版本
@Composable
fun <T> Flow<T>.collectAsStateWithLifecycle(
    initialValue: T,
    lifecycle: Lifecycle,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    context: CoroutineContext = EmptyCoroutineContext
): State<T>

// StateFlow<T> 版本（无 initialValue）
@Composable
fun <T> StateFlow<T>.collectAsStateWithLifecycle(
    lifecycle: Lifecycle,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    context: CoroutineContext = EmptyCoroutineContext
): State<T>
```

**Source**: [lifecycle-runtime-compose API](https://developer.android.com/reference/kotlin/androidx/lifecycle/compose/collectAsStateWithLifecycle.composable)

### 4.2 NavDisplay 自动提供 LifecycleOwner

Navigation 3 的 `NavDisplay` 自动为每个 `NavEntry` 提供 scoped `LocalLifecycleOwner`，生命周期状态在 entry 处于前台时到达 `RESUMED`，被覆盖/弹出时降至 `STARTED` 或更低。

**关键确认（1.0.0-rc01 release notes）**：
> "Bug Fixes: NavDisplay LocalLifecycleOwner Scene Lifecycle.State RESUMED"

这确保 `collectAsStateWithLifecycle()` 在 entry 不可见时自动暂停收集，无需手动 `LaunchedEffect` + `repeatOnLifecycle`。

**Source**: [Navigation 3 release notes](https://developer.android.com/jetpack/androidx/releases/navigation3#1.0.0-rc01)

### 4.3 在 entry 中的典型模式

```kotlin
entry<Home> {
    val viewModel = viewModel<HomeViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(uiState, onEvent = viewModel::onEvent)
}
```

---

## 5. hiltViewModel 在 Navigation 3 Entry 中的集成

### 5.1 依赖链

| 层 | 组件 | 作用 |
|----|------|------|
| Entry 装饰器 | `rememberViewModelStoreNavEntryDecorator()` | 为每个 NavEntry 提供独立的 ViewModelStoreOwner |
| ViewModel 获取 | `viewModel()` 或 `hiltViewModel()` | 从 LocalViewModelStoreOwner 获取/创建 ViewModel |
| DI | `@HiltViewModel` + `@Inject constructor` | Hilt 构造器注入 |

**maven coordinate**: `androidx.lifecycle:lifecycle-viewmodel-navigation3`

**Source**: [官方 save-state 指南](https://developer.android.com/guide/navigation/navigation-3/save-state)："The add-on library `androidx.lifecycle:lifecycle-viewmodel-navigation3` provides a `NavEntryDecorator` that provides a `ViewModelStoreOwner` for each `NavEntry`."

### 5.2 entryDecorators 配置（官方 samples 模式）

```kotlin
NavDisplay(
    backStack = backStack,
    entryDecorators = listOf(
        rememberSceneSetupNavEntryDecorator(),
        rememberSavedStateNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(), // <-- 提供 ViewModelStore
    ),
    entryProvider = entryProvider<AppDestination> {
        entry<Home> {
            val viewModel = hiltViewModel<HomeViewModel>()
            HomeScreen(viewModel = viewModel)
        }
    },
)
```

**Source**: [NavDisplaySamples.kt](https://cs.android.com/androidx/platform/frameworks/support/+/83c74c9f940e49cc86c03311af66d029d36bdad1/navigation3/navigation3-ui/samples/src/main/kotlin/androidx/navigation3/ui/samples/NavDisplaySamples.kt)

### 5.3 hiltViewModel() 签名

```kotlin
@Composable
inline fun <reified VM : ViewModel> hiltViewModel(
    viewModelStoreOwner: ViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current),
    key: String? = null,
): VM
```

**Source**: [hilt-lifecycle-viewmodel-compose API](https://github.com/androidx/androidx/blob/androidx-main/hilt/hilt-lifecycle-viewmodel-compose/api/1.3.0-rc01.txt)

### 5.4 已知问题与版本约束

- **已知 issue**: Google Issue Tracker [b/419597003](https://issuetracker.google.com/issues/419597003) — "Unable to inject dependencies in HiltViewModel from Nav3 entry"
- **影响范围**：在 Navigation 3 的 NavEntry 中使用 `@HiltViewModel` 时，可能无法注入依赖
- **1.1.4 状态**：1.1.4 发布说明仅列出 "Bug Fixes"，未明确提及此问题已修复
- **建议**：Task 30 实现时如果 `hiltViewModel()` 失败，fallback 为在 App 级别创建 ViewModel 并通过 CompositionLocal 传递，或使用 `viewModel(factory = ...)` + 手动 DI

---

## 6. CompositionLocal 在 NavDisplay 周围的提供

### 6.1 NavDisplay 内部自动提供的 CompositionLocal

`NavDisplay` 及其 `entryDecorators` 会自动提供以下 CompositionLocal：

| CompositionLocal | 来源 |
|-----------------|------|
| `LocalLifecycleOwner` | NavDisplay 内部 (per-entry scoped) |
| `LocalSavedStateRegistryOwner` | `rememberSavedStateNavEntryDecorator()` |
| `LocalViewModelStoreOwner` | `rememberViewModelStoreNavEntryDecorator()` |
| `LocalOnBackPressedDispatcherOwner` | Activity → NavDisplay 继承 |
| `LocalNavigationEventDispatcherOwner` | navigationevent-compose |

### 6.2 自定义 CompositionLocal 包裹 NavDisplay 的模式

官方 samples 中的模式（用于 SharedTransition）：

```kotlin
// 1. 定义自定义 CompositionLocal
val localNavSharedTransitionScope = compositionLocalOf<SharedTransitionScope> {
    error("Not provided")
}

// 2. 在 NavDisplay 外层用 CompositionLocalProvider 提供
SharedTransitionLayout {
    CompositionLocalProvider(
        localNavSharedTransitionScope provides this
    ) {
        NavDisplay(
            backStack = backStack,
            entryDecorators = listOf(
                sharedEntryInSceneNavEntryDecorator, // 内部消费该 CompositionLocal
                rememberSceneSetupNavEntryDecorator(),
                rememberSavedStateNavEntryDecorator(),
            ),
            entryProvider = entryProvider<AppDestination> { ... },
        )
    }
}
```

**Source**: [NavDisplaySamples.kt](https://cs.android.com/googlesource.com/platform/frameworks/support/+/83c74c9f940e49cc86c03311af66d029d36bdad1/navigation3/navigation3-ui/samples/src/main/kotlin/androidx/navigation3/ui/samples/NavDisplaySamples.kt)

**对 Task 30 的影响**：任何需要在 Feature entry 内部消费的 app-level 对象（如 one-shot event bus、navigator 接口），应通过 `CompositionLocalProvider` 在 `NavDisplay` 外层提供，Feature entry 通过 `CompositionLocal.current` 消费。

### 6.3 ActivityResultRegistry 的 CompositionLocal 是 Activity 层提供的

`LocalActivityResultRegistryOwner` 由 `ComponentActivity.setContent` 自动提供，**不在 NavDisplay 内部重新提供**。这意味着它属于 app-owned，可以被 NavDisplay 外层的 Composable 消费。

---

## 7. Activity Result Launcher 的所有权与注册约束

### 7.1 官方 API 约束

```kotlin
@Composable
fun <I : Any?, O : Any?> rememberLauncherForActivityResult(
    contract: ActivityResultContract<I, O>,
    onResult: (O) -> Unit
): ManagedActivityResultLauncher<I, O>
```

**硬约束**（来自官方 API reference）：

> "This **\*must\*** be called unconditionally, as part of initialization path."
> "You should **\*not\*** call `ActivityResultLauncher.unregister` on the returned `ActivityResultLauncher`."

**Source**: [rememberLauncherForActivityResult API reference](https://developer.android.com/reference/kotlin/androidx/activity/compose/rememberLauncherForActivityResult.composable)

### 7.2 必须 app-owned 的原因

1. **注册时机**：`rememberLauncherForActivityResult` 在组合初始化阶段向 `ActivityResultRegistry` 注册。注册必须在 `setContent` 的 composition tree 中发生。
2. **生命周期绑定**：注册与 Activity 的 `ActivityResultRegistry` 绑定，如果 launcher 在某个 NavEntry 被弹出后从 composition 中移除，后续 result 将无法投递。
3. **注册必须无条件**：不能在条件分支（如 `if`/`when`）中调用，也不能在 NavEntry 的 conditional content 内部调用。

### 7.3 正确模式（app-owned）

```kotlin
// 在 Activity.setContent 中，NavDisplay 之外
setContent {
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success -> /* ... */ }
    )
    // 通过 CompositionLocal 或 callback 传递给 entry
    CompositionLocalProvider(
        LocalCameraLauncher provides cameraLauncher
    ) {
        NavDisplay(
            backStack = backStack,
            /* ... */
        )
    }
}
```

### 7.4 替代方案（非 Compose 注册）

如果需要在 ViewModel 中触发 Activity Result（如权限请求），可在 Activity 的 `onCreate` 中使用 `registerForActivityResult`（非 Compose API），然后将结果通过 Channel/SharedFlow 传递给 ViewModel。

---

## 8. 版本概览矩阵

| 组件 | 锁定版本 | 发布日期 | 来源 |
|------|---------|---------|------|
| Navigation 3 stable | **1.1.4** | 2026-07-01 | [Releases](https://developer.android.com/jetpack/androidx/releases/navigation3) |
| Navigation 3 alpha | 1.2.0-alpha05 | 2026-07-01 | [Releases](https://developer.android.com/jetpack/androidx/releases/navigation3) |
| lifecycle-runtime-compose | 2.11.0 | Current stable | [API](https://developer.android.com/reference/kotlin/androidx/lifecycle/compose/collectAsStateWithLifecycle.composable) |
| lifecycle-viewmodel-navigation3 | 2.11.0 | Current stable | [save-state guide](https://developer.android.com/guide/navigation/navigation-3/save-state) |
| hilt-lifecycle-viewmodel-compose | 1.3.0-rc01 | — | [API](https://github.com/androidx/androidx/blob/androidx-main/hilt/hilt-lifecycle-viewmodel-compose/api/1.3.0-rc01.txt) |
| Hilt | 2.60.1 | As specified | — |

---

## 9. 对 Task 30 的关键约束总结

1. **Feature-owned entries**：每个 `:feature:*` 模块提供本模块 NavKey 的 `entry` lambda，通过 `entryProvider` DSL 注册。使用 reified 类型的 `entry<K>` 方法，K 为 NavKey 子类型。

2. **NavKey 集中定义**：所有跨模块 NavKey 定义在 `:core:navigation`（sealed interface），Feature 模块实现具体 data object / data class。

3. **ViewModel 获取**：使用 `viewModel()` 或 `hiltViewModel()`，前提是 `rememberViewModelStoreNavEntryDecorator()` 在 entryDecorators 列表中。`hiltViewModel()` 在 Nav3 中存在已知问题（b/419597003），建议做好 fallback 方案。

4. **生命周期感知收集**：在 entry content 中使用 `collectAsStateWithLifecycle()`（默认 `minActiveState = STARTED`），NavDisplay 自动提供正确 scoped 的 `LocalLifecycleOwner`。

5. **CompositionLocal 提供**：app-level 导航器、event bus 等通过 `CompositionLocalProvider` 在 `NavDisplay` 外层提供，Feature entry 通过 `CompositionLocal.current` 消费。

6. **ActivityResult 必须是 app-owned**：`rememberLauncherForActivityResult` 必须在 `setContent` 中无条件调用，不能放在 NavEntry 的 conditional content 内。通过 CompositionLocal 或 callback 传递给 Feature entry。

---

## 工具调用简报

| 工具 | 用途 | 关键参数 | 时间 |
|------|------|---------|------|
| websearch_tavily_search ×4 | 搜索官方文档、API reference、issue tracker | Navigation 3/1.1.4/Compose/NavKey/Hilt/ActivityResult | 3轮 |
| context7_resolve-library-id → context7_query-docs ×3 | 获取 AndroidX 官方 API 签名 | Navigation 3 entryProvider、collectAsStateWithLifecycle、hiltViewModel、NavEntryDecorator | 2轮 |
| webfetch ×3 | 获取源码和文档原文 | EntryProvider.kt、NavDisplaySamples.kt、save-state guide | 2轮 |
| tavily_tavily_extract | 提取官方 release notes | developer.android.com 发布页 | 1轮 |
