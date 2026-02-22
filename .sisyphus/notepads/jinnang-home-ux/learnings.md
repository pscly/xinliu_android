# learnings.md

（累计记录：实现过程中发现的规律/坑点/可复用代码位置。只追加，不覆盖。）

## [2026-02-22] - Home/Archived 长按进入多选 + 单条更多操作迁移到“..."
- `feature/home/.../MemoItem.kt`：通过给 `MemoItem` 增加可选回调 `onMoreActions: (() -> Unit)?`，可以在卡片内部放一个“...”按钮而不影响卡片本身的 click/long-click。
- `core/designsystem/.../InkCard.kt` 已使用 `combinedClickable`，因此在卡片内容里再放 `IconButton` 这类可点击控件，通常能正常拦截点击（“...”不会误触打开随笔）。
- 本仓库 Gradle Kotlin DSL 在 JDK 25 下会因 `java.lang.IllegalArgumentException: 25.0.2` 直接失败；构建时可临时切到 JDK 21（通过 `JAVA_HOME`/`PATH`）绕过。

## [2026-02-22] - Collections：当前文件夹内标签筛选（多标签 OR）
- `feature/collections/.../CollectionsScreen.kt`：NOTE_REF 的 memo 只能通过 `uiState.memoByRefTargetId` 做 Map 查找，避免在 LazyColumn 里做 per-item Flow collect（N+1 Flow）。
- 标签来源优先用 `memo.tags`（服务端/缓存的结构化标签），为空时才回退到 `TagExtractor.extractAll(memo.content)` 做内容提取，保证历史内容也能筛。

## [2026-02-22] - Collections：NOTE_REF 预览卡片性能（滚动降级 + 固定缩略图解码尺寸）
- `feature/collections/.../CollectionsScreen.kt`：滚动降级应该在 screen 层做（`rememberLazyListState` + `snapshotFlow { isScrollInProgress }`），把 `enableRichPreview` 作为参数下传到条目渲染，避免每个 item 自己订阅 Flow。
- 纯文本预览优先使用 `memo.plainPreview`，为空时用 `MarkdownDeriver.plainPreview(memo.content, maxChars=320)` 派生，减少滚动时 Markdown 渲染成本。
- 缩略图用 Coil `ImageRequest.Builder(context).size(px).crossfade(false)`，让解码直接对齐目标尺寸，避免滚动路径额外开销；列表滚动中可把缩略图数量从 2 降到 1。

## [2026-02-22] - Task 4：集成验证（verify.sh 门禁 + benchmark APK 产物）
- 为避免 JDK 25 触发 `java.lang.IllegalArgumentException: 25.0.2`，本次构建统一强制使用 JDK 21：`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:$PATH`。
- 门禁验证：运行 `./scripts/verify.sh` 并保留日志到 `.sisyphus/evidence/task-4-verify.txt`；额外用 `rg -n "verify\.sh: OK" .sisyphus/evidence/task-4-verify.txt` 确认输出包含 `verify.sh: OK`。
- benchmark APK：运行 `./scripts/build-benchmark-apk.sh` 并保留日志到 `.sisyphus/evidence/task-4-benchmark-apk.txt`，产物路径为 `/root/1codes/xinliu_android/app/build/outputs/apk/benchmark/2026-02-22T13-39-46.apk`，并已通过 `test -f` + `ls -l` 校验文件真实存在。
- ADB 设备：执行 `adb devices | tee .sisyphus/evidence/task-4-adb-devices.txt || true`；当前输出仅包含 header（无连接设备）。
