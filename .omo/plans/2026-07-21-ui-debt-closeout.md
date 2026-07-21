# 2026-07-21-ui-debt-closeout - Work Plan

## TL;DR (For humans)
<!-- Fill this LAST, after the detailed plan below is written, so it summarizes the REAL plan. -->
<!-- Plain English for a non-engineer: NO file paths, NO todo numbers, NO wave/agent/tool names. -->

**What you'll get:** 首页已有随笔到编辑器的纸墨共享转场、统一的顶栏/底单/提示样式、真正可触达且可朗读的筛选标签，以及可复跑的明暗/大字体/性能证据。全部通过后发布固定签名的 1.16.0 稳定版。

**Why this approach:** 现有纸墨主题和导航宿主已经完成，剩余问题是业务接线与验收证据，而不是再做一次视觉重构。因此只关闭审计能证明的缺口，并用自动化、真机和正式发布资产三层交叉验证。

**What it will NOT do:** 不发明新一轮审美，不改已确认满意的悬浮速记，不做跨分区或新建随笔共享转场，也不为了“统一”而无差别替换所有系统组件。不会用临时签名或省略正式发布步骤。

**Effort:** Large
**Risk:** High - 涉及 Navigation 3 动画、跨模块 Compose 接线、真机无障碍/性能和不可省略的远端稳定版发布。
**Decisions I made for you:** 我把目标解释为“关闭真实债务”而非继续扩张设计；共享转场只覆盖首页活跃分区的已有随笔；只迁移无损匹配现有原语的状态；Chip 使用 48dp 触控区但保留紧凑视觉；目标版本定为 1.16.0 (168)。这些在发布前均可否决或调整。

Your next move: 高精度评审通过后，在独立执行会话运行 `$start-work .omo/plans/2026-07-21-ui-debt-closeout.md`。本计划不在当前会话实现产品代码。

---

> TL;DR (machine): Large/high-risk；18 个任务关闭 shared bounds、PaperInk 接线、状态/a11y、Roborazzi/宏基准，并以固定签名 1.16.0 (168) 完成 main→Tag→Actions→latest Release 闭环。

## Scope
### Must have
- `core:navigation` 提供唯一 memo shared-bounds key、CompositionLocal 和 Modifier helper；`AppNavigationHost` 注入当前 `SharedTransitionLayout` scope。
- Home 活跃分区已有 memo 与 `EditorKey(uuid!=null)` 配对；新建、归档、跨顶层分区、进程恢复和 Reduced Motion 不参与。
- 8 个业务 `TopAppBar`、5 个非悬浮层 `ModalBottomSheet`、Home `SnackbarHost` 显式接入既有 `PaperInk*` 包装。
- Editor 的可重试同步失败态使用现有 `InkRetryBanner`；Collections/Profile/Todo 空态保持现有卡片、按钮和布局。
- 可交互 `TagChip` 与全部 `InkChip` 至少 48dp 触控区、紧凑视觉面、明确 selected/stateDescription、既有焦点和禁用语义。
- Roborazzi 明暗/大字体金图、源码接线契约、真机转场/TalkBack/最大字体和 fail-fast Home 宏基准均有新鲜证据。
- `DESIGN.md`、ADR 0012、M3.2 QA 记录和 `.ai_session.md` 与实际实现/验收一致。
- `1.15.2 (167)` 递增为 `1.16.0 (168)`，生成日期时间命名、固定签名的 Benchmark APK，并完成正式 latest Release 深度核验。
### Must NOT have (guardrails, anti-slop, scope boundaries)
- 不改 `app/src/main/java/cc/pscly/onememos/overlay/QuickCaptureOverlayService.kt`，不迁移其中的 raw Dialog/BottomSheet，不改变 1.15.2 悬浮窗口几何、IME 或输入框。
- 不改色板、字体、纹理、信息架构、业务文案策略或数据模型；不引入 M7、新视觉语言、渐变、玻璃、模糊、发光或阴影。
- 不引入 Navigation 2 或社区 `SharedEntryInSceneNavEntryDecorator` / `localNavSharedTransitionScope`。允许且仅允许本仓 nullable `LocalMemoSharedTransitionScope` 承载现有 `SharedTransitionLayout` scope；不得再发明第二套 local/decorator、shared key 或 Reduced Motion 开关。
- 不让归档列表、新建随笔、顶层分区切换或进程恢复参与 shared bounds；不得把无配对目标强行动画化。
- 不全量替换 `AlertDialog`；其默认形状/颜色继续走 `OneMemosTheme` 全局路径。不得扩展状态原语只为容纳 Todo 双动作或紧凑弹层。
- 不用源码 grep、自报日志或空数据 best-effort 宏基准单独宣称通过；不降低/删除失败测试，不使用 suppression 绕过类型或 Lint。
- 不清理、覆盖或提交既有 `.omo/run-continuation/**`、`.tmp_vqa_20260720/`、无关 research、密钥、APK 或 build 目录。
- 不使用 debug/临时/漂移签名作为正式资产，不跳过 PushMain、annotated Tag、Tag Actions、latest Release、独立下载核验或 Cleanup。

## Verification strategy
> Zero human intervention - all verification is agent-executed.
- Test decision: TDD for shared key/接线契约、Chip 语义与宏基准 fail-fast；tests-after for等价 UI 包装和 Editor 重试横幅；JUnit4 + Robolectric Compose UI Test + Roborazzi + AndroidX Macrobenchmark + ADB/UIAutomator。
- Evidence: `<attemptDir>/task-<N>-2026-07-21-ui-debt-closeout.*`（`attemptDir` 来自 `omo ulw-loop status --json` 的 `currentAttemptDir`；非 ulw-loop 使用 `.omo/evidence/`）。每个命令保存 stdout/stderr/exit code；图片、screenrecord、UI XML、logcat、Benchmark JSON、release JSON 均放同一任务子目录。
- 失败原则：签名、`pwsh`、解锁且授权的 ADB 设备、TalkBack 服务或远端授权任一缺失时标记 `[blocked]` 并停止发布；不得把 SKIP、空日志或组件测试替代真机 PASS。
- 工作区原则：执行开始记录 `git status --short --branch` 和基线 SHA；优先隔离 worktree，精确 staging 每个任务文件，绝不触碰已有脏文件。

