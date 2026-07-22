# 2026-07-22-feature-ux-resilience - Work Plan

## TL;DR (For humans)

**What you'll get:** Eight app screens gain accessibility announcements and resilient layouts. Tests use deterministic real-field fixtures — no `mock()` anywhere. 40 Roborazzi goldens. Release follows the repository's `release-publish.ps1` state machine exactly; implementation happens in an independent Git clone (not shared worktree) so `main` can be consumed by the release scripts. Controlled tag recovery is available via `RecoverTag` with exact lease, never plain tag deletion.

**Why this approach:** Independent clone from `git@github.com:pscly/xinliu_android.git` ensures `origin` points to GitHub (not a dirty local repo). `release-publish.ps1`'s `PushMain` stage pushes `refs/heads/main:refs/heads/main` from the clone's own `main`, which is fast-forwarded from the feature branch. Todo/Collections list seams preserve all existing production content. `TodoUndoHost` separates expiry (`onExpired`) from undo (`onUndo`) so parent state clears reliably.

**Effort:** Medium | **Risk:** Medium — InkCard `contentPadding` affects 78 callers; QuickCapture has 14 parameters; release recovery requires device walkthrough

**Decisions:** Independent clone from GitHub remote. `release-prepare.ps1 -Stage Prepare` authority-writes version from 1.16.0(168) to 1.17.0(169). Normal release path never moves tags; controlled `RecoverTag` is the only exception (requires exact OID/peeled SHA/target SHA/evidence SHA/authorization). No file:// URL for clone origin. All evidence written to primary workspace `.omo/evidence/`, never into the release clone.

**Your next move:** After dual review both approve, issue explicit start-work. Remote release authorization is separate.

---

> TL;DR (machine): 18 todos, independent clone from GitHub, release-publish.ps1 state machine, recovery via PrepareRecovery→PushRecoveryMain→RecoverTag with lease, 40 goldens, 18 verify calls, version 1.17.0 (169)

## Scope
### Must have
- C1: Auth two panes' error `Box` with distinct testTags + `SemanticsMatcher.expectValue(Error, "测试错误")` + `LiveRegion=Assertive`; Home FilterStatusBanner live-region + contentDescription; Todo 48dp outer `toggleable(Role.Checkbox)`/`clickable(Role.Button)` + `stateDescription`; `internal TodoUndoHost(deletedItemId, onUndo, onExpired)` with `visibleId` state machine; QuickCapture 48dp outer `combinedClickable` + accessible label; Profile heatmap cell `semantics.onClick`
- C2: Auth/Welcome `verticalScroll` + `imePadding()`; ShareCard `verticalScroll` + `FlowRow (@OptIn)`; QuickCapture `verticalScroll` + IME; Profile InkCard `contentPadding` param (78 callers default unchanged) → heatmap 336dp at W360 → 7×48dp disjoint cells; Todo/Collections bounded layout fixes via sibling-bounds test
- C3: Todo six-second undo preserved (verified by `composeTestRule.mainClock.advanceTimeBy(..., ignoreFrameDuration = true)`); `TodoUndoHost.onExpired` clears parent `lastDeletedItemId` enabling same-id re-delete
- C4: Robolectric Compose semantics/layout tests via `internal` entries (no Hilt/`mock()`); Roborazzi 5×8=40 goldens with deterministic state mappings; verify.sh +18 exact Gradle calls (from 16 baseline to 34 total)
- C5: version 1.17.0 (169), `release-prepare.ps1 -Stage Prepare` authority-writes version, `release-publish.ps1` drives PushMain→PushTag→WaitTagActions→PublishRelease→VerifyRelease→Cleanup; controlled recovery via PrepareRecovery→PushRecoveryMain→RecoverTag; all remote stages require explicit authorization
- Independent clone from `git@github.com:pscly/xinliu_android.git` at pinned SHA `2910580`; MainActivity `adjustResize`; InkCard `contentPadding`; `.ai_session.md`

### Must NOT have
- No private composable calls from tests, no Hilt ViewModel in tests, no `mock()`/Mockito/MockK, no fictional types or fields
- No `runTest`/`TestCoroutineScheduler`, no source grep as runtime proof, no `announceForAccessibility`/`paneTitle`/`@Config(fontScale=...)`
- No NavigationSuiteScaffold, no new third-party libs, no device-only acceptance, no stale-main push, no plain tag deletion/recreation
- No duplicate `imePadding()` nesting, no competing inner semantics, no InkCard default padding change
- No `TodoItemWithListName`, `paperInkScrollAware`, `draft = true` (nonexistent field)
- No `file://` URL as clone origin, no shared worktree with dirty primary workspace
- No `.omo` evidence written into the independent release clone

## Verification strategy
- tests-after + Robolectric Compose assertions via `internal` entries + Roborazzi pixel comparisons
- JUnit 4 + `androidx.compose.ui:ui-test-junit4` + Robolectric 4.12.2 + Roborazzi
- `SemanticsProperties.Error` = `AccessibilityKey<String>` (official AndroidX source)
- Evidence: `/root/1codes/xinliu_android/.omo/evidence/2026-07-22-feature-ux-resilience/task-<N>.txt` (`.png` for Roborazzi)
- Release JSON: `/tmp/1memos-release-1.17.0/<stage>.json`

## Execution strategy
### Parallel execution waves
Wave 0 (Todo 0) → Wave 1 (Todo 1) → Wave 2 (Todos 2-10 parallel) → Wave 3 (Todos 11-14 parallel) → Wave 4 (Todo 15) → Wave 5 (Todos 16-17 sequential)

### Dependency matrix
| Todo | Wave | Blocked by | Blocks |
| --- | --- | --- | --- |
| 0 (Clone) | 0 | — | 1-17 |
| 1 (Gradle+Manifest) | 1 | 0 | 2-17 |
| 2 (Auth) | 2 | 1 | 11,16 |
| 3 (Welcome) | 2 | 1 | 11,16 |
| 4 (Home Filter) | 2 | 1 | 16 |
| 5 (Todo) | 2 | 1 | 9,16 |
| 6 (QuickCapture) | 2 | 1 | 13,16 |
| 7 (Profile+InkCard) | 2 | 1 | 12,16 |
| 8 (ShareCard) | 2 | 1 | 14,16 |
| 9 (Todo layout test) | 2 | 5 | 16 |
| 10 (Collections layout) | 2 | 1 | 16 |
| 11 (Auth+Welcome goldens) | 3 | 2,3 | 15,16 |
| 12 (Profile goldens) | 3 | 7 | 15,16 |
| 13 (QuickCapture goldens) | 3 | 6 | 15,16 |
| 14 (ShareCard goldens) | 3 | 8 | 15,16 |
| 15 (verify.sh) | 4 | 2-14 | 16 |
| 16 (Pre-release audit) | 5 | 15 | 17 |
| 17 (Release) | 5 | 16 + auth | — |

## Todos
> Implementation + Test = ONE todo. Never separate.
<!-- APPEND TASK BATCHES BELOW THIS LINE WITH edit/apply_patch - never rewrite the headers above. -->
- [ ] 0. Independent clone creation from pinned baseline commit（GitHub remote）
  What to do: `git fetch origin`. Verify `origin/main` = `2910580bcb5f3afea8f8912e0bc726f6998ad3e2`. If drift → STOP. Clone from GitHub: `git clone --no-checkout git@github.com:pscly/xinliu_android.git ../xinliu_android-uxresil`. `cd ../xinliu_android-uxresil && git fetch --prune origin main`. Verify `origin/main` = pinned SHA. `git switch main && git rev-parse HEAD` must be pinned SHA. `git switch -c ux-resilience-1.17.0 2910580bcb5f3afea8f8912e0bc726f6998ad3e2`. If directory or branch exists → STOP (never auto-delete). Confirm `git rev-parse HEAD` = pinned SHA, branch = `ux-resilience-1.17.0`, `main` exists. This is an independent clone, NOT a shared worktree — `release-publish.ps1` requires current branch to be `main`, and the clone's own `main` can be fast-forwarded. Must NOT: use `file://` URL (would make origin point to dirty local repo), create worktree, or proceed if remote drifted.
  Parallelization: Wave 0 | Blocked by: — | Blocks: 1-17
  References: `origin/main@2910580bcb5f3afea8f8912e0bc726f6998ad3e2`; GitHub remote `git@github.com:pscly/xinliu_android.git`; `release-publish.ps1` PushMain stage
  Acceptance criteria: Independent clone at `../xinliu_android-uxresil`, origin = GitHub. HEAD = pinned SHA. Branch = `ux-resilience-1.17.0`. `main` exists. `git status --porcelain` empty.
  QA scenarios: Happy — clone clean from GitHub; Failure — SHA drift → STOP; Failure — directory/branch collision → STOP.
  Evidence: `/root/1codes/xinliu_android/.omo/evidence/2026-07-22-feature-ux-resilience/task-0.txt`
  Commit: N | infra: create independent clone from GitHub remote at pinned 2910580

