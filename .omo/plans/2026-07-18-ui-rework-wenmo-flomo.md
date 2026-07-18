# 心流界面大重构工作计划 v2（文墨 · flomo 呼吸感）

> 依据 12 轮访谈决策（CONTEXT.md「外观/随笔生命周期」词汇表 + ADR 0008–0011）。
> 审美靶心：flomo 式内容呼吸感、极简卡片流、标签安静退后；保留并强化纸、墨、线、印四要素。
> 策略 A：设计系统令牌化优先 → 首页样板屏打样 → 逐屏迁移。
> 版本基线：当前 `versionCode=157` / `versionName=1.9.0`；三个里程碑依次发布 1.10.0 / 1.11.0 / 1.12.0（以 `scripts/release-prepare.ps1` 实际递增为准）。

## 不变量（全程有效，违者返工）

- **正式包名恒为 `cc.pscly.onememos`**；`applicationIdSuffix ".dev"` + 应用名后缀"·内测"**只加在 debug buildType**，benchmark/release 构建一律不得带后缀（AGENTS.md §8 包名核验）。
- 主题相关新色值/字号/间距/圆角只允许进令牌，禁止散落业务界面。
- 归档 ≠ 删除；永不接 Memos 真删除 API。
- 每次正式 Release 走 `scripts/release-prepare.ps1` → `scripts/build-benchmark-apk.sh` → `scripts/release-publish.ps1` 完整 §8 闭环。

## 依赖与资产钉扎

| 项 | 钉法 |
|---|---|
| 霞鹜文楷 | GitHub `lxgw/LxgwWenKai` 最新 Release 下载 `LXGWWenKai-Regular.ttf`（约 19MB），放 `core/designsystem/src/main/res/font/lxgw_wenkai.ttf`；`OFL.txt` 放 `core/designsystem/src/main/assets/licenses/`；"关于与高级"页补 OFL 署名 |
| Roborazzi | `libs.versions.toml` 新增 `roborazzi = "1.41.0"` 与插件 `io.github.takahirom.roborazzi`；先挂在 `core/designsystem`，M3 按需扩到 feature；本地 `:core:designsystem:recordRoborazziDebug` 录制、CI/门禁用 `verifyRoborazziDebug`（拉取失败时执行时升级到最新稳定版并在 PR 描述记录实际版本） |
| mikepenz 渲染器 | `libs.versions.toml` 新增 `multiplatform-markdown-renderer = "0.37.0"`（`com.mikepenz:multiplatform-markdown-renderer-m3-android`，同上升策略）；M2 以新包 `core/designsystem/.../markdown2/` 并存，功能开关 `useNewMarkdownEngine` 默认开，渲染矩阵全绿后删旧 `MarkdownPaper` |
| ABI | `app/build.gradle.kts` `defaultConfig { ndk { abiFilters += "arm64-v8a" } }`（当前无 ndk 块，本次新增）；benchmark 继承；模拟器需 arm64 镜像，写入开发者文档 |
| 版本目录 | 以上全部进 `gradle/libs.versions.toml`，禁止模块内裸写版本号 |

## 设置存储与领域模型钉扎（里程碑 1 前置）

- **新存储形状**：DataStore 新增单 key `theme_descriptor`，值为 JSON：`{palette, texture, density, typeScale, fontFamily}`；旧 key `theme_palette` 保留只读用于迁移，迁移成功后清除。
- **迁移映射表**（旧 → 新，缺失/未知枚举一律回退"文墨·朱砂"预设）：
  - `PAPER_INK` → 朱砂色板 × 文墨卷轴 × 标准密度 × 标准字阶 × 文楷
  - `INDIGO` → 黛蓝色板 × 文墨卷轴 × 标准 × 标准 × 文楷
  - `CYBER` → 赛博色板 × 文墨卷轴 × 标准 × 标准 × 系统字体
- **M2/M3 设置字段 M1 一次性进 schema**（`AppSettings` + `SettingsRepository`，只写字段与默认值，UI 后补，避免二次迁移）：`listLayout=AUTO`、`swipeEnabled=true`、`swipeRightAction=ADD_TO_TODO`、`swipeLeftAction=FAVORITE`、`pageTransitionsEnabled=true`、`readingFontScale=STANDARD`、`lineHeight=STANDARD`、动态色板可见性（API 31+）。

