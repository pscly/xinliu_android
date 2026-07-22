---
slug: 2026-07-22-feature-ux-resilience
status: complete
intent: unclear
review_required: true
pending-action: explicit user start-work instruction required before implementation; remote release authorization remains separately required at execution time
approach: 以 origin/main@2910580 为唯一产品基线，在独立 clone（从 git@github.com:pscly/xinliu_android.git）中修复已验证的功能域无障碍缺陷与紧凑/窄屏/大字体/IME 布局回归，并通过 Compose 语义测试、可缩放布局截图矩阵和 Gradle 门禁补全形成可复跑证据链；授权发布通过 release-prepare.ps1 Prepare 阶段权威写版本 + release-publish.ps1 状态机（PushMain→PushTag→WaitTagActions→PublishRelease→VerifyRelease→Cleanup），受控恢复通过 PrepareRecovery→PushRecoveryMain→RecoverTag with exact lease
---

# Draft: 2026-07-22-feature-ux-resilience

## Components (topology ledger)
<!-- id | outcome (one line) | status: active|deferred | evidence path -->
| id | outcome | status | evidence path |
| --- | --- | --- | --- |
| C1 | 功能域语义与动态播报修复：Auth 两个 pane 带 testTag 的错误 wrapper + `SemanticsMatcher.expectValue(Error/LiveRegion)`；Home FilterStatusBanner live region；Todo 48dp outer `toggleable(Checkbox)`/`clickable(Button)` + `stateDescription`；TodoUndoHost with `onExpired`/`onUndo`；QuickCapture 48dp outer `combinedClickable`；Profile `semantics.onClick` | active | `AuthScreen.kt:51-97,103-196,215-310`; `HomeScreen.kt:1215-1234`; `TodoItemRow.kt:195-230`; `TodoScreen.kt:140-180,396-430`; `QuickCaptureScreen.kt:131-362,420-443`; `ProfileScreen.kt:226-432` |
| C2 | 紧凑高度、窄宽度、IME 和 fontScale=2.0 布局：Auth/Welcome `verticalScroll`；ShareCard `verticalScroll` + `FlowRow`(`@OptIn`)；QuickCapture `verticalScroll` + IME；Profile InkCard `contentPadding`→336dp→7×48dp；Todo/Collections 通过 LazyColumn container seam 测试驱动修复 | active | `AuthScreen.kt`; `WelcomeScreen.kt`; `ShareCardScreen.kt`; `QuickCaptureScreen.kt`; `ProfileScreen.kt:115-118`; `InkCard.kt:118`; `TodoScreen.kt:280-397`; `CollectionsScreen.kt:555-570` |
| C3 | 仅修复反馈不改产品行为：TodoUndoHost 内部 `visibleId` 状态机 + `onExpired`/`onUndo` 回调，延迟六秒不变；`onExpired` 清除父级 `lastDeletedItemId`，允许同 id 在过期后重新请求；Todo/Collections 本地数据库流不动 | active | `TodoScreen.kt:170-180,396-430`; `CollectionsScreen.kt` |
| C4 | 自动化 QA：internal 入口用于 Compose 语义/布局测试（无 Hilt/mock）；Roborazzi 40 张金图（Auth 8 + Welcome 8 + QuickCapture 8 + Profile 8 + ShareCard 8），使用确定性 fixture 及状态映射；verify.sh 新增 18 个调用 | active | `scripts/verify.sh`; 各模块 `build.gradle.kts` |
| C5 | 产品改动后 1.17.0 (169) 固定签名 Benchmark APK → main → Tag → Actions → latest Release（远程步骤需执行时显式授权，瞬时失败用 `gh run rerun`，永不删除已推送 tag） | active | `scripts/release-*.ps1`; `.ai_session.md` |

