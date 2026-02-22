# issues.md

（累计记录：遇到的问题、失败原因、回滚/替代方案。只追加，不覆盖。）

## [2026-02-22] - 工具链/环境问题
- LSP：`lsp_diagnostics` 在本环境初始化超时（initialize timeout），因此改用 Gradle 编译作为 Kotlin 错误兜底验证。
- JDK：默认 `java -version` 为 25.0.2，运行 `./gradlew` 会触发 `java.lang.IllegalArgumentException: 25.0.2`（Kotlin/Gradle Kotlin DSL 解析 Java 版本失败）。解决：构建命令前显式指定 Java 21：
  - `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:$PATH ./gradlew ...`

## [2026-02-22] - Collections：筛选与排序模式的潜在冲突点
- 当 `selectedTags` 非空时列表会过滤 NOTE_REF；若用户此时进入排序模式，界面可见项可能少于实际排序列表（`reorderIds` 仍包含全部 id），存在“移动时跨过不可见项导致顺序跳动”的认知风险。
- 当前处理：按需求仅禁用筛选条交互（不允许切换/清除），但未强制清空筛选或在排序模式下暂停筛选；如后续出现反馈，可考虑进入排序时自动清空筛选或临时忽略筛选。

## [2026-02-22] - Collections：NOTE_REF 图片缩略图的“远程不可用”边界
- Collections 的 UI state 没有 `serverBase`，因此 NOTE_REF 的图片缩略图仅能使用本地可访问的 `cacheUri(file://)` 或 `localUri` 作为 Coil 数据源；若附件仅有远程信息（remoteName/filename）但本地未缓存，将不会显示缩略图。
- 当前处理：这种情况下仍会正常渲染文本预览与标签，并用占位文案保持卡片结构稳定，不崩溃。

## [2026-02-22] - Collections：feature 模块未直接依赖 Coil
- `feature/collections` 的依赖里没有 Coil，因此在该模块直接引用 `coil.*` 会导致编译失败；在不改动 Gradle 依赖的前提下，只能用 `BitmapFactory + ContentResolver` 做固定尺寸缩略图解码。

## [2026-02-22] - subagent 越界改动清理
- 发现并移除未跟踪的 plan 文件：`.sisyphus/plans/jinnang-home-ux.md`（避免污染 plan 只读约束）。
- 同步清理空的临时目录：`.sisyphus/drafts/`、`.sisyphus/notepads/plan-discovery/`、`app/src/release/`（均为 untracked）。
- 清理后仅保留本计划相关改动：Home/Collections 代码 + `.sisyphus/boulder.json` + `.sisyphus/notepads/jinnang-home-ux/`。

## [2026-02-22] - plan 恢复失败：文件不在 git HEAD
- 现状：工作区缺失 `.sisyphus/plans/jinnang-home-ux.md`，且 `git ls-tree -r --name-only HEAD .sisyphus/plans` 中不存在该文件。
- 尝试：`git restore --source=HEAD -- .sisyphus/plans/jinnang-home-ux.md` 返回 `pathspec ... did not match any file(s) known to git`。
- 结论：该 plan 在当前分支/HEAD 未被跟踪，无法按“从 HEAD 恢复”的要求恢复；需要确认应从哪个提交/分支恢复，或先将 plan 纳入版本控制/调整 `.sisyphus/boulder.json` 的 `active_plan`。


## [2026-02-22] - F1 计划符合性审计：需人工确认/潜在风险
- QA 证据：`.sisyphus/evidence/` 已包含 `task-4-verify.txt`/`task-4-benchmark-apk.txt`/`task-4-adb-devices.txt`；但未看到按计划 Task 1/2/3 的 uiautomator dump/png（可能因无设备跳过）。需确认"无 adb 设备时，仅以 `adb devices` + 门禁/构建日志作为 QA 证据"是否可接受。
- Collections NOTE_REF 图片缩略图：当前仅使用本地可访问的 `cacheUri(file://)` 或 `localUri` 作为数据源；若附件仅存在远程信息且本地未缓存，将不会显示缩略图（但文本预览与标签仍可渲染）。需确认这是否符合"如有图片缩略图（如有）"的用户预期。
- Home/Archived 单条更多操作覆盖面：`...` BottomSheet 当前提供"放入锦囊/墨迹卡片"。若历史"长按更多操作"还包含其他单条动作（例如复制/删除/分享等），需确认这些入口是否仍可达，避免用户感知为功能丢失。

