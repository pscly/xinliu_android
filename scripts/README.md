# scripts/（脚本说明）

本目录下的脚本用于把“构建/验证/安装/推送”的步骤标准化，降低环境差异带来的重复劳动。

> 说明：仓库原本是 Windows + PowerShell 7 优先，因此已有 `.ps1` 脚本。本 README 主要补齐 Linux（Ubuntu/zsh/bash）场景。

## 1) Linux：验证门禁（推荐）

先跑架构边界，再跑模块测试、Lint 与 Benchmark：

```bash
./scripts/verify.sh
```

更完整（保留 `--all` 开关，供后续设备相关扩展）：

```bash
./scripts/verify.sh --all
```

单独跑架构门禁（秒级失败反馈）：

```bash
./scripts/verify-architecture.sh
```

Windows / PowerShell 7：

```powershell
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
.\scripts\verify.ps1
.\scripts\verify-architecture.ps1
```

架构门禁检查内容：

- 六个新 Core 模块（settings/update/calendar/quicktiles/externalactions/diagnostics）已注册
- Feature 互不依赖；Core 不依赖 app/Feature
- `:feature:settings` 依赖白名单（domain/navigation/designsystem）
- 归档归属 `HomeEntryContributor`，无 `:feature:archived`
- ViewModel 不含 `OneMemosNavigator`；Feature 无 Hilt Module/Binding
- 旧 Routes / NavController / navigation-compose 已消失
- §10.1 不可变字面量（包名、WorkManager、Room、FileProvider、benchmark target）

## 2) Linux：构建 benchmark APK（会生成时间戳副本）

```bash
./scripts/build-benchmark-apk.sh
```

输出示例（脚本最后一行会打印时间戳 APK 路径）：

```
app/build/outputs/apk/benchmark/2026-02-06T18-22-33.apk
```

## 3) Linux：安装到手机（adb）

如果 adb 只连着 1 台设备：

```bash
./scripts/install-benchmark.sh
```

若存在多台设备，请指定序列号：

```bash
ADB_SERIAL=192.168.12.101:5555 ./scripts/install-benchmark.sh
```

遇到签名不一致（会清数据，谨慎）：

```bash
ADB_SERIAL=192.168.12.101:5555 ./scripts/install-benchmark.sh --force-uninstall
```

## 4) Linux：推送到 Download（便于分发）

```bash
ADB_SERIAL=192.168.12.101:5555 ./scripts/push-benchmark-to-download.sh
```

## 5) Linux：一键交付（构建 + 安装 + 推送）

```bash
ADB_SERIAL=192.168.12.101:5555 ./scripts/deliver-benchmark.sh
```

## 6) 环境约束（重要）

部分环境下默认 JDK 版本过高会导致 Gradle/Kotlin DSL 解析失败，因此脚本默认固定：

- `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`（若你设置的 `JAVA_HOME` 不是 JDK21，脚本会提示并回退到默认）
- `GRADLE_USER_HOME=/tmp/gradle-user-home`
- `ANDROID_USER_HOME=/tmp/android-user-home`

如你的 JDK21 路径不同，可在运行脚本前自行覆盖（两者等价，优先级：`ONE_MEMOS_JAVA_HOME` > `JAVA_HOME`）：

```bash
export ONE_MEMOS_JAVA_HOME=/path/to/jdk21
```

## 7) GitHub Actions：门禁与 Artifact（不自动发正式 Release）

工作流：`.github/workflows/android-benchmark.yml`

触发方式：

- `push main`：执行 `./scripts/verify.sh`，上传时间戳 Benchmark APK Artifact（分支健康证据）
- `pull_request`：同一套门禁与 Artifact（可用临时签名）
- `push tag`（仅 `v*`）：同一套固定签名门禁与 Artifact（稳定版远端证据）
- `workflow_dispatch`：手动跑门禁；**无** `publish_release` 输入，不在工作流内创建 Release

固定发布顺序（Task 36）：

1. 本地完整门禁通过
2. 推送 `main`
3. 创建并推送 `vMAJOR.MINOR.PATCH` Tag
4. 等待该 Tag 对应 GitHub Actions 成功
5. 复核 Tag run 的 Artifact（包名、版本、签名证书 SHA-256）
6. **人工**创建非草稿、非预发布的 latest Release（只上传复核通过的 Benchmark APK）

说明：

- 工作流复用 `verify.sh` / `copy-benchmark-apk.sh`，本地与 CI 同一门禁。
- 工作流**不**创建 Tag、**不**创建/更新 Release、**不**删除 Release 资产。
- 固定签名证书 SHA-256：`58749c794f0c54af6b69bb6d80248a9fda0b75c687fde55b98d9575fc091633e`
- `main` 与 `v*` Tag run 缺少发布签名 Secrets 时必须失败；PR 可用临时签名。