- [ ] 1. Gradle test dependencies + Manifest IME infra
  What to do: Add `android:windowSoftInputMode="adjustResize"` to MainActivity at `app/src/main/AndroidManifest.xml:51`. For every target module, add exact `feature/home/build.gradle.kts` pattern:
  ```gradle
  testOptions {
      unitTests {
          isIncludeAndroidResources = true
          all {
              it.systemProperties["robolectric.pixelCopyRenderMode"] = "hardware"
          }
      }
  }
  testImplementation(libs.junit)  // keep if existing
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)
  testImplementation(platform(libs.androidx.compose.bom))
  testImplementation(libs.androidx.compose.ui)
  testImplementation("androidx.compose.ui:ui-test-junit4")
  debugImplementation("androidx.compose.ui:ui-test-manifest")  // NOTE: debugImplementation, NOT testImplementation
  ```
  Roborazzi addition (only for screenshot modules): add `alias(libs.plugins.roborazzi)` to plugins block, `roborazzi { outputDir.set(file("src/test/screenshots")) }`, `testImplementation(libs.roborazzi)`, `testImplementation(libs.roborazzi.compose)`, `testImplementation(libs.roborazzi.junit.rule)`.
  Module assignment:
  - **feature/todo**: base only, keep existing `junit`, NO Roborazzi
  - **feature/profile**: base + Roborazzi, keep existing `junit`
  - **feature/auth, feature/welcome, feature/quickcapture, feature/sharecard**: base + Roborazzi (zero `testImplementation` currently)
  - **feature/collections**: base only, keep existing `junit` + `kotlinx-coroutines-test`, NO Roborazzi
  - **feature/editor**: NO changes
  Must NOT: touch QuickCaptureActivity, add Roborazzi to todo/editor/collections, use `testImplementation` for `ui-test-manifest`.
  Parallelization: Wave 1 | Blocked by: 0 | Blocks: 2-17
  References: `feature/home/build.gradle.kts:26-37,79-88`（complete pattern）; `app/src/main/AndroidManifest.xml:51-56`; `feature/collections/build.gradle.kts`
  Acceptance criteria: `./gradlew :feature:todo:testDebugUnitTest --dry-run` succeeds. `./gradlew :feature:collections:testDebugUnitTest --dry-run` succeeds. All 5 `verifyRoborazziDebug --dry-run` exist. Manifest has `adjustResize`. QuickCaptureActivity unchanged.
  QA scenarios: Happy — all dry-run green; Failure — missing `isIncludeAndroidResources` → Robolectric "not an Android resource"; Failure — `ui-test-manifest` on `testImplementation` → runtime classpath issue.
  Evidence: `/root/1codes/xinliu_android/.omo/evidence/2026-07-22-feature-ux-resilience/task-1.txt`
  Commit: Y | build(infra): add Compose/Robolectric/Roborazzi test deps per home pattern; add MainActivity adjustResize

- [ ] 2. Auth screen: extract full `internal AuthScreenContent` + tagged error wrappers + verticalScroll IME + tests
  What to do: Extract `internal @Composable fun AuthScreenContent(uiState: AuthUiState, onBack: () -> Unit, onTabSelected: (AuthTab) -> Unit, onUsernameChanged: (String) -> Unit, onPasswordChanged: (String) -> Unit, onConfirmPasswordChanged: (String) -> Unit, onPasswordVisibleToggle: () -> Unit, onBackendFormSwitch: (BackendForm) -> Unit, onBackendSubmit: () -> Unit, onTokenChanged: (String) -> Unit, onTokenVisibleToggle: () -> Unit, onCustomServerUrlChanged: (String) -> Unit, onCustomSave: () -> Unit)` — 12 callbacks + `uiState` = 13 total parameters. 11 callbacks map to AuthViewModel methods (switchTab, switchBackendForm, updateUsername, updatePassword, updateConfirmPassword, updateCustomServerUrl, updateCustomToken, togglePasswordVisible, toggleTokenVisible, submitBackend, saveCustom); `onBack` is navigation. Production `AuthScreen` wrapper: collects `uiState` via `collectAsStateWithLifecycle()`, wires `LaunchedEffect` for `Authed` event (clearFocus + onAuthed), delegates to `AuthScreenContent`.
  Inside `AuthScreenContent`, wrap each pane's conditional error `Text` in a dedicated Box:
  - BackendPane error (baseline `:171-176`): `Box(modifier = Modifier.semantics { error(uiState.error.orEmpty()); liveRegion = LiveRegionMode.Assertive }.testTag("auth_error_backend"))`
  - CustomPane error (baseline `:278-283`): `Box(modifier = Modifier.semantics { error(uiState.error.orEmpty()); liveRegion = LiveRegionMode.Assertive }.testTag("auth_error_custom"))`
  Root Column (line 79) → `.verticalScroll(rememberScrollState()).imePadding()`. Must NOT: put live region on the mutable form Column.
  Write `AuthScreenSemanticsTest.kt` with TWO separate test methods:
  - `renderBackendPane` = `AuthScreenContent(uiState = AuthUiState(error = "测试错误", tab = AuthTab.BACKEND, username = "", password = "", ...), ... { } callbacks)`. Assert `onNodeWithTag("auth_error_backend").assert(hasSemantics { SemanticsMatcher.expectValue(SemanticsProperties.Error, "测试错误"); SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive) })`. Assert `auth_error_custom` node NOT present.
  - `renderCustomPane` = same with `tab = AuthTab.CUSTOM`. Assert `auth_error_custom` node with Error+LiveRegion, `auth_error_backend` NOT present.
  Write `AuthScreenLayoutTest.kt`: `@Config(qualifiers = "w360dp-h250dp-xxhdpi")` + fontScale=2f via `CompositionLocalProvider(LocalDensity provides Density(density=1f, fontScale=2f))` — `performScrollTo()` on each form field, `assertIsDisplayed()`.
  Parallelization: Wave 2 | Blocked by: 1 | Blocks: 11,16
  References: `feature/auth/AuthScreen.kt:51-97`（AuthScreen with hiltViewModel, LaunchedEffect, Scaffold, root Column）, `:103-196`（private BackendPane, error at `:171-176`）, `:215-310`（private CustomPane, error at `:278-283`）; `AuthViewModel.kt:75-147`（11 UI methods）
  Acceptance criteria: `AuthScreenContent` is `internal` with 12 callbacks + uiState = 13 total. Each pane's error Box has distinct testTag. Each test method renders a single tab state and asserts only the expected tag's Error+LiveRegion. `verticalScroll` makes form fields reachable at compact height. No `mock()` or ViewModel in tests.
  QA scenarios: Happy — `./gradlew :feature:auth:testDebugUnitTest --tests "*AuthScreen*"` green; Failure — testTag on bare Text instead of dedicated Box wrapper → LiveRegion assertion fails on leaf node; Failure — single test tries to assert both backend AND custom error nodes from one render → one node not found since panes are mutually exclusive.
  Evidence: `/root/1codes/xinliu_android/.omo/evidence/2026-07-22-feature-ux-resilience/task-2.txt`
  Commit: Y | feat(auth): extract internal AuthScreenContent, tagged error wrappers, verticalScroll IME

- [ ] 3. Welcome screen: extract WelcomeScreenContent + verticalScroll IME + tests
  What to do: Extract `internal @Composable fun WelcomeScreenContent(onEnterLocal: () -> Unit, onGoBindServer: () -> Unit)` containing the full Scaffold + Column + InkCard + Buttons body. Production `WelcomeScreen` wrapper: wraps `onEnterLocal` as `{ viewModel.completeWelcome(); onEnterLocal() }` and `onGoBindServer` similarly. Root Column → `.verticalScroll(rememberScrollState()).imePadding()`. Write `WelcomeScreenLayoutTest.kt`: call `WelcomeScreenContent({}, {})`; `@Config(qualifiers = "w360dp-h250dp-xxhdpi")` + fontScale=2f — test-tag the "立即体验（离线）" button, `performScrollTo()`, `assertIsDisplayed()`; assert `scrollState.value > 0` after scroll. Must NOT: use `hiltViewModel()`.
  Parallelization: Wave 2 | Blocked by: 1 | Blocks: 11,16
  References: `feature/welcome/WelcomeScreen.kt`（Scaffold + Column + InkCard + Buttons, BackHandler, ViewModel only for `completeWelcome()`）
  Acceptance criteria: `WelcomeScreenContent` is `internal`. Production wrapper delegates after `completeWelcome()`. ScrollState advances and reaches bottom button at compact height.
  QA scenarios: Happy — `./gradlew :feature:welcome:testDebugUnitTest --tests "*WelcomeScreen*"` green; Failure — no `verticalScroll` modifier → `performScrollTo()` fails with node-not-scrollable; Failure — `imePadding()` missing → button occluded by simulated keyboard.
  Evidence: `/root/1codes/xinliu_android/.omo/evidence/2026-07-22-feature-ux-resilience/task-3.txt`
  Commit: Y | feat(welcome): extract internal WelcomeScreenContent, verticalScroll IME

- [ ] 4. Home FilterStatusBanner: promote to internal + merged status semantics + tests
  What to do: Change `private fun FilterStatusBanner(visible, query, tags, regexEnabled, onClearAll, onClearQuery, onEditQuery, onToggleTag)` to `internal`. Root `Surface` modifier: `Modifier.semantics { liveRegion = LiveRegionMode.Polite; contentDescription = built }` where `built` is computed from `hasQuery = query.isNotBlank()` and `hasTags = tags.isNotEmpty()`:
  - both: `"筛选：${tags.size} 标签, 搜索中"`
  - tags only: `"筛选：${tags.size} 标签"`
  - query only: `"搜索中"`
  - neither: `""`
  Write `FilterStatusBannerSemanticsTest.kt`: three test methods, each renders `FilterStatusBanner` with explicit args. Test `visible=true, query="test", tags=["work","life"]` → `composeTestRule.onNode(hasContentDescription("筛选：2 标签, 搜索中")).assertExists()`. Test tags-only: `"筛选：1 标签"`. Test query-only: `"搜索中"`. Test `visible=false` → `assertDoesNotExist()`. Must NOT: add `paneTitle`, use `announceForAccessibility`.
  Parallelization: Wave 2 | Blocked by: 1 | Blocks: 16
  References: `feature/home/HomeScreen.kt:1215-1223`（FilterStatusBanner signature）, `:1232-1234`（hasQuery/hasTags locals）
  Acceptance criteria: FilterStatusBanner is `internal`. Production HomeScreen delegates. LiveRegion=Polite + contentDescription. No `hasPaneTitle()` anywhere. Three states tested with exact string assertions.
  QA scenarios: Happy — `./gradlew :feature:home:testDebugUnitTest --tests "*FilterStatus*"` green; Failure — missing `semantics {}` block → `hasContentDescription` fails; Failure — `paneTitle` used instead of `contentDescription` → assertion fails.
  Evidence: `/root/1codes/xinliu_android/.omo/evidence/2026-07-22-feature-ux-resilience/task-4.txt`
  Commit: Y | feat(home): promote FilterStatusBanner to internal, LiveRegionMode.Polite

