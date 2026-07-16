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

## 8) 稳定版发布脚本

发布脚本要求 PowerShell 7，并把 `stdout` 仅作为人类可读日志。自动化调用必须传入 `-OutputPath`，再读取 UTF-8 无 BOM 的机器 JSON：

```powershell
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$outputPath = Join-Path ([System.IO.Path]::GetTempPath()) "1memos-release-$([guid]::NewGuid()).json"
try {
    & pwsh -NoProfile -NonInteractive -File .\scripts\release-publish.ps1 `
        -Stage Status `
        -OutputPath $outputPath
    $exitCode = $LASTEXITCODE
    $result = Get-Content -LiteralPath $outputPath -Raw -Encoding utf8 |
        ConvertFrom-Json -Depth 100
    if ($exitCode -ne 0 -or $result.ok -ne $true) {
        throw "发布阶段失败：state=$($result.state) reason=$($result.reasonCode) message=$($result.message)"
    }
    $result
} finally {
    Remove-Item -LiteralPath $outputPath -Force -ErrorAction SilentlyContinue
}
```

四个脚本的职责固定如下：

- `release-state.ps1`：纯状态推导，不读取文件，也不执行 Git、Gradle、GitHub 或 APK 命令。
- `release-prepare.ps1`：版本只递增一次，执行完整本地门禁并核验固定签名 Benchmark APK。
- `release-publish.ps1`：按状态执行 main、Tag、Actions、Artifact 和正式 Release 阶段；每次都从现场事实重新推导。
- `release-verify-apk.ps1`：固定使用 Build Tools `36.0.0`，核验包名、版本、唯一证书和 APK SHA-256。

`app/build/reports/release-context.json` 只是可删除缓存。删除或陈旧时脚本会重新收集现场事实，不能依靠该文件恢复授权或重建同版本恢复证据。

## 9) 签名前置检查与正常发布

执行 `Prepare` 前必须在当前会话提供以下四项固定发布签名配置：

```powershell
$env:ANDROID_RELEASE_KEYSTORE_PATH = '本机固定签名文件路径'
$env:ANDROID_RELEASE_STORE_PASSWORD = '存储密码'
$env:ANDROID_RELEASE_KEY_ALIAS = '签名别名'
$env:ANDROID_RELEASE_KEY_PASSWORD = '密钥密码'
```

缺少任一项时，`release-prepare.ps1` 返回 `ok=false`、`reasonCode=BLOCKED_SIGNING_MISSING`，且不会修改版本、运行门禁、生成发布 APK、推送 main、创建 Tag 或创建 Release。机器输出和日志不会包含密码或 keystore 路径。此状态必须停止，不能用 Debug 签名或临时签名继续正式发布。

签名可用时，正常阶段顺序固定为：

```powershell
pwsh -NoProfile -NonInteractive -File .\scripts\release-prepare.ps1 -Stage Prepare -OutputPath $prepareOutput
# 人工核验 Prepare 输出并提交 app/build.gradle.kts 与 .ai_session.md
pwsh -NoProfile -NonInteractive -File .\scripts\release-publish.ps1 -Stage PushMain -OutputPath $pushMainOutput
pwsh -NoProfile -NonInteractive -File .\scripts\release-publish.ps1 -Stage PushTag -OutputPath $pushTagOutput
pwsh -NoProfile -NonInteractive -File .\scripts\release-publish.ps1 -Stage WaitTagActions -OutputPath $actionsOutput
pwsh -NoProfile -NonInteractive -File .\scripts\release-publish.ps1 -Stage PublishRelease -OutputPath $publishOutput
pwsh -NoProfile -NonInteractive -File .\scripts\release-publish.ps1 -Stage VerifyRelease -OutputPath $verifyOutput
pwsh -NoProfile -NonInteractive -File .\scripts\release-publish.ps1 -Stage Cleanup -OutputPath $cleanupOutput
```

每一步都必须检查机器 JSON 中的 `ok`、`state` 与 `reasonCode`，不得跳步。`PushMain` 只执行普通 main 推送；`PushTag` 只创建 annotated Tag 并普通推送。若本地 Tag 已创建但远端推送中断，重跑只会复用目标提交、annotated 类型和固定消息全部匹配的本地 Tag；任何漂移都会在推送前停止。正式 Release 资产只能来自当前目标 Tag 最新成功 run 的唯一已核验 Benchmark Artifact。

## 10) 同版本 Tag 受控恢复

同版本恢复只适用于稳定 Tag Actions 因已确认的代码或配置缺陷失败，且已有干净、已提交、保持相同 `versionName/versionCode` 的快进候选 B。临时 GitHub、配额、Runner 或 Secrets 故障应重跑原 Tag/SHA workflow，不得移动 Tag。

恢复严格分为三个阶段，禁止合并或调序：

1. `PrepareRecovery`：必须在 B 推送 main 前运行完整门禁，先写 `.pending`；安装同一 APK 完成人工走查后，以 `RECOVERY_WALKTHROUGH:<candidateSha>:<apkSha256>` 精确确认，才原子生成最终证据。
2. `PushRecoveryMain`：仅接受 `RecoveryPrepared`，使用普通快进推送 main，不操作 Tag。
3. `RecoverTag`：仅在重新推导为 `TagRecoveryRequired` 后，取得新的书面授权，再使用旧 annotated tag object O 的精确 lease 更新 Tag。

授权确认串区分大小写，固定为：

```text
RECOVER_TAG:<targetTag>:<oldTagObjectId>:<oldTagPeeledSha>:<candidateSha>:<gateEvidenceSha256>
```

`RecoverTag` 仅允许 `--force-with-lease=refs/tags/<tag>:<oldTagObjectId>`；禁止 plain `--force`、删除后重建远端 Tag、force-push main、事后补写恢复证据或在 lease 失败后降级。最终恢复证据位于 `git rev-parse --git-path "release-evidence/<targetTag>-<candidateSha>.json"` 返回的非版本控制路径；证据丢失、摘要漂移、HEAD 变化或远端事实变化都必须停止。恢复后只能接受候选 B 的 Tag push run 和 Artifact，旧提交 A 的 run、Artifact 以及 B 的 main run 均不能用于正式 Release。

发布状态机回归测试：

```powershell
pwsh -NoProfile -NonInteractive -File .\scripts\release-state.test.ps1
```