## 四套出厂预设（轴取值钉死）

| 预设 | 色板 | 质感 | 密度 | 字阶 |
|---|---|---|---|---|
| 文墨·朱砂 | 纸墨朱砂（现有） | 文墨卷轴 | 标准 | 文楷标题 + 无衬线正文 |
| 清简·月白 | **新增"月白"中性色板**：primary 焦茶 `#383431` 系、次色雅灰、底面沿用草白/米宣纸 | 清简 | 宽松 | 系统字体 |
| 夜航·黛蓝 | 黛蓝（现有，深色主场） | 文墨卷轴 | 标准 | 文楷标题 |
| 赛博·荧光青 | 赛博（现有） | 清简 | 紧凑 | 系统字体 |

动态色板"随系统"是色板轴独立档（API 31+），不绑定预设；其对比度声明尽力而为、不进自动断言矩阵。

---

## 里程碑 1：设计系统地基（发布 1.10.0）

| # | 任务 | QA（工具 + 步骤 + 期望） |
|---|---|---|
| M1.1 | 令牌收敛：`InkSpacing/InkShape/InkBorder/InkMotion` 令牌对象；DESIGN.md §5 全部原语（InkCard、ScrollPaper、ScrollPaperSurface、SealButton、SealIconButton、InkChip、TagChip、ScrollTextField、MarkdownPaper、SealStampOverlay、TagFilterBottomSheet、ImageViewerDialog）改取令牌 | `./gradlew.bat :core:designsystem:lint`；人工 grep 原语文件无裸 `dp/sp/Color(0x` 字面量；期望零残留 |
| M1.2 | 主题描述符架构：`ThemeDescriptor(palette, texture, density, typeScale, fontFamily)` 数据结构 + `OneMemosTheme` 改造 + 上述 DataStore 迁移 | `./gradlew.bat :core:data:testDebugUnitTest`：单测「旧 `theme_palette=PAPER_INK/INDIGO/CYBER` 读出映射表对应描述符」「未知值回退文墨·朱砂」「迁移后旧 key 清除」全绿 |
| M1.3 | 质感轴：文墨卷轴（现有行为精修）+ 清简（无横线、大留白、细描边、标签退后）两种 Texture；ScrollPaper/InkCard/TagChip 按质感分支 | Roborazzi：同一原语 × 2 质感截图；期望清简下 ScrollPaper 无横线、InkCard 仅细描边 |
| M1.4 | TagChip 退后：清简质感去哈希彩虹色（无色块/细描边/次要色）；文墨质感降饱和收敛；保留 `#` 前缀与文本 | Roborazzi 对比截图；期望清简下无彩色块，文本可读 |
| M1.5 | 字体：文楷入 `res/font` + OFL.txt + 关于页署名；字阶轴字体档（文楷/系统）；Typography.kt 收敛 | `./gradlew.bat :app:assembleDebug` 安装真机：文墨预设标题为文楷、清简预设为系统字体；APK 增量 ≈20MB 且总 ≤200MB |
| M1.6 | 色板数据化 + 动态色板档（API 31+）+ 新增月白色板 | 单测：色板注册表含 5 套（3 旧 + 月白 + 动态）；Android 12+ 设置页可见"随系统"，以下不可见 |
| M1.7 | 风格预设 + "外观与交互"能力页改版：预设卡片一键切换 + 四轴高级调节（复用 `AppearanceInteractionScreen.kt` 改造） | `./gradlew.bat :feature:settings:testDebugUnitTest` 现有外观页测试更新后全绿；真机四预设切换即时生效无需重启 |
| M1.8 | ABI 收敛 + debug 双包名 | `./gradlew.bat :app:assembleDebug :app:assembleBenchmark`；`aapt dump badging` 验证 debug 包名 `cc.pscly.onememos.dev`、benchmark 为 `cc.pscly.onememos`；`apk Analyzer` 确认仅 arm64-v8a |
| M1.9 | Roborazzi 基建 + 对比度断言 | `verifyRoborazziDebug` 绿；`./gradlew.bat :core:designsystem:testDebugUnitTest` 中 WCAG 断言（4 套策展色板 × 明暗，普通文本 ≥4.5:1、大文本/控件 ≥3:1）全绿 |

