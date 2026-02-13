# Issues

## 2026-02-13
- （预留）

- 本机默认 JDK=25.0.2 时，Gradle Kotlin DSL 会在 Settings 脚本编译阶段因 `JavaVersion.parse("25.0.2")` 抛 `IllegalArgumentException`；验证需显式 `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`。
- `lsp_diagnostics` 工具在本环境初始化超时（initialize timeout，stderr 出现 java.desktop 模块缺失提示），目前只能用 Gradle 编译/单测作为替代门禁。
- 本机未安装 `rg`（ripgrep），计划里涉及 `rg` 的命令需要用 `grep` 代替。
