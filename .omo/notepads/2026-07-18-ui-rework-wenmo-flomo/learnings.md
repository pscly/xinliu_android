## [2026-07-18] Session start
- Plan: 2026-07-18-ui-rework-wenmo-flomo (Momus OKAY v2)
- ADRs: 0008 four-axis theme, 0009 LXGW+arm64, 0010 mikepenz, 0011 no delete/favorites=collections
- Current baseline: versionCode=157 versionName=1.9.0 applicationId=cc.pscly.onememos
- ThemePalette is enum PAPER_INK|INDIGO|CYBER; AppSettings.themePalette; DataStore key theme_palette
- OneMemosTheme takes OneMemosThemeConfig(palette, themeMode)
- Commands: ./gradlew or ./gradlew.bat; Linux env so prefer ./gradlew
- AGENTS.md: Chinese comments/docs; commit after changes; full §8 release for APK-affecting changes; benchmark not debug for release APK
- Dual package: debug only applicationIdSuffix .dev; never on benchmark/release

## [2026-07-18] P0.1 done
- Prepended planning summary to `.ai_session.md` top (2026-07-18 文墨·flomo 规划纪要); prior history intact; no commit.

## [2026-07-18] P0.2 done
- Commit: `2bc2dd9` `docs(plan): 文墨·flomo 界面大重构规划与 ADR 0008–0011`
- Files (7, pure docs only):
  - `.ai_session.md`（P0.1 规划纪要）
  - `CONTEXT.md`（外观 / 随笔生命周期词汇）
  - `docs/adr/0008-theme-descriptor-four-axes.md`
  - `docs/adr/0009-bundle-lxgw-wenkai-arm64-only.md`
  - `docs/adr/0010-markdown-engine-mikepenz.md`
  - `docs/adr/0011-no-true-delete-favorites-as-collections.md`
  - `.omo/plans/2026-07-18-ui-rework-wenmo-flomo.md`（未被 ignore，已纳入）
- Excluded (not staged): `.omo/evidence/`、`.omo/notepads/`、其余 `.omo/plans/*`、`docs/research/`、`docs/superpowers/research/`、`docs/toolchain-verdict-2026-07-14.md`、`.omo/boulder.json` 等
- No product Kotlin/Gradle、无 versionCode/versionName 变更；未 push；未触发 §8

## [2026-07-18] M1.9 Roborazzi + WCAG
- **Version drift**: 计划钉 `roborazzi = "1.41.0"`；在 AGP 9.2.1 上 apply 失败：`Extension of type 'TestedExtension' does not exist`。上游 1.56.0 起迁移出 TestedExtension（AGP 9 兼容）；采用最新稳定 **`1.69.0`**（Maven release 2026-07-17）。
- Catalog 条目：`versions.roborazzi`、`libraries.roborazzi` / `roborazzi-compose` / `roborazzi-junit-rule`、`plugins.roborazzi`（id `io.github.takahirom.roborazzi`）。
- 接线：root `apply false`；`:core:designsystem` apply 插件 + testImplementation 三件套；`roborazzi { outputDir = src/test/screenshots }`；unitTests `robolectric.pixelCopyRenderMode=hardware`。
- 任务：`recordRoborazziDebug` / `verifyRoborazziDebug` / `compareRoborazziDebug` 均在 verification group 可见。
- WCAG：纯 JVM `WcagContrast` + Parameterized 矩阵 PAPER_INK/INDIGO/CYBER × light/dark；普通文本 ≥4.5、onPrimary/onSecondary ≥3；MOON_WHITE 结构预留注释待 M1.6。
- 金图：仅 1 张 smoke `src/test/screenshots/cc.pscly.onememos.ui.theme.RoborazziSmokeTest.paperInkLight_primaryLabel_captures.png`（~4KB）。
- 验证：`:core:designsystem:testDebugUnitTest` **33 tests, 0 failures**；`recordRoborazziDebug` + `verifyRoborazziDebug` 绿。
- 未改 versionCode/包名/ThemeDescriptor/M1.1 令牌；未 push/release。

