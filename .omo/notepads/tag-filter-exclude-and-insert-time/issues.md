# issues.md

（累计记录：遇到的问题、阻塞点与修复方案。只追加，不覆盖。）

- [2026-02-27 12:12] 设备回归阻塞：`adb devices -l` 仅看到 `192.168.12.101:5555 offline`，无法执行 `uiautomator dump`/截图/输入。
  - 已尝试：`adb kill-server && adb start-server`、`adb disconnect`/`adb connect 192.168.12.101:5555`、`adb reconnect offline`。
  - 现象：`ping 192.168.12.101` 正常，`nc 192.168.12.101 5555` 端口可连，但 `adb connect` 仍失败/连接后保持 `offline`（`adb -s ... shell` 返回 `device offline`）。
  - 证据：`.sisyphus/evidence/task-10-adb-devices-l.txt`、`.sisyphus/evidence/task-10-adb-devices-l-after-retry.txt`、`.sisyphus/evidence/task-10-adb-connect-trace.txt`、`.sisyphus/evidence/task-10-ping-192.168.12.101.txt`、`.sisyphus/evidence/task-10-nc-192.168.12.101-5555.txt`。
  - 推测与建议（需在设备侧操作）：确认目标设备已开启并授权 ADB；若使用“无线调试(配对)”模式，需要提供配对端口与配对码执行 `adb pair ip:port code`；若走传统 `adb tcpip 5555`，需要先通过 USB/本地连接执行 `adb tcpip 5555` 并确保 adbd 正常监听。

- [2026-02-28 00:08] 清理误产生的本地 untracked 产物：删除 `1` 与 `.sisyphus/tools/adb_screencap_clean.py`，避免污染后续 Task 10 的证据截图/`uiautomator dump` 交付。

- [2026-02-28 02:59] 远程取证不稳定点：`ssh/scp` 偶发出现 `Connection closed by 192.168.12.52 port 22`，导致脚本末尾 `scp` 中断。
  - 处理：先确认远程证据文件已写入（`ls -l /root/task10-final/...`），再单独重试 `scp`（建议加 `-o ServerAliveInterval=15 -o ServerAliveCountMax=3`）。
  - 补充：主页面的 `content-desc="菜单"` 常在**不可点击的子节点**上，直接按该节点 bounds 计算 tap 可能不触发抽屉；更稳妥是用 `uiautomator dump` 解析树后，向上找 `clickable="true"` 的父节点并点击其 bounds。

- [2026-02-28 06:55] Task 10 设备/模拟器回归阻塞（本环境的 emulator 不可用于 UI 取证）：
  - `adb devices -l` 当前可见 `emulator-5554 device`，但系统镜像首页为 `com.android.fakesystemapp/.launcher.EmptyHomeActivity`（非标准 SystemUI/Launcher），且未安装 `com.android.settings`。
  - 现象：`screencap`（含 `exec-out` 与落盘 `/sdcard/*.png`）产出 PNG 全黑；`uiautomator dump` 频繁报 `null root node`，或只能得到 `package="android"` 的空层级；无法生成计划要求的筛选/插入时间截图证据。
  - 补充：`QuickCaptureActivity` 与 `QuickCaptureOverlayEntryActivity` 在 `app/src/main/AndroidManifest.xml` 中 `android:exported="false"`，因此不能用 `adb shell am start -n ...` 直接启动，必须从 App 内入口（如 Settings 的“立即打开”按钮）跳转。
  - 建议：换用真机或标准 AOSP 模拟器（例如 Pixel 6 API 34），再执行计划中的 `uiautomator dump + screencap` 回归步骤。

- [2026-02-28 11:03] 误生成文件名问题：出现异常文件名 `" in xml);'"`，污染工作区并影响交付核验。
  - 避免方式：删除时始终使用精确引用路径（如 `rm -- " in xml);'"`），避免 shell 展开或误删。
  - 提交前防护：坚持白名单暂存 `git add -- <paths...>`，不使用 `git add .`，阻断误产物进入提交集。
