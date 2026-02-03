# AGENTS.md（项目级）

本仓库用于 AI 辅助开发 Android 的多项目工作区。

目录约定：后续一级目录下以 `1xxx/`、`2xxx/` 命名的文件夹各自是独立 Android 项目（独立 Gradle/Android Studio 工程）。

## 1. 环境与命令（Windows + PowerShell 7）

- 运行环境：Windows，默认使用 PowerShell 7（pwsh）。
- 首次执行任何命令前：必须先执行：
  - `[Console]::OutputEncoding = [System.Text.Encoding]::UTF8`
- 构建/测试优先使用 Gradle Wrapper：在目标子项目目录执行 `./gradlew`（类 Unix 环境）或 `.\gradlew.bat`（Windows/PowerShell）。在本仓库默认的 Windows + pwsh7 环境下，优先使用 `.\gradlew.bat`。
- 除非用户明确要求，否则不要依赖全局安装的 `gradle`、不要假设已安装 Android SDK/NDK。

## 2. 编码与文件格式（强制）

- 所有文本文件必须统一为 UTF-8（无 BOM）。
- 禁止使用 GBK/ANSI 等本地编码；禁止提交包含不可读/乱码字符的内容。
- 修改或新增文件后，若发现历史文件不是 UTF-8，应先转换为 UTF-8 再继续修改。
- 代码注释与文档统一使用中文。

## 3. 多项目结构与工作流

- 任何开发任务开始前，先明确“目标子项目”（例如 `1xxx/` 或 `2xxx/`）。
- 非必要不跨项目改动；若必须跨项目（例如抽取公共组件），先说明改动范围与风险。
- Android 子项目根目录识别（满足其一即可）：
  - 存在 `settings.gradle`/`settings.gradle.kts` 且存在 `gradlew`/`gradlew.bat`
  - 或存在 `build.gradle`/`build.gradle.kts` 且存在 `app/`

## 4. 构建、测试与质量检查（参考）

在“目标子项目根目录”执行：

- 构建 Debug：`.\gradlew.bat :app:assembleDebug`
- 单元测试：`.\gradlew.bat testDebugUnitTest`
- Android Lint：`.\gradlew.bat lint`
- 需要更详细报错时：在命令末尾追加 `--stacktrace`（必要时再加 `--info`）

说明：仪器测试（`connectedDebugAndroidTest`）需要连接真机或启动模拟器，执行前先与用户确认环境。

## 5. Git 与敏感信息

- 不要提交本地环境文件与构建产物（已通过 `.gitignore` 规避）。
- 严禁提交密钥/证书/签名文件（例如 `*.jks`、`*.keystore`、`*.pem`、`*.p12`）。
- 对外部服务配置（API Key、签名信息等）优先使用：
  - `gradle.properties` + 环境变量
  - 或者本地 untracked 配置文件（不要入库）

## 6. AI 会话意图持久化（.ai_session.md）

- 开始任务：若目标项目根目录存在 `.ai_session.md`，先读取并简述历史背景。
- 发生代码修改、架构设计或重大决策后：必须更新 `.ai_session.md`。
- 多项目约束：
  - 针对 `1xxx/`、`2xxx/` 等子项目，在各自项目根目录维护独立 `.ai_session.md`。
  - 只有涉及工作区层面的改动（例如根目录规范文件、脚手架、共享脚本）时，才写入仓库根目录的 `.ai_session.md`。

## 7. 对用户输出要求

- 只使用中文沟通。
- 变更需可追溯：说明修改了哪些文件/关键符号、修改原因、以及如何验证（命令）。
- 若使用外部检索/工具：在答复末尾提供“工具调用简报”（工具、输入摘要、关键参数、时间）。

## 每次修改和开发完毕后

都需要提交修改的代码 git commit 

如果开发是完善的那么就需要打包一个 benchmark 的apk, 并且告诉用户路径 (apk文件需要有时间和日期 xxxx-xx-xxTxx-xx-xx.apk)

apk  不要打包 debug 以后全打包 benchmark
