# bootstrap-qa

> 说明：这是本地 Sisyphus 工作计划文件（不应提交到 Git）。
> 目标：把当前工作区的“工作流状态文件”与“构建门禁”跑通，便于后续持续迭代。

## TODOs
- [x] 1. 将 `.sisyphus/` 加入仓库 `.gitignore`（避免误提交工作流状态文件）
  **What to do**: 修改 `../.gitignore`，新增 `**/.sisyphus/` 规则；确认 `git status` 不会再显示该目录。
  **Dependencies**: None
  **Verification**: `git status` 不再出现 `.sisyphus` 相关文件

- [x] 2. 跑通质量门禁（本机可重复）
  **What to do**: 在 `1memos/` 目录执行：`./gradlew.bat testDebugUnitTest lintDebug :app:assembleBenchmark --stacktrace`
  **Dependencies**: 1
  **Verification**: Gradle 退出码为 0

- [x] 3. 输出“带时间戳”的 benchmark APK
  **What to do**: 先确保 `:app:assembleBenchmark` 成功；再执行 `./scripts/copy-benchmark-apk.ps1` 生成 `YYYY-MM-DDTHH-MM-SS.apk`
  **Dependencies**: 2
  **Verification**: `app/build/outputs/apk/benchmark/` 下存在带时间戳的 apk

- [x] 4. 提交本次实际代码变更（仅限 `.gitignore`）
  **What to do**: `git add ../.gitignore` 并提交；不要提交 `.sisyphus`、build 产物等
  **Dependencies**: 1
  **Verification**: `git status` 工作区干净（或仅剩构建产物被忽略）
