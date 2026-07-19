## 已发现问题

## [2026-02-11 23:23] - Task3 风险/注意点
- `MemosSyncWorker.prepareUploadPayload()` 当前会把附件完整读入内存并 base64 成字符串；即使设置允许到 1GB，实际上传很可能 OOM/卡死（需要 Task4 的流式 base64 RequestBody 才可控）
- 新增 DataStore key 时注意：key 命名采用 snake_case；repo 映射层应做 clamp，并可按 `dev2Unlocked` 决定上限（未解锁时强制回落到默认 50MB，避免“退出 dev2 后仍生效”）

## [2026-02-12 00:11] - Task1 执行过程遇到的环境坑
- `rg`（ripgrep）在当前环境未安装；定位文本请改用 `functions.grep`/`grep` 等替代。
- 默认 JDK 为 25.0.2 时，`./gradlew` 可能在 Kotlin DSL 脚本解析阶段抛 `IllegalArgumentException: 25.0.2`；需要显式切到 JDK21（例如 `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`）。
- 已安装 Kotlin 官方 `kotlin-lsp` 用于 `lsp_diagnostics`，但该工具在本工程上 `initialize` 仍可能超时（疑似与工程导入/索引耗时相关）；当前以 Gradle 编译/单测通过作为主验证。

## [2026-02-12 02:07] - Task3 附件上传上限相关的编译坑
- `SettingsRepository` 新增 `setAttachmentUploadMaxMb(mb: Int)` 后，所有 test fake/noop（例如 `app/src/test/java/cc/pscly/onememos/core/network/AppSettingsStateTest.kt` 内的 `NoopSettingsRepository`）都必须补齐 override，否则 `:app:compileDebugUnitTestKotlin` 直接失败。