## [2026-07-18] M1.1 done
- Commit: `741743e` `feat(designsystem): add Ink* tokens and wire 12 primitives`（16 文件，+431/-165）
- 新建 `theme/InkSpacing.kt`（X1..X150 尺度 + LinePitch=30.sp/CodeLineHeight=20.sp + 语义别名）、`InkShape.kt`（14/12/10/2dp 四档 + `sealFor(size)` 44dp 阈值规则）、`InkBorder.kt`（Hairline/Stamp/TableCell 宽度 + 45/22/80/40 等命名透明度）、`InkMotion.kt`（按压 0.92/120ms、盖章 600/45%/35% 关键帧、查看器 2200ms/320ms/2.5x/5x）
- 12 原语全部改取令牌；颜色仍走 `MaterialTheme.colorScheme`；不随主题的固定色（标签文字、宣纸毛边、朱砂、行内代码底）暂收 `InkTone`（在 InkBorder.kt 内），等 M1.6 色板任务归并
- 工作树混入他车道改动（core/data、core/model、OneMemosTheme.kt、plan 文件），只 stage 本任务 16 文件提交
- `./gradlew :core:designsystem:compileDebugKotlin` BUILD SUCCESSFUL（仅遗留 ClickableText deprecated 警告，非本次引入）
- 残留检查：`grep -rn "\.dp\b|\.sp\b|Color(0x" component/*.kt` 零命中
- 经验：批量 sed 替换时“先全局替换 alpha 再替换含同行 alpha 的组合模式”会失配，需二次 grep 兜底（MarkdownPaper 引用块 lineHeight 漏改一处，已补）

## [2026-07-18] M1.2 ThemeDescriptor + DataStore 迁移
- Domain: `ThemeDescriptor(palette, texture, density, typeScale, fontFamily)` + 轴枚举；默认预设 `WENMO_ZHUSHA`
- 映射：PAPER_INK/INDIGO → SCROLL+STANDARD+WENKAI；CYBER → SCROLL+STANDARD+SYSTEM；未知 → 文墨·朱砂
- DataStore key `theme_descriptor` JSON（org.json.JSONObject，避免 core/model 挂 serialization 插件）；旧 `theme_palette` 读路径 resolve + init 异步迁移后清除
- `AppSettings.themePalette` 改为从 descriptor 推导的计算属性；构造请用 `themeDescriptor=`
- `OneMemosThemeConfig` 主字段 `themeDescriptor`，次构造 `(palette, themeMode)` + `palette` getter 兼容 call sites
- M2/M3 schema 默认：listLayout=AUTO, swipeEnabled=true, swipeRight=ADD_TO_TODO, swipeLeft=FAVORITE, pageTransitionsEnabled=true, readingFontScale/lineHeight=STANDARD
- 单测：`core/data` 加 Robolectric+junit；`@Config(sdk=[34])`（compileSdk 37 不被 robolectric 4.12 支持）
- QA：`./gradlew :core:data:testDebugUnitTest --tests '*ThemeDescriptor*'` 全绿；`:app:testDebugUnitTest --tests '*SettingsRepositoryImpl*'` 全绿
- 注意：并行 M1.1 改了 designsystem 原语，`:core:designsystem:compile` 可能因 InkSpacing.LinePitch 类型问题失败——与本任务无关

## [2026-07-18] Orchestrator verified M1.1/M1.2/M1.8/M1.9
- M1.1 `741743e`: Ink* + 12 primitives; component residual `.dp/.sp/Color(0x` = 0
- M1.2 `6347e16`+`1849178`+`94d9e63`: ThemeDescriptor + DataStore migrate; ThemeDescriptorMigrationTest 9/0 fail
- M1.8 `ae9b3d8`: abiFilters arm64-v8a; applicationIdSuffix `.dev` only on debug; benchmark initWith release
- M1.9 `2236e1e`: Roborazzi **1.69.0** (AGP9 drift from plan 1.41.0); Wcag 24+4 + Roborazzi smoke 1 green; designsystem unit tests UP-TO-DATE BUILD SUCCESSFUL
- Plan checkboxes 3/4/10/11 marked [x]

## [2026-07-18] M1.5 霞鹜文楷入包 + Typography 字体档（已完成）