## Execution strategy
### Parallel execution waves
> Target 5-8 todos per wave. Fewer than 3 (except the final) means you under-split.
- Wave 1（底座，最多 3 并行）：Todo 1、2；Todo 3 等 Todo 2 后执行，集中录制 designsystem 金图避免 Roborazzi 并发写目录。
- Wave 2（业务接线，4 并行）：Todo 4、5、6、7，文件集合互不重叠。
- Wave 3（配对与证据，最多 4 并行）：Todo 8；随后 Todo 9、10、11 可并行，Todo 12 汇总文档。
- Wave 4（本地/设备发布串行）：Todo 13 → 14 → 15 → 16。Todo 16 末尾等待用户明确授权远端副作用。
- Wave 5（远端闭环串行）：Todo 17 → 18；任何阶段失败都修复并从状态机允许的阶段重跑，不重建或移动已成功 Tag。

### Dependency matrix
| Todo | Depends on | Blocks | Can parallelize with |
| --- | --- | --- | --- |
| 1 | - | 8 | 2 |
| 2 | - | 3, 10 | 1 |
| 3 | 2 | 13 | - |
| 4 | - | 8, 9, 10 | 5, 6, 7 |
| 5 | - | 8, 9 | 4, 6, 7 |
| 6 | - | 9 | 4, 5, 7 |
| 7 | - | 9 | 4, 5, 6 |
| 8 | 1, 4, 5 | 10, 11, 12 | - |
| 9 | 4, 5, 6, 7 | 13 | 10, 11 |
| 10 | 2, 3, 4, 8 | 13 | 9, 11 |
| 11 | 8 | 13, 15 | 9, 10 |
| 12 | 3, 8, 9, 10, 11 | 13 | - |
| 13 | 3, 9, 10, 11, 12 | 14 | - |
| 14 | 13 | 15 | - |
| 15 | 14 | 16 | - |
| 16 | 15 | 17 | - |
| 17 | 16 + 用户远端授权 | 18 | - |
| 18 | 17 | Final wave | - |

## Todos
> Implementation + Test = ONE todo. Never separate.
<!-- APPEND TASK BATCHES BELOW THIS LINE WITH edit/apply_patch - never rewrite the headers above. -->
- [ ] 1. 建立唯一 memo shared-bounds 底座并接入导航宿主
  What to do / Must NOT do: 先在 `gradle/libs.versions.toml` 增加 `androidx-compose-animation = { module = "androidx.compose.animation:animation" }`（版本只由 BOM 管理），再给 `core/navigation` 增加 `implementation(libs.androidx.compose.ui)`、`implementation(libs.androidx.compose.animation)`、`implementation(libs.androidx.navigation3.ui)`。新建 `MemoSharedTransition.kt`，API 形状必须逐项等价于：

    ```kotlin
    @OptIn(ExperimentalSharedTransitionApi::class)
    val LocalMemoSharedTransitionScope =
        staticCompositionLocalOf<SharedTransitionScope?> { null }

    fun memoSharedContentKey(uuid: String?): String? =
        uuid?.takeIf { it.isNotBlank() }?.let { "memo/$it" }

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    fun Modifier.memoSharedBounds(uuid: String?): Modifier {
        val scope = LocalMemoSharedTransitionScope.current
        val key = memoSharedContentKey(uuid)
        if (scope == null || key == null) return this
        return with(scope) {
            this@memoSharedBounds.sharedBounds(
                sharedContentState = rememberSharedContentState(key = key),
                animatedVisibilityScope = LocalNavAnimatedContentScope.current,
            )
        }
    }
    ```

    `AppNavigationHost` 在现有 `SharedTransitionLayout` 内用 `CompositionLocalProvider(LocalMemoSharedTransitionScope provides (if (reducedMotion) null else this))` 包裹现有 `NavDisplay`，并继续给 `NavDisplay.sharedTransitionScope` 同一个 nullable scope；transition/pop/predictive specs 不变。null scope/key 必须在读取 `LocalNavAnimatedContentScope.current` 前 early return。key unit 只测纯函数，不伪造 `SharedTransitionScope`；host Reduced Motion 由 app 源码契约测试。不得把 scope 放进导航快照、序列化 key 或 feature API，不得钉死 Animation 版本、使用无 receiver 的 shared API 或新增第二套 Reduced Motion 状态。
  Parallelization: Wave 1 | Blocked by: none | Blocks: 8
  References (executor has NO interview context - be exhaustive): `app/src/main/java/cc/pscly/onememos/navigation/AppNavigationHost.kt:43-57,165-189`; `core/navigation/build.gradle.kts:32-40`; `core/navigation/src/main/java/cc/pscly/onememos/navigation/NavKeys.kt:41-45`; `docs/adr/0012-navigation-transition-strategy.md:22-33`; 官方 API 调研结论见 draft Findings。
  Acceptance criteria (agent-executable): `./gradlew :core:navigation:testDebugUnitTest :core:navigation:compileDebugKotlin :app:testDebugUnitTest --tests '*MemoSharedTransition*' --stacktrace --rerun-tasks` exit 0；navigation unit 断言不同 UUID key 不碰撞、null/blank 不产生 key；app 契约断言 host 只提供 `if (reducedMotion) null else this` 且无第二套 reduced 开关；helper/local 均有 `ExperimentalSharedTransitionApi` opt-in；`git diff --check` 无输出。
  QA scenarios (name the exact tool + invocation): Happy：JUnit + Kotlin compile + app source contract 证明稳定 key、receiver API 与 host 门控，保存 `<attemptDir>/task-1-2026-07-21-ui-debt-closeout.log`。Failure：契约夹具把 key 改成常量、移除 `with(scope)`/OptIn 或把 reduced branch 改成非 null 时必须 RED，恢复后再绿，保存 `.red-green.log`。
  Commit: Y | `feat(navigation): add memo shared bounds foundation`