- [ ] 5. Todo completion control + TodoUndoHost extraction (onExpired/onUndo) + mainClock timing test
  What to do:
  **Part A — TodoDoneMark:** Change `private fun TodoDoneMark(isDone: Boolean, isRecurring: Boolean, onClick: () -> Unit)` to `internal`. Rewrite body:
  - Outer `Box(modifier = Modifier.minimumInteractiveComponentSize().defaultMinSize(minHeight = InkSpacing.TouchTargetMin, minWidth = InkSpacing.TouchTargetMin))` owns ALL interaction
  - One-off (`!isRecurring`): outer Box gets `Modifier.toggleable(value = isDone, role = Role.Checkbox, onValueChange = { onClick() }).semantics { stateDescription = if(isDone) "已完成" else "未完成" }` — `toggleable` sets `ToggleableState` automatically; `stateDescription` is custom
  - Recurring (`isRecurring`): outer Box gets `Modifier.clickable(role = Role.Button, onClick = { onClick() }).semantics { contentDescription = "完成下次循环任务" }`
  - Inner 30dp `Surface`: `Modifier.size(InkSpacing.TodoStatusIconSize).clearAndSetSemantics {}` — remove old `.clickable()`, `interactionSource`, `contentDescription`. Inner icon also `clearAndSetSemantics`
  - Exactly ONE interactive semantics node per control (the outer Box)
  **Part B — TodoUndoHost:** Extract `internal @Composable fun TodoUndoHost(deletedItemId: String?, onUndo: (String) -> Unit, onExpired: (String) -> Unit, modifier: Modifier = Modifier)`. Internal state: `var visibleId by remember { mutableStateOf<String?>(null) }`. `LaunchedEffect(deletedItemId)`: when `deletedItemId` != null, set `visibleId = deletedItemId`; within `LaunchedEffect`, `delay(6_000)`; at expiry, capture `val expiredId = deletedItemId`, then if `visibleId` still equals `expiredId` (not cancelled by undo and not superseded by a newer delete), clear `visibleId` and call `onExpired(expiredId)`. Manual undo: when user clicks 撤销 button, `visibleId?.let { id -> visibleId = null; onUndo(id) }` — smart-cast ensures `id: String` before invoking `onUndo`, and clearing occurs before the callback. Render when `visibleId != null`: InkCard with `Modifier.semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "已删除 1 项，可撤销" }`, "已删除 1 项" Text, and "撤销" TextButton.
  Production integration: `TodoScreen` sets `lastDeletedItemId` on delete, passes it as `deletedItemId` to `TodoUndoHost`. `onExpired` clears `lastDeletedItemId` if it still matches (enables same-id re-delete after expiry). `onUndo` calls `viewModel.restoreItem(id)` and clears `lastDeletedItemId` if it still matches.
  **Tests:** `TodoCompletionSemanticsTest.kt` — render `TodoDoneMark(isDone = true, isRecurring = false) {}`, assert outer Box has `Role.Checkbox` + `ToggleableState.On` + `stateDescription = "已完成"`; height/width ≥ TouchTargetMin; exactly 1 `OnClick` node. Render recurring `TodoDoneMark(isDone = false, isRecurring = true) {}` → `Role.Button` + `contentDescription = "完成下次循环任务"`. `TodoUndoMainClockTest.kt` — render `TodoUndoHost("test-1", onUndo = { tracked += "undo" }, onExpired = { tracked += "expired" })`):
  - `mainClock.autoAdvance = false`; `mainClock.advanceTimeByFrame()` → assert undo InkCard displayed
  - `mainClock.advanceTimeBy(5_999L, ignoreFrameDuration = true)` → assert still displayed
  - `mainClock.advanceTimeBy(1L, ignoreFrameDuration = true)` → trigger transition past 6 000ms
  - `mainClock.advanceTimeByFrame(); waitForIdle()` → assert `assertDoesNotExist()` and tracked = "expired"
  - Manual undo: trigger delete, `mainClock.advanceTimeByFrame()`, click 撤销 → assert tracked = "undo", NOT "expired"
  - id1→id2: render with id1 → partial advance, re-render with id2 → assert visibleId reset to id2, timer restarts, onExpired fires for id2 not id1
  - Same-id after expiry: render with id1, wait full expiry → onExpired(id1). Re-render with id1 → new timer starts, undo displayed
  Parallelization: Wave 2 | Blocked by: 1 | Blocks: 9,16
  References: `feature/todo/TodoItemRow.kt:108-116`（calling context）, `:195-230`（TodoDoneMark）; `feature/todo/TodoScreen.kt:140-180`（undo state + six-second LaunchedEffect）, `:396-430`（undo InkCard + TextButton）; `InkSpacing.TouchTargetMin = 48.dp`
  Acceptance criteria: TodoDoneMark internal, 48dp outer owns toggleable/clickable, inner 30dp + icon clearAndSetSemantics. TodoUndoHost internal with visibleId state machine. Production delegates. Undo: 5 999ms displayed, one frame after 6 000ms transition gone. Manual undo cancels expiry. id1→id2 resets timer. Same-id re-delete after expiry works.
  QA scenarios: Happy — `./gradlew :feature:todo:testDebugUnitTest --tests "*TodoDoneMark*" --tests "*TodoUndoMainClock*"` green; Failure — missing `ignoreFrameDuration = true` → `advanceTimeBy(5_999L)` rounds up to next frame boundary → undo disappears too early; Failure — inner Surface retains old `.clickable()` → duplicate `OnClick` nodes; Failure — `onExpired` not called → parent `lastDeletedItemId` leaks → same-id re-delete impossible.
  Evidence: `/root/1codes/xinliu_android/.omo/evidence/2026-07-22-feature-ux-resilience/task-5.txt`
  Commit: Y | feat(todo): promote TodoDoneMark to internal, extract TodoUndoHost with onExpired/onUndo, mainClock timing test

- [ ] 6. QuickCapture: promote to internal + error/draft live regions + 48dp outer combinedClickable + scroll + tests
  What to do: Convert `private fun QuickCaptureScreen(uiState: QuickCaptureUiState, focusRequester: FocusRequester, onClose: () -> Unit, onContentChange: (TextFieldValue) -> Unit, onInsertTime: () -> Unit, onSave: () -> Unit, onEditPrevious: () -> Unit, onRefreshHistory: () -> Unit, onLoadForEdit: (String) -> Unit, onRestoreDraft: () -> Unit, onClearDraft: () -> Unit, onConfirmOverwrite: () -> Unit, onDismissOverwrite: () -> Unit, showStamp: Boolean)` to `internal`. Convert `private fun QuickCaptureTextAction(text: String, enabled: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?)` to `internal`. Production `QuickCaptureRoute` keeps Hilt and delegates.
  Wrap conditional error `Text` in `Box(modifier = Modifier.semantics { error(uiState.error.orEmpty()); liveRegion = LiveRegionMode.Assertive }.testTag("qc_error"))`. Draft banner `Row` → `Modifier.semantics { liveRegion = LiveRegionMode.Polite }`.
  Restructure `QuickCaptureTextAction`: outer `Box(modifier = Modifier.minimumInteractiveComponentSize().defaultMinSize(minHeight = InkSpacing.TouchTargetMin, minWidth = InkSpacing.TouchTargetMin).combinedClickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick, onLongClick = onLongClick ?: {}).semantics { contentDescription = text })`. Inner `Text` decorative: remove old `.combinedClickable` and `interactionSource`, keep padding/styling, apply `Modifier.clearAndSetSemantics {}`. Content `Column` at `:180` gets `.verticalScroll(rememberScrollState()).imePadding()`.
  Write `QuickCaptureSemanticsTest.kt`: on node `"qc_error"`, assert `SemanticsMatcher.expectValue(SemanticsProperties.Error, "测试模拟错误")` + `SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive)`; draft Row LiveRegion=Polite; outer action Box height/width ≥ TouchTargetMin, OnClick+OnLongClick, contentDescription=action text; exactly 1 interactive node per action. Write `QuickCaptureLayoutTest.kt`: `@Config(qualifiers = "w360dp-h250dp-xxhdpi")` + fontScale=2f — tag save button, `performScrollTo()`, `assertIsDisplayed()`.
  Parallelization: Wave 2 | Blocked by: 1 | Blocks: 13,16
  References: `feature/quickcapture/QuickCaptureScreen.kt:67-113`（QuickCaptureRoute）, `:131-362`（private QuickCaptureScreen 14 params, scope exit to end of composable）, `:420-443`（private QuickCaptureTextAction 4 params）; `InkSpacing.TouchTargetMin`
  Acceptance criteria: QuickCaptureScreen + QuickCaptureTextAction internal with exact 14/4 params. Error Box has testTag "qc_error". `expectValue(Error/LiveRegion)` on tagged node. Outer Box ≥ TouchTargetMin owns combinedClickable+label. Inner Text clearAndSetSemantics. Scroll reaches save at compact height.
  QA scenarios: Happy — `./gradlew :feature:quickcapture:testDebugUnitTest --tests "*QuickCapture*"` green; Failure — inner Text retains old `combinedClickable()` → duplicate `OnClick`/`OnLongClick` nodes; Failure — `imePadding()` missing → button unreachable under IME proxy.
  Evidence: `/root/1codes/xinliu_android/.omo/evidence/2026-07-22-feature-ux-resilience/task-6.txt`
  Commit: Y | feat(quickcapture): promote QuickCaptureScreen/QuickCaptureTextAction to internal, tagged error, 48dp outer, scroll+IME