- 三笔提交，每笔独立可回滚：
  - `28188e6` `feat(designsystem): bundle LXGW WenKai full TTF and OFL license` — 字体 25,575,676 bytes (~24.4MB)，存入 `res/font/lxgw_wenkai.ttf`；OFL 全文存入 `assets/licenses/OFL.txt`（94 行）
  - `f04d598` `feat(designsystem): wire fontFamily into oneMemosTypography and OneMemosTheme` — `oneMemosTypography(fontFamily: ThemeFontFamily)` 分支：WENKAI → `Font(R.font.lxgw_wenkai)` 标题 + SansSerif 正文；SYSTEM → Serif 标题 + SansSerif 正文；`OneMemosTypography` 默认值走 WENKAI
  - `bd46642` `feat(settings): add WenKai OFL credit to About page` — 字符串 `settings_about_font_credit`（“标题字体：霞鹜文楷（LXGW WenKai），SIL Open Font License 1.1”），显示在 VersionCard 内
- R 资源解析：`R.font.lxgw_wenkai` 映射到 `0x7f070000`（`generateDebugRFile` 验证通过）
- 编译验证：`./gradlew :core:designsystem:compileDebugKotlin` BUILD SUCCESSFUL
- 未改 versionCode/包名/ABI；未 push/release
- 注意：ThemeDescriptor 预设 WENMO_ZHUSHA / PAPER_INK / INDIGO 的 `fontFamily=WENKAI`；CYBER / MOON_WHITE / DYNAMIC 的 `fontFamily=SYSTEM`

## [2026-07-18] M1.5 done
- Commits: `28188e6` (font + OFL), `f04d598` (Typography wiring), `bd46642` (About credit)
- 霞鹜文楷 Regular v1.522 (LXGW WenKai) 落盘：`core/designsystem/src/main/res/font/lxgw_wenkai.ttf`（25,575,676 bytes），`assets/licenses/OFL.txt`
- `oneMemosTypography(fontFamily: ThemeFontFamily)` 生成搭配：WENKAI → 标题文楷 + 正文 SansSerif；SYSTEM → 标题 Serif + 正文 SansSerif
- `OneMemosTheme` 传入 `config.themeDescriptor.fontFamily`，不再硬编码 `OneMemosTypography`
- `AboutAdvancedScreen.kt` 版本卡片内附加「标题字体：霞鹜文楷（LXGW WenKai），SIL Open Font License 1.1」
- `R.font.lxgw_wenkai` 在 `cc.pscly.onememos.core.designsystem.R` 已验证
- `./gradlew :core:designsystem:compileDebugKotlin` BUILD SUCCESSFUL
- 注意：并行 M1.3/M1.6 改写了 OneMemosTheme（加了 LocalThemeTexture + DYNAMIC 色板）；M1.5 仅替换 typography 选择，不动其他

## [2026-07-18] M1.6 done
- Commits:
  - `ad9c43a` `feat(model): ThemePalette 增加 MOON_WHITE 与 DYNAMIC`
  - `37c41fb` `feat(theme): MOON_WHITE 色板与 DYNAMIC 动态色解析`
  - `c9c6783` SettingsHub mapAppearance 穷尽新枚举
- ThemePalette 五档：PAPER_INK | INDIGO | CYBER | MOON_WHITE | DYNAMIC
- MOON_WHITE light：primary=焦茶 #383431、secondary=雅灰 #878885、bg/surface=草白/米宣纸
- MOON_WHITE dark：NightBg/Surface + 米灰主/次色，on* WCAG AA
- DYNAMIC：`dynamicLight/DarkColorScheme(Context)` @ API 31+；context null 或 <31 → PAPER_INK
- ColorSchemes API：`oneMemos*ColorScheme(palette, context: Context? = null)`
- OneMemosTheme 色路径：`LocalContext`（preview 传 null）；与并行 M1.3/M1.5（LocalThemeTexture / oneMemosTypography）共存于同文件
- fromLegacyPalette：MOON_WHITE→MINIMAL+RELAXED+SYSTEM；DYNAMIC→SCROLL+STANDARD+SYSTEM
- WcagContrastTest：entries 排除 DYNAMIC；策展 4×2×4 断言
- 验证：`:core:model:compileKotlin` + `:core:designsystem:testDebugUnitTest` **41 tests, 0 failures**；settings/data/feature:settings compile 绿
- 未改 versionCode/包名/外观预设 UI（M1.7）；未 mark plan checkbox；未 push/release
- 未提交：并行车道 InkCard/ScrollPaper/TagChip/ThemeTextureLocals、About 文案、plan 勾选