- [ ] 2. 恢复 Chip 48dp 触控与非颜色选中语义
  What to do / Must NOT do: TDD 修改 `TagChip`/`InkChip`：外层交互/语义容器至少 `InkSpacing.TouchTargetMin`，内部 `Surface` 继续按原 padding/shape 绘制紧凑视觉；可点击 TagChip 与全部 InkChip 暴露 `SemanticsProperties.Selected` 和准确 `stateDescription`，disabled InkChip 保留 disabled 且不可点击，焦点边框仍只在可交互且 focused 时出现。静态未选 TagChip 不伪装按钮或朗读“未选中”；静态已选或可点击 TagChip 才进入选择语义。删除当前“产品决策不套用 48dp”反向注释/测试，替换为 48dp 触控、紧凑视觉双层契约。不得把视觉色块撑到 48dp，不改变色板/哈希色/文字/padding/点击回调次数。
  Parallelization: Wave 1 | Blocked by: none | Blocks: 3, 10
  References (executor has NO interview context - be exhaustive): `core/designsystem/.../component/TagChip.kt:30-127`; `InkChip.kt:33-105`; `SettingsPrimitivesAccessibilityTest.kt:45-194,221-287`; `docs/qa/m3-2-paper-ink-accessibility.md:20-31`; `DESIGN.md:408-425`。
  Acceptance criteria (agent-executable): `./gradlew :core:designsystem:testDebugUnitTest --tests '*SettingsPrimitivesAccessibilityTest' --stacktrace --rerun-tasks` exit 0；enabled/disabled InkChip 与 clickable TagChip 的 merged semantics width/height 均 ≥48dp；selected true/false 可机器读取；静态 TagChip 无 click action；enabled/disabled 回调计数正确。
  QA scenarios (name the exact tool + invocation): Happy：Robolectric Compose rule 测 48dp、selected、focus、disabled、click count，证据 `<attemptDir>/task-2-2026-07-21-ui-debt-closeout.log`。Failure：测试先锁定当前 clickable TagChip <48dp 且 InkChip 缺 selected 的 RED，再实现 GREEN，证据 `.red-green.log`。
  Commit: Y | `fix(designsystem): restore accessible chip targets`

- [ ] 3. 扩充 PaperInk/Chip 明暗与最大字体 Roborazzi 金图
  What to do / Must NOT do: 在 designsystem 截图测试中增加 light、dark、fontScale=2.0 三矩阵，真实渲染 `PaperInkTopAppBar`、Snackbar、BottomSheet 表面/drag handle、Dialog、clickable/static TagChip、selected/unselected/disabled InkChip；将 `PaperInkAlertDialog` 而非 raw AlertDialog 用作显式包装路径，同时保留一条全局默认 AlertDialog 令牌断言。先运行 record 生成确定性 PNG，再 verify。不得在测试中覆盖生产颜色/shape 来制造通过，不接受只有 smoke 图或未入库的本地截图。
  Parallelization: Wave 1 | Blocked by: 2 | Blocks: 13
  References (executor has NO interview context - be exhaustive): `core/designsystem/src/main/java/.../theme/PaperInkComponents.kt:26-182`; `core/designsystem/src/test/.../PaperInkComponentsScreenshotTest.kt:43-199`; `InkStatePrimitivesTest.kt:38-204`; `core/designsystem/build.gradle.kts:26-40,73-83`; 当前 `core/designsystem/src/test/screenshots/` 仅有 smoke 金图。
  Acceptance criteria (agent-executable): `./gradlew :core:designsystem:recordRoborazziDebug --stacktrace` 后新增/更新的命名金图可追溯；紧接 `./gradlew :core:designsystem:verifyRoborazziDebug --stacktrace --rerun-tasks` exit 0；PNG 非空、UTF-8 源文件无 BOM、`git diff --check` 清洁。
  QA scenarios (name the exact tool + invocation): Happy：Roborazzi 三矩阵 verify，输出 `<attemptDir>/task-3-2026-07-21-ui-debt-closeout.log`，并用 `look_at`/像素测量审查视觉 Surface，保存 `.visual-review.md`。硬失败条件：filled/bordered chip 可见面达到或超过约 40dp（xxhdpi 金图约 120px）或呈 48dp 巨型胶囊；外层 merged touch bounds 仍须 ≥48dp。Failure：临时改变一个期望色/shape 后 verify 必须检测像素差，恢复金图后再绿，保存 `.negative.log`。
  Commit: Y | `test(designsystem): expand paper ink visual matrix`

- [ ] 4. 接通 Home 顶栏、Snackbar 与三处底单纸墨包装
  What to do / Must NOT do: `HomeScreen` 的 `TopAppBar`→`PaperInkTopAppBar`、`SnackbarHost`→`PaperInkSnackbarHost`、筛选/操作 `ModalBottomSheet`→`PaperInkModalBottomSheet`；`AddToCollectionsDialog` 与 Batch 同样迁移。只换组件名/import，逐参数保持 onDismiss、sheet state、回调、布局、返回/忙碌保护和文案。不得触碰 MemoItem shared bounds（Todo 8）、AlertDialog、业务状态、悬浮服务或任何视觉令牌。
  Parallelization: Wave 2 | Blocked by: none | Blocks: 8, 9, 10
  References (executor has NO interview context - be exhaustive): `feature/home/.../HomeScreen.kt:334-337,557`; `AddToCollectionsDialog.kt:45-99`; `AddToCollectionsBatchDialog.kt:45-99`; `PaperInkComponents.kt:85-182`。`HomeScreen.kt:670-707` 是 AlertDialog 排除区，不得迁移。
  Acceptance criteria (agent-executable): `./gradlew :feature:home:compileDebugKotlin :feature:home:testDebugUnitTest --stacktrace --rerun-tasks` exit 0；目标三文件无 raw `TopAppBar(`/`SnackbarHost(`/`ModalBottomSheet(` 调用；回调参数数量/名称与改前一致；`QuickCaptureOverlayService.kt` 不在 diff。
  QA scenarios (name the exact tool + invocation): Happy：Gradle 编译/单测 + Compose source contract，证据 `<attemptDir>/task-4-2026-07-21-ui-debt-closeout.log`。Failure：契约测试夹具把 Home 任一 wrapper 改回 raw 名称时必须失败，恢复后通过，证据 `.negative.log`。
  Commit: Y | `refactor(home): wire paper ink system surfaces`

