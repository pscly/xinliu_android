---
slug: 2026-07-21-ui-debt-closeout
status: complete
intent: unclear
review_required: true
pending-action: execute .omo/plans/2026-07-21-ui-debt-closeout.md in a separate $start-work session
approach: 以既有纸墨设计系统为唯一视觉基线，只关闭审计可证实的共享转场、系统组件接线、状态原语、Chip 无障碍、视觉/性能证据和稳定版发布缺口
---

# Draft: 2026-07-21-ui-debt-closeout

## Components (topology ledger)
<!-- Lock the SHAPE before depth. One row per top-level component that can succeed or fail independently. -->
<!-- id | outcome (one line) | status: active|deferred | evidence path -->
| id | outcome | status | evidence path |
| --- | --- | --- | --- |
| C1 | Home 活跃分区已有随笔卡片与 Editor 形成稳定 shared bounds，Reduced Motion/新建/归档/跨分区不参与 | active | `AppNavigationHost.kt`、`MemoItem.kt`、`EditorScreen.kt`、ADR 0012 |
| C2 | 8 个业务 TopAppBar、5 个非悬浮层 ModalBottomSheet 和 Home Snackbar 显式使用 PaperInk 包装 | active | `PaperInkComponents.kt` 与生产调用点契约测试 |
| C3 | Editor 等价重试态迁移，TagChip/InkChip 恢复 48dp 触控和非颜色选中语义 | active | `InkStatePrimitives.kt`、`SettingsPrimitivesAccessibilityTest.kt` |
| C4 | Roborazzi、真实 Benchmark APK、设备路径和宏基准形成可复跑证据，不再接受 best-effort 假阳性 | active | `PaperInkComponentsScreenshotTest.kt`、`HomeScrollBenchmark.kt`、`.omo/evidence/` |
| C5 | 以 1.16.0 (168) 完成本地门禁、固定签名 Benchmark、main/Tag/Actions/latest Release 深度核验 | active | `scripts/release-*.ps1`、`.ai_session.md`、GitHub Release |

## Open assumptions (announced defaults)
<!-- Intent is UNCLEAR: research resolves ambiguity, defaults are adopted (not asked), and each is surfaced in the plan's human TL;DR for veto. -->
<!-- assumption | adopted default | rationale | reversible? -->
| assumption | adopted default | rationale | reversible? |
| --- | --- | --- | --- |
| 用户要的是“关闭真实债务”，而不是继续发明 M7 审美 | 只接通已存在的纸墨能力和验收证据，不重做页面视觉 | 原计划主体已完成，`DESIGN.md` 已把纸墨令牌和组件定为唯一基线 | 是 |
| 共享转场范围 | 仅 Home 活跃分区已有 memo → Editor；key 为 memo UUID；新建、归档、顶层分区切换和 Reduced Motion 禁用 | ADR 0012 明确限定同栈 Home→Editor，当前宿主已完成页面级转场和 Reduced Motion 门控 | 是 |
| 系统组件迁移策略 | 迁移 8 个业务顶栏、5 个非 overlay 底单、Home Snackbar；保留读取全局主题且无硬编码的 AlertDialog | `PaperInkComponents` 注释明确“全局主题 + 必要显式包装”双路径；全量 raw M3 替换会制造无收益 churn | 是 |
| 状态原语范围 | 只迁移 Editor 同步失败横幅；保留 Collections/Profile/Todo 空态、无 retry 的 Editor loadError 和紧凑弹层空文案 | 现有 `InkEmpty` 无法保持 Collections 的 InkCard + SealButton enabled 语义，也会改变 Profile 的安静卡片布局；Editor 横幅与 `InkRetryBanner` 完全等价 | 是 |
| Chip 触控与视觉 | 可交互 TagChip、所有 InkChip 的语义/点击容器至少 48dp，内部 Surface 保持紧凑；显式 selected/stateDescription | 当前实现与 M3.2 验收文档互相矛盾，且选中主要靠颜色表达 | 是 |
| 视觉回归 | 设计系统明暗/大字体矩阵 + Home MemoItem 明暗/大字体金图；真实 Editor/页面由设备证据覆盖 | 兼顾稳定可复跑金图与真实应用接线，不为截图测试重构整个 ViewModel 层 | 是 |
| 性能验收 | 宏基准必须实际找到 memo、进入 Editor 并返回，否则失败；无历史基线时只报告新鲜指标，不虚构“无回归” | 现用例注释和实现允许空数据假通过，当前无可引用历史结果 | 是 |
| 发布版本 | `1.15.2 (167)` → `1.16.0 (168)` | 用户可见 UI/交互变更，仓库状态机按最新稳定版推导下一 minor | 否（发布后） |
| 悬浮速记 | `QuickCaptureOverlayService.kt` 字节不变，显式排除其 AlertDialog/ModalBottomSheet | 用户已确认 1.15.2 效果“非常ok”，本轮不得回归已验收窗口行为 | 是 |

