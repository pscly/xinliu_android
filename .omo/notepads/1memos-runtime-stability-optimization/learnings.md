## 约定与经验

## 常见坑

## [2026-02-11 23:19] - Task6 附件缓存 trim 抖动定位
- 触发点：`CacheRepositoryImpl.ensureImageAttachmentCached()` 每成功写入后都会调用 `trimAttachmentCacheIfNeeded(maxBytes)`
- trim 实现：`trimAttachmentCacheIfNeeded()` 先 `dirBytes()` 全量递归扫描，再 `walkTopDown().toList().sortedBy(lastModified)` 生成/排序文件列表，随后逐个 `delete()`；单次调用≈2 次全量遍历 + O(n log n) 排序 + 每文件 `length()`/`lastModified()` 的磁盘 IO
- 调用链：`AttachmentPrefetchWorker.doWork()` -> `cacheRepository.ensureImageAttachmentCached()`；`EditorViewModel.schedulePrefetchAttachmentCache()` -> `cacheRepository.ensureImageAttachmentCached()`；`MemosSyncWorker.enqueuePrefetchWork()` -> WorkManager 触发 `AttachmentPrefetchWorker`
- 现有节流：`AttachmentPrefetchWorker` 用 `sinceLastSizeCheck >= 8` 才做 `dirBytes()`，但无法避免 `ensureImageAttachmentCached()` 内部的 trim 仍“每图触发”
- 低侵入优化建议：
  1) `trimAttachmentCacheIfNeeded()` 用一次 `walkTopDown()` 同时收集 total/len/lastModified（必要时保留列表），避免先 `dirBytes()` 再二次遍历/再每文件 `length()`
  2) 在 `CacheRepositoryImpl` 内对 trim 加“时间/计数”节流（例如每 8 次成功缓存或间隔 >= 2s 才允许全量扫描），并引入 slack（如 `maxBytes + 16MB`）作为触发阈值，允许短暂超额以换取稳定性

## [2026-02-11 23:23] - Task3 探索：附件上传大小上限设置落点
- 设置链路现状：`SettingsScreen.kt`(UI) -> `SettingsViewModel.kt` -> `SettingsRepository.kt` -> `SettingsRepositoryImpl.kt`(DataStore)
- Dev2 门禁：`SettingsScreen.kt` 通过“10s 内点按版本号 6 次 -> 密码 pscly”解锁，UI 用 `if (uiState.dev2Unlocked)` 控制展示；`dev2ShowPublicWorkspaceMemos` 也在 repo 映射层强制要求 `dev2Unlocked && token.isNotBlank()`
- 数值输入模式：
  - Slider：`OfflinePrefetchRow`、`AttachmentCacheLimitRow`（0 表示无限的语义）
  - TextField 数字过滤：`devHomeRichPreviewStickyLimit`（digits 过滤 + `KeyboardType.Number`）
- 单测样板：`app/src/test/java/cc/pscly/onememos/data/settings/SettingsRepositoryImplFullSync*Test.kt`（Robolectric，直接 new `SettingsRepositoryImpl` + 调 setX + `repo.settings.first()` 断言）

## [2026-02-12 00:11] - Task1 截图写盘切 IO + 单测
- 现有实现的最小侵入做法：把 `mkdirs()`/`FileOutputStream`/`Bitmap.compress()` 整段包进 `withContext(Dispatchers.IO)`，并通过 `companion object internal suspend fun` 暴露“返回 File”的可测入口；Activity 侧仍保持 `FileProvider.getUriForFile(...)` 的 Uri 流程不变。
- 单测写法：Robolectric + `runBlocking { ... }`，用 `ApplicationProvider.getApplicationContext<Context>().cacheDir` 作为落盘目录，断言 `file.exists()` 且 `file.length() > 0`。
- 环境经验：若默认 `java -version` 为 25.x，Gradle Kotlin DSL 可能在 settings/build 脚本解析阶段直接抛异常；可通过 `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` 运行 `./gradlew ...` 规避。

## [2026-02-12 00:13] - Task1 提交与可测性补充
- 可测性技巧：给 `saveBitmapToCacheFile(...)` 增加可注入的 `ioDispatcher`，单测可用单线程 dispatcher 记录实际执行线程名，避免只测“写出了文件”而漏掉“是否真的切到后台线程”。
- 提交：`bde712b`（仅包含 ScreenshotQuickCaptureActivity 写盘切 IO + 对应单测新增）。

## [2026-02-12 02:20] - Task3 干净提交经验（避免混入无关改动）
- 工作区存在大量无关改动（例如 `feature/sharecard/**`、`.ai_session.md` 等）时，提交必须使用“按路径逐个 add”的方式，避免 `git add .` 误入。
- 提交前用 `git show --name-only --stat -1` 复核提交范围；本次 Task3 提交：`841f111`。

## [2026-02-12 03:05] - Task4a 流式 CreateAttachment JSON RequestBody
- JSON 转义不要手写：直接用 `org.json.JSONObject.quote(value)` 生成“带引号的 JSON 字符串字面量”，避免引号/反斜杠/控制字符转义遗漏。
- base64 必须 NO_WRAP：`android.util.Base64OutputStream(..., Base64.NO_WRAP)` 可流式写入，不会引入 `\n/\r`。
- 关键坑：`Base64OutputStream.close()` 会关闭底层 `OutputStream`，而 OkHttp/Okio 的 `BufferedSink` 不能被提前关闭；需要一个 `NonClosingOutputStream` 吞掉 close。
- `contentLength()` 仅在已知原始字节数时实现：base64 长度为 `((n + 2) / 3) * 4`，再加上 JSON 前后片段（UTF-8 bytes）的长度即可得到精确总长度。

## [2026-02-12 11:04] - Task6 附件缓存 trim 抖动优化：节流 + slack + 估算
- 优化点：在 `CacheRepositoryImpl.trimAttachmentCacheIfNeeded()` 内引入“估算大小（只读新增文件 length）+ 时间/次数节流 + slack(16MB) + hard interval(30s) 强制收敛”的组合策略，避免批量缓存时每张图都触发 walkTopDown/sort。
- 行为语义：
  - 删除顺序仍按 `lastModified` 从旧到新；真正触发 trim 时仍会删除到 `total <= maxBytes`。
  - 允许短暂处于 `(maxBytes, maxBytes + slack]`，以换取稳定性；但 hard interval 到期后会强制扫描并收敛到 `<= maxBytes`，避免“永远不收敛”。
- 关键实现细节：全量扫描时单次 walkTopDown 同时统计 total 与收集必要元信息；扫描/删除后回写 `attachmentCacheBytesEstimate`，让后续调用多数情况下可以直接返回。