## Open assumptions (announced defaults)
<!-- assumption | adopted default | rationale | reversible? -->
| assumption | adopted default | rationale | reversible? |
| --- | --- | --- | --- |
| 执行环境 | 从 `git@github.com:pscly/xinliu_android.git` 独立 clone（非 shared worktree）；实现后 `git switch main && git merge --ff-only ux-resilience-1.17.0` | release-publish.ps1 要求当前分支为 main；独立 clone 不干扰 dirty 主工作区 | 否 |
| 导航与 PaperInk 方向 | 保留现有 Navigation 3 宿主和 PaperInk 视觉 | DESIGN.md + ADR 0012 | 否 |
| 测试 seam | 每个屏幕暴露 `internal` stateless composable，显式 state + callbacks；生产 route wrapper 收集 Hilt ViewModel 并委托 | 渲染路径相同，无需 mock/Hilt | 否 |
| Todo list seam | `internal TodoItemsList(modifier, LazyListScope.content)` 仅包装 `LazyColumn`；不拥有 permission/filter/empty/section/item 内容 | 所有现有列表内容完整保留 | 否 |
| Collections list seam | `internal CollectionsItemsList(items, state, modifier, itemContent)` 包装 `LazyColumn`；`modifier` 原封不动传递给调用方（生产传入 `nestedScroll`） | 匹配生产确切语义 | 否 |
| TodoUndoHost | `internal TodoUndoHost(deletedItemId, onUndo, onExpired, modifier)`：内部 `visibleId`，`onExpired` 清除父级 id 实现同 id 可重用 | 保留六秒行为，添加行为安全测试 | 是 |
| Auth seam | `internal AuthScreenContent(uiState, 12 个 callbacks)`，总共 13 个参数；11 个 callbacks 映射 AuthViewModel 方法，`onBack` 是导航 | 固定基线 11 个 ViewModel 方法已确认 | 否 |
| Profile seam | `internal ProfileScreenContent(uiState, 9 个 callbacks)`，总共 10 个参数；haptic 留在 wrapper | 固定基线 7 个 ViewModel 方法 + 2 个导航回调 | 否 |
| ShareCard seam | `internal ShareCardScreenContent(uiState, selectedTabIndex, 13 个 callbacks)`，总共 15 个参数；Context/Toast/Intent 留在 wrapper | 固定基线 11 个 ViewModel 方法 + 2 个操作回调 | 否 |
| QuickCapture seam | `internal QuickCaptureScreen(14 个参数)` + `internal QuickCaptureTextAction(4 个参数)` | 固定基线已确认确切签名 | 否 |
| Error 断言 | `SemanticsMatcher.expectValue(SemanticsProperties.Error, "string")` — Error 是 `AccessibilityKey<String>`，不是 `List<String>` | 官方 AndroidX 源码 | 否 |
| 测试 stub | 回调为普通 `{}`，无 `mock()`/Mockito/MockK | 测试渲染路径相同，无需 mock | 否 |
| 截图固定输入 | 确定性 fixture（非“mock”），附带特定状态到限定符/主题的映射表 | 每张金图一个确定状态 | 否 |
| ui-test-manifest | `debugImplementation` | 匹配已验证的 `feature/home` 模式 | 否 |
| FlowRow | 需要 `@OptIn(ExperimentalLayoutApi::class)` | API 实验性 | 否 |
| 设备 | 无物理设备强制要求；Robolectric 语义测试作为可运行兜底 | 测试矩阵无设备可运行 | 否 |
| v1.16.0 已完成 | PaperInk 包装器、Chip 48dp/语义、shared bounds、Home goldens、宏基准 fail-fast | DESIGN.md:461-475 | 否 |
| 发布 | 1.17.0 (169) | 版本状态机 | 否 |

## Findings (cited - path:lines, all from pinned baseline 2910580)
- `origin/main@2910580` 为唯一基线；`v1.16.0^{}` 为 `bab424c`。主工作区不相关修改。
- `DESIGN.md:461-475`（§8.3）确认 v1.16.0 波次已完成。`DESIGN.md:518-521`（§9.6）为阅读/系统缩放。
- `AuthScreen.kt:51-97` root Column 不可滚动。`AuthViewModel.kt:75-147` 有 11 个 UI 方法。
- `HomeScreen.kt:1372-1376` SyncStatusBanner 非缺陷。`FilterStatusBanner` 为 private。
- `TodoItemRow.kt:195-230` TodoDoneMark 为 private，视觉 30dp（`TodoStatusIconSize`）。`TodoScreen.kt:284-397` LazyColumn 包含权限提示、筛选、空态、区段、TodoItemRow。`:170-180,396-430` 撤销内联于 Hilt screen。
- `QuickCaptureScreen.kt:131-362` QuickCaptureScreen 为 private，14 个参数。`:420-443` QuickCaptureTextAction 为 private，4 个参数。
- `ProfileScreen.kt:115-118` LazyColumn X16。`InkCard.kt:118` X14 默认填充。W360 处 HeatmapGrid 收到 300dp → 已溢出。
- `ShareCardScreen.kt:60-396` 含 Context/Toast/Intent。面板使用固定 `Row`。
- `CollectionsScreen.kt:555-570` LazyColumn 含 `fillMaxWidth().nestedScroll(...)`。`:873-1270` CollectionItemCard 为 private，17 个参数。`core/model/.../CollectionItem.kt:13-26` 有 14 个字段。
- `scripts/verify.sh` 缺失 7 个现有模块测试（`:core:data`, `:core:designsystem`, `:core:domain`, `:core:sync`, `:feature:editor`, `:feature:profile`, `:feature:todo`）+ 4 个本轮新增 unit（`:feature:auth`, `:feature:welcome`, `:feature:quickcapture`, `:feature:sharecard`）+ 7 个 Roborazzi verify = 18 个新增调用。`:feature:home:testDebugUnitTest` 和 `:feature:collections:testDebugUnitTest` 已存在于基线，不重复。新增后总计 34 个 Gradle 调用。
- `SemanticsProperties.Error` 为 `AccessibilityKey<String>`（官方 AndroidX 源码）。