- [ ] 5. 接通 Editor/Collections/Profile 顶栏并迁移 Editor 重试横幅
  What to do / Must NOT do: 三屏 `TopAppBar` 使用 `PaperInkTopAppBar`。仅将 Editor 的 `SyncStatus.FAILED + lastSyncError` Row 改为 `InkRetryBanner(message = "同步失败：${uiState.lastSyncError}", retryLabel = "重试同步", onRetry = viewModel::retrySync)`。保留 Editor `loadError` early return（无 retry）、只读提示和所有 AlertDialog；Collections `itemsToRender.isEmpty()` 的 InkCard + 多行文案 + busy-aware SealButton 原样保留；Profile `sections.isEmpty()` 的安静 InkCard 原样保留。不得迁移这两个空态、扩展状态原语或删减/换型动作。
  Parallelization: Wave 2 | Blocked by: none | Blocks: 8, 9
  References (executor has NO interview context - be exhaustive): `feature/editor/.../EditorScreen.kt:247-283,325-420`; `feature/collections/.../CollectionsScreen.kt:332,540-557`; `feature/profile/.../ProfileScreen.kt:89,167-176`; `InkStatePrimitives.kt:115-166,223-267`。
  Acceptance criteria (agent-executable): `./gradlew :feature:editor:compileDebugKotlin :feature:collections:compileDebugKotlin :feature:collections:testDebugUnitTest :feature:profile:compileDebugKotlin :feature:profile:testDebugUnitTest --stacktrace --rerun-tasks` exit 0；Editor retry 仍只调用一次 `retrySync` 并有 polite live region；Collections/Profile 空态代码结构与动作类型相对基线不变；三屏无 raw TopAppBar。
  QA scenarios (name the exact tool + invocation): Happy：模块编译/测试及 Editor 状态语义契约，证据 `<attemptDir>/task-5-2026-07-21-ui-debt-closeout.log`。Failure：注入 failed sync 后缺少 polite live region/重试动作，或契约发现 Collections/Profile 空态被替换时必须失败，恢复后通过，证据 `.negative.log`。
  Commit: Y | `refactor(features): align shell and state primitives`

- [ ] 6. 接通 Todo/Auth/Welcome/ShareCard 四个业务顶栏
  What to do / Must NOT do: 四文件只把 raw `TopAppBar` 替换为 `PaperInkTopAppBar`，保持 title/navigation/actions、menu、同步、分享、返回、windowInsets/scrollBehavior 参数原样。Todo 的双动作空态和所有 AlertDialog 保持不动。不得顺手迁移按钮、CPI、状态或文案。
  Parallelization: Wave 2 | Blocked by: none | Blocks: 9
  References (executor has NO interview context - be exhaustive): `feature/todo/.../TodoScreen.kt:180-362`; `feature/auth/.../AuthScreen.kt:69`; `feature/welcome/.../WelcomeScreen.kt:37`; `feature/sharecard/.../ShareCardScreen.kt:85`; `PaperInkComponents.kt:85-105`。
  Acceptance criteria (agent-executable): `./gradlew :feature:todo:compileDebugKotlin :feature:todo:testDebugUnitTest :feature:auth:compileDebugKotlin :feature:welcome:compileDebugKotlin :feature:sharecard:compileDebugKotlin --stacktrace --rerun-tasks` exit 0；四文件无 raw TopAppBar；Todo 空态仍有“新建清单”“新增任务”两个动作。
  QA scenarios (name the exact tool + invocation): Happy：Gradle 矩阵与源码契约，证据 `<attemptDir>/task-6-2026-07-21-ui-debt-closeout.log`。Failure：删除 Todo 任一空态动作时契约测试必须失败；恢复后通过，证据 `.negative.log`。
  Commit: Y | `refactor(features): apply paper ink top bars`

- [ ] 7. 接通 QuickCapture 历史与 TagFilter 两个非 overlay 底单
  What to do / Must NOT do: `QuickCaptureScreen.QuickCaptureHistoryBottomSheet` 与 designsystem `TagFilterBottomSheet` 使用 `PaperInkModalBottomSheet`，保持所有 state/onDismiss/content/padding/筛选逻辑。`TagFilterBottomSheet` 的既有 `modifier` 当前只属于内层 `FlowRow`，迁移后仍必须挂在该 FlowRow，禁止转挂到 `PaperInkModalBottomSheet` 根。明确不改 `QuickCaptureOverlayService.kt:1513` 的 raw ModalBottomSheet，也不改 QuickCapture overwrite AlertDialog。
  Parallelization: Wave 2 | Blocked by: none | Blocks: 9
  References (executor has NO interview context - be exhaustive): `feature/quickcapture/.../QuickCaptureScreen.kt:330-389`; `core/designsystem/.../component/TagFilterBottomSheet.kt:29-100`; `app/.../overlay/QuickCaptureOverlayService.kt:1351,1513`（只读排除基线）。
  Acceptance criteria (agent-executable): `./gradlew :core:designsystem:testDebugUnitTest :feature:quickcapture:compileDebugKotlin --stacktrace --rerun-tasks` exit 0；两个目标无 raw ModalBottomSheet；`git diff -- app/src/main/java/cc/pscly/onememos/overlay/QuickCaptureOverlayService.kt` 无输出。
  QA scenarios (name the exact tool + invocation): Happy：编译 + TagFilter 行为测试（exclude 强制 OR、clear/apply/dismiss 不变）+ FlowRow modifier 归属契约，证据 `<attemptDir>/task-7-2026-07-21-ui-debt-closeout.log`。Failure：若 modifier 被转挂 sheet 根、回调/sheet state 漂移或误改 overlay 文件，契约立即失败，证据 `.negative.log`。
  Commit: Y | `refactor(quickcapture): use paper ink bottom sheets`

- [ ] 8. 配对 Home 已有随笔与 Editor 内容根容器 shared bounds
  What to do / Must NOT do: 为 `MemoItem` 增加默认空 `modifier`，`InkCard` 的 modifier 顺序写死为 `modifier.then(if (selected) Modifier.border(selectedBorder, cardShape) else Modifier).testTag("home_memo_item_${memo.uuid}")`，onClick/onLongClick/contentDescription 不变。`GroupedItemContent` 仅在 `mode == HomeScreenMode.ACTIVE` 时传 `Modifier.memoSharedBounds(memo.uuid)`，Archived 传 `Modifier` 或 null key。`EditorEntryContributor` 把 `EditorKey.uuid` 直接传入新增的 `EditorScreen(memoUuid)`；Editor 内容根写为 `Box(Modifier.fillMaxSize().memoSharedBounds(memoUuid).padding(...))`，不含 TopAppBar且不能等待 ViewModel `uiState.uuid`。新增接线契约测试锁定 source/target 同 key、new/archived/null 禁用、Reduced Motion 由 Todo 1 local 统一控制。不得改变 navigator、ViewModel bind、栈状态、页面 transition specs 或业务点击。
  Parallelization: Wave 3 | Blocked by: 1, 4, 5 | Blocks: 10, 11, 12
  References (executor has NO interview context - be exhaustive): `HomeEntryContributor.kt:20-42`; `HomeScreen.kt:824,892,982-1073`; `MemoItem.kt:59-116`; `EditorEntryContributor.kt:16-32`; `EditorScreen.kt:110-115,325-390`; ADR 0012 `:22-33`。
  Acceptance criteria (agent-executable): `./gradlew :core:navigation:testDebugUnitTest :feature:home:compileDebugKotlin :feature:home:testDebugUnitTest :feature:editor:compileDebugKotlin :app:testDebugUnitTest --tests '*MemoSharedTransitionWiringTest' --stacktrace --rerun-tasks` exit 0；契约断言 ACTIVE existing source/Editor non-null target 恰各一处、Archived/new/null 不配对、Editor 使用 route UUID。
  QA scenarios (name the exact tool + invocation): Happy：编译/契约测试，证据 `<attemptDir>/task-8-2026-07-21-ui-debt-closeout.log`。Failure：把 Archived 或 `EditorKey(null)` 接入、或改用异步 `uiState.uuid` 时测试 RED；恢复后 GREEN，证据 `.red-green.log`。
  Commit: Y | `feat(navigation): connect memo editor shared bounds`

