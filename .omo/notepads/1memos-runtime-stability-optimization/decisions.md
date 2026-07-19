## 决策记录

## 变更边界

## [2026-02-12 00:13] - Task1 决策：以“可注入 dispatcher”固定异步写盘行为
- 不在 Activity 引入额外线程池/全局 scope；只在 `saveBitmapToCacheFile(...)` 内用 `withContext(ioDispatcher)` 包裹写盘/编码，并默认 `Dispatchers.IO`。
- 单测通过注入 dispatcher 来验证调度，不依赖 Android 主线程/Looper 状态。
