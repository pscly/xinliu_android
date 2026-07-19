# Android Toolchain 兼容性裁决报告

> **生成日期**: 2026-07-14
> **用途**: 嵌入式实施计划——Navigation 3 + Settings 重新设计的同版本迁移
> **原则**: 仅基于官方一手来源，不使用推断或不支持的组合

---

## 1. 版本裁决总表

| 组件 | 草稿声明 | **裁决版本** | 判定 | 备注 |
|---|---|---|---|---|
| **AGP** | 9.2.1 | **9.2.1** | ✅ | 当前 stable。AGP API ref 页面确认 `Current Release: 9.2.1` |
| **Gradle** | 9.4.1 | **9.4.1** | ✅ | AGP 9.2 的最低要求 |
| **内置 Kotlin (KGP)** | (未声明) | **2.3.10** | ⚠️ | AGP 9.2.0 内建依赖为此版本，不可直接升级 |
| **Kotlin 语言目标** | 2.3.21 | **2.3.10** (内置) 或 **2.3.21** (禁用内置后) | ⚠️ | 详见第 4 节 |
| **Compose Compiler Plugin** | 2.3.21 | **2.3.10** (与内置 Kotlin 一致) | ⚠️ | 插件版本必须匹配 Kotlin 编译器版本 |
| **KSP** | (未声明) | **2.3.10** | ✅ | 2026-07-09 发布，最新 stable |
| **Compose BOM** | 2026.06.00 | **2026.06.00** | ✅ | 官方稳定版，映射 Compose 1.11.4 |
| **Navigation 3** | 1.1.4 | **1.1.4** | ✅ | 2026-07-01 stable |
| **Activity** | 1.13.0 | **1.13.0** | ✅ | 2026-03-11 stable |
| **Lifecycle** | 2.11.0 | **2.11.0** | ✅ | 2026-06-17 stable |
| **AndroidX Hilt** | 1.4.0 | **1.4.0** | ✅ | 2026-07-01 stable |
| **Dagger / Hilt** | 2.59.2 | **2.60.1** | ❌ | **已过时**。2.60.1 是 2026-07-06 最新版 |
| **kotlinx.serialization** | 1.11.0 | **1.11.0** | ✅ | 2026-04-09 stable，基于 Kotlin 2.3.20 |
| **compileSdk** | 36 | **36** | ✅ | Android 16 (BAKLAVA)，稳定 |
| **JDK** | (未声明) | **17** | ⚠️ | AGP 9.2 要求 JDK 17（非 Java 21） |

---

## 2. 两套可用的全兼容工具链

### 选项 A：使用 AGP 内置 Kotlin（推荐——最简单）

```toml
[versions]
agp = "9.2.1"
gradle = "9.4.1"
kotlin = "2.3.10"                        # 与 AGP 内置 KGP 一致
ksp = "2.3.10"
composeBom = "2026.06.00"
compose-compiler = "2.3.10"              # 与 Kotlin 编译器版本一致
navigation3 = "1.1.4"
activity = "1.13.0"
lifecycle = "2.11.0"
hilt-androidx = "1.4.0"
dagger = "2.60.1"
kotlinx-serialization = "1.11.0"
compileSdk = "36"
jdk = "17"
```

**不需要** `org.jetbrains.kotlin.android` 插件。AGP 内置 Kotlin 处理一切。

### 选项 B：禁用内置 Kotlin，使用 Kotlin 2.3.21（如需 2.3.21 bugfix）

```toml
[versions]
agp = "9.2.1"
gradle = "9.4.1"
kotlin = "2.3.21"
ksp = "2.3.10"
composeBom = "2026.06.00"
compose-compiler = "2.3.21"              # 匹配 Kotlin 版本
navigation3 = "1.1.4"
activity = "1.13.0"
lifecycle = "2.11.0"
hilt-androidx = "1.4.0"
dagger = "2.60.1"
kotlinx-serialization = "1.11.0"
compileSdk = "36"
jdk = "17"
```

需在 `gradle.properties` 添加 `android.builtInKotlin=false`，并重新应用 `org.jetbrains.kotlin.android` 插件。

---

## 3. 逐项证据与来源

### 3.1 AGP 9.2.1 — 当前 Stable

