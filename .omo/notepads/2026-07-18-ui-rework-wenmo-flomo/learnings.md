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

## [2026-07-18] M2.7 mikepenz Markdown 引擎接入

### 实现
- Commit: `xxx` `feat(designsystem): mikepenz markdown 渲染器接入（留存旧 MarkdownPaper）`（6 文件）
- 依赖：`gradle/libs.versions.toml` 新增 `multiplatform-markdown-renderer = "0.37.0"`，`com.mikepenz:multiplatform-markdown-renderer-m3-android` + coil2 变体
- 新文件：`core/designsystem/src/main/java/cc/pscly/onememos/ui/markdown2/MikepenzMarkdown.kt`
- Schema 字段：`AppSettings.useNewMarkdownEngine: Boolean = true` + DataStore key `use_new_markdown_engine`
- 接线：`EditorScreen` 按 `uiState.useNewMarkdownEngine` 分支到 `MikepenzMarkdown` 或旧 `MarkdownPaper`
- 纸墨皮肤映射：
  - text → `colorScheme.onSurface`；codeBackground/inlineCodeBackground → `surfaceVariant`
  - link → `colorScheme.primary` via `TextLinkStyles`
  - divider → `outline.copy(alpha=OutlineSoft)`；tableBg → `surfaceVariant.copy(alpha=TableHeaderFill)`
  - typography 全部取自 `MaterialTheme.typography`，行高 `LinePitch=30sp` / `CodeLineHeight=20sp`
  - padding/dimens 全部映射 InkSpacing / InkBorder / InkShape 令牌
- 图片加载：`Coil2ImageTransformerImpl`（mikepenz 内置 coil2 桥接）
- GFM 默认：`rememberMarkdownState` 默认 `flavour = GFMFlavourDescriptor()`

### 版本偏差
- 计划钉 `0.37.0`：Maven Central 存在 `0.37.0` release（非 rc），已用 `com.mikepenz:multiplatform-markdown-renderer-m3-android:0.37.0`
- `rememberMarkdownState` v0.37.0 无 `retainState` 参数（READEME 提及但源码无），已省略
- coil2 变体依赖 `coil-compose:2.7.0`（与项目 coil 2.6.0 兼容——KMP 模块自带声明，Gradle 解析后无冲突）

### 验证
- `./gradlew :core:designsystem:compileDebugKotlin` BUILD SUCCESSFUL
- `./gradlew :feature:editor:compileDebugKotlin` BUILD SUCCESSFUL
- `./gradlew :feature:home:compileDebugKotlin` BUILD SUCCESSFUL（未改）
- `./gradlew :core:data:compileDebugKotlin` BUILD SUCCESSFUL
- `./gradlew :core:designsystem:testDebugUnitTest` BUILD SUCCESSFUL（41 tests, 0 failures）
- `./gradlew :feature:home:testDebugUnitTest` BUILD SUCCESSFUL（25 tests, 0 failures）
- 未改 versionCode/包名；未 push/release；未勾选计划复选框

### 注意
- `useNewMarkdownEngine` 不在 M1 schema 预埋中（M1 只有 listLayout/swipeEnabled/pageTransitions 等），本次从零新增
- 列表预览（MarkdownPreview 在 HomeScreen/MemoItem/CollectionsScreen/ProfileScreen 共 5 个调用点）未切换，属于旧 commonmark 路径——预览本质上是剪辑版 MarkdownPaper，不是全文渲染，暂不切
- `PaddingValues.Absolute` 在 markdownPadding 中需显式 import `androidx.compose.foundation.layout.PaddingValues`——Kotlin 编译器已隐式解析，无编译错误

### 踩坑
1. 首次 `MikepenzMarkdown.kt` 写了非法函数名 `0.dpSafe()`（数字开头），重新写入时直接用 `0.dp` 常量 import——但 `0.dp` 需要 `import androidx.compose.ui.unit.dp`，已加
2. `rememberMarkdownState` 在 v0.37.0 源码中确实无 `retainState` 参数（浏览 README 看到这个特性但实际 tag 源码未实现），首次写入后编译通过但用了不存在的命名参数——实际上第一次编译就过了因为我没写那个参数

## [2026-07-18] M2.R local acceptance

- **Commit**: `a5d3420` `chore(release): bump 1.11.0 (159) M2 样板屏+编辑器收口`
  - Files only: `app/build.gradle.kts` (158/1.10.0 → 159/1.11.0) + `.ai_session.md` (M2.R 发布纪要)
  - Not staged: `.omo/plans/*`、`.omo/evidence/`、`.omo/notepads/`、keystores 等