**里程碑 1 验收**：lint + 单测 + 截图矩阵全绿；四预设明暗视觉过验；迁移测试绿；按不变量完成 1.10.0 发布闭环。

## 里程碑 2：首页样板屏 + 编辑器重构（发布 1.11.0）

| # | 任务 | QA |
|---|---|---|
| M2.0 | **转场 spike（0.5 天）**：验证当前 Navigation 版本共享元素转场可行性；不兼容则降级方案定为"淡入淡出 + 共享轴"并记录在 ADR 或计划批注 | spike 结论写入 `.omo/plans/` 本文件批注；期望：确定可行/降级二选一 |
| M2.1 | 首页时间线重做：编辑式日期分组（文楷大字日期 + 细墨线分隔）；卡片流按密度轴留白；稳定 key + contentType；Paging 3 沿用（`HomeScreen.kt`/`MemoItem.kt` 起步） | Roborazzi 分组截图；真机滚动千条 memo 无明显掉帧（macrobenchmark 长列表用例） |

> **M2.0 Spike 结论 (2026-07-18)**：`FEASIBLE` — 当前栈 Navigation3 **1.1.4** + Compose BOM **2026.06.00** 已具备 Home 卡片 → Editor 共享元素路径，**不必**降级为仅淡入淡出 + 共享轴。
> 依据：
> 1. 本仓 `gradle/libs.versions.toml`：`navigation3 = "1.1.4"`、`composeBom = "2026.06.00"`；主机 `AppNavigationHost` 现为 `NavDisplay(entries, onBack)`，**尚无** `transitionSpec` / `sharedTransitionScope`。
> 2. 本机解析 `navigation3-ui` **1.1.4** 字节码：`NavDisplay(List<NavEntry<T>>, …, SharedTransitionScope?, SizeTransform?, transitionSpec, popTransitionSpec, predictivePopTransitionSpec, onBack)`；另有 `SharedEntryInSceneNavEntryDecorator(SharedTransitionScope)`、`LocalNavAnimatedContentScope`、metadata 键 `TransitionKey` / `PopTransitionKey` / `PredictivePopTransitionKey`。
> 3. 官方发布说明 1.1.0（稳定，1.1.4 继承）：*Shared Elements between Scenes* — 向 `NavDisplay` 或 `rememberSceneState` 传入 `SharedTransitionScope`（https://developer.android.com/jetpack/androidx/releases/navigation3）。
> 4. 官方指南 *Animate between destinations*：`SharedTransitionLayout { NavDisplay(sharedTransitionScope = this) }` + `transitionSpec` / `popTransitionSpec` / `predictivePopTransitionSpec`（https://developer.android.com/guide/navigation/navigation-3/animate-destinations，Last updated 2026-06-16）。
> 5. Compose 共享元素：`Modifier.sharedElement` / `sharedBounds` 需 `SharedTransitionScope` + `AnimatedVisibilityScope`；Nav3 通过 `LocalNavAnimatedContentScope` 提供后者（https://developer.android.com/develop/ui/compose/animation/shared-elements）。
>
> **M2.9 实施路径（草图，本 spike 不改产品代码）**：
> 1. `AppNavigationHost`：`SharedTransitionLayout { NavDisplay(entries=…, sharedTransitionScope=this, transitionSpec/pop/predictive=…, onBack=…) }`。
> 2. 页面级默认：水平共享轴或 fade+slide 的 `ContentTransform`；共享元素用 `sharedBounds(rememberSharedContentState("memo/$uuid"), LocalNavAnimatedContentScope.current)` 挂在 Home `MemoItem` 与 Editor 根容器。
> 3. `pageTransitionsEnabled` + `ReducedMotion.current`：规格返回 `EnterTransition.None togetherWith ExitTransition.None`，或对 shared content `isEnabled=false`。
> 4. **范围**：仅 active 分区栈内（Home→Editor）；顶层分区切换替换整栈，不做跨分区共享元素。
> 5. **进程死亡**：恢复后无配对源 bounds，直接显示目标屏（可接受）；不依赖动画状态持久化。
>
> 降级保留：若 M2.9 真机列表↔编辑器 bounds 抖动/掉帧，再退到「淡入淡出 + 共享轴」页面转场，共享元素可关。详见 `docs/adr/0012-navigation-transition-strategy.md`。
| M2.2 | 图片图版：单图大图、双图并排、多图网格 +N；统一圆角令牌；Coil 占位/失败态（改 `MemoItem.kt` 缩略图区） | Roborazzi 1/2/4/9 图用例；期望圆角一致、占位非白屏 |
| M2.3 | MemoItem 重构：时间/标签/操作次要色退后，交互时强化；选择模式视觉统一 | 截图 + TalkBack 遍历顺序人工过验 |
| M2.4 | 宽屏自适应：WindowSizeClass Expanded 双列 `LazyVerticalStaggeredGrid`；设置"列表形态"三档（字段 M1 已备） | 折叠屏/平板模拟器（arm64 镜像）截图；设置三档切换即时生效 |
| M2.5 | 滑动手势：SwipeToDismiss 右滑"加入待办"、左滑"收藏"；过阈值 haptic；动作池 {待办,收藏,归档,置顶} 设置自选；总开关；归档撤销 Snackbar | UI 测试：默认动作、阈值、撤销、设置映射、关总开关回退纯长按，全绿 |
| M2.6 | **收藏领域接线**：`CollectionsRepository` 确保内置"收藏"文件夹（首次使用软创建、localFirst、离线可建待同步）；左滑收藏 = 加入该文件夹；锦囊区可见 | `./gradlew.bat :core:data:testDebugUnitTest`：无网收藏 → 恢复网络后同步成功用例绿 |
| M2.7 | Markdown 引擎切换：接入 mikepenz（`markdown2/` 并存 + 开关）；元素全映射纸墨皮肤；矩阵覆盖长文/表格/图片/待办/代码/嵌套引用；30sp 横线基线对齐自定义块间距；达标后退役旧 `MarkdownPaper` | Roborazzi 渲染矩阵全绿；旧实现删除后 `:core:designsystem` 无 `MarkdownPaper` 引用 |
| M2.8 | 编辑器重构（`EditorScreen.kt`）：BasicTextField + VisualTransformation 着色，最小标记集 = 标题/粗体/斜体/链接/待办/行内代码/引用；解析失败降级纯文本；顶栏"阅览"切预览并记住状态；宽屏左右双栏可关；rememberSaveable 草稿沿用 | UI 测试：着色各标记、旋转屏幕草稿不丢、预览往返内容一致；宽屏双栏截图 |
| M2.9 | 转场编排 + 动效分支：卡片→编辑器共享元素（或降级方案）；页面转场；设置"页面转场"开关；**基于现有 `core/designsystem/.../accessibility/ReducedMotion.kt` 扩展**，覆盖印章按压、盖章反馈、全部转场 | UI 测试：开关关 → 无转场动画；系统"移除动画"开 → 全部降级即时/淡入；`ReducedMotion` 调用点 grep 清单核对 |
| M2.10 | M3 组件纸墨化：TopAppBar/Snackbar/Dialog/BottomSheet 统一令牌皮肤 | Roborazzi 组件截图；期望无 M3 默认配色残留 |