## [2026-07-18] M1.3 质感轴：LocalThemeTexture + MINIMAL 分支
- Commit: `80d5c21` `feat(designsystem): LocalThemeTexture and MINIMAL texture for paper/card`（6 文件，+118/-28）
- `theme/ThemeTextureLocals.kt`：`LocalThemeTexture = staticCompositionLocalOf { ThemeTexture.SCROLL }`；OneMemosTheme 用 `CompositionLocalProvider(LocalThemeTexture provides config.themeDescriptor.texture)` 包住 MaterialTheme，**不动** colorScheme/typography 选择（避让并行 M1.5/M1.6）
- 质感分支模式：组件内 `val texture = LocalThemeTexture.current`，SCROLL 走既有绘制；MINIMAL 跳过重绘
- ScrollPaper/ScrollPaperSurface：MINIMAL 用 `.then(textureModifier)` 条件挂 `drawWithCache`（无横线/无朱砂竖线、保留发丝边框）；默认 contentPadding 直接读 `LocalThemeTexture.current`（默认参数在 composable 作用域求值，合法）
- InkSpacing 新增 `PaperPadding*Minimal`（X20/X20/X16），仍只引用既有尺度
- InkCard MINIMAL：idle 描边 alpha OutlineStrong→OutlineSoft，聚焦只换色、不再叠第二层 `Modifier.border`
- TagChip MINIMAL：bg=surface、fg=onSurface/onSurfaceVariant（selected 区分）、outline 描边沿用 TagIdle/OutlineSelected；SCROLL 保留 hash HSV。`remember(tag,selected)` 仅在 SCROLL 分支内求值
- 验证：`:core:designsystem:compileDebugKotlin` BUILD SUCCESSFUL；改动组件 `grep -nE "\.dp\b|\.sp\b|Color\(0x"` 零命中
- 坑：并行代理 f04d598 提交 OneMemosTheme 时把我未提交的 LocalThemeTexture 接线一并带走，导致 HEAD 引用了仍未跟踪的 ThemeTextureLocals.kt——**跨车道同文件时新建符号文件要最先提交**，否则 HEAD 处于不可独立编译状态

## [2026-07-18] Orchestrator independent verify M1.3/M1.5/M1.6
- M1.3 `80d5c21`: LocalThemeTexture + ScrollPaper/Surface/InkCard/TagChip MINIMAL; compileDebugKotlin green
- M1.5 `28188e6`+`f04d598`+`bd46642`: lxgw_wenkai.ttf 25MB + OFL + oneMemosTypography(fontFamily) + About font credit; compile green
- M1.6 `ad9c43a`+`37c41fb`+`c9c6783`: ThemePalette 5 entries MOON_WHITE/DYNAMIC; dynamic API31+ fallback PAPER_INK; Wcag excludes DYNAMIC; settings map exhaustive; designsystem/settings/feature:settings compile + WcagContrastTest green
- Plan checkboxes 5/7/8 marked [x]; next M1.4 TagChip SCROLL desaturation (MINIMAL quiet already in M1.3)

## [2026-07-18] M1.4 TagChip SCROLL desaturation
- 变更文件: core/designsystem/.../TagChip.kt (仅此一个产品文件)
- 文墨质感(SCROLL) tagBackgroundColor 饱和度: idle 0.40f -> 0.16f, selected 0.52f -> 0.26f; 色相哈希与明度(0.86/0.92)保持不变
- 清简(MINIMAL) 路径未动: surface 底 + onSurface/onSurfaceVariant 文 + outline 描边; `#$label` 前缀保留
- 编译: ./gradlew :core:designsystem:compileDebugKotlin -> BUILD SUCCESSFUL in 8s
- 提交: f729df0 feat(designsystem): TagChip 文墨质感哈希彩色降饱和安静退后
- 文本对比: bg 明度更高(0.86~0.92) 时走 InkTone.TagTextOnLight(luminance>0.55 判定未变), 降饱和后亮度基本不变, 对比无回归风险; Roborazzi 截图未跑(小改动, 编译为门槛)

