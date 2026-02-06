# scripts/（脚本说明）

本目录下的脚本用于把“构建/验证/安装/推送”的步骤标准化，降低环境差异带来的重复劳动。

> 说明：仓库原本是 Windows + PowerShell 7 优先，因此已有 `.ps1` 脚本。本 README 主要补齐 Linux（Ubuntu/zsh/bash）场景。

## 1) Linux：验证门禁（推荐）

```bash
./scripts/verify.sh
```

更完整（更慢）：

```bash
./scripts/verify.sh --all
```

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