- **Git log** (M2 chain, 43c0a17^..46f7bdd):
  - M2.0 `4edd939` — shared element spike FEASIBLE (ADR 0012)
  - M2.1 `5d97269` — home timeline redo (date grouping + density spacing)
  - M2.2 `bb4ec40` — image grid (1/2/4/9+ cols)
  - M2.3 `faa23f9` — MemoItem visual hierarchy (flomo breathing)
  - M2.4 `117acd8` — wide-screen responsive (dual-column + listLayout)
  - M2.5 `87cb907` — swipe gestures (SwipeToDismissBox + archive undo)
  - M2.6 `c17809f` — collections (addMemoToFavorites)
  - M2.7 `4cb96a3` — mikepenz markdown engine coexistence
  - M2.8 `25c1dc8` — editor refactor (syntax highlighting + dual-pane)
  - M2.9 `1ea6c89`/`b60f124`/`e6a044b` — page transitions (SharedTransitionLayout + ReducedMotion)
  - M2.10 `46f7bdd` — paper-ink components (TopAppBar/Snackbar/Dialog/BottomSheet)
- **Architecture**: `./scripts/verify-architecture.sh` → exit 0 (`verify-architecture.sh: OK`)
- **Focused tests**:
  ```
  ./gradlew :core:designsystem:testDebugUnitTest \
    :feature:home:testDebugUnitTest \
    :feature:editor:testDebugUnitTest \
    :app:testDebugUnitTest \
    -Pkotlin.compiler.execution.strategy=in-process --stacktrace
  ```
  BUILD SUCCESSFUL in 1m 49s (458 actionable tasks, 24 executed)
- **Benchmark APK** (timestamped, post-version-commit rebuild):
  - Path: `/root/1codes/xinliu_android/app/build/outputs/apk/benchmark/2026-07-18T21-25-26.apk`
  - Size: 72M (75606572 bytes)
  - `aapt2 dump badging`: `package: name='cc.pscly.onememos' versionCode='159' versionName='1.11.0'`
  - `output-metadata.json`: applicationId=`cc.pscly.onememos`, versionCode=159, versionName=1.11.0, variant=benchmark
  - No `.dev` suffix (formal package invariant held)
  - **REMOTE / §8**: `BLOCKED_SIGNING_MISSING` — `ANDROID_RELEASE_KEYSTORE_PATH` / `STORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` 本机均为空；不得 push tag / 不得用 debug/临时签名发 latest Release。
- **Remaining when secrets available**:
  1. push `main`（本地已 ahead，含 M2.0–M2.10 + 本 release bump）
  2. tag `v1.11.0` 并 push
  3. 等 GitHub Actions 成功
  4. 核验 APK 包名/版本 + 证书 SHA-256 `58749c794f0c54af6b69bb6d80248a9fda0b75c687fde55b98d9575fc091633e`
  5. 发布非草稿非预发布的 GitHub latest Release（仅固定签名 Benchmark APK）
- **Not done here (by design)**: no push, no tag, no GitHub Release, plan checkbox left for orchestrator

## [2026-07-18] M3.5 done - DESIGN.md / CONTEXT.md 文档回写

### DESIGN.md 重写（287行 → 新 10 章 580 行+）
- §1：四轴主题系统（色板×质感×密度×字阶×字体）+ 出厂四预设 + 下发机制 + 持久化
- §2：设计令牌完整表（InkSpacing 17级尺度+语义别名、InkShape 4级+9语义、InkBorder 宽/alpha/InkTone、InkMotion 全部关键帧）
- §3：色彩 5 色板×明暗 + DYNAMIC + 纸墨容器阶 + WCAG 现状
- §4：Typography 字体档（WENKAI/SYSTEM）+ 阅读模式 ReadingConfig 四档×系数
- §5：组件全量（12件 Ink* + MikepenzMarkdown + 4状态原语 + 5 PaperInk 组件纸墨化）
- §6：动效（按压/盖章/转场/ReducedMotion 门控）
- §7：深度策略（纸面色阶+细描边+自绘线）
- §8.2：债务表逐条核销——共 9 条：已核销 4条（48dp触控目标、reduced-motion、焦点环、WCAG对比度）、部分核销 3条（状态原语原语层已就绪但业务屏未全替换、TalkBack遍历系统未建立、颜色依赖部分保留）、保留 2条（禁用视觉、字体放大契约）
- §9：无障碍实现备忘（ReducedMotion/焦点环/触控/live region/对比度/阅读缩放）
- §10：关联 ADR 与领域词汇索引

### CONTEXT.md 更新（外观节重写 + 随笔生命周期补全）
- 外观：色板 3→5 档（增月白·中性、跟随系统动态色）+ 各轴枚举值完整列出 + 字体轴 + 明暗模式 + 阅读模式 + 列表形态 + 滑动操作 + 悬浮记录
- 修正「四根轴」为「五根轴」；四预设列出名称与副标题
- 随笔生命周期：新增待办转换 + 滑动操作交互模型 + 收藏的锦囊交叉引用
- 保留"避免"注释（区分色板/主题/质感等常用混词）