- [ ] 7. Profile heatmap: InkCard contentPadding + promote to internal + 336dp width fix + 48dp cells + tests
  What to do: Current baseline: `ProfileScreen.kt:115-118` LazyColumn `PaddingValues(horizontal = InkSpacing.X16)` = 32dp total. `InkCard.kt:118` default `PaddingValues(InkSpacing.CardPadding)` = X14×2 = 28dp total. At W360, HeatmapGrid receives 360−32−28 = 300dp. `computed = 300/7 = 42.86dp`, coerced to `CalendarCellMin` 44dp → 7×44 = 308dp which exceeds 300dp — already overflowing.
  Fix in five steps:
  1. Extend `core/designsystem/.../InkCard.kt`: add parameter `contentPadding: PaddingValues = PaddingValues(InkSpacing.CardPadding)`, update line 118 to use it. Default for all 78 existing callers unchanged. Write `core/designsystem/src/test/.../InkCardContentPaddingTest.kt`: assert default padding still applies, custom zero-horizontal padding yields correct width.
  2. Extract `internal @Composable fun ProfileScreenContent(uiState: ProfileUiState, onOpenDrawer: () -> Unit, onOpenMemo: (String) -> Unit, onSetMonth: (YearMonth) -> Unit, onPrevMonth: () -> Unit, onNextMonth: () -> Unit, onGoToToday: () -> Unit, onSelectSingle: (LocalDate) -> Unit, onStartRange: (LocalDate) -> Unit, onUpdateRange: (LocalDate) -> Unit)` — 9 callbacks + `uiState` = 10 total parameters. Production `ProfileScreen` wrapper: collects `uiState` via `collectAsStateWithLifecycle()`, wraps ViewModel methods in haptic callbacks, delegates.
  3. In ProfileScreenContent, remove horizontal from LazyColumn `ContentPadding`（keep `vertical = InkSpacing.X14`）— recovers 32dp.
  4. Heatmap InkCard call: `Modifier.padding(horizontal = InkSpacing.X12)`, `contentPadding = PaddingValues(vertical = InkSpacing.CardPadding)` — zero horizontal content padding. Card outer margin = 24dp. Content width at W360: 360−24 = 336dp. Cell size: 336/7 = 48dp, coerceIn(44,56) = 48dp. Seven cells = 336dp, disjoint x-axis.
  5. Inside heatmap card, wrap `MonthHeader` + `HeatmapLegend` with `Modifier.padding(horizontal = InkSpacing.CardPadding)`. Leave `HeatmapGrid` full-width. Apply `Modifier.padding(horizontal = InkSpacing.X16)` wrappers to every other LazyColumn item.
  Promote `private fun HeatmapGrid(...)` and `private fun HeatmapCell(...)` to `internal`. Cell outer Box: `Modifier.size(cellSize).minimumInteractiveComponentSize().semantics { onClick(label="选择 ${date.monthValue}月${date.dayOfMonth}日", action={ onTapDate(date) }); contentDescription = "${date.monthValue}月${date.dayOfMonth}日，${count} 篇${if(selected) "，已选中" else ""}" }`. Grid centered via `wrapContentWidth().align(CenterHorizontally)`. No horizontalScroll. Parent `pointerInput` drag preserved.
  Tests via internal entries:
  - `ProfileHeatmapBoundsTest.kt`: `@Config(qualifiers = "w360dp-h640dp-xxhdpi")` → all 7 cell outer Box `boundsInRoot` within heatmap card bounds, x-axis mutually disjoint
  - `ProfileHeatmapSemanticsTest.kt`: `performSemanticsAction(OnClick)` per cell fires `onTapDate`; contentDescription format correct; cells ≥ TouchTargetMin
  - `ProfileHeatmapDragTest.kt`: `performTouchInput { swipe }` → multi-date drag selection preserved
  - `InkCardContentPaddingTest.kt` (core/designsystem): default padding unchanged
  - `ProfileNonHeatmapMarginTest.kt`: non-heatmap cards ≥ X16 margin
  Parallelization: Wave 2 | Blocked by: 1 | Blocks: 12,16
  References: `ProfileScreen.kt:71-187`（ProfileScreen, haptic at lines 80-83）, `:115-118`（LazyColumn ContentPadding）, `:288-433`（private HeatmapGrid）, `:435-480`（private HeatmapCell）, `:298-328`（pointerInput drag）; `InkCard.kt:118`; `ProfileViewModel.kt:185-214`（7 ViewModel methods）; `InkSpacing.CardPadding=X14`, `CalendarCellMin=44dp`, `CalendarCellMax=56dp`, `TouchTargetMin=48dp`
  Acceptance criteria: ProfileScreenContent + HeatmapGrid + HeatmapCell internal. InkCard default unchanged for 78 callers. At W360, heatmap 336dp content width, 7 cells disjoint, non-heatmap ≥ X16. Drag preserved.
  QA scenarios: Happy — `./gradlew :core:designsystem:testDebugUnitTest --tests "*InkCard*"` + `./gradlew :feature:profile:testDebugUnitTest --tests "*Profile*"` all green; Failure — cells x-axis overlap → bounds test fails; Failure — default padding drift → InkCardDefaultPaddingTest fails.
  Evidence: `/root/1codes/xinliu_android/.omo/evidence/2026-07-22-feature-ux-resilience/task-7.txt`
  Commit: Y | feat(designsystem,profile): add InkCard contentPadding param, promote ProfileScreenContent/HeatmapGrid/HeatmapCell to internal, fix heatmap to 336dp

- [ ] 8. ShareCard: promote to internal ShareCardScreenContent + verticalScroll + FlowRow + Y-diff test
  What to do: Extract `internal @Composable fun ShareCardScreenContent(uiState: ShareCardUiState, selectedTabIndex: Int, onTabSelected: (Int) -> Unit, onBack: () -> Unit, onSaveToGallery: () -> Unit, onShare: () -> Unit, onThemeSelected: (ShareCardTheme) -> Unit, onRatioSelected: (ShareCardRatio) -> Unit, onFontSizeSelected: (ShareCardFontSize) -> Unit, onAlignSelected: (ShareCardAlign) -> Unit, onLongModeChanged: (Boolean) -> Unit, onLongExportModeChanged: (ShareCardLongExportMode) -> Unit, onAuthorNameChanged: (String) -> Unit, onQrEnabledChanged: (Boolean) -> Unit, onQrTextChanged: (String) -> Unit)` — 13 callbacks + `uiState` + `selectedTabIndex` = 15 total parameters.
  Production `ShareCardScreen` wrapper: keeps Hilt, Context, Toast, export/share intents (Intent/ClipData/startActivity), `LaunchedEffect(exportError)` for toast display, and delegates to `ShareCardScreenContent`.
  Inside content body: root `Column` → `.verticalScroll(rememberScrollState())`. Replace fixed `Row` blocks in StylesPanel (`:280,295,310`) and MorePanel's long-export-mode Row (`:372`) with `FlowRow(horizontalArrangement = Arrangement.spacedBy(InkSpacing.X10))` — add `@OptIn(ExperimentalLayoutApi::class)` import. Convert private panels (ThemesPanel, StylesPanel, MorePanel) to callback-based (no ViewModel reference).
  Write `ShareCardLayoutTest.kt`: call `ShareCardScreenContent` with deterministic `ShareCardUiState` fixture + `selectedTabIndex = 0` + `{}` callbacks. `@Config(qualifiers = "w360dp-h250dp-xxhdpi")` + fontScale=2f. Test-tag each FlowRow and its chips; for widest FlowRow, assert ≥2 chips have differing `boundsInRoot.top` (proves line wrapping). `selectedTabIndex = 2`, `performScrollTo()` on MorePanel content, `assertIsDisplayed()`.
  Parallelization: Wave 2 | Blocked by: 1 | Blocks: 14,16
  References: `feature/sharecard/ShareCardScreen.kt:60-226`（ShareCardScreen with Context/Toast/Intents/export/share/LaunchedEffect）, `:240-396`（private panels with fixed Rows）; `ShareCardViewModel.kt:56-424`（11 UI methods）
  Acceptance criteria: ShareCardScreenContent internal (15 total params). Production wrapper delegates, side effects preserved. Root scrollable. FlowRow Y-diff wrapping proven. Scroll reaches last tab. `@OptIn(ExperimentalLayoutApi::class)` import present.
  QA scenarios: Happy — `./gradlew :feature:sharecard:testDebugUnitTest --tests "*ShareCard*"` green; Failure — Row not replaced → all chips same Y → wrapping assertion fails; Failure — no `@OptIn` → compiler error.
  Evidence: `/root/1codes/xinliu_android/.omo/evidence/2026-07-22-feature-ux-resilience/task-8.txt`
  Commit: Y | refactor(sharecard): extract internal ShareCardScreenContent, verticalScroll + FlowRow with @OptIn

- [ ] 9. Todo layout resilience: LazyListScope seam + sibling-bounds overlap + bounded fix
  What to do: Extract `internal @Composable fun TodoItemsList(modifier: Modifier = Modifier, content: LazyListScope.() -> Unit)`. Body: `LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(InkSpacing.X10), content = content)`. Production at `TodoScreen.kt:284`: replace the `LazyColumn(verticalArrangement = ..., modifier = Modifier.fillMaxSize()) { ... }` block with `TodoItemsList(modifier = Modifier.fillMaxSize()) { /* entire existing LazyListScope body unchanged */ }`. The `ScrollPaperSurface`, `paperScrollConnection`, and `paperScrollOffsetPx` remain outside and unchanged. All existing content (permission banner, filters, empty state, section headers, `items(section.items) { TodoItemRow }`) stays in the LazyListScope lambda.
  Write `TodoLayoutResilienceTest.kt`: create two fixture `TodoItemPresentation` instances:
  ```kotlin
  val f1 = TodoItemPresentation(item = TodoItem(id = "todo-1", listId = "list-1", title = "事项一", updatedAt = "1970-01-01T00:00:00Z"), isDone = false, effectiveDueAtLocal = null, effectiveDueLocal = null, reminderSummary = null, nowDate = LocalDate.of(2026, 7, 22))
  val f2 = f1.copy(item = f1.item.copy(id = "todo-2", title = "事项二"))
  ```
  Render: `TodoItemsList(modifier = Modifier.fillMaxSize()) { items(listOf(f1, f2), key = { it.item.id }) { p -> TodoItemRow(p = p, listName = null, onOpen = {}, onToggleDone = {}, onDelete = {}, onTagClick = {}, onStamp = {}, modifier = Modifier.testTag("todo_row_${p.item.id}")) } }`. Apply `@Config(qualifiers = "w360dp-h250dp-xxhdpi")` + fontScale=2f. Get `onNodeWithTag("todo_row_todo-1")` and `"todo_row_todo-2"`, compare `boundsInRoot` → assert no vertical overlap. Exclude parent-child and intentional overlays (undo bar).
  If overlap found: ONLY permitted fix is `Modifier.fillMaxWidth()` on item card lacking it, OR `horizontal` padding from `InkSpacing.X12` to `minOf(InkSpacing.X12, 4.dp)`. If no overlap, NO CODE CHANGE to TodoScreen.kt (only seam extraction + test file).
  Parallelization: Wave 2 | Blocked by: 5 | Blocks: 16
  References: `feature/todo/TodoScreen.kt:280-397`（LazyColumn with permission banner, filters, empty state, section headers, TodoItemRow）; `feature/todo/TodoGrouping.kt`（TodoItemPresentation: item, isDone, effectiveDueAtLocal, effectiveDueLocal, reminderSummary, nowDate）; `core/model/.../TodoItem.kt`（id, listId, title, updatedAt fields）
  Acceptance criteria: `TodoItemsList` is `internal`, owning only LazyColumn. Production list content intact (permission/filter/empty/sections/items preserved). Two fixture rows' bounds tested, no vertical overlap. Bounded diff or empty. No `TodoItemWithListName` or `paperInkScrollAware` anywhere.
  QA scenarios: Happy — `./gradlew :feature:todo:testDebugUnitTest --tests "*TodoLayoutResilience*"` green; Failure — overlap detected but fix exceeds bounded modifier scope → STOP, log evidence; Failure — missing fixture fields → `TodoItem` constructor mismatch → compiler error.
  Evidence: `/root/1codes/xinliu_android/.omo/evidence/2026-07-22-feature-ux-resilience/task-9.txt`
  Commit: Y | feat(todo): extract TodoItemsList LazyListScope seam, sibling-bounds test with real TodoItemPresentation fixture