## Findings (cited - path:lines)

- `app/src/main/java/cc/pscly/onememos/navigation/AppNavigationHost.kt:178-189` 已有 `SharedTransitionLayout`、`NavDisplay(sharedTransitionScope=...)` 和 Reduced Motion 页面转场；宿主不是缺口。
- `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/MemoItem.kt:109-116` 与 `feature/editor/src/main/java/cc/pscly/onememos/ui/feature/editor/EditorScreen.kt:325-388` 均未挂 shared bounds；业务端配对为真实缺口。
- `docs/adr/0012-navigation-transition-strategy.md:22-33` 将接线面限定为宿主 + Home/Editor，并排除跨顶层栈、进程恢复动画和 Reduced Motion。
- `core/navigation/build.gradle.kts:32-40` 只有 Compose runtime 与 `navigation3-runtime`；共享 helper 需要 Compose UI/animation 与 `navigation3-ui`。
- `core/designsystem/src/main/java/cc/pscly/onememos/ui/theme/PaperInkComponents.kt:26-35` 定义全局主题和显式包装双路径；`85-182` 已有 TopAppBar/Snackbar/Dialog/BottomSheet 包装，但生产业务调用为零。
- 生产源码扫描得到 8 个 feature `TopAppBar`、5 个非 overlay `ModalBottomSheet` 和 1 个 Home `SnackbarHost`；AlertDialog 默认形状/颜色已由全局 `PaperInkShapes`/ColorScheme 覆盖。
- `core/designsystem/src/main/java/cc/pscly/onememos/ui/component/TagChip.kt:80-113` 明确拒绝 48dp；`InkChip.kt:71-92` 也没有最小触控尺寸或 selected 语义。
- `docs/qa/m3-2-paper-ink-accessibility.md:20-31` 声称 clickable TagChip 已补 48dp，而当前源码和 `SettingsPrimitivesAccessibilityTest.kt:221-225` 反向固定“无需 48dp”，属于必须修复的文档/实现矛盾。
- `feature/collections/.../CollectionsScreen.kt:540-557` 使用 InkCard + SealButton 且 busy 时禁用；`feature/profile/.../ProfileScreen.kt:167-176` 使用安静 InkCard。两者改为 `InkEmpty` 都会改变表面/图标/按钮类型，不能称为无损；仅 `feature/editor/.../EditorScreen.kt:402-419` 的带 retry 同步失败横幅与 `InkRetryBanner` 完全匹配。
- `feature/todo/.../TodoScreen.kt:337-362` 是双动作空态，不可无损映射当前单动作 `InkEmpty`；本轮保留。
- `core/designsystem/build.gradle.kts:3-40` 已接 Roborazzi，但当前金图仅有 smoke；`feature/home/build.gradle.kts:1-66` 尚未接截图基建。
- `macrobenchmark/.../HomeScrollBenchmark.kt:16-54` 把打开 Editor 定义为 best-effort，wait 结果未断言，空数据可假通过。
- `.ai_session.md:1-27` 记录当前已发布 `1.15.2 (167)`；仓库发布规则要求下一次 APK 变化完整发布。
- 工作区存在大量既有 `.omo/run-continuation/**`、`.tmp_vqa_20260720/` 和 research 噪声；执行必须使用隔离 worktree 或精确 staging，绝不清理/提交这些用户资产。

## Decisions (with rationale)

1. 在 `core:navigation` 提供本仓唯一、nullable 的 `LocalMemoSharedTransitionScope` + `Modifier.memoSharedBounds(uuid)` helper；local 只承载现有 `SharedTransitionLayout` 的 scope，不序列化、不进入快照，也不冒充官方/社区 Navigation API。`AppNavigationHost` 只负责注入，feature 不复制 Navigation 3 API。
2. Editor 的 target key 直接来自路由 `EditorKey.uuid`，不等待 ViewModel 异步加载；Home source 仅在 `HomeScreenMode.ACTIVE` 提供 UUID。
3. Reduced Motion 通过向 CompositionLocal 提供 `null` 禁用 shared bounds；保留现有 `NavDisplay(sharedTransitionScope=null)` 和零动画页面转场，不再添加第二套开关。
4. 仅对 M3 默认值确实绕过纸墨意图的 TopAppBar/Snackbar/BottomSheet 使用显式包装；AlertDialog 保持全局主题路径。
5. Chip 用外层 48dp 语义/点击容器包裹紧凑 Surface；不可点击 TagChip 保持纯展示紧凑，不伪造按钮语义。
6. 新增源码契约测试只守护明确枚举的接线边界，并由 Compose/Robolectric、Roborazzi、Gradle 编译、设备 QA 交叉验证，禁止仅凭 grep 宣称完成。
7. 宏基准改为 fail-fast；没有可比较基线时保留并报告绝对指标，不设置拍脑袋阈值。
8. 产品改动完成后按发布状态机走固定签名 1.16.0 (168)；签名、pwsh、解锁设备或外部发布授权任一缺失即标记 blocked，禁止临时签名或半发布。