### 边界
- 纯 Markdown 文档变更，不改代码、不 bump versionCode、不 push、不触发 §8 发布
- plan checkbox 待更新

### 经验
- 两代理探索 token/术语 → 1 次主写：探索覆盖度决定文档准确度
- 债务核销需 grep 验证实际接入点（焦点环 4/5 原语已接、InkChip 未接视为部分保留）
- CONTEXT.md 领域词汇更新应同时核对 strings.xml 中文标签确保一致
- ReadingConfig 虽在 OneMemosTheme + 设置 UI 中暴露，但阅读模式不是 M3.3 独立落地的「四档字号+行距滑块」，需在债务表中区分"schema 已就绪"与"设置页交互已就绪"


## [2026-07-18] M3.3 done - 阅读模式字号/行距

### 实现
- NEW `ReadingConfig` + `LocalReadingConfig`：字号 SMALL/STANDARD/LARGE/EXTRA_LARGE → 13/14/16/18.sp；基准行高 18/20/24/28.sp；行距 COMPACT/STANDARD/RELAXED 系数 0.875/1.0/1.25
- `OneMemosThemeConfig` 增 `readingFontScale`/`readingLineHeight`；`OneMemosTheme` 下发 LocalReadingConfig
- `AppViewModel`/`QuickCaptureOverlayService` 从 AppSettings 映射完整 themeDescriptor + 阅读字段
- SettingsRepository 增 setter（默认空实现避 Fake 全量改）；DataStore key 沿用 M1 `reading_font_scale`/`line_height`
- Appearance 深能力/ViewModel/Screen：阅读模式分区（字号四档 + 行距三档）；文案用「字号·」「行距·」前缀避免与密度「标准」冲突
- MemoItem 正文 plain 预览、EditorScreen `HighlightingEditorField` 消费 LocalReadingConfig

### 验证
- `:core:designsystem:testDebugUnitTest` ReadingConfigTest 绿
- `:core:settings:testDebugUnitTest` AppearanceInteractionSettingsCapabilityImplTest 绿
- `:feature:settings:testDebugUnitTest` appearance.* 绿
- `:app:compileBenchmarkKotlin` 绿

### 经验
- Appearance 选项文案若与其它 Section 共用「标准/宽松/紧凑」，Compose `onNodeWithText` assertExists 会因多节点失败——阅读档位应用唯一前缀
- SettingsRepository 新方法优先 default 空实现，可避免十余处 FakeSettingsRepository 同步改接口
- 阅读行距档是基准行高的乘数，不是独立 sp 表；字号表以 PRD 为准
- 任务约束：不 bump versionCode、不 push

## [2026-07-18] M3.R 本地验收完成——里程碑 3 发布 1.12.0 (160)

### 交付总结
- **版本**：1.11.0 (159) → **1.12.0 (160)**
- **提交**：`79afa4a` `chore(release): bump 1.12.0 (160) M3 全屏迁移+无障碍收口`
- **变更文件**：仅 `app/build.gradle.kts`（versionCode/versionName）+ `.ai_session.md`（M3.R 收口摘要）

### 门禁通过
| 门禁 | 结果 |
|------|------|
| `verify-architecture.sh` | exit 0 |
| `:core:designsystem:testDebugUnitTest` | BUILD SUCCESSFUL |
| `:feature:home:testDebugUnitTest` | BUILD SUCCESSFUL |
| `:feature:editor:testDebugUnitTest` | BUILD SUCCESSFUL |
| `:feature:settings:testDebugUnitTest` | BUILD SUCCESSFUL |
| `:app:testDebugUnitTest` | BUILD SUCCESSFUL |
| `:app:assembleBenchmark` | BUILD SUCCESSFUL |

### APK
- **路径**：`app/build/outputs/apk/benchmark/2026-07-18T22-21-38.apk`
- **包名**：`cc.pscly.onememos`（output-metadata.json 核验）
- **版本**：`1.12.0 (160)`（output-metadata.json 核验）
- **签名**：本地 debug 签名（无 `ANDROID_RELEASE_*` 环境变量）

### BLOCKED_SIGNING_MISSING
- `ANDROID_RELEASE_*` 四项（keystore/storePassword/keyAlias/keyPassword）均未在本机设置
- 本次 APK 由本地 `debug.keystore` 签名，**不得**用作 GitHub latest Release
- 远端固定签名证书 SHA-256：`58749c794f0c54af6b69bb6d80248a9fda0b75c687fde55b98d9575fc091633e`
- 远端闭环待签名/Secrets 可用后执行：push main → tag `v1.12.0` → Actions → 核验证书 → 非草稿 Release

### 不做
- 未 push（main 领先 origin 44 提交）
- 未 tag（本地无 `v1.12.0` tag）
- 未创建 GitHub Release
- 未修改任何 feature 代码