- [ ] 9. 建立 PaperInk 生产接线的全仓契约守护
  What to do / Must NOT do: 在 app 源码契约测试中只枚举以下三个生产根：`app/src/main`、每个 `feature/*/src/main`、`core/designsystem/src/main`；显式排除 `**/build/**` 与所有 `src/test`/`src/androidTest`。用确定性 Kotlin 词法清洗状态机把 `//` 行注释、支持嵌套的 `/* */`、转义普通字符串、`"""` raw string 和字符字面量的非换行字符替换为空格（保留换行以保持行号）；再匹配 `\bTopAppBar\s*\(`、`\bSnackbarHost\s*\(`、`\bModalBottomSheet\s*\(`。三种 raw 模式都整文件豁免 `core/designsystem/src/main/java/cc/pscly/onememos/ui/theme/PaperInkComponents.kt`（包装内部必须调用原生组件）；raw ModalBottomSheet 只额外豁免 `app/src/main/java/cc/pscly/onememos/overlay/QuickCaptureOverlayService.kt`。8/5/1 目标 wrapper 调用全部存在；raw AlertDialog 不作为失败。输出 offending 仓库相对 `path:line`。不得扫描其他 core 模块、用只检查 import 或无法区分注释/字符串的脆弱测试，也不得把 overlay 服务纳入迁移。
  Parallelization: Wave 3 | Blocked by: 4, 5, 6, 7 | Blocks: 13
  References (executor has NO interview context - be exhaustive): `app/src/test/.../architecture/FinalModuleBoundariesTest.kt:53-57,332-386` 的 projectDir/source contract 模式；`app/build.gradle.kts:205-215`; 本计划 Tasks 4-7 的精确目标清单。
  Acceptance criteria (agent-executable): `./gradlew :app:testDebugUnitTest --tests '*PaperInkProductionWiringTest' --stacktrace --rerun-tasks` exit 0；测试报告扫描根恰为 app/main、全部 feature/main、core/designsystem/main，目标计数为 8 TopAppBar、5 BottomSheet、1 SnackbarHost；`PaperInkComponents.kt` 是三模式共同豁免，`QuickCaptureOverlayService.kt` 是 ModalBottomSheet 唯一额外豁免。
  QA scenarios (name the exact tool + invocation): Happy：全仓契约输出 `<attemptDir>/task-9-2026-07-21-ui-debt-closeout.log`。Failure：fixture/临时副本把任一 wrapper 改回 raw 时报告精确文件行；恢复后通过，证据 `.negative.log`。
  Commit: Y | `test(architecture): guard paper ink production wiring`

- [ ] 10. 为 Home MemoItem 增加明暗/最大字体 Roborazzi 回归
  What to do / Must NOT do: 在 `feature/home` 接入与 designsystem 相同版本的 Roborazzi plugin/testOptions/deps/outputDir，新增同包 `MemoItemScreenshotTest`，使用稳定 sample Memo 捕获 light、dark、fontScale=2.0；至少包含正文、标签、附件计数、失败同步状态和更多操作，验证点击标签的 48dp 触控不扩大视觉色块、最大字体无横向裁切。scope 缺省 null 时 shared helper 必须 no-op。不得引入生产 fixture 后门、Hilt/ViewModel/数据库，不截取动态时间或网络图片。
  Parallelization: Wave 3 | Blocked by: 2, 3, 4, 8 | Blocks: 13
  References (executor has NO interview context - be exhaustive): `feature/home/build.gradle.kts:1-66`; `core/designsystem/build.gradle.kts:3-40,73-83`; `MemoItem.kt:59-315`; `MemoItemTalkBackTest.kt:60-80` sample 构造模式；`PaperInkComponentsScreenshotTest.kt:51-85` Roborazzi 模式。
  Acceptance criteria (agent-executable): `./gradlew :feature:home:recordRoborazziDebug --stacktrace` 后紧接 `./gradlew :feature:home:verifyRoborazziDebug --stacktrace --rerun-tasks` exit 0；三张命名金图存在且非空；普通 `:feature:home:testDebugUnitTest` 也通过。
  QA scenarios (name the exact tool + invocation): Happy：Roborazzi + `look_at`/像素测量三图审查，证据 `<attemptDir>/task-10-2026-07-21-ui-debt-closeout.log` 和 `.visual-review.md`。硬失败条件：Chip filled/bordered 可见面 ≥40dp（xxhdpi 约 120px）、呈 48dp 巨型胶囊，或 MemoItem 因触控外框导致不可接受的额外折行/裁切；语义外框仍须 ≥48dp。Failure：临时把最大字体标签挤出或改变纸墨色后 verify 必须报像素差，恢复后通过，证据 `.negative.log`。
  Commit: Y | `test(home): add memo visual regression matrix`

