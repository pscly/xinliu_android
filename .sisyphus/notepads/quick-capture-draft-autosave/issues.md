
## [2026-02-12] - issues

- `lsp_diagnostics` 在当前环境初始化超时（Kotlin LSP server 启动/绑定失败），无法用于“变更文件静态诊断”验证；本次以 Gradle 编译 + 单测作为替代门禁。
- 当前系统默认 JDK 为 25（`java -version` 显示 25.0.2），会导致 Gradle Kotlin DSL 侧 `JavaVersion.parse("25.0.2")` 抛异常；运行 Gradle 需要切到 JDK 21（例如 `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`）。
- Robolectric 版本未提供 `ShadowContentResolver.registerInputStream`（至少在本工程依赖版本下不可用），因此 `copyInAttachment` 的单测使用 `file://` Uri 跑通拷贝链路；后续如需严格覆盖 `content://`，建议补一个测试专用 `ContentProvider` 或升级/调整 Robolectric shadow API。

## [2026-02-12] - issues 追加

- 已按 Robolectric 4.12.2 API 改为 `Shadow.extract(...) as ShadowContentResolver` + 实例方法 `registerInputStream(...)`；`content://` 测试已恢复覆盖。
- `lsp_diagnostics` 在本环境仍持续 initialize timeout（疑似 Kotlin LSP server 无法正常启动/绑定端口），因此无法提供工具级静态诊断结果；本次继续以 `:app:testDebugUnitTest` 通过作为验证。

## [2026-02-12] - issues：协程测试依赖

- 为了使用虚拟时间测试 debounce，本次在 `app/build.gradle.kts` 增加了 `testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")`（与工程解析到的 coroutines-core 版本一致）。

## [2026-02-12] - issues：Overlay Robolectric 测试注意事项

- `QuickCaptureOverlayService` 是 `@AndroidEntryPoint` 的 Service，Robolectric 里直接 `controller.create()` 会触发 Hilt 注入前置检查并抛 `IllegalStateException`；本次单测改为只用 `Robolectric.buildService(...).get()` 拿实例，不调用 `onCreate()`，并通过 `debug*` 内部方法驱动草稿逻辑进行覆盖。
- `onStartCommand` 里有 `Settings.canDrawOverlays` 权限校验，不适合单元测试直接走该入口；单测使用内部 `debugAddAttachments(...)` 直接覆盖附件 copy + 草稿保存链路。

## [2026-02-12] - issues：DraftStore 互斥锁与 withContext 的死锁

- 现象：`QuickCaptureOverlayDraftTest` 在 `debugFlushDraftNow()` 路径可能卡死（Robolectric 主线程 runBlocking 等待，Test worker 等待 `Sandbox.runOnMainThread`）。
- 根因：`QuickCaptureDraftStore` 早期实现是 `mutex.withLock { withContext(Dispatchers.IO) { ... } }`，当调用方在主线程（例如 overlay destroy 的 runBlocking）时，锁的“释放”需要回到主线程恢复继续执行；若此时主线程被 runBlocking 占用，又有其它协程在等待同一把锁，会形成死锁。
- 修复：调整为 `withContext(Dispatchers.IO) { mutex.withLock { ... } }`，保证拿锁/释放锁都发生在 IO dispatcher 上，避免被主线程阻塞影响锁释放。