## [2026-07-18] Orchestrator independent verify M1.4
- Commit `f729df0`: TagChip SCROLL sat idle 0.40→0.16, selected 0.52→0.26; hue/value unchanged; MINIMAL path + `#` prefix intact
- Scope: 1 file TagChip.kt only
- `./gradlew :core:designsystem:compileDebugKotlin` BUILD SUCCESSFUL (UP-TO-DATE, exit 0)
- Plan checkbox 6 marked [x]; next M1.7 Appearance presets

## [2026-07-18] M1.7 风格预设 + Appearance 能力页改版

### Commits（4 笔原子提交，未勾计划 checkbox 9）
- `754cbdd` `feat(model): 四处厂风格预设 QINGJIAN/YEHANG/SAIBO + FACTORY_PRESETS`
- `da5dc2c` `feat(settings): SettingsRepository 接口暴露 setThemeDescriptor`
- `a84dc49` `feat(settings): Appearance 能力快照暴露 ThemeDescriptor + SetThemeDescriptor`
- `cfee9bd` `feat(settings): 外观页风格预设 + 高级四轴调节`

### 预设表（结构相等选中；非 fromLegacyPalette）
| Id | palette | texture | density | typeScale | fontFamily |
|---|---|---|---|---|---|
| WENMO_ZHUSHA | PAPER_INK | SCROLL | STANDARD | STANDARD | WENKAI |
| QINGJIAN_YUEBAI | MOON_WHITE | MINIMAL | RELAXED | STANDARD | SYSTEM |
| YEHANG_DAILAN | INDIGO | SCROLL | STANDARD | STANDARD | WENKAI |
| SAIBO_FLUOR | CYBER | **MINIMAL** | **COMPACT** | STANDARD | SYSTEM |

- `fromLegacyPalette(CYBER)` 仍为 SCROLL+STANDARD（迁移兼容，未改）
- DYNAMIC 仅高级色板选项，`Build.VERSION.SDK_INT >= 31`（S）才显示；非出厂预设

### 链路
- Snapshot 主字段 `themeDescriptor`；`themePalette` 派生 getter
- 命令 `SetThemeDescriptor` → `settingsRepository.setThemeDescriptor`；`SetThemePalette` 保留（仅改色板轴）
- UI：预设一键 `SetThemeDescriptor(factory)`；高级轴 `descriptor.copy(axis=…)` 再 SetThemeDescriptor
- OneMemosTheme 已读 themeDescriptor，切换即时生效无需重启

### 验证
- `./gradlew :core:settings:testDebugUnitTest --tests '*Appearance*'` 绿
- `./gradlew :feature:settings:testDebugUnitTest` 绿（含 Appearance + AccessibilityMatrix）
- 未 bump versionCode / 未 push / 未 release

### 测试注意
- Compose `onNodeWithText("跟随系统", substring=true)` 会同时命中「跟随系统动态色」→ 用 `substring=false` 精确匹配

## [2026-07-18] Orchestrator independent verify M1.7
- Commits: 754cbdd (presets), da5dc2c (setThemeDescriptor interface), a84dc49 (capability), cfee9bd (UI)
- FACTORY_PRESETS: WENMO_ZHUSHA / QINGJIAN_YUEBAI / YEHANG_DAILAN / SAIBO_FLUOR (CYBER+MINIMAL+COMPACT)
- fromLegacyPalette(CYBER) still SCROLL+STANDARD (migration intact)
- Snapshot.themeDescriptor primary; SetThemeDescriptor command; DYNAMIC gated API 31+
- UI: Preset → Mode → Advanced(palette/texture/density/font) → Overlay → Duration
- Tests: :core:settings *Appearance* + :feature:settings:testDebugUnitTest BUILD SUCCESSFUL
- Plan checkbox 9 marked [x]; next M1.R 1.10.0

## [2026-07-18] M1.R local acceptance
- **Commit**: `43c0a176bc01e473c52bdbe051a1bb90246d729e` `chore(release): bump 1.10.0 (158) M1 设计系统地基`
  - Files only: `app/build.gradle.kts` (157/1.9.0 → 158/1.10.0) + `.ai_session.md` (M1.R 发布纪要)
  - Not staged: `.omo/plans/*`、`.omo/evidence/`、`.omo/notepads/`、keystores 等