- [ ] 11. 将 HomeScrollBenchmark 从 best-effort 改为 fail-fast 真实链路
  What to do / Must NOT do: 保持冷启动、6 次滑动、10 iterations 和既有 metrics；在 `HomeScreen.kt` 对 `HomeScreen` 加 `@OptIn(ExperimentalComposeUiApi::class)`（或同文件 file-level 等价 opt-in），并将现有唯一最外层 `Scaffold` 明确改为 `Scaffold(modifier = Modifier.semantics { testTagsAsResourceId = true }, ...)`。全文件只允许这一处 `testTagsAsResourceId = true`。UiAutomator 用 `By.res(Pattern.compile("home_memo_item_.*"))` 查找当前可见 Memo，找不到即以“无 memo fixture”明确 fail。点击节点后 `Until.hasObject(By.desc("返回"))` 必须为 true；返回后 `Until.hasObject(By.desc("同步"))` 必须为 true。抽取带超时错误信息的 helper，删除“空数据也不影响”注释。不得依赖动态完整 contentDescription、用坐标点击或 desc 模糊回退，不得在 measured block 创建数据、吞掉 wait 结果或降低 iterations。
  Parallelization: Wave 3 | Blocked by: 8 | Blocks: 13, 15
  References (executor has NO interview context - be exhaustive): `macrobenchmark/src/main/.../HomeScrollBenchmark.kt:16-54`; `MemoItemTalkBack`/`MemoItem.kt:97-115` 合并 contentDescription；`macrobenchmark/build.gradle.kts:10-49`。
  Acceptance criteria (agent-executable): `./gradlew :feature:home:compileDebugKotlin :macrobenchmark:assembleBenchmark --stacktrace --rerun-tasks` exit 0；`HomeScreen`/文件带 `ExperimentalComposeUiApi` opt-in，且唯一外层 Scaffold 的 modifier 中恰有一处 `testTagsAsResourceId = true`；测试源码以 resource-id regex 查 memo，并对 memo、Editor、Home 三个结果硬断言；无 best-effort、动态整串 desc 或 coordinate fallback。
  QA scenarios (name the exact tool + invocation): Happy：在有固定 memo 的解锁设备运行 `./gradlew :macrobenchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cc.pscly.onememos.macrobenchmark.HomeScrollBenchmark --stacktrace`，保存 `<attemptDir>/task-11-2026-07-21-ui-debt-closeout.log`。Failure：空 fixture/profile 必须在首个 memo 断言处失败且不产出误导 PASS，保存 `.empty-fixture.log`；随后由 agent 通过公开 UI 创建 fixture 再重跑。
  Commit: Y | `test(macrobenchmark): require real memo editor flow`

- [ ] 12. 回写设计、ADR、无障碍与会话事实
  What to do / Must NOT do: 更新 `DESIGN.md`：PaperInk 包装生产接入、仅 Editor 重试横幅迁移且 Collections/Profile/Todo 空态保留、Chip 48dp 外触控/紧凑视觉、shared bounds 已接线；更新 ADR 0012 后果/实现状态，移除“尚未接线”语气但保留降级条件；更新 M3.2 QA 自动化表，纠正旧 48dp 声明与当前测试名，真机项仍标 pending 直到 Todo 15；在 `.ai_session.md` 顶部记录实现提交、测试和“发布待 Prepare”。所有注释/文档中文、UTF-8 无 BOM。不得改历史发布事实或把未执行设备/Release 写成 PASS。
  Parallelization: Wave 3 | Blocked by: 3, 8, 9, 10, 11 | Blocks: 13
  References (executor has NO interview context - be exhaustive): `DESIGN.md:338-383,408-425,436-487`; `docs/adr/0012-navigation-transition-strategy.md`; `docs/qa/m3-2-paper-ink-accessibility.md`; `.ai_session.md:1-27`。
  Acceptance criteria (agent-executable): 文档中的类名/测试命令可在仓库解析；`rg '待真机|SKIPPED_NO_DEVICE' docs/qa/m3-2-paper-ink-accessibility.md` 仅保留 Todo 15 尚未执行项；UTF-8/BOM 检查通过；`git diff --check` 无输出。
  QA scenarios (name the exact tool + invocation): Happy：链接/路径/命令存在性检查输出 `<attemptDir>/task-12-2026-07-21-ui-debt-closeout.log`。Failure：脚本检测任何“Release 完成”但无 release JSON、或真机 PASS 无截图/UI XML 时失败，证据 `.negative.log`。
  Commit: Y | `docs(ui): record paper ink debt closeout`

- [ ] 13. 执行新鲜的聚焦、架构、视觉和全量本地门禁
  What to do / Must NOT do: 在干净实现提交上依次跑诊断、精确测试矩阵、两个 Roborazzi verify、架构脚本和 `./scripts/verify.sh`；记录 Gradle 实际执行任务与 exit code，确认不是 up-to-date 伪通过（聚焦测试使用 `--rerun-tasks`）。扫描 changed Kotlin/XML/MD UTF-8 无 BOM、`git diff --check`、敏感文件和 scope；任何与本改动无关的既有失败单独记录，不顺手修复。不得在门禁失败时进入版本 Prepare。
  Parallelization: Wave 4 | Blocked by: 3, 9, 10, 11, 12 | Blocks: 14
  References (executor has NO interview context - be exhaustive): `scripts/verify.sh:1-92`; `scripts/verify-architecture.sh`; `AGENTS.md §4/§8`; Tasks 1-12 acceptance commands。
  Acceptance criteria (agent-executable): `./scripts/verify-architecture.sh` exit 0；`./gradlew :core:designsystem:verifyRoborazziDebug :feature:home:verifyRoborazziDebug --stacktrace --rerun-tasks` exit 0；`./scripts/verify.sh` exit 0；所有 changed source diagnostics 无 error；worktree 仅允许 release 前计划内文件。
  QA scenarios (name the exact tool + invocation): Happy：汇总 `<attemptDir>/task-13-2026-07-21-ui-debt-closeout.log` 和 `gate-manifest.json`（command/exit/artifact）。Failure：任一命令非 0 立即停止并保存首个失败完整日志，不用后续成功覆盖；修复根因后从聚焦测试重跑。
  Commit: N | 只读门禁；若修复则回到所属 Todo 新建独立修复提交并重跑

