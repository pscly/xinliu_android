# Navigation 3 与 Setting 重构稳定依赖矩阵（研究锁定日 2026-07-14）

## 1. 研究说明

- **研究日期**：2026-07-14
- **设计输入**：`docs/superpowers/specs/2026-07-14-settings-navigation3-redesign-design.md`
- **当前基线**：AGP 8.4.2、Kotlin 1.9.24、Compose BOM 2024.06.00、Navigation Compose 2.7.7、compileSdk 34、targetSdk 34、minSdk 33
- **研究方法**：仅使用官方第一方来源（Android Developers 官方文档、AndroidX 发布页、Kotlin 官方发布页、Dagger 官方站、GitHub kotlinx.serialization 仓库），逐项验证每个版本在 2026-07-14 之前已是最终稳定版
- **条款定义**：
  - **硬要求**：不满足则无法编译/运行时必然崩溃/API 不可用
  - **推荐最新稳定版**：在满足硬要求的前提下可选的最高稳定版本

---

## 2. 总览矩阵

| 组件 | 硬要求（最小） | 推荐最新稳定版 | 发布/确认日期 | 版本来源 URL |
|------|--------------|-------------|------------|------------|
| Navigation 3 | ≥1.0.0 | **1.1.4** | 2026-07-01 | <https://developer.android.com/jetpack/androidx/releases/navigation3> |
| compileSdk | ≥36 (Nav3 要求) | **36**（Nav3 1.1.x 官方要求） | — | <https://developer.android.com/guide/navigation/navigation-3/migration-guide> |
| targetSdk | —（规格固定） | **34**（规格固定） | 规格 §3.3 | specs/2026-07-14-settings-navigation3-redesign-design.md#3.3 |
| minSdk | ≥23 (Nav3 要求) | **33**（规格固定） | 规格 §3.3 | specs/2026-07-14-settings-navigation3-redesign-design.md#3.3 |
| SDK Build Tools | 36.0.0 | **36.0.0** | — | <https://developer.android.com/build/releases/agp-9-2-0-release-notes#compatibility> |
| Android Gradle Plugin | ≥9.0.1 (API 36) | **9.2.1** | Current Release | <https://developer.android.com/reference/tools/gradle-api> |
| Gradle | ≥9.4.1 (AGP 9.2.1 要求) | **9.4.1** | — | <https://developer.android.com/build/releases/agp-9-2-0-release-notes#compatibility> |
| JDK | 17 | **17** | — | AGP 9.2.x compatibility table |
| Kotlin | ≥2.3.21 (Compose 编译器兼容) | **2.3.21** | 2026-04-23 | <https://kotlinlang.org/docs/releases.html> |
| Compose Compiler Gradle Plugin | 与 Kotlin 同版本 | **2.3.21** | 2026-04-23 | <https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler> |
| Compose BOM | — | **2026.06.00** | 2026-06 | <https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler> |
| Compose Libraries (UI, Foundation, Material 等) | — | **1.11.4** | 2026-07-01 | <https://developer.android.com/jetpack/androidx/releases/compose> |
| Material3 | — | **1.4.0** | 2026-07-01 | <https://developer.android.com/jetpack/androidx/releases/compose-material3> |
| Activity | — | **1.13.0** | 2026-03-11 | <https://developer.android.com/jetpack/androidx/releases/activity> |
| Lifecycle | — | **2.11.0** | — | <https://developer.android.com/jetpack/androidx/releases/lifecycle> |
| lifecycle-viewmodel-navigation3 | ≥2.11.0 (Nav3 集成) | **2.11.0** | — | <https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0> |
| Hilt (AndroidX) | — | **1.4.0** | 2026-07-01 | <https://developer.android.com/jetpack/androidx/releases/hilt> |
| Dagger (底层) | — | **2.59.2** | — | <https://dagger.dev/> |
| kotlinx-serialization | ≥1.7.0（从 NavKey 序列化推断） | **1.11.0** | 2026-04-10 | <https://github.com/Kotlin/kotlinx.serialization/blob/master/CHANGELOG.md> |
| kotlinx-serialization-json | — | **1.11.0** | 2026-04-10 | <https://github.com/Kotlin/kotlinx.serialization> |