- [ ] 10. Collections layout resilience: promote CollectionItemCard + LazyColumn content-lambda seam + sibling-bounds test
  What to do: Change `private fun CollectionItemCard(item: CollectionItem, noteRefTargetId: String?, noteRefMemo: Memo?, enableRichPreview: Boolean, showAutoTagLineInHome: Boolean, autoTagKeywords: List<String>, selectedTags: Set<String>, onToggleTag: ((String) -> Unit)?, selected: Boolean, selectionMode: Boolean, reorderMode: Boolean, canMoveUp: Boolean, canMoveDown: Boolean, onMoveUp: () -> Unit, onMoveDown: () -> Unit, onClick: () -> Unit, onLongClick: () -> Unit)` to `internal`, adding `modifier: Modifier = Modifier` as first parameter. Pass it to the root `InkCard(modifier = modifier, ...)`. Default unchanged → all 78 production callers unaffected.
  Extract `internal @Composable fun CollectionsItemsList(items: List<CollectionItem>, state: LazyListState, modifier: Modifier = Modifier, itemContent: @Composable LazyItemScope.(CollectionItem) -> Unit)`. Body: `LazyColumn(modifier = modifier, state = state, verticalArrangement = Arrangement.spacedBy(InkSpacing.X10), contentPadding = PaddingValues(bottom = InkSpacing.X12)) { items(items, key = { it.id }, itemContent = itemContent) }`. Production passes `Modifier.fillMaxWidth().nestedScroll(paperScrollConnection)` as the `modifier` parameter, and passes the full existing per-item composable (which constructs a `CollectionItemCard` with 17 params + state-derived values) as `itemContent`.
  Write `CollectionsLayoutResilienceTest.kt`: construct two FOLDER fixture `CollectionItem` with all 14 fields:
  ```kotlin
  val f1 = CollectionItem(id = "folder-1", itemType = CollectionItemType.FOLDER, parentId = null, name = "收藏夹A", color = null, refType = null, refId = null, sortOrder = 0, clientUpdatedAtMs = 0L, createdAt = "2026-01-01", updatedAt = "2026-01-01", deletedAt = null, localOnly = false, refLocalUuid = null)
  val f2 = f1.copy(id = "folder-2", name = "收藏夹B")
  ```
  Render: `CollectionsItemsList(items = listOf(f1, f2), state = rememberLazyListState(), modifier = Modifier.testTag("col_list"), itemContent = { item -> CollectionItemCard(modifier = Modifier.testTag("card_${item.id}"), item = item, noteRefTargetId = null, noteRefMemo = null, enableRichPreview = false, showAutoTagLineInHome = false, autoTagKeywords = emptyList(), selectedTags = emptySet(), onToggleTag = null, selected = false, selectionMode = false, reorderMode = false, canMoveUp = false, canMoveDown = false, onMoveUp = {}, onMoveDown = {}, onClick = {}, onLongClick = {}) })`. Now 18 total card params (17 original + 1 new `modifier`).
  `@Config(qualifiers = "w360dp-h250dp-xxhdpi")` + fontScale=2f. Get `onNodeWithTag("card_folder-1")` and `"card_folder-2"`, compare `boundsInRoot` → assert no vertical overlap. Same bounded fixes as Todo 9. If no overlap, NO CODE CHANGE to CollectionsScreen.kt.
  Parallelization: Wave 2 | Blocked by: 1 | Blocks: 16
  References: `feature/collections/.../CollectionsScreen.kt:555-570`（LazyColumn with `fillMaxWidth().nestedScroll(paperScrollConnection)`, spacedBy X10, contentPadding bottom X12）; `:873-1270`（private CollectionItemCard 17-param signature）; `core/model/.../CollectionItem.kt:13-26`（14 data class fields）
  Acceptance criteria: CollectionItemCard internal with modifier (18 total params). CollectionsItemsList internal with content-lambda, modifier passed through verbatim. Production passes `nestedScroll` modifier. Two 14-field fixtures with all 18 card params. Bounds no overlap. No Hilt in tests.
  QA scenarios: Happy — `./gradlew :feature:collections:testDebugUnitTest --tests "*Collections*"` green; Failure — missing `CollectionItem` field → compiler error; Failure — fix exceeds bounds → STOP.
  Evidence: `/root/1codes/xinliu_android/.omo/evidence/2026-07-22-feature-ux-resilience/task-10.txt`
  Commit: Y | feat(collections): promote CollectionItemCard with modifier, add CollectionsItemsList content-lambda seam, full-fixture sibling-bounds test

- [ ] 11. Auth + Welcome screenshot matrices（Roborazzi, 16 goldens total）
  What to do: Write `feature/auth/src/test/.../AuthScreenRoborazziTest.kt` and `feature/welcome/src/test/.../WelcomeScreenRoborazziTest.kt`. Call `AuthScreenContent` / `WelcomeScreenContent` directly with deterministic state and `{}` callbacks. Use `RobolectricTestRunner`, `@GraphicsMode(GraphicsMode.Mode.NATIVE)`, `@Config(sdk = [34], application = Application::class)` + qualifiers, `RoborazziRule`, `captureRoboImage()` — pattern from `feature/home/src/test/.../MemoItemScreenshotTest.kt`.
  Qualifier matrix per screen (same 4 profiles × 2 themes = 8 each):
  | Profile | `@Config(qualifiers = ...)` | Theme |
  | --- | --- | --- |
  | compact | `"w360dp-h640dp-xxhdpi"` | light + dark |
  | expanded | `"w600dp-h840dp-xxhdpi"` | light + dark |
  | compact-height (IME proxy) | `"w360dp-h250dp-xxhdpi"` | light + dark |
  | large-font | `"w360dp-h640dp-xxhdpi"` + `CompositionLocalProvider(LocalDensity provides Density(density=1f, fontScale=2f))` | light + dark |
  Auth deterministic state per golden: `AuthUiState(error = "测试用错误消息", tab = AuthTab.BACKEND, username = "testuser", password = "", ...)` + other fields defaulted. Tab is controlled by `AuthUiState.tab` (no `selectedTabIndex` in AuthUiState). This captures BACKEND pane with visible error. Welcome uses `WelcomeScreenContent({}, {})`.
  Parallelization: Wave 3 | Blocked by: 2,3 | Blocks: 15,16
  References: `feature/home/src/test/.../MemoItemScreenshotTest.kt`（Robolectric runner, NATIVE graphics, qualifiers, RoborazziRule, captureRoboImage pattern）; internal AuthScreenContent, WelcomeScreenContent
  Acceptance criteria: `./gradlew :feature:auth:recordRoborazziDebug` produces 8 non-empty PNGs; `./gradlew :feature:auth:verifyRoborazziDebug` passes. Same for `:feature:welcome`. 16 total PNGs.
  QA scenarios: Happy — both verify green; Failure — missing Roborazzi deps → `recordRoborazziDebug` task not found; Failure — pixel diff → investigate, record-update if intentional.
  Evidence: `/root/1codes/xinliu_android/.omo/evidence/2026-07-22-feature-ux-resilience/task-11.txt` + PNG listing
  Commit: Y | test(auth,welcome): Roborazzi 8+8 goldens via internal content entries