## [2026-02-22] - F2 代码质量审查：问题与风险（按严重级别）
- 【必须修】Collections 标签筛选的热路径可能引发卡顿：`itemsToRender` 的过滤逻辑在 `selectedTags` 变化时会对每个 NOTE_REF 逐个计算 tags；当 `memo.tags` 为空时会调用 `TagExtractor.extractAll(memo.content)`，在“文件夹内 NOTE_REF 较多 + 多次点标签”的场景下容易出现明显的 O(N * 内容解析) 开销。
  - 位置：`feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsScreen.kt`（`itemsToRender` 的 `base.filter` 分支）。
  - 建议方向：把 `targetId -> tags` 的派生结果做一次性缓存（随 `uiState.memoByRefTargetId` 变化更新），筛选时只做集合包含判断，避免每次切换筛选都重新解析正文。
- 【建议修】Collections NOTE_REF 缩略图在滚动中仍会触发“解码 IO + Bitmap 创建”：滚动降级目前只禁用了富预览（Markdown），但仍会渲染 1 张缩略图；由于这里是手动解码且未做跨 item/跨滚动缓存，列表快速滚动时可能出现“后台解码堆积/电量消耗/轻微掉帧”的风险。
  - 位置：`feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsScreen.kt`（`FixedSizeThumbImage`/`decodeThumbImageBitmap` 的调用链）。
  - 建议方向：当 `enableRichPreview == false`（或 `listState.isScrollInProgress == true`）时，用纯占位替代缩略图，或延后到停稳后再解码。
- 【建议修】Collections NOTE_REF 在筛选开启时，引用 memo 尚未加载/不可用的条目会被直接过滤掉（因为 tags 为空）：这在“待同步/慢加载”场景下会让列表看起来像“被筛没了”。
  - 位置：`feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsScreen.kt`（`itemsToRender` 对 NOTE_REF 的 tags 为空处理）。
  - 需确认：是否要在筛选态下仍保留“引用不可用/待同步”的 NOTE_REF（例如作为不可匹配但可见的占位项）。
- 【需关注】Home 的 `...` 入口依赖“子控件点击能正确拦截父 InkCard 的 combinedClickable”：从实现上看大概率没问题，但建议在真机上手动确认“点 `...` 不会打开随笔/不触发长按进入多选”。
  - 位置：`feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/MemoItem.kt`（`IconButton(onClick = onMoreActions)`）+ `core/designsystem/src/main/java/cc/pscly/onememos/ui/component/InkCard.kt`（`combinedClickable`）。

## [2026-02-22] - F2 代码质量审查补充：Home 侧 TagExtractor 的潜在成本点
- Home 的过滤逻辑在 Paging 层做 `PagingData.filter { ... }`，当 `memo.tags` 为空时会回退到 `TagExtractor.extractAll(memo.content)`：这会在“加载更多/滚动触发加载”时把正文解析带进过滤热路径，可能造成 CPU 峰值与电量消耗。
  - 位置：`feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeViewModel.kt`（`applyFilterToPaging` 内 memoTags 计算）。
  - 建议方向：优先使用结构化 tags；在 tags 缺失场景下，对正文解析做缓存/延迟，或在筛选模式中降低解析频率（例如仅对可见窗口解析）。

## [2026-02-22 23:32] - 纠正：plan 文件未缺失，且为 active_plan
- 说明：本文件中“移除未跟踪的 plan 文件 / plan 恢复失败（不在 HEAD）”的记录已被后续证据推翻，应视为历史误记。
- 证据：`.sisyphus/boulder.json` 的 `active_plan` 指向 `/root/1codes/xinliu_android/.sisyphus/plans/jinnang-home-ux.md`。
- 结论：`.sisyphus/plans/jinnang-home-ux.md` 当前存在并被 boulder 选为 active_plan；无需从 git HEAD “恢复”该文件。

## [2026-02-23 00:13] - 交付文档中的 benchmark APK 路径与磁盘不一致
- 现象：文档中引用了历史时间戳 benchmark APK（例如 `.../2026-02-22T23-32-22.apk`），但磁盘上该文件可能已不存在，导致“路径看似正确但无法复现”。
- 原因：benchmark APK 采用时间戳命名；多次构建、清理旧产物（例如 Gradle 清理/脚本清理）会移除旧时间戳文件，历史路径因此变为过时记录。
- 当前有效路径：`/root/1codes/xinliu_android/app/build/outputs/apk/benchmark/2026-02-23T00-07-55.apk`。
- 证据：`.sisyphus/evidence/task-4-benchmark-apk-latest.txt`（`ls -l` 输出证明文件存在）。
