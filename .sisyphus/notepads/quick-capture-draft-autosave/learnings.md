
## [2026-02-12] - learnings：QuickCapture DraftStore（JSON+附件）

- 草稿 JSON 原子写建议采用 `draft.json.tmp -> renameTo(draft.json)`，并在 `renameTo` 失败时用 `copyTo(overwrite=true)` 兜底；这样在“写入中崩溃/异常抛出”场景下，旧 `draft.json` 仍可被 `loadDraft()` 读取。
- 孤儿附件清理必须放到“JSON 成功落盘”之后：否则一旦写入失败（旧草稿仍存在），会出现“旧草稿引用的附件被提前删掉”的不一致。
- `loadDraft()` 需要对损坏 JSON 容错（捕获解析异常返回 null），避免半截/脏文件导致崩溃。

## [2026-02-12] - learnings：原子写与 Robolectric content:// 输入流

- `copyTo(overwrite=true)` 覆盖目标文件不是“崩溃原子”的：拷贝过程中如果进程被杀/异常中断，会在目标路径留下半文件；正确做法是用 `android.util.AtomicFile` 或者“backup + rename 替换”的等价逻辑。
- Robolectric 4.12.2 的 `ShadowContentResolver.registerInputStream(Uri, InputStream)` 是实例方法：需要 `Shadow.extract(context.contentResolver)` 拿到 shadow 实例后再注册。

## [2026-02-12] - learnings：DraftAutoSaver（1 秒防抖 + flush）

- 纯逻辑层实现建议把“状态更新/取消 pending job”放到同步锁里（`synchronized`），避免 UI/Service 频繁调用导致竞态。
- `flushNow()` 除了立即保存，还要取消并抑制已排队的 debounce 保存；最稳的做法是用递增序号（token）让旧 job 即使被调度到也会自我失效。
- 单测用 `kotlinx-coroutines-test` 的虚拟时间（`runTest/advanceTimeBy/runCurrent`）验证 debounce 语义，避免 `Thread.sleep` 带来的慢与不稳定。

## [2026-02-12] - learnings：Overlay 草稿接入（banner/确认/恢复/清空）

- “覆盖确认”要做到未确认前不落盘：用 `draftWriteEnabled=false` 作为写入闸门，并在 `updateContent`/附件入口处拦截，先缓存 pending 值再弹窗。
- 覆盖确认通过后再允许写入：确认时把 `draftWriteEnabled` 置回 true，并把 pending 内容真正写入 UI state，再交给 `DraftAutoSaver` 走防抖保存。
- 附件草稿的 key 建议直接用 `fileName`：UI key/fileName/草稿引用三者一致，恢复时能稳定映射到 `filesDir/quick_capture_draft_attachments/<fileName>`。
- 退出场景的 flush 需要避免被 Service scope cancel 中断：`onClose`/`onDestroy` 用 `NonCancellable` 包一层调用 `flushNow()`，确保最后一次保存机会。