- [ ] 12. Profile screenshot matrix（Roborazzi, 8 goldens, real-field fixture）
  What to do: Write `feature/profile/src/test/.../ProfileScreenRoborazziTest.kt`. Call `ProfileScreenContent` with deterministic fixture and explicit named callbacks:
  ```kotlin
  val month = YearMonth.of(2026, 7)
  val today = LocalDate.of(2026, 7, 22)
  // gridStart = first day of month minus (dayOfWeek.value - 1) days = aligns to Monday
  val gridStart = month.atDay(1).minusDays((month.atDay(1).dayOfWeek.value - 1).toLong()) // = 2026-06-29
  val heatmap = HeatmapUiModel(month = month, activeStart = month.atDay(1), activeEnd = month.atEndOfMonth(), gridStart = gridStart, rows = 5, counts = mapOf(today to 3, today.minusDays(1) to 1), maxCount = 3)
  val selection = DateRangeSelection(anchor = today, current = today)
  val state = ProfileUiState(heatmap = heatmap, month = month, selection = selection, sections = emptyList(), selectedMemoCount = 0)
  ProfileScreenContent(state, onOpenDrawer = {}, onOpenMemo = {}, onSetMonth = {}, onPrevMonth = {}, onNextMonth = {}, onGoToToday = {}, onSelectSingle = {}, onStartRange = {}, onUpdateRange = {})
  ```
  All 9 callbacks named explicitly — no `(state, {}, {})`. Same 4-profile × 2-theme matrix = 8 goldens.
  Parallelization: Wave 3 | Blocked by: 7 | Blocks: 15,16
  References: `feature/profile/.../ProfileModels.kt`（HeatmapUiModel: month, activeStart, activeEnd, gridStart, rows, counts, maxCount; DateRangeSelection: anchor, current; ProfileUiState: heatmap, month, selection, sections, selectedMemoCount）; `feature/home/.../MemoItemScreenshotTest.kt`
  Acceptance criteria: `./gradlew :feature:profile:verifyRoborazziDebug` green, 8 PNGs. gridStart = 2026-06-29. All 9 callbacks named. No `mock`.
  QA scenarios: Happy — verify green; Failure — gridStart = 2026-07-01 → visual offset caught by golden diff; Failure — missing `heatmap` field → compiler error.
  Evidence: `/root/1codes/xinliu_android/.omo/evidence/2026-07-22-feature-ux-resilience/task-12.txt`
  Commit: Y | test(profile): Roborazzi 8 goldens with real-field fixture and correct gridStart

- [ ] 13. QuickCapture screenshot matrix（Roborazzi, 8 goldens, real-field fixture）
  What to do: Write `feature/quickcapture/src/test/.../QuickCaptureScreenRoborazziTest.kt`. Call `QuickCaptureScreen` with deterministic `QuickCaptureUiState` and a `FocusRequester()`. Deterministic state-to-profile mapping:
  | Profile | State | QuickCaptureUiState fields |
  | --- | --- | --- |
  | compact (light+dark) | normal | defaults (content=TextFieldValue("测试速记"), error=null, draftBannerVisible=false, draftOverwriteDialogVisible=false) |
  | expanded (light+dark) | normal | same as compact |
  | compact-height (light+dark) | error | `error = "模拟保存失败"`, `draftBannerVisible = false`, others defaulted |
  | large-font (light+dark) | draft banner | `draftBannerVisible = true`, `draftOverwriteDialogVisible = false`, `error = null`, others defaulted |
  Never use nonexistent `draft` field. Same Roborazzi infrastructure as other screenshot tests. 8 goldens total.
  Parallelization: Wave 3 | Blocked by: 6 | Blocks: 15,16
  References: `feature/quickcapture/.../QuickCaptureViewModel.kt`（QuickCaptureUiState: content, isSaving, error, editingUuid, hiddenAutoTagLines, history, quickInsertTimeEnabled, quickInsertTimeFormat, draftBannerVisible, draftOverwriteDialogVisible）; `feature/home/.../MemoItemScreenshotTest.kt`
  Acceptance criteria: `./gradlew :feature:quickcapture:verifyRoborazziDebug` green, 8 PNGs. No `draft = true` anywhere.
  QA scenarios: Happy — verify green; Failure — nonexistent field → compiler error; Failure — task not found or pixel diff.
  Evidence: `/root/1codes/xinliu_android/.omo/evidence/2026-07-22-feature-ux-resilience/task-13.txt`
  Commit: Y | test(quickcapture): Roborazzi 8 goldens with real-field fixture

- [ ] 14. ShareCard screenshot matrix（Roborazzi, 8 goldens, deterministic fixture）
  What to do: Write `feature/sharecard/src/test/.../ShareCardScreenRoborazziTest.kt`. Call `ShareCardScreenContent`. Deterministic `ShareCardUiState` fixture with fixed fields: content = "测试墨迹内容", createdAt = 1_784_678_400_000L, tags = listOf("灵感", "日记"), authorName = "测试作者", theme = ShareCardTheme.SU_LV, ratio = ShareCardRatio.AUTO, fontSize = ShareCardFontSize.MEDIUM, align = ShareCardAlign.LEFT, longMode = false, longExportMode = ShareCardLongExportMode.SINGLE, qrEnabled = false, qrText = "", ... (all other fields defaulted). State-to-profile mapping:
  | Profile | Theme | selectedTabIndex | Content focus |
  | --- | --- | --- | --- |
  | compact (light+dark) | light + dark | 0 | Theme tab (ThemesPanel visible) |
  | expanded (light+dark) | light + dark | 0 | Theme tab |
  | compact-height (light+dark) | light + dark | 1 | Styles tab (StylesPanel with FlowRow wrapping visible) |
  | large-font (light+dark) | light + dark | 2 | More tab (MorePanel with long switches visible) |
  Same Roborazzi infrastructure. 8 goldens total.
  Parallelization: Wave 3 | Blocked by: 8 | Blocks: 15,16
  References: internal ShareCardScreenContent (15 total params); `feature/home/.../MemoItemScreenshotTest.kt`
  Acceptance criteria: `./gradlew :feature:sharecard:verifyRoborazziDebug` green, 8 PNGs. Tab-to-profile mapping covers all three tabs.
  QA scenarios: Happy — verify green; Failure — task not found or pixel diff.
  Evidence: `/root/1codes/xinliu_android/.omo/evidence/2026-07-22-feature-ux-resilience/task-14.txt`
  Commit: Y | test(sharecard): Roborazzi 8 goldens with deterministic tab-to-profile mapping

- [ ] 15. verify.sh gate expansion（18 exact Gradle calls）
  What to do: In `scripts/verify.sh`, append the following 18 Gradle command lines AFTER the existing `:feature:collections:testDebugUnitTest` line. Each line is self-contained with `"${common_args[@]}" "$@"`:
  ```bash
  # 7 missed existing unit tests (modules with existing src/test/)
  ./gradlew :core:data:testDebugUnitTest "${common_args[@]}" "$@"
  ./gradlew :core:designsystem:testDebugUnitTest "${common_args[@]}" "$@"
  ./gradlew :core:domain:test "${common_args[@]}" "$@"                      # JVM (kotlin.jvm), not Android :testDebugUnitTest
  ./gradlew :core:sync:testDebugUnitTest "${common_args[@]}" "$@"
  ./gradlew :feature:editor:testDebugUnitTest "${common_args[@]}" "$@"
  ./gradlew :feature:profile:testDebugUnitTest "${common_args[@]}" "$@"
  ./gradlew :feature:todo:testDebugUnitTest "${common_args[@]}" "$@"
  # 4 new feature unit tests (added by this plan)
  ./gradlew :feature:auth:testDebugUnitTest "${common_args[@]}" "$@"
  ./gradlew :feature:welcome:testDebugUnitTest "${common_args[@]}" "$@"
  ./gradlew :feature:quickcapture:testDebugUnitTest "${common_args[@]}" "$@"
  ./gradlew :feature:sharecard:testDebugUnitTest "${common_args[@]}" "$@"
  # 7 Roborazzi verify tasks
  ./gradlew :core:designsystem:verifyRoborazziDebug "${common_args[@]}" "$@"
  ./gradlew :feature:home:verifyRoborazziDebug "${common_args[@]}" "$@"
  ./gradlew :feature:auth:verifyRoborazziDebug "${common_args[@]}" "$@"
  ./gradlew :feature:welcome:verifyRoborazziDebug "${common_args[@]}" "$@"
  ./gradlew :feature:quickcapture:verifyRoborazziDebug "${common_args[@]}" "$@"
  ./gradlew :feature:profile:verifyRoborazziDebug "${common_args[@]}" "$@"
  ./gradlew :feature:sharecard:verifyRoborazziDebug "${common_args[@]}" "$@"
  ```
  Existing baseline calls NOT duplicated: `:feature:home:testDebugUnitTest`, `:feature:collections:testDebugUnitTest`. Total new calls: 7+4+7 = 18. Baseline: 16 calls. New total: 34 Gradle calls. Must NOT: add `--stop` or extra logic between these lines; follow the existing pattern of other test calls.
  Parallelization: Wave 4 | Blocked by: 2-14 | Blocks: 16
  References: `scripts/verify.sh`（existing 16 calls pattern; `common_args` bash array; `--stop` before benchmark section）
  Acceptance criteria: `./scripts/verify.sh --stacktrace` → exit 0. All 18 new tasks execute. No "task not found" for any added call. `:core:domain:test` correctly uses JVM task name.
  QA scenarios: Happy — `./scripts/verify.sh --stacktrace` exits 0; Failure — any new task "not found" → verify module's `build.gradle.kts` has correct test deps from Todo 1; Failure — Roborazzi golden diff → investigate, re-record if intentional.
  Evidence: `/root/1codes/xinliu_android/.omo/evidence/2026-07-22-feature-ux-resilience/task-15.txt`
  Commit: Y | ci(verify): add 18 new test/verify tasks to gate

