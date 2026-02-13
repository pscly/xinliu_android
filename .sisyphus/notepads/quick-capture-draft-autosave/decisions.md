## [2026-02-12 11:30] - 决策：QuickCapture 草稿 JSON 原子写策略

- 存储位置：使用 `context.noBackupFilesDir/quick_capture_draft/draft.json`，避免被 Auto Backup/云备份带走。
- 原子写：优先使用 `android.util.AtomicFile`（或 `androidx.core.util.AtomicFile`）的 `startWrite()/finishWrite()/failWrite()`，避免进程被杀/崩溃导致半截 JSON 覆盖旧数据。
- 并发约束：`AtomicFile` 不提供锁语义，调用侧必须用互斥（同进程 `Mutex`/`synchronized`；多进程需要额外锁）。
- 附件管理：附件独立目录（如 `quick_capture_draft/attachments/`），先写附件（幂等命名，如 hash/uuid），再提交 JSON 引用；清理由后台 GC（按引用计数/最近访问/孤儿扫描），不做跨文件强事务。
- 错误语义：`finishWrite()` 内部会 `sync` 再 `rename`，但 `sync/rename` 失败通常只记录日志；关键路径需考虑校验（写后读/长度校验/重试）与降级策略。
- 可测试性：Robolectric 单元测试建议直接用 `androidx.core.util.AtomicFile`（纯 Java、可 JVM 跑），或确保测试环境对 `android.util.AtomicFile` 行为一致；目录不存在时由 `startWrite()` 创建或在测试里提前 `mkdirs()`。

## [2026-02-12 12:40] - 决策：实现落点与序列化方案

- 落点模块：为满足 `:feature:quickcapture` 不能依赖 `:core:data` 的约束，同时让 `:app` 也可复用，DraftStore 与模型放在 `:feature:quickcapture`（`cc.pscly.onememos.ui.feature.quickcapture.draft`）。
- JSON：不新增第三方依赖，采用 `org.json` 手写编码/解码（字段：schemaVersion/updatedAt/text/attachments）。
- 原子写实现：按任务要求使用 `draft.json.tmp` 写入 + `renameTo`（失败时 copy 兜底），而不是 `AtomicFile`；并把孤儿清理放在写入成功后。

## [2026-02-12 13:15] - 决策修正：JSON 原子写切换为 AtomicFile

- 原因：`renameTo` 失败时如果用 `copyTo(overwrite=true)` 覆盖目标，会失去“崩溃原子性”（可能产生半文件并破坏旧草稿）。
- 方案：对 `draft.json` 直接使用 `android.util.AtomicFile(startWrite/finishWrite/failWrite)`，让“写失败/崩溃”自动回退到旧文件（.bak 恢复语义由 AtomicFile 负责）。
