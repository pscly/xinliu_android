# Learnings

## 2026-02-03 Task: init
- Notepad initialized for plan `1memos-arch-ux-perf`.

## 2026-02-03 Task 16: cold start governance (EncryptedSharedPreferences)
- 避免在 Hilt graph 创建阶段（常在主线程）直接构造 `EncryptedSharedPreferences`：把创建逻辑延迟到首次真正需要 token 时。
- `DataStore.data.map { ... }` 的 mapping 默认跑在 collector 的上下文里（可能是 Main）；如果 mapping 内读加密存储，需要用 `flowOn(Dispatchers.IO)` 把上游切到 IO。
- `TOKEN_UPDATED_AT` 作为“token 变化触发器”时，建议在 settings 映射中读取该值，并用它做 in-memory cache key，避免每次偏好项变化都重复读加密存储。

## 2026-02-03 Task 17: home list governance (macrobenchmark)
- 为了让 `FrameTimingMetric` 更可比/更稳定：显式指定 `CompilationMode.Partial(BaselineProfileMode.UseIfAvailable)`，并把 `iterations` 从 5 提升到 10。
- 为降低 UI 等待抖动导致的偶发失败：适度放宽“打开记录后等待返回按钮”的 timeout（不改变业务行为，仅提升跑分稳定性）。

## 2026-02-03 Task: boulder
- boulder 的“完成进度”默认从 `.sisyphus/plans/*.md` 的 checkbox 计算；需要在 plan 中把 16/17/18 勾为 `[x]` 才会从 15/18 变为 18/18。
- `BaselineProfileMode.UseIfAvailable` 可以在 baseline profile 缺失时保持用例可跑通，同时在 profile 存在时尽量贴近发布路径的编译行为。