## Decisions (with rationale)
1. 测试仅通过 `internal` stateless composable 入口，显式 state + `{}` stub callbacks。不使用 `mock()`/Hilt。
2. `TodoItemsList` 包装仅 `LazyColumn`；所有内容（权限、筛选、空态、区段、项目）保留在生产环境的 `content` lambda 内。
3. `CollectionsItemsList` 通过调用方提供的 `modifier` 参数原封不动传递（生产环境传递 `nestedScroll`）。
4. `TodoUndoHost` 分离 `onExpired`（清除父级 id）和 `onUndo`（手动恢复），父级 id 在过期时清除，使同一 id 可重用。
5. Auth（13 个总参数：uiState + 12 个 callbacks）、Profile（10 个：uiState + 9 个 callbacks）、ShareCard（15 个：uiState + selectedTabIndex + 13 个 callbacks）的参数计数固定基线。
6. Error 断言使用 `SemanticsMatcher.expectValue(SemanticsProperties.Error, "string")`（`AccessibilityKey<String>`，非列表）。
7. 截图使用“确定性 fixture”，附带状态映射表；不使用“mock”。
8. `ui-test-manifest` 为 `debugImplementation`。`@Config(qualifiers = "...")`。FlowRow 需要 `@OptIn`。
9. 发布：1.17.0 (169)，`release-prepare.ps1 -Stage Prepare` 权威写版本，`release-publish.ps1` 状态机 PushMain→PushTag→WaitTagActions→PublishRelease→VerifyRelease→Cleanup；恢复路径 PrepareRecovery→PushRecoveryMain→RecoverTag with exact lease；永不删除 tag；远程阶段需明确授权。

## Scope IN
- AuthScreenContent 完整内容（13 个参数）+ tagged error wrappers + `expectValue(Error/LiveRegion)` + verticalScroll
- WelcomeScreenContent 完整内容 + verticalScroll + IME
- Home FilterStatusBanner internal + LiveRegion
- TodoDoneMark internal + 48dp toggleable/clickable + clearAndSetSemantics inner
- TodoUndoHost internal（onExpired/onUndo，visibleId 状态机，同 id 可重用）
- TodoItemsList LazyListScope seam + TodoItemPresentation fixture sibling-bounds
- QuickCaptureScreen（14 个参数）+ QuickCaptureTextAction（4 个参数）+ tagged error + 48dp outer + scroll + 真实字段 `QuickCaptureUiState` fixture（`draftBannerVisible=true`），不含虚构 `draft` 字段
- InkCard contentPadding（78 个调用方默认不变）+ ProfileScreenContent（10 个参数）+ heatmap 336dp + 7×48dp + semantics.onClick + 真实字段 `ProfileUiState` fixture（heatmap, month, selection, sections, selectedMemoCount），不含 `mock`
- ShareCardScreenContent（15 个参数）+ FlowRow（@OptIn）+ verticalScroll + Y-diff wrapping
- CollectionItemCard internal with modifier（18 个总参数）+ CollectionsItemsList content-lambda（modifier 传递）+ 14 字段 fixture sibling-bounds
- 40 张 Roborazzi 金图含确定性 fixture 及状态映射
- verify.sh +18 条精确调用（7 missed existing unit + 4 new feature unit + 7 Roborazzi verify）；home/collections 的 unit 已存在于基线，不重复
- 1.17.0 (169)、发布前审计、授权发布（`release-prepare.ps1 Prepare` → `release-publish.ps1` 状态机：PushMain→PushTag→WaitTagActions→PublishRelease→VerifyRelease→Cleanup，恢复路径 PrepareRecovery→PushRecoveryMain→RecoverTag with lease，固定签名 `58749c79...`）
- 独立 clone（非 shared worktree），因 `release-publish.ps1` 要求当前分支为 `main`
- Profile fixture `gridStart` 使用真实算法 `month.atDay(1).minusDays((month.atDay(1).dayOfWeek.value - 1).toLong())` = 2026-06-29（针对 2026-07）
- QuickCapture fixture 使用 `draftBannerVisible=true`，无虚构 `draft` 字段
- 准备期本地 APK SHA、CI Artifact SHA 与 Release 下载 APK SHA 为三个独立核验值；CI Artifact 与 Release APK 的 SHA 必须一致