- [ ] 14. 用发布状态机 Prepare 固定签名 1.16.0 (168) Benchmark 候选
  What to do / Must NOT do: 先确认 `pwsh`、四项 `ANDROID_RELEASE_*`（只记录 SET/UNSET）、解锁且唯一/指定 ADB 设备、干净 main；执行 Status JSON，必须推导 NewRelease `1.16.0 (168)`。再执行 `release-prepare.ps1 -Stage Prepare -OutputPath <temp-json>`，由脚本只递增一次版本并跑完整固定签名门禁。核验输出 APK 文件名严格 `YYYY-MM-DDTHH-MM-SS.apk`、包名 `cc.pscly.onememos`、版本、SHA-256、signer count=1、证书 SHA-256 `58749c794f0c54af6b69bb6d80248a9fda0b75c687fde55b98d9575fc091633e`。不得手改版本绕过状态机、不得把 fallback debug APK 当候选、不得泄漏 keystore 路径/密码。
  Parallelization: Wave 4 | Blocked by: 13 | Blocks: 15
  References (executor has NO interview context - be exhaustive): `app/build.gradle.kts:24-63`; `scripts/release-prepare.ps1:1-110,652-730`; `scripts/release-state.ps1:74-79`; `scripts/README.md §8-10`; AGENTS.md §8。
  Acceptance criteria (agent-executable): Status/Prepare JSON 均 `ok=true` 且状态进入 `Prepared`；version diff 恰 `167→168`、`1.15.2→1.16.0`；Prepare gate 8/8 通过；候选 APK 绝对路径、mtime、size、SHA、包名、版本、唯一签名与固定证书写入 `<attemptDir>/task-14-.../candidate.json`。
  QA scenarios (name the exact tool + invocation): Happy：非交互 pwsh + `aapt2 dump badging` + `apksigner verify --verbose --print-certs`，证据 `<attemptDir>/task-14-2026-07-21-ui-debt-closeout.log`。Failure：任一签名变量/pwsh/设备缺失或摘要漂移时保持/恢复 1.15.2 工作树并标 `[blocked]`，禁止 PushMain；保存不含敏感值的 `.blocked.json`。
  Commit: N | 版本与证据在 Todo 16 通过设备门禁后原子提交

- [ ] 15. 对候选执行 agent 驱动的转场、TalkBack、最大字体与宏基准 QA
  What to do / Must NOT do: 非破坏安装 Todo 14 APK 到指定解锁设备；agent 通过公开 UI 准备一条固定 memo（若无），执行 Home→Editor→Back normal screenrecord、页面转场关闭/系统动画归零的 Reduced Motion 路径、新建随笔无 shared bounds、Archived→Editor 无 shared bounds。保存并恢复设备原字体/动画/主题/无障碍设置；若设备已安装 TalkBack，启用后以 swipe/focus 走首页单节点、更多操作、浏览→返回焦点、保存印章、归档反馈、TagChip、D-pad 焦点；fontScale 最大档检查 Home/Editor/Chip/印章无裁切。运行 fail-fast HomeScrollBenchmark，保存 benchmark JSON 和绝对指标；过滤本包 FATAL/ANR。不得输入/猜测 PIN、清用户数据、`locksettings clear`、卸载签名冲突应用或把 TalkBack 缺失标 PASS。
  Parallelization: Wave 4 | Blocked by: 14 | Blocks: 16
  References (executor has NO interview context - be exhaustive): `docs/qa/m3-2-paper-ink-accessibility.md:34-59`; `HomeScrollBenchmark.kt`; `.ai_session.md:475-497` 锁屏安全先例； ADR 0012 QA 后果。
  Acceptance criteria (agent-executable): 候选安装 Success 且 dumpsys 为 1.16.0 (168)；6 个转场场景、7 个 TalkBack/焦点场景、3 个最大字体表面均有 screenshot/UI XML/screenrecord 证据并 PASS；宏基准 10 iterations 完成且真实进入/返回 Editor；本包 FATAL/ANR 为空。任一必需能力不可用则 `[blocked]`，不发布。
  QA scenarios (name the exact tool + invocation): Happy：ADB + UIAutomator + `screenrecord` + `screencap` + `look_at`/visual-qa reviewer + connectedBenchmark，汇总 `<attemptDir>/task-15-2026-07-21-ui-debt-closeout/qa-manifest.json`。Failure：锁屏、无 memo、TalkBack 未安装或 Editor wait 超时必须产生明确 blocked/fail 证据；`finally` 恢复设备设置并记录前后值。
  Commit: N | 只产生被忽略的证据；文档化在 Todo 16

- [ ] 16. 固化发布候选证据、提交版本并取得远端授权
  What to do / Must NOT do: 将 Todo 15 实际结果回写 M3.2 QA 勾选项与 `.ai_session.md` 顶部；记录候选 APK 路径/SHA/签名、宏基准绝对指标、设备型号/序列号脱敏、转场/TalkBack/最大字体裁决和任何限制。与 `app/build.gradle.kts` 的 1.16.0 (168) 做单一 release commit；精确 staging，不纳入 `.omo/**`、APK/build、research 噪声或密钥。检查本地 main 是 origin/main 的可快进后继。随后向用户展示本地门禁和候选身份，询问一次“是否授权完成 PushMain→Tag→Actions→正式 latest Release→证据提交/cleanup”的明确批准；未批准就停止。
  Parallelization: Wave 4 | Blocked by: 15 | Blocks: 17
  References (executor has NO interview context - be exhaustive): `.ai_session.md` 最新发布记录格式；`docs/qa/m3-2-paper-ink-accessibility.md`; `app/build.gradle.kts:34-35`; AGENTS.md §5-8。
  Acceptance criteria (agent-executable): release commit subject `release: prepare 1.16.0`; `git show --stat --oneline HEAD` 只含版本/QA/会话计划内文件；`git status --short` 只剩进入任务前既有噪声；候选身份清单完整；用户远端授权被明确记录后 Todo 才完成。
  QA scenarios (name the exact tool + invocation): Happy：`git status/diff/log` 审查 + secret scan，证据 `<attemptDir>/task-16-2026-07-21-ui-debt-closeout.log`。Failure：staging 含 `.omo/run-continuation`、APK、keystore、无关 research 或版本不是 1.16.0 (168) 时拒绝提交/推送并修正。
  Commit: Y | `release: prepare 1.16.0`

- [ ] 17. 按状态机完成 main、annotated Tag、Actions 与正式 latest Release
  What to do / Must NOT do: 仅在 Todo 16 明确授权后，逐阶段使用独立 OutputPath JSON 执行 `PushMain`、`PushTag`、`WaitTagActions`、`PublishRelease`、`VerifyRelease`，每步检查 `ok/state/reasonCode` 后再继续。Tag 必须为 annotated `v1.16.0` 且 peeled SHA=release commit；Tag Actions completed/success 且 artifact 唯一、未过期、来自 Tag SHA；正式 Release 非 draft/non-prerelease/latest，唯一 APK 资产必须来自该 Tag Artifact，而非本地候选。核验包名/版本/SHA/固定证书。临时 Actions/配额故障只重跑同 Tag/SHA；代码缺陷按仓库受控 recovery，禁止随意移动 Tag/force push。
  Parallelization: Wave 5 | Blocked by: 16 + 用户远端授权 | Blocks: 18
  References (executor has NO interview context - be exhaustive): `scripts/release-publish.ps1:1-14,1334-1434`; `scripts/README.md §7-10`; `scripts/release-state.test.ps1`; AGENTS.md §8。
  Acceptance criteria (agent-executable): origin/main 包含 release commit；annotated tag/peeled SHA 正确；Tag run success；VerifyRelease JSON `ok=true`, `state=Completed`, `reasonCode=RELEASE_DEEP_VERIFIED`；Release latest 且唯一时间戳 APK metadata/signature/SHA 均匹配 artifact。
  QA scenarios (name the exact tool + invocation): Happy：阶段 JSON + `gh`/GitHub API 只读交叉核验，证据 `<attemptDir>/task-17-2026-07-21-ui-debt-closeout/release-manifest.json`。Failure：任一步 reasonCode 非成功立即停止，不跳阶段、不重建已存在正确 Tag；保留完整失败 JSON 并按状态机修复。
  Commit: N | 远端发布动作，不修改源码