---

## 3. 逐项证据与推理

### 3.1 Navigation 3 — 版本选择

**硬要求**：Navigation 3 的 `NavKey`、`NavBackStack`、`NavDisplay`、`rememberNavBackStack` 和 `EntryProvider` 仅在 `androidx.navigation3` 包中可用，Navigation 2.x（`androidx.navigation`）不具备这些 API。

**稳定版发布链**：
| 版本 | 发布日期 | 状态 |
|------|---------|------|
| 1.0.0 | 2025-11-19 | 首个稳定版 |
| 1.0.1 | 2026-02-11 | 依赖更新 |
| 1.1.0 | 2026-04-08 | 功能稳定版（SceneDecoratorStrategy、NavMetadata DSL、SharedTransitionScope、OverlayScene 动画） |
| 1.1.1 | 2026-04-22 | Bug fix |
| 1.1.2 | 2026-05-19 | Bug fix (NavigationEvent) |
| 1.1.3 | 2026-06-17 | Bug fix (OverlayScene re-animation) |
| **1.1.4** | **2026-07-01** | **Bug fix（当前最新稳定版）** |
| 1.2.0-alpha05 | 2026-07-01 | Alpha（排除！） |

**证据**（[AndroidX navigation3 发布页](https://developer.android.com/jetpack/androidx/releases/navigation3)）：
- 页头表格：`Latest Update: July 01, 2026 | Stable Release: 1.1.4`
- 版本 1.1.0 正式标记为 "1.1.0 is now stable!"，引入 `SceneDecoratorStrategy`, `NavMetadata DSL`, `SharedTransitionScope`, `OverlayScene.onRemoved`
- 版本 1.1.4 是最新的 bug fix，发布日期 2026-07-01

**关键 API 清单**（此设计所需）：
- `NavKey`（可序列化路由键接口，自 1.0.0-alpha01）
- `NavBackStack`（可变导航栈，自 1.0.0-alpha01）
- `rememberNavBackStack()`（可恢复导航栈，自 1.0.0-alpha01）
- `NavDisplay`（UI 层入口，自 1.0.0-alpha01）
- `EntryProvider`（键到可组合项的映射 DSL，自 1.0.0-alpha01）
- `SceneStrategy`（场景策略，自 1.0.0-alpha09，在 1.1.0 中稳定）
- `NavMetadataKey` / `NavMetadataKeys`（类型安全元数据 DSL，自 1.1.0-alpha04，在 1.1.0 中稳定）
- `rememberSceneState()`（场景状态保持，自 1.0.0-alpha10，在 1.1.0 中稳定）
- `SavedStateNavEntryDecorator`（进程死亡恢复，自 1.0.0-alpha07）

**compileSdk 硬要求**：Navigation 3 迁移指南（[Migration Guide](https://developer.android.com/guide/navigation/navigation-3/migration-guide)）明确要求 `compileSdk` 更新至 **36**：
> "Also update the project's `minSdk` to 23 and the `compileSdk` to 36."

因此 compileSdk 必须 ≥ 36。本设计规格说明 compileSdk 不能低于 36，与此一致。

---

### 3.2 Android Gradle Plugin (AGP) — 版本选择

**推理链**：根据设计规格 §9.1 的锁定顺序：
1. Navigation 3 要求 compileSdk 36
2. 选择支持 compileSdk 36 的最高 AGP 稳定版

**AGP 稳定版链**（从 AGP API 参考页确认）：
| 版本 | 状态 | 支持 API | Gradle 要求 |
|------|------|---------|------------|
| 9.0.1 (Jan 2026) | 稳定 | 36 | 9.1.0 |
| 9.1.1 (Apr 2026) | 稳定 | 37 | 9.3.1 |
| **9.2.1** | **Current Release** | **37** | **9.4.1** |
| 9.3.0-rc01 | RC（排除！） | — | — |

**证据**（[AGP API 参考页](https://developer.android.com/reference/tools/gradle-api)）：
```
| Current Release | 9.2.1 |
| Preview Releases | 9.3.0-rc01 |
| Past Releases | 9.1.1 |
```

AGP 9.2.1 是截至 2026-06-26（页面最后更新日期）的 Current Release。由于该页面明确区分了 Current/Past/Preview，9.2.1 是稳定版。

**兼容性**（[AGP 9.2.0 发布页](https://developer.android.com/build/releases/agp-9-2-0-release-notes#compatibility)）：
| 项 | 最低版本 | 默认版本 |
|----|---------|---------|
| Gradle | 9.4.1 | 9.4.1 |
| SDK Build Tools | 36.0.0 | 36.0.0 |
| JDK | 17 | 17 |

AGP 9.2.x 支持 "Android API level 37.0 and below"，包括 API 36。

**注意**：AGP 9.x 引入了内置 Kotlin 支持（built-in Kotlin）。AGP 9.0.0 内置 Kotlin 2.2.10。但可通过显式应用 Kotlin Gradle Plugin 来使用更新的 Kotlin 版本（如下文）。

---

### 3.3 Kotlin — 版本选择

**硬要求**：必须与 Compose Compiler Gradle Plugin 兼容。Compose 编译器插件的版本必须与 Kotlin 版本精确匹配。

**关键约束**：Compose 官方设置文档（[Set up the Compose Compiler Gradle plugin](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)）明确建议使用 **2.3.21**：

```kotlin
classpath("org.jetbrains.kotlin.plugin.compose:org.jetbrains.kotlin.plugin.compose.gradle.plugin:2.3.21")
```

该页面也使用 `compileSdk 37` 和 `compose-bom:2026.06.00` 作为示例。

**Kotlin 发布链**（[Kotlin 发布页](https://kotlinlang.org/docs/releases.html)）：
| 版本 | 发布日期 | 类型 |
|------|---------|------|
| 2.3.20 | 2026-03-16 | 工具链发布 |
| 2.3.21 | 2026-04-23 | Bug fix |
| 2.4.0 | 2026-06-03 | 语言发布（Language Release） |
| 2.4.20-Beta1 | 2026-06-24 | Beta（排除！） |

**为什么不用 Kotlin 2.4.0？**

虽然 2.4.0 是更高的稳定版，但 Compose 官方文档截至 2026-06-19 仍推荐 2.3.21。2.4.0 是语言发布版，可能在 Compose 编译器兼容性方面引入未经验证的变化。按照设计规格 §9.1 的规则 6（"如果后选项与已锁定项不兼容，只下调当前正在选择的项到最近兼容稳定版"），应选择 Compose 文档明确支持的 Kotlin 版本。

---

### 3.4 Compose BOM 与编译器插件

**Compose BOM**（[Compose setup 页面](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)）：
> "Always use the latest Compose BOM version: `2026.06.00`."

BOM `2026.06.00` 映射到的 Compose 库版本（[AndroidX Compose 发布页](https://developer.android.com/jetpack/androidx/releases/compose)）：
| Maven Group ID | 稳定版 |
|----------------|--------|
| compose.animation | 1.11.4 |
| compose.foundation | 1.11.4 |
| compose.material | 1.11.4 |
| compose.material3 | 1.4.0 |
| compose.runtime | 1.11.4 |
| compose.ui | 1.11.4 |

**Compose Compiler Gradle Plugin**：从 Kotlin 2.0 开始，Compose 编译器与 Kotlin 编译器统一管理，使用 `org.jetbrains.kotlin.plugin.compose` 插件，版本号与 Kotlin 相同（2.3.21）。BOM 不再包含 Compose Compiler。

---

### 3.5 Activity 与 Lifecycle

**Activity**（[Activity 发布页](https://developer.android.com/jetpack/androidx/releases/activity)）：
- 最新稳定版：**1.13.0**（2026-03-11）
- 页头表格：`Stable Release: 1.13.0`

**Lifecycle**（[Lifecycle 发布页](https://developer.android.com/jetpack/androidx/releases/lifecycle)）：
- 最新稳定版：**2.11.0**
- `lifecycle-viewmodel-navigation3`：**2.11.0**（Navigation 3 集成专用 artifact）
  - 提供 `rememberViewModelStoreNavEntryDecorator()` API，用于将 ViewModel 作用域限制到单个导航 entry

**注意**：Navigation 3 迁移指南（[Wear OS migration page](https://developer.android.com/training/wearables/compose/migrate-to-navigation3)）显示依赖声明：
```kotlin
implementation("androidx.lifecycle:lifecycle-viewmodel-navigation3:...")
```

这是 ViewModel 与 Nav3 backstack entry 的生命周期绑定所必需的。

---

### 3.6 Hilt / Dagger

**AndroidX Hilt**（[Hilt 发布页](https://developer.android.com/jetpack/androidx/releases/hilt)）：
- 最新稳定版：**1.4.0**（2026-07-01）
- 页头表格：`Stable Release: 1.4.0`
- 1.4.0-beta01 中引入了 `rememberHiltViewModelFactory` 和 Compose ViewModelStoreOwner 集成

**Dagger**（[Dagger 官网](https://dagger.dev/)）：
> "The latest Dagger release is: Dagger 2.59.2"

- Hilt Gradle Plugin：`com.google.dagger.hilt.android` version **2.59.2**
- Hilt 编译器（KSP）：`com.google.dagger:hilt-compiler:2.59.2`
- Hilt Android 库：`com.google.dagger:hilt-android:2.59.2`

---

### 3.7 kotlinx-serialization

**要求**：Navigation 3 的 `NavKey` 必须使用 `@Serializable` 标注，因为 `rememberNavBackStack` 依赖 `kotlinx-serialization` 进行状态序列化和进程死亡恢复。

**kotlinx-serialization 发布链**（[CHANGELOG](https://github.com/Kotlin/kotlinx.serialization/blob/master/CHANGELOG.md)）：
| 版本 | 发布日期 | 基于 Kotlin 版本 |
|------|---------|----------------|
| 1.9.0 | — | 2.2.0 |
| 1.10.0 | 2026-01-21 | 2.3.0 |
| **1.11.0** | **2026-04-10** | **2.3.20** |

**推荐**：1.11.0，基于 Kotlin 2.3.20，与 Kotlin 2.3.21（bug fix）兼容。

**依赖**：
```kotlin
// 编译器插件（与 Kotlin 版本相同）
id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21"
// 运行时库
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
```

---

## 4. 编译时 SDK 区分

| SDK 类型 | 取值 | 原因 |
|----------|------|------|
| **compileSdk** | 36 | Navigation 3 迁移指南硬要求。AGP 9.2.1 支持 API 36。规格说"不得低于 36" |
| **targetSdk** | 34 | 规格 §3.3 明确固定："本轮 targetSdk 固定为 34" |
| **minSdk** | 33 | 规格 §3.3 明确固定："minSdk 固定为 33"。Navigation 3 要求 ≥23，33 满足 |

**compileSdk vs targetSdk 的含义**：
- `compileSdk` = 编译器可以引用的 Android API 版本（编译时可见性）
- `targetSdk` = 应用声明针对的 API 级别（运行时兼容性行为开关）

两者在本项目中不同（36 vs 34），这是 Android 开发的标准做法。

---

## 5. 完整 `libs.versions.toml` 版本参考资料

```
[versions]
agp = "9.2.1"
kotlin = "2.3.21"
compose-bom = "2026.06.00"
compose-compiler = "2.3.21"              # 与 kotlin 同版本
navigation3 = "1.1.4"
lifecycle = "2.11.0"
activity = "1.13.0"
hilt-androidx = "1.4.0"
dagger = "2.59.2"
kotlinx-serialization = "1.11.0"
compileSdk = "36"
targetSdk = "34"
minSdk = "33"
gradle = "9.4.1"
jdk = "17"
sdkBuildTools = "36.0.0"
```

---

## 6. 版本锁定决策记录

| 决策序号 | 决策内容 | 依据 |
|----------|---------|------|
| D1 | Navigation 3 选 1.1.4 而非 1.2.0-alpha05 | 1.2.0-alpha05 是 Alpha 版，规格禁止预发布版本 |
| D2 | compileSdk 选 36 而非 37 | Navigation 3 迁移指南硬要求 compileSdk 36。规格要求"使用该版本官方要求的 compileSdk" |
| D3 | Kotlin 选 2.3.21 而非 2.4.0 | Compose 官方设置页明确推荐 2.3.21。2.4.0 未被 Compose 文档引用，存在未验证的兼容性风险 |
| D4 | AGP 选 9.2.1 而非 9.3.0-rc01 | 9.3.0-rc01 是 RC 版（预发布），规格禁止 |
| D5 | Dagger 版本独立于 AndroidX Hilt | Dagger 和 AndroidX Hilt 是独立的发布线。AndroidX Hilt 1.4.0 来自 androidx 发布；Dagger 2.59.2 来自 dagger.dev |
| D6 | targetSdk 34 和 minSdk 33 保持不变 | 规格 §3.3 明确固定此两值，Navigation 3 迁移不要求改变它们 |

---

## 7. 研究范围与置信度声明

- **高置信度**（官方发布页直接可查的版本号）：Navigation 3 1.1.4、AGP 系列、Kotlin 系列、Compose BOM、Compose 库版本、Activity、Lifecycle、Hilt、Dagger、kotlinx-serialization
- **中置信度**（推理链路有单一假设）：Navigation 3 与 compileSdk 36 的耦合（迁移指南明确写出）、Kotlin 2.3.21 与 Compose 编译器插件的兼容性（Compose 官方页面明确引用）
- **低置信度**（多个推理链路或未经验证的假设）：无。所有推荐版本都有官方第一方 URL 作为直接证据

---

## 8. 排除的预发布版本（完整列表）

以下版本存在但在设计规格 §9.1 规则 7 下被排除：
- Navigation 3: 1.2.0-alpha01 至 1.2.0-alpha05
- AGP: 9.3.0-rc01
- Kotlin: 2.4.20-Beta1
- Compose Libraries: 1.12.0-beta02
- Material3: 1.5.0-alpha23
- Compose Material3 Adaptive: 1.3.0-alpha09

## 9. 关键参考链接汇总

| 来源 | URL |
|------|-----|
| Navigation 3 发布页 | <https://developer.android.com/jetpack/androidx/releases/navigation3> |
| Navigation 3 迁移指南（compileSdk 36 要求） | <https://developer.android.com/guide/navigation/navigation-3/migration-guide> |
| AGP 9.2.0 发布页（兼容性表格） | <https://developer.android.com/build/releases/agp-9-2-0-release-notes> |
| AGP API 参考页（Current Release 确认） | <https://developer.android.com/reference/tools/gradle-api> |
| Kotlin 发布页 | <https://kotlinlang.org/docs/releases.html> |
| Compose 设置页（BOM + 编译器插件） | <https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler> |
| Compose 发布页（版本表） | <https://developer.android.com/jetpack/androidx/releases/compose> |
| Activity 发布页 | <https://developer.android.com/jetpack/androidx/releases/activity> |
| Lifecycle 发布页 | <https://developer.android.com/jetpack/androidx/releases/lifecycle> |
| Hilt 发布页 | <https://developer.android.com/jetpack/androidx/releases/hilt> |
| Dagger 官网 | <https://dagger.dev/> |
| kotlinx.serialization CHANGELOG | <https://github.com/Kotlin/kotlinx.serialization/blob/master/CHANGELOG.md> |
| Compose BOM 说明 | <https://developer.android.com/develop/ui/compose/bom> |
| Kotlin 兼容性指南（AGP 版本表） | <https://kotlinlang.org/docs/gradle-configure-project.html> |