- **Architecture**: `./scripts/verify-architecture.sh` → exit 0 (`verify-architecture.sh: OK`)
- **Focused tests**: exit 0 / BUILD SUCCESSFUL (~10s, mostly UP-TO-DATE)
  ```
  ./gradlew :core:designsystem:testDebugUnitTest \
    :core:settings:testDebugUnitTest --tests '*Appearance*' \
    :feature:settings:testDebugUnitTest \
    :core:data:testDebugUnitTest \
    -Pkotlin.compiler.execution.strategy=in-process --stacktrace
  ```
- **Benchmark APK** (timestamped, post-version-commit rebuild):
  - Path: `/root/1codes/xinliu_android/app/build/outputs/apk/benchmark/2026-07-18T17-01-29.apk`
  - Size: 71M (74377772 bytes)
  - `aapt2 dump badging`: `package: name='cc.pscly.onememos' versionCode='158' versionName='1.10.0'`
  - `output-metadata.json`: applicationId=`cc.pscly.onememos`, versionCode=158, versionName=1.10.0, variant=benchmark
  - No `.dev` suffix (formal package invariant held)
- **REMOTE / §8**: `BLOCKED_SIGNING_MISSING` — `ANDROID_RELEASE_STORE_FILE` / `STORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` 本机均为空；不得 push tag / 不得用 debug/临时签名发 latest Release。
- **Remaining when secrets available**:
  1. push `main`（本地已 ahead origin 20，含 M1.1–M1.7 + 本 release bump）
  2. tag `v1.10.0` 并 push
  3. 等 GitHub Actions 成功
  4. 核验 APK 包名/版本 + 证书 SHA-256 `58749c794f0c54af6b69bb6d80248a9fda0b75c687fde55b98d9575fc091633e`
  5. 发布非草稿非预发布的 GitHub latest Release（仅固定签名 Benchmark APK）
- **Not done here (by design)**: no push, no tag, no GitHub Release, plan checkbox left for orchestrator

## [2026-07-18] Orchestrator independent verify M1.R
- Commit 43c0a17 verified: only app/build.gradle.kts + .ai_session.md
- APK exists: app/build/outputs/apk/benchmark/2026-07-18T17-01-29.apk 71M
- output-metadata.json: applicationId=cc.pscly.onememos versionCode=158 versionName=1.10.0
- Plan checkbox 12 marked [x]; remote §8 remains BLOCKED_SIGNING_MISSING
- Next: M2.0 shared element transition spike

## [2026-07-18] M2.0 共享元素转场 spike — FEASIBLE

### 决策
- **FEASIBLE**（非 FALLBACK）：M2.9 走 Navigation3 + Compose Shared Element；降级「淡入淡出 + 共享轴」仅作真机失败兜底。

### 本仓版本与现状
- `gradle/libs.versions.toml`：`navigation3 = "1.1.4"`，`composeBom = "2026.06.00"`
- `AppNavigationHost.kt`：`NavDisplay(entries = decoratedEntries, onBack = ::handleBack)` — **无** `transitionSpec` / `sharedTransitionScope`
- 已有 `core/designsystem/.../accessibility/ReducedMotion.kt` 可读系统动画 scale
- 多栈：`NavigationStateMachine` + 仅 active 栈进 `NavDisplay`（跨分区不做共享元素）

### API 表面（1.1.4 本机 jar 反查）
- `NavDisplay(List<NavEntry<T>>, modifier, alignment, sceneStrategies, sceneDecoratorStrategies, **SharedTransitionScope?**, SizeTransform?, **transitionSpec**, **popTransitionSpec**, **predictivePopTransitionSpec**, onBack)`
- metadata helpers：`NavDisplay.transitionSpec` / `popTransitionSpec` / `predictivePopTransitionSpec` → `TransitionKey` / `PopTransitionKey` / `PredictivePopTransitionKey`
- `SharedEntryInSceneNavEntryDecorator(SharedTransitionScope)`
- `LocalNavAnimatedContentScope`（entry 内取 `AnimatedContentScope` 给 `sharedElement`/`sharedBounds`）