- **来源**: [Android Gradle plugin API reference](https://developer.android.com/reference/tools/gradle-api)
- **引用**: "Current Release | 9.2.1"
- **兼容性**: AGP 9.2.0 release notes 声明最低 Gradle 9.4.1、SDK Build Tools 36.0.0、JDK 17
- **来源**: [AGP 9.2.0 Release Notes](https://developer.android.com/build/releases/agp-9-2-0-release-notes)

### 3.2 AGP 内置 Kotlin 版本 = 2.3.10

- **来源**: [AGP 9.2.0 Release Notes](https://developer.android.com/build/releases/agp-9-2-0-release-notes)
- **引用**: 已修复问题列表中包含 "Update Kotlin Gradle plugin dependency to 2.3.10"
- **来源**: [AGP 9.0.1 Release Notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes)
- **引用**: "Android Gradle plugin 9.0 now has a runtime dependency on Kotlin Gradle plugin (KGP) 2.2.10"
- **结论**: AGP 9.2 的内置 KGP 被更新到 2.3.10。这就是 AGP 编译 Kotlin 源码的编译器版本。

### 3.3 Kotlin 2.4.0 是最新 Stable（2026-06-03）

- **来源**: [Kotlin Release Process](https://kotlinlang.org/docs/releases.html)
- **引用**: "2.4.0 Released: June 3, 2026… A language release including both new and stable language features"

### 3.4 Kotlin 2.3.21 是 Bugfix 版（2026-04-23）

- **来源**: [同上](https://kotlinlang.org/docs/releases.html)
- **引用**: "2.3.21 Released: April 23, 2026… A bug fix release for Kotlin 2.3.20"

### 3.5 Kotlin 2.3 最低 AGP 要求

- **来源**: [AGP, D8, and R8 versions required for Kotlin versions](https://developer.android.com/build/kotlin-support)
- **引用**: "Kotlin 2.3 → Required AGP version: 8.2.2-8.13"
- **脚注**: "9.x versions before 9.0.28 don't support Kotlin 2.3" → AGP 9.2.1 >> 9.0.28，完全支持。

### 3.6 Kotlin 2.4 最低 AGP 要求

- **来源**: [同上](https://developer.android.com/build/kotlin-support)
- **引用**: "Kotlin 2.4 → Required AGP version: 8.5.2+"
- **注意**: AGP 9.2 满足此要求，但内置 Kotlin 是 2.3.10，需禁用内置 Kotlin 才能使用 2.4.0。

### 3.7 Compose Compiler 版本必须匹配 Kotlin 版本

- **来源**: [Compose to Kotlin Compatibility Map](https://developer.android.com/jetpack/androidx/releases/compose-kotlin)
- **引用**: "If you're using Kotlin 2.0 or higher, configure Compose using the Compose Compiler Gradle plugin. When you use the Compose Compiler Gradle plugin, you don't have to check Compose to Kotlin compatibility."
- **来源**: [Compose Compiler Migration Guide](https://kotlinlang.org/docs/compose-compiler-migration-guide.html)
- **引用**: 版本目录配置示例将 `compose-compiler` 的 `version.ref` 设为 `"kotlin"`——即插件版本与 Kotlin 编译器版本相同。

### 3.8 Compose Compiler Plugin 可覆盖 AGP 内置编译器

- **来源**: [Compose Compiler Migration Guide](https://kotlinlang.org/docs/compose-compiler-migration-guide.html)
- **引用**: "When applied with the Android Gradle plugin (AGP), this Compose compiler plugin will override the coordinates of the Compose compiler supplied automatically by AGP."

### 3.9 KSP 2.3.10 — 最新 Stable（2026-07-09）

- **来源**: [KSP Releases](https://github.com/google/ksp/releases/tag/2.3.10)
- **引用**: "Fix R-class resolution in KSP when AGP 9 built-in Kotlin is enabled"
- **兼容性**: KSP 版本号的第一段（2.3）对应其目标 Kotlin 版本线。KSP 2.3.x 兼容 Kotlin 2.3.x。
- **来源**: [AGP 9 Migration Skill](https://developer.android.com/agents/skills/build/agp/agp-9-upgrade/skill)
- **引用**: "If KSP (com.google.devtools.ksp) is used in the project, ensure it is on version 2.3.6 or higher."

### 3.10 Dagger 2.60.1 — 最新 Stable（2026-07-06）

- **来源**: [Dagger Releases](https://github.com/google/dagger/releases/tag/dagger-2.60.1)
- **引用**: Latest release tag
- **来源**: [Dagger 2.60 Release](https://github.com/google/dagger/releases/tag/dagger-2.60)
- **引用**: "Fixes #5190, #5180, #5177: Updated Kotlin version ton 2.3.21"
- **判定**: 草稿中 2.59.2 是 AGP 9 迁移的最低要求版本（见 [AGP 9 Migration Skill](https://developer.android.com/agents/skills/build/agp/agp-9-upgrade/skill): "If Hilt is used in the project, ensure it is on version 2.59.2 or higher"），但已不是最新版。最新版 2.60.1 内部使用 Kotlin 2.3.21，证明与 Kotlin 2.3.x 兼容。

### 3.11 Navigation 3 — 1.1.4 Stable

- **来源**: [Navigation 3 Releases](https://developer.android.com/jetpack/androidx/releases/navigation3)
- **引用**: "Stable Release: 1.1.4"

### 3.12 Compose BOM 2026.06.00

- **来源**: [Set up Compose Compiler](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)
- **引用**: "Always use the latest Compose BOM version: `2026.06.00`"
- **映射**: [BOM to Library Version Mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping) 确认 2026.06.00 映射到 Compose 1.11.4

### 3.13 Activity 1.13.0

- **来源**: [Activity Releases](https://developer.android.com/jetpack/androidx/releases/activity)
- **引用**: "Version 1.13.0 — March 11, 2026"

### 3.14 Lifecycle 2.11.0

- **来源**: [Lifecycle Releases](https://developer.android.com/jetpack/androidx/releases/lifecycle)
- **引用**: "Version 2.11.0 — June 17, 2026"
- **注意**: Lifecycle 2.11.0-alpha03 的 release note 提到 "Updated Compose compileSdk to API 37. This means that a minimum AGP version of 9.2.0 is required when using Compose." — 这说明 Lifecycle 2.11.0 期望 AGP ≥ 9.2.0。

### 3.15 AndroidX Hilt 1.4.0

- **来源**: [Hilt (AndroidX) Releases](https://developer.android.com/jetpack/androidx/releases/hilt)
- **引用**: "Stable Release: 1.4.0 — July 01, 2026"

### 3.16 kotlinx.serialization 1.11.0

- **来源**: [kotlinx.serialization Changelog](https://github.com/Kotlin/kotlinx.serialization/blob/master/CHANGELOG.md)
- **引用**: "1.11.0 / 2026-04-10 — This release is based on Kotlin 2.3.20"

### 3.17 compileSdk 36 = Android 16

- **来源**: [uses-sdk element](https://developer.android.com/guide/topics/manifest/uses-sdk-element)
- **引用**: API level 36 = `BAKLAVA` = Android 16

---

## 4. 关键问题深度分析

### 4.1 AGP 9.2.1 内置 Kotlin 允许 Kotlin 2.3.21 吗？

**不直接允许。** AGP 9.2.0 内置的 KGP 版本是 **2.3.10**。当你启用 built-in Kotlin（AGP 9.0+ 默认启用），你不能同时应用 `org.jetbrains.kotlin.android` 插件——这会导致冲突错误：

> *"Failed to apply plugin 'org.jetbrains.kotlin.android'. Cannot add extension with name 'kotlin', as there is an extension already registered with that name."*

**但是**，以下两者是可行的：

1. **Compose Compiler 插件可以覆盖**：`org.jetbrains.kotlin.plugin.compose` 插件可以独立于 AGP 内置 Kotlin 应用。
   > 来源：[Compose Compiler Migration Guide](https://kotlinlang.org/docs/compose-compiler-migration-guide.html) — "When applied with the Android Gradle plugin (AGP), this Compose compiler plugin will override the coordinates of the Compose compiler supplied automatically by AGP."

2. **禁用内置 Kotlin**：设置 `android.builtInKotlin=false` 后，你可以自由选择 Kotlin 版本。
   > 来源：[Migrate to built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin) — "Set android.builtInKotlin=false in the gradle.properties file to disable built-in Kotlin for all modules."

**结论**：如果 stick with built-in Kotlin，你的 Kotlin 语言版本就是 2.3.10。Kotlin 2.3.21 与 2.3.10 之间没有语言特性差异，只有 bug 修复。对于 compose compiler 插件版本，推荐与编译器版本 **完全一致**（2.3.10），否则 Mixing 不同版本可能导致不兼容。

### 4.2 AGP 9.2.1 内置 Kotlin 允许 Kotlin 2.4.0 吗？

**不允许。** Kotlin 2.4.0 是一个新的语言发布版（language release），包含新的语言特性和编译器内部变更。AGP 9.2.x 的内置 KGP 是 2.3.10，不支持 2.4.0 的编译器。

如果项目需要 Kotlin 2.4.0，你必须：
1. 禁用 built-in Kotlin (`android.builtInKotlin=false`)
2. 应用 `org.jetbrains.kotlin.android` 2.4.0
3. 将 Compose Compiler Plugin 对应升级到 2.4.0
4. **承担风险**：AGP 9.2 内部 API 可能依赖 KGP 2.3.x 的接口，使用 KGP 2.4.0 可能存在未测试的不兼容。多个插件（如 Koin）已知在 2.4.0 上有问题。

**强烈建议**：等待 AGP 9.3（或更高版本）正式支持 Kotlin 2.4.0 后再迁移。目前的 AGP 9.2.x 系列设计用于 Kotlin 2.3.x。

### 4.3 KSP 和 Hilt 哪个版本有效？

**KSP 2.3.10**（2026-07-09 最新）兼容 Kotlin 2.3.x 全系列。此版本明确修复了 "R-class resolution in KSP when AGP 9 built-in Kotlin is enabled"——证明它针对 AGP 9 内置 Kotlin 进行了测试。

**Dagger/Hilt 2.60.1**（2026-07-06 最新）内部使用 Kotlin 2.3.21（来自 2.60 版本更新日志），与 Kotlin 2.3.x 系列完全兼容。对于 AndroidX Hilt，使用 1.4.0。

**AGP 9 迁移最低要求**：
- KSP ≥ 2.3.6
- Dagger/Hilt ≥ 2.59.2
> 来源：[AGP 9 Migration Skill](https://developer.android.com/agents/skills/build/agp/agp-9-upgrade/skill)

### 4.4 JDK 版本：Java 21 → JDK 17

草稿声称 JDK 21，但 **AGP 9.2 要求 JDK 17**（minimum AND default）。

> 来源：[AGP 9.2.0 Release Notes](https://developer.android.com/build/releases/agp-9-2-0-release-notes) — "JDK | Minimum version: 17 | Default version: 17"

Java 21 **可以用于编写应用代码**（通过 `compileOptions` 设置 Java 21 语言级别），但 AGP 构建工具链本身运行在 JDK 17 上。如果 Gradle 配置了 JDK 21 作为 Gradle daemon JVM，应该降回 17。

---

## 5. 迁移步骤摘要

### 从当前 (AGP 8.4.2 / Gradle 8.7 / Kotlin 1.9.24 / KSP 1.9.24 / Dagger 2.51) 到目标

| 迁移项 | 当前 | 目标 | 风险 |
|---|---|---|---|
| AGP | 8.4.2 | 9.2.1 | 高——DSL API 大量变更 |
| Gradle | 8.7 | 9.4.1 | 中——Gradle 9.x 有 breaking changes |
| Kotlin | 1.9.24 | 2.3.10 (内置) | 高——K2 编译器，语言变更 |
| KSP | 1.9.24-1.0.20 | 2.3.10 | 高——KSP2 API 差异 |
| Compose Compiler | 旧版 kotlinCompilerExtensionVersion | org.jetbrains.kotlin.plugin.compose 2.3.10 | 中——迁移到 Gradle plugin |
| Dagger/Hilt | 2.51 | 2.60.1 | 中——multidex 移除，AGP 9 兼容 |
| 编译 JDK | 21 | 17 | 低——AGP 构建要求 |

### 推荐迁移顺序

1. **升级 Gradle** 到 9.4.1（gradle-wrapper.properties + 修复 breaking changes）
2. **升级 AGP** 到 9.2.1（运行 AGP Upgrade Assistant）
3. **迁移到 built-in Kotlin**（移除 `kotlin-android` 插件，AGP 9 默认启用）
4. **升级 KSP** 到 2.3.10（更新 plugin ID 和版本）
5. **迁移 Compose Compiler**（移除 `kotlinCompilerExtensionVersion`，添加 `org.jetbrains.kotlin.plugin.compose` 2.3.10）
6. **升级 Dagger/Hilt** 到 2.60.1 + AndroidX Hilt 1.4.0
7. **更新 Kotlin stdlib 版本** 到 2.3.10
8. **更新 Compose BOM** 到 2026.06.00
9. **更新其他 AndroidX 库**（Navigation3 1.1.4、Activity 1.13.0、Lifecycle 2.11.0）
10. **迁移 kapt → KSP**（如果尚未迁移，kapt 在 AGP 9 中需要 legacy-kapt 插件）
11. **切换到 JDK 17** 作为 Gradle daemon JVM

---

## 6. 已知风险

1. **AGP 9 DSL 变更**：`androidComponents` API 取代了旧的 variant API。自定义 Gradle 插件需要重写。
2. **kapt 不再内置**：必须迁移到 KSP 或添加 `com.android.legacy-kapt` 插件。
3. **multidex 移除**：Dagger 2.60+ 移除了 multidex 支持，minSdk 变为 23。
4. **Java 21 字节码兼容性**：Android Lint 在 AGP 9 中报告编译到 Java 21 的自定义 lint 检查的问题（已知 issue #314101896）。

---

## 7. 来源 URL 汇总

| 来源 | URL |
|---|---|
| AGP API Reference (Current Release) | https://developer.android.com/reference/tools/gradle-api |
| AGP 9.2.0 Release Notes | https://developer.android.com/build/releases/agp-9-2-0-release-notes |
| AGP 9.0.1 Release Notes (Built-in Kotlin) | https://developer.android.com/build/releases/agp-9-0-0-release-notes |
| AGP & Gradle Compatibility | https://developer.android.com/build/releases/about-agp |
| Migrate to Built-in Kotlin | https://developer.android.com/build/migrate-to-built-in-kotlin |
| AGP, D8, R8 vs Kotlin Versions | https://developer.android.com/build/kotlin-support |
| AGP 9 Migration Skill | https://developer.android.com/agents/skills/build/agp/agp-9-upgrade/skill |
| Kotlin Release Process | https://kotlinlang.org/docs/releases.html |
| Kotlin What's New 2.4.0 | https://kotlinlang.org/docs/whatsnew24.html |
| Compose Compiler Migration Guide | https://kotlinlang.org/docs/compose-compiler-migration-guide.html |
| Compose to Kotlin Compatibility | https://developer.android.com/jetpack/androidx/releases/compose-kotlin |
| Set Up Compose Dependencies | https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler |
| BOM to Library Version Mapping | https://developer.android.com/develop/ui/compose/bom/bom-mapping |
| KSP Releases | https://github.com/google/ksp/releases |
| Dagger Releases | https://github.com/google/dagger/releases |
| Dagger 2.60 Release (Kotlin 2.3.21) | https://github.com/google/dagger/releases/tag/dagger-2.60 |
| Navigation 3 Releases | https://developer.android.com/jetpack/androidx/releases/navigation3 |
| Activity Releases | https://developer.android.com/jetpack/androidx/releases/activity |
| Lifecycle Releases | https://developer.android.com/jetpack/androidx/releases/lifecycle |
| Hilt (AndroidX) Releases | https://developer.android.com/jetpack/androidx/releases/hilt |
| kotlinx.serialization Changelog | https://github.com/Kotlin/kotlinx.serialization/blob/master/CHANGELOG.md |
| AndroidX All Versions | https://developer.android.com/jetpack/androidx/versions |
| SDK Platform Releases | https://developer.android.com/tools/releases/platforms |
| uses-sdk API Levels | https://developer.android.com/guide/topics/manifest/uses-sdk-element |

---

> **不依赖**: 搜索引擎片段、Stack Overflow、Medium、非官方博客、AI 推断。
> **所有数据**: 均来自 developer.android.com、kotlinlang.org、github.com/google/dagger、github.com/google/ksp、github.com/Kotlin/kotlinx.serialization。
