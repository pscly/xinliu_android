# decisions.md

（累计记录：关键决策与理由。只追加，不覆盖。）

## [2026-02-23 12:46] - Task 6 提交策略与交付路径
- 提交拆分：按“业务代码改动”与“过程/计划/会话记录”拆分为多次 commit，避免把 Kotlin 业务逻辑与 Sisyphus 计划/记录混在同一提交里；本次不 `--amend`、不 push。
- 证据文件：
  - verify：`.sisyphus/evidence/task-4-verify.txt`
  - deliver：`.sisyphus/evidence/task-5-deliver-benchmark.txt`
  - status+diff：`.sisyphus/evidence/task-6-git-status-and-diff.txt`（`.sisyphus/evidence/` 默认被 `.gitignore` 忽略，仅用于本地留痕）
- benchmark APK：
  - 本地：`/root/1codes/xinliu_android/app/build/outputs/apk/benchmark/2026-02-23T12-37-14.apk`
  - 手机 Download：`/sdcard/Download/1memos-benchmark-2026-02-23T12-37-14.apk`
- ADB 串：`ADB_SERIAL=192.168.12.101:5555`
