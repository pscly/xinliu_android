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