- [ ] 16. Pre-release readiness audit: merge to main, Prepare stage, full gate, inline checks
  What to do:
  1. In independent clone at `../xinliu_android-uxresil`: `git switch main && git merge --ff-only ux-resilience-1.17.0`. This creates a clean linear `main` — required by `release-publish.ps1`.
  2. Update `.ai_session.md` with plan slug, C1-C5 scope summary, version 1.17.0 (169), date. `main` has only `.ai_session.md` as dirty file.
  3. Run `./scripts/verify.sh --stacktrace` → must exit 0.
  4. Run `./scripts/verify.sh --stacktrace` a second time → must exit 0 (test idempotency).
  5. Inline pre-release audit checklist (all must PASS):
  - **Compliance:** All 18 todos → diffs. No Scope OUT leaks. No private composable calls from tests. No Hilt/`mock()`. No forbidden API. No fictional types/modifiers. All screen tests use `internal` entries.
  - **Quality:** One `imePadding()` per scrollable root. `clearAndSetSemantics` on decorative children. `mainClock` with `ignoreFrameDuration=true`. All test entries `internal` with exact signatures + `{}` stubs. `SemanticsMatcher.expectValue` for Error (String) + LiveRegion. No `TestCoroutineScheduler`. No magic `48.dp` literals (use `InkSpacing.TouchTargetMin`). `@Config(qualifiers = "...")`. All Roborazzi goldens (40/40) verify green ± no diff output.
  - **Scope:** `QuickCaptureOverlayService.kt` zero diff. `HomeScreen.kt` SyncStatusBanner unchanged. `feature/editor/build.gradle.kts` unchanged. `feature/collections/build.gradle.kts` test deps added (Compose/Robolectric, no Roborazzi). Collections/Todo empty states preserved (InkCard + TextButton/SealButton pattern). InkCard default padding unchanged for all 78 callers.
  6. Run `pwsh -NoProfile -NonInteractive -File ./scripts/release-state.test.ps1` → must PASS.
  7. **Check signing environment:** verify all four env vars are set — `ANDROID_RELEASE_KEYSTORE_PATH`, `ANDROID_RELEASE_STORE_PASSWORD`, `ANDROID_RELEASE_KEY_ALIAS`, `ANDROID_RELEASE_KEY_PASSWORD`. If any is missing → `BLOCKED_SIGNING_MISSING`, STOP. Do NOT proceed with temporary/debug signing.
  8. Run `release-prepare.ps1` to authority-write version from 1.16.0 (168) to 1.17.0 (169):
     ```powershell
     pwsh -NoProfile -NonInteractive -File ./scripts/release-prepare.ps1 -Stage Prepare -OutputPath /tmp/1memos-release-1.17.0/prepare.json
     ```
     Check Prepare JSON:
     - `ok = true`
     - `reasonCode = RELEASE_GATE_PASSED`
     - `targetVersion = 1.17.0`
     - `targetVersionCode = 169`
     - `targetTag = v1.17.0`
     - artifact exists at reported path with package = `cc.pscly.onememos`, buildTools = `36.0.0`, signer SHA-256 = `58749c794f0c54af6b69bb6d80248a9fda0b75c687fde55b98d9575fc091633e`, timestamp filename matching `YYYY-MM-DDTHH-mm-ss.apk`
     - Record Prepare APK path and SHA-256
  9. After Prepare, only `app/build.gradle.kts` and `.ai_session.md` may be dirty. Commit: `git add app/build.gradle.kts .ai_session.md && git commit -m "release: bump to 1.17.0 (169)"`. `main` must be clean.
  10. Run `release-publish.ps1 -Stage Status`:
      ```powershell
      pwsh -NoProfile -NonInteractive -File ./scripts/release-publish.ps1 -Stage Status -OutputPath /tmp/1memos-release-1.17.0/status.json
      ```
      Check Status JSON: `state = Prepared`, `reasonCode = RELEASE_COMMIT_READY`, `transition = PushMain`. Must NOT push/tag/release.
  Parallelization: Wave 5 | Blocked by: 15 | Blocks: 17
  References: `scripts/release-prepare.ps1`（Prepare stage, authority-writes version from 1.16.0 to 1.17.0）; `scripts/release-publish.ps1`（Status stage, PushMain stage）; `scripts/release-state.ps1`（transitions: NewRelease→Prepared→MainPushed→...）; `scripts/release-verify-apk.ps1`（package=`cc.pscly.onememos`, buildTools=`36.0.0`, signerSha256=`58749c794f0c54af6b69bb6d80248a9fda0b75c687fde55b98d9575fc091633e`）; `scripts/release-state.test.ps1`; `scripts/verify.sh`
  Acceptance criteria: `verify.sh` exits 0 twice (idempotent). All inline audit checks PASS. `release-state.test.ps1` PASS. Signing env vars confirmed. Prepare JSON: ok/RELEASE_GATE_PASSED/1.17.0/169. Status JSON: state=Prepared/RELEASE_COMMIT_READY/PushMain. `main` clean. No push/tag/release executed.
  QA scenarios: Happy — all gates green, Prepare done, Status = Prepared; Failure — signing env var missing → `BLOCKED_SIGNING_MISSING`, STOP; Failure — Prepare JSON `ok = false` → examine output, fix, re-run; Failure — Status not Prepared → check stage ordering.
  Evidence: `/root/1codes/xinliu_android/.omo/evidence/2026-07-22-feature-ux-resilience/task-16.txt` + `prepare.json` + `status.json`
  Commit: Y | release: bump to 1.17.0 (169) via release-prepare.ps1, merge to main, pre-release audit pass

- [ ] 17. Authorized stable release pipeline（script-driven, with controlled recovery path）
  What to do: All stages use `release-publish.ps1`. All `-OutputPath` use `/tmp/1memos-release-1.17.0/<stage>.json`. Verify each returned JSON's state/reasonCode/transition before next stage. ALL remote stages BLOCKED until EXPLICIT EXTERNAL AUTHORIZATION at execution time.
  **Normal path（sequential, authorization-gated）:**
  1. `PushMain`:
     ```powershell
     pwsh -NoProfile -NonInteractive -File ./scripts/release-publish.ps1 -Stage PushMain -OutputPath /tmp/1memos-release-1.17.0/pushmain.json
     ```
     Requires current branch = `main`. Pushes `refs/heads/main:refs/heads/main`. After: JSON `state = MainPushed`, `reasonCode = MAIN_PUSHED_TAG_MISSING`, `transition = PushTag`.
  2. `PushTag`:
     ```powershell
     pwsh -NoProfile -NonInteractive -File ./scripts/release-publish.ps1 -Stage PushTag -OutputPath /tmp/1memos-release-1.17.0/pushtag.json
     ```
     Creates annotated tag `v1.17.0` with subject `1memos 1.17.0`. After: JSON `state = AwaitingTagActions`, `reasonCode = TAG_ACTIONS_PENDING`, `transition = WaitTagActions`. Tag SHA and peeled SHA recorded in JSON.
  3. `WaitTagActions`:
     ```powershell
     pwsh -NoProfile -NonInteractive -File ./scripts/release-publish.ps1 -Stage WaitTagActions -OutputPath /tmp/1memos-release-1.17.0/wait.json -DiscoveryTimeoutMinutes 5 -CompletionTimeoutMinutes 60 -PollSeconds 15
     ```
     After: JSON `state = TagActionsSucceeded`, `reasonCode = TAG_ACTIONS_AND_ARTIFACT_VERIFIED`, `transition = PublishRelease`. CI Artifact independently verified (package = `cc.pscly.onememos`, versionCode = 169, versionName = 1.17.0, signer SHA-256 = `58749c79...`, Artifact APK SHA-256 recorded).
  4. `PublishRelease`:
     ```powershell
     pwsh -NoProfile -NonInteractive -File ./scripts/release-publish.ps1 -Stage PublishRelease -OutputPath /tmp/1memos-release-1.17.0/publish.json
     ```
     Uploads the latest verified Tag Actions Artifact as a non-draft, non-prerelease latest GitHub Release. APK filename is timestamp `YYYY-MM-DDTHH-mm-ss.apk`. After: JSON `state = ReleasePublished`, `reasonCode = RELEASE_REVERIFY_REQUIRED`, `transition = VerifyRelease`.
  5. `VerifyRelease`:
     ```powershell
     pwsh -NoProfile -NonInteractive -File ./scripts/release-publish.ps1 -Stage VerifyRelease -OutputPath /tmp/1memos-release-1.17.0/verify.json
     ```
     Downloads Release APK, re-verifies metadata + SHA-256. Release APK SHA-256 must equal Tag Artifact SHA-256. After: JSON `state = Completed`, `reasonCode = RELEASE_DEEP_VERIFIED`, `transition = Cleanup`.
  6. `Cleanup`:
     ```powershell
     pwsh -NoProfile -NonInteractive -File ./scripts/release-publish.ps1 -Stage Cleanup -OutputPath /tmp/1memos-release-1.17.0/cleanup.json
     ```
     After: JSON `state = Completed`. Release complete.
  **Failure branches:**
  - **Transient Actions failure** (runner timeout, network): `gh run rerun <run-id>` on the same Tag/SHA. Re-run `WaitTagActions` stage. Do NOT move, delete, or recreate the tag.
  - **Code/configuration defect** after tag pushed but before Release published: STOP. Normal path cannot proceed. Do NOT manually `gh release create`, delete the tag, recreate the tag, force-push main, or collect post-hoc evidence.
  - **Controlled tag recovery path** (ONLY path that moves a pushed tag — requires exact lease + new authorization):
    1. Fix the code in the feature branch, commit to local `ux-resilience-1.17.0`, `git switch main && git merge --ff-only ux-resilience-1.17.0`. `main` now at clean strict-successor commit `B`.
    2. `release-publish.ps1 -Stage Status` to capture current state. Obtain old tag object ID `O` and peeled SHA `A`.
    3. Run PrepareRecovery:
       ```powershell
       pwsh -NoProfile -NonInteractive -File ./scripts/release-prepare.ps1 -Stage PrepareRecovery -CandidateSha B -ExpectedOldTagObjectId O -ExpectedOldTagPeeledSha A -RecoveryEvidencePath /tmp/1memos-release-1.17.0/recovery-evidence.json -OutputPath /tmp/1memos-release-1.17.0/recovery-prepare.json
       ```
       First run produces `.pending` output and `reasonCode = RECOVERY_WALKTHROUGH_REQUIRED`. Install the PrepareRecovery APK on a device, complete the walkthrough, record walkthrough evidence at the evidence path.
    4. Re-run PrepareRecovery with finalization:
       ```powershell
       pwsh -NoProfile -NonInteractive -File ./scripts/release-prepare.ps1 -Stage PrepareRecovery -CandidateSha B -ExpectedOldTagObjectId O -ExpectedOldTagPeeledSha A -RecoveryEvidencePath /tmp/1memos-release-1.17.0/recovery-evidence.json -OutputPath /tmp/1memos-release-1.17.0/recovery-prepare.json -FinalizeWalkthrough -ConfirmWalkthrough "RECOVERY_WALKTHROUGH:<B>:<apkSha>"
       ```
       After: JSON `ok = true`, `reasonCode = RECOVERY_GATE_PASSED`. Records recovery evidence SHA-256.
    5. `PushRecoveryMain`:
       ```powershell
       pwsh -NoProfile -NonInteractive -File ./scripts/release-publish.ps1 -Stage PushRecoveryMain -RecoveryEvidencePath /tmp/1memos-release-1.17.0/recovery-evidence.json -OutputPath /tmp/1memos-release-1.17.0/recovery-pushmain.json
       ```
    6. `Status` → must show `state = TagRecoveryRequired`, `transition = RequestAuthorizationThenRecoverTag`.
    7. Obtain new explicit authorization token: `RECOVER_TAG:v1.17.0:<O>:<A>:<B>:<evidenceSha>`.
    8. `RecoverTag`:
       ```powershell
       pwsh -NoProfile -NonInteractive -File ./scripts/release-publish.ps1 -Stage RecoverTag -RecoveryEvidencePath /tmp/1memos-release-1.17.0/recovery-evidence.json -ExpectedOldTagObjectId O -ExpectedOldTagPeeledSha A -NewTargetSha B -ExpectedRecoveryEvidenceSha256 <evidenceSha> -ConfirmTagRecovery RECOVER_TAG:v1.17.0:<O>:<A>:<B>:<evidenceSha> -OutputPath /tmp/1memos-release-1.17.0/recover.json
       ```
    9. After RecoverTag, state resolves to `AwaitingTagActions` with reason `TAG_ACTIONS_PENDING` and transition `WaitTagActions`. Re-run `WaitTagActions` → `PublishRelease` → `VerifyRelease` → `Cleanup` as in normal path.
    - **If recovery walkthrough cannot be completed** (no device, no authorization) → `BLOCKED_WALKTHROUGH` and STOP. Do not fabricate completion.
    - **Never**: delete tag, recreate tag, plain `git push --force`, force-push main, create Release without verified recovery evidence.
  **SHA-256 contracts:**
  - Prepare APK SHA (local): recorded at Todo 16 Prepare stage.
  - Tag Artifact SHA (CI): independently verified by `WaitTagActions`.
  - Release APK SHA (downloaded from GitHub Release): must equal Tag Artifact SHA.
  - Do NOT require local Prepare APK SHA to equal CI Artifact APK SHA.
  Parallelization: Wave 5 | Blocked by: 16 + explicit release authorization | Blocks: —
  References: `scripts/release-publish.ps1`（PushMain/PushTag/WaitTagActions/PublishRelease/VerifyRelease/Cleanup/PushRecoveryMain/RecoverTag stages）; `scripts/release-prepare.ps1`（Prepare/PrepareRecovery stages, `-CandidateSha`, `-ExpectedOldTagObjectId`, `-ExpectedOldTagPeeledSha`, `-RecoveryEvidencePath`, `-FinalizeWalkthrough`, `-ConfirmWalkthrough`）; `scripts/release-state.ps1`（transitions: Prepared→MainPushed→AwaitingTagActions→TagActionsSucceeded→ReleasePublished→Completed; recovery: RecoveryPrepared→TagRecoveryRequired→...）; `scripts/release-verify-apk.ps1`（package, buildTools, signerSha256）; `AGENTS.md §8`
  Acceptance criteria: All JSON outputs show expected state/reasonCode/transition at each stage. VerifyRelease returns Completed/RELEASE_DEEP_VERIFIED. Cleanup returns Completed. GitHub latest Release: v1.17.0, non-draft, non-prerelease, target_commitish=main. Release APK SHA = Tag Artifact SHA. Tag SHA intact (normal) or accurately reflected after controlled RecoverTag (recovery).
  QA scenarios: Happy — `gh release view v1.17.0 --json isDraft,isPrerelease,tagName,targetCommitish` → `false,false,v1.17.0,main`; VerifyRelease → Completed/RELEASE_DEEP_VERIFIED; Failure（transient）— `gh run rerun`, re-run WaitTagActions; Failure（code defect）— STOP, follow PrepareRecovery path; Failure（walkthrough impossible）— `BLOCKED_WALKTHROUGH`, STOP, do not fabricate; Failure（sign mismatch）— STOP, check keystore, no Release; Failure（missing auth）— BLOCKED, log missing token/env.
  Evidence: `/root/1codes/xinliu_android/.omo/evidence/2026-07-22-feature-ux-resilience/task-17.txt` + all `/tmp/1memos-release-1.17.0/*.json`
  Commit: N | release: v1.17.0 (169) authorized release via release-publish.ps1 state machine