## Scope IN

- Navigation 3 shared bounds helper、宿主注入、Home/Editor 配对和回归契约。
- 8 个业务 TopAppBar、5 个非 overlay ModalBottomSheet、1 个 Home SnackbarHost 的 PaperInk 接线。
- Editor 同步失败重试横幅；Collections/Profile/Todo 空态保持现有布局和动作。
- TagChip/InkChip 48dp、selected/stateDescription、焦点/禁用语义测试。
- PaperInk/Chip/Home MemoItem 的 Roborazzi 明暗/大字体金图及 verify 门禁。
- HomeScrollBenchmark 真实打开/返回断言和新鲜指标留存。
- DESIGN/ADR/QA/会话事实回写、1.16.0 (168) 固定签名 Benchmark 与完整 stable Release。

## Scope OUT (Must NOT have)

- 不新增 M7 视觉语言，不改色板、字体、纹理、信息架构、文案策略或业务流程。
- 不修改 `QuickCaptureOverlayService.kt` 及其 overlay AlertDialog/ModalBottomSheet，不改变 1.15.2 已验收悬浮速记几何/IME/输入框。
- 不做跨顶层栈、归档→Editor、新建随笔或进程恢复 shared bounds；不引入 Navigation 2 或社区 `SharedEntryInSceneNavEntryDecorator` / `localNavSharedTransitionScope`。除本仓唯一 nullable `LocalMemoSharedTransitionScope` 外，不再发明第二套 local/decorator 或 Reduced Motion 开关。
- 不全量替换 AlertDialog 或所有 raw Material3；不扩展状态原语以容纳双动作/紧凑弹层，不丢失 Todo 现有两个动作。
- 不为截图测试重构 ViewModel/数据层，不新增测试后门或预置生产数据库。
- 不用临时/debug/漂移签名上传正式 Release，不跳过 main、Tag、Actions、latest Release 任一步。
- 不删除、覆盖、提交既有 `.omo/run-continuation/**`、`.tmp_vqa_20260720/` 或无关 research 文件。

## Open questions

- 无。所有可逆内部决策已按仓库事实采用默认；执行到远端 PushMain 前必须按外部副作用规则取得一次明确发布授权。

## Approval gate
status: approved
approved-by: 用户在 2026-07-21 回复“Continue if you have next steps”并要求继续当前工作
approved-action: 完成并高精度复核 `.omo/plans/2026-07-21-ui-debt-closeout.md`；不授权实现或远端发布
metis-receipt: `ses_07d195268ffeqdiLZy4cy98CQN`（已完成；支持排除全量 raw-M3 迁移并要求精确验收）
momus-receipt: round-1 `ses_07ce1f145ffeETRMDOzoMXyBqq` = OKAY；round-2 `ses_07cd33d69ffeDYKYI0e2nN9PO2` = OKAY；round-3 `ses_07cc4ca38ffeODcd4eHCFkd01n` = OKAY
oracle-receipt: round-1 `ses_07ce1f172ffeGjm55v17Ay6iqi` = NOT OKAY；round-2 `ses_07cd33d81ffefAiDV8n5jU1nOU` = NOT OKAY；round-3 `ses_07cbf827dffeIfqXfSRtp7ykqH` = OKAY
review-fix-summary: round-1 收窄状态迁移并写死 scope receiver/OptIn/dependency、modifier 顺序、TagFilter modifier 归属、宏基准 selector、Chip 视觉失败阈值与源码词法清洗；round-2 补齐扫描根/豁免及 Home Scaffold `testTagsAsResourceId` 挂载点/OptIn；round-3 Momus 与 Oracle 均无条件批准，无剩余修订项
<!-- When exploration is exhausted and unknowns are answered, set status: awaiting-approval. -->
<!-- That durable record is the loop guard: on a later turn read it and resume at the gate instead of re-running exploration. -->