- [ ] 18. 独立下载正式 APK、回写最终证据并执行 Cleanup
  What to do / Must NOT do: 从正式 Release URL 下载唯一 APK 到 `/tmp/opencode/v1.16.0-independent-release-download/`，与 Tag Artifact 逐字节比较并独立跑 aapt2/apksigner/SHA。安装正式下载资产做 Home→Editor→Back 最终 smoke。把 Release URL/id、Tag object/peeled SHA、Actions run/artifact、资产文件名/size/SHA/证书、独立下载路径和最终 APK 路径写入 `.ai_session.md`；docs-only evidence commit 普通 push main，不移动 Tag。随后执行 `Cleanup`，确认只删除 v1.16.0 临时报告且 `Completed/RELEASE_DEEP_VERIFIED` 仍成立。
  Parallelization: Wave 5 | Blocked by: 17 | Blocks: Final verification wave
  References (executor has NO interview context - be exhaustive): `.ai_session.md:130-199` 的 v1.14.0 证据格式；`scripts/README.md §8-10`; `scripts/release-publish.ps1` Cleanup；用户要求最终告知时间戳 Benchmark APK 路径。
  Acceptance criteria (agent-executable): Release 下载、Tag Artifact、正式资产 SHA 一致；包名 1.16.0 (168)、唯一固定证书；安装 smoke PASS；docs evidence commit 已普通推送 main；Cleanup JSON success；Tag object/peeled SHA/Release asset 未改变。
  QA scenarios (name the exact tool + invocation): Happy：独立下载校验 + ADB smoke + Cleanup，证据 `<attemptDir>/task-18-2026-07-21-ui-debt-closeout.log`。Failure：下载与 Artifact 任一字节/metadata/signature 不一致时停止、撤下错误 Release 资产并按同版本状态机修复，绝不宣称完成。
  Commit: Y | `docs(release): record v1.16.0 evidence`

## Final verification wave
> Runs in parallel after ALL todos. ALL must APPROVE. Surface results and wait for the user's explicit okay before declaring complete.
- [ ] F1. Plan compliance audit：独立 Oracle 逐条核对 18 个 Todo 的 acceptance/evidence/release JSON，尤其是 shared bounds 排除路径与稳定版闭环；输出 `APPROVE` 或精确缺口。
- [ ] F2. Code quality review：独立 reviewer 检查 Compose modifier/semantics、Navigation 3 scope 生命周期、依赖边界、测试强度、UTF-8 与敏感信息；运行 changed-file diagnostics 和 `git diff --check`；必须 `APPROVE`。
- [ ] F3. Real manual QA：由 agent 对独立下载的正式 Release APK 再走 Home→Editor→Back、Reduced Motion、TalkBack 单节点和最大字体 smoke，保存 screenshot/UI XML/logcat；不得用候选或组件测试替代；必须 `APPROVE`。
- [ ] F4. Scope fidelity：比较执行基线到最终 main/tag diff；确认 `QuickCaptureOverlayService.kt` 字节未变、无 M7/AlertDialog 全量迁移/跨栈 shared bounds/无关 research 或 `.omo/run-continuation` 提交；确认 Tag 不在 docs evidence commit 上移动；必须 `APPROVE`。

## Commit strategy
- 每个实现 Todo 按其 Commit 行原子提交；并行代理只交付 disjoint patch，主执行者按依赖顺序逐一诊断、测试、精确 stage 和 commit。
- 提交前统一检查 `git status`、`git diff --cached`、`git log --oneline -10`；禁止 amend、force-push、skip hooks、提交密钥或 build/APK。
- 推荐顺序：navigation foundation → Chip a11y → designsystem goldens → 四个业务接线提交 → shared bounds → contract → Home goldens → macrobenchmark → docs → `release: prepare 1.16.0` → `docs(release): record v1.16.0 evidence`。
- `.omo/plans/**` 与 `.omo/drafts/**` 是规划产物，不纳入产品/发布提交；既有脏文件保持不动。

## Success criteria
- Home active existing memo 与 Editor 内容根容器使用同一 `memo/<uuid>` shared bounds；Archived/new/cross-section/Reduced Motion 不启用；页面栈与转场既有测试全绿。
- PaperInk 生产契约报告 8 个 TopAppBar、5 个非 overlay BottomSheet、1 个 SnackbarHost 全部接线；唯一 overlay 豁免未改；AlertDialog 全局路径保持。
- clickable TagChip 和 enabled/disabled InkChip merged touch bounds ≥48dp、视觉紧凑、selected/disabled/focus/TalkBack 语义正确；静态 TagChip 无伪按钮。
- Editor 同步失败横幅迁移后文案、单次 retry 和 polite live region 正确；Collections/Profile/Todo 空态及 Editor loadError 原样保留。
- designsystem 与 Home Roborazzi 明暗/最大字体 verify 全绿；full verify/architecture/lint/tests/benchmark assemble 全绿；宏基准真实进入/返回 Editor 并产出 10 iterations 指标。
- 设备 normal/reduced/new/archived 转场、TalkBack、D-pad、最大字体、FATAL/ANR 全部有可审计 PASS 证据；无设备/服务不能降级为完成。
- 正式稳定版为 `v1.16.0`, `cc.pscly.onememos`, versionCode 168；Release 非 draft/non-prerelease/latest，唯一时间戳 Benchmark APK 来自固定签名 Tag Artifact，证书 SHA-256 固定且独立下载一致。
- main、origin/main、annotated Tag、Tag Actions、Release、docs evidence commit、Cleanup 状态全部符合仓库状态机；最终向用户报告本地候选与正式下载 APK 绝对路径、Release URL、SHA-256 和签名摘要。