### 第一方依据
- https://developer.android.com/jetpack/androidx/releases/navigation3 — 1.1.0 stable *Shared Elements between Scenes*
- https://developer.android.com/guide/navigation/navigation-3/animate-destinations — `SharedTransitionLayout` + `sharedTransitionScope` + 三规格 ContentTransform
- https://developer.android.com/develop/ui/compose/animation/shared-elements — `sharedElement` / `sharedBounds`
- 本仓既有研究：`docs/superpowers/research/2026-07-14-navigation3-stable-dependency-matrix.md`（1.1.0 已列 SharedTransitionScope）

### 落盘
- 计划批注：`.omo/plans/2026-07-18-ui-rework-wenmo-flomo.md`（M2.0 表后 blockquote）
- ADR：`docs/adr/0012-navigation-transition-strategy.md`
- **未**改产品 Kotlin；**未** bump versionCode；**未** push/tag/release；checkbox 13 留给 orchestrator

### M2.9 草图（零产品改动）
```kotlin
// AppNavigationHost 未来接线示意
SharedTransitionLayout {
  NavDisplay(
    entries = decoratedEntries,
    onBack = ::handleBack,
    sharedTransitionScope = this,
    transitionSpec = { /* fade+shared-axis 或 None if ReducedMotion */ },
    popTransitionSpec = { /* ... */ },
    predictivePopTransitionSpec = { _, _ -> /* ... */ },
  )
}
// Home MemoItem / Editor: sharedBounds(rememberSharedContentState("memo/$uuid"), LocalNavAnimatedContentScope.current)
```


## [2026-07-18] Orchestrator independent verify M2.0
- DECISION: FEASIBLE (shared element via NavDisplay sharedTransitionScope)
- Plan annotation present under 里程碑 2 table
- ADR docs/adr/0012-navigation-transition-strategy.md present
- No product Kotlin/version change
- Plan checkbox 13 marked [x]; next M2.1 home timeline

## [2026-07-18] M2.1 首页时间线重做（日期分组 + 密度留白）

### 文件变更
| 文件 | 操作 | 说明 |
|------|------|------|
| `core/designsystem/.../ThemeDensityCompositionLocal.kt` | 新建 | `LocalThemeDensity` = `staticCompositionLocalOf { ThemeDensity.STANDARD }` |
| `core/designsystem/.../OneMemosTheme.kt` | 修改 | `CompositionLocalProvider` 新增 `LocalThemeDensity provides config.themeDescriptor.density` |
| `feature/home/.../DateHeader.kt` | 新建 | `DateHeader` composable：大号中文日期 + 细墨线分隔线 |
| `feature/home/.../HomeScreen.kt` | 修改 | LazyColumn 按日期分组 + 密度轴 spacing 自适应 |
| `feature/home/.../HomeDateGroupingTest.kt` | 新建 | 6 个 JVM 单元测试覆盖日期分组逻辑 |

### DateHeader 设计
- 日期格式：`yyyy年MM月dd日`（`java.time.format.DateTimeFormatter`）
- 字体：取自 `MaterialTheme.typography.headlineSmall`（由 OneMemosTheme 按主题档下发文楷/系统字体）
- 分隔线：`InkBorder.Hairline`（1.dp）+ `InkBorder.OutlineSoft`（0.22f alpha）+ `colorScheme.outline`
- 间距：`InkSpacing.CardPadding`（X14）垂直 padding，`InkSpacing.X8` 日期文本与分隔线间距

### 密度→间距映射
| 密度 | contentPadding H | contentPadding V | itemGap |
|------|-----------------|-----------------|---------|
| COMPACT | InkSpacing.X8 | InkSpacing.X8 | InkSpacing.X8 |
| STANDARD | InkSpacing.X16 | InkSpacing.X12 | InkSpacing.X12 |
| RELAXED | InkSpacing.X24 | InkSpacing.X20 | InkSpacing.X20 |