**里程碑 2 验收**：样板屏两种质感 × 明暗经用户亲自验收（验收截图集存 `.omo/plans/acceptance-m2/`）；渲染矩阵与性能基准绿；手势/转场/reduced-motion 测试绿；完成 1.11.0 发布闭环。

## 里程碑 3：全屏迁移 + 无障碍验收（发布 1.12.0）

| # | 任务 | QA |
|---|---|---|
| M3.1 | 逐屏迁移，顺序按顶层分区：锦囊 → 待办 → 个人中心 → 归档 → 设置能力页 → auth/welcome/start → quickcapture → sharecard | 每屏 Roborazzi 明暗截图；期望全部取令牌、无旧样式残留 |
| M3.2 | 无障碍清债：48dp 触控包围盒、纸墨焦点环、禁用视觉、关键路径（浏览→编辑→保存→归档）TalkBack 顺序与播报、字体放大不裁切（含文楷印章） | `SettingsAccessibilityMatrixTest` 扩展全绿；TalkBack 人工过验记录；系统字体最大档截图无裁切 |
| M3.3 | 阅读模式：设置开放"阅读字号"四档、"行距"三档（字段 M1 已备），映射字阶/密度轴 | UI 测试档位切换；Roborazzi 各档正文截图 |
| M3.4 | 状态原语：统一加载/空/错误/重试组件，替换白版与默认样式 | 截图三态；关键页面接入 grep 清单核对 |
| M3.5 | 文档回写：DESIGN.md 按新事实重写（现状/约束/债务核销）；CONTEXT.md 术语复核 | DESIGN.md §8.2 债务表逐条核销或注明保留理由；评审通过 |