## Final verification wave
> Runs in parallel after ALL todos including release. ALL must APPROVE.
- [ ] F1. Plan compliance audit (role: Oracle/Momus)
  Input: plan, draft, `git diff origin/main@2910580`, published Release
  Check: 18 todos → diffs. C1-C5 evidence. Scope OUT preserved. No forbidden API. No private composable calls from tests. No Hilt/`mock()`. All screen tests use `internal` entries. 40 goldens green. verify.sh +18 calls (total 34). Release published via state machine.
  Evidence: `/root/1codes/xinliu_android/.omo/evidence/2026-07-22-feature-ux-resilience/f1-compliance.txt`
  APPROVE when: All checks pass, scope clean, published Release matches plan.

- [ ] F2. Code quality review (role: Oracle)
  Input: Full git diff + published APK
  Check: `ui-test-manifest` on `debugImplementation`. One `imePadding()` per scrollable root. `clearAndSetSemantics` on decorative children. `mainClock` with `ignoreFrameDuration=true`. No `TestCoroutineScheduler`. All test entries `internal` with exact verified signatures + `{}` stubs. `SemanticsMatcher.expectValue` for Error (String) + LiveRegion. No `assertIsLiveRegion()`. No `mock()` or fictional types/fields. `@Config(qualifiers = "...")`. Independent clone from GitHub remote (origin = `git@github.com:pscly/xinliu_android.git`). No `file://` clone origin. No worktree. No manual git push/tag/release — script state machine only.
  Evidence: `/root/1codes/xinliu_android/.omo/evidence/2026-07-22-feature-ux-resilience/f2-quality.txt`
  APPROVE when: Zero anti-patterns, all constraints met.

- [ ] F3. Real manual QA (role: agent-executed)
  Input: `verify.sh` output (both runs), 40 Roborazzi verify reports, Benchmark APK, release JSON chain
  Check: verify.sh exit 0 twice. 40 goldens verify green with no diff output. Test classes re-run idempotently. Benchmark APK assembles. Release JSON chain unbroken from Prepare through VerifyRelease. TalkBack supplemental (non-blocking): if device available, `adb install` + swipe Auth→Home→Todo flow, record spoken announcements; else note "device unavailable, Robolectric semantics serve as proxy".
  Evidence: `/root/1codes/xinliu_android/.omo/evidence/2026-07-22-feature-ux-resilience/f3-qa.txt`
  APPROVE when: verify.sh=0 twice, 40/40 goldens match, tests idempotent, APK assembles, release chain complete.

- [ ] F4. Scope fidelity (role: Oracle)
  Input: Scope OUT, git diff, APK manifest, published Release
  Check: QuickCaptureOverlayService.kt zero diff. Home SyncStatusBanner unchanged. delay(6_000) behavior-tested (mainClock, not source grep). Collections/Todo empty states preserved. No NavigationSuiteScaffold imports. editor build.gradle.kts unchanged. InkCard default padding visually unchanged for 78 callers. APK: versionCode=169, versionName=1.17.0, package=cc.pscly.onememos, buildTools=36.0.0, signer=58749c79....
  Evidence: `/root/1codes/xinliu_android/.omo/evidence/2026-07-22-feature-ux-resilience/f4-scope.txt`
  APPROVE when: All Scope OUT confirmed untouched, APK metadata correct.

## Commit strategy
- Todo 0: no commit (infra)
- Todos 1-10: independent `feat`/`fix`/`test` conventional commits
- Todos 11-14: `test` commits
- Todo 15: `ci` commit
- Todo 16: `release` commit (version bump authored by `release-prepare.ps1 -Stage Prepare`, `.ai_session.md` update, merge to `main`)
- Todo 17: no new commit (release pipeline stages)
- All feature commits on `ux-resilience-1.17.0` in independent clone at `../xinliu_android-uxresil`
- Todo 16: `git switch main && git merge --ff-only ux-resilience-1.17.0`
- Evidence paths: `/root/1codes/xinliu_android/.omo/evidence/2026-07-22-feature-ux-resilience/` (not inside release clone)
- Release JSON: `/tmp/1memos-release-1.17.0/` (not inside release clone)

## Success criteria
1. 18 todos completed; F1-F4 all APPROVE
2. `verify.sh` exits 0 twice; 40 Roborazzi goldens verify green; `release-state.test.ps1` PASS
3. All screen tests use `internal` composables with exact baseline-verified signatures + `{}` stubs — no private calls, Hilt, `mock()`
4. `ui-test-manifest` on `debugImplementation`; Collections has base Compose/Robolectric test deps
5. Auth/QuickCapture error wrappers have distinct testTags; `SemanticsMatcher.expectValue(Error, "测试错误")` + `SemanticsMatcher.expectValue(LiveRegion, ...)` — Error is String, not list
6. TodoDoneMark internal, 48dp outer owns toggleable/clickable, inner 30dp + icon clearAndSetSemantics; TodoUndoHost internal with onExpired/onUndo, mainClock test passes all 5 cases
7. Profile 7×48dp = 336dp at W360, cells disjoint x-axis, gridStart = 2026-06-29; InkCard default padding unchanged for 78 callers
8. CollectionItemCard internal with modifier (18 total params); CollectionsItemsList content-lambda, modifier passed-through; full 14-field fixtures
9. ShareCardScreenContent internal (15 total params); FlowRow + @OptIn; Y-diff wrapping proven
10. Release via `release-publish.ps1` state machine: Prepare→PushMain→PushTag→WaitTagActions→PublishRelease→VerifyRelease→Cleanup; controlled recovery via PrepareRecovery→PushRecoveryMain→RecoverTag with exact lease
11. GitHub latest Release: v1.17.0 (169), non-draft, non-prerelease, target_commitish=main, signing SHA-256 = 58749c79..., Release APK SHA = Tag Artifact SHA
