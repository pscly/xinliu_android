# 1memos：多模块化（Hybrid）+ 设计系统收敛 + 性能可量化与防回归（本地流程）

## TL;DR

> **目标**：把当前工程从“当前可迭代”升级到“结构边界写死 + 风格一致 + 性能可测且不回退”。
>
> **核心手段**：Gradle 多模块（Hybrid：`:core:*` + `:feature:*` + `:app` 仅组装）→ 设计系统收敛（tokens + 组件规范）→ 建立本地性能门禁脚本（PS1）→ 冷启动与主页列表性能治理（Top2）。
>
> **重要约束**：本轮**不新增功能**；默认追求“行为不变”，任何行为变化都必须显式列出并单独验收。

**交付物（Deliverables）**
- 新的模块结构（新增多个 `:core:*` / `:feature:*` 模块），`.\gradlew.bat` 常用任务仍可用。
- `:core:designsystem`（或等价模块）：统一主题 tokens/排版/间距/组件库的入口与用法。
- `scripts/` 下新增本地门禁脚本（build/test/lint + 可选 benchmark/baseline）。
- 关键性能场景的宏基准/基线流程可跑通（设备可用时），并能记录结果。
- 冷启动、主页列表至少完成一轮“测量→定位→治理→复测”。

**预计工作量**：Large
**并行**：少量（以“每一步可编译/可回滚”为主）
**关键路径**：模块骨架 → core 下沉 → designsystem 收敛 → feature 迁移 → 门禁脚本 → 冷启动 → 主页列表

---

## 背景与已知现状（证据）

### 工程结构
- 工程根：`settings.gradle.kts`
- 现有模块：`:app`、`:macrobenchmark`、`:baselineprofile`（见 `settings.gradle.kts`）
- 依赖管理：Version Catalog（`gradle/libs.versions.toml`）

### 关键入口（保持语义不变）
- `app/src/main/java/cc/pscly/onememos/MainActivity.kt`
  - Compose root
  - 显式注入 `LocalLifecycleOwner`（曾用于规避启动崩溃）
- `app/src/main/java/cc/pscly/onememos/OneMemosApplication.kt`
  - `Configuration.Provider`：WorkManager `HiltWorkerFactory`
  - `ImageLoaderFactory`：Coil 缓存配置
  - 前台 token refresh 与派生字段回填调度

### 已有能力（不重造轮子）
- UI 组件库雏形：`app/src/main/java/cc/pscly/onememos/ui/component/*`
- 同步/派生字段：`app/src/main/java/cc/pscly/onememos/worker/*`
- 单测/回归基础：`app/src/test/java/**`（含 Robolectric、纯逻辑单测）

---

## Metis 审查结论（已吸收为 guardrails）

需要显式写死的风险点：
- Hilt 跨模块：重复 binding/可见性/InstallIn 范围
- WorkManager：`Configuration.Provider`、`HiltWorkerFactory`、UniqueWorkName/Tags 语义
- Compose Navigation：route contract 分散导致循环依赖
- BaselineProfile/Macrobenchmark：变体匹配与可跑通性
- 性能测量噪声：设备温度/后台状态/动画/充电状态导致误判

本计划的硬 guardrails：
- 先重构后优化：禁止“多模块拆分 + 性能优化 + 视觉改版”三件事同时做
- 不引入新的架构大件（例如全新 MVI 框架、全量重写导航）
- 默认行为不变；行为变化必须列清单并验收
- 不重命名 `:app` 模块（否则 `:baselineprofile`/`:macrobenchmark` 的 targetProjectPath 与变体匹配需同步调整）

---

## 目标与完成标准（Definition of Done）

### 结构目标
- 完成 Hybrid 多模块拆分：`:core:*` 与 `:feature:*` 形成清晰依赖方向
- 写出“模块所有权表 + 依赖方向表”（谁提供、谁消费）并落到文档

### 设计系统目标
- 设计 token 与组件使用方式统一：不再出现各页面各写一套 spacing/shape/字体/按钮风格
- 现有视觉整体不大改，但“风格漂移”显著减少

### 性能目标（可量化）
- 提供本地门禁脚本：至少能一键跑 `assembleDebug + testDebugUnitTest + lintDebug`，并可选跑宏基准/基线
- 冷启动与主页列表完成一轮治理：宏基准/基线流程能复测并对比