**里程碑 3 验收**：全部门禁绿；无障碍四项验证记录齐；200MB 预算核验；完成 1.12.0 发布闭环。

## 风险与注意

- M1.2 迁移是最大风险点：先合 schema 与迁移测试，再做 UI。
- mikepenz 与 30sp 横线基线对齐：M2.7 优先验证，不达标先调块间距再谈退役旧实现。
- 共享元素转场若 spike 失败：降级"淡入淡出 + 共享轴"，不阻塞里程碑。
- 每次发布前核验：包名（正式无后缀）、版本号、SHA-256、签名证书摘要。
- Roborazzi/mikepenz 版本若升级：PR 描述记录实际版本与变更原因。

## 开工前待办（执行者先做）

- 在 `.ai_session.md` 顶部 prepend 本次规划纪要：12 轮访谈决策摘要 + ADR 0008–0011 + 本计划路径（内容可直接摘抄上文「决策摘要」表）。
- 提交规划文档本身：`CONTEXT.md`、`docs/adr/0008–0011`、本计划文件（纯文档提交，不触发 §8 发布）。

## TODOs

### P0 开工前

- [x] 1. P0.1 规划纪要写入 `.ai_session.md` 顶部
- [x] 2. P0.2 提交规划文档（CONTEXT.md、ADR 0008–0011、本计划；纯文档，不触发 §8）

### 里程碑 1：设计系统地基

- [x] 3. M1.1 令牌收敛：InkSpacing/Shape/Border/Motion + 12 原语改取
- [x] 4. M1.2 ThemeDescriptor 领域模型 + DataStore 迁移与单测
- [x] 5. M1.3 质感轴：文墨卷轴 + 清简
- [x] 6. M1.4 TagChip 安静退后
- [x] 7. M1.5 霞鹜文楷入包 + Typography 字体档
- [x] 8. M1.6 色板数据化 + 月白 + 动态色
- [x] 9. M1.7 风格预设 + Appearance 能力页改版
- [x] 10. M1.8 ABI arm64-only + debug 双包名 `.dev`
- [x] 11. M1.9 Roborazzi 基建 + WCAG 对比度断言
- [x] 12. M1.R 里程碑 1 验收 + 发布 1.10.0 闭环

### 里程碑 2：样板屏 + 编辑器

- [x] 13. M2.0 共享元素转场 spike
- [ ] 14. M2.1 首页时间线重做（日期分组 + 密度留白）
- [ ] 15. M2.2 图片图版
- [ ] 16. M2.3 MemoItem 重构
- [ ] 17. M2.4 宽屏自适应 + 列表形态设置
- [ ] 18. M2.5 滑动手势 + 动作池
- [ ] 19. M2.6 收藏领域接线（内置收藏文件夹）
- [ ] 20. M2.7 Markdown 引擎切换 mikepenz
- [ ] 21. M2.8 编辑器重构（着色 + 预览 + 双栏）
- [ ] 22. M2.9 转场编排 + ReducedMotion 扩展
- [ ] 23. M2.10 M3 组件纸墨化
- [ ] 24. M2.R 里程碑 2 验收 + 发布 1.11.0 闭环

### 里程碑 3：全屏迁移 + 无障碍

- [ ] 25. M3.1 逐屏迁移（顶层分区顺序）
- [ ] 26. M3.2 无障碍清债
- [ ] 27. M3.3 阅读模式字号/行距
- [ ] 28. M3.4 状态原语
- [ ] 29. M3.5 DESIGN.md / CONTEXT.md 文档回写
- [ ] 30. M3.R 里程碑 3 验收 + 发布 1.12.0 闭环

## Final Verification Wave

- [ ] F1. 目标与约束核对（对照 ADR 0008–0011 + 本计划不变量）
- [ ] F2. 代码质量与架构审查
- [ ] F3. 安全与敏感路径审查
- [ ] F4. 端到端 / 门禁复验（lint + 单测 + 截图矩阵 + 包名/版本/签名）
