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


## [2026-02-22] - F1 计划符合性审计：可复用实现模式
- Collections NOTE_REF 预览遵守 guardrail：UI 仅做 `uiState.memoByRefTargetId[targetId]` Map 查找，memo 的加载/缓存集中在 `CollectionsViewModel`（避免 LazyColumn item 内 per-item Flow collect）。
- 滚动降级策略：`rememberLazyListState` + `snapshotFlow { isScrollInProgress }`（`distinctUntilChanged`）在 screen 层得到 `enableRichPreview`，并下传到条目渲染控制 MarkdownPreview/纯文本切换。
- 无 Coil 依赖时的本地缩略图：`produceState` + `Dispatchers.IO` + `BitmapFactory`（bounds + inSampleSize + RGB_565），按目标尺寸解码降低内存峰值。

## [2026-02-22] - F2 代码质量审查：可复用的优化要点
- Collections 的“标签筛选”不要在每次 `selectedTags` 变更时对全量 NOTE_REF 反复跑 `TagExtractor.extractAll(memo.content)`：更稳妥的做法是先基于 `uiState.memoByRefTargetId` 派生 `targetId -> tags` 缓存（remember 一次），筛选只做集合判断，降低频繁交互下的抖动。
- Collections 的“滚动降级”除了富预览（Markdown）外，缩略图解码也应纳入降级策略：手动 `BitmapFactory` 解码没有 Coil 那样的缓存/复用体系，若滚动中仍解码，容易出现 IO/CPU 负载峰值。
- Home 的 `InkCard(combinedClickable)` + 子控件 `IconButton(clickable)` 的组合通常能保证事件分发正确；但涉及长按语义迁移时，建议把“`...` 点击不冒泡、长按不误触”纳入固定回归检查清单。

## [2026-02-22 23:24] - Task 4：集成验证重跑（门禁 + benchmark APK）
- `./scripts/verify.sh` 会在日志末尾打印 `verify.sh: OK`，可用 `rg -n "verify\.sh: OK" .sisyphus/evidence/task-4-verify.txt` 做门禁证据核验。
- `./scripts/build-benchmark-apk.sh` 会打印最终 APK 的绝对路径；本次产物为 `/root/1codes/xinliu_android/app/build/outputs/apk/benchmark/2026-02-22T23-24-31.apk`，并已通过 `test -f` 校验文件真实存在。

## [2026-02-22] - F2 代码质量审查补充：滚动热路径的“解码/解析”边界
- `feature/collections/.../CollectionsScreen.kt`：`FixedSizeThumbImage` 使用 `produceState + Dispatchers.IO` 调 `decodeThumbImageBitmap`（两次 `openInputStream` + `BitmapFactory.decodeStream`），即使已做“滚动降级（禁 Markdown 富预览）”，仍会在滚动过程中触发缩略图解码；需要把“缩略图是否渲染”也纳入 `isScrollInProgress` 的降级策略。
- `feature/collections/.../CollectionsScreen.kt`：标签筛选 `itemsToRender` 在 `selectedTags` 变更时会对每个 NOTE_REF 重新计算 tags（包含 `TagExtractor.extractAll(memo.content)` 的回退路径）；更稳妥的结构是先缓存 `targetId -> tags`，筛选只做集合判断，避免交互密集时重复解析正文。

## [2026-02-22 23:32] - 纠正：Task 4 交付证据以最新为准（verify/benchmark/adb）
- 说明：本文件此前条目中提到的 benchmark APK（如 `2026-02-22T13-39-46.apk`、`2026-02-22T23-24-31.apk`）及“无 ADB 设备”已过时，仅作历史过程记录。
- verify：`.sisyphus/evidence/task-4-verify.txt` 中包含 `verify.sh: OK`（门禁通过）。
- benchmark APK：当前有效交付产物为 `/root/1codes/xinliu_android/app/build/outputs/apk/benchmark/2026-02-22T23-32-22.apk`。
- adb：`.sisyphus/evidence/task-4-adb-devices.txt` 显示 `192.168.12.101:5555\tdevice`。

## [2026-02-23 00:13] - 纠正：Task 4 benchmark APK 交付以最新可验证产物为准
- 说明：由于 benchmark APK 以时间戳命名且构建/清理会导致旧产物不可复现，文档中引用的旧路径（例如 `2026-02-22T23-32-22.apk`）可能在磁盘上已不存在。
- 当前有效交付 APK：`/root/1codes/xinliu_android/app/build/outputs/apk/benchmark/2026-02-23T00-07-55.apk`。
- 证据：`.sisyphus/evidence/task-4-benchmark-apk-latest.txt`（包含 `ls -l` 输出）。