### 最低验收命令（不依赖真机）
> Windows/pwsh7：先执行 `[Console]::OutputEncoding = [System.Text.Encoding]::UTF8`

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :baselineprofile:assemble
.\gradlew.bat :macrobenchmark:assemble
```

（有设备/模拟器时）增强验收：
```powershell
.\gradlew.bat :macrobenchmark:connectedCheck
# 以及 baseline profile 的生成任务（按当前工程已存在的任务名执行）
```

---

## 建议模块划分（Hybrid）

> 目标：最少的“循环依赖风险”，最大化复用与可测试性。

### :core（稳定底座）
- `:core:model`：`domain/model/*`（纯模型/枚举）
- `:core:domain`：Repository 接口、tag/derived 等纯逻辑（优先保持纯 Kotlin）
- `:core:database`：Room DB/DAO/Entity/Converters
- `:core:network`：Retrofit API、interceptor、DTO、URL 解析
- `:core:data`：Repository 实现、Mapper、settings/token storage、cache
- `:core:sync`：WorkManager Scheduler/Workers（与 data/network/db 协作）
- `:core:designsystem`：主题、tokens、基础组件（InkCard/SealButton/ScrollPaper 等）、haptics、formatters
- `:core:navigation`：Routes/argument contract（避免 feature-to-feature 依赖）
- `:core:performance`：高刷请求、trace/性能辅助（最小化）

### :feature（按页面/功能）
- `:feature:home`
- `:feature:editor`
- `:feature:settings`
- `:feature:sharecard`
- `:feature:quickcapture`
- `:feature:profile`
- `:feature:auth`
- `:feature:welcome`
- `:feature:start`（如需要，把启动路由/分流集中）

### :app（仅组装）
- 仅保留：`MainActivity`、`OneMemosApplication`、`OneMemosApp`（导航组装）、`di` 聚合（必要时）

依赖方向（强约束）：
- `:feature:*` 只依赖 `:core:*`，**禁止 feature 互相依赖**
- `:core:domain` 不依赖任何 Android/Compose（如当前有 Android 依赖则先抽离）
- `:core:designsystem` 不依赖具体 feature

---

## 执行策略（分波次，保持可编译）

Wave 1（地基）：文档与门禁骨架
- 写清楚模块所有权/依赖方向/不可触碰项
- 搭建空模块（只包含 build.gradle.kts + 最小代码）确保工程能解析

Wave 2（core 下沉）：model/domain/db/network/data/sync
- 按依赖方向从“最纯”到“最脏”迁移，避免循环

Wave 3（designsystem 收敛）
- 把现有 `ui/theme`、`ui/component`、`ui/util` 迁移并统一入口

Wave 4（feature 迁移）
- home/editor/settings/sharecard… 逐个迁移，确保每个 feature 迁移后可编译

Wave 5（性能流程 + Top2 治理）
- 脚本化本地门禁
- 冷启动与主页列表：测量→定位→治理→复测

---

## TODOs（每个任务都要求“可验证 + 可回滚”）

> 注意：每个任务都默认“行为不变”。如必须行为变化，需单独列出并加验收。

- [x] 1. 编写模块边界与不可变约束文档（Architecture Guardrails）

  **要做什么**：
  - 在 `ARCHITECTURE.md` 增加一份架构文档（模块所有权表、依赖方向、禁止事项、常见坑）
  - 明确：WorkManager uniqueName/tags、DB schema version、route contract、存储路径等“不可触碰项”

  **参考（为什么重要）**：
  - `项目总结.md`：已有整体架构说明，可抽取为可执行规范
  - `app/src/main/java/cc/pscly/onememos/OneMemosApplication.kt`：启动与 WorkManager/Coil 关键语义
  - `app/src/main/java/cc/pscly/onememos/MainActivity.kt`：Compose root 与 lifecycle 修复语义

  **验收**：
  - 文档存在且包含：模块列表、依赖方向、DI ownership map、验证命令清单

- [x] 2. 建立 Hybrid 多模块骨架（只建空模块，先跑通构建）

  **要做什么**：
  - 在 `settings.gradle.kts` 新增 include：`:core:*`、`:feature:*`
  - 为每个模块创建 `build.gradle.kts`（尽量复用 `gradle/libs.versions.toml`）
  - `:app` 先只依赖空模块（必要时先不接入任何代码）

  **参考**：
  - `settings.gradle.kts`：当前模块声明
  - `build.gradle.kts`：plugins apply false 的基线
  - `gradle/libs.versions.toml`：依赖与插件版本
  - `app/build.gradle.kts`：现有 app 配置（benchmark buildType 等）

  **验收（必须可编译）**：
  - `.\gradlew.bat :app:assembleDebug` 成功

- [x] 3. 迁移 `:core:model`（domain/model）

  **要做什么**：
  - 把 `app/src/main/java/cc/pscly/onememos/domain/model/*` 迁移到 `:core:model`
  - 处理依赖：确保尽量纯 Kotlin（若有 Android 依赖，先保守用 android-library）

  **参考**：
  - `app/src/main/java/cc/pscly/onememos/domain/model/*`：现有模型

  **验收**：
  - `:app:testDebugUnitTest` 仍通过（或临时调整测试归属后通过）

- [x] 4. 迁移 `:core:domain`（repository 接口 + tag/derived 纯逻辑）

  **要做什么**：
  - 迁移：
    - `app/src/main/java/cc/pscly/onememos/domain/repository/*`
    - `app/src/main/java/cc/pscly/onememos/domain/tag/*`
    - `app/src/main/java/cc/pscly/onememos/domain/derived/*`
  - 建立 domain 的依赖最小化（不依赖 data/ui）

  **参考**：
  - `app/src/main/java/cc/pscly/onememos/domain/tag/TagExtractor.kt`
  - `app/src/main/java/cc/pscly/onememos/domain/derived/MemoDerivedFields.kt`
  - 单测：`app/src/test/java/cc/pscly/onememos/domain/**`

  **验收**：
  - domain 相关单测迁移/调整后仍通过

- [x] 5. 迁移 `:core:database`（Room）

  **要做什么**：
  - 迁移 `app/src/main/java/cc/pscly/onememos/core/database/**`
  - 保持 DB 名称、版本号、migration 行为不变

  **参考**：
  - `app/src/main/java/cc/pscly/onememos/core/database/OneMemosDatabase.kt`
  - `app/src/main/java/cc/pscly/onememos/di/AppModule.kt`（提供 DB/DAO）

  **验收**：
  - `.\gradlew.bat :app:assembleDebug` 通过

- [x] 6. 迁移 `:core:network`（Retrofit/OkHttp/DTO/URL 工具）

  **要做什么**：
  - 迁移 `app/src/main/java/cc/pscly/onememos/core/network/**`
  - 保持 `AuthorizationInterceptor` 语义不变
  - Flow backend 网络保持日志策略不变（debug basic / release none）

  **参考**：
  - `app/src/main/java/cc/pscly/onememos/di/NetworkModule.kt`
  - `app/src/main/java/cc/pscly/onememos/di/FlowBackendModule.kt`
  - 测试：`app/src/test/java/cc/pscly/onememos/core/network/FlowDeviceHeadersInterceptorTest.kt`

  **验收**：
  - unit tests 仍通过

- [x] 7. 迁移 `:core:data`（Repository 实现/mapper/settings/token/cache）

  **要做什么**：
  - 迁移：
    - `app/src/main/java/cc/pscly/onememos/data/**`
  - 梳理 data → domain/model/database/network 的依赖方向

  **参考**：
  - `app/src/main/java/cc/pscly/onememos/data/repository/MemoRepositoryImpl.kt`
  - `app/src/main/java/cc/pscly/onememos/data/settings/SettingsRepositoryImpl.kt`
  - `app/src/main/java/cc/pscly/onememos/di/AppModule.kt`
  - 测试：`app/src/test/java/cc/pscly/onememos/data/settings/*`

  **验收**：
  - `.\gradlew.bat :app:testDebugUnitTest` 通过

- [x] 8. 迁移 `:core:sync`（WorkManager Scheduler/Workers）

  **要做什么**：
  - 迁移 `app/src/main/java/cc/pscly/onememos/worker/*`
  - 保持 UniqueWorkName、InputData keys、调度语义不变
  - 让 `OneMemosApplication` 的 WorkManager 配置仍能找到正确的 `HiltWorkerFactory`

  **参考**：
  - `app/src/main/java/cc/pscly/onememos/worker/WorkManagerSyncScheduler.kt`
  - `app/src/main/java/cc/pscly/onememos/di/WorkerModule.kt`
  - `app/src/test/java/cc/pscly/onememos/worker/FullSyncHelpersTest.kt`
  - `app/src/main/java/cc/pscly/onememos/OneMemosApplication.kt`

  **验收**：
  - `.\gradlew.bat :app:assembleDebug` 通过

- [x] 9. 建立 `:core:navigation`（route/argument contract 收口）

  **要做什么**：
  - 把路由常量与参数编码/解码规则集中到 core
  - feature 只能依赖 contract，不直接依赖别的 feature

  **参考**：
  - `app/src/main/java/cc/pscly/onememos/ui/Routes.kt`
  - `app/src/main/java/cc/pscly/onememos/ui/OneMemosApp.kt`

  **验收**：
  - `:app:assembleDebug` 成功

- [x] 10. 建立 `:core:designsystem`（主题 tokens + 组件库入口 + 使用规范）

  **要做什么**：
  - 迁移并整理：
    - `app/src/main/java/cc/pscly/onememos/ui/theme/*`
    - `app/src/main/java/cc/pscly/onememos/ui/component/*`
    - `app/src/main/java/cc/pscly/onememos/ui/util/*`（haptics/formatter 等应归口）
  - 输出“使用规范”：feature 不再直接自定义卡片/按钮/间距，统一走 designsystem

  **参考**：
  - `app/src/main/java/cc/pscly/onememos/ui/theme/OneMemosTheme.kt`
  - `app/src/main/java/cc/pscly/onememos/ui/component/InkCard.kt`
  - `app/src/main/java/cc/pscly/onememos/ui/component/ScrollPaper.kt`
  - `app/src/main/java/cc/pscly/onememos/ui/util/OneMemosHaptics.kt`

  **验收**：
  - `:app:assembleDebug` 成功
  - `:app:testDebugUnitTest` 通过（必要时迁移 tests）

- [x] 11. 迁移 feature：Home（`:feature:home`）

  **要做什么**：
  - 迁移：`app/src/main/java/cc/pscly/onememos/ui/feature/home/*`
  - 替换依赖：用 `:core:designsystem` 组件与 `:core:navigation` contract

  **参考**：
  - `app/src/main/java/cc/pscly/onememos/ui/feature/home/HomeScreen.kt`
  - `app/src/main/java/cc/pscly/onememos/ui/feature/home/HomeViewModel.kt`
  - 单测：`app/src/test/java/cc/pscly/onememos/ui/feature/home/*`

  **验收**：
  - `:app:assembleDebug` 成功

- [x] 12. 迁移 feature：Editor（`:feature:editor`）

  **参考**：
  - `app/src/main/java/cc/pscly/onememos/ui/feature/editor/EditorScreen.kt`
  - `app/src/main/java/cc/pscly/onememos/ui/feature/editor/EditorViewModel.kt`
  - 单测：`app/src/test/java/cc/pscly/onememos/ui/feature/editor/TagCompletionTest.kt`

  **验收**：
  - `:app:assembleDebug` 成功

- [x] 13. 迁移 feature：Settings / Welcome / Auth / Profile / ShareCard / QuickCapture（逐个迁移，保持每次可编译）

  **参考（各模块入口）**：
  - Settings：`app/src/main/java/cc/pscly/onememos/ui/feature/settings/*`
  - Welcome：`app/src/main/java/cc/pscly/onememos/ui/feature/welcome/*`
  - Auth：`app/src/main/java/cc/pscly/onememos/ui/feature/auth/*`
  - Profile：`app/src/main/java/cc/pscly/onememos/ui/feature/profile/*`
  - ShareCard：`app/src/main/java/cc/pscly/onememos/ui/feature/sharecard/*`
  - QuickCapture：
    - `app/src/main/java/cc/pscly/onememos/ui/feature/quickcapture/*`
    - `app/src/main/java/cc/pscly/onememos/qs/*`
    - `app/src/main/java/cc/pscly/onememos/screenshot/*`
    - `app/src/main/java/cc/pscly/onememos/overlay/*`

  **验收**：
  - 每迁移一个 feature：`.\gradlew.bat :app:assembleDebug` 必须通过

- [x] 14. DI 聚合与“所有权表”落地（Hilt modules 归属清晰）

  **要做什么**：
  - 明确哪些 Hilt Module 放在 `:core:*`，哪些放在 `:app`（聚合）
  - 避免重复绑定：尤其是 `OkHttpClient`、`Retrofit`、`ImageLoader`、DB、Repository

  **参考**：
  - `app/src/main/java/cc/pscly/onememos/di/*`

  **验收**：
  - `:app:assembleDebug` + `:app:testDebugUnitTest` 通过

- [x] 15. 基础门禁脚本（PowerShell）：build/test/lint + 可选 baseline/macrobenchmark

  **要做什么**：
  - 在 `scripts/` 新增：
    - `verify.ps1`：一键跑 assembleDebug/test/lintDebug（失败直接 exit non-zero）
    - `perf-startup.ps1`：检查 adb 设备 → 运行 macrobenchmark（若可用）→ 保存结果
    - `perf-home-scroll.ps1`：同上（主页滚动/jank 指标）
    - `baselineprofile.ps1`：生成/验证 baseline profile（若设备可用）
  - 脚本里固化“减少噪声”的提醒：禁动画/充电/温控

  **参考**：
  - 现有脚本：`scripts/copy-benchmark-apk.ps1`、`scripts/copy-debug-apk.ps1`
  - 性能模块：`:macrobenchmark`、`:baselineprofile`

  **验收**：
  - `pwsh -File scripts/verify.ps1` 成功

- [x] 16. 冷启动治理（Top2）：测量 → 定位 → 优化 → 复测

  **要做什么**：
  - 先用 macrobenchmark 增加/确认启动场景测量（冷启动/热启动口径固定）
  - 优化点优先级：Application.onCreate 里任何可延迟的工作（调度/预取/初始化）都延后到首帧后
  - 复测并记录数据（写入 docs 或脚本输出目录）

  **参考**：
  - `app/src/main/java/cc/pscly/onememos/OneMemosApplication.kt`
  - `app/src/main/java/cc/pscly/onememos/MainActivity.kt`
  - `app/src/main/java/cc/pscly/onememos/worker/MemoDerivedFieldsRebuildScheduler.kt`

  **验收**：
  - 启动 macrobenchmark 可运行（设备可用时）
  - 至少记录一份“优化前/后”的启动指标（中位数/分位数）

- [x] 17. 主页列表治理（Top2）：jank 指标 + 滚动路径瘦身

  **要做什么**：
  - 基于 macrobenchmark/jank 指标定位：重组频率、图片解码/GC、富预览策略
  - 治理方向（不新增功能）：
    - 滚动路径只做轻量渲染
    - 重计算字段预计算/缓存
    - 图片请求与 key 稳定

  **参考**：
  - `app/src/main/java/cc/pscly/onememos/ui/feature/home/HomeScreen.kt`
  - `app/src/main/java/cc/pscly/onememos/ui/feature/home/MemoItem.kt`
  - `app/src/main/java/cc/pscly/onememos/ui/component/MarkdownPaper.kt`
  - `app/src/main/java/cc/pscly/onememos/ui/feature/home/StickyRichPreviewPolicy.kt`

  **验收**：
  - home scroll macrobenchmark 可运行（设备可用时）
  - 至少记录一份“治理前/后”的 jank 指标

- [x] 18. 收尾：回归门禁 + benchmark APK 产物 + 文档记录

  **要做什么**：
  - 运行全门禁：test/lint/assemble
  - 打包 benchmark APK（按仓库约定，不打 debug）并用时间戳命名
  - 更新 `.ai_session.md` 记录关键结构变化与验证命令

  **参考**：
  - 构建配置：`app/build.gradle.kts`（已有 benchmark buildType）
  - 脚本：`scripts/copy-benchmark-apk.ps1`

  **验收**：
  - `.\gradlew.bat :app:assembleBenchmark` 成功
  - 产物路径输出（示例）：`app/build/outputs/apk/benchmark/YYYY-MM-DDTHH-mm-ss.apk`

---

## Commit 策略（执行时遵守仓库约定）

> 仓库约定：每次修改后都要提交；阶段完成后产出 benchmark APK。

建议按波次提交（示例）：
- `refactor(modules): add core/feature module skeleton`
- `refactor(core): extract model/domain/database/network/data/sync modules`
- `refactor(ui): extract designsystem module`
- `refactor(feature): move home/editor/settings... into feature modules`
- `chore(scripts): add local verify/perf scripts`
- `perf(startup): improve cold start and document measurements`
- `perf(home): reduce list jank and document measurements`

---

## 风险清单与降级策略

- Hilt 编译/运行期问题：每次迁移模块后立刻跑 `assembleDebug + test`，不要堆到最后
- WorkManager 语义回归：保持 uniqueName/keys 不变；必要时为 scheduler/worker 写回归单测
- baselineprofile/macrobenchmark 断裂：每波次至少保证 `:baselineprofile:assemble` 与 `:macrobenchmark:assemble` 仍可通过
- 性能数据噪声：本地脚本强调同一台设备、充电、禁动画、重复多次取中位数