## Scope OUT (Must NOT have)
- 不修改 OverlayService、SyncStatusBanner、editor build.gradle.kts
- 不使用 `announceForAccessibility`、`paneTitle`、`@Config(fontScale=...)`、`selected` for Todo
- 不使用 `mock()`、Mockito、MockK、Hilt ViewModel、private composables、`runTest`、`TestCoroutineScheduler`、source grep
- 不使用 `assertIsLiveRegion()`、不使用 `testImplementation` for `ui-test-manifest`
- 不使用 `TodoItemWithListName`、`paperInkScrollAware` 或任何虚构类型/修饰符
- 不引入 NavigationSuiteScaffold、新库、设备专用验收、horizontalScroll
- 不改变 Todo 六秒延迟（仅从 source grep 改为 mainClock behavior test）
- 不改变 InkCard 默认填充（78 个调用方），不删除/覆盖 `.omo/**`

## Open questions
- 无。所有 seam、fixture 和状态机均已按固定基线精确指定。发布管道需要执行时的明确外部授权。

## Approval gate
status: complete
requires: PASSED — Momus and Oracle both unconditionally APPROVE the same reviewed plan/draft hash pair
pending-action: explicit user start-work instruction before any product implementation
approval-scope: 计划生成仅授权计划；实现与远程发布均需分别明确授权
<!-- 与 Plan 对齐：LazyListScope/TodoItemsList、CollectionsItemsList content-lambda（modifier 传递）、TodoUndoHost onExpired/onUndo、确定性 fixture（非 mock）、Error 为 String、Auth 12/13 cb、Profile 9/10 cb、ShareCard 13/15 cb、40 张金图、18 次 verify 调用 -->
<!-- 状态：complete — 双审均已 APPROVE，计划 hash 已匹配且不可变 -->

## High-accuracy review receipts

| Reviewer | Session | Verdict | Reviewed Plan SHA-256 | Reviewed Draft SHA-256 |
| --- | --- | --- | --- | --- |
| Momus | `ses_0766c0b74ffeWdM63nv9u3aRog` | APPROVE | `d0b4d32b7d43698a6ba91c045dbea8939d31ce9a72607525304f673aa662e8b8` | `e115ad9852357277ef1726c3152271788756f2e81f932545644eb6f9491dd3e3` |
| Oracle | `ses_0766bfdb7ffeOqAuqBOb4DGnez` | APPROVE | `d0b4d32b7d43698a6ba91c045dbea8939d31ce9a72607525304f673aa662e8b8` | `e115ad9852357277ef1726c3152271788756f2e81f932545644eb6f9491dd3e3` |

**Fix/retry summary:** Earlier rejected versions contained fictional API (`assertIsLiveRegion`), fictional fixture fields (`draft = true`, `TodoItemWithListName`), nullable `onUndo(id)` with unsafe cast, missing `PushRecoveryMain` in recovery sequence, and `Instant.now()` / `ShareCardTheme.CLASSIC` instead of real baseline fields. All were repaired before the final reviewed hash pair. The final review round found zero blockers.

**Note:** This post-receipt draft hash differs from the reviewed input draft hash because this receipt-only metadata mutation was added after reviews completed. The reviewed plan remains byte-for-byte unchanged at its reviewed SHA-256.
