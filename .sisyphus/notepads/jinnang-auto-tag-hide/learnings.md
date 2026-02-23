# learnings.md

（累计记录：实现过程中发现的规律/坑点/可复用代码位置。只追加，不覆盖。）

## 2026-02-23 任务1：CollectionsUiState 透传自动标签隐藏开关/关键字
- `CollectionsUiState` 新增透传字段：`devAutoTagLineKeywordsRaw`、`devShowAutoTagLineInHome`（只透传，不在 ViewModel 做解析）。
- ViewModel 侧建议把 settings 映射成小的 Flow（如 `keywordsRaw to showInHome`）并 `distinctUntilChanged()`，再与 `uiState` combine，避免无意义重组。
- 本地环境若默认 JDK=25，Gradle Kotlin DSL 可能因 `JavaVersion.parse("25.0.2")` 失败；可临时用 `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew ...` 规避。

## 2026-02-23 任务2：NOTE_REF 预览对齐 Home 的“元数据行隐藏”
- `CollectionsScreen` 顶层先 `AutoTagLineHider.parseKeywords(uiState.devAutoTagLineKeywordsRaw)`，并把 `showAutoTagLineInHome/autoTagKeywords` 透传到 `CollectionItemCard`，避免在列表 item 内重复解析。
- NOTE_REF 富预览：`MarkdownPreview(markdown = displayMarkdown)`，其中 `displayMarkdown` 用 `remember(memo.uuid, memo.updatedAt, showAutoTagLineInHome, autoTagKeywords)` 缓存，开关关闭时走 `AutoTagLineHider.hideFast(memo.content, autoTagKeywords)`。
- NOTE_REF 文本降级预览：仅当开关关闭且 `basePlainPreview` 可能包含关键字时，再调用 `MarkdownDeriver.plainPreviewSkippingLinesEndingWithKeywords(markdown = memo.content, keywords = autoTagKeywords, maxChars = 320)` 重派生。

## 2026-02-23 任务3："放入锦囊" displayName 对齐 settings 的关键字
- `AddToCollectionsViewModel.buildDisplayName()` 的 `keywords` 改为来自 `settings.devAutoTagLineKeywords`（解析复用 `AutoTagLineHider.parseKeywords`），避免与锦囊预览的隐藏规则不一致。
- 关键字解析做等价缓存：在 ViewModel 内把 `settingsRepository.settings.map { it.devAutoTagLineKeywords }` 转成 `StateFlow<List<String>>`，并用 `SharingStarted.Eagerly` 启动，确保即使无人 collect 也能拿到最新 `.value`。

## 2026-02-23 任务4：门禁验证与计划勾选
- 已运行 `./scripts/verify.sh` 并保存证据：`.sisyphus/evidence/task-4-verify.txt`（包含 `verify.sh: OK`）。
- 已在 `.sisyphus/plans/jinnang-auto-tag-hide.md` 将 Task 4 勾选为完成（`- [x]`）。

## 2026-02-23 任务5：benchmark 交付（build/install/push + 证据）
- 本地时间戳 benchmark APK：`/root/1codes/xinliu_android/app/build/outputs/apk/benchmark/2026-02-23T12-37-14.apk`
- 手机 Download 远端路径：`/sdcard/Download/1memos-benchmark-2026-02-23T12-37-14.apk`
- 证据文件：`.sisyphus/evidence/task-5-deliver-benchmark.txt`（包含 `deliver-benchmark.sh: OK` 以及 ADB 断言输出）。
- 备注：本机 `adb connect 192.168.12.101:5555` 可能提示 Connection refused，但 `adb -s 192.168.12.101:5555 ...` 仍可执行（连接可能已被 adb server 保持）。