### 日期分组实现
- `internal sealed class GroupedListItem` 三种子类型：`DateHeader(dateKey, epochMillis)` / `MemoEntry(index, memo)` / `LoadingEntry(index)`
- `buildGroupedItemsFromList(memos: List<Memo?>)` 纯数据分组逻辑（可 JVM 测试）
- `buildGroupedItems(pagingItems: LazyPagingItems<Memo>)` 包装转换，供 Composable 调用
- 分组遍历一次，按 `DateTimeFormatter.formatYmd(memo.createdAt)` 判断日期变更
- LazyColumn key：DateHeader → `"date_header_$dateKey"`，MemoEntry → `memo.uuid`，LoadingEntry → `"loading_$index"`
- LazyColumn contentType：`"date_header"` / `"memo"` / `"loading"`
- 性能：`buildGroupedItems` 在 Composable 中直接调用（未使用 `remember` 缓存，因 `remember` 在 LazyColumn content 的 `else` 分支内引发 Composable context 错误）

### 验证
- `./gradlew :core:designsystem:compileDebugKotlin` BUILD SUCCESSFUL
- `./gradlew :feature:home:compileDebugKotlin` BUILD SUCCESSFUL
- `./gradlew :feature:home:testDebugUnitTest` **25 tests, 0 failures**（含新增 6 个日期分组测试）
- 测试使用 `DateTimeFormatter.formatYmd()` 动态计算期望日期，避免时区硬编码偏差
- 未改 versionCode/包名；未 push/release

### 踩坑
1. `remember` 在 LazyColumn 的 `else { }` 分支内调用会触发 `@Composable invocations can only happen from the context of a @Composable function` 错误，即使代码在 `@Composable` 函数内。根因可能与 Kotlin 编译器对 `if/else if/else` 链中 Composable lambda scope 的识别有关。改为直接调用非 Composable 函数。
2. 编辑文件时，`GroupedListItem` 等新增声明被意外插入到函数内部而非文件顶层，需要特别注意 `edit` 工具匹配的精确边界。
3. 测试中的硬编码日期字符串 (`"2023-11-14"`) 因时区差异（Asia/Shanghai vs UTC）而失败，改为使用 `DateTimeFormatter.formatYmd()` 动态生成期望值。

## M2.2 图片宫格（MemoItem 响应式图片布局）

### 实现
- 仅改 `feature/home/.../MemoItem.kt`：删除旧的“单图侧栏缩略图(76dp) / 双图 88dp 行”逻辑，新增 `MemoImageGrid` + `MemoImageTile` 两个私有 Composable
- 布局规则：1 图通栏 3:2；2 图等宽并排(1:1)；3~4 图 2×2 宫格；5+ 图 3 列宫格、最多 9 张、最后一张叠 `+N` 角标（半透明黑底白字）
- 圆角统一 `InkShape.Card`（14dp，含卡片选中边框 shape 一并替换裸值）；瓦片间距 `InkSpacing.X6`；图片区上方间距 `InkSpacing.X12`
- Coil 改用 `SubcomposeAsyncImage`：底层 `Surface(surfaceVariant)` 做加载占位（非白屏），`error` 槽叠加 `Icons.Outlined.BrokenImage`（onSurfaceVariant 60% 透明度）
- 不再显式 `.size(px)`：交由 Coil 按瓦片实际布局约束解析解码尺寸；保留 `crossfade(false)` 滚动性能优化
- `enableRichPreview` 不再限制图片数量（旧逻辑 maxThumbs=2/1），图片区只按数量排布；文本预览统一整行（rich: maxBlocks=4/maxLines=6，plain: maxLines=6）

### 令牌偏差（计划 vs 实际）
- 计划写 `InkShape.DEFAULT`(14dp)：实际令牌名是 `InkShape.Card`（RadiusL=14dp），已用 `Card`
- 计划写 `InkSpacing.X4`(4dp)：**InkSpacing 中不存在 X4**（尺度为 X1/X6/X8/X10/X12...），就近取 `InkSpacing.X6`(6dp) 作为瓦片间距

### 验证
- `./gradlew :feature:home:compileDebugKotlin` BUILD SUCCESSFUL
- `./gradlew :feature:home:testDebugUnitTest` BUILD SUCCESSFUL（全部通过，无新增/失败用例）
- 未改 versionCode/包名；未 push/release；未勾选计划复选框
